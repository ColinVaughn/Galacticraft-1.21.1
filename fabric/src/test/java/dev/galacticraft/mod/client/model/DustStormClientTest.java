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

package dev.galacticraft.mod.client.model;

import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.Galacticraft;
import dev.galacticraft.mod.client.render.dimension.MarsDimensionEffects;
import dev.galacticraft.mod.client.render.dimension.SpaceflightTransition;
import dev.galacticraft.mod.client.render.dimension.duststorm.ClientDustStorms;
import dev.galacticraft.mod.client.sounds.DustStormSound;
import dev.galacticraft.mod.util.DimensionTime;
import dev.galacticraft.mod.world.dimension.GCDimensions;
import dev.galacticraft.mod.world.dimension.duststorm.DustStormTuning;
import dev.galacticraft.mod.world.dimension.duststorm.MarsDustStormManager;
import dev.galacticraft.mod.world.dimension.duststorm.MarsDustStormState;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.particle.DustParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

import java.lang.reflect.Field;
import java.util.*;

/**
 * Checks dust, fog, wind, and screen haze outdoors, under cover, underground, and in breathable air
 * (issue #21). Fixtures sit above generated terrain to isolate shelter effects.
 * Run with {@code gradlew :fabric:runClient -PdustStormClientTest -Prei=false -Pastrocraft=false}.
 */
public final class DustStormClientTest implements ClientModInitializer {
    /** Mars generates nothing this high, so the terrain never shelters a spot. */
    private static final int FLOOR_Y = 259;
    /** Long enough for chunks, light and the wind's easing to settle after each move. */
    private static final int SETTLE_TICKS = 120, MEASURE_TICKS = 40;
    /** The shed's roof, two blocks over the player's head and wider than the dust spawns around the camera. */
    private static final int ROOF_Y = FLOOR_Y + 4, ROOF_HALF = 14;
    /** A closed stone shell standing in for deep rock: no sky light inside, and its top far overhead. */
    private static final int SHELL_BOTTOM = 240, SHELL_TOP = 300, SHELL_HALF = 13;
    /** A breathable space open to the sky, as under a bubble distributor. */
    private static final int AIR_HALF = 12, AIR_BOTTOM = FLOOR_Y - 4, AIR_TOP = FLOOR_Y + 13;

    private enum Spot {
        OPEN(0, FLOOR_Y + 1), ROOFED(64, FLOOR_Y + 1), UNDERGROUND(128, SHELL_BOTTOM + 1), BREATHABLE(192, FLOOR_Y + 1);

        final int x, feetY;

        Spot(int x, int feetY) {
            this.x = x;
            this.feetY = feetY;
        }

        /** Whether a dust mote at this position is somewhere the storm should not reach. */
        boolean sheltered(double px, double py, double pz) {
            double dx = px - (this.x + 0.5), dz = pz - 0.5;
            return switch (this) {
                case OPEN -> false;
                case ROOFED -> py < ROOF_Y && Math.abs(dx) <= ROOF_HALF && Math.abs(dz) <= ROOF_HALF;
                case UNDERGROUND -> true;
                case BREATHABLE -> py >= AIR_BOTTOM && py < AIR_TOP + 1 && Math.abs(dx) <= AIR_HALF + 0.5 && Math.abs(dz) <= AIR_HALF + 0.5;
            };
        }
    }

    private record Result(int dust, int shelteredDust, int foggyTicks, float wind) {
    }

    private int stage, spot, ticks, timeout;
    private final Map<Spot, Result> results = new EnumMap<>(Spot.class);
    private final Set<Particle> seen = Collections.newSetFromMap(new IdentityHashMap<>());
    private int shelteredDust, foggyTicks;
    private Field particles, x, y, z, active, volume;

