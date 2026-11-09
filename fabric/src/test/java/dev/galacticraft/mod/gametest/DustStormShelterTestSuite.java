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

import dev.galacticraft.mod.Galacticraft;
import dev.galacticraft.mod.world.dimension.duststorm.MarsDustStormManager;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.Blocks;

/**
 * Checks storm hazards against roof, cave, and breathable-air shelters (issue #21). Runs the
 * hazards directly in a vacuum because GameTest does not create Mars. Plots have sky access
 * so barrier roofs cannot accidentally shelter the outdoor control.
 */
public final class DustStormShelterTestSuite implements GalacticraftGameTest {
    /** Breathability belongs to the whole level, so these hold it in vacuum for as long as any of them runs. */
    private static final String BATCH = "galacticraft:dust_storm_shelter";
    /** Thick enough for darkness and slowness, short of the peak damage that only lands on some ticks. */
    private static final float INTENSITY = 0.8F;
    /** Where the player stands, on a stone floor. */
    private static final BlockPos FEET = new BlockPos(1, 1, 1);
    /** Two blocks over the player's head. */
    private static final BlockPos ROOF = FEET.above(3);
    private static boolean levelWasBreathable = true;

    @BeforeBatch(batch = BATCH)
    public void emptyTheAir(ServerLevel level) {
        levelWasBreathable = level.getDefaultBreathable();
        level.setDefaultBreathable(false);
    }

    @AfterBatch(batch = BATCH)
    public void putTheAirBack(ServerLevel level) {
        level.setDefaultBreathable(levelWasBreathable);
    }

    /** The control: out in the open the storm has to reach the player, or the shelter tests prove nothing. */
    @GameTest(template = EMPTY_STRUCTURE, batch = BATCH, skyAccess = true)
    public void aPlayerInTheOpenIsCaughtByTheStorm(GameTestHelper context) {
        ServerPlayer player = standInTheStorm(context);
        try {
            MarsDustStormManager.applyPlayerEffects(player, context.getLevel(), INTENSITY, Galacticraft.CONFIG);
            if (!player.hasEffect(MobEffects.DARKNESS)) {
                context.fail("the storm did not darken the view of a player in the open, so the shelter tests prove nothing");
            } else if (!player.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)) {
                context.fail("the storm did not slow a player in the open, so the shelter tests prove nothing");
            } else {
                context.succeed();
            }
        } finally {
            leave(context, player);
        }
    }

    /**
     * Rock overhead is what shelters a player down a cave, and a roof does the same for a shed above ground,
     * with no seal and no air under either.
     */
    @GameTest(template = EMPTY_STRUCTURE, batch = BATCH, skyAccess = true)
    public void aRoofKeepsTheStormOff(GameTestHelper context) {
        context.setBlock(ROOF, Blocks.STONE);
        ServerPlayer player = standInTheStorm(context);
        try {
            MarsDustStormManager.applyPlayerEffects(player, context.getLevel(), INTENSITY, Galacticraft.CONFIG);
            if (player.hasEffect(MobEffects.DARKNESS) || player.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)) {
                context.fail("the dust storm reached a player standing under a roof");
            } else {
                context.succeed();
            }
        } finally {
            leave(context, player);
        }
    }

    /** A sealed, breathable space keeps the dust out too, with nothing overhead at all. */
    @GameTest(template = EMPTY_STRUCTURE, batch = BATCH, skyAccess = true)
    public void breathableAirKeepsTheStormOff(GameTestHelper context) {
        ServerPlayer player = standInTheStorm(context);
        BlockPos eyes = BlockPos.containing(player.getEyePosition());
        context.getLevel().setBreathable(eyes, true);
        try {
            MarsDustStormManager.applyPlayerEffects(player, context.getLevel(), INTENSITY, Galacticraft.CONFIG);
            if (player.hasEffect(MobEffects.DARKNESS) || player.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)) {
                context.fail("the dust storm reached a player breathing sealed air");
            } else {
                context.succeed();
            }
        } finally {
            context.getLevel().setBreathable(eyes, false);
            leave(context, player);
        }
    }

    /** A survival player, so the storm does not pass over them as it does a creative one. */
    private ServerPlayer standInTheStorm(GameTestHelper context) {
        context.setBlock(FEET.below(), Blocks.STONE);
        ServerPlayer player = makeSurvivalServerPlayer(context);
        BlockPos feet = context.absolutePos(FEET);
        player.moveTo(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, 0.0F, 0.0F);
        return player;
    }

    private static void leave(GameTestHelper context, ServerPlayer player) {
        context.getLevel().getServer().getPlayerList().remove(player);
    }
}
