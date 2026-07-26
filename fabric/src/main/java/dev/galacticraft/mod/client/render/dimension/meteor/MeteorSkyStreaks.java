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

package dev.galacticraft.mod.client.render.dimension.meteor;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * The cosmetic half of a meteor shower: distant streaks burning out overhead.
 *
 * <p>These are not entities and never touch the world. They exist purely so a shower looks like a
 * shower — dozens of trails an hour at the sporadic background, hundreds at peak — while the server
 * only ever simulates the handful of real meteoroids that fall near a player. Streaks stream away
 * from the shower's synced radiant, exactly as the real bodies do.
 *
 * <p>They are drawn at a fixed distance in front of the camera with depth testing on, so terrain
 * occludes them properly, and only appear in a sky dark enough and thick enough to show them.
 */
public final class MeteorSkyStreaks {
    private static final int MAX_STREAKS = 220;
    /** Distance from the camera the streaks are drawn at, in blocks. */
    private static final double SKY_DISTANCE = 90.0;
    /** Streaks below this elevation are dropped; they would be buried in terrain anyway. */
    private static final double MIN_ELEVATION = 0.12;
    /** Streaks per tick with no shower running — roughly one every fifteen seconds. */
    private static final float SPORADIC_RATE = 0.0035f;
    /** Additional streaks per tick at a full-intensity shower peak. */
    private static final float PEAK_RATE = 0.65f;

    private static final List<Streak> STREAKS = new ArrayList<>();
    private static final RandomSource RANDOM = RandomSource.create();

    private MeteorSkyStreaks() {
    }

    /** One trail across the sky, parameterised on the unit sphere around the camera. */
    private static final class Streak {
        private final Vec3 origin;
        private final Vec3 travel;
        private final float speed;
        private final float trail;
        private final float brightness;
        private final int lifetime;
        private int age;

        private Streak(Vec3 origin, Vec3 travel, float speed, float trail, float brightness, int lifetime) {
            this.origin = origin;
            this.travel = travel;
            this.speed = speed;
            this.trail = trail;
            this.brightness = brightness;
            this.lifetime = lifetime;
        }

        /** Direction of the trail's head at a fractional age, as a unit vector. */
        private Vec3 directionAt(float at) {
            return this.origin.add(this.travel.scale(at * this.speed)).normalize();
        }

        /** Brightness envelope: fades in, peaks mid-flight, fades out. */
        private float alphaAt(float at) {
            float progress = Mth.clamp(at / this.lifetime, 0.0f, 1.0f);
            return this.brightness * Mth.sin(progress * Mth.PI);
        }
    }

    /** Advances existing streaks and spawns new ones at a rate set by the shower intensity. */
    public static void clientTick(ClientLevel level, float intensity, float radiantYaw, float radiantPitch) {
        for (int i = STREAKS.size() - 1; i >= 0; i--) {
            Streak streak = STREAKS.get(i);
            if (++streak.age >= streak.lifetime) STREAKS.remove(i);
        }

        // Meteors are only visible against a dark sky, so daylight suppresses the whole layer.
        float darkness = Mth.clamp(level.getStarBrightness(1.0f) * 2.5f, 0.0f, 1.0f);
        if (darkness <= 0.0f) return;

        float expected = (SPORADIC_RATE + intensity * PEAK_RATE) * darkness;
        int count = (int) expected;
        if (RANDOM.nextFloat() < expected - count) count++;

        boolean shower = intensity > 0.02f;
        for (int i = 0; i < count && STREAKS.size() < MAX_STREAKS; i++) {
            // Outside a shower every meteor is sporadic, so each gets its own random radiant.
            Vec3 radiant = shower
                    ? unitVector(radiantYaw, radiantPitch)
                    : unitVector(RANDOM.nextFloat() * 360.0f, 20.0f + RANDOM.nextFloat() * 65.0f);
            Streak streak = spawn(radiant, intensity);
            if (streak != null) STREAKS.add(streak);
        }
    }

