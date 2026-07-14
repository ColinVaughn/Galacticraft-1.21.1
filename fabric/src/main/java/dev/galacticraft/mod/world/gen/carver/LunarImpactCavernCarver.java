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

package dev.galacticraft.mod.world.gen.carver;

import com.mojang.serialization.Codec;
import dev.galacticraft.mod.content.GCBlocks;
import dev.galacticraft.mod.world.biome.GCBiomes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.carver.CarvingContext;
import net.minecraft.world.level.levelgen.carver.CaveCarverConfiguration;
import net.minecraft.world.level.levelgen.carver.WorldCarver;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Makes bounded collapse caverns linked by impact fractures in the Moon's ancient crust.
 *
 * <p>This is the lunar counterpart to the Overworld's large noise caves: it supplies memorable
 * rooms and junctions without creating terrestrial, water-eroded cave networks. Retained bedrock
 * columns, angular cracks, roof-fall debris, buried impactors, volatile cold traps, and uncommon
 * olivine exposures make each chamber useful for both navigation and exploration.</p>
 */
public class LunarImpactCavernCarver extends WorldCarver<CaveCarverConfiguration> {
    private static final int RANGE = 6;
    private static final CarveSkipChecker CHAMBER_SHAPE = (context, x, y, z, absoluteY) ->
            y <= -0.76 || x * x + y * y + z * z >= 1.0;
    private static final CarveSkipChecker FRACTURE_SHAPE = (context, x, y, z, absoluteY) ->
            y <= -0.68 || x * x + y * y + z * z >= 1.0;

    public LunarImpactCavernCarver(Codec<CaveCarverConfiguration> codec) {
        super(codec);
    }

    @Override
    public int getRange() {
        return RANGE;
    }

    @Override
    public boolean isStartChunk(CaveCarverConfiguration config, RandomSource random) {
        return random.nextFloat() <= config.probability;
    }

    @Override
    public boolean carve(CarvingContext context, CaveCarverConfiguration config, ChunkAccess chunk,
                         Function<BlockPos, Holder<Biome>> posToBiome, RandomSource random, Aquifer aquifer,
                         ChunkPos origin, CarvingMask carvingMask) {
        double x = origin.getBlockX(random.nextInt(16));
        double y = config.y.sample(random, context);
        double z = origin.getBlockZ(random.nextInt(16));
        double heading = random.nextDouble() * Mth.TWO_PI;
        int chamberCount = 2 + random.nextInt(2);
        List<Chamber> chambers = new ArrayList<>(chamberCount);

        for (int index = 0; index < chamberCount; index++) {
            double horizontalRadius = (7.5 + random.nextDouble() * 5.5)
                    * config.yScale.sample(random)
                    * config.horizontalRadiusMultiplier.sample(random);
            double verticalRadius = Math.max(3.0, horizontalRadius
                    * (0.42 + random.nextDouble() * 0.18)
                    * config.verticalRadiusMultiplier.sample(random));
            boolean retainPillar = horizontalRadius >= 7.0 && random.nextFloat() < 0.58F;
            double pillarX = (random.nextDouble() - 0.5) * 0.55;
            double pillarZ = (random.nextDouble() - 0.5) * 0.55;
            Chamber chamber = new Chamber(x, y, z, horizontalRadius, verticalRadius);
            chambers.add(chamber);

            CarveSkipChecker shape = retainPillar
                    ? pillaredChamberShape(pillarX, pillarZ, 0.12 + random.nextDouble() * 0.06)
                    : CHAMBER_SHAPE;
            this.carveEllipsoid(context, config, chunk, posToBiome, aquifer, x, y, z,
                    horizontalRadius, verticalRadius, carvingMask, shape);

            if (index + 1 < chamberCount) {
                double distance = 18.0 + random.nextDouble() * 15.0;
                heading += (random.nextDouble() - 0.5) * 0.85;
                double nextX = x + Math.cos(heading) * distance;
                double nextZ = z + Math.sin(heading) * distance;
                double nextY = Mth.clamp(y + (random.nextDouble() - 0.5) * 7.0, -12.0, 48.0);
                carveConnector(context, config, chunk, posToBiome, aquifer, carvingMask, random,
                        x, y, z, nextX, nextY, nextZ);
                x = nextX;
                y = nextY;
                z = nextZ;
            }
        }

        for (Chamber chamber : chambers) {
            carveRadialFractures(context, config, chunk, posToBiome, aquifer, carvingMask, random, chamber);
            decorateChamber(chunk, posToBiome, random, chamber);
        }
        return true;
    }

