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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Fire needs oxygen. A torch placed where there is none must snuff out into its unlit variant,
 * as it does in Galacticraft Legacy, instead of burning happily in a vacuum.
 */
public final class VacuumTorchTestSuite implements GalacticraftGameTest {
    @GameTest(template = EMPTY_STRUCTURE)
    public void torchPlacedInVacuumIsExtinguished(GameTestHelper context) {
        BlockPos floor = new BlockPos(0, 1, 0);
        BlockPos torchPos = new BlockPos(0, 2, 0);
        boolean defaultBreathable = context.getLevel().getDefaultBreathable();

        context.getLevel().setDefaultBreathable(false);
        try {
            context.setBlock(floor, Blocks.STONE);
            context.setBlock(torchPos, Blocks.TORCH);

            BlockState placed = context.getBlockState(torchPos);
            if (placed.is(Blocks.TORCH)) {
                context.fail("a torch placed in vacuum stayed lit", torchPos);
            } else if (!placed.is(GCBlocks.UNLIT_TORCH)) {
                context.fail("expected an unlit torch but found " + placed, torchPos);
            } else {
                context.succeed();
            }
        } finally {
            context.setBlock(torchPos, Blocks.AIR);
            context.setBlock(floor, Blocks.AIR);
            context.getLevel().setDefaultBreathable(defaultBreathable);
        }
    }

    /**
     * The scenario from the bug report: a player putting a torch on a wall by hand. Placement runs
     * through {@link net.minecraft.world.item.BlockItem}, not a direct block set, so it is worth
     * covering separately.
     */
    @GameTest(template = EMPTY_STRUCTURE)
    public void torchPlacedByAPlayerInVacuumIsExtinguished(GameTestHelper context) {
        BlockPos floor = new BlockPos(0, 1, 0);
        BlockPos torchPos = floor.above();
        boolean defaultBreathable = context.getLevel().getDefaultBreathable();

        context.getLevel().setDefaultBreathable(false);
        try {
            context.setBlock(floor, Blocks.STONE);

            ServerPlayer player = context.makeMockServerPlayerInLevel();
            player.setGameMode(GameType.CREATIVE);
            ItemStack torch = new ItemStack(Items.TORCH);

            BlockPos absoluteFloor = context.absolutePos(floor);
            BlockHitResult hit = new BlockHitResult(
                    Vec3.atCenterOf(absoluteFloor).add(0.0, 0.5, 0.0), Direction.UP, absoluteFloor, false);
            torch.useOn(new UseOnContext(context.getLevel(), player, InteractionHand.MAIN_HAND, torch, hit));

            BlockState placed = context.getBlockState(torchPos);
            if (placed.is(Blocks.TORCH)) {
                context.fail("a player-placed torch stayed lit in vacuum", torchPos);
            } else if (!placed.is(GCBlocks.UNLIT_TORCH)) {
                context.fail("expected an unlit torch but found " + placed, torchPos);
            } else {
                context.succeed();
            }
        } finally {
            context.setBlock(torchPos, Blocks.AIR);
            context.setBlock(floor, Blocks.AIR);
            context.getLevel().setDefaultBreathable(defaultBreathable);
        }
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void torchPlacedInBreathableAirStaysLit(GameTestHelper context) {
        BlockPos floor = new BlockPos(0, 1, 0);
        BlockPos torchPos = new BlockPos(0, 2, 0);
        boolean defaultBreathable = context.getLevel().getDefaultBreathable();

        context.getLevel().setDefaultBreathable(true);
        try {
            context.setBlock(floor, Blocks.STONE);
            context.setBlock(torchPos, Blocks.TORCH);

            if (!context.getBlockState(torchPos).is(Blocks.TORCH)) {
                context.fail("a torch in breathable air was wrongly extinguished", torchPos);
            } else {
                context.succeed();
            }
        } finally {
            context.setBlock(torchPos, Blocks.AIR);
            context.setBlock(floor, Blocks.AIR);
            context.getLevel().setDefaultBreathable(defaultBreathable);
        }
    }
}
