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

import dev.galacticraft.api.rocket.LaunchStage;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.Galacticraft;
import dev.galacticraft.mod.client.particle.LaunchFlameParticle;
import dev.galacticraft.mod.client.particle.LaunchSmokeParticle;
import dev.galacticraft.mod.client.render.dimension.SpaceflightTransition;
import dev.galacticraft.mod.config.ConfigImpl;
import dev.galacticraft.mod.content.GCEntityTypes;
import dev.galacticraft.mod.content.entity.vehicle.RocketEntity;
import dev.galacticraft.mod.content.rocket.RocketPrefabs;
import dev.galacticraft.mod.world.dimension.SpaceTravel;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.util.*;

/**
 * Measures engine exhaust relative to a moving rocket during ascent, boost, yaw, and pitch,
 * with side-view captures of each maneuver.
 * Run with {@code gradlew :fabric:runClient -PexhaustClientTest -Prei=false -Pastrocraft=false}.
 */
public final class ExhaustPlumeClientTest implements ClientModInitializer {
    /** The ascent runs through the atmosphere until the seamless handoff; the rest are timed spaceflight manoeuvres. */
    private static final String[] MANOEUVRES = {"ascent", "straight-boost", "yaw-turn", "after-turn", "pitch", "cruise", "boost-lock"};
    private static final int[] LENGTHS = {0, 80, 45, 40, 30, 40, 130};
    /** A flame this many ticks old must still sit behind the nozzle, within this angle of the exhaust axis. */
    private static final int SAMPLE_AGE = 4;
    private static final double MAX_ANGLE = 25, MAX_LATERAL = 1.2;
    /** Smoke left by dying flames must stay on the plume line while the plume is steady. */
    private static final double MAX_SMOKE_LATERAL = 1.2;
    private static final int SMOKE_CHECKED = 1;

    private int stage, manoeuvre, ticks, timeout;
    private ArmorStand camera;
    private Vec3 lastRocket;
    private final Map<Particle, Integer> tracked = new IdentityHashMap<>();
    private final Map<Particle, Vec3> spawnMotion = new IdentityHashMap<>();
    private Vec3 motion = Vec3.ZERO;
    private double lockedSpeed;
    private final List<List<Double>> angles = new ArrayList<>(), laterals = new ArrayList<>(), smokeLaterals = new ArrayList<>();
    private final Set<Particle> seenSmoke = Collections.newSetFromMap(new IdentityHashMap<>());
    private Field particles, x, y, z;

