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
import dev.galacticraft.mod.client.model.GCModelState;
import dev.galacticraft.mod.client.model.GCRenderTypes;
import dev.galacticraft.mod.content.entity.vehicle.CargoRocketEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

public class CargoRocketRenderer extends EntityRenderer<CargoRocketEntity> {
    public static final ResourceLocation MODEL = Constant.id("models/misc/cargo_rocket.json");
    private static final GCModelState CHASSIS = new GCModelState("Chassis");
    private static final GCModelState MIDDLE = new GCModelState("FairingMiddle");
    private static final GCModelState UPPER = new GCModelState("FairingUpper");
    private static final GCModelState CAP = new GCModelState("FairingCap");

    public CargoRocketRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(CargoRocketEntity entity, float yaw, float tickDelta, PoseStack matrices, MultiBufferSource vertexConsumers, int light) {
        matrices.pushPose();
        matrices.mulPose(Axis.YP.rotationDegrees(180.0F - yaw));
        draw(matrices, vertexConsumers, light, OverlayTexture.NO_OVERLAY, entity.getFairingLoad(tickDelta));
        matrices.popPose();
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
    }

    public static void draw(PoseStack matrices, MultiBufferSource vertices, int light, int overlay, float load) {
        GCModel model = GCModelLoader.INSTANCE.getModel(MODEL);
        VertexConsumer consumer = vertices.getBuffer(GCRenderTypes.obj(GCRenderTypes.OBJ_ATLAS));
        model.render(matrices, CHASSIS, consumer, light, overlay);
        matrices.pushPose();
        matrices.translate(0, CargoRocketEntity.FAIRING_EXTENSION * load / 2, 0);
        model.render(matrices, MIDDLE, consumer, light, overlay);
        matrices.translate(0, CargoRocketEntity.FAIRING_EXTENSION * load / 2, 0);
        model.render(matrices, UPPER, consumer, light, overlay);
        model.render(matrices, CAP, consumer, light, overlay);
        matrices.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(CargoRocketEntity entity) {
        return null;
    }
}
