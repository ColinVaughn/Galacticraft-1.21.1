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

import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.tag.GCBlockTags;
import dev.galacticraft.mod.world.gen.WorldgenPlatformHooks;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.data.worldgen.placement.PlacementUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.placement.BiomeFilter;
import net.minecraft.world.level.levelgen.placement.BlockPredicateFilter;
import net.minecraft.world.level.levelgen.placement.CountPlacement;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.placement.EnvironmentScanPlacement;
import net.minecraft.world.level.levelgen.placement.HeightRangePlacement;
import net.minecraft.world.level.levelgen.placement.HeightmapPlacement;
import net.minecraft.world.level.levelgen.placement.InSquarePlacement;
import net.minecraft.world.level.levelgen.placement.SurfaceRelativeThresholdFilter;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.RandomOffsetPlacement;
import net.minecraft.world.level.levelgen.placement.RarityFilter;

import java.util.List;

public class GCPlacedFeatures {
    public static final ResourceKey<PlacedFeature> OIL_LAKE = ResourceKey.create(Registries.PLACED_FEATURE, Constant.id("oil_lake"));
    public static final ResourceKey<PlacedFeature> SULFURIC_ACID_LAKE = ResourceKey.create(Registries.PLACED_FEATURE, Constant.id("sulfuric_acid_lake"));
    public static final ResourceKey<PlacedFeature> VENUS_VAPOR_SPOUT = ResourceKey.create(Registries.PLACED_FEATURE, Constant.id("venus_vapor_spout"));

    public static final ResourceKey<PlacedFeature> MARS_BOULDER = ResourceKey.create(Registries.PLACED_FEATURE, Constant.id("mars_boulder"));
    public static final ResourceKey<PlacedFeature> MARS_BOULDER_SPARSE = ResourceKey.create(Registries.PLACED_FEATURE, Constant.id("mars_boulder_sparse"));
    public static final ResourceKey<PlacedFeature> MARS_HEMATITE_DEPOSIT = ResourceKey.create(Registries.PLACED_FEATURE, Constant.id("mars_hematite_deposit"));
    public static final ResourceKey<PlacedFeature> MARS_ICE_SPIKE = ResourceKey.create(Registries.PLACED_FEATURE, Constant.id("mars_ice_spike"));
    public static final ResourceKey<PlacedFeature> MARS_ICE_BOULDER = ResourceKey.create(Registries.PLACED_FEATURE, Constant.id("mars_ice_boulder"));
    public static final ResourceKey<PlacedFeature> MARS_FROZEN_BRINE = ResourceKey.create(Registries.PLACED_FEATURE, Constant.id("mars_frozen_brine"));
    public static final ResourceKey<PlacedFeature> MARS_FROZEN_LAKE = ResourceKey.create(Registries.PLACED_FEATURE, Constant.id("mars_frozen_lake"));

    public static final ResourceKey<PlacedFeature> VENUS_VOLCANIC_BOULDER = ResourceKey.create(Registries.PLACED_FEATURE, Constant.id("venus_volcanic_boulder"));
    public static final ResourceKey<PlacedFeature> VENUS_VOLCANIC_BOULDER_SPARSE = ResourceKey.create(Registries.PLACED_FEATURE, Constant.id("venus_volcanic_boulder_sparse"));
    public static final ResourceKey<PlacedFeature> VENUS_PUMICE_BOULDER = ResourceKey.create(Registries.PLACED_FEATURE, Constant.id("venus_pumice_boulder"));
    public static final ResourceKey<PlacedFeature> VENUS_VOLCANO = ResourceKey.create(Registries.PLACED_FEATURE, Constant.id("venus_volcano"));

    public static final ResourceKey<PlacedFeature> MERCURY_LAVA_LAKE = ResourceKey.create(Registries.PLACED_FEATURE, Constant.id("mercury_lava_lake"));
    public static final ResourceKey<PlacedFeature> MERCURY_CRYSTAL_SCATTER = ResourceKey.create(Registries.PLACED_FEATURE, Constant.id("mercury_crystal_scatter"));

