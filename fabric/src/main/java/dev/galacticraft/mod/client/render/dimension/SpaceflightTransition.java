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

package dev.galacticraft.mod.client.render.dimension;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import dev.architectury.networking.NetworkManager;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.client.accessor.SpaceflightTerrainAccess;
import dev.galacticraft.mod.content.entity.vehicle.RocketEntity;
import dev.galacticraft.mod.client.sounds.RocketSound;
import dev.galacticraft.mod.network.c2s.SpaceflightReadyPayload;
import dev.galacticraft.mod.network.s2c.SpaceflightHandoffPayload;
import dev.galacticraft.mod.world.dimension.GCDimensions;
import dev.galacticraft.mod.world.dimension.SpaceTravel;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.lwjgl.opengl.GL30;

/** Keeps a live flight view across the native world swap, then reveals an actually prepared destination. */
public final class SpaceflightTransition {
    private static @Nullable SpaceflightTransition active;
    private static int completed, suppressed;
    private static long taskDeadline;
    private static @Nullable java.util.UUID lastRenderedRocket;
    private final SpaceflightHandoffPayload transfer;
    private final ClientLevel source;
    private final long started = System.nanoTime();
    private final float skyTime;
    private @Nullable RenderTarget previousFrame, flightFrame;
    private boolean swapping, cameraAligned, terrainAcknowledged, surfacePrepared;
    private @Nullable java.util.UUID arrivalVehicle;
    private long respawnStarted;
    private long readyAt;
    private float yaw, pitch;
    private long lastFrame = started, lastDiagnostic;

