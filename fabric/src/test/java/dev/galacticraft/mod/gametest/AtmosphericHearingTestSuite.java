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

import dev.galacticraft.mod.world.atmosphere.AtmosphericDensity;
import dev.galacticraft.mod.world.atmosphere.VibrationAcoustics;
import dev.galacticraft.mod.world.dimension.GCDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SculkSensorBlockEntity;
import net.minecraft.world.level.gameevent.EntityPositionSource;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.vibrations.VibrationSelector;
import net.minecraft.world.phys.Vec3;

import java.util.function.BiConsumer;

public final class AtmosphericHearingTestSuite implements GalacticraftGameTest {
    // Far from spawn and every other suite's fixtures.
    private static final int CHUNK = 1900;

    @GameTest(template = EMPTY_STRUCTURE)
    public void earthAirHearsAtVanillaRange(GameTestHelper context) {
        inRig(context, Level.OVERWORLD, (level, sensor) -> {
            context.assertTrue(hears(level, sensor, sensor.above(4)), "Earth air should carry 4 blocks");
            context.assertTrue(!hears(level, sensor, sensor.east(18)), "Earth air should not carry past vanilla's 8");
        });
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void theMoonOnlyHearsThroughContact(GameTestHelper context) {
        inRig(context, GCDimensions.MOON, (level, sensor) -> {
            context.assertTrue(!hears(level, sensor, sensor.above(4)), "a vacuum should carry nothing through the air");
            context.assertTrue(hears(level, sensor, sensor.east()), "footsteps on the sensor's floor should conduct");
            context.assertTrue(!hears(level, sensor, sensor.east(2)), "footsteps two blocks off should not conduct");
        });
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void aMoonHabitatHearsLikeEarth(GameTestHelper context) {
        inRig(context, GCDimensions.MOON, (level, sensor) -> {
            BlockPos source = sensor.above(4);
            level.setBreathable(sensor, true);
            level.setBreathable(source, true);
            try {
                context.assertTrue(hears(level, sensor, source), "habitat air should carry 4 blocks");
                level.setBreathable(source, false);
                context.assertTrue(!hears(level, sensor, source), "sound from vacuum should not reach into a habitat");
            } finally {
                level.setBreathable(sensor, false);
                level.setBreathable(source, false);
            }
        });
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void marsThinAirFallsShort(GameTestHelper context) {
        inRig(context, GCDimensions.MARS, (level, sensor) -> {
            context.assertTrue(hears(level, sensor, sensor.above(2)), "Mars air should still carry 2 blocks");
            context.assertTrue(!hears(level, sensor, sensor.above(3)), "Mars air should not carry 3 blocks");
        });
    }

    // 18 blocks is past the dispatcher's vanilla 16-block search, so this also covers widening it.
    @GameTest(template = EMPTY_STRUCTURE)
    public void venusThickAirCarriesFurtherAndStronger(GameTestHelper context) {
        inRig(context, GCDimensions.VENUS, (level, sensor) -> {
            context.assertTrue(hears(level, sensor, sensor.east(18)), "Venus air should carry 18 blocks");
            context.assertTrue(!hears(level, sensor, sensor.east(22)), "Venus air should not carry 22 blocks");
            float felt = VibrationAcoustics.perceivedDistance(level, sensor.east(10), sensor, sensor, 10.0F, 8);
            context.assertTrue(felt < 5.0F, "10 blocks of Venus air should feel under 5, felt " + felt);
        });
    }

    // Pins the elytra numbers to their values from before they were shared with sculk.
    @GameTest(template = EMPTY_STRUCTURE)
    public void elytraReadTheSameAir(GameTestHelper context) {
        var server = context.getLevel().getServer();
        ServerLevel moon = server.getLevel(GCDimensions.MOON);
        ServerLevel mars = server.getLevel(GCDimensions.MARS);
        ServerLevel venus = server.getLevel(GCDimensions.VENUS);
        context.assertTrue(moon != null && mars != null && venus != null, "planet levels should be loaded");
        context.assertTrue(AtmosphericDensity.isVacuum(moon) && !AtmosphericDensity.isVacuum(mars), "vacuum tag");
        context.assertTrue(AtmosphericDensity.elytraDrag(venus) == 0.02F, "Venus drag should sit at the 0.02 cap");
        context.assertTrue(AtmosphericDensity.elytraDrag(mars) == 0.006F / 2000.0F, "Mars drag " + AtmosphericDensity.elytraDrag(mars));
        context.assertTrue(VibrationAcoustics.rangeMultiplier(AtmosphericDensity.of(moon)) == 0.0F, "Moon range should be zero");
        context.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void entitiesTouchTheWorldAtTheirFeet(GameTestHelper context) {
        Zombie zombie = context.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(1.5, 2, 1.5));
        EntityPositionSource source = new EntityPositionSource(zombie, zombie.getEyeHeight());
        Vec3 eyes = source.getPosition(context.getLevel()).orElseThrow();
        context.assertTrue(VibrationAcoustics.anchor(source, eyes).equals(zombie.blockPosition()), "anchor should be the feet block");
        context.succeed();
    }

    private static void inRig(GameTestHelper context, ResourceKey<Level> key, BiConsumer<ServerLevel, BlockPos> body) {
        ServerLevel level = context.getLevel().getServer().getLevel(key);
        context.assertTrue(level != null, key.location() + " should be loaded");
        for (int dx = -1; dx <= 2; dx++) level.setChunkForced(CHUNK + dx, CHUNK, true);
        BlockPos sensor = new BlockPos((CHUNK << 4) + 8, level.getMaxBuildHeight() - 24, (CHUNK << 4) + 8);
        try {
            for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) level.setBlockAndUpdate(sensor.offset(x, -1, z), Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(sensor, Blocks.SCULK_SENSOR.defaultBlockState());
            body.accept(level, sensor);
            context.succeed();
        } finally {
            level.setBlockAndUpdate(sensor, Blocks.AIR.defaultBlockState());
            for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) level.setBlockAndUpdate(sensor.offset(x, -1, z), Blocks.AIR.defaultBlockState());
            for (int dx = -1; dx <= 2; dx++) level.setChunkForced(CHUNK + dx, CHUNK, false);
        }
    }

    // Reads the candidate straight off the selector, before the sensor's next tick consumes it.
    private static boolean hears(ServerLevel level, BlockPos sensor, BlockPos source) {
        VibrationSelector selector = ((SculkSensorBlockEntity) level.getBlockEntity(sensor)).getVibrationData().getSelectionStrategy();
        selector.startOver();
        level.gameEvent(GameEvent.HIT_GROUND, Vec3.atBottomCenterOf(source), GameEvent.Context.of((Entity) null));
        boolean heard = selector.chosenCandidate(Long.MAX_VALUE).isPresent();
        selector.startOver();
        return heard;
    }
}
