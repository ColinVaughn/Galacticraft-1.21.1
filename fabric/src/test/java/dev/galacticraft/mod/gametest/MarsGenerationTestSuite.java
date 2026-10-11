package dev.galacticraft.mod.gametest;

import dev.galacticraft.mod.content.GCBlocks;
import dev.galacticraft.mod.tag.GCBlockTags;
import dev.galacticraft.mod.world.gen.feature.GCFeatures;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.configurations.BlockStateConfiguration;

public class MarsGenerationTestSuite implements GalacticraftGameTest {
    @GameTest(template = EMPTY_STRUCTURE)
    public void bouldersUseMarsGroundAndRejectCliffs(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(3, 3, 3));
        var config = new BlockStateConfiguration(GCBlocks.MARS_COBBLESTONE.defaultBlockState());
        for (Block ground : new Block[]{GCBlocks.MARS_SURFACE_ROCK, GCBlocks.MARS_ROCKY_REGOLITH,
                GCBlocks.MARS_BASALTIC_SAND, GCBlocks.MARS_WEATHERED_BASALT,
                GCBlocks.MARS_SEDIMENTARY_ROCK, Blocks.PACKED_ICE, Blocks.BLUE_ICE}) {
            for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-2, -1, -2), origin.offset(2, 2, 2))) {
                level.setBlock(pos, pos.getY() < origin.getY() ? ground.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
            }
            helper.assertTrue(GCFeatures.MARS_BOULDER.place(config, level,
                    level.getChunkSource().getGenerator(), RandomSource.create(42), origin),
                    "Boulder rejected Mars ground: " + ground);
            helper.assertTrue(level.getBlockState(origin).is(GCBlocks.MARS_COBBLESTONE),
                    "Boulder was buried instead of appearing above ground");
        }
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-2, -1, -2), origin.offset(2, 2, 2))) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
        }
        level.setBlock(origin.below(), GCBlocks.MARS_SURFACE_ROCK.defaultBlockState(), 2);
        helper.assertTrue(!GCFeatures.MARS_BOULDER.place(config, level,
                level.getChunkSource().getGenerator(), RandomSource.create(42), origin),
                "Boulder must reject an unsupported footprint");
        helper.assertTrue(level.getBlockState(origin).isAir(), "Rejected boulder changed the world");
        for (Block block : new Block[]{GCBlocks.MARS_ROCKY_REGOLITH, GCBlocks.MARS_BASALTIC_SAND,
                GCBlocks.MARS_WEATHERED_BASALT, GCBlocks.MARS_SEDIMENTARY_ROCK}) {
            helper.assertTrue(block.defaultBlockState().is(GCBlockTags.MARS_CARVER_REPLACEABLES),
                    "New surface block would obstruct Mars caves: " + block);
        }
        helper.succeed();
    }
}