    private SpaceflightTransition(SpaceflightHandoffPayload transfer, ClientLevel source) {
        this.transfer = transfer;
        this.source = source;
        this.skyTime = source.getGameTime() / 24000F;
        this.yaw = transfer.yaw();
        this.pitch = transfer.pitch() - 90;
        var mc = Minecraft.getInstance();
        var main = mc.getMainRenderTarget();
        // An instant server-spawned launch may precede the first mounted world frame.
        if (transfer.rocket().equals(lastRenderedRocket)) {
            previousFrame = new TextureTarget(main.width, main.height, false, Minecraft.ON_OSX);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousFrame.frameBufferId);
            GL30.glBlitFramebuffer(0, 0, main.width, main.height, 0, 0, main.width, main.height,
                    GL30.GL_COLOR_BUFFER_BIT, GL30.GL_NEAREST);
            main.bindWrite(true);
        }
    }

    public static void rememberRenderedView() {
        var mc = Minecraft.getInstance();
        if (active == null) lastRenderedRocket = mc.screen == null && mc.player != null
                && mc.player.getVehicle() instanceof RocketEntity rocket ? rocket.getUUID() : null;
    }

    public static void preload() {
        SolarSystemSkyRenderer.preload();
    }

    public static void begin(SpaceflightHandoffPayload transfer) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.player.isDeadOrDying()) return;
        reset();
        PlanetSurfaceRenderer.clearApproach();
        active = new SpaceflightTransition(transfer, mc.level);
        if (mc.player.getVehicle() instanceof RocketEntity rocket && rocket.getUUID().equals(transfer.rocket()))
            RocketSound.play(rocket);
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        if (mc.screen instanceof ReceivingLevelScreen) mc.setScreen(null);
        NetworkManager.sendToServer(new SpaceflightReadyPayload(transfer.rocket(), transfer.token(), false, transfer.yaw(), transfer.pitch()));
        Constant.LOGGER.info("Spaceflight handoff armed for {}", transfer.destination().location());
    }

    public static void onRespawn(ResourceKey<Level> destination) {
        PlanetSurfaceRenderer.clearApproach();
        if (active == null) return;
        if (active.transfer.destination().equals(destination)) {
            active.swapping = true;
            active.respawnStarted = System.nanoTime();
        } else reset();
    }

    public static void onRespawnFinished() {
        if (active != null && active.swapping && Boolean.getBoolean("galacticraft.spaceflight.client-test")) {
            long millis = (System.nanoTime() - active.respawnStarted) / 1_000_000;
            Constant.LOGGER.info("SPACEFLIGHT NATIVE RESPAWN: {} ms", millis);
            if (millis > 100) throw new AssertionError("Native flight respawn blocked the render thread for " + millis + " ms");
        }
        if (active != null) active.respawnStarted = 0;
    }

    public static boolean isReplacingLevel() { return active != null && active.respawnStarted != 0; }

    public static boolean suppressTerrainScreen(Object screen) {
        if (active != null && active.swapping && screen instanceof ReceivingLevelScreen) {
            suppressed++;
            return true;
        }
        return false;
    }

    public static void cancel(java.util.UUID token) {
        if (active != null && active.transfer.token().equals(token)) {
            PlanetSurfaceRenderer.clearApproach();
            reset();
        }
    }

    public static void arrive(java.util.UUID token, java.util.UUID vehicle) {
        if (active != null && active.terrainAcknowledged && active.transfer.token().equals(token)) {
            active.arrivalVehicle = vehicle;
        }
    }

    public static void beginTaskBatch() { taskDeadline = System.nanoTime() + 2_000_000L; }
    public static void endTaskBatch() { taskDeadline = 0; }
    public static boolean shouldYieldTasks() {
        return active != null && taskDeadline != 0 && System.nanoTime() >= taskDeadline;
    }

    public static boolean isActive() { return active != null; }
    public static boolean coversWorld() { return active != null && active.readyAt == 0 && Minecraft.getInstance().screen == null; }
    public static float engineVolume() {
        return active != null && active.arrivalVehicle != null && active.readyAt != 0
                ? .55F * (1 - SpaceflightAnimation.reveal((System.nanoTime() - active.readyAt) / 1_000_000_000F)) : .55F;
    }
    public static boolean carries(java.util.UUID rocket) { return active != null && active.transfer.rocket().equals(rocket); }
    public static @Nullable Vec3 arrivalPosition() { return active == null ? null : active.transfer.arrivalPosition(); }
    public static boolean isPreparingTerrain() { return active != null && active.swapping && active.readyAt == 0; }
    public static int completedTransfers() { return completed; }
    public static int suppressedScreens() { return suppressed; }

    public static void onDisconnect() {
        PlanetSurfaceRenderer.clearApproach();
        reset();
        RocketSound.clear();
        SpaceflightViewArea.releaseRetired(Long.MAX_VALUE);
        lastRenderedRocket = null;
    }

    public static void reset() {
        if (active == null) return;
        if (active.previousFrame != null) active.previousFrame.destroyBuffers();
        if (active.flightFrame != null) active.flightFrame.destroyBuffers();
        active = null;
    }

    public static void render() {
        if (active == null) {
            SpaceflightViewArea.releaseRetired(System.nanoTime() + 2_000_000L);
            return;
        }
        var mc = Minecraft.getInstance();
        if (mc.getConnection() == null || mc.player == null || mc.player.isDeadOrDying()) { reset(); return; }
        active.renderFrame(mc);
    }

    private void renderFrame(Minecraft mc) {
        if (cameraAligned && !terrainAcknowledged && (!(mc.player.getVehicle() instanceof RocketEntity ship) || !ship.getUUID().equals(transfer.rocket()))) {
            if (readyAt != 0) completed++;
            reset();
            return;
        }
        long now = System.nanoTime();
        float seconds = (now - started) / 1_000_000_000F;
        float delta = Math.min(.1F, (now - lastFrame) / 1_000_000_000F);
        lastFrame = now;
        logReadiness(mc, now, seconds);
        prepareDestination(mc, now, seconds);
        if (readyAt != 0 && !surfacePrepared && !transfer.destination().equals(GCDimensions.SOLAR_SYSTEM)) {
            double days = SpaceTravel.days(source);
            var body = SpaceTravel.nearest(source.registryAccess(), transfer.flightPosition(), days);
            if (body == null) { reset(); return; }
            double endSeconds = (readyAt - started) / 1_000_000_000.0 + SpaceflightAnimation.REVEAL_SECONDS;
            double endAltitude = SpaceflightAnimation.approachAltitude(
                    body.distance(transfer.flightPosition(), days), body.radius(), endSeconds, 1);
            PlanetSurfaceRenderer.beginApproach(mc.level, body, transfer.flightPosition(), transfer.arrivalPosition(),
                    days, endAltitude, skyTime + (float) endSeconds / 1200F);
            surfacePrepared = true;
        }
        float reveal = readyAt == 0 ? 0 : SpaceflightAnimation.reveal((now - readyAt) / 1_000_000_000F);
        if (reveal >= 1) {
            completed++;
            reset();
            return;
        }
        if (mc.screen != null) return;
        updateHeading(mc, delta);
        Vec3 position = transfer.flightPosition();
        if (!transfer.destination().equals(GCDimensions.SOLAR_SYSTEM)) {
            // Match the retained world's clock used by renderFlight, even after the native level is replaced.
            double days = SpaceTravel.days(source);
            var body = SpaceTravel.nearest(source.registryAccess(), position, days);
            if (body == null) { reset(); return; }
            double altitude = SpaceflightAnimation.approachAltitude(body.distance(position, days), body.radius(), seconds, reveal);
            position = body.approachPosition(position, altitude, days);
        } else {
            position = position.add(SpaceTravel.forward(transfer.pitch(), transfer.yaw()).scale(seconds * 8));
        }
        var main = mc.getMainRenderTarget();
        if (flightFrame == null || flightFrame.width != main.width || flightFrame.height != main.height) {
            if (flightFrame != null) flightFrame.destroyBuffers();
            flightFrame = new TextureTarget(main.width, main.height, false, Minecraft.ON_OSX);
        }
        RenderSystem.backupProjectionMatrix();
        var modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.identity();
        RenderSystem.applyModelViewMatrix();
        var projection = new Matrix4f().perspective((float) Math.toRadians(mc.options.fov().get()),
                (float) main.width / main.height, .05F, 4096);
        RenderSystem.setProjectionMatrix(projection, VertexSorting.DISTANCE_TO_ORIGIN);
        flightFrame.setClearColor(0, 0, 0, 1);
        flightFrame.clear(Minecraft.ON_OSX);
        flightFrame.bindWrite(true);
        var rotation = new Quaternionf().rotationYXZ((float) Math.PI - yaw * Mth.DEG_TO_RAD, -pitch * Mth.DEG_TO_RAD, 0);
        SolarSystemSkyRenderer.INSTANCE.renderFlight(source, new Matrix4f().rotation(rotation.conjugate()), projection,
                position, 0, skyTime + seconds / 1200F);
        main.bindWrite(true);
        RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0, main.width, main.height, 0, -1, 1), VertexSorting.ORTHOGRAPHIC_Z);
        float alpha = 1 - reveal;
        blit(flightFrame, main.width, main.height, alpha);
        if (previousFrame != null) {
            float previousAlpha = 1 - Math.min(1, seconds / .3F);
            if (previousAlpha > 0) blit(previousFrame, main.width, main.height, previousAlpha);
            else { previousFrame.destroyBuffers(); previousFrame = null; }
        }
        modelView.popMatrix();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.restoreProjectionMatrix();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private void logReadiness(Minecraft mc, long now, float seconds) {
        if (Integer.getInteger("galacticraft.spaceflight.ready-delay", 0) > 0 && seconds > 3 && now - lastDiagnostic > 2_000_000_000L) {
            lastDiagnostic = now;
            Constant.LOGGER.info("Handoff diagnostic: swapped={}, level={}, screen={}, seat={}, lights={}, age={}s",
                    swapping, mc.level == null ? null : mc.level.dimension().location(),
                    mc.screen == null ? null : mc.screen.getClass().getSimpleName(),
                    mc.player.getVehicle() == null ? null : mc.player.getVehicle().getUUID(),
                    mc.level == null ? null : mc.level.isLightUpdateQueueEmpty(), Math.round(seconds));
        }
    }

    private void prepareDestination(Minecraft mc, long now, float seconds) {
        if (readyAt != 0 || !swapping || mc.level == null || !mc.level.dimension().equals(transfer.destination())) return;
        if (arrivalVehicle != null) {
            var vehicle = mc.player.getVehicle() == null ? mc.player : mc.player.getVehicle();
            if (!vehicle.getUUID().equals(arrivalVehicle)) return;
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            if (vehicle == mc.player) {
                var gear = mc.player.galacticraft$getGearInv();
                for (int slot = 0; slot < gear.getContainerSize(); slot++) {
                    if (gear.getItem(slot).getItem() instanceof dev.galacticraft.mod.content.item.ParachuteItem) {
                        mc.player.galacticraft$setParachuteOpen(true);
                        break;
                    }
                }
            }
            mc.player.setXRot(35);
            mc.player.xRotO = 35;
            // Recheck the landing view after the rocket seat is replaced by the destination seat.
            if (((SpaceflightTerrainAccess) mc.levelRenderer).galacticraft$prepareFlightTerrain(transfer.arrivalPosition())) {
                readyAt = now;
                Constant.LOGGER.info("Spaceflight arrival vehicle mounted after {} ms", Math.round(seconds * 1000));
            }
            return;
        }
        if (terrainAcknowledged || !(mc.player.getVehicle() instanceof RocketEntity rocket) || !rocket.getUUID().equals(transfer.rocket())) return;
        if (!cameraAligned) {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            if (!rocket.isInSpaceflight()) {
                // The upright ship's belly camera looks down the actual landing corridor.
                mc.player.setXRot(65);
                mc.player.xRotO = 65;
            }
            cameraAligned = true;
        }
        double delay = Integer.getInteger("galacticraft.spaceflight.ready-delay", 0) / 20.0;
        if (seconds >= delay && ((SpaceflightTerrainAccess) mc.levelRenderer).galacticraft$prepareFlightTerrain(transfer.arrivalPosition())) {
            if (rocket.isInSpaceflight() && rocket.getFirstPassenger() == mc.player) {
                rocket.setYRot(Mth.wrapDegrees(yaw));
                rocket.setXRot(pitch + 90);
                rocket.yRotO = rocket.getYRot();
                rocket.xRotO = rocket.getXRot();
            } else {
                mc.player.setYRot(yaw);
                mc.player.yRotO = yaw;
            }
            NetworkManager.sendToServer(new SpaceflightReadyPayload(transfer.rocket(), transfer.token(), true, yaw, pitch + 90));
            terrainAcknowledged = true;
            if (rocket.isInSpaceflight()) readyAt = now;
            Constant.LOGGER.info("Spaceflight destination rendered and mounted after {} ms", Math.round(seconds * 1000));
        }
    }

    private void updateHeading(Minecraft mc, float delta) {
        // Continue responding to steering while the new level is being prepared underneath the view.
        if (readyAt == 0 && mc.player.getVehicle() instanceof RocketEntity controller && controller.getFirstPassenger() == mc.player) {
            if (mc.options.keyLeft.isDown()) yaw += delta * 24;
            if (mc.options.keyRight.isDown()) yaw -= delta * 24;
            if (mc.options.keyUp.isDown()) pitch = Mth.clamp(pitch - delta * 24, -89, 89);
            if (mc.options.keyDown.isDown()) pitch = Mth.clamp(pitch + delta * 24, -89, 89);
        } else if (mc.player.getVehicle() instanceof RocketEntity rocket) {
            if (rocket.isInSpaceflight()) {
                yaw = rocket.getYRot();
                pitch = rocket.getXRot() - 90;
            }
            // Keep the approach aimed at the planet while the downward landing view is revealed underneath.
        }
    }

    private static void blit(RenderTarget target, int width, int height, float opacity) {
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.setShaderTexture(0, target.getColorTextureId());
        int alpha = Math.round(255 * Mth.clamp(opacity, 0, 1));
        var buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        buffer.addVertex(0, height, 0).setUv(0, 0).setColor(255, 255, 255, alpha);
        buffer.addVertex(width, height, 0).setUv(1, 0).setColor(255, 255, 255, alpha);
        buffer.addVertex(width, 0, 0).setUv(1, 1).setColor(255, 255, 255, alpha);
        buffer.addVertex(0, 0, 0).setUv(0, 1).setColor(255, 255, 255, alpha);
        BufferUploader.drawWithShader(buffer.buildOrThrow());
    }
}