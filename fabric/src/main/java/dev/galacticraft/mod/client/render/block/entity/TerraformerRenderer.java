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
import dev.galacticraft.mod.content.block.entity.machine.TerraformerBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;

public class TerraformerRenderer implements BlockEntityRenderer<TerraformerBlockEntity> {
    public static final ResourceLocation MODEL = Constant.id("models/misc/terraformer_sphere.json");
    private static final int VIEW_DISTANCE = 256;
    // Legacy BubbleRenderer packed an alpha of 30 into every terraformer bubble.
    static final int LEGACY_GREEN = 0x1E208020;

    public TerraformerRenderer(BlockEntityRendererProvider.Context context) {
    }

    public static AABB bubbleBounds(BlockPos pos, double size) {
        return new AABB(
                pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D
        ).inflate(Math.max(size, 0.0D));
    }

    @Override
    public void render(TerraformerBlockEntity machine, float tickDelta, PoseStack matrices,
                       MultiBufferSource vertexConsumers, int light, int overlay) {
        if (!machine.isBubbleVisible() || machine.getBubbleSize() <= 0.0D) {
            return;
        }
        GCModel model = GCModelLoader.INSTANCE.getModel(MODEL);
        if (model == GCModelLoader.MISSING_MODEL) {
            return;
        }

        float size = (float) machine.getBubbleSize();
        matrices.pushPose();
        matrices.translate(0.5F, 0.5F, 0.5F);
        matrices.scale(size, size, size);
        model.render(matrices, null, vertexConsumers.getBuffer(GCRenderTypes.bubble(GCRenderTypes.OBJ_ATLAS)),
                light, OverlayTexture.NO_OVERLAY, LEGACY_GREEN);
        matrices.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(TerraformerBlockEntity machine) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return VIEW_DISTANCE;
    }
}
