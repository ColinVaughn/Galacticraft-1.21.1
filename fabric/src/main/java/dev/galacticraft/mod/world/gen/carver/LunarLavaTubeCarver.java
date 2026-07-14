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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.carver.CarvingContext;
import net.minecraft.world.level.levelgen.carver.CaveCarverConfiguration;
import net.minecraft.world.level.levelgen.carver.WorldCarver;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Carves drained lunar lava tubes rather than terrestrial solution caves.
 *
 * <p>Lunar tubes are modelled as long, gently graded conduits in mare basalt. Their flat floors,
 * occasional distributary branches, widened flow chambers, and rare collapsed skylights are all
 * features seen in terrestrial basalt tubes or inferred from lunar pits and sinuous rilles.</p>
 */
public class LunarLavaTubeCarver extends WorldCarver<CaveCarverConfiguration> {
    private static final int RANGE = 8;
    private static final double STEP_LENGTH = 1.25;
    private static final CarveSkipChecker TUBE_SHAPE = (context, x, y, z, absoluteY) ->
            y <= -0.62 || x * x + y * y + z * z >= 1.0;
    private static final CarveSkipChecker ROUND_SHAPE = (context, x, y, z, absoluteY) ->
            x * x + y * y + z * z >= 1.0;

    public LunarLavaTubeCarver(Codec<CaveCarverConfiguration> codec) {
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
        double startX = origin.getBlockX(random.nextInt(16));
        double startY = config.y.sample(random, context);
        double startZ = origin.getBlockZ(random.nextInt(16));
        double heading = random.nextDouble() * Mth.TWO_PI;
        double pitch = (random.nextDouble() - 0.5) * 0.035;
        double radius = (2.8 + random.nextDouble() * 2.2)
                * config.yScale.sample(random)
                * config.horizontalRadiusMultiplier.sample(random);
        double verticalScale = config.verticalRadiusMultiplier.sample(random);
        int length = 76 + random.nextInt(41);
        int firstChamber = length / 4 + random.nextInt(Math.max(1, length / 5));
        int secondChamber = length * 3 / 5 + random.nextInt(Math.max(1, length / 6));
        int branchCount = random.nextFloat() < 0.78F ? 1 + random.nextInt(2) : 0;
        boolean hasSkylight = random.nextFloat() < 0.18F;
        int skylightPoint = length / 4 + random.nextInt(Math.max(1, length / 2));

        List<TubeNode> mainTube = createPath(random, startX, startY, startZ, heading, pitch, radius,
                verticalScale, length, firstChamber, secondChamber, true);
        carvePath(context, config, chunk, posToBiome, aquifer, carvingMask, mainTube);

        for (int branchIndex = 0; branchIndex < branchCount; branchIndex++) {
            int branchPoint = length / 5 + random.nextInt(Math.max(1, length * 3 / 5));
            TubeNode junction = mainTube.get(branchPoint);
            double branchTurn = (random.nextBoolean() ? 1.0 : -1.0) * (0.55 + random.nextDouble() * 0.55);
            int branchLength = 28 + random.nextInt(29);
            List<TubeNode> branch = createPath(random, junction.x, junction.y, junction.z,
                    junction.heading + branchTurn, pitch * 0.5 + (random.nextDouble() - 0.5) * 0.045,
                    radius * (0.52 + random.nextDouble() * 0.22), verticalScale, branchLength,
                    branchLength / 2, -100, true);
            carvePath(context, config, chunk, posToBiome, aquifer, carvingMask, branch);
        }

        // A bypass rejoins the main conduit and turns an otherwise linear tube into a natural loop.
        // Split-flow passages like this form where a lava stream flows around a stable obstruction.
        if (random.nextFloat() < 0.48F) {
            int bypassStart = length / 4;
            int bypassEnd = length * 3 / 4;
            List<TubeNode> bypass = createBypass(random, mainTube.get(bypassStart), mainTube.get(bypassEnd),
                    radius * 0.58, verticalScale);
            carvePath(context, config, chunk, posToBiome, aquifer, carvingMask, bypass);
        }

        if (hasSkylight) {
            carveSkylight(context, config, chunk, posToBiome, aquifer, carvingMask, mainTube.get(skylightPoint));
        }

        placeBreakdownFormations(chunk, random, mainTube.get(firstChamber));
        placeBreakdownFormations(chunk, random, mainTube.get(secondChamber));

        return true;
    }

