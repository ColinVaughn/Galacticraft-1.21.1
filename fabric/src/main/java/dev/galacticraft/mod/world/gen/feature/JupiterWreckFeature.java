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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Suspended aerostat wrecks and storm harvesters. Requires open atmosphere with enough clearance
 * for a parked Cloud Diver.
 */
public class JupiterWreckFeature extends Feature<NoneFeatureConfiguration> {
    public enum Kind { SURVEY_WRECK, STORM_HARVESTER }

    private final Kind kind;

    public JupiterWreckFeature(Kind kind) {
        super(NoneFeatureConfiguration.CODEC);
        this.kind = kind;
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();
        if (!clear(level, origin, 7, -6, 9)) return false;
        Frame frame = new Frame(level, origin, random.nextBoolean() ? Direction.EAST : Direction.SOUTH);
        if (this.kind == Kind.SURVEY_WRECK) surveyWreck(frame, random);
        else stormHarvester(frame, random);
        return true;
    }

    /** A gondola with a torn-open stern under three buoyancy ribs. */
    private static void surveyWreck(Frame f, RandomSource random) {
        BlockState hull = JupiterBlocks.AEROSTAT_HULL.defaultBlockState();
        BlockState grating = JupiterBlocks.AEROSTAT_GRATING.defaultBlockState();
        BlockState glass = JupiterBlocks.PRESSURE_GLASS.defaultBlockState();
        for (int u = -4; u <= 4; u++) {
            for (int v = -2; v <= 2; v++) {
                f.set(u, 0, v, grating);
                if (Math.abs(v) <= 1 && Math.abs(u) <= 3) f.set(u, -1, v, hull);
                boolean wall = Math.abs(v) == 2 || u == -4;
                for (int y = 1; y <= 2; y++) {
                    if (wall && random.nextFloat() > 0.2F) f.set(u, y, v, y == 2 && Math.abs(v) == 2 && (u & 1) != 0 ? glass : hull);
                }
                if (u < 3 && Math.abs(v) <= 1 && random.nextFloat() > 0.25F) f.set(u, 3, v, hull);
            }
        }
        // A torn gas envelope still holds the gondola up on four chains.
        envelope(f, 0, 7, 0, 5.2, 1.8, 2.6, 0.12F, random);
        for (int u : new int[]{-3, 3}) for (int v : new int[]{-1, 1}) for (int y = 4; y <= 5; y++) f.set(u, y, v, Blocks.CHAIN.defaultBlockState());
        f.chest(-3, 1, 1, Direction.SOUTH, GCLootTables.JUPITER_SURVEY_WRECK, random);
        f.set(0, 0, 0, JupiterBlocks.SEALED_HATCH.defaultBlockState());
    }

    /** A grated collection ring on a reinforced core, a lightning mast, and two buoyancy tanks below. */
    private static void stormHarvester(Frame f, RandomSource random) {
        BlockState hull = JupiterBlocks.AEROSTAT_HULL.defaultBlockState();
        BlockState core = JupiterBlocks.REINFORCED_AEROSTAT_HULL.defaultBlockState();
        BlockState grating = JupiterBlocks.AEROSTAT_GRATING.defaultBlockState();
        BlockState charged = JupiterBlocks.CHARGED_CONDENSATE_CLUSTER.defaultBlockState();
        for (int u = -5; u <= 5; u++) {
            for (int v = -5; v <= 5; v++) {
                double r = Math.sqrt(u * u + v * v);
                if (r > 5.5) continue;
                if (r > 4.5) {
                    f.set(u, 0, v, hull);
                    if (random.nextFloat() < 0.18F) f.set(u, 1, v, charged);
                } else if (random.nextFloat() > 0.15F) {
                    f.set(u, 0, v, grating);
                }
            }
        }
        for (int y = -3; y <= 2; y++) {
            for (int u = -1; u <= 1; u++) for (int v = -1; v <= 1; v++) f.set(u, y, v, core);
        }
        // The rig's electrical machinery: a grounder still wired under the lightning mast.
        f.set(0, 3, 0, JupiterBlocks.LIGHTNING_GROUNDER.defaultBlockState());
        for (int y = 4; y <= 6; y++) f.set(0, y, 0, Blocks.IRON_BARS.defaultBlockState());
        f.set(0, 7, 0, Blocks.LIGHTNING_ROD.defaultBlockState());
        for (int side : new int[]{-1, 1}) {
            for (int u = -2; u <= 2; u++) {
                for (int v = -1; v <= 1; v++) {
                    for (int y = -5; y <= -3; y++) {
                        if (Math.abs(u) == 2 && Math.abs(v) == 1) continue;
                        f.set(u, y, side * 4 + v, JupiterBlocks.BUOYANCY_CELL.defaultBlockState());
                    }
                }
            }
            for (int u = -2; u <= 2; u++) {
                if (random.nextFloat() < 0.4F) f.set(u, -6, side * 4, charged.setValue(JupiterBlocks.Cluster.HANGING, true));
            }
            f.set(0, -2, side * 2, hull);
            f.set(0, -2, side * 3, hull);
        }
        f.chest(2, 1, 0, Direction.WEST, GCLootTables.JUPITER_STORM_HARVESTER, random);
    }

