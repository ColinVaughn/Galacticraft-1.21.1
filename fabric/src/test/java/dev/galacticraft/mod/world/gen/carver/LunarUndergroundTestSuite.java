package dev.galacticraft.mod.world.gen.carver;

import com.mojang.serialization.JsonOps;
import dev.galacticraft.mod.content.GCBlocks;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.dynamicdimensions.api.DynamicDimensionRegistry;
import dev.galacticraft.mod.gametest.GalacticraftGameTest;
import dev.galacticraft.mod.world.biome.GCBiomes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.carver.CarvingContext;
import net.minecraft.world.level.levelgen.carver.CaveCarverConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class LunarUndergroundTestSuite implements GalacticraftGameTest {
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
    public void freshMoonChunksGenerateConnectedCavesAndDiscoveries(GameTestHelper helper) throws java.io.IOException {
        var server = helper.getLevel().getServer();
        var registry = server.registryAccess();
        var settings = registry.registryOrThrow(Registries.NOISE_SETTINGS)
                .getHolderOrThrow(Constant.key(Registries.NOISE_SETTINGS, "moon"));
        var type = registry.registryOrThrow(Registries.DIMENSION_TYPE)
                .getOrThrow(Constant.key(Registries.DIMENSION_TYPE, "moon"));
        try (var writer = java.nio.file.Files.newBufferedWriter(java.nio.file.Path.of("..", "moon-generated-caves.csv"))) {
            writer.write("kind,x,y,z\n");
            for (var biomeKey : List.of(GCBiomes.Moon.BASALTIC_MARE, GCBiomes.Moon.OLIVINE_SPIKES)) {
                var biome = registry.registryOrThrow(Registries.BIOME).getHolderOrThrow(biomeKey);
                String name = biomeKey.location().getPath();
                // DynamicDimensions requires its own type instance rather than an already registered object.
                var testType = DimensionType.DIRECT_CODEC.parse(JsonOps.INSTANCE,
                        DimensionType.DIRECT_CODEC.encodeStart(JsonOps.INSTANCE, type).getOrThrow()).getOrThrow();
                var moon = DynamicDimensionRegistry.from(server).createDynamicDimension(
                        ResourceLocation.fromNamespaceAndPath("galacticraft-test", "moon_exploration_" + name),
                        new NoiseBasedChunkGenerator(new FixedBiomeSource(biome), settings), testType);
                helper.assertTrue(moon != null, "Dedicated Moon test dimension could not be created");
                int air = 0, discoveries = 0;
                int bottom = Math.max(moon.getMinBuildHeight() + 4, settings.value().noiseSettings().minY() + 4);
                for (int cx = -1; cx < 5; cx++) for (int cz = -1; cz < 5; cz++) {
                    var chunk = moon.getChunk(cx, cz);
                    for (int x = chunk.getPos().getMinBlockX(); x <= chunk.getPos().getMaxBlockX(); x++) {
                        for (int z = chunk.getPos().getMinBlockZ(); z <= chunk.getPos().getMaxBlockZ(); z++) {
                            int ceiling = chunk.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, x, z) - 8;
                            for (int y = bottom; y <= Math.min(72, ceiling); y++) {
                                var state = chunk.getBlockState(new BlockPos(x, y, z));
                                if (state.isAir()) { air++; writer.write(name + "," + x + "," + y + "," + z + "\n"); }
                                if (state.is(GCBlocks.FALLEN_METEOR) || state.is(GCBlocks.DENSE_ICE)
                                        || state.is(GCBlocks.ICE_BEARING_BRECCIA) || state.is(GCBlocks.HELION_NODULES)
                                        || state.is(GCBlocks.LUNAR_SAPPHIRE_ORE) || state.is(GCBlocks.BUDDING_OLIVINE)) discoveries++;
                            }
                        }
                    }
                }
                helper.assertTrue(air > 100 && discoveries > 0, "Fresh " + name + " terrain lacks caves or their rewards: air=" + air + ", discoveries=" + discoveries);
            }
        }
        helper.succeed();
    }

    private static ProtoChunk chunk(GameTestHelper helper, int x, int z) {
        var level = helper.getLevel();
        return new ProtoChunk(new ChunkPos(x, z), UpgradeData.EMPTY, level,
                level.registryAccess().registryOrThrow(Registries.BIOME), null);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void impactGeometryDoesNotDependOnTheDecoratedChunk(GameTestHelper helper) {
        var registry = helper.getLevel().registryAccess();
        var config = (CaveCarverConfiguration) registry.registryOrThrow(Registries.CONFIGURED_CARVER)
                .getOrThrow(GCConfiguredCarvers.MOON_HIGHLANDS_CAVE_CARVER).config();
        Holder<Biome> biome = registry.registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.PLAINS);
        for (int seed = 0; seed < 32; seed++) {
            RecordingCarver first = new RecordingCarver(), second = new RecordingCarver();
            first.carve(null, config, chunk(helper, 0, 0), ignored -> biome, RandomSource.create(seed), null,
                    new ChunkPos(0, 0), new CarvingMask(384, -64));
            second.carve(null, config, chunk(helper, 1, 0), ignored -> biome, RandomSource.create(seed), null,
                    new ChunkPos(0, 0), new CarvingMask(384, -64));
            helper.assertTrue(first.geometry.equals(second.geometry), "Chunk-local decoration changed fractures for seed " + seed);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void coldTrapRewardsCoverEveryIntersectingChunkAndIgnoreGenerationOrder(GameTestHelper helper) {
        var room = new LunarChamber(0, 12, 0, 13, 9, 0, LunarChamber.Kind.COLD_TRAP, 42);
        for (int cx = -1; cx <= 0; cx++) for (int cz = -1; cz <= 0; cz++) {
            ProtoChunk first = chamberChunk(helper, cx, cz, room), second = chamberChunk(helper, cx, cz, room);
            room.decorate(first);
            new LunarChamber(200, 12, 200, 13, 9, 0, LunarChamber.Kind.COLD_TRAP, 17).decorate(first);
            room.decorate(second);
            int rewards = 0;
            for (int x = first.getPos().getMinBlockX(); x <= first.getPos().getMaxBlockX(); x++) {
                for (int z = first.getPos().getMinBlockZ(); z <= first.getPos().getMaxBlockZ(); z++) {
                    for (int y = 0; y <= 24; y++) {
                        BlockPos pos = new BlockPos(x, y, z);
                        var state = first.getBlockState(pos);
                        helper.assertTrue(state.equals(second.getBlockState(pos)), "Decoration depends on chunk order at " + pos);
                        if (state.is(GCBlocks.DENSE_ICE) || state.is(GCBlocks.ICE_BEARING_BRECCIA)) rewards++;
                    }
                }
            }
            helper.assertTrue(rewards > 5, "Cold-trap deposit was clipped at chunk " + first.getPos());
        }
        helper.succeed();
    }

    private static ProtoChunk chamberChunk(GameTestHelper helper, int cx, int cz, LunarChamber room) {
        ProtoChunk chunk = chunk(helper, cx, cz);
        for (int x = chunk.getPos().getMinBlockX(); x <= chunk.getPos().getMaxBlockX(); x++) {
            for (int z = chunk.getPos().getMinBlockZ(); z <= chunk.getPos().getMaxBlockZ(); z++) {
                for (int y = 0; y <= 24; y++) {
                    boolean solid = room.skip((x + 0.5 - room.x()) / room.radius(),
                            (y + 0.5 - room.y()) / room.height(), (z + 0.5 - room.z()) / room.radius());
                    chunk.setBlockState(new BlockPos(x, y, z), solid ? GCBlocks.MOON_ROCK.defaultBlockState()
                            : Blocks.AIR.defaultBlockState(), false);
                }
            }
        }
        return chunk;
    }

    private static class RecordingCarver extends LunarImpactCavernCarver {
        private final List<List<Double>> geometry = new ArrayList<>();
        RecordingCarver() { super(CaveCarverConfiguration.CODEC); }
        @Override protected boolean carveEllipsoid(CarvingContext context, CaveCarverConfiguration config,
                ChunkAccess chunk, Function<BlockPos, Holder<Biome>> biomes, Aquifer aquifer, double x,
                double y, double z, double radius, double height, CarvingMask mask, CarveSkipChecker checker) {
            geometry.add(List.of(x, y, z, radius, height, checker.shouldSkip(context, 0.1, -0.4, 0.5, 0) ? 1.0 : 0.0));
            return false;
        }
    }
}
