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

import dev.galacticraft.mod.content.GCBlocks;
import dev.galacticraft.mod.content.block.entity.machine.OxygenSealerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

public final class OxygenSealerTestSuite implements GalacticraftGameTest {
    @GameTest(template = EMPTY_STRUCTURE)
    public void blockChangeInvalidatesSealedState(GameTestHelper context) {
        BlockPos sealerPos = new BlockPos(0, 1, 0);
        BlockPos changedPos = new BlockPos(1, 1, 0);
        boolean defaultBreathable = context.getLevel().getDefaultBreathable();

        context.getLevel().setDefaultBreathable(false);
        try {
            context.setBlock(sealerPos, GCBlocks.OXYGEN_SEALER);
            OxygenSealerBlockEntity sealer = context.getBlockEntity(sealerPos);
            sealer.setSealed(true);

            context.setBlock(changedPos, Blocks.STONE);
            if (sealer.isSealed()) {
                context.fail("A changed block did not invalidate the oxygen sealer", changedPos);
            } else {
                context.succeed();
            }
        } finally {
            context.setBlock(sealerPos, Blocks.AIR);
            context.getLevel().setDefaultBreathable(defaultBreathable);
        }
    }
}
