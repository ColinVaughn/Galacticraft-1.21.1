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

package dev.galacticraft.mod.client.render.dimension;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.client.model.GCRenderTypes;
import dev.galacticraft.mod.client.render.dimension.star.CelestialBodyRendererManager;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;

public class MoonSkyRenderer extends SpaceSkyRenderer {
    public static final MoonSkyRenderer INSTANCE = new MoonSkyRenderer();

    private static final float CELESTIAL_DISTANCE = 100.0F;
    // At this distance the bright center of sun_moon.png is about 0.6 degrees
    // across. Earth uses a modest perceptual boost over its real 1.9-degree
    // diameter so the low-resolution legacy sprite remains readable at the
    // wide fields of view commonly used in Minecraft.
    private static final float SUN_SIZE = 2.0F;
    private static final float EARTH_SIZE = 3.0F;
    // The Moon is tidally locked, so Earth should remain at one point in the
    // sky instead of following the day/night rotation like the Sun.
    private static final float EARTH_AZIMUTH = 32.0F;
    private static final float EARTH_ZENITH_ANGLE = 46.0F;

    private VertexBuffer galaxyBuffer;

    @Override
    public void render(GCWorldRenderContext context) {
        RenderSystem.depthMask(false);
        RenderSystem.disableBlend();
        FogRenderer.setupNoFog();

        float partialTicks = context.tickCounter().getGameTimeDeltaPartialTick(true);
        float skyAngle = context.world().getTimeOfDay(partialTicks) * 360.0F;

        PoseStack celestialMatrices = new PoseStack();
        celestialMatrices.mulPose(context.positionMatrix());
        celestialMatrices.mulPose(Axis.ZP.rotationDegrees(skyAngle));

        context.profiler().push("moon_milky_way");
        this.renderMilkyWay(context, celestialMatrices.last().pose());
        context.profiler().pop();

        context.profiler().push("moon_stars");
        CelestialBodyRendererManager celestialBodyRendererManager = this.celestialBodyRendererManager(context);
        celestialBodyRendererManager.updateSolarPosition(0.0, 0.0, 0.0);
        celestialBodyRendererManager.render(context, celestialMatrices.last().pose());
        context.profiler().pop();

        context.profiler().push("moon_sun");
        this.renderSun(celestialMatrices.last().pose());
        context.profiler().pop();

        context.profiler().push("moon_earth");
        this.renderEarth(context);
        context.profiler().pop();

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(true);
    }

    private void renderMilkyWay(GCWorldRenderContext context, Matrix4f celestialMatrix) {
        ShaderInstance shader = GCRenderTypes.getSpaceGalaxyShader();
        if (shader == null) return;

        if (this.galaxyBuffer == null) {
            this.galaxyBuffer = createGalaxyBuffer();
        }

        shader.safeGetUniform("Exposure").set(LunarSkyExposure.value());
        RenderSystem.setShader(() -> shader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableBlend();
        RenderSystem.disableCull();

        this.galaxyBuffer.bind();
        this.galaxyBuffer.drawWithShader(celestialMatrix, context.projectionMatrix(), shader);
        VertexBuffer.unbind();

        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private void renderSun(Matrix4f celestialMatrix) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, Constant.Skybox.SUN_MOON);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
        drawTexturedBody(celestialMatrix, SUN_SIZE);
        RenderSystem.disableBlend();
    }

    private void renderEarth(GCWorldRenderContext context) {
        PoseStack earthMatrices = new PoseStack();
        earthMatrices.mulPose(context.positionMatrix());
        earthMatrices.mulPose(Axis.YP.rotationDegrees(EARTH_AZIMUTH));
        earthMatrices.mulPose(Axis.ZP.rotationDegrees(EARTH_ZENITH_ANGLE));

        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, Constant.CelestialBody.EARTH);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableBlend();
        // The legacy Earth sprite has black opaque corners. Additive blending
        // makes those corners transparent against the starfield without a
        // custom fragment shader.
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
        drawTexturedBody(earthMatrices.last().pose(), EARTH_SIZE);
        RenderSystem.disableBlend();
    }

    private static void drawTexturedBody(Matrix4f matrix, float size) {
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        buffer.addVertex(matrix, -size, CELESTIAL_DISTANCE, -size).setUv(0.0F, 0.0F)
                .addVertex(matrix, size, CELESTIAL_DISTANCE, -size).setUv(1.0F, 0.0F)
                .addVertex(matrix, size, CELESTIAL_DISTANCE, size).setUv(1.0F, 1.0F)
                .addVertex(matrix, -size, CELESTIAL_DISTANCE, size).setUv(0.0F, 1.0F);
        BufferUploader.drawWithShader(buffer.buildOrThrow());
    }

    private static VertexBuffer createGalaxyBuffer() {
        float size = 120.0F;
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);

        buffer.addVertex(-size, -size, -size).addVertex(-size, -size, size).addVertex(-size, size, size).addVertex(-size, size, -size);
        buffer.addVertex(size, -size, size).addVertex(size, -size, -size).addVertex(size, size, -size).addVertex(size, size, size);
        buffer.addVertex(-size, -size, size).addVertex(size, -size, size).addVertex(size, size, size).addVertex(-size, size, size);
        buffer.addVertex(size, -size, -size).addVertex(-size, -size, -size).addVertex(-size, size, -size).addVertex(size, size, -size);
        buffer.addVertex(-size, size, size).addVertex(size, size, size).addVertex(size, size, -size).addVertex(-size, size, -size);
        buffer.addVertex(-size, -size, -size).addVertex(size, -size, -size).addVertex(size, -size, size).addVertex(-size, -size, size);

        VertexBuffer vertexBuffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
        vertexBuffer.bind();
        vertexBuffer.upload(buffer.buildOrThrow());
        VertexBuffer.unbind();
        return vertexBuffer;
    }
}