    private static List<TubeNode> createPath(RandomSource random, double x, double y, double z, double heading,
                                             double pitch, double baseRadius, double verticalScale, int length,
                                             int firstChamber, int secondChamber, boolean allowChamber) {
        List<TubeNode> nodes = new ArrayList<>(length);
        double headingVelocity = 0.0;
        double pitchVelocity = 0.0;

        for (int step = 0; step < length; step++) {
            double firstPulse = Math.max(0.0, 1.0 - Math.abs(step - firstChamber) / 7.0);
            double secondPulse = Math.max(0.0, 1.0 - Math.abs(step - secondChamber) / 8.0);
            double chamber = allowChamber ? Math.max(firstPulse, secondPulse) : 0.0;
            double pulse = 1.0 + Math.sin(step * 0.31) * 0.08 + chamber * 0.42;
            double horizontalRadius = Math.min(7.5, baseRadius * pulse);
            double verticalRadius = Math.max(2.0, horizontalRadius * verticalScale);
            nodes.add(new TubeNode(x, y, z, horizontalRadius, verticalRadius, heading));

            x += Math.cos(heading) * Math.cos(pitch) * STEP_LENGTH;
            z += Math.sin(heading) * Math.cos(pitch) * STEP_LENGTH;
            y += Math.sin(pitch) * STEP_LENGTH;

            // Low-viscosity lava follows a persistent course; changes accumulate slowly instead of
            // making the sharp, chaotic turns used by vanilla cave worms.
            headingVelocity = headingVelocity * 0.86 + (random.nextDouble() - 0.5) * 0.035;
            pitchVelocity = pitchVelocity * 0.72 + (random.nextDouble() - 0.5) * 0.006;
            heading += headingVelocity;
            pitch = Mth.clamp(pitch * 0.92 + pitchVelocity, -0.075, 0.075);
        }
        return nodes;
    }

    private static List<TubeNode> createBypass(RandomSource random, TubeNode start, TubeNode end,
                                                double radius, double verticalScale) {
        int length = Math.max(18, Mth.floor(Math.sqrt(Mth.square(end.x - start.x)
                + Mth.square(end.y - start.y) + Mth.square(end.z - start.z)) / STEP_LENGTH));
        List<TubeNode> nodes = new ArrayList<>(length + 1);
        double side = random.nextBoolean() ? 1.0 : -1.0;
        double lateralX = -Math.sin(start.heading) * side;
        double lateralZ = Math.cos(start.heading) * side;
        double bow = 7.0 + random.nextDouble() * 6.0;
        for (int step = 0; step <= length; step++) {
            double progress = (double) step / length;
            double offset = Math.sin(progress * Math.PI) * bow;
            double x = Mth.lerp(progress, start.x, end.x) + lateralX * offset;
            double y = Mth.lerp(progress, start.y, end.y) + Math.sin(progress * Mth.TWO_PI) * 1.5;
            double z = Mth.lerp(progress, start.z, end.z) + lateralZ * offset;
            double heading = Math.atan2(end.z - start.z, end.x - start.x);
            nodes.add(new TubeNode(x, y, z, radius, Math.max(2.0, radius * verticalScale), heading));
        }
        return nodes;
    }

