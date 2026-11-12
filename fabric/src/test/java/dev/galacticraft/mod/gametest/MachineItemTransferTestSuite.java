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

package dev.galacticraft.mod.gametest;

import dev.galacticraft.machinelib.api.block.entity.MachineBlockEntity;
import dev.galacticraft.mod.content.GCBlocks;
import dev.galacticraft.mod.content.block.entity.machine.CompressorBlockEntity;
import dev.galacticraft.mod.content.block.entity.machine.ElectricArcFurnaceBlockEntity;
import dev.galacticraft.mod.content.block.entity.machine.ElectricCompressorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.HopperBlockEntity;

public final class MachineItemTransferTestSuite implements GalacticraftGameTest {
    private static final BlockPos MACHINE = new BlockPos(1, 2, 1);

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
    public void hoppersFeedAndEmptyAnArcFurnace(GameTestHelper context) {
        checkHoppers(context, GCBlocks.ELECTRIC_ARC_FURNACE,
                ElectricArcFurnaceBlockEntity.INPUT_SLOT, ElectricArcFurnaceBlockEntity.OUTPUT_SLOTS);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
    public void hoppersFeedAndEmptyACompressor(GameTestHelper context) {
        checkHoppers(context, GCBlocks.COMPRESSOR, CompressorBlockEntity.INPUT_SLOTS, CompressorBlockEntity.OUTPUT_SLOT);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
    public void hoppersFeedAndEmptyAnElectricCompressor(GameTestHelper context) {
        checkHoppers(context, GCBlocks.ELECTRIC_COMPRESSOR,
                ElectricCompressorBlockEntity.INPUT_SLOTS, ElectricCompressorBlockEntity.OUTPUT_SLOTS);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
    public void hoppersFuelACompressorWithoutExtractingItsCoal(GameTestHelper context) {
        context.setBlock(MACHINE, GCBlocks.COMPRESSOR);
        context.setBlock(MACHINE.above(), Blocks.HOPPER);
        context.setBlock(MACHINE.below(), Blocks.HOPPER);
        HopperBlockEntity input = context.getBlockEntity(MACHINE.above());
        input.setItem(0, new ItemStack(Items.COAL, 2));
        runAt(context, 40, () -> {
            CompressorBlockEntity machine = context.getBlockEntity(MACHINE);
            context.assertTrue(machine.itemStorage().getItem(CompressorBlockEntity.FUEL_SLOT).getCount() == 2,
                    "Hopper coal did not reach the compressor's fuel slot");
            HopperBlockEntity output = context.getBlockEntity(MACHINE.below());
            context.assertTrue(output.isEmpty(), "The output hopper stole the compressor's fuel");
            context.succeed();
        });
    }

    private void checkHoppers(GameTestHelper context, Block block, int inputSlot, int outputSlot) {
        context.setBlock(MACHINE, block);
        context.setBlock(MACHINE.above(), Blocks.HOPPER);
        context.setBlock(MACHINE.below(), Blocks.HOPPER);
        MachineBlockEntity machine = context.getBlockEntity(MACHINE);
        machine.itemStorage().slot(outputSlot).set(Items.DIAMOND, 2);
        HopperBlockEntity input = context.getBlockEntity(MACHINE.above());
        input.setItem(0, new ItemStack(Items.RAW_IRON, 4));
        runAt(context, 48, () -> {
            context.assertTrue(machine.itemStorage().getItem(inputSlot).getCount() == 4,
                    "Hoppers did not load the input slot or extracted unprocessed ingredients");
            HopperBlockEntity output = context.getBlockEntity(MACHINE.below());
            context.assertTrue(output.getItem(0).is(Items.DIAMOND) && output.getItem(0).getCount() == 2,
                    "The output hopper did not collect the machine's products");
            context.assertTrue(machine.itemStorage().getItem(outputSlot).isEmpty(), "Products remained in the machine");
            context.succeed();
        });
    }
}