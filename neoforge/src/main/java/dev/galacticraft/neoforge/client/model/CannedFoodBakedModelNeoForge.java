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

package dev.galacticraft.neoforge.client.model;

import dev.galacticraft.api.component.GCDataComponents;
import dev.galacticraft.mod.client.model.CannedFoodBakedModel;
import dev.galacticraft.mod.content.block.decoration.CannedFoodBlock;
import dev.galacticraft.mod.content.block.entity.decoration.CannedFoodBlockEntity;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Loader-native translation/color wrapper for stacked canned-food blocks. */
public final class CannedFoodBakedModelNeoForge implements BakedModel {
    private static final ModelProperty<List<ItemStack>> CONTENTS = new ModelProperty<>();
    private final BakedModel wrapped;

    public CannedFoodBakedModelNeoForge(BakedModel wrapped) { this.wrapped = wrapped; }

    @Override
    public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData data) {
        if (level.getBlockEntity(pos) instanceof CannedFoodBlockEntity cans) {
            return ModelData.of(CONTENTS, List.copyOf(cans.getCanContents()));
        }
        return ModelData.EMPTY;
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random,
                                    ModelData data, @Nullable RenderType renderType) {
        if (state == null || !data.has(CONTENTS)) return wrapped.getQuads(state, side, random, data, renderType);
        List<ItemStack> contents = data.get(CONTENTS);
        int count = Math.min(8, contents.size());
        List<BakedQuad> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            float[] position = CannedFoodBakedModel.POSITIONS[count][i];
            float x0 = (position[0] - 8) / 16.0F;
            float y = position[1] / 16.0F;
            float z0 = (position[2] - 8) / 16.0F;
            Direction facing = state.getValue(CannedFoodBlock.FACING);
            float x = facing.getStepX() * z0 + facing.getStepZ() * x0;
            float z = facing.getStepZ() * z0 - facing.getStepX() * x0;
            int color = contents.get(i).getOrDefault(GCDataComponents.COLOR, 0xFFFFFF) | 0xFF000000;
            for (BakedQuad quad : wrapped.getQuads(state, side, random, data, renderType)) result.add(move(quad, x, y, z, color));
        }
        return result;
    }

    private static BakedQuad move(BakedQuad quad, float x, float y, float z, int color) {
        int[] vertices = quad.getVertices().clone();
        int stride = vertices.length / 4;
        for (int vertex = 0; vertex < 4; vertex++) {
            int offset = vertex * stride;
            vertices[offset] = Float.floatToRawIntBits(Float.intBitsToFloat(vertices[offset]) + x);
            vertices[offset + 1] = Float.floatToRawIntBits(Float.intBitsToFloat(vertices[offset + 1]) + y);
            vertices[offset + 2] = Float.floatToRawIntBits(Float.intBitsToFloat(vertices[offset + 2]) + z);
            if (quad.isTinted()) vertices[offset + 3] = color;
        }
        return new BakedQuad(vertices, quad.getTintIndex(), quad.getDirection(), quad.getSprite(), quad.isShade(), quad.hasAmbientOcclusion());
    }

    @Override public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random) { return wrapped.getQuads(state, side, random); }
    @Override public boolean useAmbientOcclusion() { return wrapped.useAmbientOcclusion(); }
    @Override public boolean isGui3d() { return wrapped.isGui3d(); }
    @Override public boolean usesBlockLight() { return wrapped.usesBlockLight(); }
    @Override public boolean isCustomRenderer() { return wrapped.isCustomRenderer(); }
    @Override public TextureAtlasSprite getParticleIcon() { return wrapped.getParticleIcon(); }
    @Override public ItemTransforms getTransforms() { return wrapped.getTransforms(); }
    @Override public ItemOverrides getOverrides() { return wrapped.getOverrides(); }
}
