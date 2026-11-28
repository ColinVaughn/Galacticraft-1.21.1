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
import dev.galacticraft.mod.client.render.block.entity.lcd.LcdImage;
import dev.galacticraft.mod.client.render.block.entity.lcd.LcdRenderer;
import dev.galacticraft.mod.content.block.entity.machine.ResourceStorageBlockEntity;
import dev.galacticraft.mod.content.block.machine.ResourceStorageBlock;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.Collections;
import java.util.stream.IntStream;

/** The fill gauge on a storage block's front: one 1/16-block pixel per stored step, along the top edge. */
public class ResourceStorageBlockRenderer implements BlockEntityRenderer<ResourceStorageBlockEntity> {
    private static final LcdImage[] GAUGES = IntStream.rangeClosed(0, Collections.max(ResourceStorageBlock.AMOUNT.getPossibleValues()))
            .mapToObj(amount -> LcdImage.rect(16, 2, 1, amount, 1))
            .toArray(LcdImage[]::new);

    public ResourceStorageBlockRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(ResourceStorageBlockEntity machine, float tickDelta, PoseStack matrices, MultiBufferSource vertexConsumers, int light, int overlay) {
        int amount = machine.getBlockState().getValue(ResourceStorageBlock.AMOUNT);
        if (amount == 0) return;
        Direction facing = machine.getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING);
        int frontLight = LevelRenderer.getLightColor(machine.getLevel(), machine.getBlockPos().relative(facing));
        LcdRenderer.draw(LcdRenderer.Face.FRONT, GAUGES[amount], machine.getColor(), facing, matrices, vertexConsumers, frontLight, overlay);
    }
}
