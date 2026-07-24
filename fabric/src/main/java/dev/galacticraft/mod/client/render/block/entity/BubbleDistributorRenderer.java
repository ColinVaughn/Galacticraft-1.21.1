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

package dev.galacticraft.mod.client.render.block.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.client.model.GCModel;
import dev.galacticraft.mod.client.model.GCModelLoader;
import dev.galacticraft.mod.client.model.GCRenderTypes;
import dev.galacticraft.mod.content.block.entity.machine.OxygenBubbleDistributorBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;

public class BubbleDistributorRenderer implements BlockEntityRenderer<OxygenBubbleDistributorBlockEntity> {
    public static final ResourceLocation MODEL = Constant.id("models/misc/sphere.json");
    /**
     * Large bubbles stay on screen long after the machine block itself is out of the default 64 block
     * range, which is measured from the machine and not from the surface of the sphere.
     */
    private static final int VIEW_DISTANCE = 256;

    public BubbleDistributorRenderer(BlockEntityRendererProvider.Context context) {
    }

    /**
     * The volume the bubble occupies, used for frustum culling. The model is a unit sphere anchored to
     * the top face of the machine and scaled by the bubble size, so it reaches {@code size} blocks in
     * every direction from that point - far outside the machine's own block.
     */
    public static AABB bubbleBounds(BlockPos pos, double size) {
        return new AABB(pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D, pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D)
                .inflate(Math.max(size, 0.0D));
    }

    @Override
    public void render(OxygenBubbleDistributorBlockEntity machine, float tickDelta, PoseStack matrices, MultiBufferSource vertexConsumers, int light, int overlay) {
        if (machine.isDisabled() || !machine.isBubbleVisible()) {
            return;
        }
        GCModel bubbleModel = GCModelLoader.INSTANCE.getModel(MODEL);
        if (bubbleModel == GCModelLoader.MISSING_MODEL) {
            return;
        }
        double size = machine.getSize();

        matrices.pushPose();
        matrices.translate(0.5F, 1.0F, 0.5F);
        matrices.scale((float) size, (float) size, (float) size);

        bubbleModel.render(matrices, null, vertexConsumers.getBuffer(GCRenderTypes.bubble(GCRenderTypes.OBJ_ATLAS)), light, OverlayTexture.NO_OVERLAY);

        matrices.popPose();
    }

    /**
     * Keeps the bubble out of the per-section culling pass. A sectioned block entity renderer only runs
     * while the section holding the machine is visible, which drops the sphere the moment you look away
     * from the machine even though most of the bubble is still on screen.
     */
    @Override
    public boolean shouldRenderOffScreen(OxygenBubbleDistributorBlockEntity machine) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return VIEW_DISTANCE;
    }
}