    private static Streak spawn(Vec3 radiant, float intensity) {
        // Meteors appear at an angular distance from the radiant and stream directly away from it.
        double angle = Math.toRadians(12.0 + RANDOM.nextDouble() * 70.0);
        double azimuth = RANDOM.nextDouble() * Math.PI * 2.0;

        Vec3 basisU = perpendicular(radiant);
        Vec3 basisV = radiant.cross(basisU).normalize();
        Vec3 origin = radiant.scale(Math.cos(angle))
                .add(basisU.scale(Math.cos(azimuth) * Math.sin(angle)))
                .add(basisV.scale(Math.sin(azimuth) * Math.sin(angle)))
                .normalize();

        if (origin.y < MIN_ELEVATION) return null;

        // Great-circle tangent at the origin, pointing away from the radiant.
        Vec3 travel = origin.scale(Math.cos(angle)).subtract(radiant);
        if (travel.lengthSqr() < 1.0e-6) return null;
        travel = travel.normalize();

        float speed = 0.05f + RANDOM.nextFloat() * 0.09f;
        float trail = 2.5f + RANDOM.nextFloat() * 4.0f;
        float brightness = (0.35f + RANDOM.nextFloat() * 0.5f) * (0.7f + intensity * 0.3f);
        int lifetime = 10 + RANDOM.nextInt(14);
        return new Streak(origin, travel, speed, trail, brightness, lifetime);
    }

    /**
     * Draws every live streak as an additive, tapered quad on a sphere around the camera.
     *
     * <p>Takes the camera rotation matrix and builds its own pose from it, exactly as the sky
     * renderers in this package do. The streaks are positioned by world-space direction, so they
     * must be transformed by the camera's orientation or the whole field ends up pinned to the
     * screen and follows you around when you look down.
     *
     * @param cameraRotation the frustum / camera rotation matrix for this frame
     */
    public static void render(Matrix4f cameraRotation, float partialTick) {
        if (STREAKS.isEmpty()) return;

        PoseStack poses = new PoseStack();
        poses.mulPose(cameraRotation);
        poses.pushPose();
        FogRenderer.setupNoFog();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        RenderSystem.depthMask(false);
        RenderSystem.enableDepthTest();
        RenderSystem.disableCull();

        Matrix4f matrix = poses.last().pose();
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        boolean any = false;

        for (Streak streak : STREAKS) {
            float at = streak.age + partialTick;
            float alpha = streak.alphaAt(at);
            if (alpha <= 0.01f) continue;

            Vec3 headDir = streak.directionAt(at);
            Vec3 tailDir = streak.directionAt(Math.max(0.0f, at - streak.trail));
            if (headDir.y < MIN_ELEVATION && tailDir.y < MIN_ELEVATION) continue;

            Vec3 head = headDir.scale(SKY_DISTANCE);
            Vec3 tail = tailDir.scale(SKY_DISTANCE);
            Vec3 axis = head.subtract(tail);
            if (axis.lengthSqr() < 1.0e-4) continue;

            Vec3 right = axis.cross(head).normalize().scale(0.25 + alpha * 0.55);
            any = true;

            // Bright white head tapering to a dim ember tail.
            vertex(buffer, matrix, head.add(right), 1.0f, 0.97f, 0.9f, alpha);
            vertex(buffer, matrix, head.subtract(right), 1.0f, 0.97f, 0.9f, alpha);
            vertex(buffer, matrix, tail.subtract(right), 1.0f, 0.55f, 0.25f, 0.0f);
            vertex(buffer, matrix, tail.add(right), 1.0f, 0.55f, 0.25f, 0.0f);
        }

        if (any) {
            BufferUploader.drawWithShader(buffer.buildOrThrow());
        } else {
            buffer.build();
        }

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        poses.popPose();
    }

    public static void clear() {
        STREAKS.clear();
    }

    private static void vertex(BufferBuilder buffer, Matrix4f matrix, Vec3 position, float r, float g, float b, float a) {
        buffer.addVertex(matrix, (float) position.x, (float) position.y, (float) position.z).setColor(r, g, b, a);
    }

    /** Unit vector for a compass bearing and an elevation above the horizon, both in degrees. */
    private static Vec3 unitVector(float yawDegrees, float pitchDegrees) {
        double yaw = Math.toRadians(yawDegrees);
        double pitch = Math.toRadians(pitchDegrees);
        double horizontal = Math.cos(pitch);
        return new Vec3(horizontal * Math.sin(yaw), Math.sin(pitch), horizontal * Math.cos(yaw));
    }

    /** Any unit vector perpendicular to the given one. */
    private static Vec3 perpendicular(Vec3 vector) {
        Vec3 reference = Math.abs(vector.y) < 0.9 ? new Vec3(0.0, 1.0, 0.0) : new Vec3(1.0, 0.0, 0.0);
        return vector.cross(reference).normalize();
    }
}
