/*
 * Copyright (c) 2026 Colin Vaughn
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

import dev.galacticraft.mod.content.GCBlocks;
import dev.galacticraft.mod.content.block.jupiter.JupiterBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

import java.util.ArrayList;
import java.util.List;

/**
 * Grows a patch of Jupiter resource clusters on an exposed cloud face between {@code minY} and {@code maxY}:
 * upright on formation tops, hanging from bank and formation undersides. Columns without any cloud are skipped,
 * so the open atmosphere stays empty.
 */
public class JupiterClusterFeature extends Feature<NoneFeatureConfiguration> {
    private final Block cluster;
    private final int minY, maxY;
    private final float hangingChance;

    public JupiterClusterFeature(Block cluster, int minY, int maxY, float hangingChance) {
        super(NoneFeatureConfiguration.CODEC);
        this.cluster = cluster;
        this.minY = minY;
        this.maxY = maxY;
        this.hangingChance = hangingChance;
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();
        boolean hanging = random.nextFloat() < this.hangingChance;
        List<BlockPos> faces = faces(level, origin.getX(), origin.getZ(), hanging);
        if (faces.isEmpty()) return false;
        BlockPos seed = faces.get(random.nextInt(faces.size()));

        int placed = 0;
        int count = 2 + random.nextInt(5);
        for (int i = 0; i < count * 3 && placed < count; i++) {
            int x = seed.getX() + random.nextInt(7) - 3, z = seed.getZ() + random.nextInt(7) - 3;
            BlockPos spot = nearestFace(level, x, seed.getY(), z, hanging);
            if (spot != null) {
                level.setBlock(spot, this.cluster.defaultBlockState().setValue(JupiterBlocks.Cluster.HANGING, hanging), 2);
                placed++;
            }
        }
        return placed > 0;
    }

    /** Every air cell in the column that sits on (or hangs under) Dense Cloud. */
    private List<BlockPos> faces(WorldGenLevel level, int x, int z, boolean hanging) {
        List<BlockPos> faces = new ArrayList<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, this.maxY, z);
        Direction support = hanging ? Direction.UP : Direction.DOWN;
        for (int y = this.maxY; y >= this.minY; y--) {
            pos.setY(y);
            if (level.isEmptyBlock(pos) && level.getBlockState(pos.relative(support)).is(GCBlocks.DENSE_CLOUD)) faces.add(pos.immutable());
        }
        return faces;
    }

    private static BlockPos nearestFace(WorldGenLevel level, int x, int y, int z, boolean hanging) {
        Direction support = hanging ? Direction.UP : Direction.DOWN;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dy = 0; dy <= 3; dy++) {
            for (int sign : new int[]{1, -1}) {
                pos.set(x, y + dy * sign, z);
                if (level.isEmptyBlock(pos) && level.getBlockState(pos.relative(support)).is(GCBlocks.DENSE_CLOUD)) return pos.immutable();
            }
        }
        return null;
    }
}
