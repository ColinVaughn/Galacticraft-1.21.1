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

package dev.galacticraft.mod.content.block.entity.machine;

import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.content.GCBlocks;
import dev.galacticraft.mod.content.item.CannedFoodItem;
import dev.galacticraft.mod.content.item.GCItems;
import dev.galacticraft.mod.gametest.GalacticraftGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;

public class FoodCannerGameTest implements GalacticraftGameTest {
    @GameTest(template = EMPTY_STRUCTURE)
    public void everyNonEmptyRowPatternProducesOneFullCanWithoutLosingFood(GameTestHelper test) {
        for (int mask = 1; mask < 16; mask++) {
            var machine = machine(test);
            machine.energyStorage().setEnergy(100000);
            machine.itemStorage().slot(FoodCannerBlockEntity.INPUT_SLOT).set(GCItems.EMPTY_CAN, 1);
            for (int row = 0; row < 4; row++) for (int col = 0; col < 4; col++) {
                if ((mask & (1 << row)) != 0) machine.itemStorage().slot(FoodCannerBlockEntity.GRID_START + row * 4 + col).set(Items.APPLE, 4);
            }
            tick(test, machine, Constant.FoodCanner.MAX_PROGRESS + 5);
            var output = machine.itemStorage().getItem(FoodCannerBlockEntity.OUTPUT_SLOT);
            test.assertTrue(output.is(GCItems.CANNED_FOOD) && output.getCount() == 1 && CannedFoodItem.isFull(output), "Row pattern " + mask + " failed to fill a can");
            int remaining = 0;
            for (int slot = FoodCannerBlockEntity.GRID_START; slot < FoodCannerBlockEntity.STORAGE_SLOT; slot++) {
                remaining += machine.itemStorage().getItem(slot).getCount();
            }
            test.assertValueEqual(remaining, 16 * Integer.bitCount(mask) - CannedFoodItem.MAX_FOOD, "Food conserved for row pattern " + mask);
            test.assertTrue(machine.itemStorage().slot(FoodCannerBlockEntity.INPUT_SLOT).isEmpty()
                    && machine.itemStorage().slot(FoodCannerBlockEntity.STORAGE_SLOT).isEmpty(), "Can was duplicated");
        }
        test.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void noPowerPreservesInputAndEjectionReturnsAnEmptyCan(GameTestHelper test) {
        var machine = machine(test);
        machine.itemStorage().slot(FoodCannerBlockEntity.INPUT_SLOT).set(GCItems.EMPTY_CAN, 1);
        tick(test, machine, 10);
        test.assertValueEqual(machine.getProgress(), 0, "Unpowered progress");
        test.assertValueEqual(machine.itemStorage().getItem(FoodCannerBlockEntity.INPUT_SLOT).getCount(), 1, "Unpowered input preserved");
        machine.energyStorage().setEnergy(100000);
        tick(test, machine, Constant.FoodCanner.START_ROW_1 + 2);
        test.assertTrue(machine.itemStorage().getItem(FoodCannerBlockEntity.STORAGE_SLOT).is(GCItems.CANNED_FOOD), "Empty can not loaded");
        machine.ejectCan();
        tick(test, machine, Constant.FoodCanner.MAX_PROGRESS - Constant.FoodCanner.TRANSFER_OUTPUT + 2);
        test.assertTrue(machine.itemStorage().getItem(FoodCannerBlockEntity.OUTPUT_SLOT).is(GCItems.EMPTY_CAN)
                && machine.itemStorage().slot(FoodCannerBlockEntity.STORAGE_SLOT).isEmpty(), "Ejection lost or duplicated the empty can");
        test.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void blockedEjectionPreservesBothSlots(GameTestHelper test) {
        var machine = machine(test);
        machine.energyStorage().setEnergy(100000);
        machine.itemStorage().slot(FoodCannerBlockEntity.STORAGE_SLOT).set(GCItems.CANNED_FOOD, 1);
        machine.itemStorage().slot(FoodCannerBlockEntity.OUTPUT_SLOT).set(Items.DIAMOND, 64);
        machine.ejectCan();
        tick(test, machine, 1);
        test.assertTrue(machine.itemStorage().getItem(FoodCannerBlockEntity.STORAGE_SLOT).is(GCItems.CANNED_FOOD), "Blocked ejection removed the can");
        test.assertValueEqual(machine.itemStorage().getItem(FoodCannerBlockEntity.OUTPUT_SLOT).getCount(), 64, "Blocked output preserved");
        test.assertValueEqual(machine.getProgress(), 0, "Blocked ejection advanced progress");
        test.succeed();
    }

    private FoodCannerBlockEntity machine(GameTestHelper test) {
        BlockPos relative = new BlockPos(1, 1, 1);
        test.setBlock(relative, net.minecraft.world.level.block.Blocks.AIR);
        test.setBlock(relative, GCBlocks.FOOD_CANNER);
        return (FoodCannerBlockEntity) test.getLevel().getBlockEntity(test.absolutePos(relative));
    }

    private void tick(GameTestHelper test, FoodCannerBlockEntity machine, int ticks) {
        var level = test.getLevel();
        for (int tick = 0; tick < ticks; tick++) {
            machine.tickBase(level, machine.getBlockPos(), level.getBlockState(machine.getBlockPos()), level.getProfiler());
        }
    }
}
