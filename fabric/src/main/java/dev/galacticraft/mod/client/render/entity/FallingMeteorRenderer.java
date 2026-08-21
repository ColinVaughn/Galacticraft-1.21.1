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
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.content.entity.FallingMeteorEntity;
import dev.galacticraft.mod.world.dimension.meteor.MeteoroidClass;
import dev.galacticraft.mod.world.dimension.meteor.MeteoroidPalette;
import dev.galacticraft.mod.world.dimension.meteor.MeteoroidShape;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Draws a meteoroid as the block body it actually is.
 *
 * The voxel list is rebuilt from the entity's synced seed, and only the first
 * {@code survivingVoxelCount()} entries are drawn. Because {@link MeteoroidShape} orders voxels
 * from the centre outwards, ablation reads as the body eroding from the outside in without a
 * single block ever crossing the network.
 *
 * An additive bloom scaled by the entity's ablation rate rides on top, so a body glows in
 * proportion to how hard the atmosphere is actually working on it: fiercely over Venus, briefly
 * over Earth, and not at all over an airless body.
 */
@Environment(EnvType.CLIENT)
public class FallingMeteorRenderer extends EntityRenderer<FallingMeteorEntity> {
    private static final ResourceLocation TEXTURE = Constant.id("textures/block/fallen_meteor.png");
    /** Relative sizes and weights of the nested bloom quads, innermost last. */
    private static final float[] GLOW_LAYER_SCALE = {1.0f, 0.66f, 0.36f};
    private static final float[] GLOW_LAYER_ALPHA = {0.22f, 0.45f, 0.95f};

    private final BlockRenderDispatcher dispatcher;

    public FallingMeteorRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.dispatcher = ctx.getBlockRenderDispatcher();
    }

    @Override
    public void render(FallingMeteorEntity entity, float yaw, float partialTick, PoseStack matrices,
                       MultiBufferSource vertexConsumers, int light) {
        List<MeteoroidShape.Voxel> voxels = entity.voxels();
        int surviving = Math.min(entity.survivingVoxelCount(), voxels.size());
        if (surviving <= 0) return;

        this.shadowRadius = (float) MeteoroidShape.radiusFor(entity.getSize());
        MeteoroidClass type = entity.getMeteoroidClass();

        matrices.pushPose();
        matrices.mulPose(Axis.YP.rotationDegrees(entity.getYRot()));
        matrices.mulPose(Axis.XP.rotationDegrees(entity.getXRot()));

        for (int i = 0; i < surviving; i++) {
            MeteoroidShape.Voxel voxel = voxels.get(i);
            BlockState state = MeteoroidPalette.bodyState(type, voxel.roll());

            matrices.pushPose();
            matrices.translate(voxel.x() - 0.5, voxel.y() - 0.5, voxel.z() - 0.5);
            this.dispatcher.renderBatched(
                    state,
                    entity.blockPosition(),
                    entity.level(),
                    matrices,
                    vertexConsumers.getBuffer(ItemBlockRenderTypes.getMovingBlockRenderType(state)),
                    false,
                    entity.getRandom());
            matrices.popPose();
        }

        matrices.popPose();

        renderGlow(entity, matrices, vertexConsumers);
    }

    /**
     * Stacks a few additive quads facing the camera. Overlapping them fakes a radial falloff far
     * more cheaply than a textured billboard, and they are skipped entirely when the body is not
     * ablating - which is exactly what happens in a vacuum.
     */
    private void renderGlow(FallingMeteorEntity entity, PoseStack matrices, MultiBufferSource vertexConsumers) {
        float glow = entity.getGlow();
        if (glow <= 0.02f) return;

        float radius = (float) MeteoroidShape.radiusFor(entity.getSize());
        VertexConsumer consumer = vertexConsumers.getBuffer(RenderType.lightning());

        matrices.pushPose();
        matrices.mulPose(this.entityRenderDispatcher.cameraOrientation());
        Matrix4f pose = matrices.last().pose();

        for (int layer = 0; layer < GLOW_LAYER_SCALE.length; layer++) {
            float size = radius * (1.7f + glow * 0.9f) * GLOW_LAYER_SCALE[layer];
            int alpha = (int) (Math.min(1.0f, glow * GLOW_LAYER_ALPHA[layer]) * 255.0f);
            if (alpha <= 0) continue;

            // Cooler orange in the outer haze, white hot at the core.
            float core = GLOW_LAYER_SCALE[GLOW_LAYER_SCALE.length - 1 - layer];
            int green = 140 + (int) (100.0f * core);
            int blue = 60 + (int) (170.0f * core);

            consumer.addVertex(pose, -size, -size, 0.0f).setColor(255, green, blue, alpha);
            consumer.addVertex(pose, size, -size, 0.0f).setColor(255, green, blue, alpha);
            consumer.addVertex(pose, size, size, 0.0f).setColor(255, green, blue, alpha);
            consumer.addVertex(pose, -size, size, 0.0f).setColor(255, green, blue, alpha);
        }

        matrices.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(FallingMeteorEntity entity) {
        return TEXTURE;
    }
}
