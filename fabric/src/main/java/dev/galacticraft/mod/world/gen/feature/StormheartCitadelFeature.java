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
import dev.galacticraft.mod.content.block.jupiter.encounter.StormRelayBlock;
import dev.galacticraft.mod.content.block.jupiter.encounter.StormheartPedestalBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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

/**
 * Suspended Stormheart arena with relays, grounder shelters, and a dock. Fits within a 15-block
 * radius to stay inside writable feature chunks.
 */
public class StormheartCitadelFeature extends Feature<NoneFeatureConfiguration> {
    public static final int RADIUS = 13, RELAY_DISTANCE = 9, GROUNDER_DISTANCE = 6;

    public StormheartCitadelFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();
        BlockState reinforced = JupiterBlocks.REINFORCED_AEROSTAT_HULL.defaultBlockState();
        BlockState hull = JupiterBlocks.AEROSTAT_HULL.defaultBlockState();
        BlockState grating = JupiterBlocks.AEROSTAT_GRATING.defaultBlockState();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int x = -RADIUS - 1; x <= RADIUS + 1; x++) {
            for (int z = -RADIUS - 1; z <= RADIUS + 1; z++) {
                double r = Math.sqrt(x * x + z * z);
                if (r > RADIUS + 0.5) continue;
                // The arena is always open, even where a formation drifts through it.
                for (int y = 1; y <= 9; y++) {
                    if (!level.isEmptyBlock(pos.setWithOffset(origin, x, y, z))) level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
                BlockState deck = r > RADIUS - 1.5 ? grating : (Math.abs(r - 4.5) < 0.5 || Math.abs(r - 8.5) < 0.5 || r < 1.5) ? reinforced : hull;
                level.setBlock(pos.setWithOffset(origin, x, 0, z), deck, Block.UPDATE_CLIENTS);
                if (r <= RADIUS - 2) level.setBlock(pos.setWithOffset(origin, x, -1, z), hull, Block.UPDATE_CLIENTS);
            }
        }

        for (int sx : new int[]{-8, 8}) {
            for (int sz : new int[]{-8, 8}) {
                for (int x = -3; x <= 3; x++) {
                    for (int y = -3; y <= 3; y++) {
                        for (int z = -3; z <= 3; z++) {
                            int r2 = x * x + y * y + z * z;
                            // Buoyancy cells strapped with a hull band around the equator.
                            if (r2 <= 10) level.setBlock(pos.setWithOffset(origin, sx + x, -5 + y, sz + z),
                                    y == 0 && r2 >= 7 ? hull : JupiterBlocks.BUOYANCY_CELL.defaultBlockState(), Block.UPDATE_CLIENTS);
                        }
                    }
                }
            }
        }

        level.setBlock(origin.above(), JupiterBlocks.STORMHEART_PEDESTAL.defaultBlockState()
                .setValue(StormheartPedestalBlock.FACING, Direction.SOUTH), Block.UPDATE_CLIENTS);
        double spin = random.nextDouble() * Math.PI * 2;
        for (int i = 0; i < 3; i++) {
            double relayAngle = spin + i * Math.PI * 2 / 3;
            int rx = (int) Math.round(Math.cos(relayAngle) * RELAY_DISTANCE), rz = (int) Math.round(Math.sin(relayAngle) * RELAY_DISTANCE);
            level.setBlock(pos.setWithOffset(origin, rx, 1, rz), reinforced, Block.UPDATE_CLIENTS);
            level.setBlock(pos.setWithOffset(origin, rx, 2, rz), JupiterBlocks.STORM_RELAY.defaultBlockState()
                    .setValue(StormRelayBlock.FACING, Direction.getNearest(-rx, 0, -rz)), Block.UPDATE_CLIENTS);
            double shelterAngle = relayAngle + Math.PI / 3;
            int gx = (int) Math.round(Math.cos(shelterAngle) * GROUNDER_DISTANCE), gz = (int) Math.round(Math.sin(shelterAngle) * GROUNDER_DISTANCE);
            level.setBlock(pos.setWithOffset(origin, gx, 1, gz), JupiterBlocks.LIGHTNING_GROUNDER.defaultBlockState(), Block.UPDATE_CLIENTS);
        }

        // Keep the dock within service range of divers parked on the approach pier.
        for (int z = RADIUS; z <= RADIUS + 2; z++) {
            for (int x = -1; x <= 1; x++) level.setBlock(origin.offset(x, 0, z), grating, Block.UPDATE_CLIENTS);
        }
        level.setBlock(origin.offset(0, 1, RADIUS - 2), JupiterBlocks.VEHICLE_DOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
        for (int x : new int[]{-2, 2}) {
            level.setBlock(origin.offset(x, 1, RADIUS), JupiterBlocks.REINFORCED_AEROSTAT_HULL.defaultBlockState(), Block.UPDATE_CLIENTS);
            level.setBlock(origin.offset(x, 2, RADIUS), Blocks.LANTERN.defaultBlockState(), Block.UPDATE_CLIENTS);
        }

        BlockPos chest = origin.offset(0, 1, -3);
        level.setBlock(chest, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH), Block.UPDATE_CLIENTS);
        if (level.getBlockEntity(chest) instanceof ChestBlockEntity entity) entity.setLootTable(GCLootTables.JUPITER_STORMHEART_CITADEL, random.nextLong());
        return true;
    }
}
