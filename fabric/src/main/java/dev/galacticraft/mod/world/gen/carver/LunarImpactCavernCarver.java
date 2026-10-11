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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.carver.CarvingContext;
import net.minecraft.world.level.levelgen.carver.CaveCarverConfiguration;
import net.minecraft.world.level.levelgen.carver.WorldCarver;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Terraced impact cores, cold traps and collapse halls connected by readable fractures. */
public class LunarImpactCavernCarver extends WorldCarver<CaveCarverConfiguration> {
    private static final CarveSkipChecker FRACTURE_SHAPE = (context, x, y, z, absoluteY) ->
            y <= -0.68 || x * x + y * y + z * z >= 1.0;

    public LunarImpactCavernCarver(Codec<CaveCarverConfiguration> codec) { super(codec); }
    @Override public int getRange() { return 6; }
    @Override public boolean isStartChunk(CaveCarverConfiguration config, RandomSource random) {
        return random.nextFloat() <= config.probability;
    }

    @Override public boolean carve(CarvingContext context, CaveCarverConfiguration config, ChunkAccess chunk,
                                   Function<BlockPos, Holder<Biome>> posToBiome, RandomSource random,
                                   Aquifer aquifer, ChunkPos origin, CarvingMask mask) {
        double x = origin.getBlockX(random.nextInt(16)), y = config.y.sample(random, context);
        double z = origin.getBlockZ(random.nextInt(16)), heading = random.nextDouble() * Mth.TWO_PI;
        int count = random.nextFloat() < 0.30F ? 2 : 1;
        List<LunarChamber> chambers = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            var kind = LunarChamber.choose(posToBiome.apply(BlockPos.containing(x, y, z)), random);
            double radius = (18 + random.nextDouble() * 7) * config.yScale.sample(random)
                    * config.horizontalRadiusMultiplier.sample(random);
            double height = Math.max(8, radius * (kind == LunarChamber.Kind.COLD_TRAP ? 0.95
                    : kind == LunarChamber.Kind.CHEESE ? 0.48 : 0.72) * config.verticalRadiusMultiplier.sample(random));
            // Deep destinations are below the approach, leaving an overlooking balcony.
            double roomY = kind == LunarChamber.Kind.COLD_TRAP || kind == LunarChamber.Kind.IMPACT_CORE ? y - 3 : y;
            chambers.add(new LunarChamber(x, roomY, z, radius, height, heading, kind, random.nextLong()));
            double distance = 36 + random.nextDouble() * 16;
            heading += (random.nextDouble() - 0.5) * 1.35;
            x += Math.cos(heading) * distance;
            z += Math.sin(heading) * distance;
            y = Mth.clamp(y + (random.nextDouble() - 0.5) * 8, -12, 48);
        }
        // All geometry is completed before a block-dependent decoration check can run.
        for (int index = 0; index < chambers.size(); index++) {
            LunarChamber chamber = chambers.get(index);
            carveRoom(context, config, chunk, posToBiome, aquifer, mask, chamber);
            if (index > 0) {
                LunarChamber previous = chambers.get(index - 1);
                connector(context, config, chunk, posToBiome, aquifer, mask,
                        previous.x(), previous.y(), previous.z(), chamber.x(), chamber.y(), chamber.z(), 2.6, chambers);
            }
            RandomSource fractures = RandomSource.create(chamber.seed());
            int fractureCount = 1 + fractures.nextInt(2);
            for (int branch = 0; branch < fractureCount; branch++) {
                double angle = chamber.heading() + branch * Mth.TWO_PI / fractureCount;
                double length = chamber.radius() + 12 + fractures.nextDouble() * 12;
                connector(context, config, chunk, posToBiome, aquifer, mask,
                        chamber.x(), chamber.y(), chamber.z(), chamber.x() + Math.cos(angle) * length,
                        chamber.y() + (fractures.nextDouble() - 0.5) * 6,
                        chamber.z() + Math.sin(angle) * length, 2.0, chambers);
            }
        }
        for (int index = 0; index < chambers.size(); index++) {
            LunarChamber chamber = chambers.get(index);
            chamber.decorate(chunk);
            if (index > 0) {
                LunarChamber previous = chambers.get(index - 1);
                List<double[]> approach = new ArrayList<>();
                connectorPoints(previous.x(), previous.y(), previous.z(), chamber.x(), chamber.y(), chamber.z(), approach::add);
                for (int step = 5; step < approach.size() - 3; step += 5) {
                    double[] point = approach.get(step);
                    chamber.clue(chunk, point[0], point[1], point[2], (double) step / (approach.size() - 1));
                }
            }
        }
        return true;
    }

    static void connectorPoints(double startX, double startY, double startZ, double endX, double endY,
                                double endZ, java.util.function.Consumer<double[]> consumer) {
        double distance = Math.sqrt(Mth.square(endX - startX) + Mth.square(endY - startY) + Mth.square(endZ - startZ));
        int steps = Math.max(1, Mth.ceil(distance / 1.05));
        double heading = Math.atan2(endZ - startZ, endX - startX);
        double bow = Math.min(8, distance * 0.15) * (Math.sin(startX + startZ) < 0 ? -1 : 1);
        for (int step = 0; step <= steps; step++) {
            double progress = (double) step / steps;
            double bend = Math.sin(progress * Math.PI) * bow;
            consumer.accept(new double[]{Mth.lerp(progress, startX, endX) - Math.sin(heading) * bend,
                    Mth.lerp(progress, startY, endY), Mth.lerp(progress, startZ, endZ) + Math.cos(heading) * bend});
        }
    }

    private void connector(CarvingContext context, CaveCarverConfiguration config, ChunkAccess chunk,
                           Function<BlockPos, Holder<Biome>> biomes, Aquifer aquifer, CarvingMask mask,
                           double sx, double sy, double sz, double ex, double ey, double ez, double radius,
                           List<LunarChamber> rooms) {
        connectorPoints(sx, sy, sz, ex, ey, ez, point -> this.carveEllipsoid(context, config, chunk,
                biomes, aquifer, point[0], point[1], point[2], radius, radius * 1.15, mask,
                (context2, nx, ny, nz, absoluteY) -> FRACTURE_SHAPE.shouldSkip(context2, nx, ny, nz, absoluteY)
                        || LunarChamber.protectedStone(rooms, point[0] + nx * radius,
                        point[1] + ny * radius * 1.15, point[2] + nz * radius)));
    }

    private void carveRoom(CarvingContext context, CaveCarverConfiguration config, ChunkAccess chunk,
                           Function<BlockPos, Holder<Biome>> biomes, Aquifer aquifer, CarvingMask mask,
                           LunarChamber chamber) {
        this.carveEllipsoid(context, config, chunk, biomes, aquifer, chamber.x(), chamber.y(), chamber.z(),
                chamber.radius(), chamber.height(), mask,
                (ignored, nx, ny, nz, absoluteY) -> chamber.skip(nx, ny, nz));
    }
}
