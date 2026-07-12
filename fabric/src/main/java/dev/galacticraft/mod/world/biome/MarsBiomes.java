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

package dev.galacticraft.mod.world.biome;

import dev.galacticraft.mod.content.GCSounds;
import dev.galacticraft.mod.world.gen.carver.GCConfiguredCarvers;
import dev.galacticraft.mod.world.gen.feature.GCOrePlacedFeatures;
import dev.galacticraft.mod.world.gen.feature.GCPlacedFeatures;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.Musics;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/**
 * Mars biomes, modelled on real Martian regions. Terrain shape is driven by the
 * {@code galacticraft:mars} noise settings; per-region surface materials are applied by the
 * surface rules there. These builders control mob spawns, per-biome fog/sky tinting, carvers,
 * and decoration features.
 */
public class MarsBiomes {
    public static void addDefaultMarsOres(BiomeGenerationSettings.Builder builder) {
        builder.addFeature(GenerationStep.Decoration.UNDERGROUND_ORES, GCOrePlacedFeatures.ORE_IRON_MARS);
        builder.addFeature(GenerationStep.Decoration.UNDERGROUND_ORES, GCOrePlacedFeatures.ORE_COPPER_MARS);
        builder.addFeature(GenerationStep.Decoration.UNDERGROUND_ORES, GCOrePlacedFeatures.ORE_TIN_MARS);
        builder.addFeature(GenerationStep.Decoration.UNDERGROUND_ORES, GCOrePlacedFeatures.ORE_DESH_MARS);
    }

    private static BiomeGenerationSettings.Builder baseGeneration(
            HolderGetter<PlacedFeature> featureGetter, HolderGetter<ConfiguredWorldCarver<?>> carverGetter
    ) {
        BiomeGenerationSettings.Builder generation = new BiomeGenerationSettings.Builder(featureGetter, carverGetter);
        addDefaultMarsOres(generation);
        generation.addCarver(GenerationStep.Carving.AIR, GCConfiguredCarvers.MARS_CAVE_CARVER);
        return generation;
    }

    public static Biome vastitas(HolderGetter<PlacedFeature> featureGetter, HolderGetter<ConfiguredWorldCarver<?>> carverGetter) {
        BiomeGenerationSettings.Builder generation = baseGeneration(featureGetter, carverGetter);
        generation.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION, GCPlacedFeatures.MARS_HEMATITE_DEPOSIT);
        generation.addFeature(GenerationStep.Decoration.SURFACE_STRUCTURES, GCPlacedFeatures.MARS_BOULDER_SPARSE);
        return mars(generation, 2.0F, 0x8C4E36, 0xC67A54, false);
    }

    public static Biome highlands(HolderGetter<PlacedFeature> featureGetter, HolderGetter<ConfiguredWorldCarver<?>> carverGetter) {
        BiomeGenerationSettings.Builder generation = baseGeneration(featureGetter, carverGetter);
        generation.addCarver(GenerationStep.Carving.AIR, GCConfiguredCarvers.MARS_CRATER_CARVER);
        generation.addFeature(GenerationStep.Decoration.SURFACE_STRUCTURES, GCPlacedFeatures.MARS_BOULDER);
        return mars(generation, 2.0F, 0x7A4230, 0xB56A48, true);
    }

    public static Biome dunes(HolderGetter<PlacedFeature> featureGetter, HolderGetter<ConfiguredWorldCarver<?>> carverGetter) {
        BiomeGenerationSettings.Builder generation = baseGeneration(featureGetter, carverGetter);
        return mars(generation, 2.0F, 0x9C5A3C, 0xD08A60, true);
    }

    public static Biome canyon(HolderGetter<PlacedFeature> featureGetter, HolderGetter<ConfiguredWorldCarver<?>> carverGetter) {
        BiomeGenerationSettings.Builder generation = baseGeneration(featureGetter, carverGetter);
        generation.addCarver(GenerationStep.Carving.AIR, GCConfiguredCarvers.MARS_CANYON_CARVER);
        generation.addCarver(GenerationStep.Carving.AIR, GCConfiguredCarvers.MARS_CHANNEL_CARVER);
        generation.addFeature(GenerationStep.Decoration.LOCAL_MODIFICATIONS, GCPlacedFeatures.MARS_FROZEN_LAKE);
        generation.addFeature(GenerationStep.Decoration.SURFACE_STRUCTURES, GCPlacedFeatures.MARS_BOULDER);
        generation.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION, GCPlacedFeatures.MARS_FROZEN_BRINE);
        return mars(generation, 2.0F, 0x6E3B2A, 0xA85E40, false);
    }

    public static Biome volcanics(HolderGetter<PlacedFeature> featureGetter, HolderGetter<ConfiguredWorldCarver<?>> carverGetter) {
        BiomeGenerationSettings.Builder generation = baseGeneration(featureGetter, carverGetter);
        // Use a denser desh pass with a unique key to avoid FeatureSorter cycles.
        generation.addFeature(GenerationStep.Decoration.UNDERGROUND_ORES, GCOrePlacedFeatures.ORE_DESH_MARS_VOLCANIC);
        generation.addFeature(GenerationStep.Decoration.SURFACE_STRUCTURES, GCPlacedFeatures.MARS_BOULDER);
        return mars(generation, 2.0F, 0x5A3428, 0x8C4E36, true);
    }

    public static Biome polar(HolderGetter<PlacedFeature> featureGetter, HolderGetter<ConfiguredWorldCarver<?>> carverGetter) {
        BiomeGenerationSettings.Builder generation = baseGeneration(featureGetter, carverGetter);
        generation.addCarver(GenerationStep.Carving.AIR, GCConfiguredCarvers.MARS_CHANNEL_CARVER);
        generation.addFeature(GenerationStep.Decoration.LOCAL_MODIFICATIONS, GCPlacedFeatures.MARS_FROZEN_LAKE);
        generation.addFeature(GenerationStep.Decoration.SURFACE_STRUCTURES, GCPlacedFeatures.MARS_ICE_SPIKE);
        generation.addFeature(GenerationStep.Decoration.SURFACE_STRUCTURES, GCPlacedFeatures.MARS_ICE_BOULDER);
        // Precipitation stays off, so no snow layer forms.
        return mars(generation, 0.0F, 0xB0A8B8, 0xC8C0D0, false);
    }

    private static Biome mars(BiomeGenerationSettings.Builder generation, float temperature, int fogColor, int skyColor, boolean dusty) {
        MobSpawnSettings.Builder spawnBuilder = new MobSpawnSettings.Builder();
        MoonBiomes.monsters(spawnBuilder, 95, 5, 100);

        BiomeSpecialEffects.Builder specialEffects = new BiomeSpecialEffects.Builder();
        specialEffects.waterColor(4159204)
                .waterFogColor(329011)
                .fogColor(fogColor)
                .skyColor(skyColor)
                .ambientMoodSound(AmbientMoodSettings.LEGACY_CAVE_SETTINGS)
                .backgroundMusic(Musics.createGameMusic(GCSounds.MUSIC_MARS));
        if (dusty) {
            // Wind-blown dust haze drifting across the surface.
            specialEffects.ambientParticle(new AmbientParticleSettings(ParticleTypes.WHITE_ASH, 0.00625F));
        }

        return new Biome.BiomeBuilder()
                .mobSpawnSettings(spawnBuilder.build())
                .hasPrecipitation(false)
                .temperature(temperature)
                .downfall(0.0F)
                .specialEffects(specialEffects.build())
                .generationSettings(generation.build())
                .build();
    }
}