    @Override
    public void onInitializeClient() {
        if (Boolean.getBoolean("galacticraft.dust-storm.client-test")) ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private void tick(Minecraft client) {
        if (++timeout > 6000) throw new AssertionError("Dust storm check timed out in stage " + stage + " at " + Spot.values()[spot]);
        if (stage >= 2 && client.screen instanceof PauseScreen) client.setScreen(null);
        if (client.getOverlay() != null || SpaceflightTransition.isActive()) return;
        if (stage == 0) {
            if (client.screen == null) return;
            stage = 1;
            org.lwjgl.glfw.GLFW.glfwHideWindow(client.getWindow().getWindow());
            org.lwjgl.glfw.GLFW.glfwSetWindowSize(client.getWindow().getWindow(), 1280, 720);
            client.options.renderDistance().set(6);
            client.options.particles().set(ParticleStatus.ALL);
            client.options.pauseOnLostFocus = false;
            client.options.hideGui = false;
            client.options.tutorialStep = TutorialSteps.NONE;
            client.createWorldOpenFlows().createFreshLevel("dust-storm-" + System.currentTimeMillis(),
                    new LevelSettings("Dust Storm Check", GameType.CREATIVE, false, Difficulty.PEACEFUL,
                            true, new GameRules(), WorldDataConfiguration.DEFAULT), new WorldOptions(73, false, false),
                    registries -> registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT)
                            .value().createWorldDimensions(), client.screen);
            return;
        }
        if (client.player == null || client.level == null || client.getSingleplayerServer() == null) return;
        if (stage == 1) {
            if (client.screen != null) return;
            client.options.setCameraType(CameraType.FIRST_PERSON);
            client.getSingleplayerServer().execute(() -> {
                ServerLevel mars = client.getSingleplayerServer().getLevel(GCDimensions.MARS);
                if (mars == null) throw new AssertionError("The integrated server has no Mars");
                mars.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, mars.getServer());
                mars.setDayTime(DimensionTime.sameTimeOfDay(6000, DimensionTime.dayLength(mars)));
                build(mars);
            });
            stage = 2;
            moveTo(client, Spot.values()[spot]);
            return;
        }

        Spot here = Spot.values()[spot];
        if (!client.level.dimension().equals(GCDimensions.MARS) || client.screen != null) return;
        if (Math.abs(client.player.getX() - (here.x + 0.5)) > 1 || Math.abs(client.player.getY() - here.feetY) > 1) return;
        if (stage == 2) {
            // The storm eases in on the client; only measure once it is at full strength.
            if (++ticks < SETTLE_TICKS || ClientDustStorms.intensity() < 0.9F) return;
            stage = 3;
            ticks = 0;
            seen.clear();
            shelteredDust = 0;
            foggyTicks = 0;
            return;
        }
        if (stage != 3) return;

        measure(client, here);
        if (MarsDimensionEffects.INSTANCE.isFoggyAt((int) Math.floor(client.player.getX()), (int) Math.floor(client.player.getEyeY()))) foggyTicks++;
        if (ticks == MEASURE_TICKS / 2) {
            Screenshot.grab(client.gameDirectory, "dust-storm-" + here.name().toLowerCase(Locale.ROOT) + ".png", client.getMainRenderTarget(), ignored -> {});
        }
        if (++ticks < MEASURE_TICKS) return;

        Result result = new Result(seen.size(), shelteredDust, foggyTicks, wind());
        results.put(here, result);
        Constant.LOGGER.info("DUST STORM RESULT {}: {} dust motes, {} where the storm should not reach, fog on {}/{} ticks, wind volume {}",
                here, result.dust(), result.shelteredDust(), result.foggyTicks(), MEASURE_TICKS, String.format("%.3f", result.wind()));
        if (++spot < Spot.values().length) {
            stage = 2;
            ticks = 0;
            moveTo(client, Spot.values()[spot]);
            return;
        }

        List<String> failures = new ArrayList<>();
        Result open = results.get(Spot.OPEN), roofed = results.get(Spot.ROOFED),
                underground = results.get(Spot.UNDERGROUND), breathable = results.get(Spot.BREATHABLE);
        // The controls: in the open everything is at full strength, or the sheltered spots prove nothing.
        if (open.dust() < 100) failures.add("too little dust in the open (" + open.dust() + ")");
        if (open.foggyTicks() < MEASURE_TICKS) failures.add("no thick fog in the open");
        if (open.wind() < 0.8F) failures.add("the wind was quiet in the open (" + open.wind() + ")");

        if (roofed.shelteredDust() > 0) failures.add(roofed.shelteredDust() + " dust motes under the shed roof");
        if (roofed.foggyTicks() > 0) failures.add("thick fog under the shed roof");
        if (roofed.wind() < 0.1F || roofed.wind() > 0.6F) failures.add("the wind under the shed roof was not muffled to between 0.1 and 0.6 (" + roofed.wind() + ")");

        if (underground.dust() > 0) failures.add(underground.dust() + " dust motes deep under rock");
        if (underground.foggyTicks() > 0) failures.add("thick fog deep under rock");
        if (underground.wind() > 0.05F) failures.add("the wind was still audible deep under rock (" + underground.wind() + ")");

        if (breathable.shelteredDust() > 0) failures.add(breathable.shelteredDust() + " dust motes inside breathable air");
        if (breathable.foggyTicks() < MEASURE_TICKS) failures.add("no thick fog seen from breathable air under open sky");
        if (breathable.wind() < 0.2F || breathable.wind() > 0.32F) failures.add("the wind in breathable air was not muffled to about 0.3 (" + breathable.wind() + ")");

