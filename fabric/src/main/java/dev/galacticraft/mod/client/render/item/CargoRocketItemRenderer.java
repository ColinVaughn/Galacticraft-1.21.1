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
import com.mojang.math.Axis;
import dev.galacticraft.mod.client.render.entity.CargoRocketRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Renders the {@code cargo_rocket} item using its OBJ entity model, since the vehicle has no flat
 * inventory icon. The item model json ({@code models/item/cargo_rocket.json}) delegates here via
 * {@code minecraft:builtin/entity}.
 */
public class CargoRocketItemRenderer {
    public void render(ItemStack stack, ItemDisplayContext mode, PoseStack matrices, MultiBufferSource vertexConsumers, int light, int overlay) {
        matrices.pushPose();

        switch (mode) {
            case GUI -> {
                matrices.translate(0.5F, 0.5F, 0.5F);
                matrices.scale(0.2F, 0.2F, 0.2F);
                matrices.mulPose(Axis.XP.rotationDegrees(15.0F));
                matrices.mulPose(Axis.YP.rotationDegrees(155.0F));
            }
            case FIXED -> {
                matrices.translate(0.5F, 0.5F, 0.5F);
                matrices.scale(0.2F, 0.2F, 0.2F);
                matrices.mulPose(Axis.YP.rotationDegrees(180.0F));
            }
            case GROUND -> {
                matrices.translate(0.5F, 0.35F, 0.5F);
                matrices.scale(0.15F, 0.15F, 0.15F);
            }
            default -> {
                matrices.translate(0.5F, 0.5F, 0.5F);
                matrices.scale(0.18F, 0.18F, 0.18F);
            }
        }

        matrices.translate(0, -2.25F, 0);
        CargoRocketRenderer.draw(matrices, vertexConsumers, light, overlay, 0);

        matrices.popPose();
    }
}
