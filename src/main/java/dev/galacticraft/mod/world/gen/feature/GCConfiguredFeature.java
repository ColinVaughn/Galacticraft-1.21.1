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
import net.minecraft.world.level.block.Blocks;
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
    public static final ResourceKey<ConfiguredFeature<?, ?>> VENUS_LAVA_LAKE = key("venus_lava_lake");
    public static final ResourceKey<ConfiguredFeature<?, ?>> VENUS_VOLCANIC_BOULDER = key("venus_volcanic_boulder");
    public static final ResourceKey<ConfiguredFeature<?, ?>> VENUS_PUMICE_BOULDER = key("venus_pumice_boulder");

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

        // Scattered rock outcrops (Martian cobble boulders).
        context.register(MARS_BOULDER, new ConfiguredFeature<>(Feature.FOREST_ROCK,
                new BlockStateConfiguration(GCBlocks.MARS_COBBLESTONE.defaultBlockState())));
        // Surface hematite ("blueberry" iron concretion) deposits.
        context.register(MARS_HEMATITE_DEPOSIT, new ConfiguredFeature<>(Feature.FOREST_ROCK,
                new BlockStateConfiguration(Blocks.RAW_IRON_BLOCK.defaultBlockState())));
        // Polar packed-ice spikes.
        context.register(MARS_ICE_SPIKE, new ConfiguredFeature<>(Feature.ICE_SPIKE, NoneFeatureConfiguration.INSTANCE));
        // Mineable water-ice chunks at the poles.
        context.register(MARS_ICE_BOULDER, new ConfiguredFeature<>(Feature.FOREST_ROCK,
                new BlockStateConfiguration(Blocks.BLUE_ICE.defaultBlockState())));
        // Frozen brine seeps on canyon floors.
        context.register(MARS_FROZEN_BRINE, new ConfiguredFeature<>(Feature.FOREST_ROCK,
                new BlockStateConfiguration(Blocks.PACKED_ICE.defaultBlockState())));
        // Giant flat frozen lake beds — broad sheets of packed ice pooled on the surface.
        context.register(MARS_FROZEN_LAKE, new ConfiguredFeature<>(Feature.DISK,
                new DiskConfiguration(
                        RuleBasedBlockStateProvider.simple(BlockStateProvider.simple(Blocks.PACKED_ICE)),
                        BlockPredicate.matchesBlocks(List.of(
                                GCBlocks.MARS_SURFACE_ROCK, GCBlocks.MARS_SUB_SURFACE_ROCK, GCBlocks.MARS_STONE,
                                Blocks.RED_SAND, Blocks.PACKED_ICE, Blocks.BLUE_ICE)),
                        UniformInt.of(6, 8),
                        2)));

        // Broad flat sheets of surface lava — lava lakes and the floors of lava channels.
        context.register(VENUS_LAVA_LAKE, new ConfiguredFeature<>(Feature.DISK,
                new DiskConfiguration(
                        RuleBasedBlockStateProvider.simple(BlockStateProvider.simple(Blocks.LAVA)),
                        BlockPredicate.matchesBlocks(List.of(
                                GCBlocks.HARD_VENUS_ROCK, GCBlocks.SCORCHED_VENUS_ROCK, GCBlocks.VOLCANIC_ROCK,
                                Blocks.MAGMA_BLOCK, Blocks.BASALT)),
                        UniformInt.of(5, 8),
                        2)));
        // Scattered volcanic-rock outcrops.
        context.register(VENUS_VOLCANIC_BOULDER, new ConfiguredFeature<>(Feature.FOREST_ROCK,
                new BlockStateConfiguration(GCBlocks.VOLCANIC_ROCK.defaultBlockState())));
        // Pumice mounds around the shield volcano.
        context.register(VENUS_PUMICE_BOULDER, new ConfiguredFeature<>(Feature.FOREST_ROCK,
                new BlockStateConfiguration(GCBlocks.PUMICE.defaultBlockState())));
    }
}
