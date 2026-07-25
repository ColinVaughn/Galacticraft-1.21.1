/*
 * Copyright (c) 2019-2026 Team Galacticraft
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

package dev.galacticraft.mod.attachments;

import net.minecraft.SharedConstants;
import net.minecraft.core.NonNullList;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GCServerPlayerTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void landingTransferKeepsLaunchpadRocketAndUnusedFuel() {
        GCServerPlayer transfer = new GCServerPlayer((ServerPlayer) null);
        transfer.setLaunchpadStack(new ItemStack(Items.IRON_BLOCK, 9));
        transfer.setFuel(12_345);

        NonNullList<ItemStack> cargo = NonNullList.withSize(20, ItemStack.EMPTY);
        cargo.set(0, new ItemStack(Items.DIAMOND, 3));
        transfer.setRocketStacks(cargo);
        transfer.finishReturnInventory(new ItemStack(Items.MINECART));

        assertEquals(3, transfer.getRocketStacks().get(0).getCount());
        assertEquals(9, transfer.getRocketStacks().get(18).getCount());
        assertEquals(Items.IRON_BLOCK, transfer.getRocketStacks().get(18).getItem());
        assertEquals(Items.MINECART, transfer.getRocketStacks().get(19).getItem());
        assertEquals(12_345, transfer.getFuel());
        assertTrue(transfer.getLaunchpadStack().isEmpty());
    }
}