    public static final ResourceKey<PlacedFeature> JUPITER_HYDROSULFIDE_CRYSTALS = ResourceKey.create(Registries.PLACED_FEATURE, Constant.id("jupiter_hydrosulfide_crystals"));
    public static final ResourceKey<PlacedFeature> JUPITER_CHARGED_CONDENSATE = ResourceKey.create(Registries.PLACED_FEATURE, Constant.id("jupiter_charged_condensate"));
    public static final ResourceKey<PlacedFeature> JUPITER_SURVEY_WRECK = ResourceKey.create(Registries.PLACED_FEATURE, Constant.id("jupiter_survey_wreck"));
    public static final ResourceKey<PlacedFeature> JUPITER_STORM_HARVESTER = ResourceKey.create(Registries.PLACED_FEATURE, Constant.id("jupiter_storm_harvester"));
    public static final ResourceKey<PlacedFeature> STORMHEART_CITADEL = ResourceKey.create(Registries.PLACED_FEATURE, Constant.id("stormheart_citadel"));
    public static final ResourceKey<PlacedFeature> JUPITER_WEATHER_STATION = ResourceKey.create(Registries.PLACED_FEATURE, Constant.id("jupiter_weather_station"));
    public static final ResourceKey<PlacedFeature> JUPITER_WEATHER_STATION_REFUGE = ResourceKey.create(Registries.PLACED_FEATURE, Constant.id("jupiter_weather_station_refuge"));
    public static final ResourceKey<PlacedFeature> JUPITER_RESEARCH_FACILITY = ResourceKey.create(Registries.PLACED_FEATURE, Constant.id("jupiter_research_facility"));
    public static final ResourceKey<PlacedFeature> JUPITER_STORM_HARVESTER_DENSE = ResourceKey.create(Registries.PLACED_FEATURE, Constant.id("jupiter_storm_harvester_dense"));
    public static final ResourceKey<PlacedFeature> JUPITER_HYDROSULFIDE_FRONT = ResourceKey.create(Registries.PLACED_FEATURE, Constant.id("jupiter_hydrosulfide_front"));

    public static final ResourceKey<PlacedFeature> MOON_BOULDER = ResourceKey.create(Registries.PLACED_FEATURE, Constant.id("moon_boulder"));
    public static final ResourceKey<PlacedFeature> MOON_BOULDER_SPARSE = ResourceKey.create(Registries.PLACED_FEATURE, Constant.id("moon_boulder_sparse"));
    public static final ResourceKey<PlacedFeature> MOON_OLIVINE_SPIRE = ResourceKey.create(Registries.PLACED_FEATURE, Constant.id("moon_olivine_spire"));
    public static final ResourceKey<PlacedFeature> MOON_ICE_PATCH = ResourceKey.create(Registries.PLACED_FEATURE, Constant.id("moon_ice_patch"));
    public static final ResourceKey<PlacedFeature> MOON_ICE_BOULDER = ResourceKey.create(Registries.PLACED_FEATURE, Constant.id("moon_ice_boulder"));
    public static final ResourceKey<PlacedFeature> MOON_FALLEN_METEOR = ResourceKey.create(Registries.PLACED_FEATURE, Constant.id("moon_fallen_meteor"));
    public static final ResourceKey<PlacedFeature> MOON_CHEESE_TREE = ResourceKey.create(Registries.PLACED_FEATURE, Constant.id("moon_cheese_tree"));
    public static final ResourceKey<PlacedFeature> MOON_CHEESE_FLORA = ResourceKey.create(Registries.PLACED_FEATURE, Constant.id("moon_cheese_flora"));
    public static final ResourceKey<PlacedFeature> MOON_CAVE_LANDMARK = ResourceKey.create(Registries.PLACED_FEATURE, Constant.id("moon_cave_landmark"));

    public static final ResourceKey<PlacedFeature> MOON_CAVE_DECORATIONS = ResourceKey.create(Registries.PLACED_FEATURE, Constant.id("moon_cave_decorations"));

    public static final ResourceKey<PlacedFeature> MOON_BURROWER_DEN = ResourceKey.create(Registries.PLACED_FEATURE, Constant.id("moon_burrower_den"));

    private static PlacedFeature surfaceScatter(HolderGetter<ConfiguredFeature<?, ?>> lookup, ResourceKey<ConfiguredFeature<?, ?>> feature, int rarity) {
        return new PlacedFeature(lookup.getOrThrow(feature), List.of(
                RarityFilter.onAverageOnceEvery(rarity),
                InSquarePlacement.spread(),
                PlacementUtils.HEIGHTMAP_WORLD_SURFACE,
                BiomeFilter.biome()
        ));
    }

