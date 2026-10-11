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
import dev.galacticraft.mod.content.GCEntityTypes;
import dev.galacticraft.mod.content.entity.LunarBurrowerEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.Difficulty;
import dev.galacticraft.mod.content.block.environment.LunarFormationBlock;
import dev.galacticraft.mod.tag.GCBlockTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;

/** Decorates an existing broad chamber; leaves a three-block-wide route through the den. */
public class LunarBurrowerDenFeature extends Feature<NoneFeatureConfiguration> {
    public LunarBurrowerDenFeature(Codec<NoneFeatureConfiguration> codec) { super(codec); }

    @Override public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        for (int attempt = 0; attempt < 8; attempt++) if (placeDen(context)) return true;
        return false;
    }

    private boolean placeDen(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        int x = (context.origin().getX() & ~15) + 5 + context.random().nextInt(6);
        int z = (context.origin().getZ() & ~15) + 5 + context.random().nextInt(6);
        BlockPos floor = LunarCavePlacement.floor(level, x, z, context.random());
        if (floor == null) return false;
        Direction forward = Direction.Plane.HORIZONTAL.getRandomDirection(context.random());
        Direction right = forward.getClockWise();
        int supported = 0;
        for (int side = -5; side <= 5; side++) for (int depth = -4; depth <= 5; depth++) {
            BlockPos pos = floor.relative(right, side).relative(forward, depth);
            var state = level.getBlockState(pos);
            if ((!state.isAir() && !state.is(GCBlockTags.MOON_CAVE_FORMATIONS))
                    || !level.isEmptyBlock(pos.above()) || !level.isEmptyBlock(pos.above(2))) return false;
            if ((Math.abs(side) <= 1 || Math.abs(side) >= 4) && (!floorSpace(level, pos)
                    || !LunarCavePlacement.ground(level, pos.below()))) return false;
            if (!LunarCavePlacement.inside(floor, pos)) return false;
            if (LunarCavePlacement.ground(level, pos.below())) supported++;
        }
        if (supported < 85) return false;
        int[][] sites = {{-3, 2}, {-3, 3}, {-3, 0}, {-3, -1}, {3, 1}, {3, -2}};
        Block[] blocks = {GCBlocks.BURROWER_EGG_CLUSTER, GCBlocks.BURROWER_BROKEN_EGGSHELL,
                GCBlocks.BURROWER_DEN_SCRAPS, GCBlocks.BURROWER_EXCAVATED_TAILINGS,
                GCBlocks.BURROWER_EGG_CLUSTER, GCBlocks.BURROWER_EXCAVATED_TAILINGS};
        // Require every piece to fit before placing any part of the den.
        for (int[] site : sites) {
            BlockPos pos = floor.relative(right, site[0]).relative(forward, site[1]);
            if (!floorSpace(level, pos) || !LunarCavePlacement.ground(level, pos.below())) return false;
        }
        BlockPos cacheSite = floor.relative(right, 3).relative(forward, 4);
        if (!floorSpace(level, cacheSite) || !LunarCavePlacement.ground(level, cacheSite.below())) return false;
        for (int side : new int[]{-2, 2}) for (int depth = -3; depth <= 3; depth++) {
            BlockPos bank = floor.relative(right, side).relative(forward, depth);
            if (!floorSpace(level, bank) || !LunarCavePlacement.ground(level, bank.below())) return false;
        }
        for (int side = -5; side <= 5; side++) for (int depth = -4; depth <= 5; depth++) {
            if (Math.abs(side) > 1 && Math.abs(side) < 4) continue;
            BlockPos pos = floor.relative(right, side).relative(forward, depth);
            if (!level.isEmptyBlock(pos)) level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        for (int i = 0; i < sites.length; i++) {
            BlockPos pos = floor.relative(right, sites[i][0]).relative(forward, sites[i][1]);
            level.setBlock(pos, blocks[i].defaultBlockState().setValue(
                    LunarFormationBlock.FACING, forward), Block.UPDATE_CLIENTS);
        }
        LunarCavePlacement.place(level, floor.relative(right, 3).relative(forward, 2),
                GCBlocks.HELION_NODULES, forward);
        // Low excavated banks screen the outer walking lanes from the adult's sight.
        for (int side : new int[]{-2, 2}) for (int depth = -3; depth <= 3; depth++) {
            BlockPos bank = floor.relative(right, side).relative(forward, depth);
            for (int height = 0; height < 2; height++) {
                BlockPos pos = bank.above(height);
                level.setBlock(pos, GCBlocks.COBBLED_MOON_ROCK.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
        if (!level.isEmptyBlock(cacheSite)) level.setBlock(cacheSite, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        var cache = LunarCaveLandmarkFeature.placeLootChest(level, context.random(), cacheSite, forward.getOpposite());
        if (cache != null) {
            ItemStack instrument = new ItemStack(GCBlocks.ABANDONED_SURVEY_BEACON);
            instrument.set(DataComponents.CUSTOM_NAME, Component.literal("Salvaged den survey instrument"));
            cache.setItem(26, instrument);
        }
        var routes = LunarCavePlacement.routes(level, floor.relative(forward, -4));
        BlockPos approach = routes.keySet().stream().max(java.util.Comparator.comparingDouble(pos -> pos.distSqr(floor))).orElse(floor);
        var trail = LunarCavePlacement.path(routes, approach);
        java.util.Collections.reverse(trail);
        LunarCavePlacement.markRoute(level, trail, GCBlocks.BURROWER_EXCAVATED_TAILINGS);
        for (int index = trail.size() / 2; index < trail.size(); index += 4) {
            BlockPos shell = trail.get(index).relative(right);
            if (!trail.contains(shell) && LunarCavePlacement.writable(level, shell)) {
                LunarCavePlacement.place(level, shell, GCBlocks.BURROWER_BROKEN_EGGSHELL, forward);
            }
        }
        if (level.getDifficulty() != Difficulty.PEACEFUL) {
            LunarBurrowerEntity worm = GCEntityTypes.LUNAR_BURROWER.create(level.getLevel());
            if (worm != null) {
                BlockPos head = floor.relative(forward, -3);
                worm.moveTo(head.getX() + 0.5, head.getY(), head.getZ() + 0.5, forward.getOpposite().toYRot(), 0);
                worm.finalizeSpawn(level, level.getCurrentDifficultyAt(head), MobSpawnType.STRUCTURE, null);
                worm.setYRot(forward.getOpposite().toYRot()); worm.yBodyRot = worm.getYRot(); worm.yHeadRot = worm.getYRot();
                worm.setDenPosition(head);
                level.addFreshEntity(worm);
            }
        }
        return true;
    }

    private static boolean floorSpace(WorldGenLevel level, BlockPos pos) {
        var state = level.getBlockState(pos);
        return state.isAir() || state.is(GCBlocks.LUNAR_SCATTERED_FRAGMENTS)
                || state.is(GCBlocks.LUNAR_BREAKDOWN_RUBBLE) || state.is(GCBlocks.LUNAR_BASALT_BREAKDOWN_RUBBLE)
                || state.is(GCBlocks.LUNAR_FROSTED_BREAKDOWN_RUBBLE) || state.is(GCBlocks.LUNAR_FALLEN_ROOF_SLAB);
    }
}
