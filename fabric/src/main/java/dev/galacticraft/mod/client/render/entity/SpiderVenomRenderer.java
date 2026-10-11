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
import com.mojang.math.Axis;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.client.model.entity.SpiderVenomModel;
import dev.galacticraft.mod.client.render.entity.model.GCEntityModelLayer;
import dev.galacticraft.mod.content.entity.boss.SpiderVenomEntity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

@Environment(EnvType.CLIENT)
public class SpiderVenomRenderer extends EntityRenderer<SpiderVenomEntity> {
    private static final ResourceLocation TEXTURE = Constant.id("textures/entity/boss/spider_venom.png");
    private static final ResourceLocation EMISSIVE_TEXTURE = Constant.id("textures/entity/boss/spider_venom_emissive.png");
    private final SpiderVenomModel model;

    public SpiderVenomRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new SpiderVenomModel(context.bakeLayer(GCEntityModelLayer.SPIDER_VENOM));
        this.shadowRadius = 0.0F;
    }

    @Override
    public void render(SpiderVenomEntity entity, float yaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int light) {
        poseStack.pushPose();
        if (entity.isPooled()) {
            float age = entity.getPoolAge(partialTick);
            float growth = Mth.clamp(age / 8.0F, 0.15F, 1.0F);
            float pulse = 1.0F + Mth.sin(age * 0.24F) * 0.025F;
            poseStack.scale(2.2F * growth * pulse, 1.0F, 2.2F * growth * pulse);
        } else {
            float age = entity.tickCount + partialTick;
            poseStack.translate(0.0D, Mth.sin(age * 0.5F) * 0.05D, 0.0D);
            poseStack.mulPose(Axis.YP.rotationDegrees(age * 25.0F));
        }
        this.model.render(entity.isPooled(), poseStack,
                buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)), light);
        this.model.render(entity.isPooled(), poseStack,
                buffers.getBuffer(RenderType.eyes(EMISSIVE_TEXTURE)), 0xF000F0);
        poseStack.popPose();
        super.render(entity, yaw, partialTick, poseStack, buffers, light);
    }

    @Override
    public boolean shouldRender(SpiderVenomEntity entity, Frustum frustum, double cameraX, double cameraY, double cameraZ) {
        return frustum.isVisible(entity.getBoundingBox().inflate(entity.isPooled() ? 3.0D : 1.0D));
    }

    @Override
    public ResourceLocation getTextureLocation(SpiderVenomEntity entity) {
        return TEXTURE;
    }
}
