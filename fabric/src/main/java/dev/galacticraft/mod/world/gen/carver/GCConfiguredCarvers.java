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

package dev.galacticraft.mod.world.gen.carver;

import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.tag.GCBlockTags;
import dev.galacticraft.mod.world.gen.carver.config.CraterCarverConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.valueproviders.ConstantFloat;
import net.minecraft.util.valueproviders.TrapezoidFloat;
import net.minecraft.util.valueproviders.UniformFloat;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.carver.*;
import net.minecraft.world.level.levelgen.heightproviders.ConstantHeight;
import net.minecraft.world.level.levelgen.heightproviders.UniformHeight;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public class GCConfiguredCarvers {
    public static final ResourceKey<ConfiguredWorldCarver<?>> MOON_CANYON_CARVER = key(Constant.Carver.MOON_CANYON_CARVER);
    public static final ResourceKey<ConfiguredWorldCarver<?>> MOON_CRATER_CARVER = key(Constant.Carver.MOON_CRATER_CARVER);
    public static final ResourceKey<ConfiguredWorldCarver<?>> MOON_LARGE_CRATER_CARVER = key(Constant.Carver.MOON_LARGE_CRATER_CARVER);
    public static final ResourceKey<ConfiguredWorldCarver<?>> MOON_HIGHLANDS_CAVE_CARVER = key(Constant.Carver.MOON_HIGHLANDS_CAVE_CARVER);
    public static final ResourceKey<ConfiguredWorldCarver<?>> MOON_MARE_LAVA_TUBE_CARVER = key(Constant.Carver.MOON_MARE_LAVA_TUBE_CARVER);
    public static final ResourceKey<ConfiguredWorldCarver<?>> MARS_CANYON_CARVER = key(Constant.Carver.MARS_CANYON_CARVER);
    public static final ResourceKey<ConfiguredWorldCarver<?>> MARS_CRATER_CARVER = key(Constant.Carver.MARS_CRATER_CARVER);
    public static final ResourceKey<ConfiguredWorldCarver<?>> MARS_CAVE_CARVER = key(Constant.Carver.MARS_CAVE_CARVER);
    public static final ResourceKey<ConfiguredWorldCarver<?>> MARS_CHANNEL_CARVER = key(Constant.Carver.MARS_CHANNEL_CARVER);
    public static final ResourceKey<ConfiguredWorldCarver<?>> VENUS_CAVE_CARVER = key(Constant.Carver.VENUS_CAVE_CARVER);
    public static final ResourceKey<ConfiguredWorldCarver<?>> VENUS_CRATER_CARVER = key(Constant.Carver.VENUS_CRATER_CARVER);
    public static final ResourceKey<ConfiguredWorldCarver<?>> VENUS_LAVA_CHANNEL_CARVER = key(Constant.Carver.VENUS_LAVA_CHANNEL_CARVER);
    public static final ResourceKey<ConfiguredWorldCarver<?>> MERCURY_CAVE_CARVER = key(Constant.Carver.MERCURY_CAVE_CARVER);
    public static final ResourceKey<ConfiguredWorldCarver<?>> MERCURY_CAVERN_CARVER = key(Constant.Carver.MERCURY_CAVERN_CARVER);
    public static final ResourceKey<ConfiguredWorldCarver<?>> MERCURY_CANYON_CARVER = key(Constant.Carver.MERCURY_CANYON_CARVER);

    @Contract(pure = true)
    private static @NotNull ResourceKey<ConfiguredWorldCarver<?>> key(String s) {
        return Constant.key(Registries.CONFIGURED_CARVER, s);
    }

    public static void bootstrapRegistries(BootstrapContext<ConfiguredWorldCarver<?>> context) {
        // Sparse impact fractures in old highland crust. These are deliberately narrow and deep,
        // unlike the enormous terrestrial-style ravines previously applied to every Moon biome.
        context.register(MOON_CANYON_CARVER, WorldCarver.CANYON.configured(new CanyonCarverConfiguration(
                0.012f,
                UniformHeight.of(VerticalAnchor.absolute(4), VerticalAnchor.absolute(48)),
                ConstantFloat.of(1.2f),
                VerticalAnchor.aboveBottom(8),
                CarverDebugSettings.DEFAULT,
                BuiltInRegistries.BLOCK.getOrCreateTag(GCBlockTags.MOON_CARVER_REPLACEABLES),
                UniformFloat.of(-0.125f, 0.125f),
                new CanyonCarverConfiguration.CanyonShapeConfiguration(
                        UniformFloat.of(0.75f, 1.0f),
                        TrapezoidFloat.of(0.35f, 2.2f, 0.5f),
                        3,
                        UniformFloat.of(0.75f, 1.0f),
                        1.0f,
                        0.0f)
        )));
        // Common impact craters for lunar biomes.
        context.register(MOON_CRATER_CARVER, GCCarvers.CRATERS.configured(new CraterCarverConfig(
                0.055f,
                ConstantHeight.of(VerticalAnchor.absolute(128)),
                UniformFloat.of(0.4f, 0.6f),
                CarverDebugSettings.DEFAULT,
                34,
                10,
                8,
                2.0f
        )));
        // Rare large craters for highlands and fresh-impact fields.
        context.register(MOON_LARGE_CRATER_CARVER, GCCarvers.CRATERS.configured(new CraterCarverConfig(
                0.012f,
                ConstantHeight.of(VerticalAnchor.absolute(128)),
                UniformFloat.of(0.4f, 0.6f),
                CarverDebugSettings.DEFAULT,
                44,
                20,
                6,
                2.6f
        )));
        context.register(MOON_HIGHLANDS_CAVE_CARVER, GCCarvers.LUNAR_IMPACT_CAVERN.configured(new CaveCarverConfiguration(
                0.032f,
                UniformHeight.of(VerticalAnchor.absolute(-8), VerticalAnchor.absolute(44)),
                UniformFloat.of(0.82f, 1.12f),
                VerticalAnchor.aboveBottom(-64),
                BuiltInRegistries.BLOCK.getOrCreateTag(GCBlockTags.MOON_CARVER_REPLACEABLES),
                UniformFloat.of(0.85f, 1.15f),
                UniformFloat.of(0.72f, 1.0f),
                UniformFloat.of(-0.95f, -0.65f)
        )));
        context.register(MOON_MARE_LAVA_TUBE_CARVER, GCCarvers.LUNAR_LAVA_TUBE.configured(new CaveCarverConfiguration(
                0.045f,
                UniformHeight.of(VerticalAnchor.absolute(24), VerticalAnchor.absolute(58)),
                UniformFloat.of(0.85f, 1.15f),
                VerticalAnchor.aboveBottom(-64),
                BuiltInRegistries.BLOCK.getOrCreateTag(GCBlockTags.MOON_CARVER_REPLACEABLES),
                UniformFloat.of(0.9f, 1.2f),
                UniformFloat.of(0.62f, 0.82f),
                UniformFloat.of(-0.95f, -0.65f)
        )));

        // Valles Marineris: deep, wide rift canyons cut into the Martian plateaus.
        context.register(MARS_CANYON_CARVER, WorldCarver.CANYON.configured(new CanyonCarverConfiguration(
                0.06f,
                UniformHeight.of(VerticalAnchor.absolute(20), VerticalAnchor.absolute(90)),
                ConstantFloat.of(3.0f),
                VerticalAnchor.aboveBottom(8),
                CarverDebugSettings.DEFAULT,
                BuiltInRegistries.BLOCK.getOrCreateTag(GCBlockTags.MARS_CARVER_REPLACEABLES),
                UniformFloat.of(-0.125f, 0.125f),
                new CanyonCarverConfiguration.CanyonShapeConfiguration(
                        UniformFloat.of(0.75f, 1.0f),
                        TrapezoidFloat.of(0, 6, 2),
                        3,
                        UniformFloat.of(0.75f, 1.0f),
                        1.0f,
                        0.0f)
        )));
        // Impact craters scattered across the cratered highlands.
        context.register(MARS_CRATER_CARVER, GCCarvers.CRATERS.configured(new CraterCarverConfig(
                0.04f,
                ConstantHeight.of(VerticalAnchor.absolute(128)),
                UniformFloat.of(0.4f, 0.6f),
                CarverDebugSettings.DEFAULT,
                24,
                7,
                7,
                1.0f
        )));
        context.register(MARS_CAVE_CARVER, GCCarvers.LUNAR_CAVE.configured(new CaveCarverConfiguration(
                0.14f,
                UniformHeight.of(VerticalAnchor.aboveBottom(8), VerticalAnchor.absolute(180)),
                UniformFloat.of(0.1f, 0.9f),
                VerticalAnchor.aboveBottom(-64),
                BuiltInRegistries.BLOCK.getOrCreateTag(GCBlockTags.MARS_CARVER_REPLACEABLES),
                UniformFloat.of(0.7f, 1.4f),
                UniformFloat.of(0.8f, 1.3f),
                UniformFloat.of(-1.0f, -0.4f)
        )));
        // Shallow near-surface channels; surface rules freeze their floors.
        context.register(MARS_CHANNEL_CARVER, WorldCarver.CANYON.configured(new CanyonCarverConfiguration(
                0.08f,
                UniformHeight.of(VerticalAnchor.absolute(55), VerticalAnchor.absolute(85)),
                ConstantFloat.of(2.0f),
                VerticalAnchor.aboveBottom(8),
                CarverDebugSettings.DEFAULT,
                BuiltInRegistries.BLOCK.getOrCreateTag(GCBlockTags.MARS_CARVER_REPLACEABLES),
                UniformFloat.of(-0.1f, 0.1f),
                new CanyonCarverConfiguration.CanyonShapeConfiguration(
                        UniformFloat.of(0.6f, 1.0f),
                        TrapezoidFloat.of(0, 3, 1),
                        3,
                        UniformFloat.of(0.75f, 1.0f),
                        0.4f,
                        0.0f)
        )));

        // Venus caves in volcanic bedrock.
        context.register(VENUS_CAVE_CARVER, GCCarvers.LUNAR_CAVE.configured(new CaveCarverConfiguration(
                0.14f,
                UniformHeight.of(VerticalAnchor.aboveBottom(8), VerticalAnchor.absolute(180)),
                UniformFloat.of(0.1f, 0.9f),
                VerticalAnchor.aboveBottom(-64),
                BuiltInRegistries.BLOCK.getOrCreateTag(GCBlockTags.VENUS_CARVER_REPLACEABLES),
                UniformFloat.of(0.7f, 1.4f),
                UniformFloat.of(0.8f, 1.3f),
                UniformFloat.of(-1.0f, -0.4f)
        )));
        // Impact craters pocking the highland tesserae.
        context.register(VENUS_CRATER_CARVER, GCCarvers.CRATERS.configured(new CraterCarverConfig(
                0.04f,
                ConstantHeight.of(VerticalAnchor.absolute(128)),
                UniformFloat.of(0.4f, 0.6f),
                CarverDebugSettings.DEFAULT,
                24,
                7,
                7,
                1.0f
        )));
        // Winding channels below the lava line.
        context.register(VENUS_LAVA_CHANNEL_CARVER, WorldCarver.CANYON.configured(new CanyonCarverConfiguration(
                0.14f,
                UniformHeight.of(VerticalAnchor.absolute(44), VerticalAnchor.absolute(66)),
                ConstantFloat.of(3.0f),
                VerticalAnchor.aboveBottom(8),
                CarverDebugSettings.DEFAULT,
                BuiltInRegistries.BLOCK.getOrCreateTag(GCBlockTags.VENUS_CARVER_REPLACEABLES),
                UniformFloat.of(-0.1f, 0.1f),
                new CanyonCarverConfiguration.CanyonShapeConfiguration(
                        UniformFloat.of(0.7f, 1.0f),
                        TrapezoidFloat.of(0, 4, 1),
                        3,
                        UniformFloat.of(0.75f, 1.0f),
                        0.5f,
                        0.0f)
        )));

        // Mercury cave network.
        context.register(MERCURY_CAVE_CARVER, GCCarvers.LUNAR_CAVE.configured(new CaveCarverConfiguration(
                0.14f,
                UniformHeight.of(VerticalAnchor.aboveBottom(8), VerticalAnchor.absolute(180)),
                UniformFloat.of(0.1f, 0.9f),
                // Below y = -8, carved space fills from the lava aquifer.
                VerticalAnchor.aboveBottom(-64),
                BuiltInRegistries.BLOCK.getOrCreateTag(GCBlockTags.MERCURY_CARVER_REPLACEABLES),
                UniformFloat.of(0.7f, 1.4f),
                UniformFloat.of(0.8f, 1.3f),
                UniformFloat.of(-1.0f, -0.4f)
        )));
        // Larger low-altitude caverns.
        context.register(MERCURY_CAVERN_CARVER, GCCarvers.LUNAR_CAVE.configured(new CaveCarverConfiguration(
                0.20f,
                UniformHeight.of(VerticalAnchor.aboveBottom(4), VerticalAnchor.absolute(60)),
                UniformFloat.of(0.1f, 0.9f),
                // Below y = -8, carved space fills from the lava aquifer.
                VerticalAnchor.aboveBottom(-64),
                BuiltInRegistries.BLOCK.getOrCreateTag(GCBlockTags.MERCURY_CARVER_REPLACEABLES),
                UniformFloat.of(1.6f, 3.2f),
                UniformFloat.of(1.4f, 2.4f),
                UniformFloat.of(-1.0f, -0.2f)
        )));
        // Mercury rift canyons.
        context.register(MERCURY_CANYON_CARVER, WorldCarver.CANYON.configured(new CanyonCarverConfiguration(
                0.06f,
                UniformHeight.of(VerticalAnchor.absolute(20), VerticalAnchor.absolute(90)),
                ConstantFloat.of(3.0f),
                VerticalAnchor.aboveBottom(8),
                CarverDebugSettings.DEFAULT,
                BuiltInRegistries.BLOCK.getOrCreateTag(GCBlockTags.MERCURY_CARVER_REPLACEABLES),
                UniformFloat.of(-0.125f, 0.125f),
                new CanyonCarverConfiguration.CanyonShapeConfiguration(
                        UniformFloat.of(0.75f, 1.0f),
                        TrapezoidFloat.of(0, 6, 2),
                        3,
                        UniformFloat.of(0.75f, 1.0f),
                        1.0f,
                        0.0f)
        )));
    }
}
