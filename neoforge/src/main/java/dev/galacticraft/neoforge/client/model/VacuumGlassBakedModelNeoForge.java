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

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** NeoForge translation of Fabric's connected vacuum-glass baked model. */
public final class VacuumGlassBakedModelNeoForge implements BakedModel {
    private static final float PANE_INSET = 6.0F / 16.0F;
    private static final float INNER_FRAME_INSET = 5.0F / 16.0F;
    private static final float INNER_FRAME_THICKNESS = 3.0F / 16.0F;
    private static final float FRAME_INSET = 4.0F / 16.0F;
    private static final float FRAME_THICKNESS = 2.0F / 16.0F;

    private final TextureAtlasSprite glass;
    private final TextureAtlasSprite frame;
    private final Map<BlockState, List<BakedQuad>> cache = Collections.synchronizedMap(new IdentityHashMap<>());

    public VacuumGlassBakedModelNeoForge(TextureAtlasSprite glass, TextureAtlasSprite frame) {
        this.glass = glass;
        this.frame = frame;
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random,
                                    ModelData data, @Nullable RenderType renderType) {
        if (state == null || side != null) return List.of();
        return this.cache.computeIfAbsent(state, this::build);
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random) {
        if (state == null || side != null) return List.of();
        return this.cache.computeIfAbsent(state, this::build);
    }

    private List<BakedQuad> build(BlockState state) {
        List<BakedQuad> quads = new ArrayList<>();
        NeoBakedQuadEmitter emitter = new NeoBakedQuadEmitter(quads, this.glass);
        boolean up = state.getValue(BlockStateProperties.UP);
        boolean down = state.getValue(BlockStateProperties.DOWN);
        boolean north = state.getValue(BlockStateProperties.NORTH);
        boolean east = state.getValue(BlockStateProperties.EAST);
        boolean south = state.getValue(BlockStateProperties.SOUTH);
        boolean west = state.getValue(BlockStateProperties.WEST);
        int horizontal = (north ? 1 : 0) + (east ? 1 : 0) + (south ? 1 : 0) + (west ? 1 : 0);

        switch (horizontal) {
            case 0 -> {
                Direction.Axis axis = state.getValue(BlockStateProperties.HORIZONTAL_AXIS);
                Direction face = axis == Direction.Axis.X ? Direction.NORTH : Direction.EAST;
                emitter.sprite(this.glass);
                emitPane(emitter, face, false, false, down, up);
                emitPane(emitter, face.getOpposite(), false, false, down, up);
                emitter.sprite(this.frame);
                emitBasePlate(emitter, face, down, up);
                emitSides(emitter, face, false, false);
            }
            case 1 -> {
                Direction face = east || west ? Direction.NORTH : Direction.EAST;
                boolean left = face == Direction.NORTH ? east : south;
                boolean right = face == Direction.NORTH ? west : north;
                emitter.sprite(this.glass);
                emitPane(emitter, face, left, right, down, up);
                emitPane(emitter, face.getOpposite(), right, left, down, up);
                emitter.sprite(this.frame);
                emitBasePlate(emitter, face, down, up);
                emitSides(emitter, face, left, right);
            }
            case 2 -> {
                if (east && west || north && south) {
                    Direction face = east && west ? Direction.NORTH : Direction.EAST;
                    emitter.sprite(this.glass);
                    emitPane(emitter, face, true, true, down, up);
                    emitPane(emitter, face.getOpposite(), true, true, down, up);
                    emitter.sprite(this.frame);
                    emitBasePlate(emitter, face, down, up);
                } else {
                    emitter.sprite(this.glass);
                    emitCornerPane(emitter, east, down, north, up);
                    emitter.sprite(this.frame);
                    emitCornerFrame(emitter, east, north, down, up);
                }
            }
            case 3 -> {
                Direction missing = !north ? Direction.NORTH : !east ? Direction.EAST : !south ? Direction.SOUTH : Direction.WEST;
                Direction face = missing.getOpposite();
                emitter.sprite(this.glass);
                emitBrokenPane(emitter, face, down, up);
                emitPane(emitter, face.getOpposite(), true, true, down, up);
                emitCenterPane(emitter, face, down, up);
                emitter.sprite(this.frame);
                emitBasePlate(emitter, face, down, up);
                emitCenterBasePlate(emitter, face, down, up);
            }
            case 4 -> {
                emitter.sprite(this.glass);
                for (Direction face : Direction.Plane.HORIZONTAL) emitCenterPane(emitter, face, down, up);
                emitter.sprite(this.frame);
                for (Direction face : Direction.Plane.HORIZONTAL) emitCenterBasePlate(emitter, face, down, up);
                if (!down) emitter.square(Direction.DOWN, FRAME_INSET, FRAME_INSET, 1 - FRAME_INSET, 1 - FRAME_INSET, 0).emit();
                if (!up) emitter.square(Direction.UP, FRAME_INSET, FRAME_INSET, 1 - FRAME_INSET, 1 - FRAME_INSET, 0).emit();
            }
        }
        return List.copyOf(quads);
    }

