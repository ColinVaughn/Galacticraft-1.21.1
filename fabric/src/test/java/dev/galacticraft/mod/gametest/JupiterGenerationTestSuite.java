package dev.galacticraft.mod.gametest;

import com.mojang.serialization.JsonOps;
import dev.galacticraft.dynamicdimensions.api.DynamicDimensionRegistry;
import dev.galacticraft.mod.content.GCBlocks;
import dev.galacticraft.mod.world.dimension.GCDimensionTypes;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import dev.galacticraft.mod.world.gen.GCNoiseGeneratorSettings;
import dev.galacticraft.mod.world.gen.JupiterWorldgen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import dev.galacticraft.mod.content.GCLootTables;
import dev.galacticraft.mod.content.block.jupiter.JupiterBlocks;
import dev.galacticraft.mod.content.block.jupiter.encounter.StormRelayBlockEntity;
import dev.galacticraft.mod.content.block.jupiter.encounter.StormheartPedestalBlockEntity;
import dev.galacticraft.mod.world.gen.feature.GCFeatures;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.storage.loot.LootTable;
import org.slf4j.LoggerFactory;

import java.util.List;

public final class JupiterGenerationTestSuite implements GalacticraftGameTest {
    @GameTest(template = EMPTY_STRUCTURE)
    public void seededTerrainHasSettlementBanksAndDescentChannels(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        var settings = registries.registryOrThrow(Registries.NOISE_SETTINGS)
                .getOrThrow(GCNoiseGeneratorSettings.JUPITER);
        helper.assertTrue(settings.defaultFluid().isAir(), "Jupiter atmosphere must not fill with liquid");
        helper.assertTrue(!settings.isAquifersEnabled() && !settings.oreVeinsEnabled(), "Jupiter must not generate aquifers or ore veins");
        long firstFingerprint = 0;
        for (long seed : new long[]{23L, 8675309L, -140153L}) {
            var random = RandomState.create(settings, registries.lookupOrThrow(Registries.NOISE), seed);
            var repeat = RandomState.create(settings, registries.lookupOrThrow(Registries.NOISE), seed);
            var density = random.router().finalDensity();
            int banks = 0, samples = 0;
            long fingerprint = 1;
            for (int x = -1024; x <= 1024; x += 64) {
                for (int z = -1024; z <= 1024; z += 64) {
                    var point = new DensityFunction.SinglePointContext(x, JupiterWorldgen.CLOUD_CENTER, z);
                    double value = density.compute(point);
                    helper.assertTrue(value == repeat.router().finalDensity().compute(point), "Same seed generated different banks");
                    if (value > 0.0) banks++;
                    samples++;
                    fingerprint = fingerprint * 31 + Double.doubleToLongBits(value);
                    // Formations fill the expedition layers; the Crushing Depths and upper sky stay open.
                    for (int y : new int[]{-64, -48, JupiterWorldgen.CRUSHING_TOP, 256, 319}) {
                        helper.assertTrue(density.compute(new DensityFunction.SinglePointContext(x, y, z)) < 0.0,
                                "Terrain escaped its layers at " + x + "," + y + "," + z);
                    }
                }
            }
            double coverage = (double) banks / samples;
            helper.assertTrue(coverage > 0.15 && coverage < 0.90, "Missing banks or descent channels: " + coverage);
            if (seed == 23L) firstFingerprint = fingerprint;
            else helper.assertTrue(fingerprint != firstFingerprint, "Cloud terrain ignores world seed");
            LoggerFactory.getLogger("JupiterGenerationTest").info("Seed {} cloud-center coverage: {}", seed, coverage);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
    public void generatedChunksMatchColumnsAndKeepCrushingDepthsEmpty(GameTestHelper helper) {
        // GameTest boots only its flat Overworld; generate a dedicated world from Jupiter's runtime resources.
        var server = helper.getLevel().getServer();
        var registries = server.registryAccess();
        var type = registries.registryOrThrow(Registries.DIMENSION_TYPE).getOrThrow(GCDimensionTypes.JUPITER);
        var testType = DimensionType.DIRECT_CODEC.parse(JsonOps.INSTANCE,
                DimensionType.DIRECT_CODEC.encodeStart(JsonOps.INSTANCE, type).getOrThrow()).getOrThrow();
        var settings = registries.registryOrThrow(Registries.NOISE_SETTINGS).getHolderOrThrow(GCNoiseGeneratorSettings.JUPITER);
        var biome = registries.registryOrThrow(Registries.BIOME).getHolderOrThrow(JupiterWorldgen.CLOUDLANDS);
        var level = DynamicDimensionRegistry.from(server).createDynamicDimension(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("galacticraft-test", "jupiter_generation"),
                new NoiseBasedChunkGenerator(new FixedBiomeSource(biome), settings), testType);
        helper.assertTrue(level != null, "Jupiter test dimension did not load");
        var generator = level.getChunkSource().getGenerator();
        var random = level.getChunkSource().randomState();
        var height = LevelHeightAccessor.create(JupiterWorldgen.MIN_Y, JupiterWorldgen.HEIGHT);
        BlockPos bank = null;
        for (int x = -256; x <= 256 && bank == null; x += 32) {
            for (int z = -256; z <= 256; z += 32) {
                int top = generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, height, random);
                if (top >= 216 && top <= 248) {
                    bank = new BlockPos(x, top, z);
                    break;
                }
            }
        }
        helper.assertTrue(bank != null, "No usable bank found near the origin");
        // Sample both sides of a chunk boundary after real chunk generation and surface placement.
        int boundaryX = (bank.getX() >> 4) * 16;
        for (int x = boundaryX - 1; x <= boundaryX + 1; x++) {
            int z = bank.getZ();
            var column = generator.getBaseColumn(x, z, height, random);
            var chunk = level.getChunkAt(new BlockPos(x, 222, z));
            for (int y = JupiterWorldgen.MIN_Y; y < JupiterWorldgen.MIN_Y + JupiterWorldgen.HEIGHT; y++) {
                var actual = chunk.getBlockState(new BlockPos(x, y, z));
                boolean terrain = actual.is(GCBlocks.DENSE_CLOUD) || actual.is(GCBlocks.AMMONIA_FROST);
                boolean solid = !column.getBlock(y).isAir();
                // Cloud terrain must match the noise column. Features only replace it with their own blocks, or
                // with air where a Deep Tempest citadel clears its arena.
                if (terrain) helper.assertTrue(solid, "Chunk/column seam at " + x + "," + y + "," + z);
                else if (solid) helper.assertTrue(isFeatureBlock(actual) || (actual.isAir() && y < 64),
                        "Chunk/column seam at " + x + "," + y + "," + z);
                helper.assertTrue(actual.isAir() || terrain || isFeatureBlock(actual), "Unexpected terrain material: " + actual);
                if (y <= JupiterWorldgen.CRUSHING_TOP) helper.assertTrue(actual.isAir(), "The Crushing Depths contain a floor");
            }
        }
        helper.assertTrue(level.getBlockState(bank.below()).isCollisionShapeFullBlock(level, bank.below()), "Cloud bank cannot support a player");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void terrainBlocksHaveToolsLootAndStableCollision(GameTestHelper helper) {
        var level = helper.getLevel();
        for (var block : new net.minecraft.world.level.block.Block[]{GCBlocks.DENSE_CLOUD, GCBlocks.PACKED_CLOUD, GCBlocks.AMMONIA_FROST}) {
            var state = block.defaultBlockState();
            helper.assertTrue(state.is(BlockTags.MINEABLE_WITH_SHOVEL), "Cloud material missing shovel tag: " + block);
            helper.assertTrue(state.isCollisionShapeFullBlock(level, BlockPos.ZERO), "Cloud material lacks stable collision");
            helper.assertTrue(!state.isRandomlyTicking(), "Cloud terrain must not decay or melt");
            helper.assertTrue(level.getServer().reloadableRegistries().getLootTable(block.getLootTable())
                    != net.minecraft.world.level.storage.loot.LootTable.EMPTY, "Cloud material missing loot table");
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void wrecksNeedClearanceAndCarryTheirLoot(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos survey = helper.absolutePos(BlockPos.ZERO).atY(150);
        BlockPos harvester = survey.offset(32, 0, 0);
        try {
            helper.assertTrue(place(level, GCFeatures.JUPITER_SURVEY_WRECK, survey), "Survey wreck refused open atmosphere");
            helper.assertTrue(place(level, GCFeatures.JUPITER_STORM_HARVESTER, harvester), "Storm harvester refused open atmosphere");
            // Control: the same site is no longer clear, so nothing may be built into it.
            helper.assertTrue(!place(level, GCFeatures.JUPITER_SURVEY_WRECK, survey), "Survey wreck was built into existing blocks");
            helper.assertTrue(count(level, survey, 8, JupiterBlocks.AEROSTAT_HULL) > 20, "Survey wreck has no hull");
            helper.assertTrue(count(level, harvester, 8, JupiterBlocks.CHARGED_CONDENSATE_CLUSTER) > 0, "Storm harvester has no charged condensate");
            helper.assertTrue(count(level, harvester, 8, JupiterBlocks.LIGHTNING_GROUNDER) == 1, "Storm harvester has no electrical machinery");
            helper.assertTrue(GCLootTables.JUPITER_SURVEY_WRECK.equals(chest(level, survey, 8)), "Survey wreck chest has the wrong loot");
            helper.assertTrue(GCLootTables.JUPITER_STORM_HARVESTER.equals(chest(level, harvester, 8)), "Storm harvester chest has the wrong loot");
            for (ResourceKey<LootTable> table : List.of(GCLootTables.JUPITER_SURVEY_WRECK, GCLootTables.JUPITER_STORM_HARVESTER,
                    GCLootTables.JUPITER_STORMHEART_CITADEL)) {
                helper.assertTrue(level.getServer().reloadableRegistries().getLootTable(table) != LootTable.EMPTY,
                        "Missing Jupiter loot table " + table.location());
            }
        } finally {
            clear(level, survey, 8);
            clear(level, harvester, 8);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void citadelHoldsACompleteStormheartEncounter(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO).atY(200);
        try {
            helper.assertTrue(place(level, GCFeatures.STORMHEART_CITADEL, origin), "Citadel was not built");
            helper.assertTrue(level.getBlockState(origin.above()).is(JupiterBlocks.STORMHEART_PEDESTAL), "Citadel has no pedestal");
            int relays = 0;
            for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-15, 0, -15), origin.offset(15, 4, 15))) {
                if (level.getBlockEntity(pos) instanceof StormRelayBlockEntity
                        && pos.closerThan(origin.above(), StormheartPedestalBlockEntity.LINK_RANGE)) relays++;
            }
            helper.assertTrue(relays == 3, "Citadel should link three relays, found " + relays);
            helper.assertTrue(count(level, origin, 15, JupiterBlocks.LIGHTNING_GROUNDER) == 3, "Citadel lacks grounded shelters");
            helper.assertTrue(count(level, origin, 15, JupiterBlocks.VEHICLE_DOCK) == 1, "Citadel has no vehicle dock for the approach");
            helper.assertTrue(GCLootTables.JUPITER_STORMHEART_CITADEL.equals(chest(level, origin, 15)), "Citadel chest has the wrong loot");
        } finally {
            clear(level, origin, 16);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void clustersOnlyGrowOnExposedCloudFaces(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos slab = helper.absolutePos(BlockPos.ZERO).atY(180);
        BlockPos open = slab.offset(24, 0, 0);
        try {
            for (BlockPos pos : BlockPos.betweenClosed(slab.offset(-6, 0, -6), slab.offset(6, 1, 6))) {
                level.setBlock(pos, GCBlocks.DENSE_CLOUD.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
            var random = RandomSource.create(42L);
            var generator = level.getChunkSource().getGenerator();
            int grown = 0;
            for (int i = 0; i < 12; i++) {
                if (GCFeatures.JUPITER_HYDROSULFIDE_CRYSTALS.place(NoneFeatureConfiguration.INSTANCE, level, generator, random, slab)) grown++;
                // Control: a column of open atmosphere offers nothing to grow on.
                helper.assertTrue(!GCFeatures.JUPITER_HYDROSULFIDE_CRYSTALS.place(NoneFeatureConfiguration.INSTANCE, level, generator, random, open),
                        "Clusters grew in open atmosphere");
            }
            helper.assertTrue(grown > 0, "No clusters grew on the cloud slab");
            int hanging = 0, upright = 0;
            for (BlockPos pos : BlockPos.betweenClosed(slab.offset(-8, -5, -8), slab.offset(8, 6, 8))) {
                BlockState state = level.getBlockState(pos);
                if (!state.is(JupiterBlocks.HYDROSULFIDE_CRYSTAL_CLUSTER)) continue;
                boolean hangs = state.getValue(JupiterBlocks.Cluster.HANGING);
                helper.assertTrue(level.getBlockState(hangs ? pos.above() : pos.below()).is(GCBlocks.DENSE_CLOUD), "Unsupported cluster at " + pos);
                if (hangs) hanging++;
                else upright++;
            }
            helper.assertTrue(hanging > 0 && upright > 0, "Clusters should grow on tops and undersides: " + upright + " up, " + hanging + " hanging");
        } finally {
            clear(level, slab, 8);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void weatherStationsStandOnBanksWithTheirSurveyGear(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos ground = helper.absolutePos(BlockPos.ZERO).atY(225);
        BlockPos station = ground.above();
        BlockPos overVoid = station.offset(40, 0, 0);
        try {
            for (BlockPos pos : BlockPos.betweenClosed(ground.offset(-9, -1, -9), ground.offset(9, 0, 9))) {
                level.setBlock(pos, GCBlocks.DENSE_CLOUD.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
            // Control: with no bank under its stilts the station must not be built.
            helper.assertTrue(!place(level, GCFeatures.JUPITER_WEATHER_STATION, overVoid), "Weather station was built over a descent channel");
            helper.assertTrue(place(level, GCFeatures.JUPITER_WEATHER_STATION, station), "Weather station refused a bank top");
            helper.assertTrue(count(level, station, 8, JupiterBlocks.SURVEY_BEACON) == 1, "Weather station has no survey beacon");
            helper.assertTrue(count(level, station, 8, JupiterBlocks.AEROSTAT_HULL_STAIRS) > 0, "Weather station has no steps");
            helper.assertTrue(count(level, station, 8, JupiterBlocks.AEROSTAT_HULL_SLAB) > 10, "Weather station has no eaves");
            helper.assertTrue(count(level, station.above(10), 8, JupiterBlocks.BUOYANCY_CELL) == 1, "Weather station lost its sounding balloon");
            helper.assertTrue(GCLootTables.JUPITER_WEATHER_STATION.equals(chest(level, station, 8)), "Weather station chest has the wrong loot");
        } finally {
            clear(level, station, 10);
            clear(level, station.above(10), 10);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void researchFacilitiesHangFromBuoyancyCellsWithFourCaches(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO).atY(60);
        try {
            helper.assertTrue(place(level, GCFeatures.JUPITER_RESEARCH_FACILITY, origin), "Research facility refused open atmosphere");
            // Control: the site is now occupied.
            helper.assertTrue(!place(level, GCFeatures.JUPITER_RESEARCH_FACILITY, origin), "Research facility was built into existing blocks");
            int caches = 0;
            for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-15, -8, -15), origin.offset(15, 16, 15))) {
                if (level.getBlockEntity(pos) instanceof ChestBlockEntity chest && GCLootTables.JUPITER_RESEARCH_FACILITY.equals(chest.getLootTable())) caches++;
            }
            helper.assertTrue(caches == 4, "Research facility should hold four caches, found " + caches);
            int cells = 0;
            for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-15, 6, -15), origin.offset(15, 16, 15))) {
                if (level.getBlockState(pos).is(JupiterBlocks.BUOYANCY_CELL)) cells++;
            }
            helper.assertTrue(cells > 40, "Research facility has no visible buoyancy, cells: " + cells);
            var drones = level.getEntitiesOfClass(dev.galacticraft.mod.content.entity.jupiter.SalvageDroneEntity.class, new net.minecraft.world.phys.AABB(origin).inflate(16));
            helper.assertTrue(drones.size() == 3 && drones.stream().allMatch(drone -> drone.post() != null && drone.isPersistenceRequired()),
                    "The facility should post three persistent Salvage Drones, found " + drones.size());
        } finally {
            level.getEntitiesOfClass(dev.galacticraft.mod.content.entity.jupiter.SalvageDroneEntity.class, new net.minecraft.world.phys.AABB(origin).inflate(16)).forEach(drone -> drone.discard());
            for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-15, -8, -15), origin.offset(15, 16, 15))) {
                if (!level.getBlockState(pos).isAir()) level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
            }
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void constructionVariantsAndBuoyancyCellsBehave(GameTestHelper helper) {
        var level = helper.getLevel();
        for (var entry : JupiterBlocks.VARIANT_BASES.entrySet()) {
            var variant = entry.getKey();
            helper.assertTrue(level.getServer().reloadableRegistries().getLootTable(variant.getLootTable()) != LootTable.EMPTY,
                    "Missing loot for " + variant);
            boolean slab = variant instanceof net.minecraft.world.level.block.SlabBlock;
            helper.assertTrue(variant.defaultBlockState().is(slab ? net.minecraft.tags.BlockTags.SLABS : net.minecraft.tags.BlockTags.STAIRS),
                    "Missing stairs/slab tag for " + variant);
            var tool = entry.getValue() == GCBlocks.PACKED_CLOUD ? net.minecraft.tags.BlockTags.MINEABLE_WITH_SHOVEL : net.minecraft.tags.BlockTags.MINEABLE_WITH_PICKAXE;
            helper.assertTrue(variant.defaultBlockState().is(tool), "Variant mined with the wrong tool: " + variant);
        }
        // A buoyancy cell softens a fall; the control lands the same fall on stone.
        var cushioned = helper.spawn(net.minecraft.world.entity.EntityType.WOLF, new BlockPos(1, 2, 1));
        var control = helper.spawn(net.minecraft.world.entity.EntityType.WOLF, new BlockPos(2, 2, 2));
        float before = cushioned.getHealth();
        JupiterBlocks.BUOYANCY_CELL.fallOn(level, JupiterBlocks.BUOYANCY_CELL.defaultBlockState(), cushioned.blockPosition(), cushioned, 9.0F);
        Blocks.STONE.fallOn(level, Blocks.STONE.defaultBlockState(), control.blockPosition(), control, 9.0F);
        float soft = before - cushioned.getHealth(), hard = before - control.getHealth();
        helper.assertTrue(hard >= 5.0F, "Control fall did no damage: " + hard);
        helper.assertTrue(soft <= 2.0F, "Buoyancy cell did not soften the fall: " + soft);
        helper.succeed();
    }

    private static boolean isFeatureBlock(BlockState state) {
        return JupiterBlocks.NAMES.containsKey(state.getBlock()) || state.is(Blocks.CHEST) || state.is(Blocks.IRON_BARS) || state.is(Blocks.LIGHTNING_ROD);
    }

    private static boolean place(ServerLevel level, Feature<NoneFeatureConfiguration> feature, BlockPos origin) {
        return feature.place(NoneFeatureConfiguration.INSTANCE, level, level.getChunkSource().getGenerator(), RandomSource.create(origin.asLong()), origin);
    }

    private static int count(ServerLevel level, BlockPos origin, int radius, Block block) {
        int found = 0;
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-radius, -8, -radius), origin.offset(radius, 10, radius))) {
            if (level.getBlockState(pos).is(block)) found++;
        }
        return found;
    }

    private static ResourceKey<LootTable> chest(ServerLevel level, BlockPos origin, int radius) {
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-radius, -8, -radius), origin.offset(radius, 10, radius))) {
            if (level.getBlockEntity(pos) instanceof ChestBlockEntity chest) return chest.getLootTable();
        }
        return null;
    }

    private static void clear(ServerLevel level, BlockPos origin, int radius) {
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-radius, -9, -radius), origin.offset(radius, 10, radius))) {
            if (!level.getBlockState(pos).isAir()) level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
        }
    }
}