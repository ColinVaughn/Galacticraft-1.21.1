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

package dev.galacticraft.mod.content.item;

import dev.galacticraft.mod.content.GCBlocks;
import dev.galacticraft.mod.content.block.special.CryogenicChamberBlock;
import dev.galacticraft.mod.content.block.special.CryogenicChamberPart;
import dev.galacticraft.mod.gametest.GalacticraftGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class StandardWrenchGameTest implements GalacticraftGameTest {
    @GameTest(template = EMPTY_STRUCTURE)
    public void rotationsStayReversibleAndChargeDurabilityOnce(GameTestHelper test) {
        var player = makeSurvivalServerPlayer(test);
        var wrench = new ItemStack(GCItems.STANDARD_WRENCH);
        player.setItemInHand(InteractionHand.MAIN_HAND, wrench);
        BlockPos pos = test.absolutePos(new BlockPos(1, 2, 1));
        var level = test.getLevel();
        level.setBlock(pos, Blocks.OAK_LOG.defaultBlockState(), Block.UPDATE_ALL);
        var original = level.getBlockState(pos);
        use(test, player, wrench, pos);
        test.assertTrue(level.getBlockState(pos).getValue(RotatedPillarBlock.AXIS) != Direction.Axis.Y, "Log did not rotate");
        player.setShiftKeyDown(true);
        use(test, player, wrench, pos);
        test.assertTrue(level.getBlockState(pos).equals(original), "Sneaking did not reverse rotation");
        level.setBlock(pos, Blocks.DIRT.defaultBlockState(), Block.UPDATE_ALL);
        int damage = wrench.getDamageValue();
        test.assertTrue(GCItems.STANDARD_WRENCH.useOn(context(test, player, wrench, pos)) == InteractionResult.PASS,
                "Unsupported block consumed the interaction");
        test.assertTrue(wrench.getDamageValue() == damage, "Unsupported block damaged the wrench");
        test.assertTrue(GCItems.STANDARD_WRENCH.useOn(context(test, null, wrench, pos)) == InteractionResult.PASS,
                "Missing player consumed the interaction");
        player.discard();
        test.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void pairedBlocksAndAllChamberPartsRotateTogether(GameTestHelper test) {
        var player = makeSurvivalServerPlayer(test);
        var wrench = new ItemStack(GCItems.STANDARD_WRENCH);
        player.setItemInHand(InteractionHand.MAIN_HAND, wrench);
        var level = test.getLevel();
        BlockPos pos = test.absolutePos(new BlockPos(1, 2, 1));
        var chest = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH).setValue(ChestBlock.TYPE, ChestType.LEFT);
        BlockPos other = pos.relative(ChestBlock.getConnectedDirection(chest));
        level.setBlock(pos, chest, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        level.setBlock(other, chest.setValue(ChestBlock.TYPE, ChestType.RIGHT), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        use(test, player, wrench, pos);
        test.assertTrue(level.getBlockState(pos).getValue(ChestBlock.FACING) == Direction.SOUTH
                && level.getBlockState(pos).getValue(ChestBlock.TYPE) == ChestType.RIGHT
                && level.getBlockState(other).getValue(ChestBlock.FACING) == Direction.SOUTH
                && level.getBlockState(other).getValue(ChestBlock.TYPE) == ChestType.LEFT, "Double chest did not swap its halves");
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(other, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(pos.south().below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        var bed = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.FOOT);
        other = pos.south();
        level.setBlock(pos, bed, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        level.setBlock(other, bed.setValue(BedBlock.PART, BedPart.HEAD), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        use(test, player, wrench, pos);
        test.assertTrue(level.getBlockState(pos).getValue(BedBlock.FACING) == Direction.NORTH
                && level.getBlockState(pos).getValue(BedBlock.PART) == BedPart.HEAD
                && level.getBlockState(other).getValue(BedBlock.PART) == BedPart.FOOT, "Bed did not swap its halves");
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(other, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        for (int clickedPart = 0; clickedPart < 3; clickedPart++) {
            level.setBlock(pos, GCBlocks.CRYOGENIC_CHAMBER.defaultBlockState().setValue(CryogenicChamberBlock.FACING, Direction.NORTH), Block.UPDATE_CLIENTS);
            level.setBlock(pos.above(), GCBlocks.CRYOGENIC_CHAMBER_PART.defaultBlockState().setValue(CryogenicChamberBlock.FACING, Direction.NORTH), Block.UPDATE_CLIENTS);
            level.setBlock(pos.above(2), GCBlocks.CRYOGENIC_CHAMBER_PART.defaultBlockState().setValue(CryogenicChamberBlock.FACING, Direction.NORTH).setValue(CryogenicChamberPart.TOP, true), Block.UPDATE_CLIENTS);
            use(test, player, wrench, pos.above(clickedPart));
            Direction facing = level.getBlockState(pos).getValue(CryogenicChamberBlock.FACING);
            test.assertTrue(facing != Direction.NORTH && level.getBlockState(pos.above()).getValue(CryogenicChamberBlock.FACING) == facing
                    && level.getBlockState(pos.above(2)).getValue(CryogenicChamberBlock.FACING) == facing, "Chamber rotation missed a part when clicking " + clickedPart);
        }
        player.discard();
        test.succeed();
    }

    private static UseOnContext context(GameTestHelper test, ServerPlayer player, ItemStack wrench, BlockPos pos) {
        return new UseOnContext(test.getLevel(), player, InteractionHand.MAIN_HAND, wrench,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
    }

    private static void use(GameTestHelper test, ServerPlayer player, ItemStack wrench, BlockPos pos) {
        int damage = wrench.getDamageValue();
        test.assertTrue(GCItems.STANDARD_WRENCH.useOn(context(test, player, wrench, pos)) == InteractionResult.SUCCESS,
                "Wrench did not handle " + test.getLevel().getBlockState(pos));
        test.assertTrue(wrench.getDamageValue() == damage + 1, "Rotation must damage the wrench exactly once");
    }
}
