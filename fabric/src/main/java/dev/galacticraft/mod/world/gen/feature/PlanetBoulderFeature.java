package dev.galacticraft.mod.world.gen.feature;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.BlockStateConfiguration;

/** Small, partly buried rocks seated on a planet's terrain, including polar ice. */
public class PlanetBoulderFeature extends Feature<BlockStateConfiguration> {
    private final TagKey<Block> ground;

    public PlanetBoulderFeature(TagKey<Block> ground) {
        super(BlockStateConfiguration.CODEC);
        this.ground = ground;
    }

    private boolean isGround(BlockState state) {
        return state.is(this.ground)
                || state.is(Blocks.PACKED_ICE) || state.is(Blocks.BLUE_ICE);
    }

    @Override
    public boolean place(FeaturePlaceContext<BlockStateConfiguration> context) {
        var level = context.level();
        BlockPos center = context.origin().below();
        int radiusX = 1 + context.random().nextInt(2);
        int radiusZ = 1 + context.random().nextInt(2);
        int height = 1 + context.random().nextInt(2);

        // Reject cliff edges and unsuitable ground before writing any blocks.
        for (int x = -radiusX; x <= radiusX; x++) {
            for (int z = -radiusZ; z <= radiusZ; z++) {
                if (!isGround(level.getBlockState(center.offset(x, 0, z)))) return false;
            }
        }

        for (int x = -radiusX; x <= radiusX; x++) {
            for (int z = -radiusZ; z <= radiusZ; z++) {
                for (int y = 0; y <= height; y++) {
                    double distance = (double) (x * x) / (radiusX * radiusX)
                            + (double) (z * z) / (radiusZ * radiusZ)
                            + (double) (y * y) / (height * height);
                    if (distance > 1.0) continue;
                    BlockPos pos = center.offset(x, y, z);
                    BlockState existing = level.getBlockState(pos);
                    if (existing.isAir() || isGround(existing)) {
                        setBlock(level, pos, context.config().state);
                    }
                }
            }
        }
        return true;
    }
}
