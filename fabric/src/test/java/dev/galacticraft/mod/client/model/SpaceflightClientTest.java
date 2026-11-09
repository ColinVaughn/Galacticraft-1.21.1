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
import dev.galacticraft.mod.config.ConfigImpl;
import dev.galacticraft.mod.content.GCEntityTypes;
import dev.galacticraft.mod.content.entity.vehicle.RocketEntity;
import dev.galacticraft.mod.content.rocket.RocketPrefabs;
import dev.galacticraft.mod.world.dimension.GCDimensions;
import dev.galacticraft.mod.world.dimension.SpaceTravel;
import dev.galacticraft.mod.client.render.dimension.SpaceflightTransition;
import dev.galacticraft.mod.client.sounds.RocketSound;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.CameraType;
import net.minecraft.client.Screenshot;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/** Opt-in rendered check: real player packets, dimension changes, HUD and screenshots. */
public final class SpaceflightClientTest implements ClientModInitializer {
    private int phase, ticks, timeout;
    private double startZ;
    private int steadyServerTick;
    private int bridgeTicks, entryBridgeTicks;
    private long handoffStarted;
    private long entryStarted;
    private int handoffTicks;
    private java.util.concurrent.CompletableFuture<Void> resourceReload;
    private net.minecraft.client.renderer.PostChain beforeReload;

