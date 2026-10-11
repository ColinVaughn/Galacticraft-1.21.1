package dev.galacticraft.mod.gametest;

import com.mojang.serialization.JsonOps;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.content.GCBlocks;
import dev.galacticraft.mod.tag.GCBlockTags;
import dev.galacticraft.mod.world.gen.feature.GCFeatures;
import dev.galacticraft.mod.world.gen.surfacerule.MoonSurfaceRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.minecraft.world.level.levelgen.feature.configurations.BlockStateConfiguration;

public class MoonGenerationTestSuite implements GalacticraftGameTest {
    @GameTest(template = EMPTY_STRUCTURE)
    public void bouldersUseLunarGroundAndRejectCliffs(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(3, 3, 3));
        var config = new BlockStateConfiguration(GCBlocks.MOON_IMPACT_BRECCIA.defaultBlockState());
        for (Block ground : new Block[]{GCBlocks.MOON_TURF, GCBlocks.MOON_SURFACE_ROCK,
                GCBlocks.MOON_BASALT, GCBlocks.OLIVINE_BASALT, GCBlocks.MOON_ROCKY_REGOLITH, GCBlocks.MOON_MARE_REGOLITH,
                GCBlocks.MOON_ANORTHOSITE, GCBlocks.MOON_IMPACT_BRECCIA, GCBlocks.DENSE_ICE, Blocks.PACKED_ICE}) {
            for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-2, -1, -2), origin.offset(2, 2, 2))) {
                level.setBlock(pos, pos.getY() < origin.getY() ? ground.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
            }
            helper.assertTrue(GCFeatures.MOON_BOULDER.place(config, level,
                    level.getChunkSource().getGenerator(), RandomSource.create(42), origin),
                    "Boulder rejected lunar ground: " + ground);
            helper.assertTrue(level.getBlockState(origin).is(GCBlocks.MOON_IMPACT_BRECCIA),
                    "Boulder must appear above ground");
        }
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-2, -1, -2), origin.offset(2, 2, 2))) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
        }
        level.setBlock(origin.below(), GCBlocks.MOON_TURF.defaultBlockState(), 2);
        helper.assertTrue(!GCFeatures.MOON_BOULDER.place(config, level,
                level.getChunkSource().getGenerator(), RandomSource.create(42), origin),
                "Boulder must reject an unsupported footprint");
        helper.assertTrue(level.getBlockState(origin).isAir(), "Rejected boulder changed the world");
        for (Block block : new Block[]{GCBlocks.MOON_ROCKY_REGOLITH, GCBlocks.MOON_MARE_REGOLITH}) {
            helper.assertTrue(block.defaultBlockState().is(GCBlockTags.FOOTPRINTS), "Lunar soil lost footprints");
            helper.assertTrue(block.defaultBlockState().is(GCBlockTags.TERRAFORMABLE), "Lunar soil cannot be terraformed");
        }
        for (Block block : new Block[]{GCBlocks.MOON_ROCKY_REGOLITH, GCBlocks.MOON_MARE_REGOLITH,
                GCBlocks.MOON_ANORTHOSITE, GCBlocks.MOON_IMPACT_BRECCIA}) {
            helper.assertTrue(block.defaultBlockState().is(GCBlockTags.MOON_CARVER_REPLACEABLES),
                    "New surface block obstructs lunar caves: " + block);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void runtimeSurfaceRulesMatchJavaRules(GameTestHelper helper) {
        var settings = helper.getLevel().registryAccess().registryOrThrow(Registries.NOISE_SETTINGS)
                .getOrThrow(Constant.key(Registries.NOISE_SETTINGS, "moon"));
        var runtime = SurfaceRules.RuleSource.CODEC.encodeStart(JsonOps.INSTANCE, settings.surfaceRule()).getOrThrow();
        var javaRules = SurfaceRules.RuleSource.CODEC.encodeStart(JsonOps.INSTANCE, MoonSurfaceRules.MOON).getOrThrow();
        helper.assertTrue(runtime.equals(javaRules), "Moon JSON and Java surface rules have diverged");
        helper.succeed();
    }
}
