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

import com.mojang.serialization.Codec;
import dev.galacticraft.mod.content.GCBlocks;
import dev.galacticraft.mod.content.GCFluids;
import dev.galacticraft.mod.tag.GCBlockTags;
import dev.galacticraft.mod.world.biome.GCBiomes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** Sparse clusters on cave margins, with occasional contained luminous fluid pockets. */
public class LunarCaveDecorationFeature extends Feature<NoneFeatureConfiguration> {
    public LunarCaveDecorationFeature(Codec<NoneFeatureConfiguration> codec) { super(codec); }

    @Override public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        int startX = context.origin().getX() & ~15;
        int startZ = context.origin().getZ() & ~15;
        boolean placed = false;
        for (int attempt = 0; attempt < 3; attempt++) {
            BlockPos floor = LunarCavePlacement.floor(level, startX + 4 + random.nextInt(8),
                    startZ + 4 + random.nextInt(8), random);
            if (floor == null) continue;
            boolean cold = level.getBiome(floor).is(GCBiomes.Moon.COMET_TUNDRA);
            boolean mare = level.getBiome(floor).is(GCBiomes.Moon.BASALTIC_MARE);
            Block rubble = cold ? GCBlocks.LUNAR_FROSTED_BREAKDOWN_RUBBLE
                    : mare ? GCBlocks.LUNAR_BASALT_BREAKDOWN_RUBBLE : GCBlocks.LUNAR_BREAKDOWN_RUBBLE;
            placed |= decorateMargins(level, floor, rubble, random);
            placed |= replaceFloorMaterial(level, floor, cold, mare, random);
            boolean signalled = hasHelionSignal(level, floor);
            if (signalled && random.nextInt(4) == 0 || random.nextInt(40) == 0) placed |= helionPocket(level, floor, random);
        }
        return placed;
    }

    private static boolean decorateMargins(WorldGenLevel level, BlockPos floor, Block rubble, RandomSource random) {
        boolean placed = false;
        // Keep the center of this pocket clear; low fragments decorate its margins.
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            placed |= decorateEdge(level, floor, rubble, direction, random);
        }
        return placed;
    }

    private static boolean decorateEdge(WorldGenLevel level, BlockPos floor, Block rubble, Direction direction, RandomSource random) {
        boolean placed = false;
        BlockPos edge = floor.relative(direction, 2 + random.nextInt(2));
        if (random.nextFloat() < 0.30F) {
            int choice = random.nextInt(8);
            Block block = choice < 4 ? GCBlocks.LUNAR_SCATTERED_FRAGMENTS : choice < 6 ? rubble
                    : choice == 6 ? GCBlocks.LUNAR_FALLEN_ROOF_SLAB : GCBlocks.LUNAR_FRACTURED_OUTCROP;
            placed |= LunarCavePlacement.place(level, edge, block, direction);
        }
        for (int y = 1; y <= 5; y++) {
            BlockPos wall = edge.above(y);
            if (level.getBlockState(wall.relative(direction)).is(GCBlockTags.MOON_CAVE_SUBSTRATE)
                    && random.nextInt(4) == 0) {
                placed |= LunarCavePlacement.place(level, wall, GCBlocks.LUNAR_BASALT_WALL_SHELF, direction);
            }
        }
        for (int y = 4; y <= 12; y++) {
            BlockPos roof = edge.above(y);
            if (!level.isEmptyBlock(roof)) break;
            if (level.getBlockState(roof.above()).is(GCBlockTags.MOON_CAVE_SUBSTRATE)) {
                if (random.nextBoolean()) placed |= LunarCavePlacement.place(level, roof,
                        GCBlocks.LUNAR_CEILING_BREAKDOWN, direction);
                break;
            }
        }
        return placed;
    }

    private static boolean replaceFloorMaterial(WorldGenLevel level, BlockPos floor, boolean cold, boolean mare, RandomSource random) {
        boolean placed = false;
        // Only replace plain natural substrate; ores and installations are excluded by the tag.
        if (cold || mare) {
            Block material = cold ? GCBlocks.ICE_BEARING_BRECCIA : GCBlocks.VESICULAR_LUNAR_BASALT;
            for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
                BlockPos pos = floor.offset(x, -1, z);
                if (random.nextFloat() < 0.55F && LunarCavePlacement.ground(level, pos)) {
                    placed |= level.setBlock(pos, material.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
        return placed;
    }

    private static boolean hasHelionSignal(WorldGenLevel level, BlockPos floor) {
        boolean signalled = false;
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            var signal = level.getBlockState(floor.offset(dx, 0, dz));
            signalled |= signal.is(GCBlocks.HELION_NODULES) || signal.is(GCBlocks.HELIUM_3_VENT);
        }
        return signalled;
    }

    private static boolean helionPocket(WorldGenLevel level, BlockPos floor, RandomSource random) {
        Direction side = Direction.Plane.HORIZONTAL.getRandomDirection(random);
        BlockPos vent = floor.relative(side, 3);
        boolean placed = LunarCavePlacement.place(level, vent, GCBlocks.HELIUM_3_VENT, side);
        placed |= LunarCavePlacement.place(level, vent.relative(side.getClockWise()), GCBlocks.HELION_NODULES, side);
        for (int y = 1; y <= 4; y++) {
            BlockPos crust = vent.above(y);
            if (level.getBlockState(crust.relative(side)).is(GCBlockTags.MOON_CAVE_SUBSTRATE)) {
                placed |= LunarCavePlacement.place(level, crust, GCBlocks.HELION_WALL_CRUST, side);
            }
        }
        if (placed) {
            var routes = LunarCavePlacement.routes(level, floor);
            BlockPos approach = routes.keySet().stream().max(java.util.Comparator.comparingDouble(pos -> pos.distSqr(floor))).orElse(floor);
            var trail = LunarCavePlacement.path(routes, approach);
            java.util.Collections.reverse(trail);
            LunarCavePlacement.markRoute(level, trail, GCBlocks.HELION_NODULES);
        }
        // Preflight the complete basin, including a solid rim and bottom, before removing any floor.
        BlockPos basin = floor.below();
        for (int x = -1; x <= 2; x++) for (int z = -1; z <= 2; z++) {
            BlockPos pos = basin.offset(x, 0, z);
            if (!LunarCavePlacement.inside(floor, pos) || !LunarCavePlacement.ground(level, pos)
                    || !LunarCavePlacement.ground(level, pos.below()) || !level.isEmptyBlock(pos.above())) return placed;
        }
        for (int x = 0; x < 2; x++) for (int z = 0; z < 2; z++) {
            BlockPos pos = basin.offset(x, 0, z);
            level.setBlock(pos, GCBlocks.HELIUM_3.defaultBlockState(), Block.UPDATE_CLIENTS);
            level.scheduleTick(pos, GCFluids.HELIUM_3, GCFluids.HELIUM_3.getTickDelay(level));
        }
        return true;
    }
}