    private static CarveSkipChecker pillaredChamberShape(double pillarX, double pillarZ, double pillarRadius) {
        return (context, x, y, z, absoluteY) -> {
            if (y <= -0.76 || x * x + y * y + z * z >= 1.0) {
                return true;
            }
            double dx = x - pillarX;
            double dz = z - pillarZ;
            double taper = pillarRadius * (1.0 + Math.abs(y) * 0.75);
            return dx * dx + dz * dz < taper * taper;
        };
    }

    private void carveConnector(CarvingContext context, CaveCarverConfiguration config, ChunkAccess chunk,
                                Function<BlockPos, Holder<Biome>> posToBiome, Aquifer aquifer,
                                CarvingMask carvingMask, RandomSource random, double startX, double startY,
                                double startZ, double endX, double endY, double endZ) {
        double distance = Math.sqrt(Mth.square(endX - startX) + Mth.square(endY - startY)
                + Mth.square(endZ - startZ));
        int steps = Math.max(1, Mth.ceil(distance / 1.4));
        double radius = 1.8 + random.nextDouble() * 1.2;
        for (int step = 1; step < steps; step += 2) {
            double progress = (double) step / steps;
            double pulse = 1.0 + Math.sin(progress * Math.PI) * 0.35;
            this.carveEllipsoid(context, config, chunk, posToBiome, aquifer,
                    Mth.lerp(progress, startX, endX),
                    Mth.lerp(progress, startY, endY),
                    Mth.lerp(progress, startZ, endZ),
                    radius * pulse, radius * (0.9 + pulse * 0.25), carvingMask, FRACTURE_SHAPE);
        }
    }

    private void carveRadialFractures(CarvingContext context, CaveCarverConfiguration config, ChunkAccess chunk,
                                      Function<BlockPos, Holder<Biome>> posToBiome, Aquifer aquifer,
                                      CarvingMask carvingMask, RandomSource random, Chamber chamber) {
        int count = 2 + random.nextInt(3);
        double baseHeading = random.nextDouble() * Mth.TWO_PI;
        for (int fracture = 0; fracture < count; fracture++) {
            double heading = baseHeading + fracture * Mth.TWO_PI / count + (random.nextDouble() - 0.5) * 0.5;
            double length = 10.0 + random.nextDouble() * 17.0;
            double startDistance = chamber.horizontalRadius * 0.45;
            double startX = chamber.x + Math.cos(heading) * startDistance;
            double startZ = chamber.z + Math.sin(heading) * startDistance;
            double endX = chamber.x + Math.cos(heading) * (chamber.horizontalRadius + length);
            double endZ = chamber.z + Math.sin(heading) * (chamber.horizontalRadius + length);
            double endY = chamber.y + (random.nextDouble() - 0.5) * 8.0;
            carveConnector(context, config, chunk, posToBiome, aquifer, carvingMask, random,
                    startX, chamber.y, startZ, endX, endY, endZ);
        }
    }

    private static void decorateChamber(ChunkAccess chunk, Function<BlockPos, Holder<Biome>> posToBiome,
                                        RandomSource random, Chamber chamber) {
        int centerX = Mth.floor(chamber.x);
        int centerZ = Mth.floor(chamber.z);
        if (!chunk.getPos().equals(new ChunkPos(new BlockPos(centerX, 0, centerZ)))) {
            return;
        }

        BlockPos center = BlockPos.containing(chamber.x, chamber.y, chamber.z);
        Holder<Biome> biome = posToBiome.apply(center);
        boolean coldTrap = biome.is(GCBiomes.Moon.COMET_TUNDRA);
        boolean olivineProvince = biome.is(GCBiomes.Moon.OLIVINE_SPIKES);
        boolean freshImpact = biome.is(GCBiomes.Moon.RAY_CRATER_FIELD);
        boolean cheeseGrove = biome.is(GCBiomes.Moon.CHEESE_GROVE);

        int rubbleAttempts = 14 + random.nextInt(10);
        for (int attempt = 0; attempt < rubbleAttempts; attempt++) {
            double angle = random.nextDouble() * Mth.TWO_PI;
            double distance = Math.sqrt(random.nextDouble()) * chamber.horizontalRadius * 0.82;
            BlockPos floor = findFloor(chunk,
                    Mth.floor(chamber.x + Math.cos(angle) * distance),
                    Mth.floor(chamber.z + Math.sin(angle) * distance), chamber);
            if (floor == null || random.nextFloat() >= 0.54F) {
                continue;
            }
            int height = random.nextFloat() < 0.18F ? 2 : 1;
            BlockState rubble = (floor.getY() < 4 ? GCBlocks.COBBLED_LUNASLATE : GCBlocks.COBBLED_MOON_ROCK)
                    .defaultBlockState();
            for (int dy = 0; dy < height; dy++) {
                BlockPos place = floor.above(dy);
                if (chunk.getBlockState(place).isAir()) {
                    chunk.setBlockState(place, rubble, false);
                }
            }
        }

        if (coldTrap) {
            placeFloorPatches(chunk, random, chamber, GCBlocks.DENSE_ICE, 9 + random.nextInt(7));
            placeFloorColumns(chunk, random, chamber, GCBlocks.DENSE_ICE, 5 + random.nextInt(4), 2, 5);
        }
        if (olivineProvince || random.nextFloat() < 0.13F) {
            placeWallDeposit(chunk, random, chamber,
                    olivineProvince ? 7 + random.nextInt(5) : 3 + random.nextInt(3));
            placeOlivineGrotto(chunk, random, chamber, olivineProvince ? 7 : 3);
        }
        if (cheeseGrove) {
            placeCheeseGrotto(chunk, random, chamber);
        }
        if (freshImpact) {
            placeImpactMeltLens(chunk, random, chamber);
        } else if (!coldTrap && !cheeseGrove && random.nextFloat() < 0.56F) {
            placeFloorColumns(chunk, random, chamber,
                    chamber.y < 4 ? GCBlocks.LUNASLATE : GCBlocks.MOON_ROCK,
                    2 + random.nextInt(3), 1, 4);
        }
        if ((freshImpact && random.nextFloat() < 0.30F) || random.nextFloat() < 0.035F) {
            BlockPos floor = findFloor(chunk, centerX, centerZ, chamber);
            if (floor != null && chunk.getBlockState(floor).isAir()) {
                chunk.setBlockState(floor, GCBlocks.FALLEN_METEOR.defaultBlockState(), false);
            }
        }
    }

