/*
 * Copyright (c) 2019-2026 Team Galacticraft
 * Copyright (c) 2026 Colin Vaughn
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package dev.galacticraft.mod.content.entity.vehicle;

import net.minecraft.SharedConstants;
import net.minecraft.core.NonNullList;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The crewed rocket's cargo hold. Galacticraft Legacy gave a rocket 18 slots per chest installed at
 * the workbench, hid two trailing slots that carry the rocket item and launch pad back down, and
 * grew the GUI to match. These tests pin those numbers.
 */
class RocketCargoLogicTest {
    /** Legacy's two reserved slots, mirrored by {@code GCServerPlayer.RESERVED_RETURN_STACKS}. */
    private static final int RESERVED = 2;

    /** The workbench offers three chest slots, so three is the storage upgrade's ceiling. */
    private static final int MAX_CHESTS = 3;

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void eachChestIsWorthTwoRowsOfCargo() {
        assertEquals(0, RocketCargoLogic.storageSlots(0, MAX_CHESTS));
        assertEquals(18, RocketCargoLogic.storageSlots(1, MAX_CHESTS));
        assertEquals(36, RocketCargoLogic.storageSlots(2, MAX_CHESTS));
        assertEquals(54, RocketCargoLogic.storageSlots(3, MAX_CHESTS));
    }

    @Test
    void aRocketCannotClaimMoreStorageThanTheUpgradeAllows() {
        assertEquals(54, RocketCargoLogic.storageSlots(4, MAX_CHESTS));
        assertEquals(54, RocketCargoLogic.storageSlots(Integer.MAX_VALUE, MAX_CHESTS));
        // A malformed chest count must not produce a negative container size.
        assertEquals(0, RocketCargoLogic.storageSlots(-1, MAX_CHESTS));
    }

    @Test
    void cargoIsLaidOutInRowsOfNine() {
        assertEquals(0, RocketCargoLogic.rows(0));
        assertEquals(2, RocketCargoLogic.rows(18));
        assertEquals(4, RocketCargoLogic.rows(36));
        assertEquals(6, RocketCargoLogic.rows(54));
    }

    @Test
    void theBackgroundGrowsWithTheCargo() {
        // Legacy's ContainerRocketInventory heights.
        assertEquals(132, RocketCargoLogic.menuHeight(0));
        assertEquals(181, RocketCargoLogic.menuHeight(18));
        assertEquals(217, RocketCargoLogic.menuHeight(36));
        assertEquals(253, RocketCargoLogic.menuHeight(54));
    }

    @Test
    void cargoRowsStartBelowTheFuelGauge() {
        assertEquals(50, RocketCargoLogic.cargoRowY(0));
        assertEquals(68, RocketCargoLogic.cargoRowY(1));
        assertEquals(140, RocketCargoLogic.cargoRowY(5));
    }

    @Test
    void thePlayerInventorySitsBelowTheCargoWithoutOverlapping() {
        for (int slots : new int[]{0, 18, 36, 54}) {
            int lastCargoRowBottom = slots == 0
                    ? RocketCargoLogic.cargoRowY(0)
                    : RocketCargoLogic.cargoRowY(RocketCargoLogic.rows(slots) - 1) + 18;

            assertTrue(RocketCargoLogic.playerInventoryY(slots) >= lastCargoRowBottom,
                    "player inventory overlaps cargo at " + slots + " slots");
            // Three rows of inventory then the hotbar, all inside the background.
            assertEquals(RocketCargoLogic.playerInventoryY(slots) + 58, RocketCargoLogic.hotbarY(slots));
            assertTrue(RocketCargoLogic.hotbarY(slots) + 18 <= RocketCargoLogic.menuHeight(slots),
                    "hotbar falls outside the background at " + slots + " slots");
        }
    }

    @Test
    void theZeroCargoLayoutIsUnchanged() {
        // The rocket built without chests kept the original fuel-gauge-only screen.
        assertEquals(50, RocketCargoLogic.playerInventoryY(0));
        assertEquals(108, RocketCargoLogic.hotbarY(0));
    }

    @Test
    void departingCargoKeepsTheReservedSlotsFree() {
        int storage = 18;
        SimpleContainer hold = new SimpleContainer(storage + RESERVED);
        hold.setItem(0, new ItemStack(Items.STONE, 42));
        hold.setItem(17, new ItemStack(Items.TORCH, 7));

        NonNullList<ItemStack> transferred = RocketCargoLogic.collectForTransfer(hold, storage + RESERVED);

        assertEquals(storage + RESERVED, transferred.size());
        assertEquals(42, transferred.get(0).getCount());
        assertEquals(Items.STONE, transferred.get(0).getItem());
        assertEquals(7, transferred.get(17).getCount());
        // The rocket item and launch pad are written into these two on the way down.
        assertTrue(transferred.get(storage).isEmpty());
        assertTrue(transferred.get(storage + 1).isEmpty());
    }

    @Test
    void departingCargoIsCopiedSoTheHoldCanBeCleared() {
        SimpleContainer hold = new SimpleContainer(RESERVED + 18);
        hold.setItem(3, new ItemStack(Items.DIAMOND, 5));

        NonNullList<ItemStack> transferred = RocketCargoLogic.collectForTransfer(hold, RESERVED + 18);
        assertNotSame(hold.getItem(3), transferred.get(3));

        hold.clearContent();
        assertEquals(5, transferred.get(3).getCount(), "clearing the hold emptied the transferred stacks");
    }

    @Test
    void aShrunkenHoldStillProducesAFullLengthTransfer() {
        // A rocket whose parts failed to resolve falls back to a bare reserved-slot hold; the list
        // handed to the player still has to be long enough for the rocket item and launch pad.
        SimpleContainer hold = new SimpleContainer(RESERVED);

        NonNullList<ItemStack> transferred = RocketCargoLogic.collectForTransfer(hold, 18 + RESERVED);

        assertEquals(20, transferred.size());
        assertTrue(transferred.stream().allMatch(ItemStack::isEmpty));
    }
}
