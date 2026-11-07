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

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import dev.galacticraft.api.universe.celestialbody.SkyConfig;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.client.render.ShaderPackCompat;
import dev.galacticraft.mod.client.render.dimension.star.CelestialBodyRendererManager;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;

/**
 * Registry-driven sky renderer for dimensions using {@code galacticraft:space} effects.
 * Uses {@link SkyConfig} for star size and {@link SpaceDimensionEffects} for sky color.
 */
public class ConfigurableSkyRenderer extends SpaceSkyRenderer {
    public static final ConfigurableSkyRenderer INSTANCE = new ConfigurableSkyRenderer();

    private static final float CELESTIAL_DISTANCE = 100.0F;

    @Override
    public void render(GCWorldRenderContext context) {
        SkyConfig sky = SpaceDimensionEffects.sky(context.world());
        RenderSystem.depthMask(false);
        RenderSystem.disableBlend();
        FogRenderer.setupNoFog();

        float partialTicks = context.tickCounter().getGameTimeDeltaPartialTick(true);
        float skyAngle = context.world().getTimeOfDay(partialTicks) * 360.0F;

        PoseStack celestialMatrices = new PoseStack();
        celestialMatrices.mulPose(context.positionMatrix());
        celestialMatrices.mulPose(Axis.ZP.rotationDegrees(skyAngle));

        if (context.world().dimension().equals(dev.galacticraft.mod.world.dimension.GCDimensions.JUPITER)
                && JupiterDimensionEffects.coreHeat(net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().y) > 0) {
            // Inside the core nothing from above shows through: no sun, no bodies, just the glow.
            renderJupiterCore(context, partialTicks);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.depthMask(true);
            return;
        }

        float stars = SpaceDimensionEffects.INSTANCE.getStarBrightness(context.world(), partialTicks);
        if (context.world().dimension().equals(dev.galacticraft.mod.world.dimension.GCDimensions.JUPITER)) {
            // The playable cloud decks are still inside an optically thick atmosphere.
            stars = 0;
        }
        if (stars > 0.0F) {
            context.profiler().push("space_sky_bodies");
            RenderSystem.setShaderColor(stars, stars, stars, 1.0F);
            CelestialBodyRendererManager bodies = this.celestialBodyRendererManager(context);
            bodies.updateSolarPosition(0.0, 0.0, 0.0);
            bodies.render(context, celestialMatrices.last().pose());
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            context.profiler().pop();
        }

        if (!SurfaceCelestialRenderer.render(context) && sky.sunSize() > 0.0F) {
            context.profiler().push("space_sky_sun");
            ShaderPackCompat.renderSun(context.positionMatrix(), skyAngle, celestialMatrices.last().pose(), matrix -> {
                RenderSystem.setShader(GameRenderer::getPositionTexShader);
                RenderSystem.setShaderTexture(0, Constant.Skybox.SUN_MOON);
                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                RenderSystem.enableBlend();
                RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
                drawQuad(matrix, sky.sunSize());
                RenderSystem.disableBlend();
            });
            context.profiler().pop();
        }

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(true);
    }

    /**
     * Blends the core dome from a dim overhead glow to bright light below, matching the horizon fog.
     */
    private static void renderJupiterCore(GCWorldRenderContext context, float partialTicks) {
        double heat = JupiterDimensionEffects.coreHeat(net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().y);
        if (heat <= 0) return;
        net.minecraft.world.phys.Vec3 haze = JupiterDimensionEffects.INSTANCE.coreFog(context.world(), partialTicks);
        net.minecraft.world.phys.Vec3 above = haze.multiply(1 - 0.6 * heat, 1 - 0.75 * heat, 1 - 0.85 * heat);
        net.minecraft.world.phys.Vec3 below = haze.lerp(new net.minecraft.world.phys.Vec3(1.0, 0.93, 0.78), 0.65 * heat);
        double[] elevation = {90, 50, 20, 6, 0, -6, -20, -50, -90};
        net.minecraft.world.phys.Vec3[] colour = new net.minecraft.world.phys.Vec3[elevation.length];
        for (int i = 0; i < elevation.length; i++) {
            double e = elevation[i];
            colour[i] = e >= 0 ? haze.lerp(above, Math.pow(e / 90.0, 0.6)) : haze.lerp(below, Math.pow(-e / 90.0, 0.6));
        }
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Matrix4f matrix = context.positionMatrix();
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        int segments = 24;
        for (int ring = 0; ring < elevation.length - 1; ring++) {
            for (int seg = 0; seg < segments; seg++) {
                double a0 = seg * Math.PI * 2 / segments, a1 = (seg + 1) * Math.PI * 2 / segments;
                skyVertex(buffer, matrix, a0, elevation[ring], colour[ring]);
                skyVertex(buffer, matrix, a1, elevation[ring], colour[ring]);
                skyVertex(buffer, matrix, a1, elevation[ring + 1], colour[ring + 1]);
                skyVertex(buffer, matrix, a0, elevation[ring + 1], colour[ring + 1]);
            }
        }
        RenderSystem.disableCull();
        BufferUploader.drawWithShader(buffer.buildOrThrow());
        RenderSystem.enableCull();
    }

    private static void skyVertex(BufferBuilder buffer, Matrix4f matrix, double azimuth, double elevationDegrees, net.minecraft.world.phys.Vec3 colour) {
        double e = Math.toRadians(elevationDegrees);
        float r = CELESTIAL_DISTANCE * 1.5F;
        buffer.addVertex(matrix, (float) (Math.cos(azimuth) * Math.cos(e) * r), (float) (Math.sin(e) * r), (float) (Math.sin(azimuth) * Math.cos(e) * r))
                .setColor((float) colour.x, (float) colour.y, (float) colour.z, 1.0F);
    }

    private static void drawQuad(Matrix4f matrix, float size) {
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        buffer.addVertex(matrix, -size, CELESTIAL_DISTANCE, -size).setUv(0.0F, 0.0F)
                .addVertex(matrix, size, CELESTIAL_DISTANCE, -size).setUv(1.0F, 0.0F)
                .addVertex(matrix, size, CELESTIAL_DISTANCE, size).setUv(1.0F, 1.0F)
                .addVertex(matrix, -size, CELESTIAL_DISTANCE, size).setUv(0.0F, 1.0F);
        BufferUploader.drawWithShader(buffer.buildOrThrow());
    }
}