    private static void placeFloorColumns(ChunkAccess chunk, RandomSource random, Chamber chamber, Block block,
                                          int attempts, int minHeight, int maxHeight) {
        for (int attempt = 0; attempt < attempts; attempt++) {
            double angle = random.nextDouble() * Mth.TWO_PI;
            double distance = Math.sqrt(random.nextDouble()) * chamber.horizontalRadius * 0.72;
            BlockPos floor = findFloor(chunk,
                    Mth.floor(chamber.x + Math.cos(angle) * distance),
                    Mth.floor(chamber.z + Math.sin(angle) * distance), chamber);
            if (floor == null) {
                continue;
            }
            int height = minHeight + random.nextInt(Math.max(1, maxHeight - minHeight + 1));
            for (int dy = 0; dy < height; dy++) {
                BlockPos place = floor.above(dy);
                if (!chunk.getBlockState(place).isAir()) {
                    break;
                }
                chunk.setBlockState(place, block.defaultBlockState(), false);
            }
        }
    }

    private static void placeOlivineGrotto(ChunkAccess chunk, RandomSource random, Chamber chamber, int attempts) {
        for (int attempt = 0; attempt < attempts; attempt++) {
            double angle = random.nextDouble() * Mth.TWO_PI;
            double distance = Math.sqrt(random.nextDouble()) * chamber.horizontalRadius * 0.68;
            BlockPos floor = findFloor(chunk,
                    Mth.floor(chamber.x + Math.cos(angle) * distance),
                    Mth.floor(chamber.z + Math.sin(angle) * distance), chamber);
            if (floor == null || !chunk.getBlockState(floor).isAir()) {
                continue;
            }
            BlockPos base = floor.below();
            if (!isLunarStone(chunk.getBlockState(base))) {
                continue;
            }
            chunk.setBlockState(base, GCBlocks.BUDDING_OLIVINE.defaultBlockState(), false);
            int spikeHeight = 1 + random.nextInt(3);
            for (int dy = 0; dy < spikeHeight; dy++) {
                BlockPos place = floor.above(dy);
                if (!chunk.getBlockState(place).isAir()) {
                    break;
                }
                chunk.setBlockState(place,
                        dy == spikeHeight - 1
                                ? GCBlocks.OLIVINE_CLUSTER.defaultBlockState()
                                : GCBlocks.OLIVINE_BLOCK.defaultBlockState(),
                        false);
            }
        }
    }

    private static void placeCheeseGrotto(ChunkAccess chunk, RandomSource random, Chamber chamber) {
        int attempts = 12 + random.nextInt(9);
        for (int attempt = 0; attempt < attempts; attempt++) {
            double angle = random.nextDouble() * Mth.TWO_PI;
            double distance = Math.sqrt(random.nextDouble()) * chamber.horizontalRadius * 0.76;
            BlockPos floor = findFloor(chunk,
                    Mth.floor(chamber.x + Math.cos(angle) * distance),
                    Mth.floor(chamber.z + Math.sin(angle) * distance), chamber);
            if (floor == null || !chunk.getBlockState(floor).isAir()) {
                continue;
            }
            BlockPos substrate = floor.below();
            if (!isLunarStone(chunk.getBlockState(substrate))) {
                continue;
            }
            chunk.setBlockState(substrate,
                    random.nextFloat() < 0.22F
                            ? GCBlocks.MOON_CHEESE_BLOCK.defaultBlockState()
                            : GCBlocks.MOON_MOSS.defaultBlockState(),
                    false);
            if (random.nextFloat() < 0.72F) {
                chunk.setBlockState(floor,
                        random.nextBoolean()
                                ? GCBlocks.MOON_WEED.defaultBlockState()
                                : GCBlocks.MOON_SHRUBS.defaultBlockState(),
                        false);
            }
        }
    }