    @Override
    public void onInitializeClient() {
        if (!Boolean.getBoolean("galacticraft.exhaust.client-test")) return;
        for (int i = 0; i < MANOEUVRES.length; i++) { angles.add(new ArrayList<>()); laterals.add(new ArrayList<>()); smokeLaterals.add(new ArrayList<>()); }
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private void tick(Minecraft client) {
        if (++timeout > 5000) throw new AssertionError("Exhaust check timed out in stage " + stage);
        if (stage >= 2 && client.screen instanceof PauseScreen) client.setScreen(null);
        if (client.getOverlay() != null || SpaceflightTransition.isActive()) return;
        if (stage == 0) {
            if (client.screen == null) return;
            stage = 1;
            org.lwjgl.glfw.GLFW.glfwHideWindow(client.getWindow().getWindow());
            client.options.renderDistance().set(4);
            client.options.pauseOnLostFocus = false;
            client.options.guiScale().set(2);
            client.options.hideGui = true;
            client.options.tutorialStep = TutorialSteps.NONE;
            client.createWorldOpenFlows().createFreshLevel("exhaust-" + System.currentTimeMillis(),
                    new LevelSettings("Exhaust Check", GameType.CREATIVE, false, Difficulty.PEACEFUL,
                            true, new GameRules(), WorldDataConfiguration.DEFAULT), new WorldOptions(73, false, false),
                    registries -> registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT)
                            .value().createWorldDimensions(), client.screen);
            return;
        }
        if (client.player == null || client.level == null || client.getSingleplayerServer() == null) return;
        if (stage == 1) {
            if (client.screen != null) return;
            stage = 2;
            client.options.setCameraType(CameraType.FIRST_PERSON);
            client.getSingleplayerServer().execute(() -> {
                ((ConfigImpl) Galacticraft.CONFIG).setSeamlessSpaceTravel(true);
                var player = client.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                var rocket = new RocketEntity(GCEntityTypes.ROCKET, player.serverLevel());
                rocket.setData(RocketPrefabs.TIER_1_STORAGE_UPGRADE);
                rocket.setFuel(rocket.getFuelTankCapacity());
                rocket.setLaunchStage(LaunchStage.LAUNCHED);
                rocket.setPos(0, Constant.ESCAPE_HEIGHT - 120, 0);
                // Climbing from the first tick, as after a pad launch; a fresh launch's zero thrust would fall first.
                rocket.setThrust(1);
                player.serverLevel().addFreshEntity(rocket);
                player.teleportTo(0, Constant.ESCAPE_HEIGHT - 119, 0);
                player.startRiding(rocket, true);
            });
            return;
        }
        if (!(client.player.getVehicle() instanceof RocketEntity rocket) || client.screen != null) return;
        if (stage == 2) {
            // Atmospheric ascent on the ordinary launch path, up to the seamless handoff.
            if (camera == null || camera.level() != client.level) watch(client, rocket);
            if (rocket.isInSpaceflight()) {
                if (++ticks < 30) return;
                lastRocket = rocket.position();
                stage = 3;
                manoeuvre = 1;
                ticks = 0;
                return;
            }
            observe(rocket);
            if (++ticks == 40) Screenshot.grab(client.gameDirectory, "exhaust-0-ascent.png", client.getMainRenderTarget(), ignored -> {});
            return;
        }
        if (stage != 3) return;

        // boost-lock: double-tap Space and let go; boost must stay on until Shift releases it.
        boolean tap = manoeuvre == 6 && (ticks <= 1 || ticks == 4 || ticks == 5);
        client.options.keyJump.setDown(manoeuvre <= 4 || tap);
        client.options.keyRight.setDown(manoeuvre == 2);
        client.options.keyUp.setDown(manoeuvre == 4);
        client.options.keyShift.setDown(manoeuvre == 6 && (ticks == 90 || ticks == 91));
        Vec3 nose = observe(rocket);
        if (manoeuvre == 6 && ticks == 85) {
            if (!rocket.isBoostLocked()) throw new AssertionError("Double-tapping boost did not lock it on");
            if (motion.length() < 7) throw new AssertionError("Locked boost did not keep accelerating: " + motion.length());
            lockedSpeed = motion.length();
        }
        if (manoeuvre == 6 && ticks == 125) {
            if (rocket.isBoostLocked()) throw new AssertionError("Braking did not release the boost lock");
            if (motion.length() > lockedSpeed - 3) throw new AssertionError("Released boost did not slow down: " + motion.length());
            Constant.LOGGER.info("EXHAUST BOOST LOCK PASSED: locked at {} blocks/tick, released to {}", lockedSpeed, motion.length());
        }

        if (ticks % 10 == 0) {
            double drift = motion.lengthSqr() < 1e-4 ? 0 : angle(motion, nose);
            Constant.LOGGER.info("EXHAUST {} t={} speed={} drift={}deg pitch={} yaw={} clientDelta={}", MANOEUVRES[manoeuvre], ticks,
                    String.format("%.2f", motion.length()), String.format("%.1f", drift), rocket.getXRot(), rocket.getYRot(), rocket.getDeltaMovement());
        }
        if (ticks == LENGTHS[manoeuvre] * 3 / 4)
            Screenshot.grab(client.gameDirectory, "exhaust-" + manoeuvre + "-" + MANOEUVRES[manoeuvre] + ".png", client.getMainRenderTarget(), ignored -> {});
        if (++ticks < LENGTHS[manoeuvre]) return;
        ticks = 0;
        if (++manoeuvre < MANOEUVRES.length) return;

        client.options.keyJump.setDown(false);
        StringBuilder failures = new StringBuilder();
        for (int i = 0; i < MANOEUVRES.length; i++) {
            double angle = percentile(angles.get(i), 0.9), lateral = percentile(laterals.get(i), 0.9);
            Constant.LOGGER.info("EXHAUST RESULT {}: {} flames, p90 plume angle {}deg (median {}), p90 nozzle offset {} blocks",
                    MANOEUVRES[i], angles.get(i).size(), String.format("%.1f", angle), String.format("%.1f", percentile(angles.get(i), 0.5)),
                    String.format("%.2f", lateral));
            if (angles.get(i).isEmpty() || angle > MAX_ANGLE || lateral > MAX_LATERAL)
                failures.append(' ').append(MANOEUVRES[i]);
            double smoke = percentile(smokeLaterals.get(i), 0.9);
            Constant.LOGGER.info("EXHAUST SMOKE {}: {} puffs, p90 distance from plume line {} blocks (median {})", MANOEUVRES[i],
                    smokeLaterals.get(i).size(), String.format("%.2f", smoke), String.format("%.2f", percentile(smokeLaterals.get(i), 0.5)));
            if (i == SMOKE_CHECKED && (smokeLaterals.get(i).isEmpty() || smoke > MAX_SMOKE_LATERAL))
                failures.append(' ').append(MANOEUVRES[i]).append("-smoke");
        }
        if (!failures.isEmpty()) throw new AssertionError("Engine flames left the exhaust axis during:" + failures);
        Constant.LOGGER.info("EXHAUST CLIENT CHECK PASSED");
        client.stop();
        stage = 4;
    }

