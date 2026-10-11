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
import com.mojang.math.Axis;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.client.model.MeteorDefenseModel;
import dev.galacticraft.mod.content.block.entity.machine.RadarBlockEntity;
import dev.galacticraft.mod.content.block.machine.RadarBlock;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

@Environment(EnvType.CLIENT)
public class RadarBlockEntityRenderer implements BlockEntityRenderer<RadarBlockEntity> {
    private static final ResourceLocation TEXTURE = Constant.id("textures/model/radar.png");
    private final MeteorDefenseModel model;

    public RadarBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        model = new MeteorDefenseModel(Minecraft.getInstance().getResourceManager(), "radar");
    }

    @Override
    public void render(RadarBlockEntity radar, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
                       int light, int overlay) {
        model.reset();
        if (radar.getState().isActive())
            model.animate("animation.radar.scan", (radar.getLevel().getGameTime() + (double) partialTick) / 20);
        poseStack.pushPose();
        poseStack.translate(0.5, 0, 0.5);
        face(poseStack, radar.getBlockState().getValue(RadarBlock.FACING));
        model.render(poseStack, buffers.getBuffer(RenderType.entityCutout(TEXTURE)), light, overlay);
        poseStack.popPose();
    }

    static void face(PoseStack pose, Direction facing) {
        pose.mulPose(Axis.YP.rotationDegrees(180 - facing.toYRot()));
    }

    @Override public boolean shouldRenderOffScreen(RadarBlockEntity radar) { return true; }
    @Override public int getViewDistance() { return 160; }
}