    private void carvePath(CarvingContext context, CaveCarverConfiguration config, ChunkAccess chunk,
                           Function<BlockPos, Holder<Biome>> posToBiome, Aquifer aquifer, CarvingMask carvingMask,
                           List<TubeNode> nodes) {
        // Adjacent ellipsoids overlap at this spacing, producing a continuous conduit without the
        // giant spherical rooms and recursive tangles of the vanilla cave algorithm.
        for (int i = 0; i < nodes.size(); i += 2) {
            TubeNode node = nodes.get(i);
            this.carveEllipsoid(context, config, chunk, posToBiome, aquifer, node.x, node.y, node.z,
                    node.horizontalRadius, node.verticalRadius, carvingMask, TUBE_SHAPE);
        }

        // A narrow, dark basalt gutter marks the last lava that drained through the conduit. It
        // also gives explorers a visual trail through junctions without adding artificial light.
        BlockPos.MutableBlockPos floorPos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < nodes.size(); i += 2) {
            TubeNode node = nodes.get(i);
            double perpendicularX = -Math.sin(node.heading);
            double perpendicularZ = Math.cos(node.heading);
            for (int offset = -1; offset <= 1; offset++) {
                lineFlowGutter(chunk, floorPos,
                        Mth.floor(node.x + perpendicularX * offset),
                        Mth.floor(node.y),
                        Mth.floor(node.z + perpendicularZ * offset),
                        Mth.ceil(node.verticalRadius + 2.0));
            }
        }
    }

    private static void lineFlowGutter(ChunkAccess chunk, BlockPos.MutableBlockPos pos, int x, int centerY, int z,
                                       int searchDepth) {
        if (x < chunk.getPos().getMinBlockX() || x > chunk.getPos().getMaxBlockX()
                || z < chunk.getPos().getMinBlockZ() || z > chunk.getPos().getMaxBlockZ()) {
            return;
        }

        pos.set(x, centerY, z);
        for (int depth = 0; depth <= searchDepth; depth++) {
            if (!chunk.getBlockState(pos).isAir()) {
                if (chunk.getBlockState(pos).is(GCBlocks.MOON_ROCK)
                        || chunk.getBlockState(pos).is(GCBlocks.LUNASLATE)) {
                    chunk.setBlockState(pos, GCBlocks.MOON_BASALT.defaultBlockState(), false);
                }
                return;
            }
            pos.move(0, -1, 0);
        }
    }

    private static void placeBreakdownFormations(ChunkAccess chunk, RandomSource random, TubeNode chamber) {
        int centerX = Mth.floor(chamber.x);
        int centerZ = Mth.floor(chamber.z);
        if (!chunk.getPos().equals(new ChunkPos(new BlockPos(centerX, 0, centerZ)))) {
            return;
        }

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int attempts = 10 + random.nextInt(7);
        for (int attempt = 0; attempt < attempts; attempt++) {
            double angle = random.nextDouble() * Mth.TWO_PI;
            double distance = Math.sqrt(random.nextDouble()) * chamber.horizontalRadius * 0.72;
            int x = Mth.floor(chamber.x + Math.cos(angle) * distance);
            int z = Mth.floor(chamber.z + Math.sin(angle) * distance);
            if (x < chunk.getPos().getMinBlockX() || x > chunk.getPos().getMaxBlockX()
                    || z < chunk.getPos().getMinBlockZ() || z > chunk.getPos().getMaxBlockZ()) {
                continue;
            }

            int centerY = Mth.floor(chamber.y);
            int roof = Mth.ceil(chamber.y + chamber.verticalRadius + 2.0);
            pos.set(x, centerY, z);
            while (pos.getY() <= roof && chunk.getBlockState(pos).isAir()) {
                pos.move(0, 1, 0);
            }
            if (pos.getY() <= roof && random.nextFloat() < 0.62F) {
                int dripLength = 1 + random.nextInt(3);
                pos.move(0, -1, 0);
                for (int dy = 0; dy < dripLength && chunk.getBlockState(pos).isAir(); dy++) {
                    chunk.setBlockState(pos, GCBlocks.MOON_BASALT.defaultBlockState(), false);
                    pos.move(0, -1, 0);
                }
            }

            int floor = Mth.floor(chamber.y - chamber.verticalRadius - 2.0);
            pos.set(x, centerY, z);
            while (pos.getY() >= floor && chunk.getBlockState(pos).isAir()) {
                pos.move(0, -1, 0);
            }
            if (pos.getY() >= floor && random.nextFloat() < 0.48F) {
                pos.move(0, 1, 0);
                int rubbleHeight = random.nextFloat() < 0.22F ? 2 : 1;
                for (int dy = 0; dy < rubbleHeight && chunk.getBlockState(pos).isAir(); dy++) {
                    chunk.setBlockState(pos, GCBlocks.MOON_BASALT.defaultBlockState(), false);
                    pos.move(0, 1, 0);
                }
            }
        }
    }

    private void carveSkylight(CarvingContext context, CaveCarverConfiguration config, ChunkAccess chunk,
                               Function<BlockPos, Holder<Biome>> posToBiome, Aquifer aquifer,
                               CarvingMask carvingMask, TubeNode node) {
        int blockX = Mth.floor(node.x);
        int blockZ = Mth.floor(node.z);
        if (!chunk.getPos().equals(new ChunkPos(new BlockPos(blockX, 0, blockZ)))) {
            return;
        }

        int localX = blockX & 15;
        int localZ = blockZ & 15;
        int surfaceY = chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, localX, localZ);
        double roofY = node.y + node.verticalRadius * 0.75;
        double overburden = surfaceY - roofY;
        if (overburden < 5.0 || overburden > 30.0) {
            return;
        }

        double shaftRadius = Math.min(2.4, node.horizontalRadius * 0.42);
        for (double y = roofY; y <= surfaceY; y += 1.5) {
            this.carveEllipsoid(context, config, chunk, posToBiome, aquifer, node.x, y, node.z,
                    shaftRadius, 1.8, carvingMask, ROUND_SHAPE);
        }

        // A slightly flared rim reads as a collapse pit from the surface and makes the entrance
        // discoverable without turning every tube into an open ravine.
        this.carveEllipsoid(context, config, chunk, posToBiome, aquifer, node.x, surfaceY - 0.5, node.z,
                shaftRadius + 1.2, 2.2, carvingMask, ROUND_SHAPE);
    }

    private record TubeNode(double x, double y, double z, double horizontalRadius, double verticalRadius,
                            double heading) {
    }
}
