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

package dev.galacticraft.mod.client.gui.overlay;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import dev.galacticraft.api.universe.celestialbody.CelestialBody;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.api.rocket.LaunchStage;
import dev.galacticraft.mod.client.GCKeyBinds;
import dev.galacticraft.mod.util.Translations;
import dev.galacticraft.mod.world.dimension.SpaceTravel;
import net.minecraft.network.chat.Component;
import dev.galacticraft.mod.content.entity.vehicle.RocketEntity;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelPart;

import java.util.concurrent.CompletableFuture;

/**
 * Displays the rocket's progress to the left of the screen
 */
public class RocketOverlay {
    /**
     * The skin of the player in the rocket.
     * Use one of the defaults if the skin cannot be found.
     */
    private static CompletableFuture<PlayerSkin> playerSkin;

    /**
     * Get the player's spaceship height off ground
     *
     * @param player thePlayer
     * @return position of player's spaceship
     */
    protected static int getPlayerPositionY(Player player) {
        if (player.getVehicle() != null && player.getVehicle() instanceof RocketEntity rocket) {
            return (int) Math.floor(rocket.getY());
        }

        return (int) Math.floor(player.getY());
    }

    public static void onHudRender(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || dev.galacticraft.mod.client.render.dimension.SpaceflightTransition.isActive()) return;
        if (mc.player.getVehicle() instanceof RocketEntity rocketEntity) {
            if (rocketEntity.isInSpaceflight()) {
                if (!mc.options.hideGui) renderSpaceflight(graphics, mc, rocketEntity);
                return;
            }
            renderLandingHint(graphics, mc, rocketEntity);
            if (playerSkin == null) {
                playerSkin = mc.getSkinManager().getOrLoad(mc.player.getGameProfile());
            }

            Holder<CelestialBody<?, ?>> body = rocketEntity.level().galacticraft$getCelestialBody();
            final int height = mc.getWindow().getGuiScaledHeight();
            RenderSystem.depthMask(true);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.setShaderTexture(0, body != null ? body.value().display().rocketOverlay() : Constant.CelestialOverlay.EARTH);

            float scale = 0.65F;
            float x0 = 0.0F;
            float x1 = x0 + 20.0F * scale;
            float y0 = height / 2 - 170 / 2;
            float y1 = y0 + 242.0F * scale;
            float u0 = 0.0F;
            float u1 = u0 + 1.0F;
            float v0 = 0.0F;
            float v1 = v0 + 1.0F;

            BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            buffer.addVertex(x0, y1, 0.0F).setUv(u0, v1)
                    .addVertex(x1, y1, 0.0F).setUv(u1, v1)
                    .addVertex(x1, y0, 0.0F).setUv(u1, v0)
                    .addVertex(x0, y0, 0.0F).setUv(u0, v0);
            BufferUploader.drawWithShader(buffer.buildOrThrow());

            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

            EntityRenderer<RocketEntity> spaceshipRender = (EntityRenderer<RocketEntity>) mc.getEntityRenderDispatcher().getRenderer(rocketEntity);

            final int h = height / 2 + 60 - (int) Math.floor(getPlayerPositionY(mc.player) / 10.5F);
            scale = 1.0F / 8.0F;
            x0 = 2.5F;
            x1 = x0 + 8.0F;
            y0 = h - 10.0F;
            y1 = y0 + 8.0F;
            u0 = 5.0F * scale;
            u1 = u0 + scale;
            v0 = scale;
            v1 = v0 + scale;

            graphics.pose().pushPose();

            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            graphics.pose().translate(x0 + 4.0F, y0 + 16.0F, 50.0F);
            graphics.pose().scale(5.0F, 5.0F, 5.0F);
            graphics.pose().mulPose(Axis.XP.rotationDegrees(180.0F));
            graphics.pose().mulPose(Axis.YP.rotationDegrees(90.0F));

            try {
                MultiBufferSource.BufferSource source = mc.renderBuffers().bufferSource();
                spaceshipRender.render(rocketEntity, 0, 0, graphics.pose(), source, LightTexture.FULL_BRIGHT);
                source.endBatch();
            } catch (Exception e) {
                e.printStackTrace();
            }

            RenderSystem.setShaderTexture(0, playerSkin.getNow(DefaultPlayerSkin.get(mc.player.getUUID())).texture());

            RenderSystem.enableBlend();
            RenderSystem.blendFunc(770, 771);

            graphics.pose().translate(0.0F, -7.0F, 60.0F);

            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            buffer.addVertex(x0, y1, 0.0F).setUv(v0, v1)
                    .addVertex(x1, y1, 0.0F).setUv(v1, v1)
                    .addVertex(x1, y0, 0.0F).setUv(v1, v0)
                    .addVertex(x0, y0, 0.0F).setUv(v0, v0);
            BufferUploader.drawWithShader(buffer.buildOrThrow());

            if (mc.player.isModelPartShown(PlayerModelPart.HAT)) {
                RenderSystem.setShader(GameRenderer::getPositionTexShader);
                buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
                buffer.addVertex(x0, y1, 0.0F).setUv(u0, v1)
                        .addVertex(x1, y1, 0.0F).setUv(u1, v1)
                        .addVertex(x1, y0, 0.0F).setUv(u1, v0)
                        .addVertex(x0, y0, 0.0F).setUv(u0, v0);
                BufferUploader.drawWithShader(buffer.buildOrThrow());
            }

            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.disableBlend();
            graphics.pose().popPose();
        }
    }
    private static void renderLandingHint(GuiGraphics graphics, Minecraft mc, RocketEntity rocket) {
        if (mc.options.hideGui || rocket.getLaunchStage() != LaunchStage.LAUNCHED) return;
        Component hint = rocket.isLanding()
                ? Component.translatable(Translations.Ui.ROCKET_LANDING, GCKeyBinds.TOGGLE_ROCKET_LANDING.getTranslatedKeyMessage())
                : Component.translatable(Translations.Ui.ROCKET_LANDING_HINT, GCKeyBinds.TOGGLE_ROCKET_LANDING.getTranslatedKeyMessage());
        graphics.drawCenteredString(mc.font, hint, graphics.guiWidth() / 2, graphics.guiHeight() - 64,
                rocket.isLanding() ? 0x7AE7D9 : 0xFFFFFF);
        if (rocket.isLanding()) {
            Component thrustHint = Component.translatable(Translations.Ui.ROCKET_LANDING_THRUST,
                    mc.options.keyJump.getTranslatedKeyMessage(), mc.options.keyShift.getTranslatedKeyMessage(),
                    Math.round(rocket.getThrust() * 100));
            graphics.drawCenteredString(mc.font, thrustHint, graphics.guiWidth() / 2, graphics.guiHeight() - 53, 0x7AE7D9);
        }
    }

    private static void renderSpaceflight(GuiGraphics graphics, Minecraft mc, RocketEntity rocket) {
        final int width = graphics.guiWidth(), height = graphics.guiHeight();
        final int ink = 0xFFD5E6EB, muted = 0xFF81959D, accent = 0xFF89CFDF;
        graphics.drawString(mc.font, Component.translatable("ui.galacticraft.space.system"), 16, 16, muted, false);
        graphics.fill(16, 29, 78, 30, 0x707D9DA8);

        var camera = mc.gameRenderer.getMainCamera();
        var inverse = new org.joml.Quaternionf(camera.rotation()).conjugate();
        double focal = height / (2 * Math.tan(Math.toRadians(mc.options.fov().get()) / 2));
        double closestAngle = -1;
        SpaceTravel.Destination focus = null;
        var forward = SpaceTravel.forward(rocket.getXRot(), rocket.getYRot());
        for (var target : SpaceTravel.destinations(rocket.registryAccess())) {
            if (target.resolve(rocket.registryAccess()) == null) continue;
            double alignment = target.center().subtract(camera.getPosition()).normalize().dot(forward);
            if (alignment > closestAngle) { closestAngle = alignment; focus = target; }
        }
        for (var target : SpaceTravel.destinations(rocket.registryAccess())) {
            var body = target.resolve(rocket.registryAccess());
            if (body == null) continue;
            var direction = target.center().subtract(camera.getPosition());
            var view = direction.toVector3f().rotate(inverse);
            if (view.z >= -1) continue;
            int x = (int) (width / 2.0 + focal * view.x / -view.z);
            int y = (int) (height / 2.0 - focal * view.y / -view.z);
            int radius = (int) Math.min(68, Math.max(8, focal * target.radius() / -view.z));
            if (x < 20 || x > width - 20 || y < 44 || y > height - 94) continue;
            boolean selected = target.equals(focus);
            int color = selected ? accent : 0x7081959D;
            graphics.fill(x - radius - 4, y - 3, x - radius - 3, y + 4, color);
            graphics.fill(x + radius + 3, y - 3, x + radius + 4, y + 4, color);
        }
        if (focus != null) {
            var body = focus.resolve(rocket.registryAccess());
            if (body != null) {
                Component name = body.name();
                Component range = Component.translatable("ui.galacticraft.space.range",
                        Math.max(0, Math.round(focus.distance(rocket.position(), SpaceTravel.days(rocket.level())))));
                graphics.drawString(mc.font, name, width - 16 - mc.font.width(name), 16, ink, false);
                graphics.drawString(mc.font, range, width - 16 - mc.font.width(range), 30, muted, false);
            }
        }
        int fuel = (int) Math.round(100.0 * rocket.getFuel() / Math.max(1, rocket.getFuelTankCapacity()));
        int panelY = height - 89;
        graphics.drawString(mc.font, Component.translatable("ui.galacticraft.space.speed",
                Math.round(rocket.position().subtract(rocket.xo, rocket.yo, rocket.zo).length() * 20)), 16, panelY, ink, false);
        graphics.drawString(mc.font, Component.translatable("ui.galacticraft.space.fuel", fuel), 16, panelY + 14, muted, false);
        graphics.fill(16, panelY + 27, 96, panelY + 29, 0x50495E67);
        graphics.fill(16, panelY + 27, 16 + Math.clamp(fuel, 0, 100) * 80 / 100, panelY + 29,
                fuel < 15 ? 0xFFDD9068 : accent);
        graphics.fill(width / 2 - 9, height / 2, width / 2 - 4, height / 2 + 1, 0x8089CFDF);
        graphics.fill(width / 2 + 4, height / 2, width / 2 + 9, height / 2 + 1, 0x8089CFDF);
        graphics.drawCenteredString(mc.font, Component.translatable("ui.galacticraft.space.controls",
                mc.options.keyUp.getTranslatedKeyMessage(), mc.options.keyDown.getTranslatedKeyMessage(),
                mc.options.keyLeft.getTranslatedKeyMessage(), mc.options.keyRight.getTranslatedKeyMessage(),
                mc.options.keyJump.getTranslatedKeyMessage(), mc.options.keyShift.getTranslatedKeyMessage()),
                width / 2, height - 55, muted);
        if (rocket.isBoostLocked())
            graphics.drawCenteredString(mc.font, Component.translatable("ui.galacticraft.space.boost_locked",
                    mc.options.keyJump.getTranslatedKeyMessage(), mc.options.keyShift.getTranslatedKeyMessage()),
                    width / 2, height - 67, accent);
    }

}
