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

import dev.galacticraft.mod.content.GCEntityTypes;
import dev.galacticraft.mod.content.GCSounds;
import dev.galacticraft.mod.world.gen.carver.GCConfiguredCarvers;
import dev.galacticraft.mod.world.gen.feature.GCOrePlacedFeatures;
import dev.galacticraft.mod.world.gen.feature.GCPlacedFeatures;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.Musics;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.jetbrains.annotations.Nullable;

/**
 * Venus biomes, modelled on real Venusian geology. Terrain shape is driven by the
 * {@code galacticraft:venus} noise settings; per-region surface materials are applied by
 * {@link dev.galacticraft.mod.world.gen.surfacerule.VenusSurfaceRules}. These builders control
 * mob spawns, per-biome fog/sky tinting, carvers, and decoration features. Lava lakes and rivers
 * come from feature/carver placement, not a global fluid sea.
 */
public class VenusBiomes {
    public static void monsters(MobSpawnSettings.Builder builder, int zombieWeight, int zombieVillagerWeight, int skeletonWeight) {
        builder.addSpawn(MobCategory.MONSTER, new MobSpawnSettings.SpawnerData(GCEntityTypes.EVOLVED_SPIDER, 100, 4, 4));
        builder.addSpawn(MobCategory.MONSTER, new MobSpawnSettings.SpawnerData(GCEntityTypes.EVOLVED_ZOMBIE, zombieWeight, 4, 4));
//        builder.addSpawn(MobCategory.MONSTER, new MobSpawnSettings.SpawnerData(GCEntityTypes.EVOLVED_ZOMBIE_VILLAGER, zombieVillagerWeight, 1, 1));
        builder.addSpawn(MobCategory.MONSTER, new MobSpawnSettings.SpawnerData(GCEntityTypes.EVOLVED_SKELETON, skeletonWeight, 4, 4));
        builder.addSpawn(MobCategory.MONSTER, new MobSpawnSettings.SpawnerData(GCEntityTypes.EVOLVED_CREEPER, 100, 4, 4));
//        builder.addSpawn(MobCategory.MONSTER, new MobSpawnSettings.SpawnerData(GCEntityTypes.EVOLVED_SLIME, 100, 4, 4));
//        builder.addSpawn(MobCategory.MONSTER, new MobSpawnSettings.SpawnerData(GCEntityTypes.EVOLVED_ENDERMAN, 10, 1, 4));
//        builder.addSpawn(MobCategory.MONSTER, new MobSpawnSettings.SpawnerData(GCEntityTypes.EVOLVED_WITCH, 5, 1, 1));
    }

    public static void addDefaultVenusOres(BiomeGenerationSettings.Builder builder) {
        builder.addFeature(GenerationStep.Decoration.UNDERGROUND_ORES, GCOrePlacedFeatures.ORE_COPPER_VENUS);
        builder.addFeature(GenerationStep.Decoration.UNDERGROUND_ORES, GCOrePlacedFeatures.ORE_TIN_VENUS);
        builder.addFeature(GenerationStep.Decoration.UNDERGROUND_ORES, GCOrePlacedFeatures.ORE_ALUMINUM_VENUS);
        builder.addFeature(GenerationStep.Decoration.UNDERGROUND_ORES, GCOrePlacedFeatures.ORE_SOLAR_VENUS);
        builder.addFeature(GenerationStep.Decoration.UNDERGROUND_ORES, GCOrePlacedFeatures.ORE_GALENA_VENUS);
    }

    private static BiomeGenerationSettings.Builder baseGeneration(
            HolderGetter<PlacedFeature> featureGetter, HolderGetter<ConfiguredWorldCarver<?>> carverGetter
    ) {
        BiomeGenerationSettings.Builder generation = new BiomeGenerationSettings.Builder(featureGetter, carverGetter);
        addDefaultVenusOres(generation);
        generation.addCarver(GenerationStep.Carving.AIR, GCConfiguredCarvers.VENUS_CAVE_CARVER);
        return generation;
    }