        if (!failures.isEmpty()) throw new AssertionError("Dust storm reached where it should not: " + String.join("; ", failures));
        Constant.LOGGER.info("DUST STORM CLIENT CHECK PASSED");
        client.stop();
        stage = 4;
    }

    /** Puts the player on a spot and keeps the storm at its peak while they are there. */
    private static void moveTo(Minecraft client, Spot spot) {
        client.getSingleplayerServer().execute(() -> {
            ServerLevel mars = client.getSingleplayerServer().getLevel(GCDimensions.MARS);
            var config = Galacticraft.CONFIG;
            MarsDustStormState state = MarsDustStormState.get(mars);
            state.debugStart(mars.random, new DustStormTuning(true, config.dustStormMeanInterval(), config.dustStormMinDuration(),
                    config.dustStormMaxDuration(), config.dustStormIntensity()), 1.0F);
            MarsDustStormManager.sync(mars, state);
            var player = client.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
            player.teleportTo(mars, spot.x + 0.5, spot.feetY, 0.5, 0.0F, 0.0F);
        });
    }

    private static void build(ServerLevel mars) {
        BlockState stone = Blocks.STONE.defaultBlockState();
        for (Spot spot : new Spot[]{Spot.OPEN, Spot.ROOFED, Spot.BREATHABLE}) {
            fill(mars, spot.x - 2, FLOOR_Y, -2, spot.x + 2, FLOOR_Y, 2, stone);
        }
        fill(mars, Spot.ROOFED.x - ROOF_HALF, ROOF_Y, -ROOF_HALF, Spot.ROOFED.x + ROOF_HALF, ROOF_Y, ROOF_HALF, stone);

        int ux = Spot.UNDERGROUND.x;
        for (int bx = ux - SHELL_HALF; bx <= ux + SHELL_HALF; bx++) {
            for (int by = SHELL_BOTTOM; by <= SHELL_TOP; by++) {
                for (int bz = -SHELL_HALF; bz <= SHELL_HALF; bz++) {
                    boolean wall = bx == ux - SHELL_HALF || bx == ux + SHELL_HALF || by == SHELL_BOTTOM || by == SHELL_TOP
                            || bz == -SHELL_HALF || bz == SHELL_HALF;
                    if (wall) mars.setBlock(new BlockPos(bx, by, bz), stone, Block.UPDATE_CLIENTS);
                }
            }
        }

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int bx = Spot.BREATHABLE.x - AIR_HALF; bx <= Spot.BREATHABLE.x + AIR_HALF; bx++) {
            for (int by = AIR_BOTTOM; by <= AIR_TOP; by++) {
                for (int bz = -AIR_HALF; bz <= AIR_HALF; bz++) {
                    mars.setBreathable(pos.set(bx, by, bz), true);
                }
            }
        }
    }

    private static void fill(ServerLevel level, int x1, int y1, int z1, int x2, int y2, int z2, BlockState state) {
        for (BlockPos pos : BlockPos.betweenClosed(x1, y1, z1, x2, y2, z2)) level.setBlock(pos, state, Block.UPDATE_CLIENTS);
    }

    /** Counts the storm's dust motes around the spot, and those in places the storm should not reach. */
    @SuppressWarnings("unchecked")
    private void measure(Minecraft client, Spot spot) {
        try {
            if (particles == null) {
                particles = ParticleEngine.class.getDeclaredField("particles");
                x = Particle.class.getDeclaredField("x");
                y = Particle.class.getDeclaredField("y");
                z = Particle.class.getDeclaredField("z");
                for (Field f : new Field[]{particles, x, y, z}) f.setAccessible(true);
            }
            for (Queue<Particle> queue : ((Map<ParticleRenderType, Queue<Particle>>) particles.get(client.particleEngine)).values()) {
                for (Particle particle : queue) {
                    if (!(particle instanceof DustParticle) || !particle.isAlive()) continue;
                    double px = x.getDouble(particle), py = y.getDouble(particle), pz = z.getDouble(particle);
                    if (Math.abs(px - (spot.x + 0.5)) > 16 || Math.abs(pz - 0.5) > 16 || Math.abs(py - spot.feetY) > 12) continue;
                    if (seen.add(particle) && spot.sheltered(px, py, pz)) shelteredDust++;
                }
            }
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Could not read the particle engine", e);
        }
    }

    /** The storm wind's current volume, or 0 if it is not playing. */
    private float wind() {
        try {
            if (active == null) {
                active = DustStormSound.class.getDeclaredField("active");
                volume = AbstractSoundInstance.class.getDeclaredField("volume");
                active.setAccessible(true);
                volume.setAccessible(true);
            }
            Object sound = active.get(null);
            if (sound == null || ((DustStormSound) sound).isStopped()) return 0.0F;
            return volume.getFloat(sound);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Could not read the dust storm wind", e);
        }
    }
}
