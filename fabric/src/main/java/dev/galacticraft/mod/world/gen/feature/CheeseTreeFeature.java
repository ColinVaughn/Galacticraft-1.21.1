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

/** Places a small moon-cheese tree without vanilla soil checks. */
public class CheeseTreeFeature extends Feature<NoneFeatureConfiguration> {
    public CheeseTreeFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();

        int cx = origin.getX();
        int cz = origin.getZ();
        int groundY = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, cx, cz);

        int trunkHeight = 4 + random.nextInt(3);          // 4 - 6
        int canopyRadius = 2 + random.nextInt(2);         // 2 - 3

        BlockState log = GCBlocks.MOON_CHEESE_LOG.defaultBlockState();
        BlockState leaves = GCBlocks.MOON_CHEESE_LEAVES.defaultBlockState();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();

        // Trunk.
        for (int i = 0; i < trunkHeight; i++) {
            m.set(cx, groundY + i, cz);
            level.setBlock(m, log, 2);
        }

        // Rounded canopy blob centred a little below the trunk top.
        int canopyBase = groundY + trunkHeight - 1;
        for (int dy = -1; dy <= canopyRadius; dy++) {
            double layerRadius = canopyRadius - Math.max(0, dy) * 0.6;
            int r = (int) Math.round(layerRadius);
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx * dx + dz * dz > (layerRadius + 0.4) * (layerRadius + 0.4)) continue;
                    // Skip the trunk column below the top so leaves don't overwrite the log.
                    if (dx == 0 && dz == 0 && dy < canopyRadius) continue;
                    m.set(cx + dx, canopyBase + dy, cz + dz);
                    if (level.getBlockState(m).isAir()) {
                        level.setBlock(m, leaves, 2);
                    }
                }
            }
        }
        return true;
    }
}
