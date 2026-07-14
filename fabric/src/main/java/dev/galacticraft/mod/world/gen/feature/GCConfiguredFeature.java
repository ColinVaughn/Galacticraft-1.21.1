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
import dev.galacticraft.mod.content.GCBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.random.SimpleWeightedRandomList;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import java.util.List;

import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.LakeFeature;
import net.minecraft.world.level.levelgen.feature.configurations.BlockStateConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.DiskConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.SimpleBlockConfiguration;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.level.levelgen.feature.stateproviders.RuleBasedBlockStateProvider;
import net.minecraft.world.level.levelgen.feature.stateproviders.WeightedStateProvider;

public class GCConfiguredFeature {
    public static final ResourceKey<ConfiguredFeature<?, ?>> OIL_LAKE = ResourceKey.create(Registries.CONFIGURED_FEATURE, Constant.id("oil_lake"));
    public static final ResourceKey<ConfiguredFeature<?, ?>> SULFURIC_ACID_LAKE = ResourceKey.create(Registries.CONFIGURED_FEATURE, Constant.id("sulfuric_acid_lake"));
    public static final ResourceKey<ConfiguredFeature<?, ?>> VENUS_VAPOR_SPOUT = ResourceKey.create(Registries.CONFIGURED_FEATURE, Constant.id("venus_vapor_spout"));

    // Mars surface decoration
    public static final ResourceKey<ConfiguredFeature<?, ?>> MARS_BOULDER = key("mars_boulder");
    public static final ResourceKey<ConfiguredFeature<?, ?>> MARS_HEMATITE_DEPOSIT = key("mars_hematite_deposit");
    public static final ResourceKey<ConfiguredFeature<?, ?>> MARS_ICE_SPIKE = key("mars_ice_spike");
    public static final ResourceKey<ConfiguredFeature<?, ?>> MARS_ICE_BOULDER = key("mars_ice_boulder");
    public static final ResourceKey<ConfiguredFeature<?, ?>> MARS_FROZEN_BRINE = key("mars_frozen_brine");
    public static final ResourceKey<ConfiguredFeature<?, ?>> MARS_FROZEN_LAKE = key("mars_frozen_lake");

    // Venus surface decoration
    public static final ResourceKey<ConfiguredFeature<?, ?>> VENUS_VOLCANIC_BOULDER = key("venus_volcanic_boulder");
    public static final ResourceKey<ConfiguredFeature<?, ?>> VENUS_PUMICE_BOULDER = key("venus_pumice_boulder");
    public static final ResourceKey<ConfiguredFeature<?, ?>> VENUS_VOLCANO = key("venus_volcano");

    // Mercury underground decoration
    public static final ResourceKey<ConfiguredFeature<?, ?>> MERCURY_LAVA_LAKE = key("mercury_lava_lake");
    public static final ResourceKey<ConfiguredFeature<?, ?>> MERCURY_CRYSTAL_SCATTER = key("mercury_crystal_scatter");

    // Moon surface decoration
    public static final ResourceKey<ConfiguredFeature<?, ?>> MOON_BOULDER = key("moon_boulder");
    public static final ResourceKey<ConfiguredFeature<?, ?>> MOON_OLIVINE_SPIRE = key("moon_olivine_spire");
    public static final ResourceKey<ConfiguredFeature<?, ?>> MOON_ICE_PATCH = key("moon_ice_patch");
    public static final ResourceKey<ConfiguredFeature<?, ?>> MOON_ICE_BOULDER = key("moon_ice_boulder");
    public static final ResourceKey<ConfiguredFeature<?, ?>> MOON_FALLEN_METEOR = key("moon_fallen_meteor");
    public static final ResourceKey<ConfiguredFeature<?, ?>> MOON_CHEESE_TREE = key("moon_cheese_tree");
    public static final ResourceKey<ConfiguredFeature<?, ?>> MOON_CHEESE_FLORA = key("moon_cheese_flora");
    public static final ResourceKey<ConfiguredFeature<?, ?>> MOON_CAVE_LANDMARK = key("moon_cave_landmark");

    private static ResourceKey<ConfiguredFeature<?, ?>> key(String id) {
        return ResourceKey.create(Registries.CONFIGURED_FEATURE, Constant.id(id));
    }

