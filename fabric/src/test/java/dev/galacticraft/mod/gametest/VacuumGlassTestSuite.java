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

package dev.galacticraft.mod.gametest;

import dev.galacticraft.mod.content.GCBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Checks vacuum glass connection updates from pistons, whose notifications can originate at the
 * block itself instead of a neighboring position.
 */
public final class VacuumGlassTestSuite implements GalacticraftGameTest {
    /**
     * What a piston does once it has pushed vacuum glass into place. The glass still carries the
     * connections it had where it came from, so this is its one chance to look round its new spot.
     */
    @GameTest(template = EMPTY_STRUCTURE)
    public void vacuumGlassToldAboutItselfChecksEverySide(GameTestHelper context) {
        BlockPos glassPos = new BlockPos(2, 2, 2);
        context.setBlock(glassPos.east(), Blocks.STONE);
        context.setBlock(glassPos.west(), GCBlocks.VACUUM_GLASS);
        // Joined to the east, where there is only stone now - as if it had been pushed away from a pane.
        context.setBlock(glassPos, GCBlocks.VACUUM_GLASS.defaultBlockState().setValue(BlockStateProperties.EAST, true));

        BlockPos absolute = context.absolutePos(glassPos);
        context.getLevel().neighborChanged(absolute, GCBlocks.VACUUM_GLASS, absolute);

        BlockState glass = context.getBlockState(glassPos);
        if (glass.getValue(BlockStateProperties.EAST)) {
            context.fail("vacuum glass kept a connection to the stone beside it");
        } else if (!glass.getValue(BlockStateProperties.WEST)) {
            context.fail("vacuum glass lost its connection to the pane beside it");
        } else {
            context.succeed();
        }
    }

    /** The real thing: pushed east, out from beside the pane it was joined to on its north side. */
    @GameTest(template = EMPTY_STRUCTURE)
    public void vacuumGlassPushedByAPistonLetsGoOfThePaneItLeft(GameTestHelper context) {
        BlockPos glassPos = new BlockPos(2, 2, 2);
        BlockPos pushedTo = glassPos.east();
        context.setBlock(glassPos.north(), GCBlocks.VACUUM_GLASS);
        context.setBlock(glassPos, GCBlocks.VACUUM_GLASS);
        if (!context.getBlockState(glassPos).getValue(BlockStateProperties.NORTH)) {
            context.fail("the glass never joined the pane beside it, so this proves nothing");
            return;
        }

        context.setBlock(glassPos.west(), Blocks.PISTON.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.EAST));
        context.setBlock(glassPos.west(2), Blocks.REDSTONE_BLOCK);
        context.succeedWhen(() -> {
            context.assertBlockPresent(GCBlocks.VACUUM_GLASS, pushedTo);
            context.assertBlockProperty(pushedTo, BlockStateProperties.NORTH, false);
        });
    }
}
