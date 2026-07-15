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

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import org.joml.Matrix4f;
import org.joml.Vector3d;

import java.util.Random;

/** A deterministic, colored star field with a denser galactic band. */
public class StarManager {
    private static final long STAR_FIELD_SEED = 27893L;
    private static final int UNIFORM_STAR_COUNT = 8000;
    private static final int GALACTIC_STAR_COUNT = 4000;
    private static final double STAR_FIELD_RADIUS = 100.0;
    private static final double GALACTIC_BAND_WIDTH = Math.toRadians(7.0);
    private static final double GALACTIC_BAND_TILT = Math.toRadians(63.0);

    private final VertexBuffer starBuffer;

    public StarManager() {
        this.starBuffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
        this.starBuffer.bind();
        this.starBuffer.upload(buildStarField(STAR_FIELD_SEED, UNIFORM_STAR_COUNT, GALACTIC_STAR_COUNT, STAR_FIELD_RADIUS));
        VertexBuffer.unbind();
    }

    public static MeshData buildStarField(long seed, int uniformStarCount, int galacticStarCount, double radius) {
        Random random = new Random(seed);
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        for (int i = 0; i < uniformStarCount; ++i) {
            addStar(buffer, random, randomSphereDirection(random), radius);
        }
        for (int i = 0; i < galacticStarCount; ++i) {
            addStar(buffer, random, galacticBandDirection(random), radius);
        }
        return buffer.buildOrThrow();
    }

    private static void addStar(BufferBuilder buffer, Random random, Vector3d direction, double radius) {
        double apparentBrightness = 0.12 + Math.pow(random.nextDouble(), 5.5) * 0.88;
        double size = 0.07 + apparentBrightness * 0.20;
        if (random.nextDouble() < 0.006) {
            size *= 1.7;
        }

        Vector3d center = new Vector3d(direction).mul(radius);
        Vector3d reference = Math.abs(direction.y) < 0.9
                ? new Vector3d(0.0, 1.0, 0.0)
                : new Vector3d(1.0, 0.0, 0.0);
        Vector3d right = new Vector3d(direction).cross(reference).normalize();
        // Inward-facing winding keeps the stars visible with sky culling enabled.
        Vector3d up = new Vector3d(direction).cross(right).normalize();
        double rotation = random.nextDouble() * Math.PI * 2.0;
        double sin = Math.sin(rotation);
        double cos = Math.cos(rotation);

        float[] color = stellarColor(random.nextDouble());
        float intensity = (float) (0.58 + apparentBrightness * 0.42);
        int red = Math.round(255.0F * color[0] * intensity);
        int green = Math.round(255.0F * color[1] * intensity);
        int blue = Math.round(255.0F * color[2] * intensity);
        int alpha = Math.round((float) (90.0 + apparentBrightness * 165.0));

        for (int corner = 0; corner < 4; corner++) {
            double cornerX = ((corner & 2) - 1) * size;
            double cornerY = ((corner + 1 & 2) - 1) * size;
            double rotatedX = cornerX * cos - cornerY * sin;
            double rotatedY = cornerY * cos + cornerX * sin;
            Vector3d vertex = new Vector3d(center)
                    .add(new Vector3d(right).mul(rotatedX))
                    .add(new Vector3d(up).mul(rotatedY));
            buffer.addVertex((float) vertex.x, (float) vertex.y, (float) vertex.z)
                    .setColor(red, green, blue, alpha);
        }
    }

    private static Vector3d randomSphereDirection(Random random) {
        double y = random.nextDouble() * 2.0 - 1.0;
        double longitude = random.nextDouble() * Math.PI * 2.0;
        double horizontal = Math.sqrt(1.0 - y * y);
        return new Vector3d(horizontal * Math.cos(longitude), y, horizontal * Math.sin(longitude));
    }

    private static Vector3d galacticBandDirection(Random random) {
        double longitude = random.nextDouble() * Math.PI * 2.0;
        double latitude = Mth.clamp(random.nextGaussian() * GALACTIC_BAND_WIDTH, -0.4, 0.4);
        double x = Math.cos(latitude) * Math.cos(longitude);
        double y = Math.sin(latitude);
        double z = Math.cos(latitude) * Math.sin(longitude);
        double tiltedY = y * Math.cos(GALACTIC_BAND_TILT) - z * Math.sin(GALACTIC_BAND_TILT);
        double tiltedZ = y * Math.sin(GALACTIC_BAND_TILT) + z * Math.cos(GALACTIC_BAND_TILT);
        return new Vector3d(x, tiltedY, tiltedZ).normalize();
    }

    static float[] stellarColor(double colorHint) {
        if (colorHint < 0.08) return new float[]{1.0F, 0.72F, 0.52F};
        if (colorHint < 0.24) return new float[]{1.0F, 0.88F, 0.72F};
        if (colorHint < 0.76) return new float[]{0.96F, 0.98F, 1.0F};
        return new float[]{0.72F, 0.84F, 1.0F};
    }

    public void tick() {

    }

    public void render(PoseStack poseStack, Matrix4f projectionMatrix, Level level, float partialTicks, float brightness) {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, brightness);
        FogRenderer.setupNoFog();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        this.starBuffer.bind();
        this.starBuffer.drawWithShader(poseStack.last().pose(), projectionMatrix, GameRenderer.getPositionColorShader());
        VertexBuffer.unbind();
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    public void render(PoseStack poseStack, Matrix4f projectionMatrix, Level level, float partialTicks) {
        this.render(poseStack, projectionMatrix, level, partialTicks, getStarBrightness(level, partialTicks));
    }

    public float getStarBrightness(Level world, float delta) {
        final float skyAngle = world.getTimeOfDay(delta);
        float brightness = Mth.clamp(1.0F - (Mth.cos((float) (skyAngle * Math.PI * 2.0D) * 2.0F + 0.25F)), 0.0F, 1.0F);
        return brightness * brightness * 0.5F + 0.3F;
    }
}
