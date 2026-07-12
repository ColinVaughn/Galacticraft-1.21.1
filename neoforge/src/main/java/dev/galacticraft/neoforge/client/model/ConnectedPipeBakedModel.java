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

import dev.galacticraft.mod.api.block.entity.Connected;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** NeoForge equivalent of Fabric's emitter-based connected pipe model. */
public final class ConnectedPipeBakedModel implements BakedModel {
    private static final ModelProperty<Integer> CONNECTIONS = new ModelProperty<>();
    private final TextureAtlasSprite sprite;
    private final Map<Direction, BakedQuad> centerFaces = new EnumMap<>(Direction.class);
    private final Map<Direction, List<BakedQuad>> arms = new EnumMap<>(Direction.class);
    private final float radius;

    public ConnectedPipeBakedModel(TextureAtlasSprite sprite, float radius) {
        this.sprite = sprite;
        this.radius = radius;
        float min = 0.5F - radius;
        float max = 0.5F + radius;
        float centerStart = min * 16.0F;
        float centerEnd = max * 16.0F;
        float width = centerEnd - centerStart;

        for (Direction direction : Direction.values()) {
            List<BakedQuad> cap = new ArrayList<>(1);
            NeoBakedQuadEmitter emitter = new NeoBakedQuadEmitter(cap, sprite);
            emitCap(emitter, direction, min, min, max, max, min);
            centerFaces.put(direction, cap.getFirst());
        }

        List<BakedQuad> down = new ArrayList<>(4);
        NeoBakedQuadEmitter emitter = new NeoBakedQuadEmitter(down, sprite);
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            emitSquare(emitter, direction, min, 0.0F, max, min, min, 0, centerEnd, width, 16);
        }
        arms.put(Direction.DOWN, List.copyOf(down));

        List<BakedQuad> up = new ArrayList<>(4);
        emitter = new NeoBakedQuadEmitter(up, sprite);
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            emitSquare(emitter, direction, min, max, max, 1.0F, min, 0, 0, width, centerStart);
        }
        arms.put(Direction.UP, List.copyOf(up));

        List<BakedQuad> north = new ArrayList<>(4);
        emitter = new NeoBakedQuadEmitter(north, sprite);
        emitSquare(emitter, Direction.WEST, 0, min, min, max, min, 0, 0, width, centerStart);
        emitSquare(emitter, Direction.UP, min, max, max, 1, min, 0, 0, width, centerStart);
        emitSquare(emitter, Direction.EAST, max, min, 1, max, min, 0, centerEnd, width, 16);
        emitSquare(emitter, Direction.DOWN, min, 0, max, min, min, 0, centerEnd, width, 16);
        arms.put(Direction.NORTH, List.copyOf(north));

        List<BakedQuad> south = new ArrayList<>(4);
        emitter = new NeoBakedQuadEmitter(south, sprite);
        emitSquare(emitter, Direction.WEST, max, min, 1, max, min, 0, centerEnd, width, 16);
        emitSquare(emitter, Direction.EAST, 0, min, min, max, min, 0, 0, width, centerStart);
        emitSquare(emitter, Direction.UP, min, 0, max, min, min, 0, centerEnd, width, 16);
        emitSquare(emitter, Direction.DOWN, min, max, max, 1, min, 0, 0, width, centerStart);
        arms.put(Direction.SOUTH, List.copyOf(south));

        List<BakedQuad> east = new ArrayList<>(4);
        emitter = new NeoBakedQuadEmitter(east, sprite);
        emitSquare(emitter, Direction.NORTH, 0, min, min, max, min, 0, 0, width, centerStart);
        emitSquare(emitter, Direction.SOUTH, max, min, 1, max, min, 0, centerEnd, width, 16);
        emitSquare(emitter, Direction.UP, max, min, 1, max, min, width, centerEnd, 0, 16);
        emitSquare(emitter, Direction.DOWN, max, min, 1, max, min, width, centerEnd, 0, 16);
        arms.put(Direction.EAST, List.copyOf(east));

        List<BakedQuad> west = new ArrayList<>(4);
        emitter = new NeoBakedQuadEmitter(west, sprite);
        emitSquare(emitter, Direction.NORTH, max, min, 1, max, min, 0, centerEnd, width, 16);
        emitSquare(emitter, Direction.SOUTH, 0, min, min, max, min, 0, 0, width, centerStart);
        emitSquare(emitter, Direction.UP, 0, min, min, max, min, width, 0, 0, centerStart);
        emitSquare(emitter, Direction.DOWN, 0, min, min, max, min, width, 0, 0, centerStart);
        arms.put(Direction.WEST, List.copyOf(west));
    }

    private static void emitSquare(NeoBakedQuadEmitter emitter, Direction direction,
                                   float left, float bottom, float right, float top, float depth,
                                   float u0, float v0, float u1, float v1) {
        emitter.square(direction, left, bottom, right, top, depth)
                .uv(0, u0, v0).uv(1, u0, v1).uv(2, u1, v1).uv(3, u1, v0).emit();
    }

    private void emitCap(NeoBakedQuadEmitter emitter, Direction direction,
                         float minX, float minY, float maxX, float maxY, float depth) {
        float u0 = (minX - (0.5F - this.radius)) * 16.0F;
        float u1 = (maxX - (0.5F - this.radius)) * 16.0F;
        emitter.square(direction, minX, minY, maxX, maxY, depth)
                .uv(0, u0, minY * 16.0F).uv(1, u0, maxY * 16.0F)
                .uv(2, u1, maxY * 16.0F).uv(3, u1, minY * 16.0F).emit();
    }

    @Override
    public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData data) {
        int mask = 0;
        if (level.getBlockEntity(pos) instanceof Connected connected) {
            for (Direction direction : Direction.values()) {
                if (connected.isConnected(direction)) mask |= 1 << direction.get3DDataValue();
            }
        }
        return ModelData.of(CONNECTIONS, mask);
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random,
                                    ModelData data, @Nullable RenderType renderType) {
        if (side != null) return List.of();
        int mask = data.has(CONNECTIONS) ? data.get(CONNECTIONS) : 0;
        List<BakedQuad> result = new ArrayList<>(36);
        for (Direction direction : Direction.values()) {
            if ((mask & (1 << direction.get3DDataValue())) != 0) result.addAll(arms.get(direction));
            else result.add(centerFaces.get(direction));
        }
        return result;
    }

    @Override public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random) {
        return List.of();
    }
    @Override public boolean useAmbientOcclusion() { return true; }
    @Override public boolean isGui3d() { return true; }
    @Override public boolean usesBlockLight() { return true; }
    @Override public boolean isCustomRenderer() { return false; }
    @Override public TextureAtlasSprite getParticleIcon() { return sprite; }
    @Override public ItemTransforms getTransforms() { return ItemTransforms.NO_TRANSFORMS; }
    @Override public ItemOverrides getOverrides() { return ItemOverrides.EMPTY; }
}
