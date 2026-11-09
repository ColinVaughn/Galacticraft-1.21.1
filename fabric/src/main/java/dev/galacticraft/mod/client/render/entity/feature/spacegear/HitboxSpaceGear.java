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

package dev.galacticraft.mod.client.render.entity.feature.spacegear;

import com.mojang.math.Axis;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import org.joml.Matrix3f;
import org.joml.Quaternionf;

/**
 * Fallback space gear placement for renderers without model anchors. Uses hitbox dimensions
 * and head/body rotations in vanilla model space.
 */
public final class HitboxSpaceGear {
    private static final float PIXEL = 1.0F / 16.0F;
    /** Where vanilla puts model space's origin above the feet, in pixels. */
    private static final float MODEL_ORIGIN = 24.0F;
    private static final float MIN_HEAD = 0.25F;
    private static final float MAX_HEAD = 1.5F;

    private HitboxSpaceGear() {}

    /**
     * Approximates a body from its hitbox, keeping tanks outside its back or top surface.
     *
     * @param width hitbox width, in pixels
     * @param height hitbox height, in pixels
     */
    public static SpaceGearFit.Box body(float width, float height) {
        return height >= 2 * width
                // Tall, like a humanoid: a torso across the upper half, tanks on the back between the shoulders.
                ? new SpaceGearFit.Box(-width * 0.45F, MODEL_ORIGIN - height * 0.8F, -width * 0.5F,
                        width * 0.45F, MODEL_ORIGIN - height * 0.3F, width * 0.5F)
                // Anything stockier is all body: tanks on the back of a blocky mob, on top of a flat one.
                : new SpaceGearFit.Box(-width * 0.45F, MODEL_ORIGIN - height, -width * 0.5F,
                        width * 0.45F, MODEL_ORIGIN, width * 0.5F);
    }

    /**
     * @param pose positioned at the entity, as {@code EntityRenderDispatcher} leaves it for its renderer
     */
    public static void draw(LivingEntity entity, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        // A multipart dragon's hitbox has nothing to do with where its head is.
        if (entity instanceof EnderDragon) return;

        float width = entity.getBbWidth() * 16;
        float height = entity.getBbHeight() * 16;
        float bodyYaw = Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot);

        pose.pushPose();
        // The same steps LivingEntityRenderer takes into model space.
        pose.mulPose(Axis.YP.rotationDegrees(180.0F - bodyYaw));
        pose.scale(-1.0F, -1.0F, 1.0F);
        pose.translate(0.0F, -1.501F, 0.0F);

        if (entity.galacticraft$hasMask()) {
            float side = Mth.clamp(entity.getBbWidth() * 0.75F, MIN_HEAD, MAX_HEAD) * 16;
            SpaceGearFit.helmet(new SpaceGearFit.Box(-side / 2, -side / 2, -side / 2, side / 2, side / 2, side / 2)).ifPresent(helmet -> {
                float headYaw = Mth.rotLerp(partialTick, entity.yHeadRotO, entity.yHeadRot) - bodyYaw;
                float pitch = Mth.lerp(partialTick, entity.xRotO, entity.getXRot());
                pose.pushPose();
                pose.translate(0.0F, (MODEL_ORIGIN - entity.getEyeHeight() * 16) * PIXEL, 0.0F);
                pose.mulPose(new Quaternionf().rotationZYX(0.0F, headYaw * Mth.DEG_TO_RAD, pitch * Mth.DEG_TO_RAD));
                SpaceGearDrawer.drawHelmet(pose, buffers, light, helmet);
                pose.popPose();
            });
        }

        if (entity.galacticraft$hasGear()) {
            SpaceGearFit.tanks(body(width, height), new Matrix3f()).ifPresent(tanks ->
                    SpaceGearDrawer.drawTanks(pose, buffers, light, tanks, entity.galacticraft$tankSize(0), entity.galacticraft$tankSize(1)));
        }
        pose.popPose();
    }
}
