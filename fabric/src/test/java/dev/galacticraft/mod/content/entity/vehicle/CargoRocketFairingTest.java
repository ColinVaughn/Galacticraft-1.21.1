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

package dev.galacticraft.mod.content.entity.vehicle;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CargoRocketFairingTest {
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test void emptyHoldsStayCompact() {
        assertEquals(0, CargoRocketEntity.calculateCargoLoad(new SimpleContainer(27)));
        assertEquals(0, CargoRocketEntity.calculateCargoLoad(new SimpleContainer(0)));
    }

    @Test void partialStacksGrowTheFairingGradually() {
        var cargo = new SimpleContainer(27);
        cargo.setItem(0, new ItemStack(Items.IRON_INGOT, 32));
        assertEquals(0.5F / 27, CargoRocketEntity.calculateCargoLoad(cargo), 0.000001F);
        cargo.getItem(0).grow(32);
        assertEquals(1.0F / 27, CargoRocketEntity.calculateCargoLoad(cargo), 0.000001F);
    }

    @Test void stackSizesRepresentTheSameSlotVolume() {
        var cargo = new SimpleContainer(3);
        cargo.setItem(0, new ItemStack(Items.IRON_INGOT, 64));
        cargo.setItem(1, new ItemStack(Items.ENDER_PEARL, 16));
        cargo.setItem(2, new ItemStack(Items.DIAMOND_SWORD));
        assertEquals(1, CargoRocketEntity.calculateCargoLoad(cargo));
        cargo.setItem(0, new ItemStack(Items.IRON_INGOT, 32));
        cargo.setItem(1, new ItemStack(Items.ENDER_PEARL, 8));
        assertEquals(2.0F / 3, CargoRocketEntity.calculateCargoLoad(cargo), 0.000001F);
    }

    @Test void fullAndHalfHoldsHavePredictableSizes() {
        var cargo = new SimpleContainer(27);
        for (int i = 0; i < 27; i++) cargo.setItem(i, new ItemStack(Items.STONE, 32));
        assertEquals(0.5F, CargoRocketEntity.calculateCargoLoad(cargo));
        for (int i = 0; i < 27; i++) cargo.getItem(i).setCount(64);
        assertEquals(1, CargoRocketEntity.calculateCargoLoad(cargo));
    }

    @Test void oversizedStacksCannotOverextendTheFairing() {
        var cargo = new SimpleContainer(1);
        cargo.setItem(0, new ItemStack(Items.STONE));
        cargo.getItem(0).setCount(1000);
        assertEquals(1, CargoRocketEntity.calculateCargoLoad(cargo));
    }
}
