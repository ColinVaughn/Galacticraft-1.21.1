/* Copyright (c) 2026 Team Galacticraft. GC4 model assets retain their original license. */
package dev.galacticraft.mod.client.render.block.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.client.model.GCModelLoader;
import dev.galacticraft.mod.client.model.GCModelState;
import dev.galacticraft.mod.client.model.GCRenderTypes;
import dev.galacticraft.mod.content.block.entity.machine.ShortRangeTelepadBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class ShortRangeTelepadRenderer implements BlockEntityRenderer<ShortRangeTelepadBlockEntity> {
    public static final ResourceLocation MODEL = Constant.id("models/misc/telepad_short.json");
    private static final GCModelState BOTTOM = new GCModelState("Bottom"), TOP = new GCModelState("Top"), CONNECTOR = new GCModelState("Connector");
    public ShortRangeTelepadRenderer(BlockEntityRendererProvider.Context context) {}
    @Override public void render(ShortRangeTelepadBlockEntity pad, float delta, PoseStack poses, MultiBufferSource buffers, int light, int overlay) {
        poses.pushPose();
        poses.translate(0.5, 0, 0.5);
        poses.mulPose(Axis.YP.rotationDegrees(180 - pad.getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING).toYRot()));
        draw(poses, buffers, light, overlay);
        poses.popPose();
    }
    public static void draw(PoseStack poses, MultiBufferSource buffers, int light, int overlay) {
        poses.pushPose();
        poses.scale(0.745F, 1, 0.745F);
        var model = GCModelLoader.INSTANCE.getModel(MODEL);
        var consumer = buffers.getBuffer(GCRenderTypes.obj(GCRenderTypes.OBJ_ATLAS));
        model.render(poses, BOTTOM, consumer, light, overlay);
        poses.translate(0, -0.7, 0);
        model.render(poses, TOP, consumer, light, overlay);
        model.render(poses, CONNECTOR, consumer, light, overlay);
        poses.popPose();
    }
    @Override public boolean shouldRenderOffScreen(ShortRangeTelepadBlockEntity pad) { return true; }
}
