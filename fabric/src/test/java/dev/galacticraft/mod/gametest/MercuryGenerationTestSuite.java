package dev.galacticraft.mod.gametest;

import com.mojang.serialization.JsonOps;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.content.GCBlocks;
import dev.galacticraft.mod.tag.GCBlockTags;
import dev.galacticraft.mod.world.gen.surfacerule.MercurySurfaceRules;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.SurfaceRules;

public class MercuryGenerationTestSuite implements GalacticraftGameTest {
    @GameTest(template = EMPTY_STRUCTURE)
    public void runtimeTerrainResourcesMatchMercuryMaterials(GameTestHelper helper) {
        var settings = helper.getLevel().registryAccess().registryOrThrow(Registries.NOISE_SETTINGS)
                .getOrThrow(Constant.key(Registries.NOISE_SETTINGS, "mercury"));
        var runtime = SurfaceRules.RuleSource.CODEC.encodeStart(JsonOps.INSTANCE, settings.surfaceRule()).getOrThrow();
        var expected = SurfaceRules.RuleSource.CODEC.encodeStart(JsonOps.INSTANCE, MercurySurfaceRules.MERCURY).getOrThrow();
        helper.assertTrue(runtime.equals(expected), "Mercury runtime surface rules differ from the data generator");
        for (Block block : new Block[]{GCBlocks.MERCURY_ROCKY_REGOLITH, GCBlocks.MERCURY_PLAINS_ROCK,
                GCBlocks.MERCURY_IMPACT_BRECCIA, GCBlocks.MERCURY_DARK_REGOLITH}) {
            var id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block);
            helper.assertTrue(runtime.toString().contains(id.toString()), "Mercury surface rules omit " + id);
            helper.assertTrue(block.defaultBlockState().is(GCBlockTags.MERCURY_CARVER_REPLACEABLES),
                    "Mercury caves cannot replace " + id);
        }
        for (Block block : new Block[]{GCBlocks.MERCURY_ROCKY_REGOLITH, GCBlocks.MERCURY_DARK_REGOLITH}) {
            helper.assertTrue(block.defaultBlockState().is(BlockTags.MINEABLE_WITH_SHOVEL), "Mercury soil lacks shovel tag");
            helper.assertTrue(block.defaultBlockState().is(GCBlockTags.FOOTPRINTS), "Mercury soil lacks footprints");
        }
        for (Block block : new Block[]{GCBlocks.MERCURY_PLAINS_ROCK, GCBlocks.MERCURY_IMPACT_BRECCIA}) {
            helper.assertTrue(block.defaultBlockState().is(BlockTags.MINEABLE_WITH_PICKAXE), "Mercury rock lacks pickaxe tag");
        }
        helper.succeed();
    }
}
