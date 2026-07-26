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

package dev.galacticraft.mod.client.render.block.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.content.block.entity.TreasureChestBlockEntity;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.Material;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Renders a treasure chest with the vanilla chest geometry and a per-tier texture. Treasure
 * chests are always single chests, so none of the vanilla double-chest handling is needed.
 */
public class TreasureChestBlockEntityRenderer implements BlockEntityRenderer<TreasureChestBlockEntity> {
    private static final Material[] MATERIALS = {
            material("treasure_tier_1"),
            material("treasure_tier_2"),
            material("treasure_tier_3")
    };

    private final ModelPart lid;
    private final ModelPart bottom;
    private final ModelPart lock;

    public TreasureChestBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        ModelPart root = context.bakeLayer(ModelLayers.CHEST);
        this.bottom = root.getChild("bottom");
        this.lid = root.getChild("lid");
        this.lock = root.getChild("lock");
    }

    private static Material material(String name) {
        return new Material(Sheets.CHEST_SHEET, Constant.id("entity/chest/" + name));
    }

    @Override
    public void render(TreasureChestBlockEntity chest, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay) {
        BlockState state = chest.getBlockState();
        Direction facing = state.hasProperty(ChestBlock.FACING) ? state.getValue(ChestBlock.FACING) : Direction.SOUTH;

        poseStack.pushPose();
        poseStack.translate(0.5F, 0.5F, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        poseStack.translate(-0.5F, -0.5F, -0.5F);

        // Matches vanilla ChestRenderer's easing of the lid angle.
        float openness = 1.0F - chest.getOpenNess(partialTick);
        openness = 1.0F - openness * openness * openness;

        int tier = Mth.clamp(chest.getTier(), 1, MATERIALS.length);
        VertexConsumer consumer = MATERIALS[tier - 1].buffer(buffer, RenderType::entityCutout);

        this.lid.xRot = -(openness * ((float) Math.PI / 2F));
        this.lock.xRot = this.lid.xRot;
        this.lid.render(poseStack, consumer, packedLight, packedOverlay);
        this.lock.render(poseStack, consumer, packedLight, packedOverlay);
        this.bottom.render(poseStack, consumer, packedLight, packedOverlay);

        poseStack.popPose();
    }
}
