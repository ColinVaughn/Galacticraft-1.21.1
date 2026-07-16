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

package dev.galacticraft.mod.client.render.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.client.model.GCModel;
import dev.galacticraft.mod.client.model.GCModelLoader;
import dev.galacticraft.mod.client.model.GCRenderTypes;
import dev.galacticraft.mod.content.entity.vehicle.CargoRocketEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

public class CargoRocketRenderer extends EntityRenderer<CargoRocketEntity> {
    public static final ResourceLocation MODEL = Constant.id("models/misc/cargo_rocket.json");
    private static final float MODEL_SCALE = 0.4F;
    private static final float MODEL_Y_OFFSET = -0.1F;

    private GCModel cargoRocketModel;

    public CargoRocketRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(CargoRocketEntity entity, float yaw, float tickDelta, PoseStack matrices, MultiBufferSource vertexConsumers, int light) {
        if (this.cargoRocketModel == null)
            this.cargoRocketModel = GCModelLoader.INSTANCE.getModel(MODEL);
        VertexConsumer consumer = vertexConsumers.getBuffer(GCRenderTypes.obj(GCRenderTypes.OBJ_ATLAS));
        matrices.pushPose();
        matrices.translate(0.0F, MODEL_Y_OFFSET, 0.0F);
        matrices.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
        matrices.mulPose(Axis.YP.rotationDegrees(180.0F - yaw));
        // null state = render the whole model at once.
        this.cargoRocketModel.render(matrices, null, consumer, light, OverlayTexture.NO_OVERLAY);
        matrices.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(CargoRocketEntity entity) {
        return null;
    }
}
