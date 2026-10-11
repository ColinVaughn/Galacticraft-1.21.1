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
import dev.galacticraft.mod.content.GCLootTables;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;

/** Places rare abandoned survey installations on sufficiently broad lunar cave floors. */
public class LunarCaveLandmarkFeature extends Feature<NoneFeatureConfiguration> {
    public LunarCaveLandmarkFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();
        Direction forward = Direction.Plane.HORIZONTAL.getRandomDirection(random);
        Direction right = forward.getClockWise();
        BlockPos floor = null;
        for (int attempt = 0; attempt < 8; attempt++) {
            BlockPos candidate = findCaveFloor(level, (origin.getX() & ~15) + 3 + random.nextInt(10),
                    (origin.getZ() & ~15) + 3 + random.nextInt(10), random);
            if (candidate != null && hasRoom(level, candidate, forward, right)) { floor = candidate; break; }
        }
        if (floor == null) return false;
        var routes = LunarCavePlacement.routes(level, floor);
        BlockPos destination = null;
        String discovery = "Exposed sapphire seam";
        double bestDistance = 0;
        for (BlockPos candidate : routes.keySet()) {
            double distance = candidate.distSqr(floor);
            if (distance < 36) continue;
            String name = discoveryAt(level, candidate);
            if (name != null && (destination == null || distance > bestDistance)) {
                destination = candidate; discovery = name; bestDistance = distance;
            }
        }
        if (destination == null) {
            for (BlockPos candidate : routes.keySet()) {
                double distance = candidate.distSqr(floor);
                if (distance > bestDistance && LunarCavePlacement.ground(level, candidate.below())) {
                    destination = candidate; bestDistance = distance;
                }
            }
            if (destination == null || bestDistance < 64) return false;
            level.setBlock(destination.below(), GCBlocks.LUNAR_SAPPHIRE_ORE.defaultBlockState(), Block.UPDATE_CLIENTS);
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos deposit = destination.relative(direction).below();
                if (LunarCavePlacement.writable(level, deposit) && LunarCavePlacement.ground(level, deposit)) {
                    level.setBlock(deposit, GCBlocks.LUNAR_SAPPHIRE_ORE.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
        float type = random.nextFloat();
        ChestBlockEntity cache;
        if (type < 0.52F) {
            cache = buildSurveyCache(level, random, floor, forward, right);
        } else if (type < 0.82F) {
            cache = buildSeismicStation(level, random, floor, forward, right);
        } else {
            cache = buildCollapsedDrill(level, random, floor, forward, right);
        }
        // Recheck the actual installation: walls and equipment can change the walking route.
        routes = LunarCavePlacement.routes(level, floor.relative(forward, -2), 1536);
        if (!routes.containsKey(destination)) {
            BlockPos replacement = null;
            double distance = -1;
            for (BlockPos candidate : routes.keySet()) {
                if (LunarCavePlacement.ground(level, candidate.below()) && candidate.distSqr(floor) > distance) {
                    replacement = candidate; distance = candidate.distSqr(floor);
                }
            }
            destination = replacement != null ? replacement : floor.relative(forward, -2);
            discovery = "Exposed sapphire seam";
            level.setBlock(destination.below(), GCBlocks.LUNAR_SAPPHIRE_ORE.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        List<BlockPos> route = LunarCavePlacement.path(routes, destination);
        LunarCavePlacement.markRoute(level, route, GCBlocks.LUNAR_SCATTERED_FRAGMENTS);
        // A second, lit trail identifies the expedition's equipment rather than a natural deposit.
        for (int index = 4; index < route.size(); index += 7) {
            BlockPos edge = route.get(index).relative(forward.getClockWise());
            if (!route.contains(edge) && LunarCavePlacement.writable(level, edge) && LunarCavePlacement.walkable(level, edge) && level.isEmptyBlock(edge)) {
                level.setBlock(edge, GCBlocks.GLOWSTONE_TORCH.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
        for (int index = 7; index < route.size(); index += 11) {
            BlockPos equipment = route.get(index).relative(forward.getCounterClockWise());
            if (!route.contains(equipment) && LunarCavePlacement.writable(level, equipment) && LunarCavePlacement.walkable(level, equipment)) {
                LunarCavePlacement.place(level, equipment, GCBlocks.ABANDONED_SURVEY_BEACON, forward);
            }
        }
        if (cache != null) cache.setItem(26, surveyNote(discovery, destination));
        return true;
    }

    static ItemStack surveyNote(String discovery, BlockPos destination) {
        ItemStack note = new ItemStack(Items.PAPER);
        note.set(DataComponents.CUSTOM_NAME, Component.literal("Recovered lunar survey"));
        note.set(DataComponents.LORE, new ItemLore(List.of(Component.literal(discovery),
                Component.literal("Follow the marked passage to the deposit."),
                Component.literal("Survey: " + destination.getX() + ", " + destination.getY() + ", " + destination.getZ()))));
        return note;
    }

    private static String discoveryAt(WorldGenLevel level, BlockPos pos) {
        for (BlockPos probe : List.of(pos.below(), pos.north(), pos.south(), pos.east(), pos.west())) {
            if (!LunarCavePlacement.writable(level, probe)) continue;
            BlockState state = level.getBlockState(probe);
            if (state.is(GCBlocks.DENSE_ICE) || state.is(GCBlocks.ICE_BEARING_BRECCIA)) return "Volatile ice deposit";
            if (state.is(GCBlocks.FALLEN_METEOR)) return "Buried meteoric iron core";
            if (state.is(GCBlocks.HELION_NODULES) || state.is(GCBlocks.HELIUM_3_VENT)) return "Luminous helion pocket";
            if (state.is(GCBlocks.BUDDING_OLIVINE) || state.is(GCBlocks.RICH_OLIVINE_BASALT)) return "Olivine grotto";
        }
        return null;
    }

    private static BlockPos findCaveFloor(WorldGenLevel level, int x, int z, RandomSource random) {
        int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
        int maxY = Math.min(56, surface - 9);
        List<BlockPos> candidates = new ArrayList<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = Math.max(level.getMinBuildHeight() + 4, -40); y <= maxY; y++) {
            pos.set(x, y, z);
            if (level.getBlockState(pos).isAir()
                    && level.getBlockState(pos.above()).isAir()
                    && level.getBlockState(pos.above(2)).isAir()
                    && isStableGround(level.getBlockState(pos.below()))) {
                candidates.add(pos.immutable());
            }
        }
        return candidates.isEmpty() ? null : candidates.get(random.nextInt(candidates.size()));
    }

    private static boolean hasRoom(WorldGenLevel level, BlockPos floor, Direction forward, Direction right) {
        int supported = 0;
        for (int depth = -2; depth <= 3; depth++) {
            for (int side = -2; side <= 2; side++) {
                BlockPos column = local(floor, forward, right, side, depth, 0);
                if (!LunarCavePlacement.writable(level, column) || !level.getBlockState(column).isAir()
                        || !level.getBlockState(column.above()).isAir() || !level.getBlockState(column.above(2)).isAir()) {
                    return false;
                }
                if (isStableGround(level.getBlockState(column.below()))) {
                    supported++;
                }
            }
        }
        return supported >= 18;
    }

    private static ChestBlockEntity buildSurveyCache(WorldGenLevel level, RandomSource random, BlockPos floor,
                                         Direction forward, Direction right) {
        buildPlatform(level, random, floor, forward, right, 2, 3);
        BlockState aluminum = GCBlocks.ALUMINUM_DECORATION.block().defaultBlockState();
        BlockState dark = GCBlocks.DARK_DECORATION.block().defaultBlockState();

        for (int side = -2; side <= 2; side++) {
            if (Math.abs(side) < 2) continue; // Keep passages beside the supply chest open.
            setIfAir(level, local(floor, forward, right, side, 3, 0),
                    random.nextFloat() < 0.28F ? dark : aluminum);
        }
        for (int y = 1; y <= 2; y++) {
            setIfAir(level, local(floor, forward, right, -2, 3, y), aluminum);
            setIfAir(level, local(floor, forward, right, 2, 3, y), aluminum);
        }
        setIfAir(level, local(floor, forward, right, -2, 3, 3), Blocks.IRON_BARS.defaultBlockState());
        setIfAir(level, local(floor, forward, right, 2, 3, 3), Blocks.IRON_BARS.defaultBlockState());
        ChestBlockEntity cache = placeLootChest(level, random, local(floor, forward, right, 0, 3, 0), forward.getOpposite());
        setIfAir(level, local(floor, forward, right, -1, 3, 1), GCBlocks.GLOWSTONE_TORCH.defaultBlockState());
        setIfAir(level, local(floor, forward, right, 1, 3, 1), GCBlocks.GLOWSTONE_TORCH.defaultBlockState());
        return cache;
    }

    private static ChestBlockEntity buildSeismicStation(WorldGenLevel level, RandomSource random, BlockPos floor,
                                            Direction forward, Direction right) {
        buildPlatform(level, random, floor, forward, right, 2, 2);
        BlockState steel = GCBlocks.STEEL_DECORATION.block().defaultBlockState();
        BlockPos center = local(floor, forward, right, 0, 0, 0);
        level.setBlock(center.below(), steel, Block.UPDATE_CLIENTS);
        LunarCavePlacement.place(level, center, GCBlocks.ABANDONED_SURVEY_BEACON, forward);
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos sensor = center.relative(direction, 2);
            setIfAir(level, sensor, Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE.defaultBlockState());
            setIfAir(level, sensor.below(), GCBlocks.IRON_DECORATION.block().defaultBlockState());
        }
        ChestBlockEntity cache = placeLootChest(level, random, local(floor, forward, right, -2, 2, 0), right);
        setIfAir(level, local(floor, forward, right, 2, 2, 0), GCBlocks.GLOWSTONE_TORCH.defaultBlockState());
        return cache;
    }

    private static ChestBlockEntity buildCollapsedDrill(WorldGenLevel level, RandomSource random, BlockPos floor,
                                            Direction forward, Direction right) {
        buildPlatform(level, random, floor, forward, right, 2, 3);
        BlockState dark = GCBlocks.DARK_DECORATION.block().defaultBlockState();
        for (int y = 0; y <= 3; y++) {
            setIfAir(level, local(floor, forward, right, -2, 2, y), dark);
            if (y < 3 || random.nextBoolean()) {
                setIfAir(level, local(floor, forward, right, 2, 2, y), dark);
            }
        }
        for (int side = -2; side <= 2; side++) {
            setIfAir(level, local(floor, forward, right, side, 2, 3), GCBlocks.WALKWAY.defaultBlockState());
        }
        BlockPos drill = local(floor, forward, right, 0, 2, 2);
        setIfAir(level, drill, Blocks.CHAIN.defaultBlockState());
        setIfAir(level, drill.below(), Blocks.POINTED_DRIPSTONE.defaultBlockState());
        return placeLootChest(level, random, local(floor, forward, right, 2, 3, 0), right.getOpposite());
    }

    private static void buildPlatform(WorldGenLevel level, RandomSource random, BlockPos floor,
                                      Direction forward, Direction right, int halfWidth, int depth) {
        for (int localDepth = -2; localDepth <= depth; localDepth++) {
            for (int side = -halfWidth; side <= halfWidth; side++) {
                BlockPos pos = local(floor, forward, right, side, localDepth, -1);
                BlockState state = random.nextFloat() < 0.22F
                        ? GCBlocks.IRON_GRATING.defaultBlockState()
                        : GCBlocks.WALKWAY.defaultBlockState();
                if (isStableGround(level.getBlockState(pos)) || level.getBlockState(pos).isAir()) {
                    level.setBlock(pos, state, Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    static ChestBlockEntity placeLootChest(WorldGenLevel level, RandomSource random, BlockPos pos, Direction facing) {
        if (!level.getBlockState(pos).isAir()) {
            return null;
        }
        level.setBlock(pos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing), Block.UPDATE_CLIENTS);
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof ChestBlockEntity chest) {
            chest.setLootTable(GCLootTables.MOON_CAVE_EXPEDITION_CACHE, random.nextLong());
            return chest;
        }
        return null;
    }

    private static void setIfAir(WorldGenLevel level, BlockPos pos, BlockState state) {
        if (level.getBlockState(pos).isAir()) {
            level.setBlock(pos, state, Block.UPDATE_CLIENTS);
        }
    }

    private static BlockPos local(BlockPos origin, Direction forward, Direction right,
                                  int side, int depth, int y) {
        return origin.relative(right, side).relative(forward, depth).above(y);
    }

    private static boolean isStableGround(BlockState state) {
        return state.is(GCBlocks.VESICULAR_LUNAR_BASALT)
                || state.is(GCBlocks.ICE_BEARING_BRECCIA)
                || state.is(GCBlocks.MOON_ROCK)
                || state.is(GCBlocks.MOON_SURFACE_ROCK)
                || state.is(GCBlocks.LUNASLATE)
                || state.is(GCBlocks.MOON_BASALT)
                || state.is(GCBlocks.COBBLED_MOON_ROCK)
                || state.is(GCBlocks.COBBLED_LUNASLATE)
                || state.is(GCBlocks.DENSE_ICE)
                || state.is(GCBlocks.MOON_MOSS)
                || state.is(GCBlocks.MOON_CHEESE_BLOCK);
    }
}
