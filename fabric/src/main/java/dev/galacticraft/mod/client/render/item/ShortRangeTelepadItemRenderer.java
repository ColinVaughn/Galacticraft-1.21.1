/* Copyright (c) 2026 Team Galacticraft. Licensed under the MIT license. */
package dev.galacticraft.mod.client.render.item;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.galacticraft.mod.client.render.block.entity.ShortRangeTelepadRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class ShortRangeTelepadItemRenderer {
    public void render(ItemStack stack, ItemDisplayContext context, PoseStack poses, MultiBufferSource buffers, int light, int overlay) {
        poses.pushPose();
        poses.translate(0.5, 0.5, 0.5);
        float scale = context == ItemDisplayContext.GUI ? 0.25F : 0.18F;
        poses.scale(scale, scale, scale);
        if (context == ItemDisplayContext.GUI) {
            poses.mulPose(Axis.XP.rotationDegrees(20));
            poses.mulPose(Axis.YP.rotationDegrees(36));
        }
        poses.translate(0, -1.5, 0);
        ShortRangeTelepadRenderer.draw(poses, buffers, light, overlay);
        poses.popPose();
    }
}