    private static void placeImpactMeltLens(ChunkAccess chunk, RandomSource random, Chamber chamber) {
        int centerX = Mth.floor(chamber.x);
        int centerZ = Mth.floor(chamber.z);
        int radius = 2 + random.nextInt(3);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radius * radius || random.nextFloat() < 0.18F) {
                    continue;
                }
                BlockPos floor = findFloor(chunk, centerX + dx, centerZ + dz, chamber);
                if (floor != null && isLunarStone(chunk.getBlockState(floor.below()))) {
                    chunk.setBlockState(floor.below(), GCBlocks.MOON_BASALT.defaultBlockState(), false);
                }
            }
        }
    }

    private static void placeFloorPatches(ChunkAccess chunk, RandomSource random, Chamber chamber, Block block,
                                          int attempts) {
        for (int attempt = 0; attempt < attempts; attempt++) {
            double angle = random.nextDouble() * Mth.TWO_PI;
            double distance = Math.sqrt(random.nextDouble()) * chamber.horizontalRadius * 0.7;
            BlockPos floor = findFloor(chunk,
                    Mth.floor(chamber.x + Math.cos(angle) * distance),
                    Mth.floor(chamber.z + Math.sin(angle) * distance), chamber);
            if (floor == null) {
                continue;
            }
            BlockPos substrate = floor.below();
            if (isLunarStone(chunk.getBlockState(substrate))) {
                chunk.setBlockState(substrate, block.defaultBlockState(), false);
                if (random.nextBoolean() && chunk.getBlockState(floor).isAir()) {
                    chunk.setBlockState(floor, block.defaultBlockState(), false);
                }
            }
        }
    }

    private static void placeWallDeposit(ChunkAccess chunk, RandomSource random, Chamber chamber, int attempts) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int attempt = 0; attempt < attempts; attempt++) {
            double angle = random.nextDouble() * Mth.TWO_PI;
            double dx = Math.cos(angle);
            double dz = Math.sin(angle);
            int y = Mth.floor(chamber.y + (random.nextDouble() - 0.5) * chamber.verticalRadius);
            for (double distance = chamber.horizontalRadius * 0.45;
                 distance <= chamber.horizontalRadius + 2.0; distance += 0.75) {
                pos.set(Mth.floor(chamber.x + dx * distance), y, Mth.floor(chamber.z + dz * distance));
                if (!isInsideChunk(chunk, pos)) {
                    break;
                }
                BlockState state = chunk.getBlockState(pos);
                if (isLunarStone(state)) {
                    chunk.setBlockState(pos, GCBlocks.RICH_OLIVINE_BASALT.defaultBlockState(), false);
                    break;
                }
            }
        }
    }

    private static BlockPos findFloor(ChunkAccess chunk, int x, int z, Chamber chamber) {
        if (x < chunk.getPos().getMinBlockX() || x > chunk.getPos().getMaxBlockX()
                || z < chunk.getPos().getMinBlockZ() || z > chunk.getPos().getMaxBlockZ()) {
            return null;
        }
        int top = Mth.floor(chamber.y + chamber.verticalRadius);
        int bottom = Mth.floor(chamber.y - chamber.verticalRadius - 2.0);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, top, z);
        for (int y = top; y >= bottom; y--) {
            pos.setY(y);
            if (chunk.getBlockState(pos).isAir() && !chunk.getBlockState(pos.below()).isAir()) {
                return pos.immutable();
            }
        }
        return null;
    }

    private static boolean isLunarStone(BlockState state) {
        return state.is(GCBlocks.MOON_ROCK)
                || state.is(GCBlocks.MOON_SURFACE_ROCK)
                || state.is(GCBlocks.LUNASLATE)
                || state.is(GCBlocks.MOON_BASALT);
    }

    private static boolean isInsideChunk(ChunkAccess chunk, BlockPos pos) {
        return pos.getX() >= chunk.getPos().getMinBlockX() && pos.getX() <= chunk.getPos().getMaxBlockX()
                && pos.getZ() >= chunk.getPos().getMinBlockZ() && pos.getZ() <= chunk.getPos().getMaxBlockZ();
    }

    private record Chamber(double x, double y, double z, double horizontalRadius, double verticalRadius) {
    }
}
