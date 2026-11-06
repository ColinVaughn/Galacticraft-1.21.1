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
import dev.galacticraft.mod.client.render.block.entity.LCD.LCDrecords.*;
import dev.galacticraft.mod.client.render.block.entity.LCD.LCDrenderer;
import dev.galacticraft.mod.client.render.block.entity.LCD.LCD;
import dev.galacticraft.mod.client.render.block.entity.blockLCDdata.ResourceStorageBlockLCDData;
import dev.galacticraft.mod.content.block.entity.machine.ResourceStorageBlockEntity;
import dev.galacticraft.mod.content.block.machine.ResourceStorageBlock;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import static dev.galacticraft.mod.client.render.block.entity.LCD.LCD.SIZE16;


public class ResourceStorageBlockRenderer implements BlockEntityRenderer<ResourceStorageBlockEntity> {
    private final LCDrenderer DISPLAY = new LCDrenderer();
    private final ResourceStorageBlockLCDData LCDData = new ResourceStorageBlockLCDData(
        LCD.reserve(
            2, 1
        )
    );

    public ResourceStorageBlockRenderer(BlockEntityRendererProvider.Context context){
    }

    @Override
    public void render(ResourceStorageBlockEntity machine, float tickDelta, PoseStack matrices, MultiBufferSource vertexConsumers, int light, int overlay) {
        final int amount = machine.getBlockState().getValue(ResourceStorageBlock.AMOUNT);
        if (amount == 0)
            return;

        Direction direction = machine.getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING);

        LCDDynamicData dynamicData = LCDData.getDynamicData(0);
        dynamicData.dynamicVertices().set(0, new Vertex(SIZE16*amount, 0.f, 0.f));
        dynamicData.dynamicVertices().set(1, new Vertex(SIZE16*amount, -SIZE16, 0.f));
        dynamicData.dynamicColors().set(0, machine.getColor());

        DISPLAY.render(LCDData, 0, direction, matrices, vertexConsumers, LevelRenderer.getLightColor(machine.getLevel(), machine.getBlockPos().relative(direction)), overlay);
    }
}
