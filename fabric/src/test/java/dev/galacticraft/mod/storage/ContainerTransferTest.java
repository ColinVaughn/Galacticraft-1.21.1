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

package dev.galacticraft.mod.storage;

import net.minecraft.core.component.DataComponents;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ContainerTransferTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void transferConservesItemsAndHonorsLimit() {
        SimpleContainer source = new SimpleContainer(2);
        SimpleContainer target = new SimpleContainer(2);
        source.setItem(0, new ItemStack(Items.IRON_INGOT, 40));

        assertEquals(16, ContainerTransfer.move(source, 0, 2, target, 0, 2, 16));
        assertEquals(24, source.getItem(0).getCount());
        assertEquals(16, target.getItem(0).getCount());
        assertEquals(40, total(source) + total(target));
    }

    @Test
    void fullDestinationCannotDeleteOrDuplicateCargo() {
        SimpleContainer source = new SimpleContainer(1);
        SimpleContainer target = new SimpleContainer(1);
        source.setItem(0, new ItemStack(Items.IRON_INGOT, 12));
        target.setItem(0, new ItemStack(Items.IRON_INGOT, 64));

        assertEquals(0, ContainerTransfer.move(source, 0, 1, target, 0, 1, 16));
        assertEquals(12, source.getItem(0).getCount());
        assertEquals(64, target.getItem(0).getCount());
    }

    @Test
    void partialMergeMovesOnlyAvailableCapacity() {
        SimpleContainer source = new SimpleContainer(1);
        SimpleContainer target = new SimpleContainer(1);
        source.setItem(0, new ItemStack(Items.IRON_INGOT, 12));
        target.setItem(0, new ItemStack(Items.IRON_INGOT, 60));

        assertEquals(4, ContainerTransfer.move(source, 0, 1, target, 0, 1, 16));
        assertEquals(8, source.getItem(0).getCount());
        assertEquals(64, target.getItem(0).getCount());
        assertEquals(72, total(source) + total(target));
    }

    @Test
    void differingComponentsNeverMerge() {
        SimpleContainer source = new SimpleContainer(1);
        SimpleContainer target = new SimpleContainer(1);
        ItemStack named = new ItemStack(Items.IRON_INGOT, 60);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Route A"));
        source.setItem(0, new ItemStack(Items.IRON_INGOT, 8));
        target.setItem(0, named);

        assertEquals(0, ContainerTransfer.move(source, 0, 1, target, 0, 1, 16));
        assertEquals(68, total(source) + total(target));
    }

    @Test
    void rejectedItemsDoNotMergeIntoExistingStacks() {
        SimpleContainer source = new SimpleContainer(1);
        SimpleContainer target = new SimpleContainer(1) {
            @Override
            public boolean canPlaceItem(int slot, ItemStack stack) {
                return false;
            }
        };
        source.setItem(0, new ItemStack(Items.IRON_INGOT, 8));
        target.setItem(0, new ItemStack(Items.IRON_INGOT, 32));

        assertEquals(0, ContainerTransfer.move(source, 0, 1, target, 0, 1, 8));
        assertEquals(8, source.getItem(0).getCount());
        assertEquals(32, target.getItem(0).getCount());
    }

    @Test
    void roundTripConservesEveryStack() {
        SimpleContainer loader = new SimpleContainer(3);
        SimpleContainer rocket = new SimpleContainer(3);
        SimpleContainer unloader = new SimpleContainer(3);
        loader.setItem(0, new ItemStack(Items.IRON_INGOT, 48));
        loader.setItem(1, new ItemStack(Items.GOLD_INGOT, 23));
        int initial = total(loader);

        while (ContainerTransfer.move(loader, 0, 3, rocket, 0, 3, 16) > 0) {
            // Emulate successive loader ticks.
        }
        while (ContainerTransfer.move(rocket, 0, 3, unloader, 0, 3, 16) > 0) {
            // Emulate successive unloader ticks.
        }

        assertEquals(0, total(loader));
        assertEquals(0, total(rocket));
        assertEquals(initial, total(unloader));
    }

    private static int total(SimpleContainer container) {
        int total = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            total += container.getItem(slot).getCount();
        }
        return total;
    }
}
