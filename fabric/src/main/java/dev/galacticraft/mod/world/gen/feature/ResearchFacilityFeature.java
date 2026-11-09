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

import dev.galacticraft.mod.content.GCLootTables;
import dev.galacticraft.mod.content.block.jupiter.JupiterBlocks;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Suspended facility with an intact laboratory and breached storage wing. Fits within 15 blocks
 * of the origin to stay inside writable feature chunks and requires open atmosphere.
 */
public class ResearchFacilityFeature extends Feature<NoneFeatureConfiguration> {
    public static final int HALF_LENGTH = 14, HALF_WIDTH = 4;

    public ResearchFacilityFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        if (!JupiterWreckFeature.clear(context.level(), context.origin(), 13, -7, 17)) return false;
        RandomSource random = context.random();
        var f = new JupiterWreckFeature.Frame(context.level(), context.origin(), random.nextBoolean() ? Direction.EAST : Direction.SOUTH);
        BlockState reinforced = JupiterBlocks.REINFORCED_AEROSTAT_HULL.defaultBlockState();
        BlockState hull = JupiterBlocks.AEROSTAT_HULL.defaultBlockState();
        BlockState glass = JupiterBlocks.PRESSURE_GLASS.defaultBlockState();
        BlockState grating = JupiterBlocks.AEROSTAT_GRATING.defaultBlockState();
        BlockState chain = Blocks.CHAIN.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();

        // Hub: two floors under one roof, windows on both floors.
        room(f, -4, 4, 4, 0, 10, reinforced, hull, glass, random, 0.0F);
        for (int u = -3; u <= 3; u++) for (int v = -3; v <= 3; v++) f.set(u, 5, v, grating);
        for (int i = 0; i < 4; i++) {
            f.set(-3 + i, 1 + i, 0, JupiterBlocks.REINFORCED_AEROSTAT_HULL_STAIRS.defaultBlockState().setValue(StairBlock.FACING, f.turn(Direction.EAST)));
            f.set(-3 + i, 5, 0, air);
        }
        f.set(1, 5, 0, JupiterBlocks.SEALED_HATCH.defaultBlockState());

        for (int side : new int[]{-1, 1}) {
            for (int step = 5; step <= 7; step++) {
                int u = side * step;
                for (int v = -2; v <= 2; v++) {
                    f.set(u, 0, v, Math.abs(v) == 2 ? hull : grating);
                    f.set(u, 4, v, hull);
                    for (int y = 1; y <= 3; y++) f.set(u, y, v, Math.abs(v) == 2 ? (y == 2 ? glass : hull) : air);
                }
            }
            for (int v = -1; v <= 1; v++) for (int y = 1; y <= 3; y++) {
                f.set(side * 4, y, v, air);
                f.set(side * 8, y, v, air);
            }
        }

        // Laboratory wing (intact) and storage wing (breached: the pressure hazard).
        room(f, 8, HALF_LENGTH, 4, 0, 6, reinforced, hull, glass, random, 0.0F);
        room(f, -HALF_LENGTH, -8, 4, 0, 6, hull, hull, glass, random, 0.35F);
        for (int v = -1; v <= 1; v++) for (int y = 1; y <= 3; y++) {
            f.set(8, y, v, air);
            f.set(-8, y, v, air);
        }

        BlockState lantern = Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
        f.set(0, 9, 0, lantern);
        f.set(0, 4, 2, lantern);
        f.set(11, 5, 0, lantern);
        f.set(-11, 5, 0, lantern);

        f.chest(-3, 1, 3, Direction.WEST, GCLootTables.JUPITER_RESEARCH_FACILITY, random);
        f.chest(3, 6, -3, Direction.WEST, GCLootTables.JUPITER_RESEARCH_FACILITY, random);
        f.chest(12, 1, 3, Direction.WEST, GCLootTables.JUPITER_RESEARCH_FACILITY, random);
        f.chest(-12, 1, -3, Direction.EAST, GCLootTables.JUPITER_RESEARCH_FACILITY, random);

        // Buoyancy: a large envelope above the hub and a smaller one above each wing, all on chains.
        JupiterWreckFeature.envelope(f, 0, 14, 0, 5.6, 2.6, 3.6, 0.0F, random);
        for (int u : new int[]{-3, 3}) for (int v : new int[]{-2, 2}) for (int y = 11; y <= 12; y++) f.set(u, y, v, chain);
        for (int side : new int[]{-1, 1}) {
            JupiterWreckFeature.envelope(f, side * 11, 9, 0, 3.4, 1.6, 2.4, 0.0F, random);
            for (int y = 7; y <= 7; y++) {
                f.set(side * 9, y, 0, chain);
                f.set(side * 13, y, 0, chain);
            }
        }
        for (int[] post : new int[][]{{0, 1, 2}, {11, 1, 0}, {-11, 1, 0}}) {
            var drone = dev.galacticraft.mod.content.GCEntityTypes.SALVAGE_DRONE.create(context.level().getLevel());
            if (drone == null) continue;
            var at = f.at(post[0], post[1], post[2]);
            drone.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, random.nextFloat() * 360, 0);
            drone.guard(at);
            context.level().addFreshEntity(drone);
        }
        for (int u : new int[]{-3, 3}) for (int v : new int[]{-3, 3}) {
            int length = 3 + random.nextInt(3);
            for (int y = -1; y >= -length; y--) f.set(u, y, v, chain);
            f.set(u, -length - 1, v, reinforced);
        }
        return true;
    }

    /** A box room from {@code u0} to {@code u1}, {@code halfWidth} either side, glazed along the long walls. */
    private static void room(JupiterWreckFeature.Frame f, int u0, int u1, int halfWidth, int floor, int roof, BlockState shell,
                             BlockState walls, BlockState glass, RandomSource random, float breach) {
        for (int u = u0; u <= u1; u++) {
            for (int v = -halfWidth; v <= halfWidth; v++) {
                boolean edge = u == u0 || u == u1 || Math.abs(v) == halfWidth;
                boolean corner = (u == u0 || u == u1) && Math.abs(v) == halfWidth;
                if (random.nextFloat() >= breach * 0.4F) f.set(u, floor, v, edge ? shell : walls);
                f.set(u, roof, v, edge ? shell : walls);
                for (int y = floor + 1; y < roof; y++) {
                    if (!edge) {
                        f.set(u, y, v, Blocks.AIR.defaultBlockState());
                        continue;
                    }
                    if (random.nextFloat() < breach) continue;
                    boolean window = Math.abs(v) == halfWidth && (y - floor) % 5 >= 2 && (y - floor) % 5 <= 3 && Math.abs(u - (u0 + u1) / 2) <= 2;
                    f.set(u, y, v, window ? glass : corner ? shell : walls);
                }
            }
        }
    }
}