    private static void emitSides(NeoBakedQuadEmitter emitter, Direction direction, boolean left, boolean right) {
        if (!left) {
            emitter.square(direction.getClockWise(), FRAME_INSET, 0, 1 - FRAME_INSET, 1, 0).emit();
            emitter.square(direction, 0, 0, FRAME_THICKNESS, 1, FRAME_INSET).emit();
            emitter.square(direction, FRAME_THICKNESS, 0, INNER_FRAME_THICKNESS, 1, INNER_FRAME_INSET).emit();
            emitter.square(direction.getCounterClockWise(), FRAME_INSET, 0, INNER_FRAME_INSET, 1, 1 - FRAME_THICKNESS).emit();
            emitter.square(direction.getOpposite(), 1 - FRAME_THICKNESS, 0, 1, 1, FRAME_INSET).emit();
            emitter.square(direction.getOpposite(), 1 - INNER_FRAME_THICKNESS, 0, 1 - FRAME_THICKNESS, 1, INNER_FRAME_INSET).emit();
            emitter.square(direction.getCounterClockWise(), 1 - INNER_FRAME_INSET, 0, 1 - FRAME_INSET, 1, 1 - FRAME_THICKNESS).emit();
            emitter.square(direction.getCounterClockWise(), INNER_FRAME_INSET, 0, 1 - INNER_FRAME_INSET, 1, 1 - INNER_FRAME_THICKNESS).emit();
        }
        if (!right) {
            emitter.square(direction.getCounterClockWise(), FRAME_INSET, 0, 1 - FRAME_INSET, 1, 0).emit();
            emitter.square(direction.getOpposite(), 0, 0, FRAME_THICKNESS, 1, FRAME_INSET).emit();
            emitter.square(direction.getOpposite(), FRAME_THICKNESS, 0, INNER_FRAME_THICKNESS, 1, INNER_FRAME_INSET).emit();
            emitter.square(direction.getClockWise(), FRAME_INSET, 0, INNER_FRAME_INSET, 1, 1 - FRAME_THICKNESS).emit();
            emitter.square(direction, 1 - FRAME_THICKNESS, 0, 1, 1, FRAME_INSET).emit();
            emitter.square(direction, 1 - INNER_FRAME_THICKNESS, 0, 1 - FRAME_THICKNESS, 1, INNER_FRAME_INSET).emit();
            emitter.square(direction.getClockWise(), 1 - INNER_FRAME_INSET, 0, 1 - FRAME_INSET, 1, 1 - FRAME_THICKNESS).emit();
            emitter.square(direction.getClockWise(), INNER_FRAME_INSET, 0, 1 - INNER_FRAME_INSET, 1, 1 - INNER_FRAME_THICKNESS).emit();
        }
    }

    private static void emitCornerPane(NeoBakedQuadEmitter emitter, boolean east, boolean down, boolean north, boolean up) {
        float low = down ? 0 : INNER_FRAME_THICKNESS;
        float high = up ? 1 : 1 - INNER_FRAME_THICKNESS;
        cornerPaneFace(emitter, east, north, low, high, false);
        cornerPaneFace(emitter, east, north, low, high, true);
    }

    private static void cornerPaneFace(NeoBakedQuadEmitter emitter, boolean east, boolean north, float low, float high, boolean reverse) {
        float ax = east ? PANE_INSET : 1 - PANE_INSET;
        float az = north ? 0 : 1;
        float bx = east ? 1 : 0;
        float bz = north ? 1 - PANE_INSET : PANE_INSET;
        if (!reverse) {
            emitter.pos(0, ax, low, az).pos(1, bx, low, bz).pos(2, bx, high, bz).pos(3, ax, high, az);
        } else {
            emitter.pos(3, ax, low, az).pos(2, bx, low, bz).pos(1, bx, high, bz).pos(0, ax, high, az);
        }
        emitter.uv(0, 0, low * 16).uv(1, 13, low * 16).uv(2, 13, high * 16).uv(3, 0, high * 16).emit();
    }

    private static void emitCornerFrame(NeoBakedQuadEmitter emitter, boolean east, boolean north, boolean down, boolean up) {
        // The two rails meet on the same diagonal as Fabric's mitred corner model.
        if (!down) emitDiagonalRail(emitter, east, north, 0, INNER_FRAME_THICKNESS);
        if (!up) emitDiagonalRail(emitter, east, north, 1 - INNER_FRAME_THICKNESS, 1);
    }

