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
import dev.galacticraft.mod.client.model.GCModel;
import dev.galacticraft.mod.client.model.GCModelLoader;
import dev.galacticraft.mod.client.model.GCRenderTypes;
import dev.galacticraft.mod.content.block.entity.machine.AstroMinerBaseBlockEntity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/**
 * Renders the hand-authored {@code minerbase} OBJ for the master corner of an Astro Miner
 * Base, oriented by the base's facing. Mirrors the OBJ pipeline used by
 * {@code AstroMinerRenderer}.
 */
@Environment(EnvType.CLIENT)
public class AstroMinerBaseBlockEntityRenderer implements BlockEntityRenderer<AstroMinerBaseBlockEntity> {
    public static final ResourceLocation MODEL = Constant.id("models/misc/minerbase.json");

    private GCModel model;

    public AstroMinerBaseBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(AstroMinerBaseBlockEntity blockEntity, float tickDelta, PoseStack matrices, MultiBufferSource vertexConsumers, int light, int overlay) {
        if (this.model == null) {
            this.model = GCModelLoader.INSTANCE.getModel(MODEL);
        }
        light = getAverageLight(blockEntity, light);
        VertexConsumer consumer = vertexConsumers.getBuffer(GCRenderTypes.obj(GCRenderTypes.OBJ_ATLAS));
        matrices.pushPose();
        // Transform mirrors legacy TileEntityMinerBaseRenderer: master is the min corner, so
        // shift to the 2x2x2 centre, scale the ~1/20-block-unit OBJ down, then rotate by facing.
        matrices.translate(1.0F, 1.0F, 1.0F);
        matrices.scale(0.05F, 0.05F, 0.05F);
        float angle = switch (blockEntity.getFacing()) {
            case SOUTH -> 180.0F;
            case WEST -> 90.0F;
            case EAST -> 270.0F;
            default -> 0.0F; // NORTH
        };
        matrices.mulPose(Axis.YP.rotationDegrees(angle));
        this.model.render(matrices, null, consumer, light, OverlayTexture.NO_OVERLAY);
        matrices.popPose();
    }

    /** Matches the legacy renderer by averaging the light across all eight base blocks. */
    private static int getAverageLight(AstroMinerBaseBlockEntity blockEntity, int fallback) {
        if (blockEntity.getLevel() == null) {
            return fallback;
        }

        int blockLight = 0;
        int skyLight = 0;
        BlockPos master = blockEntity.getBlockPos();
        BlockPos.MutableBlockPos sample = new BlockPos.MutableBlockPos();

        for (int x = 0; x <= 1; x++) {
            for (int y = 0; y <= 1; y++) {
                for (int z = 0; z <= 1; z++) {
                    sample.setWithOffset(master, x, y, z);
                    int sampledLight = LevelRenderer.getLightColor(blockEntity.getLevel(), sample);
                    blockLight += LightTexture.block(sampledLight);
                    skyLight += LightTexture.sky(sampledLight);
                }
            }
        }
        return LightTexture.pack(blockLight / 8, skyLight / 8);
    }
}
