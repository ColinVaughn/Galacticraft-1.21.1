/* Copyright (c) 2026 Team Galacticraft. Licensed under the MIT license. */
package dev.galacticraft.mod.world.gen.feature;

import dev.galacticraft.mod.content.GCBlocks;
import dev.galacticraft.mod.tag.GCBlockTags;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** GC4's buried acid pool and open shaft, placed from the modern Venus surface heightmap. */
public class VaporSpoutFeature extends Feature<NoneFeatureConfiguration> {
    public VaporSpoutFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        var level = context.level();
        BlockPos spout = context.origin();
        if (!level.getBlockState(spout).canBeReplaced()
                || !level.getBlockState(spout.below()).is(GCBlockTags.VENUS_CARVER_REPLACEABLES)) return false;

        int radius = 5 + context.random().nextInt(4);
        // Acid lies 11–18 blocks below a generator above the spout, within GC4's scan range.
        BlockPos center = spout.below(10 + context.random().nextInt(8));
        if (center.getY() - radius < level.getMinBuildHeight()) return false;

        int radiusSquared = radius * radius;
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    int distance = x * x + y * y + z * z;
                    if (distance > radiusSquared) continue;
                    var state = distance >= radiusSquared - 16 ? GCBlocks.SOFT_VENUS_ROCK.defaultBlockState()
                            : y <= 0 ? GCBlocks.SULFURIC_ACID.defaultBlockState() : Blocks.AIR.defaultBlockState();
                    setBlock(level, center.offset(x, y, z), state);
                }
            }
        }
        for (int y = center.getY() + 1; y < spout.getY(); y++) {
            setBlock(level, new BlockPos(spout.getX(), y, spout.getZ()), Blocks.AIR.defaultBlockState());
        }
        setBlock(level, spout, GCBlocks.VAPOR_SPOUT.defaultBlockState());
        return true;
    }
}