    private static void emitDiagonalRail(NeoBakedQuadEmitter emitter, boolean east, boolean north, float y0, float y1) {
        float ax = east ? 1 : 0;
        float az = east ? FRAME_INSET : 1 - FRAME_INSET;
        float bx = north ? 1 - FRAME_INSET : FRAME_INSET;
        float bz = north ? 0 : 1;
        emitter.pos(0, ax, y0, az).pos(1, bx, y0, bz).pos(2, bx, y1, bz).pos(3, ax, y1, az)
                .uv(0, 4, y0 * 16).uv(1, 10, y0 * 16).uv(2, 10, y1 * 16).uv(3, 4, y1 * 16).emit();
        emitter.pos(3, ax, y0, az).pos(2, bx, y0, bz).pos(1, bx, y1, bz).pos(0, ax, y1, az)
                .uv(0, 4, y1 * 16).uv(1, 10, y1 * 16).uv(2, 10, y0 * 16).uv(3, 4, y0 * 16).emit();
    }

    private static void emitBasePlate(NeoBakedQuadEmitter emitter, Direction direction, boolean down, boolean up) {
        boolean side = direction.getAxis() == Direction.Axis.X;
        if (!down) {
            emitter.square(direction, 0, 0, 1, FRAME_THICKNESS, FRAME_INSET).emit();
            emitter.square(direction, 0, 0, 1, INNER_FRAME_THICKNESS, INNER_FRAME_INSET).emit();
            emitter.square(direction.getOpposite(), 0, 0, 1, FRAME_THICKNESS, FRAME_INSET).emit();
            emitter.square(direction.getOpposite(), 0, 0, 1, INNER_FRAME_THICKNESS, INNER_FRAME_INSET).emit();
            emitter.square(Direction.UP, side ? FRAME_INSET : 0, side ? 0 : FRAME_INSET, side ? 1 - FRAME_INSET : 1, side ? 1 : 1 - FRAME_INSET, 1 - FRAME_THICKNESS).emit();
            emitter.square(Direction.UP, side ? INNER_FRAME_INSET : 0, side ? 0 : INNER_FRAME_INSET, side ? 1 - INNER_FRAME_INSET : 1, side ? 1 : 1 - INNER_FRAME_INSET, 1 - INNER_FRAME_THICKNESS).emit();
            emitter.square(Direction.DOWN, side ? FRAME_INSET : 0, side ? 0 : FRAME_INSET, side ? 1 - FRAME_INSET : 1, side ? 1 : 1 - FRAME_INSET, 0).emit();
        }
        if (!up) {
            emitter.square(direction, 0, 1 - FRAME_THICKNESS, 1, 1, FRAME_INSET).emit();
            emitter.square(direction, 0, 1 - INNER_FRAME_THICKNESS, 1, 1, INNER_FRAME_INSET).emit();
            emitter.square(direction.getOpposite(), 0, 1 - FRAME_THICKNESS, 1, 1, FRAME_INSET).emit();
            emitter.square(direction.getOpposite(), 0, 1 - INNER_FRAME_THICKNESS, 1, 1, INNER_FRAME_INSET).emit();
            emitter.square(Direction.DOWN, side ? FRAME_INSET : 0, side ? 0 : FRAME_INSET, side ? 1 - FRAME_INSET : 1, side ? 1 : 1 - FRAME_INSET, 1 - FRAME_THICKNESS).emit();
            emitter.square(Direction.DOWN, side ? INNER_FRAME_INSET : 0, side ? 0 : INNER_FRAME_INSET, side ? 1 - INNER_FRAME_INSET : 1, side ? 1 : 1 - INNER_FRAME_INSET, 1 - INNER_FRAME_THICKNESS).emit();
            emitter.square(Direction.UP, side ? FRAME_INSET : 0, side ? 0 : FRAME_INSET, side ? 1 - FRAME_INSET : 1, side ? 1 : 1 - FRAME_INSET, 0).emit();
        }
    }

