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

package dev.galacticraft.mod.client.render.dimension.star.display;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import dev.galacticraft.mod.client.model.GCRenderTypes;
import dev.galacticraft.mod.client.render.dimension.LunarSkyExposure;
import dev.galacticraft.mod.client.render.dimension.star.GeographicalSolarPosition;
import dev.galacticraft.mod.client.render.dimension.star.data.CelestialBody;
import dev.galacticraft.mod.client.render.dimension.star.data.StarData;
import dev.galacticraft.mod.client.render.dimension.GCWorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Renderer for stars.
 */
public class StarRenderer implements CelestialBodyRenderer {
    private static final double STAR_RENDER_DISTANCE = 850.0;

    private VertexBuffer starBuffer;
    private final GeographicalSolarPosition cameraRenderPosition;
    private double bufferedCameraX = Double.NaN;
    private double bufferedCameraY = Double.NaN;
    private double bufferedCameraZ = Double.NaN;
    private int bufferedBodyCount = -1;

    public StarRenderer(GeographicalSolarPosition cameraRenderPosition) {
        this.cameraRenderPosition = cameraRenderPosition;
    }

    @Override
    public void setupBufferPositions(List<CelestialBody> bodies) {
        double cameraX = cameraRenderPosition.getX();
        double cameraY = cameraRenderPosition.getY();
        double cameraZ = cameraRenderPosition.getZ();

        // The Moon uses a fixed astronomical camera. Its old renderer rebuilt
        // and uploaded thousands of identical star quads every frame.
        if (this.starBuffer != null
                && this.bufferedBodyCount == bodies.size()
                && Double.compare(this.bufferedCameraX, cameraX) == 0
                && Double.compare(this.bufferedCameraY, cameraY) == 0
                && Double.compare(this.bufferedCameraZ, cameraZ) == 0) {
            return;
        }

        if (this.starBuffer == null) {
            this.starBuffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
        }

        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

        // Calculate the direction from the star to the camera
        Vec3 cameraPos = new Vec3(cameraX, cameraY, cameraZ);


        for (CelestialBody body : bodies) {
            if (body instanceof StarData star) {

                double starBrightness = star.getBrightness();
                double x = star.getX() - cameraPos.x;
                double y = star.getY() - cameraPos.y;
                double z = star.getZ() - cameraPos.z;

                Vec3 starToCamera = new Vec3(x, y, z); // Star's position relative to camera

                Vec3 starPos = new Vec3(star.getX(), star.getY(), star.getZ());

                double distance = starPos.distanceTo(cameraPos);

                if (distance < 4.0 || distance > STAR_RENDER_DISTANCE) {
                    continue;
                }

                // Only visually bright stars reveal much temperature color to a
                // dark-adapted human eye. Rotation is a deterministic color hint.
                double temperature = star.getRotation() / 360.0;
                float r = 0.975F;
                float g = 0.985F;
                float b = 1.0F;
                float colorStrength = (float) Math.max(0.0, Math.min(1.0, (starBrightness - 0.45) / 0.45));
                if (temperature < 0.10) {
                    r = 1.0F;
                    g = 1.0F - 0.14F * colorStrength;
                    b = 1.0F - 0.27F * colorStrength;
                } else if (temperature > 0.90) {
                    r = 1.0F - 0.20F * colorStrength;
                    g = 1.0F - 0.10F * colorStrength;
                }
                float a = (float) starBrightness;

                // UV0 identifies the four corners. Their positions intentionally
                // coincide; space_star.vsh expands them by a fixed pixel radius.
                buffer.addVertex((float) starToCamera.x, (float) starToCamera.y, (float) starToCamera.z)
                        .setUv(0.0F, 0.0F).setColor(r, g, b, a);
                buffer.addVertex((float) starToCamera.x, (float) starToCamera.y, (float) starToCamera.z)
                        .setUv(1.0F, 0.0F).setColor(r, g, b, a);
                buffer.addVertex((float) starToCamera.x, (float) starToCamera.y, (float) starToCamera.z)
                        .setUv(1.0F, 1.0F).setColor(r, g, b, a);
                buffer.addVertex((float) starToCamera.x, (float) starToCamera.y, (float) starToCamera.z)
                        .setUv(0.0F, 1.0F).setColor(r, g, b, a);

            }
        }

        this.starBuffer.bind();
        this.starBuffer.upload(buffer.build());
        VertexBuffer.unbind();

        this.bufferedCameraX = cameraX;
        this.bufferedCameraY = cameraY;
        this.bufferedCameraZ = cameraZ;
        this.bufferedBodyCount = bodies.size();

    }

    @Override
    public void render(CelestialBody body, GCWorldRenderContext worldRenderContext) {
        // Individual star rendering is not used
        // Stars are rendered all at once in renderAll
    }

    @Override
    public void renderAll(List<CelestialBody> bodies, GCWorldRenderContext worldRenderContext) {
        this.renderAll(bodies, worldRenderContext, new Matrix4f(worldRenderContext.positionMatrix()));
    }

    public void renderAll(List<CelestialBody> bodies, GCWorldRenderContext worldRenderContext, Matrix4f celestialMatrix) {
        // Setup buffer positions once for all bodies
        this.setupBufferPositions(bodies);

        // Render all stars at once
        ShaderInstance shader = GCRenderTypes.getSpaceStarShader();
        if (starBuffer != null && shader != null) {
            shader.safeGetUniform("ScreenSize").set(
                    (float) Minecraft.getInstance().getWindow().getWidth(),
                    (float) Minecraft.getInstance().getWindow().getHeight()
            );
            shader.safeGetUniform("PointScale").set(1.0F);
            shader.safeGetUniform("RenderMode").set(0.0F);
            shader.safeGetUniform("Exposure").set(LunarSkyExposure.value());
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.enableBlend();
            RenderSystem.disableCull();
            FogRenderer.setupNoFog();

            this.starBuffer.bind();
            this.starBuffer.drawWithShader(
                    celestialMatrix,
                    worldRenderContext.projectionMatrix(),
                    shader
            );
            VertexBuffer.unbind();
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
        }
    }
}