    private void watch(Minecraft client, RocketEntity rocket) {
        camera = new ArmorStand(EntityType.ARMOR_STAND, client.level);
        camera.setInvisible(true);
        camera.setNoGravity(true);
        client.level.addEntity(camera);
        client.setCameraEntity(camera);
        lastRocket = rocket.position();
    }

    private Vec3 observe(RocketEntity rocket) {
        Vec3 nose = SpaceTravel.forward(rocket.getXRot(), rocket.getYRot());
        Vec3 nozzle = rocket.position().add(0, 1.6, 0).subtract(nose.scale(1.6));
        motion = rocket.position().subtract(lastRocket);
        lastRocket = rocket.position();
        measure(Minecraft.getInstance(), nose, nozzle);
        follow(nose, nozzle);
        return nose;
    }

    /** New flames are measured at the nozzle; flames SAMPLE_AGE ticks old are measured against the exhaust axis. */
    @SuppressWarnings("unchecked")
    private void measure(Minecraft client, Vec3 nose, Vec3 nozzle) {
        try {
            if (particles == null) {
                particles = ParticleEngine.class.getDeclaredField("particles");
                x = Particle.class.getDeclaredField("x");
                y = Particle.class.getDeclaredField("y");
                z = Particle.class.getDeclaredField("z");
                for (Field f : new Field[]{particles, x, y, z}) f.setAccessible(true);
            }
            Vec3 exhaust = nose.reverse();
            Set<Particle> alive = Collections.newSetFromMap(new IdentityHashMap<>()), smoke = Collections.newSetFromMap(new IdentityHashMap<>());
            for (Queue<Particle> queue : ((Map<ParticleRenderType, Queue<Particle>>) particles.get(client.particleEngine)).values())
                for (Particle particle : queue) {
                    if (particle instanceof LaunchSmokeParticle && particle.isAlive()) {
                        smoke.add(particle);
                        if (seenSmoke.add(particle)) {
                            Vec3 offset = new Vec3(x.getDouble(particle), y.getDouble(particle), z.getDouble(particle)).subtract(nozzle);
                            smokeLaterals.get(manoeuvre).add(offset.subtract(exhaust.scale(offset.dot(exhaust))).length());
                        }
                    }
                    if (!(particle instanceof LaunchFlameParticle) || !particle.isAlive()) continue;
                    alive.add(particle);
                    Vec3 offset = new Vec3(x.getDouble(particle), y.getDouble(particle), z.getDouble(particle)).subtract(nozzle);
                    Integer age = tracked.get(particle);
                    if (age == null) {
                        laterals.get(manoeuvre).add(offset.subtract(exhaust.scale(offset.dot(exhaust))).length());
                        tracked.put(particle, 0);
                        spawnMotion.put(particle, motion);
                    } else if (age + 1 == SAMPLE_AGE) {
                        double angle = angle(offset, exhaust);
                        angles.get(manoeuvre).add(angle);
                        if (angle > MAX_ANGLE) Constant.LOGGER.info("EXHAUST OUTLIER {} angle={} offset={} spawnMotion={} motionNow={}",
                                MANOEUVRES[manoeuvre], String.format("%.1f", angle), offset, spawnMotion.get(particle), motion);
                        tracked.put(particle, SAMPLE_AGE);
                    } else if (age < SAMPLE_AGE) {
                        tracked.put(particle, age + 1);
                    }
                }
            tracked.keySet().retainAll(alive);
            spawnMotion.keySet().retainAll(alive);
            seenSmoke.retainAll(smoke);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Could not read the particle engine", e);
        }
    }

    /** Side-on camera looking at the plume, so screenshots show where flames go relative to the engine. */
    private void follow(Vec3 nose, Vec3 nozzle) {
        Vec3 side = nose.cross(new Vec3(0, 1, 0));
        side = side.lengthSqr() < 1e-3 ? new Vec3(1, 0, 0) : side.normalize();
        Vec3 plume = nozzle.subtract(nose.scale(8));
        Vec3 eye = plume.add(side.scale(26)).add(nose.scale(3)).add(0, 6, 0);
        Vec3 look = plume.subtract(eye).normalize();
        camera.setPos(eye.x, eye.y - camera.getEyeHeight(), eye.z);
        camera.setYRot((float) Math.toDegrees(Math.atan2(-look.x, look.z)));
        camera.setXRot((float) Math.toDegrees(Math.asin(-look.y)));
        camera.setYHeadRot(camera.getYRot());
    }

    private static double angle(Vec3 a, Vec3 b) {
        return Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, a.normalize().dot(b.normalize())))));
    }

    private static double percentile(List<Double> values, double q) {
        if (values.isEmpty()) return Double.NaN;
        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        return sorted.get(Math.min(sorted.size() - 1, (int) (q * sorted.size())));
    }
}