    private static void emitCenterBasePlate(NeoBakedQuadEmitter emitter, Direction direction, boolean down, boolean up) {
        if (!down) {
            emitter.square(direction.getClockWise(), 1 - FRAME_INSET, 0, 1, FRAME_THICKNESS, FRAME_INSET).emit();
            emitter.square(direction.getClockWise(), 1 - INNER_FRAME_INSET, 0, 1, INNER_FRAME_THICKNESS, INNER_FRAME_INSET).emit();
            emitter.square(direction.getCounterClockWise(), 0, 0, FRAME_INSET, FRAME_THICKNESS, FRAME_INSET).emit();
            emitter.square(direction.getCounterClockWise(), 0, 0, INNER_FRAME_INSET, INNER_FRAME_THICKNESS, INNER_FRAME_INSET).emit();
            emitCenterHorizontal(emitter, direction, false);
        }
        if (!up) {
            emitter.square(direction.getClockWise(), 1 - FRAME_INSET, 1 - FRAME_THICKNESS, 1, 1, FRAME_INSET).emit();
            emitter.square(direction.getClockWise(), 1 - INNER_FRAME_INSET, 1 - INNER_FRAME_THICKNESS, 1, 1, INNER_FRAME_INSET).emit();
            emitter.square(direction.getCounterClockWise(), 0, 1 - FRAME_THICKNESS, FRAME_INSET, 1, FRAME_INSET).emit();
            emitter.square(direction.getCounterClockWise(), 0, 1 - INNER_FRAME_THICKNESS, INNER_FRAME_INSET, 1, INNER_FRAME_INSET).emit();
            emitCenterHorizontal(emitter, direction, true);
        }
    }

    private static void emitCenterHorizontal(NeoBakedQuadEmitter emitter, Direction direction, boolean top) {
        Direction face = top ? Direction.DOWN : Direction.UP;
        float depth = 1 - FRAME_THICKNESS;
        switch (direction) {
            case NORTH -> emitter.square(face, FRAME_INSET, top ? 0 : 1 - PANE_INSET, 1 - FRAME_INSET, top ? PANE_INSET : 1, depth).emit();
            case SOUTH -> emitter.square(face, FRAME_INSET, top ? 1 - PANE_INSET : 0, 1 - FRAME_INSET, top ? 1 : PANE_INSET, depth).emit();
            case WEST -> emitter.square(face, 0, FRAME_INSET, PANE_INSET, 1 - FRAME_INSET, depth).emit();
            case EAST -> emitter.square(face, 1 - PANE_INSET, FRAME_INSET, 1, 1 - FRAME_INSET, depth).emit();
        }
    }

    private static void emitCenterPane(NeoBakedQuadEmitter emitter, Direction direction, boolean down, boolean up) {
        float low = down ? 0 : INNER_FRAME_THICKNESS;
        float high = up ? 1 : 1 - INNER_FRAME_THICKNESS;
        emitter.square(direction.getClockWise(), 1 - PANE_INSET, low, 1, high, PANE_INSET).emit();
        emitter.square(direction.getCounterClockWise(), 0, low, PANE_INSET, high, PANE_INSET).emit();
        emitter.square(direction.getCounterClockWise(), 0, low, PANE_INSET, high, 1 - PANE_INSET).emit();
        emitter.square(direction.getClockWise(), 1 - PANE_INSET, low, 1, high, 1 - PANE_INSET).emit();
    }

    private static void emitPane(NeoBakedQuadEmitter emitter, Direction direction, boolean left, boolean right, boolean down, boolean up) {
        emitter.square(direction, left ? 0 : INNER_FRAME_THICKNESS, down ? 0 : INNER_FRAME_THICKNESS,
                right ? 1 : 1 - INNER_FRAME_THICKNESS, up ? 1 : 1 - INNER_FRAME_THICKNESS, PANE_INSET).emit();
        emitter.square(direction.getOpposite(), right ? 0 : INNER_FRAME_THICKNESS, down ? 0 : INNER_FRAME_THICKNESS,
                left ? 1 : 1 - INNER_FRAME_THICKNESS, up ? 1 : 1 - INNER_FRAME_THICKNESS, 1 - PANE_INSET).emit();
    }

    private static void emitBrokenPane(NeoBakedQuadEmitter emitter, Direction direction, boolean down, boolean up) {
        float low = down ? 0 : INNER_FRAME_THICKNESS;
        float high = up ? 1 : 1 - INNER_FRAME_THICKNESS;
        emitter.square(direction, 0, low, PANE_INSET, high, PANE_INSET).emit();
        emitter.square(direction, 1 - PANE_INSET, low, 1, high, PANE_INSET).emit();
        emitter.square(direction.getOpposite(), 0, low, PANE_INSET, high, 1 - PANE_INSET).emit();
        emitter.square(direction.getOpposite(), 1 - PANE_INSET, low, 1, high, 1 - PANE_INSET).emit();
    }

    @Override public boolean useAmbientOcclusion() { return true; }
    @Override public boolean isGui3d() { return true; }
    @Override public boolean usesBlockLight() { return true; }
    @Override public boolean isCustomRenderer() { return false; }
    @Override public TextureAtlasSprite getParticleIcon() { return this.glass; }
    @Override public ItemTransforms getTransforms() { return ItemTransforms.NO_TRANSFORMS; }
    @Override public ItemOverrides getOverrides() { return ItemOverrides.EMPTY; }
}
