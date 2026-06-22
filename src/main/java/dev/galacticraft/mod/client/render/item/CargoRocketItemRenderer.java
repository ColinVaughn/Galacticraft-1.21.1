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
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Renders the {@code cargo_rocket} item using its OBJ entity model, since the vehicle has no flat
 * inventory icon. The item model json ({@code models/item/cargo_rocket.json}) delegates here via
 * {@code minecraft:builtin/entity}.
 */
public class CargoRocketItemRenderer implements BuiltinItemRendererRegistry.DynamicItemRenderer {
    public static final ResourceLocation MODEL = Constant.id("models/misc/cargo_rocket.json");

    // Geometry center of cargo_rocket.obj (the model already sits at ~1 unit = 1 block).
    private static final float CENTER_X = 0.0F;
    private static final float CENTER_Y = 3.42F;
    private static final float CENTER_Z = 0.0F;

    private GCModel model;

    @Override
    public void render(ItemStack stack, ItemDisplayContext mode, PoseStack matrices, MultiBufferSource vertexConsumers, int light, int overlay) {
        if (this.model == null) {
            this.model = GCModelLoader.INSTANCE.getModel(MODEL);
        }
        VertexConsumer consumer = vertexConsumers.getBuffer(GCRenderTypes.obj(GCRenderTypes.OBJ_ATLAS));
        matrices.pushPose();

        switch (mode) {
            case GUI -> {
                matrices.translate(0.5F, 0.5F, 0.5F);
                matrices.scale(0.13F, 0.13F, 0.13F);
                matrices.mulPose(Axis.XP.rotationDegrees(15.0F));
                matrices.mulPose(Axis.YP.rotationDegrees(180.0F));
            }
            case FIXED -> {
                matrices.translate(0.5F, 0.5F, 0.5F);
                matrices.scale(0.12F, 0.12F, 0.12F);
                matrices.mulPose(Axis.YP.rotationDegrees(180.0F));
            }
            case GROUND -> {
                matrices.translate(0.5F, 0.25F, 0.5F);
                matrices.scale(0.09F, 0.09F, 0.09F);
            }
            default -> {
                matrices.translate(0.5F, 0.5F, 0.5F);
                matrices.scale(0.1F, 0.1F, 0.1F);
            }
        }

        // Centre the model vertically on the origin so framing rotates about its middle.
        matrices.translate(-CENTER_X, -CENTER_Y, -CENTER_Z);
        this.model.render(matrices, null, consumer, light, OverlayTexture.NO_OVERLAY);

        matrices.popPose();
    }
}
