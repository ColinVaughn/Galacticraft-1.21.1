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

public final class OxygenBubbleDistributorGameTest implements GalacticraftGameTest {
    @GameTest(template = EMPTY_STRUCTURE)
    public void defaultBubbleProtectsAPlayerBesideIt(GameTestHelper context) {
        BlockPos distributorPos = new BlockPos(4, 2, 4);
        boolean levelWasBreathable = context.getLevel().getDefaultBreathable();

        try {
            context.getLevel().setDefaultBreathable(false);
            context.setBlock(distributorPos, GCBlocks.OXYGEN_BUBBLE_DISTRIBUTOR);
            OxygenBubbleDistributorBlockEntity distributor = context.getBlockEntity(distributorPos);
            distributor.distributeOxygenToArea(1, true);
            BlockPos eyePos = context.absolutePos(distributorPos.offset(1, 1, 0));
            context.assertTrue(context.getLevel().isBreathable(eyePos), "the default bubble did not reach a nearby player's eye");
            context.succeed();
        } finally {
            OxygenBubbleDistributorBlockEntity distributor = context.getBlockEntity(distributorPos);
            if (distributor != null) distributor.distributeOxygenToArea(1, false);
            context.setBlock(distributorPos, Blocks.AIR);
            context.getLevel().setDefaultBreathable(levelWasBreathable);
        }
    }
}