    /**
     * A gas envelope: an ellipsoid of Buoyancy Cells centred at (u, y, v) with a hull strap around its equator.
     * {@code tear} is the share of outer cells missing, so wrecks look deflated while facilities stay whole.
     */
    static void envelope(Frame f, double cu, double cy, double cv, double ru, double ry, double rv, float tear, RandomSource random) {
        BlockState cell = JupiterBlocks.BUOYANCY_CELL.defaultBlockState();
        BlockState strap = JupiterBlocks.AEROSTAT_HULL.defaultBlockState();
        for (int u = (int) Math.floor(cu - ru); u <= (int) Math.ceil(cu + ru); u++) {
            for (int v = (int) Math.floor(cv - rv); v <= (int) Math.ceil(cv + rv); v++) {
                for (int y = (int) Math.floor(cy - ry); y <= (int) Math.ceil(cy + ry); y++) {
                    double du = (u - cu) / ru, dy = (y - cy) / ry, dv = (v - cv) / rv;
                    double d = du * du + dy * dy + dv * dv;
                    if (d > 1.0) continue;
                    boolean outer = d > 0.45;
                    if (outer && random.nextFloat() < tear) continue;
                    boolean equator = Math.abs(y - cy) < 0.5 && du * du + dv * dv > 0.55;
                    f.set(u, y, v, equator ? strap : cell);
                }
            }
        }
    }

    /** True when the box around {@code origin} is open atmosphere. */
    static boolean clear(WorldGenLevel level, BlockPos origin, int radius, int down, int up) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = -radius; x <= radius; x += 2) {
            for (int z = -radius; z <= radius; z += 2) {
                for (int y = down; y <= up; y += 2) {
                    if (!level.isEmptyBlock(pos.setWithOffset(origin, x, y, z))) return false;
                }
            }
        }
        return true;
    }

    /** Places blocks in a frame rotated so {@code u} runs along {@code forward}. */
    record Frame(WorldGenLevel level, BlockPos origin, Direction forward) {
        BlockPos at(int u, int y, int v) {
            Direction side = this.forward.getClockWise();
            return this.origin.offset(this.forward.getStepX() * u + side.getStepX() * v, y, this.forward.getStepZ() * u + side.getStepZ() * v);
        }

        /** Rotates a horizontal direction authored for an east-facing frame into this frame. */
        Direction turn(Direction local) {
            Direction result = local;
            for (Direction d = Direction.EAST; d != this.forward; d = d.getClockWise()) result = result.getClockWise();
            return result;
        }

        void set(int u, int y, int v, BlockState state) {
            this.level.setBlock(at(u, y, v), state, Block.UPDATE_CLIENTS);
        }

        void chest(int u, int y, int v, Direction facing, ResourceKey<LootTable> loot, RandomSource random) {
            BlockPos pos = at(u, y, v);
            this.level.setBlock(pos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, turn(facing)), Block.UPDATE_CLIENTS);
            if (this.level.getBlockEntity(pos) instanceof ChestBlockEntity chest) chest.setLootTable(loot, random.nextLong());
        }
    }
}
