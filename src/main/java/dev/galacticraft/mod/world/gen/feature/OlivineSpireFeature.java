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
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Builds an olivine spire: a tapering column of {@code olivine_basalt} rearing off the surface,
 * shot through with raw olivine and tipped with solid olivine glass, evoking the green pyroclastic
 * mantle-glass provinces sampled by Apollo. Every spire is randomized in height, taper and lean so
 * the field never looks uniform.
 */
public class OlivineSpireFeature extends Feature<NoneFeatureConfiguration> {
    public OlivineSpireFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();

        int baseRadius = 2 + random.nextInt(3);                       // 2 - 4 footprint
        int height = 6 + random.nextInt(11);                          // 6 - 16 tall
        double profileExp = 1.5 + random.nextDouble() * 1.3;          // pointier -> stubbier
        double leanX = (random.nextDouble() - 0.5) * 0.8;             // slight lean, per-level drift
        double leanZ = (random.nextDouble() - 0.5) * 0.8;

        int cx = origin.getX();
        int cz = origin.getZ();
        int baseY = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, cx, cz);

        // Reject sites where the footprint straddles a big step, so spires never float off a ledge.
        int minG = baseY, maxG = baseY;
        for (int dx = -baseRadius; dx <= baseRadius; dx += baseRadius) {
            for (int dz = -baseRadius; dz <= baseRadius; dz += baseRadius) {
                int g = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, cx + dx, cz + dz);
                minG = Math.min(minG, g);
                maxG = Math.max(maxG, g);
            }
        }
        if (maxG - minG > 4) return false;
        baseY = minG;

        BlockState basalt = GCBlocks.OLIVINE_BASALT.defaultBlockState();
        BlockState olivine = GCBlocks.OLIVINE_BLOCK.defaultBlockState();
        BlockState glass = GCBlocks.OLIVINE_BLOCK.defaultBlockState();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();

        boolean placedAny = false;
        for (int dx = -baseRadius; dx <= baseRadius; dx++) {
            for (int dz = -baseRadius; dz <= baseRadius; dz++) {
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist > baseRadius + 0.5) continue;
                double t = dist / (baseRadius + 0.5);
                int colHeight = (int) Math.round(height * Math.pow(1.0 - t, profileExp));
                if (colHeight <= 0) continue;
                // Seat each column on its own local ground so the base hugs the terrain.
                int groundY = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, cx + dx, cz + dz);
                int fromY = Math.min(groundY, baseY) - 1;
                int topY = baseY + colHeight;
                for (int y = fromY; y <= topY; y++) {
                    // Lean: offset the whole column progressively with height.
                    double f = (y - baseY) / (double) Math.max(1, height);
                    int ox = (int) Math.round(leanX * f * height * 0.15);
                    int oz = (int) Math.round(leanZ * f * height * 0.15);
                    m.set(cx + dx + ox, y, cz + dz + oz);
                    BlockState s;
                    if (y >= topY - 1) {
                        s = glass;                                    // glassy green tip
                    } else if (random.nextInt(6) == 0) {
                        s = olivine;                                  // raw olivine inclusions
                    } else {
                        s = basalt;
                    }
                    level.setBlock(m, s, 2);
                    placedAny = true;
                }
            }
        }
        return placedAny;
    }
}
