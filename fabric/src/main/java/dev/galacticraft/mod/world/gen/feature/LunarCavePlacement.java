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

package dev.galacticraft.mod.world.gen.feature;

import dev.galacticraft.mod.content.block.environment.LunarFormationBlock;
import dev.galacticraft.mod.tag.GCBlockTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.ArrayList;
import java.util.List;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Collections;

/** Support checks and bounded walking routes for lunar discoveries. */
final class LunarCavePlacement {
    private LunarCavePlacement() {}

    static boolean ground(WorldGenLevel level, BlockPos pos) {
        var state = level.getBlockState(pos);
        return state.is(GCBlockTags.MOON_CAVE_SUBSTRATE) && state.isFaceSturdy(level, pos, Direction.UP);
    }

    static BlockPos floor(WorldGenLevel level, int x, int z, RandomSource random) {
        int ceiling = Math.min(56, level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 9);
        List<BlockPos> candidates = new ArrayList<>();
        for (int y = Math.max(level.getMinBuildHeight() + 4, -48); y <= ceiling; y++) {
            BlockPos pos = new BlockPos(x, y, z);
            if (level.isEmptyBlock(pos) && level.isEmptyBlock(pos.above())
                    && level.isEmptyBlock(pos.above(2)) && ground(level, pos.below())) candidates.add(pos);
        }
        return candidates.isEmpty() ? null : candidates.get(random.nextInt(candidates.size()));
    }

    static boolean place(WorldGenLevel level, BlockPos pos, Block block, Direction facing) {
        if (!writable(level, pos)) return false;
        var state = block.defaultBlockState().setValue(LunarFormationBlock.FACING, facing);
        return level.isEmptyBlock(pos) && state.canSurvive(level, pos)
                && level.setBlock(pos, state, Block.UPDATE_CLIENTS);
    }

    static boolean inside(BlockPos center, BlockPos pos) {
        return (center.getX() >> 4) == (pos.getX() >> 4) && (center.getZ() >> 4) == (pos.getZ() >> 4);
    }

    static boolean walkable(WorldGenLevel level, BlockPos pos) {
        var state = level.getBlockState(pos);
        boolean low = state.isAir() || state.is(GCBlockTags.MOON_CAVE_FORMATIONS)
                && state.getCollisionShape(level, pos, CollisionContext.empty()).max(Direction.Axis.Y) <= 0.5;
        return low && level.isEmptyBlock(pos.above()) && level.isEmptyBlock(pos.above(2))
                && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
    }

    static boolean writable(WorldGenLevel level, BlockPos pos) {
        // FEATURES can write one neighboring chunk; reject farther candidates without an error log.
        if (level instanceof WorldGenRegion region
                && (Math.abs((pos.getX() >> 4) - region.getCenter().x) > 1
                || Math.abs((pos.getZ() >> 4) - region.getCenter().z) > 1)) return false;
        return level.ensureCanWrite(pos);
    }

    static Map<BlockPos, BlockPos> routes(WorldGenLevel level, BlockPos start) {
        return routes(level, start, 640);
    }

    static Map<BlockPos, BlockPos> routes(WorldGenLevel level, BlockPos start, int limit) {
        Map<BlockPos, BlockPos> parents = new LinkedHashMap<>();
        ArrayDeque<BlockPos> pending = new ArrayDeque<>();
        parents.put(start, start);
        pending.add(start);
        while (!pending.isEmpty() && parents.size() < limit) {
            BlockPos current = pending.removeFirst();
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                for (int rise : new int[]{0, 1, -1}) {
                    BlockPos next = current.relative(direction).above(rise);
                    if (Math.abs(next.getX() - start.getX()) > 22 || Math.abs(next.getZ() - start.getZ()) > 22
                            || Math.abs(next.getY() - start.getY()) > 12
                            || Math.abs((next.getX() >> 4) - (start.getX() >> 4)) > 1
                            || Math.abs((next.getZ() >> 4) - (start.getZ() >> 4)) > 1
                            || next.getY() <= level.getMinBuildHeight() || next.getY() >= level.getMaxBuildHeight() - 3
                            || !writable(level, next)) continue;
                    if (walkable(level, next)) {
                        if (!parents.containsKey(next)) { parents.put(next, current); pending.addLast(next); }
                        break;
                    }
                }
            }
        }
        return parents;
    }

    static List<BlockPos> path(Map<BlockPos, BlockPos> parents, BlockPos target) {
        List<BlockPos> result = new ArrayList<>();
        if (!parents.containsKey(target)) return result;
        for (BlockPos pos = target;; pos = parents.get(pos)) {
            result.add(pos);
            if (pos.equals(parents.get(pos))) break;
        }
        Collections.reverse(result);
        return result;
    }

    static void markRoute(WorldGenLevel level, List<BlockPos> route, Block clue) {
        var lane = new java.util.HashSet<>(route);
        for (int index = 1; index < route.size(); index++) {
            int spacing = index < route.size() / 2 ? 5 : 2;
            if (index % spacing != 0) continue;
            BlockPos pos = route.get(index);
            BlockPos previous = route.get(index - 1);
            Direction forward = Direction.fromDelta(pos.getX() - previous.getX(), 0, pos.getZ() - previous.getZ());
            if (forward == null) continue;
            for (Direction side : new Direction[]{forward.getClockWise(), forward.getCounterClockWise()}) {
                BlockPos edge = pos.relative(side);
                if (!lane.contains(edge) && writable(level, edge) && walkable(level, edge) && level.isEmptyBlock(edge)) {
                    place(level, edge, clue, forward);
                    break;
                }
            }
        }
    }
}
