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

package dev.galacticraft.mod.content.block.entity.machine;

import dev.galacticraft.mod.content.GCBlocks;
import dev.galacticraft.mod.gametest.GalacticraftGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class OxygenCollectorGameTest implements GalacticraftGameTest {
    @GameTest(template = EMPTY_STRUCTURE)
    public void nearbyLeavesControlCollectionRate(GameTestHelper context) {
        BlockPos collectorPos = new BlockPos(6, 3, 3);

        try {
            context.setBlock(collectorPos, GCBlocks.OXYGEN_COLLECTOR);
            OxygenCollectorBlockEntity collector = context.getBlockEntity(collectorPos);
            BlockPos absoluteCollectorPos = context.absolutePos(collectorPos);

            context.assertValueEqual(collector.collectOxygen(context.getLevel(), absoluteCollectorPos, false), 0, "airless collection rate without leaves");

            BlockState playerPlacedLeaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
            for (int index = 0; index < 28; index++) {
                int x = 1 + index % 5;
                int y = 1 + (index / 5) % 3;
                int z = 1 + index / 15;
                context.setBlock(new BlockPos(x, y, z), playerPlacedLeaves);

                if (index == 13) {
                    context.assertValueEqual(collector.collectOxygen(context.getLevel(), absoluteCollectorPos, false), 20, "airless collection rate with 14 leaves");
                }
            }

            context.assertValueEqual(collector.collectOxygen(context.getLevel(), absoluteCollectorPos, false), 42, "airless collection rate with 28 leaves");
            context.assertValueEqual(collector.collectOxygen(context.getLevel(), absoluteCollectorPos, true), 186, "atmospheric collection rate");
            context.succeed();
        } finally {
            context.setBlock(collectorPos, Blocks.AIR);
        }
    }
}
