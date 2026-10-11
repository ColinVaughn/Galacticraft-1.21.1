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

package dev.galacticraft.mod.client.render.block.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.client.model.MeteorDefenseModel;
import dev.galacticraft.mod.content.block.entity.machine.CannonBlockEntity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

@Environment(EnvType.CLIENT)
public class CannonBlockEntityRenderer implements BlockEntityRenderer<CannonBlockEntity> {
    private static final ResourceLocation TEXTURE = Constant.id("textures/model/cannon.png");
    private final MeteorDefenseModel model;

    public CannonBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        model = new MeteorDefenseModel(Minecraft.getInstance().getResourceManager(), "cannon");
    }

    @Override
    public void render(CannonBlockEntity cannon, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
                       int light, int overlay) {
        Vec3 target = cannon.getTargetPosition();
        if (cannon.getLevel() instanceof ClientLevel clientLevel) {
            Entity entity = clientLevel.getEntity(cannon.getTargetEntityId());
            if (entity != null) target = entity.getPosition(partialTick);
        }
        model.reset();
        model.bone("pitch").zRot = 15 * Mth.DEG_TO_RAD;
        if (target != null) {
            double dx = target.x - (cannon.getBlockPos().getX() + 0.5);
            double dy = target.y - (cannon.getBlockPos().getY() + 2.0);
            double dz = target.z - (cannon.getBlockPos().getZ() + 0.5);
            model.bone("yaw").yRot = (float) Math.atan2(dz, dx);
            model.bone("pitch").zRot = Mth.clamp((float) Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)),
                    -10 * Mth.DEG_TO_RAD, 80 * Mth.DEG_TO_RAD);
            if (!cannon.isShotInFlight())
                model.animate("animation.cannon.charge", (cannon.getLevel().getGameTime() + (double) partialTick) / 20);
        }
        long firedAt = cannon.getLastShotTime();
        if (firedAt >= 0)
            model.animate("animation.cannon.recoil", (cannon.getLevel().getGameTime() - firedAt + partialTick) / 20);
        poseStack.pushPose();
        poseStack.translate(0.5, 0, 0.5);
        model.render(poseStack, buffers.getBuffer(RenderType.entityCutout(TEXTURE)), light, overlay);
        poseStack.popPose();
    }

    @Override public boolean shouldRenderOffScreen(CannonBlockEntity cannon) { return true; }
    @Override public int getViewDistance() { return 160; }
}
