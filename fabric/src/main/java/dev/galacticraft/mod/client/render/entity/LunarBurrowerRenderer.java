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
import dev.galacticraft.mod.client.model.entity.LunarBurrowerModel;
import dev.galacticraft.mod.content.entity.LunarBurrowerEntity;
import dev.galacticraft.mod.content.entity.BurrowerState;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

public final class LunarBurrowerRenderer extends EntityRenderer<LunarBurrowerEntity> {
    private static final ResourceLocation SKIN = Constant.id("textures/entity/lunar_burrower.png");
    private static final ResourceLocation GLOW = Constant.id("textures/entity/lunar_burrower_glow.png");
    private static final ResourceLocation EQUIPMENT = Constant.id("textures/entity/lunar_burrower_equipment.png");
    private final LunarBurrowerModel model, childModel, grownModel, equipmentModel;
    private final PlayerModel<AbstractClientPlayer> playerWide, playerSlim;
    public LunarBurrowerRenderer(EntityRendererProvider.Context context) {
        super(context);
        model = new LunarBurrowerModel(Minecraft.getInstance().getResourceManager());
        childModel = new LunarBurrowerModel(Minecraft.getInstance().getResourceManager(), "lunar_burrower_child");
        grownModel = new LunarBurrowerModel(Minecraft.getInstance().getResourceManager(), "lunar_burrower_grown");
        equipmentModel = new LunarBurrowerModel(Minecraft.getInstance().getResourceManager(), "lunar_burrower_equipment");
        playerWide = new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false);
        playerSlim = new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
        shadowRadius = 0.9F;
    }
    @Override public ResourceLocation getTextureLocation(LunarBurrowerEntity entity) { return SKIN; }
    @Override public void render(LunarBurrowerEntity entity, float yaw, float partialTick, PoseStack pose,
                                 MultiBufferSource buffers, int light) {
        pose.pushPose();
        float scale = entity.getModelScale();
        pose.scale(scale, scale, scale);
        pose.mulPose(Axis.YP.rotationDegrees(180 - entity.getHeadYaw(partialTick)));
        if (entity.deathTime > 0) pose.mulPose(Axis.ZP.rotationDegrees(Math.min(35, entity.deathTime * 2)));
        int overlay = OverlayTexture.pack(0, entity.hurtTime > 0 || entity.deathTime > 0);
        LunarBurrowerModel body = entity.isBaby() ? childModel : entity.isHatched() ? grownModel : model;
        if (!entity.isInvisible()) {
            body.render(entity, partialTick, pose, buffers.getBuffer(RenderType.entityCutoutNoCull(SKIN)), light, overlay, -1);
            body.render(entity, partialTick, pose, buffers.getBuffer(RenderType.eyes(GLOW)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, -1);
            if (entity.isSaddled() || entity.hasChest()) equipmentModel.render(entity, partialTick, pose,
                    buffers.getBuffer(RenderType.entityCutoutNoCull(EQUIPMENT)), light, overlay, -1);
        }
        renderCapturedPlayer(entity, partialTick, pose, buffers, light);
        pose.popPose();
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }
    private void renderCapturedPlayer(LunarBurrowerEntity worm, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        if (worm.getBurrowerState() != BurrowerState.EAT || worm.getStateTicks() < LunarBurrowerEntity.GRAB_TICK
                || worm.getStateTicks() >= LunarBurrowerEntity.PROXY_END_TICK) return;
        if (!(worm.level().getEntity(worm.getCapturedId()) instanceof AbstractClientPlayer player)) return;
        float progress = net.minecraft.util.Mth.clamp((worm.getStateTicks() + partialTick - LunarBurrowerEntity.GRAB_TICK)
                / (LunarBurrowerEntity.PROXY_END_TICK - LunarBurrowerEntity.GRAB_TICK), 0, 1);
        PlayerModel<AbstractClientPlayer> proxy = player.getSkin().model() == net.minecraft.client.resources.PlayerSkin.Model.SLIM ? playerSlim : playerWide;
        proxy.riding = false; proxy.young = false; proxy.crouching = false; proxy.attackTime = 0;
        proxy.leftArmPose = proxy.rightArmPose = HumanoidModel.ArmPose.EMPTY;
        proxy.setAllVisible(true);
        proxy.setupAnim(player, 0, 0, worm.tickCount + partialTick, 0, 0);
        pose.pushPose();
        var head = LunarBurrowerModel.headOffset(worm, partialTick);
        pose.translate(head.x, head.y, head.z);
        pose.translate(0, 0.62, 0.65 + progress * 2.7);
        pose.mulPose(Axis.XP.rotationDegrees(-90));
        pose.scale(-1, -1, 1);
        pose.translate(0, -1.5, 0);
        // Armor and held items are omitted from the swallowed-player proxy.
        proxy.renderToBuffer(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(player.getSkin().texture())), light, OverlayTexture.NO_OVERLAY, -1);
        pose.popPose();
    }
}
