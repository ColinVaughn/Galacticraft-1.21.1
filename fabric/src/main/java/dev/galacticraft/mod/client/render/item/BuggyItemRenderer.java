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

package dev.galacticraft.mod.client.render.item;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.galacticraft.mod.client.model.GCModel;
import dev.galacticraft.mod.client.model.GCModelLoader;
import dev.galacticraft.mod.client.model.GCModelState;
import dev.galacticraft.mod.client.model.GCRenderTypes;
import dev.galacticraft.mod.client.render.entity.BuggyRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Renders the {@code buggy} item as the vehicle it places, the way the rocket, astro miner and cargo
 * rocket items already do. The item model json ({@code models/item/buggy.json}) delegates here through
 * {@code minecraft:builtin/entity}.
 *
 * <p>The buggy's OBJ is not one piece: the wheels and the radar dish are each modelled about the origin
 * and moved into place by {@link BuggyRenderer} as it animates them. Drawing the model whole would pile
 * all four wheels up inside the chassis, so this repeats that renderer's layout with the animation held
 * still.
 */
public class BuggyItemRenderer {
    /** Wheel mounting points, copied from {@link BuggyRenderer} so the item matches the placed vehicle. */
    private static final float FRONT_AXLE_X = 1.25F;
    private static final float REAR_AXLE_X = 1.9F;
    private static final float AXLE_Y = 0.976F;
    private static final float AXLE_Z = 2.727F;

    // Centre of the assembled buggy's bounding box - chassis, wheels at their mounts and dish included -
    // so the framing below turns about the middle of the vehicle rather than the middle of the chassis.
    private static final float CENTER_X = 0.0F;
    private static final float CENTER_Y = 2.25F;
    private static final float CENTER_Z = 0.03F;

    private GCModel model;

    public void render(ItemStack stack, ItemDisplayContext mode, PoseStack matrices, MultiBufferSource vertexConsumers, int light, int overlay) {
        if (this.model == null) {
            this.model = GCModelLoader.INSTANCE.getModel(BuggyRenderer.MODEL);
        }
        VertexConsumer consumer = vertexConsumers.getBuffer(GCRenderTypes.obj(GCRenderTypes.OBJ_ATLAS));
        matrices.pushPose();

        // The assembled buggy is just under 8 model units along its longest axis, so these scales bring
        // it to roughly the width of an inventory slot, matching the other vehicle items.
        switch (mode) {
            case GUI -> {
                matrices.translate(0.5F, 0.5F, 0.5F);
                matrices.scale(0.115F, 0.115F, 0.115F);
                matrices.mulPose(Axis.XP.rotationDegrees(30.0F));
                matrices.mulPose(Axis.YP.rotationDegrees(135.0F));
            }
            case FIXED -> {
                matrices.translate(0.5F, 0.5F, 0.5F);
                matrices.scale(0.105F, 0.105F, 0.105F);
                matrices.mulPose(Axis.YP.rotationDegrees(180.0F));
            }
            case GROUND -> {
                matrices.translate(0.5F, 0.25F, 0.5F);
                matrices.scale(0.075F, 0.075F, 0.075F);
            }
            default -> {
                matrices.translate(0.5F, 0.5F, 0.5F);
                matrices.scale(0.085F, 0.085F, 0.085F);
                matrices.mulPose(Axis.YP.rotationDegrees(135.0F));
            }
        }
        matrices.translate(-CENTER_X, -CENTER_Y, -CENTER_Z);

        wheel(matrices, consumer, light, FRONT_AXLE_X, -AXLE_Z, BuggyRenderer.WHEEL_RIGHT_COVER, BuggyRenderer.WHEEL_RIGHT);
        wheel(matrices, consumer, light, -FRONT_AXLE_X, -AXLE_Z, BuggyRenderer.WHEEL_LEFT_COVER, BuggyRenderer.WHEEL_LEFT);
        wheel(matrices, consumer, light, REAR_AXLE_X, AXLE_Z, null, BuggyRenderer.WHEEL_RIGHT);
        wheel(matrices, consumer, light, -REAR_AXLE_X, AXLE_Z, null, BuggyRenderer.WHEEL_LEFT);

        this.model.render(matrices, BuggyRenderer.MAIN_MODEL, consumer, light, OverlayTexture.NO_OVERLAY);

        matrices.pushPose();
        matrices.translate(-1.178F, 4.1F, -2.397F);
        this.model.render(matrices, BuggyRenderer.RADAR_DISH, consumer, light, OverlayTexture.NO_OVERLAY);
        matrices.popPose();

        matrices.popPose();
    }

    private void wheel(PoseStack matrices, VertexConsumer consumer, int light, float x, float z,
                       GCModelState cover, GCModelState wheel) {
        matrices.pushPose();
        matrices.translate(x, AXLE_Y, z);
        if (cover != null) {
            this.model.render(matrices, cover, consumer, light, OverlayTexture.NO_OVERLAY);
        }
        this.model.render(matrices, wheel, consumer, light, OverlayTexture.NO_OVERLAY);
        matrices.popPose();
    }
}