    public static void bootstrapRegistries(BootstrapContext<PlacedFeature> context) {
        HolderGetter<ConfiguredFeature<?, ?>> configuredFeatureLookup = context.lookup(Registries.CONFIGURED_FEATURE);
        // Jupiter: the cluster features find their own cloud faces, the wrecks and citadel their own clearance.
        context.register(JUPITER_HYDROSULFIDE_CRYSTALS, new PlacedFeature(configuredFeatureLookup.getOrThrow(GCConfiguredFeature.JUPITER_HYDROSULFIDE_CRYSTALS), List.of(
                CountPlacement.of(6), InSquarePlacement.spread(), BiomeFilter.biome())));
        context.register(JUPITER_CHARGED_CONDENSATE, new PlacedFeature(configuredFeatureLookup.getOrThrow(GCConfiguredFeature.JUPITER_CHARGED_CONDENSATE), List.of(
                CountPlacement.of(6), InSquarePlacement.spread(), BiomeFilter.biome())));
        context.register(JUPITER_SURVEY_WRECK, new PlacedFeature(configuredFeatureLookup.getOrThrow(GCConfiguredFeature.JUPITER_SURVEY_WRECK), List.of(
                RarityFilter.onAverageOnceEvery(90), InSquarePlacement.spread(),
                HeightRangePlacement.uniform(VerticalAnchor.absolute(150), VerticalAnchor.absolute(198)), BiomeFilter.biome())));
        context.register(JUPITER_STORM_HARVESTER, new PlacedFeature(configuredFeatureLookup.getOrThrow(GCConfiguredFeature.JUPITER_STORM_HARVESTER), List.of(
                RarityFilter.onAverageOnceEvery(120), InSquarePlacement.spread(),
                HeightRangePlacement.uniform(VerticalAnchor.absolute(72), VerticalAnchor.absolute(134)), BiomeFilter.biome())));
        context.register(JUPITER_HYDROSULFIDE_FRONT, new PlacedFeature(configuredFeatureLookup.getOrThrow(GCConfiguredFeature.JUPITER_HYDROSULFIDE_CRYSTALS), List.of(
                CountPlacement.of(10), InSquarePlacement.spread(), BiomeFilter.biome())));
        context.register(JUPITER_STORM_HARVESTER_DENSE, new PlacedFeature(configuredFeatureLookup.getOrThrow(GCConfiguredFeature.JUPITER_STORM_HARVESTER), List.of(
                RarityFilter.onAverageOnceEvery(70), InSquarePlacement.spread(),
                HeightRangePlacement.uniform(VerticalAnchor.absolute(72), VerticalAnchor.absolute(134)), BiomeFilter.biome())));
        context.register(JUPITER_WEATHER_STATION, new PlacedFeature(configuredFeatureLookup.getOrThrow(GCConfiguredFeature.JUPITER_WEATHER_STATION), List.of(
                RarityFilter.onAverageOnceEvery(160), InSquarePlacement.spread(),
                HeightmapPlacement.onHeightmap(Heightmap.Types.WORLD_SURFACE_WG), BiomeFilter.biome())));
        context.register(JUPITER_WEATHER_STATION_REFUGE, new PlacedFeature(configuredFeatureLookup.getOrThrow(GCConfiguredFeature.JUPITER_WEATHER_STATION), List.of(
                RarityFilter.onAverageOnceEvery(30), InSquarePlacement.spread(),
                HeightmapPlacement.onHeightmap(Heightmap.Types.WORLD_SURFACE_WG), BiomeFilter.biome())));
        context.register(JUPITER_RESEARCH_FACILITY, new PlacedFeature(configuredFeatureLookup.getOrThrow(GCConfiguredFeature.JUPITER_RESEARCH_FACILITY), List.of(
                RarityFilter.onAverageOnceEvery(220), InSquarePlacement.spread(),
                HeightRangePlacement.uniform(VerticalAnchor.absolute(24), VerticalAnchor.absolute(76)), BiomeFilter.biome())));
        // Citadels only generate inside Red Vortices, so the rarity is per vortex chunk.
        context.register(STORMHEART_CITADEL, new PlacedFeature(configuredFeatureLookup.getOrThrow(GCConfiguredFeature.STORMHEART_CITADEL), List.of(
                RarityFilter.onAverageOnceEvery(40), InSquarePlacement.spread(),
                HeightRangePlacement.uniform(VerticalAnchor.absolute(6), VerticalAnchor.absolute(44)), BiomeFilter.biome())));
        context.register(MOON_CAVE_DECORATIONS, new PlacedFeature(configuredFeatureLookup.getOrThrow(GCConfiguredFeature.MOON_CAVE_DECORATIONS), List.of(
                CountPlacement.of(2), InSquarePlacement.spread(), BiomeFilter.biome())));
        context.register(MOON_BURROWER_DEN, new PlacedFeature(configuredFeatureLookup.getOrThrow(GCConfiguredFeature.MOON_BURROWER_DEN), List.of(
                RarityFilter.onAverageOnceEvery(24), InSquarePlacement.spread(), BiomeFilter.biome())));
        context.register(MARS_BOULDER, surfaceScatter(configuredFeatureLookup, GCConfiguredFeature.MARS_BOULDER, 5));
        context.register(MARS_BOULDER_SPARSE, surfaceScatter(configuredFeatureLookup, GCConfiguredFeature.MARS_BOULDER, 14));
        context.register(MARS_HEMATITE_DEPOSIT, surfaceScatter(configuredFeatureLookup, GCConfiguredFeature.MARS_HEMATITE_DEPOSIT, 10));
        context.register(MARS_ICE_SPIKE, new PlacedFeature(configuredFeatureLookup.getOrThrow(GCConfiguredFeature.MARS_ICE_SPIKE), List.of(
                CountPlacement.of(2),
                RarityFilter.onAverageOnceEvery(3),
                InSquarePlacement.spread(),
                PlacementUtils.HEIGHTMAP_WORLD_SURFACE,
                BiomeFilter.biome()
        )));
        context.register(MARS_ICE_BOULDER, surfaceScatter(configuredFeatureLookup, GCConfiguredFeature.MARS_ICE_BOULDER, 6));
        context.register(MARS_FROZEN_BRINE, surfaceScatter(configuredFeatureLookup, GCConfiguredFeature.MARS_FROZEN_BRINE, 8));
        // Frequent overlap builds broader frozen lake beds.
        context.register(MARS_FROZEN_LAKE, new PlacedFeature(configuredFeatureLookup.getOrThrow(GCConfiguredFeature.MARS_FROZEN_LAKE), List.of(
                RarityFilter.onAverageOnceEvery(4),
                InSquarePlacement.spread(),
                PlacementUtils.HEIGHTMAP_WORLD_SURFACE,
                BiomeFilter.biome()
        )));
        // Roughly one volcano per 50 chunks.
        context.register(VENUS_VOLCANO, new PlacedFeature(configuredFeatureLookup.getOrThrow(GCConfiguredFeature.VENUS_VOLCANO), List.of(
                RarityFilter.onAverageOnceEvery(50),
                InSquarePlacement.spread(),
                PlacementUtils.HEIGHTMAP_WORLD_SURFACE,
                BiomeFilter.biome()
        )));
        context.register(VENUS_VOLCANIC_BOULDER, surfaceScatter(configuredFeatureLookup, GCConfiguredFeature.VENUS_VOLCANIC_BOULDER, 5));
        context.register(VENUS_VOLCANIC_BOULDER_SPARSE, surfaceScatter(configuredFeatureLookup, GCConfiguredFeature.VENUS_VOLCANIC_BOULDER, 14));
        context.register(VENUS_PUMICE_BOULDER, surfaceScatter(configuredFeatureLookup, GCConfiguredFeature.VENUS_PUMICE_BOULDER, 6));
        context.register(OIL_LAKE, new PlacedFeature(configuredFeatureLookup.getOrThrow(GCConfiguredFeature.OIL_LAKE), List.of(
                PlacementUtils.HEIGHTMAP_WORLD_SURFACE,
                RarityFilter.onAverageOnceEvery(70),
                InSquarePlacement.spread(),
                BiomeFilter.biome()
        )));
        context.register(SULFURIC_ACID_LAKE, new PlacedFeature(configuredFeatureLookup.getOrThrow(GCConfiguredFeature.SULFURIC_ACID_LAKE), List.of(
                PlacementUtils.HEIGHTMAP_WORLD_SURFACE,
                RarityFilter.onAverageOnceEvery(9),
                InSquarePlacement.spread(),
                BiomeFilter.biome()
        )));
        context.register(VENUS_VAPOR_SPOUT, new PlacedFeature(configuredFeatureLookup.getOrThrow(GCConfiguredFeature.VENUS_VAPOR_SPOUT), List.of(
                CountPlacement.of(2),
                RarityFilter.onAverageOnceEvery(3),
                InSquarePlacement.spread(),
                PlacementUtils.HEIGHTMAP_WORLD_SURFACE,
                // Only place on solid Venus rock.
                BlockPredicateFilter.forPredicate(BlockPredicate.allOf(
                        BlockPredicate.replaceable(),
                        BlockPredicate.matchesTag(new Vec3i(0, -1, 0), GCBlockTags.VENUS_CARVER_REPLACEABLES))),
                BiomeFilter.biome()
        )));

        // Scattered underground lava pools.
        context.register(MERCURY_LAVA_LAKE, new PlacedFeature(configuredFeatureLookup.getOrThrow(GCConfiguredFeature.MERCURY_LAVA_LAKE), List.of(
                HeightRangePlacement.uniform(VerticalAnchor.absolute(0), VerticalAnchor.absolute(48)),
                RarityFilter.onAverageOnceEvery(12),
                InSquarePlacement.spread(),
                BiomeFilter.biome()
        )));
        context.register(MOON_BOULDER, surfaceScatter(configuredFeatureLookup, GCConfiguredFeature.MOON_BOULDER, 6));
        context.register(MOON_BOULDER_SPARSE, surfaceScatter(configuredFeatureLookup, GCConfiguredFeature.MOON_BOULDER, 16));
        context.register(MOON_OLIVINE_SPIRE, new PlacedFeature(configuredFeatureLookup.getOrThrow(GCConfiguredFeature.MOON_OLIVINE_SPIRE), List.of(
                CountPlacement.of(UniformInt.of(1, 3)),
                RarityFilter.onAverageOnceEvery(2),
                InSquarePlacement.spread(),
                PlacementUtils.HEIGHTMAP_WORLD_SURFACE,
                BiomeFilter.biome()
        )));
        context.register(MOON_ICE_PATCH, new PlacedFeature(configuredFeatureLookup.getOrThrow(GCConfiguredFeature.MOON_ICE_PATCH), List.of(
                RarityFilter.onAverageOnceEvery(5),
                InSquarePlacement.spread(),
                PlacementUtils.HEIGHTMAP_WORLD_SURFACE,
                BiomeFilter.biome()
        )));
        context.register(MOON_ICE_BOULDER, surfaceScatter(configuredFeatureLookup, GCConfiguredFeature.MOON_ICE_BOULDER, 8));
        context.register(MOON_FALLEN_METEOR, surfaceScatter(configuredFeatureLookup, GCConfiguredFeature.MOON_FALLEN_METEOR, 12));
        context.register(MOON_CHEESE_TREE, new PlacedFeature(configuredFeatureLookup.getOrThrow(GCConfiguredFeature.MOON_CHEESE_TREE), List.of(
                CountPlacement.of(UniformInt.of(1, 3)),
                InSquarePlacement.spread(),
                PlacementUtils.HEIGHTMAP_WORLD_SURFACE,
                BiomeFilter.biome()
        )));
        context.register(MOON_CHEESE_FLORA, new PlacedFeature(configuredFeatureLookup.getOrThrow(GCConfiguredFeature.MOON_CHEESE_FLORA), List.of(
                CountPlacement.of(UniformInt.of(4, 10)),
                InSquarePlacement.spread(),
                PlacementUtils.HEIGHTMAP_WORLD_SURFACE,
                BiomeFilter.biome()
        )));
        context.register(MOON_CAVE_LANDMARK, new PlacedFeature(configuredFeatureLookup.getOrThrow(GCConfiguredFeature.MOON_CAVE_LANDMARK), List.of(
                RarityFilter.onAverageOnceEvery(14),
                InSquarePlacement.spread(),
                BiomeFilter.biome()
        )));

        // Place crystal clusters on underground floors.
        context.register(MERCURY_CRYSTAL_SCATTER, new PlacedFeature(configuredFeatureLookup.getOrThrow(GCConfiguredFeature.MERCURY_CRYSTAL_SCATTER), List.of(
                CountPlacement.of(UniformInt.of(12, 32)),
                InSquarePlacement.spread(),
                HeightRangePlacement.uniform(VerticalAnchor.aboveBottom(4), VerticalAnchor.absolute(56)),
                EnvironmentScanPlacement.scanningFor(Direction.DOWN, BlockPredicate.solid(), BlockPredicate.ONLY_IN_AIR_PREDICATE, 12),
                // Reject floors too close to the surface.
                SurfaceRelativeThresholdFilter.of(Heightmap.Types.OCEAN_FLOOR_WG, Integer.MIN_VALUE, -5),
                RandomOffsetPlacement.vertical(ConstantInt.of(1)),
                BiomeFilter.biome()
        )));
    }

    public static void register() {
        WorldgenPlatformHooks.registerOilLake();
    }
}
