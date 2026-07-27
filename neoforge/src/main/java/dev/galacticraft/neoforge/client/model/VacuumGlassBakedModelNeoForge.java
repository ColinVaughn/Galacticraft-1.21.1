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
                    emitCornerBasePlate(emitter, east, north, down, up);
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
        emitCornerPaneFace(emitter, east, north, low, high, false, false);
        emitCornerPaneFace(emitter, east, north, low, high, false, true);
        emitCornerPaneFace(emitter, east, north, low, high, true, false);
        emitCornerPaneFace(emitter, east, north, low, high, true, true);
    }

    private static void emitCornerPaneFace(NeoBakedQuadEmitter emitter, boolean east, boolean north,
                                           float low, float high, boolean inner, boolean reverse) {
        float ax = east
                ? (inner ? 1 - PANE_INSET : PANE_INSET)
                : (inner ? PANE_INSET : 1 - PANE_INSET);
        float az = north ? 0 : 1;
        float bx = east ? 1 : 0;
        float bz = north
                ? (inner ? PANE_INSET : 1 - PANE_INSET)
                : (inner ? 1 - PANE_INSET : PANE_INSET);
        float width = inner ? 8 : 13;

        if (!reverse) {
            emitter.pos(0, ax, low, az).pos(1, bx, low, bz).pos(2, bx, high, bz).pos(3, ax, high, az);
        } else {
            emitter.pos(3, ax, low, az).pos(2, bx, low, bz).pos(1, bx, high, bz).pos(0, ax, high, az);
        }
        emitter.uv(0, 0, low * 16).uv(1, width, low * 16)
                .uv(2, width, high * 16).uv(3, 0, high * 16).emit();
    }

    /**
     * Emits the complete two-step mitred rail used by the Fabric model.  A corner
     * needs horizontal caps and both diagonal side walls; a pair of back-to-back
     * vertical quads leaves the rail open and causes the atlas to show through.
     */
    private static void emitCornerBasePlate(NeoBakedQuadEmitter emitter, boolean east, boolean north,
                                            boolean down, boolean up) {
        if (!up) {
            emitCornerCap(emitter, east, north, 1, true);
            emitCornerRailSides(emitter, east, north, true);
        }
        if (!down) {
            emitCornerCap(emitter, east, north, 0, false);
            emitCornerRailSides(emitter, east, north, false);
        }
    }

    private static void emitCornerCap(NeoBakedQuadEmitter emitter, boolean east, boolean north,
                                      float y, boolean top) {
        if (top) {
            emitter
                    .pos(0, east ? 1 : 0, y, east ? 1 - FRAME_INSET : FRAME_INSET)
                    .pos(1, east ? 1 : 0, y, east ? FRAME_INSET : 1 - FRAME_INSET)
                    .pos(2, north ? 1 - FRAME_INSET : FRAME_INSET, y, north ? 0 : 1)
                    .pos(3, north ? FRAME_INSET : 1 - FRAME_INSET, y, north ? 0 : 1)
                    .nominalFace(Direction.UP).lockUv().emit();
        } else {
            emitter
                    .pos(3, east ? 1 : 0, y, east ? 1 - FRAME_INSET : FRAME_INSET)
                    .pos(2, east ? 1 : 0, y, east ? FRAME_INSET : 1 - FRAME_INSET)
                    .pos(1, north ? 1 - FRAME_INSET : FRAME_INSET, y, north ? 0 : 1)
                    .pos(0, north ? FRAME_INSET : 1 - FRAME_INSET, y, north ? 0 : 1)
                    .nominalFace(Direction.DOWN).lockUv().emit();
        }
    }

    private static void emitCornerRailSides(NeoBakedQuadEmitter emitter, boolean east, boolean north,
                                            boolean top) {
        float outerY = top ? 1 - FRAME_THICKNESS : FRAME_THICKNESS;
        float edgeY = top ? 1 : 0;
        float innerY = top ? 1 - INNER_FRAME_THICKNESS : INNER_FRAME_THICKNESS;

        // First diagonal side.
        emitCornerOuterWall(emitter, east, north, outerY, edgeY, false, top);
        emitCornerOuterShelf(emitter, east, north, outerY, false, top);
        emitCornerConnector(emitter, east, north, outerY, innerY, false, top);
        emitCornerInnerShelf(emitter, east, north, innerY, false, top);

        // Mirrored diagonal side.
        emitCornerOuterWall(emitter, east, north, outerY, edgeY, true, top);
        emitCornerOuterShelf(emitter, east, north, outerY, true, top);
        emitCornerConnector(emitter, east, north, outerY, innerY, true, top);
    }

    private static void emitCornerOuterWall(NeoBakedQuadEmitter emitter, boolean east, boolean north,
                                            float innerY, float edgeY, boolean reverse, boolean top) {
        float eax = east ? 1 : 0;
        float eaz = east
                ? (reverse ? 1 - FRAME_INSET : FRAME_INSET)
                : (reverse ? FRAME_INSET : 1 - FRAME_INSET);
        float nbx = north
                ? (reverse ? FRAME_INSET : 1 - FRAME_INSET)
                : (reverse ? 1 - FRAME_INSET : FRAME_INSET);
        float nbz = north ? 0 : 1;

        boolean innerFirst = top != reverse;
        if (innerFirst) {
            emitter.pos(0, eax, innerY, eaz).pos(1, nbx, innerY, nbz)
                    .pos(2, nbx, edgeY, nbz).pos(3, eax, edgeY, eaz);
        } else {
            emitter.pos(3, eax, innerY, eaz).pos(2, nbx, innerY, nbz)
                    .pos(1, nbx, edgeY, nbz).pos(0, eax, edgeY, eaz);
        }
        float innerV = top ? 14 : 2;
        float edgeV = top ? 16 : 0;
        emitter.uv(0, 4, innerFirst ? innerV : edgeV).uv(1, 9, innerFirst ? innerV : edgeV)
                .uv(2, 9, innerFirst ? edgeV : innerV).uv(3, 4, innerFirst ? edgeV : innerV).emit();
    }

    private static void emitCornerOuterShelf(NeoBakedQuadEmitter emitter, boolean east, boolean north,
                                             float y, boolean reverse, boolean top) {
        float outerE = east
                ? (reverse ? 1 - FRAME_INSET : FRAME_INSET)
                : (reverse ? FRAME_INSET : 1 - FRAME_INSET);
        float innerE = east
                ? (reverse ? 1 - INNER_FRAME_INSET : INNER_FRAME_INSET)
                : (reverse ? INNER_FRAME_INSET : 1 - INNER_FRAME_INSET);
        float outerN = north
                ? (reverse ? FRAME_INSET : 1 - FRAME_INSET)
                : (reverse ? 1 - FRAME_INSET : FRAME_INSET);
        float innerN = north
                ? (reverse ? INNER_FRAME_INSET : 1 - INNER_FRAME_INSET)
                : (reverse ? 1 - INNER_FRAME_INSET : INNER_FRAME_INSET);
        float ex = east ? 1 : 0;
        float nz = north ? 0 : 1;

        if (top) {
            emitter.pos(reverse ? 3 : 0, ex, y, outerE).pos(reverse ? 2 : 1, ex, y, innerE)
                    .pos(reverse ? 1 : 2, innerN, y, nz).pos(reverse ? 0 : 3, outerN, y, nz)
                    .nominalFace(Direction.DOWN).lockUv().emit();
        } else {
            emitter.pos(reverse ? 0 : 3, ex, y, outerE).pos(reverse ? 1 : 2, ex, y, innerE)
                    .pos(reverse ? 2 : 1, innerN, y, nz).pos(reverse ? 3 : 0, outerN, y, nz)
                    .nominalFace(Direction.UP).lockUv().emit();
        }
    }

    private static void emitCornerConnector(NeoBakedQuadEmitter emitter, boolean east, boolean north,
                                            float outerY, float innerY, boolean reverse, boolean top) {
        float ez = east
                ? (reverse ? 1 - INNER_FRAME_INSET : INNER_FRAME_INSET)
                : (reverse ? INNER_FRAME_INSET : 1 - INNER_FRAME_INSET);
        float nx = north
                ? (reverse ? INNER_FRAME_INSET : 1 - INNER_FRAME_INSET)
                : (reverse ? 1 - INNER_FRAME_INSET : INNER_FRAME_INSET);
        float ex = east ? 1 : 0;
        float nz = north ? 0 : 1;

        if (top) {
            emitter.pos(reverse ? 3 : 0, ex, outerY, ez).pos(reverse ? 2 : 1, ex, innerY, ez)
                    .pos(reverse ? 1 : 2, nx, innerY, nz).pos(reverse ? 0 : 3, nx, outerY, nz);
        } else {
            emitter.pos(reverse ? 0 : 3, ex, outerY, ez).pos(reverse ? 1 : 2, ex, innerY, ez)
                    .pos(reverse ? 2 : 1, nx, innerY, nz).pos(reverse ? 3 : 0, nx, outerY, nz);
        }
        float farU = top ? 9.65F : 10;
        emitter.uv(0, 4, top ? 13 : 2).uv(1, farU, top ? 14 : 2)
                .uv(2, farU, top ? 14 : 3).uv(3, 4, top ? 13 : 3).emit();
    }

    private static void emitCornerInnerShelf(NeoBakedQuadEmitter emitter, boolean east, boolean north,
                                             float y, boolean reverse, boolean top) {
        float nearE = east
                ? (reverse ? 1 - INNER_FRAME_INSET : INNER_FRAME_INSET)
                : (reverse ? INNER_FRAME_INSET : 1 - INNER_FRAME_INSET);
        float farE = 1 - nearE;
        float nearN = north
                ? (reverse ? INNER_FRAME_INSET : 1 - INNER_FRAME_INSET)
                : (reverse ? 1 - INNER_FRAME_INSET : INNER_FRAME_INSET);
        float farN = 1 - nearN;
        float ex = east ? 1 : 0;
        float nz = north ? 0 : 1;

        if (top) {
            emitter.pos(reverse ? 3 : 0, ex, y, nearE).pos(reverse ? 2 : 1, ex, y, farE)
                    .pos(reverse ? 1 : 2, farN, y, nz).pos(reverse ? 0 : 3, nearN, y, nz)
                    .nominalFace(Direction.DOWN).lockUv().emit();
        } else {
            emitter.pos(reverse ? 0 : 3, ex, y, nearE).pos(reverse ? 1 : 2, ex, y, farE)
                    .pos(reverse ? 2 : 1, farN, y, nz).pos(reverse ? 3 : 0, nearN, y, nz)
                    .nominalFace(Direction.UP).lockUv().emit();
        }
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
        float outerDepth = 1 - FRAME_THICKNESS;
        float innerDepth = 1 - INNER_FRAME_THICKNESS;
        switch (direction) {
            case NORTH -> {
                emitter.square(face, FRAME_INSET, top ? 0 : 1 - PANE_INSET,
                        1 - FRAME_INSET, top ? PANE_INSET : 1, outerDepth).emit();
                emitter.square(face, INNER_FRAME_INSET, top ? 0 : 1 - PANE_INSET,
                        1 - INNER_FRAME_INSET, top ? PANE_INSET : 1, innerDepth).emit();
                emitter.square(top ? Direction.UP : Direction.DOWN, FRAME_INSET,
                        top ? 1 - FRAME_INSET : 1 - FRAME_INSET,
                        1 - FRAME_INSET, top ? 1 : 1, 0).emit();
            }
            case SOUTH -> {
                emitter.square(face, FRAME_INSET, top ? 1 - PANE_INSET : 0,
                        1 - FRAME_INSET, top ? 1 : PANE_INSET, outerDepth).emit();
                emitter.square(face, INNER_FRAME_INSET, top ? 1 - PANE_INSET : 0,
                        1 - INNER_FRAME_INSET, top ? 1 : PANE_INSET, innerDepth).emit();
                emitter.square(top ? Direction.UP : Direction.DOWN, FRAME_INSET, 0,
                        1 - FRAME_INSET, FRAME_INSET, 0).emit();
            }
            case WEST -> {
                emitter.square(face, 0, FRAME_INSET, PANE_INSET, 1 - FRAME_INSET, outerDepth).emit();
                emitter.square(face, 0, INNER_FRAME_INSET, PANE_INSET, 1 - INNER_FRAME_INSET, innerDepth).emit();
                emitter.square(top ? Direction.UP : Direction.DOWN, 0, FRAME_INSET,
                        FRAME_INSET, 1 - FRAME_INSET, 0).emit();
            }
            case EAST -> {
                emitter.square(face, 1 - PANE_INSET, FRAME_INSET, 1, 1 - FRAME_INSET, outerDepth).emit();
                emitter.square(face, 1 - PANE_INSET, INNER_FRAME_INSET, 1, 1 - INNER_FRAME_INSET, innerDepth).emit();
                emitter.square(top ? Direction.UP : Direction.DOWN, 1 - FRAME_INSET, FRAME_INSET,
                        1, 1 - FRAME_INSET, 0).emit();
            }
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
