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

import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/**
 * Layout and transfer maths for the crewed rocket's cargo hold, kept free of entity and rendering
 * state so the menu, the screen and the tests all agree on the same numbers.
 *
 * <p>The geometry reproduces Galacticraft Legacy's {@code ContainerRocketInventory}: cargo rows start
 * below the fuel gauge and the player inventory is pinned to the bottom of a background whose height
 * grows with the cargo.</p>
 */
public final class RocketCargoLogic {
    /** Cargo slots per row, as drawn on the background textures. */
    public static final int SLOTS_PER_ROW = 9;

    /** Height of the fuel-gauge-only background, used when the rocket has no storage upgrade. */
    public static final int BASE_HEIGHT = 132;

    /** First cargo row's y offset, immediately below the fuel gauge. */
    public static final int FIRST_CARGO_ROW_Y = 50;

    /** Each chest installed at the workbench is worth two rows of cargo, as in Galacticraft Legacy. */
    public static final int SLOTS_PER_CHEST = 18;

    private static final int SLOT_HEIGHT = 18;

    private RocketCargoLogic() {
    }

    /**
     * Cargo slots for a rocket built with {@code chests} chests, where {@code maxChests} is the
     * ceiling the storage upgrade allows. Out-of-range counts clamp rather than produce a container
     * that is negative or larger than the upgrade permits.
     */
    public static int storageSlots(int chests, int maxChests) {
        return Math.max(0, Math.min(chests, maxChests)) * SLOTS_PER_CHEST;
    }

    /** Rows of cargo drawn for the given slot count. */
    public static int rows(int storageSlots) {
        return storageSlots / SLOTS_PER_ROW;
    }

    /**
     * Height of the menu background. Legacy grew this by two pixels per cargo slot, which works out
     * to 181/217/253 for the one-, two- and three-chest rockets.
     */
    public static int menuHeight(int storageSlots) {
        return storageSlots == 0 ? BASE_HEIGHT : 145 + storageSlots * 2;
    }

    /** The y offset of a cargo row. */
    public static int cargoRowY(int row) {
        return FIRST_CARGO_ROW_Y + row * SLOT_HEIGHT;
    }

    /** The y offset of the first row of the player's main inventory. */
    public static int playerInventoryY(int storageSlots) {
        return menuHeight(storageSlots) - 82;
    }

    /** The y offset of the player's hotbar. */
    public static int hotbarY(int storageSlots) {
        return menuHeight(storageSlots) - 24;
    }

    /**
     * Copies the rocket's hold into the list carried across the dimension change.
     *
     * <p>The list is always {@code returnSlotCount} long even if the hold is a different size, because
     * the two trailing slots are where the rocket item and launch pad are written before the player
     * lands. Copies are taken so the hold can be cleared afterwards without emptying the result.</p>
     */
    public static NonNullList<ItemStack> collectForTransfer(Container inventory, int returnSlotCount) {
        NonNullList<ItemStack> stacks = NonNullList.withSize(returnSlotCount, ItemStack.EMPTY);

        int shared = Math.min(inventory.getContainerSize(), returnSlotCount);
        for (int slot = 0; slot < shared; ++slot) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty()) {
                stacks.set(slot, stack.copy());
            }
        }

        return stacks;
    }
}
