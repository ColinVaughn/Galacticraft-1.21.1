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
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.content.block.entity.machine.ResourceStorageBlockEntity;
import dev.galacticraft.mod.content.block.machine.ResourceStorageBlock;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.joml.Matrix4f;

public class ResourceStorageBlockRenderer implements BlockEntityRenderer<ResourceStorageBlockEntity> {
    private static final ResourceLocation WHITE = Constant.id("textures/obj/white.png");
    private static final float SIZE = 0.0625f;

    public ResourceStorageBlockRenderer(BlockEntityRendererProvider.Context context){
    }

    @Override
    public void render(ResourceStorageBlockEntity machine, float tickDelta, PoseStack matrices, MultiBufferSource vertexConsumers, int light, int overlay) {
        final int amount = machine.getBlockState().getValue(ResourceStorageBlock.AMOUNT);
        if (amount == 0)
            return;

        final int color = machine.getColor();
        final int[] rgb = {(color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF};

        final Direction direction = machine.getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING);
        light = LevelRenderer.getLightColor(machine.getLevel(), machine.getBlockPos().relative(direction));
        light = LightTexture.pack(Math.max(10, LightTexture.block(light)), Math.max(10, LightTexture.sky(light)));

        matrices.pushPose();
        matrices.translate(0.5, 0.5, 0.5);
        matrices.mulPose(direction.getRotation());
        matrices.translate(-0.5, -0.5, -0.5);
        matrices.translate(0.125, 1.001, 0.0625);
        final Matrix4f matrix = matrices.last().pose();
        final VertexConsumer consumer = vertexConsumers.getBuffer(RenderType.entityTranslucent(WHITE));

        final float[][] vertices = {
                {0, 0, 0},
                {SIZE * amount, 0, 0},
                {SIZE * amount, 0, SIZE},
                {0, 0, SIZE}
        };

        for (float[] e : vertices) {
            consumer.addVertex(matrix, e[0], e[1], e[2])
                    .setColor(rgb[0], rgb[1], rgb[2], 255)
                    .setLight(light)
                    .setOverlay(overlay)
                    .setUv(0.5f, 0.5f)
                    .setNormal(0.0f, 1.0f, 0.0f);

        }

        matrices.popPose();
    }
}