    @Override public void onInitializeClient() {
        if (!Boolean.getBoolean("galacticraft.spaceflight.client-test")) return;
        net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register((graphics, delta) -> recordFrame());
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (++timeout > 6000) throw new AssertionError("Spaceflight client timed out in phase " + phase);
            if (phase >= 2 && client.screen instanceof net.minecraft.client.gui.screens.PauseScreen) client.setScreen(null);
            if (phase >= 2 && client.screen instanceof ReceivingLevelScreen)
                throw new AssertionError("Rocket flight displayed the terrain-loading screen");
            if (phase == 3 && client.level != null && client.player != null && client.player.getVehicle() instanceof RocketEntity approach
                    && approach.isInSpaceflight() && entryStarted == 0
                    && SpaceTravel.DESTINATIONS.get(1).distance(approach.position(), SpaceTravel.days(client.level)) <= SpaceTravel.ENTRY_DISTANCE + 32)
                entryStarted = System.nanoTime();
            if (phase == 3 && entryStarted != 0 && System.nanoTime() - entryStarted > 5_000_000_000L
                    + Integer.getInteger("galacticraft.spaceflight.ready-delay", 0) * 50_000_000L)
                throw new AssertionError("Atmospheric entry stalled before or during the handoff");
            if (SpaceflightTransition.isActive()) {
                if (handoffStarted == 0) handoffStarted = System.nanoTime();
                handoffTicks++;
                if (handoffTicks > 2 && client.player != null && client.player.getVehicle() instanceof RocketEntity ship
                        && !RocketSound.hasCabinSound(ship.getUUID()))
                    throw new AssertionError("Engine sound stopped during the flight handoff");
                if (System.nanoTime() - handoffStarted > 5_000_000_000L
                        + Integer.getInteger("galacticraft.spaceflight.ready-delay", 0) * 50_000_000L)
                    throw new AssertionError("Flight handoff stalled in phase " + phase + " for " + handoffTicks + " ticks");
                bridgeTicks++;
                // GPU readback is intrusive; keep flight screenshots in the opt-in visual run.
                if (Boolean.getBoolean("galacticraft.spaceflight.gallery")) {
                    if (phase == 3) {
                        entryBridgeTicks++;
                        if (entryBridgeTicks == 10 || entryBridgeTicks == 20 || entryBridgeTicks == 35)
                            Screenshot.grab(client.gameDirectory, "spaceflight-entry-handoff-" + entryBridgeTicks + ".png", client.getMainRenderTarget(), ignored -> {});
                    }
                    if (bridgeTicks == 10 || bridgeTicks == 50)
                        Screenshot.grab(client.gameDirectory, "spaceflight-live-handoff-" + bridgeTicks + ".png", client.getMainRenderTarget(), ignored -> {});
                }
                return;
            }
            handoffStarted = 0;
            handoffTicks = 0;
            if (client.getOverlay() != null) return;
            if (phase == 0 && client.screen != null) {
                phase = 1;
                org.lwjgl.glfw.GLFW.glfwHideWindow(client.getWindow().getWindow());
                SpaceflightTransition.preload();
                client.options.renderDistance().set(Integer.getInteger("galacticraft.spaceflight.render-distance", 12));
                client.options.graphicsMode().set(net.minecraft.client.GraphicsStatus.FABULOUS);
                client.options.pauseOnLostFocus = false;
                client.options.guiScale().set(2);
                client.options.tutorialStep = TutorialSteps.NONE;
                client.createWorldOpenFlows().createFreshLevel("spaceflight-" + System.currentTimeMillis(),
                        new LevelSettings("Spaceflight Check", GameType.CREATIVE, false, Difficulty.PEACEFUL,
                                true, new GameRules(), WorldDataConfiguration.DEFAULT), new WorldOptions(73, false, false),
                        registries -> registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT)
                                .value().createWorldDimensions(), client.screen);
                return;
            }
            if (client.player == null || client.level == null || client.getSingleplayerServer() == null) return;
            if (phase == 1 && client.screen == null) {
                // Measure the flight swap after the initial world and mod-reload tasks settle.
                if (++ticks < 60) return;
                ticks = 0;
                if (SpaceflightTransition.suppressedScreens() != 0)
                    throw new AssertionError("Initial world loading was changed by the flight-only handoff");
                phase = 2;
                client.options.setCameraType(CameraType.FIRST_PERSON);
                client.getSingleplayerServer().execute(() -> {
                    ((ConfigImpl) Galacticraft.CONFIG).setSeamlessSpaceTravel(true);
                    var player = client.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                    var rocket = new RocketEntity(GCEntityTypes.ROCKET, player.serverLevel());
                    rocket.setData(RocketPrefabs.TIER_1_STORAGE_UPGRADE);
                    rocket.setFuel(rocket.getFuelTankCapacity());
                    rocket.setLaunchStage(LaunchStage.LAUNCHED);
                    rocket.setThrust(1);
                    rocket.setDeltaMovement(new Vec3(0, 2, 0));
                    // Fly through the ascent preload band instead of teleporting directly into the handoff.
                    rocket.setPos(0, Constant.ESCAPE_HEIGHT - 64, 0);
                    player.serverLevel().addFreshEntity(rocket);
                    player.teleportTo(0, Constant.ESCAPE_HEIGHT - 61, 0);
                    player.startRiding(rocket, true);
                });
                return;
            }
            if (phase == 5 && ticks == 20) org.lwjgl.glfw.GLFW.glfwSetWindowSize(client.getWindow().getWindow(), 960, 540);
            if (phase == 5 && ticks == 30) {
                beforeReload = client.gameRenderer.currentEffect();
                if (beforeReload == null) throw new AssertionError("Moon post effect was not activated");
                resourceReload = client.reloadResourcePacks();
            }
            if (phase == 5 && ++ticks >= 120) {
                if (resourceReload == null || !resourceReload.isDone()) return;
                resourceReload.join();
                if (client.gameRenderer.currentEffect() == null || client.gameRenderer.currentEffect() == beforeReload)
                    throw new AssertionError("Planetary shader cache did not survive resize and resource reload");
                if (client.getSingleplayerServer().getTickCount() < steadyServerTick + 60)
                    throw new AssertionError("Server stopped advancing while approach chunks unloaded");
                if (SpaceflightTransition.completedTransfers() < 2 || SpaceflightTransition.suppressedScreens() < 4 || bridgeTicks == 0)
                    throw new AssertionError("The rendered test did not cover both staged seamless handoffs");
                Constant.LOGGER.info("SPACEFLIGHT FUNCTIONAL CHECK PASSED: mounted flight, engine continuity, landing terrain, resize and resource reload");
                for (var frame : slowFrames)
                    Constant.LOGGER.info("SPACEFLIGHT SLOW FRAME: {} ms ending {}, render-thread CPU {} ms, GC {} ms",
                            frame.duration() / 1_000_000, frame.ended(), frame.cpu() / 1_000_000, frame.gc());
                // Report timing after exercising both directions, so one spike cannot hide a landing regression.
                if (worstHandoffFrame > 100_000_000L)
                    throw new AssertionError("Flight stalled for " + worstHandoffFrame / 1_000_000 + " ms");
                Constant.LOGGER.info("SPACEFLIGHT CLIENT CHECK PASSED: {} seamless handoffs, {} native terrain screens prevented, {} live bridge ticks; mounted flight, landing, resize and shader reload",
                        SpaceflightTransition.completedTransfers(), SpaceflightTransition.suppressedScreens(), bridgeTicks);
                client.options.keyJump.setDown(false);
                phase = 6;
                ticks = 0;
                arrivalGallery(client, GCEntityTypes.LANDING_BALLOONS);
            }
            if (phase >= 6) {
                var expected = phase == 6 ? GCEntityTypes.LANDING_BALLOONS : phase == 7 ? GCEntityTypes.ENTRY_POD : GCEntityTypes.VENUS_ENTRY_POD;
                if (client.player.getVehicle() == null || client.player.getVehicle().getType() != expected) return;
                client.player.setXRot(20);
                if (++ticks == 3 || ticks == 25)
                    Screenshot.grab(client.gameDirectory, "spaceflight-arrival-" + phase + "-" + ticks + ".png", client.getMainRenderTarget(), ignored -> {});
                if (ticks < 30) return;
                if (phase == 8) {
                    Constant.LOGGER.info("SPACEFLIGHT ARRIVAL MODELS PASSED: balloons, asteroid pod, Venus flames and parachute");
                    client.stop();
                } else {
                    phase++;
                    ticks = 0;
                    arrivalGallery(client, phase == 7 ? GCEntityTypes.ENTRY_POD : GCEntityTypes.VENUS_ENTRY_POD);
                }
                return;
            }
            if (phase == 5) return;
            if (phase == 3 && client.level.dimension().equals(GCDimensions.MOON)) {
                if (entryStarted == 0) throw new AssertionError("Flight skipped the measured approach corridor");
                Constant.LOGGER.info("SPACEFLIGHT TOTAL ENTRY: {} ms", (System.nanoTime() - entryStarted) / 1_000_000);
                // The Moon lander is piloted: brake through descent instead of crashing during capture.
                client.options.keyJump.setDown(true);
                phase = 4;
                ticks = 0;
            }
            if (phase == 4 && client.level.dimension().equals(GCDimensions.MOON)) {
                if (!(client.player.getVehicle() instanceof dev.galacticraft.mod.content.entity.vehicle.LanderEntity lander)) throw new AssertionError("Moon did not deploy its lander");
                client.player.setXRot(35);
                if (++ticks == 1) {
                    if (!dev.galacticraft.mod.client.render.dimension.PlanetSurfaceRenderer.hasApproach(GCDimensions.MOON))
                        throw new AssertionError("Moon descent lost its transfer texture projection");
                    if (lander.getY() < 850) throw new AssertionError("Moon entry skipped the GC4 high-altitude descent");
                    Screenshot.grab(client.gameDirectory, "spaceflight-moon-high-entry.png", client.getMainRenderTarget(), ignored -> {});
                    Constant.LOGGER.info("SPACEFLIGHT HIGH ENTRY: lander Y={}", lander.getY());
                    return;
                }
                // Terrain is naturally beyond the camera at Y=900; check it when the real descent reaches it.
                int ground = client.level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                        lander.getBlockX(), lander.getBlockZ());
                if (lander.getY() - ground > 48) return;
                if (dev.galacticraft.mod.client.render.dimension.PlanetSurfaceRenderer.hasApproach(GCDimensions.MOON))
                    throw new AssertionError("Arrival projection survived the return to terrain");
                Constant.LOGGER.info("SPACEFLIGHT LANDING VIEW: type={}, player pitch={}, camera pitch={}, lander pitch={}, camera={}, lander={}",
                        client.options.getCameraType(), client.player.getXRot(), client.gameRenderer.getMainCamera().getXRot(),
                        lander.getXRot(), client.gameRenderer.getMainCamera().getPosition(), lander.position());
                if (client.options.getCameraType() != CameraType.THIRD_PERSON_BACK)
                    throw new AssertionError("Seamless arrival lost its third-person lander view");
                try (var image = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
                    int samples = 0, terrain = 0;
                    for (int x = image.getWidth() / 4; x < image.getWidth() * 3 / 4; x += 12)
                        for (int y = image.getHeight() / 3; y < image.getHeight() * 2 / 3; y += 12) {
                            int pixel = image.getPixelRGBA(x, y);
                            int r = pixel & 255, g = pixel >>> 8 & 255, b = pixel >>> 16 & 255;
                            samples++;
                            if (r + g + b > 30 && Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b)) < 36) terrain++;
                        }
                    if (terrain < samples * 3 / 4) throw new AssertionError("Seamless landing did not reveal visible lunar terrain");
                }
                Screenshot.grab(client.gameDirectory, "spaceflight-moon-descent.png", client.getMainRenderTarget(), ignored -> {});
                phase = 5;
                ticks = 0;
                steadyServerTick = client.getSingleplayerServer().getTickCount();
            }
            if (phase >= 4) return;
            if (!(client.player.getVehicle() instanceof RocketEntity rocket) || client.screen != null) return;
            if (phase == 2 && rocket.isInSpaceflight()) {
                if (!client.options.getCameraType().isFirstPerson()) throw new AssertionError("Space arrival did not face the flight direction");
                client.options.keyShift.setDown(true);
                ticks++;
                if (!Boolean.getBoolean("galacticraft.spaceflight.gallery")) ticks = 240;
                var earth = SpaceTravel.DESTINATIONS.getFirst();
                if (ticks == 40) pose(client, earth.center().add(new Vec3(-2.34, .68, 2.86).scale(earth.radius())), earth.center());
                if (ticks == 90) {
                    Screenshot.grab(client.gameDirectory, "spaceflight-earth-orbit.png", client.getMainRenderTarget(), ignored -> {});
                    // Gallery poses scale with each body, so the framing survives size changes.
                    double sun = SpaceTravel.SUN_RADIUS;
                    pose(client, SpaceTravel.SUN_POSITION.add(4.5 * sun, 1.625 * sun, 4.5 * sun), SpaceTravel.SUN_POSITION);
                }
                if (ticks == 140) {
                    Screenshot.grab(client.gameDirectory, "spaceflight-sun.png", client.getMainRenderTarget(), ignored -> {});
                    pose(client, SpaceTravel.SUN_POSITION.add(4.5 * SpaceTravel.SUN_RADIUS, 0, 0), SpaceTravel.SUN_POSITION);
                }
                if (ticks == 165) {
                    Screenshot.grab(client.gameDirectory, "spaceflight-sun-face.png", client.getMainRenderTarget(), ignored -> {});
                    assertSquareSun(client);
                    var jupiter = SpaceTravel.DESTINATIONS.getLast();
                    pose(client, jupiter.center().add(new Vec3(-2.93, .33, .65).scale(jupiter.radius())), jupiter.center());
                }
                if (ticks == 190) {
                    Screenshot.grab(client.gameDirectory, "spaceflight-jupiter-orbit.png", client.getMainRenderTarget(), ignored -> {});
                    pose(client, earth.departurePosition(), SpaceTravel.DESTINATIONS.get(1).center());
                }
                if (ticks < 240) return;
                client.options.keyShift.setDown(false);
                Screenshot.grab(client.gameDirectory, "spaceflight-departure.png", client.getMainRenderTarget(), ignored -> {});
                startZ = rocket.getZ();
                client.options.keyJump.setDown(true);
                ticks = 0;
                phase = 3;
            } else if (phase == 3) {
                ticks++;
                if (ticks == 100) {
                    if (rocket.getZ() < startZ + 300) throw new AssertionError("Real boost input did not move the rocket");
                    Screenshot.grab(client.gameDirectory, "spaceflight-boost.png", client.getMainRenderTarget(), ignored -> {});
                }
                if (ticks == 200 && rocket.isInSpaceflight()) {
                    Screenshot.grab(client.gameDirectory, "spaceflight-moon-approach.png", client.getMainRenderTarget(), ignored -> {});
                }
                if (ticks > 600) throw new AssertionError("Continuous boost did not reach the Moon");
                if (client.level.dimension().equals(GCDimensions.MOON)) {
                    if (entryStarted == 0) throw new AssertionError("Flight skipped the measured approach corridor");
                    Constant.LOGGER.info("SPACEFLIGHT TOTAL ENTRY: {} ms", (System.nanoTime() - entryStarted) / 1_000_000);
                    client.options.keyJump.setDown(false);
                    phase = 4;
                    ticks = 0;
                }
            }
        });
    }

    private static void arrivalGallery(Minecraft client, net.minecraft.world.entity.EntityType<dev.galacticraft.mod.content.entity.vehicle.AutomaticLanderEntity> type) {
        var id = client.player.getUUID();
        client.getSingleplayerServer().execute(() -> {
            var player = client.getSingleplayerServer().getPlayerList().getPlayer(id);
            var previous = player.getVehicle();
            var lander = type.create(player.serverLevel());
            int ground = player.level().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, player.getBlockX(), player.getBlockZ());
            double height = type == GCEntityTypes.LANDING_BALLOONS ? 900 : type == GCEntityTypes.VENUS_ENTRY_POD ? 275 : 310;
            lander.setPos(player.getX(), Math.max(height, ground + 35), player.getZ());
            lander.startDescent();
            player.serverLevel().addFreshEntity(lander);
            player.startRiding(lander, true);
            if (previous != null) previous.discard();
        });
    }

    private final java.lang.management.ThreadMXBean frameThreads = java.lang.management.ManagementFactory.getThreadMXBean();
    private final java.util.List<java.lang.management.GarbageCollectorMXBean> frameCollectors = java.lang.management.ManagementFactory.getGarbageCollectorMXBeans();
    private record SlowFrame(long duration, java.time.Instant ended, long cpu, long gc) { }
    private final java.util.List<SlowFrame> slowFrames = new java.util.ArrayList<>();
    private long lastFrameCpu, lastFrameGc;
    private long lastFrame, longestFrame, worstHandoffFrame;
    private boolean measuringFlight;
    private int flightFrames;

    private void recordFrame() {
        long now = System.nanoTime();
        long cpu = frameThreads.isCurrentThreadCpuTimeSupported() ? frameThreads.getCurrentThreadCpuTime() : 0;
        long gc = 0;
        for (var collector : frameCollectors) gc += Math.max(0, collector.getCollectionTime());
        boolean flying = SpaceflightTransition.isActive();
        if (flying || measuringFlight) {
            if (lastFrame != 0) {
                long duration = now - lastFrame;
                longestFrame = Math.max(longestFrame, duration);
                // Console logging can block the render thread and inflate the next measured frame.
                if (duration > 50_000_000L) slowFrames.add(new SlowFrame(duration, java.time.Instant.now(), cpu - lastFrameCpu, gc - lastFrameGc));
            }
            flightFrames++;
        }
        if (!flying && measuringFlight) {
            Constant.LOGGER.info("SPACEFLIGHT FRAME TIMING: {} frames, maximum {} ms", flightFrames, longestFrame / 1_000_000);
            worstHandoffFrame = Math.max(worstHandoffFrame, longestFrame);
            longestFrame = 0;
            flightFrames = 0;
        }
        measuringFlight = flying;
        lastFrame = now;
        lastFrameCpu = cpu;
        lastFrameGc = gc;
    }

    private static boolean isSunPixel(int pixel) {
        int r = pixel & 255, g = pixel >>> 8 & 255, b = pixel >>> 16 & 255;
        return r > 160 && g > 120 && r > b + 15;
    }

    private static void assertSquareSun(Minecraft client) {
        try (var image = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
            // The camera aims at the Sun, so flood-fill its surface from the screen centre. Bounding every warm pixel
            // would also take in lit planets or asteroid-belt specks elsewhere in the frame.
            int width = image.getWidth(), height = image.getHeight();
            int left = width, bottom = height, right = 0, top = 0;
            boolean[] seen = new boolean[width * height];
            var queue = new java.util.ArrayDeque<int[]>();
            // Seed from the Sun pixel nearest the centre; the centre itself is under the crosshair.
            seed:
            for (int r = 0; r < Math.min(width, height) / 4; r++)
                for (int dx = -r; dx <= r; dx++)
                    for (int dy : new int[]{-r, r})
                        for (int[] p : new int[][]{{width / 2 + dx, height / 2 + dy}, {width / 2 + dy, height / 2 + dx}})
                            if (isSunPixel(image.getPixelRGBA(p[0], p[1]))) { queue.add(p); break seed; }
            while (!queue.isEmpty()) {
                int[] p = queue.poll();
                int x = p[0], y = p[1];
                if (x < 0 || y < 0 || x >= width || y >= height || seen[y * width + x]) continue;
                seen[y * width + x] = true;
                if (!isSunPixel(image.getPixelRGBA(x, y))) continue;
                left = Math.min(left, x); right = Math.max(right, x);
                bottom = Math.min(bottom, y); top = Math.max(top, y);
                queue.add(new int[]{x + 1, y}); queue.add(new int[]{x - 1, y});
                queue.add(new int[]{x, y + 1}); queue.add(new int[]{x, y - 1});
            }
            if (right - left < 24 || top - bottom < 24) throw new AssertionError("Sun surface was not visible");
            int insetX = Math.max(2, (right - left) / 12), insetY = Math.max(2, (top - bottom) / 12);
            for (int x : new int[]{left + insetX, right - insetX})
                for (int y : new int[]{bottom + insetY, top - insetY})
                    if (!isSunPixel(image.getPixelRGBA(x, y))) throw new AssertionError("Sun silhouette lost a square corner");
            Constant.LOGGER.info("SPACEFLIGHT SUN CHECK PASSED: four bright square corners, {} x {} surface", right - left, top - bottom);
        }
    }

    private static void pose(Minecraft client, Vec3 position, Vec3 target) {
        client.getSingleplayerServer().execute(() -> {
            var player = client.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
            if (!(player.getVehicle() instanceof RocketEntity rocket)) throw new AssertionError("Gallery lost the rocket");
            var direction = target.subtract(position).normalize();
            rocket.setPos(position);
            rocket.setDeltaMovement(Vec3.ZERO);
            rocket.setXRot((float) Math.toDegrees(Math.acos(direction.y)));
            rocket.setYRot((float) Math.toDegrees(Math.atan2(-direction.x, direction.z)));
            rocket.xRotO = rocket.getXRot();
            rocket.yRotO = rocket.getYRot();
            rocket.getPassengers().forEach(rocket::positionRider);
        });
    }
}