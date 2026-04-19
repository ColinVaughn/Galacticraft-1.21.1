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
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.data.worldgen.features.MiscOverworldFeatures;
import net.minecraft.data.worldgen.placement.PlacementUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.placement.BiomeFilter;
import net.minecraft.world.level.levelgen.placement.CountPlacement;
import net.minecraft.world.level.levelgen.placement.InSquarePlacement;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
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
        // Frequent, overlapping ice sheets build up into broad frozen lake beds.
        context.register(MARS_FROZEN_LAKE, new PlacedFeature(configuredFeatureLookup.getOrThrow(GCConfiguredFeature.MARS_FROZEN_LAKE), List.of(
                RarityFilter.onAverageOnceEvery(4),
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
                BiomeFilter.biome()
        )));
    }

    public static void register() {
        BiomeModifications.addFeature(context -> context.hasFeature(MiscOverworldFeatures.LAKE_LAVA), GenerationStep.Decoration.LAKES, OIL_LAKE);
    }
}
