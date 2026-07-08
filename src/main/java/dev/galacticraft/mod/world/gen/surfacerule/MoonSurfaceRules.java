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

package dev.galacticraft.mod.world.gen.surfacerule;

import com.mojang.serialization.MapCodec;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.content.GCBlocks;
import dev.galacticraft.mod.world.biome.GCBiomes;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.minecraft.world.level.levelgen.SurfaceRules.ConditionSource;
import net.minecraft.world.level.levelgen.SurfaceRules.RuleSource;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * Per-biome Moon surface materials.
 * Keep this aligned with {@code data/galacticraft/worldgen/noise_settings/moon.json}, which is the runtime source of truth.
 */
public class MoonSurfaceRules {
    private static final RuleSource BEDROCK = block(Blocks.BEDROCK);
    private static final RuleSource LUNASLATE = block(GCBlocks.LUNASLATE);
    private static final RuleSource MOON_DIRT = block(GCBlocks.MOON_DIRT);
    private static final RuleSource MOON_ROCK = block(GCBlocks.MOON_ROCK);
    private static final RuleSource MOON_TURF = block(GCBlocks.MOON_TURF);
    private static final RuleSource MOON_BASALT = block(GCBlocks.MOON_BASALT);
    private static final RuleSource MOON_SURFACE_ROCK = block(GCBlocks.MOON_SURFACE_ROCK);
    private static final RuleSource MOON_MOSS = block(GCBlocks.MOON_MOSS);
    private static final RuleSource OLIVINE_BASALT = block(GCBlocks.OLIVINE_BASALT);
    private static final RuleSource DENSE_ICE = block(GCBlocks.DENSE_ICE);
    private static final RuleSource PACKED_ICE = block(Blocks.PACKED_ICE);

    // Shadowed basins freeze near the surface.
    private static final ConditionSource ICE_LINE_SURFACE = SurfaceRules.verticalGradient("moon_ice_line", VerticalAnchor.absolute(70), VerticalAnchor.absolute(80));
    private static final ConditionSource ICE_LINE_SUB = SurfaceRules.verticalGradient("moon_ice_sub", VerticalAnchor.absolute(68), VerticalAnchor.absolute(78));

    private static RuleSource biomeSurface(ResourceKey<Biome> biome, RuleSource onFloor, RuleSource subSurface) {
        return SurfaceRules.ifTrue(SurfaceRules.isBiome(biome),
                SurfaceRules.sequence(SurfaceRules.ifTrue(SurfaceRules.ON_FLOOR, onFloor), subSurface));
    }

    private static final RuleSource SURFACE_GENERATION = SurfaceRules.sequence(
            biomeSurface(GCBiomes.Moon.BASALTIC_MARE, MOON_BASALT, MOON_BASALT),
            SurfaceRules.ifTrue(SurfaceRules.isBiome(GCBiomes.Moon.COMET_TUNDRA), SurfaceRules.sequence(
                    SurfaceRules.ifTrue(SurfaceRules.ON_FLOOR, SurfaceRules.sequence(
                            SurfaceRules.ifTrue(ICE_LINE_SURFACE, PACKED_ICE),
                            MOON_TURF)),
                    SurfaceRules.ifTrue(ICE_LINE_SUB, DENSE_ICE),
                    MOON_DIRT)),
            biomeSurface(GCBiomes.Moon.OLIVINE_SPIKES, OLIVINE_BASALT, MOON_ROCK),
            biomeSurface(GCBiomes.Moon.RAY_CRATER_FIELD, MOON_SURFACE_ROCK, MOON_ROCK),
            biomeSurface(GCBiomes.Moon.CHEESE_GROVE, MOON_MOSS, MOON_DIRT),
            biomeSurface(GCBiomes.Moon.LUNAR_HIGHLANDS, MOON_SURFACE_ROCK, MOON_DIRT),
            // Default to regolith over dirt.
            SurfaceRules.sequence(SurfaceRules.ifTrue(SurfaceRules.ON_FLOOR, MOON_TURF), MOON_DIRT)
    );

    public static final RuleSource MOON = createDefaultRule();

    public static @NotNull RuleSource createDefaultRule() {
        return SurfaceRules.sequence(
                SurfaceRules.ifTrue(SurfaceRules.verticalGradient("bedrock_floor", VerticalAnchor.bottom(), VerticalAnchor.aboveBottom(5)), BEDROCK),
                SurfaceRules.ifTrue(SurfaceRules.abovePreliminarySurface(), SurfaceRules.ifTrue(SurfaceRules.UNDER_FLOOR, SURFACE_GENERATION)),
                SurfaceRules.ifTrue(SurfaceRules.verticalGradient("lunaslate", VerticalAnchor.absolute(-4), VerticalAnchor.absolute(4)), LUNASLATE)
        );
    }

    @Contract("_ -> new")
    private static @NotNull RuleSource block(@NotNull Block block) {
        return SurfaceRules.state(block.defaultBlockState());
    }

    @Contract("_ -> new")
    public static SurfaceRules.@NotNull ConditionSource biome(@NotNull TagKey<Biome> biome) {
        return new BiomeTagRule(biome);
    }

    @SafeVarargs
    @Contract("_ -> new")
    public static SurfaceRules.@NotNull ConditionSource biome(@NotNull ResourceKey<Biome> @NotNull ... keys) {
        return SurfaceRules.isBiome(keys);
    }

    public static void register() {
        Registry.register(BuiltInRegistries.MATERIAL_RULE, Constant.id("moon"), MapCodec.unit(MOON));
    }
}
