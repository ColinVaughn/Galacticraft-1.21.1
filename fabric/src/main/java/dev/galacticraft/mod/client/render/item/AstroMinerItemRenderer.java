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

package dev.galacticraft.mod.client.render.item;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.client.model.GCModel;
import dev.galacticraft.mod.client.model.GCModelLoader;
import dev.galacticraft.mod.client.model.GCRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Renders the {@code astro_miner} item using its OBJ entity model, since the vehicle has no flat
 * inventory icon. The item model json ({@code models/item/astro_miner.json}) delegates here via
 * {@code minecraft:builtin/entity}.
 */
public class AstroMinerItemRenderer {
    public static final ResourceLocation MODEL = Constant.id("models/misc/astro_miner.json");

    // Geometry center of astro_miner.obj; translated to the origin so framing rotates about the middle.
    private static final float CENTER_X = 0.0F;
    private static final float CENTER_Y = -0.47F;
    private static final float CENTER_Z = -7.58F;

    private GCModel model;

    public void render(ItemStack stack, ItemDisplayContext mode, PoseStack matrices, MultiBufferSource vertexConsumers, int light, int overlay) {
        if (this.model == null) {
            this.model = GCModelLoader.INSTANCE.getModel(MODEL);
        }
        VertexConsumer consumer = vertexConsumers.getBuffer(GCRenderTypes.obj(GCRenderTypes.OBJ_ATLAS));
        matrices.pushPose();

        switch (mode) {
            case GUI -> {
                matrices.translate(0.5F, 0.5F, 0.5F);
                matrices.scale(0.3F, 0.3F, 0.3F);
                matrices.mulPose(Axis.XP.rotationDegrees(30.0F));
                matrices.mulPose(Axis.YP.rotationDegrees(225.0F));
            }
            case FIXED -> {
                matrices.translate(0.5F, 0.5F, 0.5F);
                matrices.scale(0.28F, 0.28F, 0.28F);
                matrices.mulPose(Axis.YP.rotationDegrees(180.0F));
            }
            case GROUND -> {
                matrices.translate(0.5F, 0.25F, 0.5F);
                matrices.scale(0.2F, 0.2F, 0.2F);
            }
            default -> {
                matrices.translate(0.5F, 0.5F, 0.5F);
                matrices.scale(0.22F, 0.22F, 0.22F);
                matrices.mulPose(Axis.YP.rotationDegrees(135.0F));
            }
        }

        // Shrink the oversized OBJ down and centre its geometry on the origin.
        matrices.scale(0.0495F, 0.0495F, 0.0495F);
        matrices.translate(-CENTER_X, -CENTER_Y, -CENTER_Z);
        this.model.render(matrices, null, consumer, light, OverlayTexture.NO_OVERLAY);

        matrices.popPose();
    }
}