    public static Biome volcanicPlains(HolderGetter<PlacedFeature> featureGetter, HolderGetter<ConfiguredWorldCarver<?>> carverGetter) {
        BiomeGenerationSettings.Builder generation = baseGeneration(featureGetter, carverGetter);
        generation.addFeature(GenerationStep.Decoration.SURFACE_STRUCTURES, GCPlacedFeatures.VENUS_VOLCANIC_BOULDER_SPARSE);
        generation.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION, GCPlacedFeatures.VENUS_VAPOR_SPOUT);
        return venus(generation, 0xC97A3D, 0xE0A050, ParticleTypes.ASH);
    }

    public static Biome highlands(HolderGetter<PlacedFeature> featureGetter, HolderGetter<ConfiguredWorldCarver<?>> carverGetter) {
        BiomeGenerationSettings.Builder generation = baseGeneration(featureGetter, carverGetter);
        generation.addCarver(GenerationStep.Carving.AIR, GCConfiguredCarvers.VENUS_CRATER_CARVER);
        generation.addFeature(GenerationStep.Decoration.SURFACE_STRUCTURES, GCPlacedFeatures.VENUS_VOLCANIC_BOULDER);
        return venus(generation, 0xA85A38, 0xD08850, ParticleTypes.WHITE_ASH);
    }

    public static Biome lavaChannels(HolderGetter<PlacedFeature> featureGetter, HolderGetter<ConfiguredWorldCarver<?>> carverGetter) {
        BiomeGenerationSettings.Builder generation = baseGeneration(featureGetter, carverGetter);
        generation.addCarver(GenerationStep.Carving.AIR, GCConfiguredCarvers.VENUS_LAVA_CHANNEL_CARVER);
        generation.addFeature(GenerationStep.Decoration.SURFACE_STRUCTURES, GCPlacedFeatures.VENUS_VOLCANIC_BOULDER);
        return venus(generation, 0xC85028, 0xE87038, ParticleTypes.ASH);
    }

    public static Biome shieldVolcano(HolderGetter<PlacedFeature> featureGetter, HolderGetter<ConfiguredWorldCarver<?>> carverGetter) {
        BiomeGenerationSettings.Builder generation = baseGeneration(featureGetter, carverGetter);
        generation.addFeature(GenerationStep.Decoration.UNDERGROUND_ORES, GCOrePlacedFeatures.ORE_GALENA_VENUS_VOLCANIC);
        generation.addFeature(GenerationStep.Decoration.SURFACE_STRUCTURES, GCPlacedFeatures.VENUS_VOLCANO);
        generation.addFeature(GenerationStep.Decoration.SURFACE_STRUCTURES, GCPlacedFeatures.VENUS_PUMICE_BOULDER);
        generation.addFeature(GenerationStep.Decoration.SURFACE_STRUCTURES, GCPlacedFeatures.VENUS_VOLCANIC_BOULDER);
        return venus(generation, 0x8C3A20, 0xC85830, ParticleTypes.WHITE_ASH);
    }

    public static Biome sulfurFlats(HolderGetter<PlacedFeature> featureGetter, HolderGetter<ConfiguredWorldCarver<?>> carverGetter) {
        BiomeGenerationSettings.Builder generation = baseGeneration(featureGetter, carverGetter);
        generation.addFeature(GenerationStep.Decoration.LAKES, GCPlacedFeatures.SULFURIC_ACID_LAKE);
        generation.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION, GCPlacedFeatures.VENUS_VAPOR_SPOUT);
        return venus(generation, 0xC9C24E, 0xD8D060, null);
    }

    public static Biome lavaSea(HolderGetter<PlacedFeature> featureGetter, HolderGetter<ConfiguredWorldCarver<?>> carverGetter) {
        BiomeGenerationSettings.Builder generation = baseGeneration(featureGetter, carverGetter);
        generation.addCarver(GenerationStep.Carving.AIR, GCConfiguredCarvers.VENUS_LAVA_CHANNEL_CARVER);
        return venus(generation, 0xE0431E, 0xFF6636, ParticleTypes.ASH);
    }

    private static Biome venus(BiomeGenerationSettings.Builder generation, int fogColor, int skyColor, @Nullable ParticleOptions particle) {
        MobSpawnSettings.Builder spawnBuilder = new MobSpawnSettings.Builder();
        monsters(spawnBuilder, 95, 5, 100);

        BiomeSpecialEffects.Builder specialEffects = new BiomeSpecialEffects.Builder();
        specialEffects.waterColor(4159204)
                .waterFogColor(329011)
                .fogColor(fogColor)
                .skyColor(skyColor)
                .ambientMoodSound(AmbientMoodSettings.LEGACY_CAVE_SETTINGS)
                .backgroundMusic(Musics.createGameMusic(GCSounds.MUSIC_MOON));
        if (particle != null) {
            // Wind-blown volcanic ash drifting across the surface.
            specialEffects.ambientParticle(new AmbientParticleSettings(particle, 0.00625F));
        }

        return new Biome.BiomeBuilder()
                .mobSpawnSettings(spawnBuilder.build())
                .hasPrecipitation(true)
                .temperature(2.0F) // temp is hot to prevent snow
                .downfall(0.5F)
                .specialEffects(specialEffects.build())
                .generationSettings(generation.build())
                .build();
    }
}
