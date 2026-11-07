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
import dev.galacticraft.mod.content.GCLootTables;
import dev.galacticraft.mod.content.block.jupiter.JupiterBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Weather station above a settlement bank, with supplies, a beacon, and a sounding balloon.
 * Requires cloud within reach of all four stilts to avoid overhanging descent channels.
 */
public class WeatherStationFeature extends Feature<NoneFeatureConfiguration> {
    /** Banks sit near Y 222; anything outside this range is not a settlement bank top. */
    public static final int MIN_GROUND = 208, MAX_GROUND = 250;

    public WeatherStationFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();
        if (origin.getY() < MIN_GROUND || origin.getY() > MAX_GROUND) return false;
        var f = new JupiterWreckFeature.Frame(level, origin, random.nextBoolean() ? Direction.EAST : Direction.SOUTH);
        int deck = 3;
        int[][] stilts = {{-3, -3}, {-3, 3}, {3, -3}, {3, 3}, {7, 0}};
        int[] ground = new int[stilts.length];
        for (int i = 0; i < stilts.length; i++) {
            ground[i] = groundBelow(f, stilts[i][0], stilts[i][1], deck - 1);
            if (ground[i] == Integer.MIN_VALUE) return false;
        }

        BlockState hull = JupiterBlocks.AEROSTAT_HULL.defaultBlockState();
        BlockState grating = JupiterBlocks.AEROSTAT_GRATING.defaultBlockState();
        BlockState glass = JupiterBlocks.PRESSURE_GLASS.defaultBlockState();
        for (int i = 0; i < 4; i++) {
            for (int y = ground[i] + 1; y < deck; y++) f.set(stilts[i][0], y, stilts[i][1], hull);
        }
        for (int u = -4; u <= 4; u++) {
            for (int v = -4; v <= 4; v++) {
                if (random.nextFloat() > 0.08F) f.set(u, deck, v, grating);
                for (int y = deck + 1; y <= deck + 6; y++) f.set(u, y, v, Blocks.AIR.defaultBlockState());
            }
        }
        for (int u = -2; u <= 2; u++) {
            for (int v = -2; v <= 2; v++) {
                boolean wall = Math.abs(u) == 2 || Math.abs(v) == 2;
                for (int y = deck + 1; y <= deck + 3; y++) {
                    boolean door = u == 2 && v == 0 && y <= deck + 2;
                    if (!wall || door || random.nextFloat() < 0.08F) continue;
                    boolean window = y == deck + 2 && (Math.abs(u) == 2 ? Math.abs(v) <= 1 : Math.abs(u) <= 1);
                    f.set(u, y, v, window ? glass : hull);
                }
                f.set(u, deck + 4, v, hull);
            }
        }
        BlockState eave = JupiterBlocks.AEROSTAT_HULL_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
        for (int u = -3; u <= 3; u++) {
            for (int v = -3; v <= 3; v++) {
                if (Math.abs(u) == 3 || Math.abs(v) == 3) f.set(u, deck + 4, v, eave);
            }
        }
        BlockState step = JupiterBlocks.AEROSTAT_HULL_STAIRS.defaultBlockState().setValue(StairBlock.FACING, f.turn(Direction.WEST));
        for (int i = 1; i <= deck; i++) {
            int u = 4 + i, y = deck - i + 1;
            if (y <= ground[4]) break;
            f.set(u, y, 0, step);
            for (int below = y - 1; below > ground[4]; below--) f.set(u, below, 0, hull);
        }

        f.set(-1, deck + 1, 1, JupiterBlocks.SURVEY_BEACON.defaultBlockState());
        f.chest(-1, deck + 1, -1, Direction.EAST, GCLootTables.JUPITER_WEATHER_STATION, random);
        for (int y = deck + 5; y <= deck + 9; y++) f.set(0, y, 0, Blocks.IRON_BARS.defaultBlockState());
        f.set(0, deck + 10, 0, Blocks.LIGHTNING_ROD.defaultBlockState());
        for (int y = deck + 5; y <= deck + 11; y++) f.set(2, y, 2, Blocks.CHAIN.defaultBlockState());
        f.set(2, deck + 12, 2, JupiterBlocks.BUOYANCY_CELL.defaultBlockState());
        return true;
    }

    /** The highest cloud below the deck at (u, v), within six blocks of the origin; {@code MIN_VALUE} when there is none. */
    private static int groundBelow(JupiterWreckFeature.Frame f, int u, int v, int from) {
        for (int y = from; y >= -6; y--) {
            BlockState state = f.level().getBlockState(f.at(u, y, v));
            if (state.is(GCBlocks.DENSE_CLOUD) || state.is(GCBlocks.AMMONIA_FROST) || state.is(GCBlocks.PACKED_CLOUD)) return y;
        }
        return Integer.MIN_VALUE;
    }
}
