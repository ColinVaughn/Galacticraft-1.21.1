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

package dev.galacticraft.mod.content.block.entity;

import dev.galacticraft.mod.content.GCBlocks;
import dev.galacticraft.mod.content.block.special.AirlockSealBlock;
import dev.galacticraft.mod.gametest.GalacticraftGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

public class AirlockSealingGameTest implements GalacticraftGameTest {
    @GameTest(template = EMPTY_STRUCTURE)
    public void allThreeFramePlanesSealWithTheCorrectFacingAndPreserveSolidContents(GameTestHelper test) {
        BlockPos origin = new BlockPos(1, 1, 1);
        for (Direction normal : new Direction[]{Direction.UP, Direction.EAST, Direction.NORTH}) {
            for (int x = 0; x <= 4; x++) for (int y = 0; y <= 4; y++) for (int z = 0; z <= 4; z++) {
                test.setBlock(origin.offset(x, y, z), Blocks.AIR);
            }
            Direction across = normal == Direction.EAST ? Direction.SOUTH : Direction.EAST;
            Direction along = normal == Direction.UP ? Direction.SOUTH : Direction.UP;
            for (int u = 0; u <= 4; u++) for (int v = 0; v <= 4; v++) {
                if (u == 0 || u == 4 || v == 0 || v == 4) test.setBlock(origin.relative(across, u).relative(along, v), GCBlocks.AIR_LOCK_FRAME);
            }
            test.setBlock(origin, GCBlocks.AIR_LOCK_CONTROLLER);
            BlockPos stone = origin.relative(across).relative(along);
            BlockPos water = origin.relative(across, 2).relative(along);
            test.setBlock(stone, Blocks.STONE);
            test.setBlock(water, Blocks.WATER);
            AirlockControllerBlockEntity controller = test.getBlockEntity(origin);
            controller.playerDistanceActivation = false;
            controller.horizontalModeEnabled = controller.lastHorizontalModeEnabled = normal == Direction.UP;
            for (int tick = 0; tick < 5; tick++) controller.tick();
            for (int u = 1; u < 4; u++) for (int v = 1; v < 4; v++) {
                BlockPos pos = origin.relative(across, u).relative(along, v);
                if (pos.equals(stone) || pos.equals(water)) continue;
                var state = test.getBlockState(pos);
                test.assertTrue(state.is(GCBlocks.AIR_LOCK_SEAL) && state.getValue(AirlockSealBlock.FACING) == normal, "Wrong seal or facing for " + normal);
            }
            test.assertTrue(test.getBlockState(stone).is(Blocks.STONE) && test.getBlockState(water).is(Blocks.WATER), "Sealing replaced non-air contents");
            controller.invertSelection = true;
            for (int tick = 0; tick < 5; tick++) controller.tick();
            test.assertTrue(test.getBlockState(origin.relative(across, 2).relative(along, 2)).isAir(), "Airlock failed to reopen");
            test.assertTrue(test.getBlockState(stone).is(Blocks.STONE) && test.getBlockState(water).is(Blocks.WATER), "Reopening removed non-seal blocks");
        }
        test.succeed();
    }
}
