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
import dev.galacticraft.mod.content.block.entity.VolcanoVentBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** Builds a randomized Venus shield volcano with a lava caldera and eruption vent. */
public class VolcanoFeature extends Feature<NoneFeatureConfiguration> {
    public VolcanoFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();

        // Per-volcano shape.
        int baseRadius = 8 + random.nextInt(9);                                  // 8 - 16 (footprint)
        double slope = 0.75 + random.nextDouble() * 1.05;                         // squat shield -> tall cone
        int height = Mth.clamp((int) Math.round(baseRadius * slope), 8, 28);
        double profileExp = 1.4 + random.nextDouble() * 1.6;                      // flared base, pointier summit
        // Keep the caldera inside the cone radius so it cannot overhang.
        int calderaRadius = Mth.clamp((int) Math.round(baseRadius * (0.18 + random.nextDouble() * 0.12)), 2, Math.max(2, baseRadius / 2 - 2));
        int calderaDepth = Math.min(3 + random.nextInt(4), height / 2);           // 3 - 6, capped
        double noiseAmp = 1.2 + random.nextDouble() * 2.0;                        // gentle -> craggy outline
        int volcanicPct = 42 + random.nextInt(38);                               // dark/old -> bright/active surface
        int channels = random.nextInt(4);                                        // 0 - 3 lava rivers
        double noiseSeed = random.nextDouble() * 10.0;

        int cx = origin.getX();
        int cz = origin.getZ();
        // Reject steep sites so the cone does not float or bury itself.
        int baseY = groundAt(level, cx, cz);
        int minGround = baseY, maxGround = baseY;
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4.0;
            int g = groundAt(level, cx + (int) Math.round(Math.cos(a) * baseRadius), cz + (int) Math.round(Math.sin(a) * baseRadius));
            minGround = Math.min(minGround, g);
            maxGround = Math.max(maxGround, g);
        }
        if (maxGround - minGround > 15) {
            return false; // footprint is too uneven
        }
        // Sit the summit relative to the average rim height so it doesn't perch on the highest point.
        baseY = (minGround + maxGround) / 2;

        BlockState volcanic = GCBlocks.VOLCANIC_ROCK.defaultBlockState();
        BlockState scorched = GCBlocks.SCORCHED_VENUS_ROCK.defaultBlockState();
        BlockState basalt = Blocks.BASALT.defaultBlockState();
        BlockState magma = Blocks.MAGMA_BLOCK.defaultBlockState();
        BlockState lava = Blocks.LAVA.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();

        // Build the dome: parabolic profile, edge perturbed by value noise, speckled surface.
        for (int dx = -baseRadius - 3; dx <= baseRadius + 3; dx++) {
            for (int dz = -baseRadius - 3; dz <= baseRadius + 3; dz++) {
                double dist = Math.sqrt(dx * dx + dz * dz) - bump(dx, dz, noiseSeed) * noiseAmp;
                int colHeight = coneHeight(dist, height, baseRadius, profileExp);
                if (colHeight < 0) continue;
                int topY = baseY + colHeight;
                // Fill from local terrain to cone surface so flanks meet the hillside.
                int groundY = groundAt(level, cx + dx, cz + dz);
                if (topY < groundY) continue; // terrain is already higher here
                int fromY = Math.max(Math.min(groundY, baseY) - 2, baseY - 14);
                for (int y = fromY; y <= topY; y++) {
                    m.set(cx + dx, y, cz + dz);
                    BlockState s;
                    if (y == topY) {
                        int roll = random.nextInt(100);
                        s = roll < volcanicPct ? volcanic : (roll < volcanicPct + (100 - volcanicPct) / 2 ? scorched : basalt);
                    } else {
                        s = (random.nextInt(9) == 0) ? basalt : volcanic;
                    }
                    level.setBlock(m, s, 2);
                }
            }
        }

        // The crater floor follows the cone top so the lava pool stays inside the summit.
        for (int dx = -calderaRadius; dx <= calderaRadius; dx++) {
            for (int dz = -calderaRadius; dz <= calderaRadius; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > calderaRadius + 0.3) continue;
                int coneTop = baseY + coneHeight(d, height, baseRadius, profileExp);
                int bowl = (int) Math.round(calderaDepth * (1.0 - d / (calderaRadius + 0.5)));
                int floorY = coneTop - bowl;
                for (int y = floorY + 1; y <= coneTop + 2; y++) {
                    m.set(cx + dx, y, cz + dz);
                    level.setBlock(m, air, 2);
                }
                m.set(cx + dx, floorY, cz + dz);
                level.setBlock(m, d > calderaRadius - 0.8 ? magma : lava, 2);
                m.set(cx + dx, floorY - 1, cz + dz);
                level.setBlock(m, magma, 2);
            }
        }

        // Lava rivers down random flanks.
        for (int i = 0; i < channels; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            boolean wide = random.nextBoolean();
            lavaRiver(level, m, cx, cz, baseY, height, baseRadius, profileExp, calderaRadius, angle, wide, lava, air);
        }

        // Flag 3 creates the vent block entity.
        m.set(cx, baseY + height - calderaDepth, cz);
        level.setBlock(m, GCBlocks.VOLCANO_VENT.defaultBlockState(), 3);
        if (level.getBlockEntity(m) instanceof VolcanoVentBlockEntity vent) {
            vent.configure(calderaRadius + 1); // eruption lava starts outside the crater lip
        }
        return true;
    }

    private static void lavaRiver(WorldGenLevel level, BlockPos.MutableBlockPos m, int cx, int cz, int baseY,
                                  int height, int baseRadius, double profileExp, int calderaRadius,
                                  double angle, boolean wide, BlockState lava, BlockState air) {
        double ax = Math.cos(angle), az = Math.sin(angle);
        double px = -az, pz = ax; // perpendicular, for widening
        for (double r = calderaRadius; r <= baseRadius; r += 0.5) {
            for (int w = 0; w <= (wide ? 1 : 0); w++) {
                int x = cx + (int) Math.round(ax * r + px * w);
                int z = cz + (int) Math.round(az * r + pz * w);
                int h = coneHeight(Math.sqrt((x - cx) * (x - cx) + (z - cz) * (z - cz)), height, baseRadius, profileExp);
                if (h < 0) continue;
                int yy = Math.max(baseY + h, groundAt(level, x, z)); // stay on the surface
                m.set(x, yy + 1, z);
                level.setBlock(m, air, 2);
                m.set(x, yy, z);
                level.setBlock(m, lava, 2);
            }
        }
    }

    /** Shield-volcano profile; returns the column height at radius {@code r}, or -1 outside. */
    private static int coneHeight(double r, int height, int baseRadius, double exp) {
        if (r < 0) r = 0;
        double t = r / baseRadius;
        if (t > 1.0) return -1;
        return (int) Math.round(height * Math.pow(1.0 - t, exp));
    }

    /** Solid-ground surface height (ignores the lava sea and other fluids). */
    private static int groundAt(WorldGenLevel level, int x, int z) {
        return level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z);
    }

    /** Cheap smooth value noise in roughly [-1, 1], for perturbing the cone's outline. */
    private static double bump(int x, int z, double seed) {
        return (Math.sin(x * 0.6 + seed) + Math.sin(z * 0.6 + seed * 1.3 + 1.7) + Math.sin((x + z) * 0.33 + 0.9)) / 3.0;
    }
}
