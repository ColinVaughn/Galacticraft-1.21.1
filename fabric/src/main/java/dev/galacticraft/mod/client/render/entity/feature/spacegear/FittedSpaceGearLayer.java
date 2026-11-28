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

import com.mojang.blaze3d.vertex.PoseStack;
import dev.galacticraft.mod.mixin.client.AnimalModelAgeableListModel;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Fits stock space gear to animated head and body anchors on models without dedicated gear layers.
 * Only renders entities whose synced state enables the gear.
 */
public class FittedSpaceGearLayer<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {
    private static final float PIXEL = 1.0F / 16.0F;

    /** Read on first use, once the model is final; renderers keep one model for life, but a few swap. */
    private @Nullable ModelGearAnchors anchors;
    private @Nullable M readFrom;

    public FittedSpaceGearLayer(RenderLayerParent<T, M> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, T entity, float limbAngle, float limbDistance,
                       float tickDelta, float animationProgress, float headYaw, float headPitch) {
        boolean mask = entity.galacticraft$hasMask();
        boolean gear = entity.galacticraft$hasGear();
        if (!mask && !gear) return;

        M model = this.getParentModel();
        if (this.anchors == null || this.readFrom != model) {
            this.anchors = ModelGearAnchors.of(model);
            this.readFrom = model;
        }
        // Nothing readable: leave the mob unmarked so the hitbox fallback dresses it instead.
        if (this.anchors.isEmpty()) return;

        boolean baby = model.young && this.anchors.ageableList() && model instanceof AnimalModelAgeableListModel;
        boolean drew = false;

        ModelGearAnchors.Anchor<SpaceGearFit.Helmet> head = this.anchors.head();
        if (mask && head != null && head.part().visible) {
            pose.pushPose();
            if (baby) babyHead(pose, (AnimalModelAgeableListModel) model);
            head.enter(pose);
            SpaceGearDrawer.drawHelmet(pose, buffers, light, head.fit());
            pose.popPose();
            drew = true;
        }

        ModelGearAnchors.Anchor<SpaceGearFit.Tanks> body = this.anchors.body();
        if (gear && body != null && body.part().visible) {
            pose.pushPose();
            if (baby) babyBody(pose, (AnimalModelAgeableListModel) model);
            body.enter(pose);
            SpaceGearDrawer.drawTanks(pose, buffers, light, body.fit(), entity.galacticraft$tankSize(0), entity.galacticraft$tankSize(1));
            pose.popPose();
            drew = true;
        }

        if (drew) SpaceGearRenderState.markDrawn(entity);
    }

    /** {@code AgeableListModel} shrinks a baby's head and body separately inside its own draw call; repeat it. */
    private static void babyHead(PoseStack pose, AnimalModelAgeableListModel model) {
        if (model.galacticraft$scalesBabyHead()) {
            float scale = 1.5F / model.galacticraft$babyHeadScale();
            pose.scale(scale, scale, scale);
        }
        pose.translate(0.0F, model.galacticraft$babyYHeadOffset() * PIXEL, model.galacticraft$babyZHeadOffset() * PIXEL);
    }

    private static void babyBody(PoseStack pose, AnimalModelAgeableListModel model) {
        float scale = 1.0F / model.galacticraft$babyBodyScale();
        pose.scale(scale, scale, scale);
        pose.translate(0.0F, model.galacticraft$bodyYOffset() * PIXEL, 0.0F);
    }
}