    public static void bootstrapRegistries(BootstrapContext<ConfiguredFeature<?, ?>> context) {
        context.register(OIL_LAKE, new ConfiguredFeature<>(Feature.LAKE,
                new LakeFeature.Configuration(BlockStateProvider.simple(GCBlocks.CRUDE_OIL), BlockStateProvider.simple(Blocks.STONE))));
        context.register(SULFURIC_ACID_LAKE, new ConfiguredFeature<>(Feature.LAKE,
                new LakeFeature.Configuration(BlockStateProvider.simple(GCBlocks.SULFURIC_ACID), BlockStateProvider.simple(GCBlocks.HARD_VENUS_ROCK))));
        context.register(VENUS_VAPOR_SPOUT, new ConfiguredFeature<>(Feature.SIMPLE_BLOCK,
                new SimpleBlockConfiguration(BlockStateProvider.simple(GCBlocks.VAPOR_SPOUT))));

        // Mars
        context.register(MARS_BOULDER, new ConfiguredFeature<>(Feature.FOREST_ROCK,
                new BlockStateConfiguration(GCBlocks.MARS_COBBLESTONE.defaultBlockState())));
        context.register(MARS_HEMATITE_DEPOSIT, new ConfiguredFeature<>(Feature.FOREST_ROCK,
                new BlockStateConfiguration(Blocks.RAW_IRON_BLOCK.defaultBlockState())));
        context.register(MARS_ICE_SPIKE, new ConfiguredFeature<>(Feature.ICE_SPIKE, NoneFeatureConfiguration.INSTANCE));
        context.register(MARS_ICE_BOULDER, new ConfiguredFeature<>(Feature.FOREST_ROCK,
                new BlockStateConfiguration(Blocks.BLUE_ICE.defaultBlockState())));
        context.register(MARS_FROZEN_BRINE, new ConfiguredFeature<>(Feature.FOREST_ROCK,
                new BlockStateConfiguration(Blocks.PACKED_ICE.defaultBlockState())));
        context.register(MARS_FROZEN_LAKE, new ConfiguredFeature<>(Feature.DISK,
                new DiskConfiguration(
                        RuleBasedBlockStateProvider.simple(BlockStateProvider.simple(Blocks.PACKED_ICE)),
                        BlockPredicate.matchesBlocks(List.of(
                                GCBlocks.MARS_SURFACE_ROCK, GCBlocks.MARS_SUB_SURFACE_ROCK, GCBlocks.MARS_STONE,
                                Blocks.RED_SAND, Blocks.PACKED_ICE, Blocks.BLUE_ICE)),
                        UniformInt.of(6, 8),
                        2)));

        // Venus
        context.register(VENUS_VOLCANIC_BOULDER, new ConfiguredFeature<>(Feature.FOREST_ROCK,
                new BlockStateConfiguration(GCBlocks.VOLCANIC_ROCK.defaultBlockState())));
        context.register(VENUS_PUMICE_BOULDER, new ConfiguredFeature<>(Feature.FOREST_ROCK,
                new BlockStateConfiguration(GCBlocks.PUMICE.defaultBlockState())));
        context.register(VENUS_VOLCANO, new ConfiguredFeature<>(GCFeatures.VOLCANO, NoneFeatureConfiguration.INSTANCE));

        // Mercury
        context.register(MERCURY_LAVA_LAKE, new ConfiguredFeature<>(Feature.LAKE,
                new LakeFeature.Configuration(BlockStateProvider.simple(Blocks.LAVA), BlockStateProvider.simple(GCBlocks.MERCURY_SCARP_ROCK))));
        context.register(MERCURY_CRYSTAL_SCATTER, new ConfiguredFeature<>(Feature.SIMPLE_BLOCK,
                new SimpleBlockConfiguration(BlockStateProvider.simple(GCBlocks.MERCURY_CRYSTAL_CLUSTER))));

        // Moon
        context.register(MOON_BOULDER, new ConfiguredFeature<>(Feature.FOREST_ROCK,
                new BlockStateConfiguration(GCBlocks.MOON_ROCK.defaultBlockState())));
        context.register(MOON_OLIVINE_SPIRE, new ConfiguredFeature<>(GCFeatures.OLIVINE_SPIRE, NoneFeatureConfiguration.INSTANCE));
        context.register(MOON_ICE_PATCH, new ConfiguredFeature<>(Feature.DISK,
                new DiskConfiguration(
                        RuleBasedBlockStateProvider.simple(BlockStateProvider.simple(Blocks.PACKED_ICE)),
                        BlockPredicate.matchesBlocks(List.of(
                                GCBlocks.MOON_TURF, GCBlocks.MOON_DIRT, GCBlocks.MOON_ROCK, GCBlocks.MOON_BASALT,
                                GCBlocks.DENSE_ICE, Blocks.PACKED_ICE, Blocks.ICE)),
                        UniformInt.of(4, 7),
                        2)));
        context.register(MOON_ICE_BOULDER, new ConfiguredFeature<>(Feature.FOREST_ROCK,
                new BlockStateConfiguration(GCBlocks.DENSE_ICE.defaultBlockState())));
        context.register(MOON_FALLEN_METEOR, new ConfiguredFeature<>(Feature.FOREST_ROCK,
                new BlockStateConfiguration(GCBlocks.FALLEN_METEOR.defaultBlockState())));
        context.register(MOON_CHEESE_TREE, new ConfiguredFeature<>(GCFeatures.CHEESE_TREE, NoneFeatureConfiguration.INSTANCE));
        context.register(MOON_CHEESE_FLORA, new ConfiguredFeature<>(Feature.SIMPLE_BLOCK,
                new SimpleBlockConfiguration(new WeightedStateProvider(
                        SimpleWeightedRandomList.<BlockState>builder()
                                .add(GCBlocks.MOON_SHRUBS.defaultBlockState(), 3)
                                .add(GCBlocks.MOON_WEED.defaultBlockState(), 2)
                                .add(GCBlocks.MOON_TANGLE.defaultBlockState(), 2)
                                .build()))));
        context.register(MOON_CAVE_LANDMARK,
                new ConfiguredFeature<>(GCFeatures.LUNAR_CAVE_LANDMARK, NoneFeatureConfiguration.INSTANCE));
    }
}
