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

package dev.galacticraft.mod.client.render.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.client.model.GCModelLoader;
import dev.galacticraft.mod.client.model.GCModelState;
import dev.galacticraft.mod.client.model.GCRenderTypes;
import dev.galacticraft.mod.client.render.entity.model.ParachestModel;
import dev.galacticraft.mod.content.entity.vehicle.AutomaticLanderEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

public final class AutomaticLanderRenderer extends EntityRenderer<AutomaticLanderEntity> {
    private static final ResourceLocation BALLOONS = Constant.id("models/misc/landing_balloon.json");
    private static final ResourceLocation POD = Constant.id("models/misc/pod.json");
    private static final ResourceLocation VENUS = Constant.id("models/misc/pod_flame.json");
    private static final ResourceLocation CHUTE = Constant.id("textures/model/parachute/white.png");
    private static final GCModelState BODY = new GCModelState("PodBody"), FLAME = new GCModelState("Flame_Sphere");
    private final ParachestModel canopy = new ParachestModel(ParachestModel.createParachuteLayer().bakeRoot());

    public AutomaticLanderRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 1.2F;
    }

    @Override
    public void render(AutomaticLanderEntity entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        boolean balloons = entity.kind() == AutomaticLanderEntity.Kind.BALLOONS;
        var model = GCModelLoader.INSTANCE.getModel(balloons ? BALLOONS : entity.kind() == AutomaticLanderEntity.Kind.VENUS_POD ? VENUS : POD);
        var consumer = buffers.getBuffer(GCRenderTypes.obj(GCRenderTypes.OBJ_ATLAS));
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(180 - yaw));
        if (balloons) {
            pose.translate(0, 1.14, 0);
            pose.scale(.5F, .5F, .5F);
        } else {
            pose.scale(.65F, .6F, .65F);
        }
        model.render(pose, balloons ? null : BODY, consumer, light, OverlayTexture.NO_OVERLAY);
        if (entity.hasEntryFlames()) {
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees((entity.tickCount + partialTick) * 20));
            model.render(pose, FLAME, buffers.getBuffer(GCRenderTypes.objTranslucent(GCRenderTypes.OBJ_ATLAS)),
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0xBFFFFFFF);
            pose.popPose();
        }
        pose.popPose();
        if (entity.hasParachute()) {
            pose.pushPose();
            pose.translate(0, balloons ? 1.3 : 2, 0);
            pose.scale(1.6F, 1.6F, 1.6F);
            pose.translate(-9F / 16, 0, -2F / 16);
            canopy.setupParachutePose();
            canopy.renderToBuffer(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(CHUTE)), light, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }

    @Override public ResourceLocation getTextureLocation(AutomaticLanderEntity entity) { return GCRenderTypes.OBJ_ATLAS; }
}