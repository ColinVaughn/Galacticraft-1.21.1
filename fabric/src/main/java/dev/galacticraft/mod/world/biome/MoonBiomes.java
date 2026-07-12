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
import net.minecraft.sounds.Musics;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/** Moon biome builders and their surface decoration, carvers, and fog colors. */
public class MoonBiomes {
    private static final int SKY = 0;

    public static Biome createCometTundra(HolderGetter<PlacedFeature> featureLookup, HolderGetter<ConfiguredWorldCarver<?>> carverLookup) {
        var generation = baseGeneration(featureLookup, carverLookup);
        generation.addFeature(GenerationStep.Decoration.LOCAL_MODIFICATIONS, GCPlacedFeatures.MOON_ICE_PATCH);
        generation.addFeature(GenerationStep.Decoration.SURFACE_STRUCTURES, GCPlacedFeatures.MOON_ICE_BOULDER);
        generation.addFeature(GenerationStep.Decoration.SURFACE_STRUCTURES, GCPlacedFeatures.MOON_BOULDER_SPARSE);
        MobSpawnSettings.Builder spawns = new MobSpawnSettings.Builder();
        monsters(spawns, 70, 5, 80); // colder, quieter basins
        return moon(generation, spawns, 0x1E2430);
    }

    public static Biome createBasalticMare(HolderGetter<PlacedFeature> featureLookup, HolderGetter<ConfiguredWorldCarver<?>> carverLookup) {
        var generation = baseGeneration(featureLookup, carverLookup);
        generation.addCarver(GenerationStep.Carving.AIR, GCConfiguredCarvers.MOON_MARE_CAVE_CARVER);
        // Use dedicated bonus keys so these passes do not duplicate the defaults.
        generation.addFeature(GenerationStep.Decoration.UNDERGROUND_ORES, GCOrePlacedFeatures.ORE_TIN_MARE_BONUS);
        generation.addFeature(GenerationStep.Decoration.UNDERGROUND_ORES, GCOrePlacedFeatures.ORE_LUNAR_SAPPHIRE_MARE_BONUS);
        generation.addFeature(GenerationStep.Decoration.SURFACE_STRUCTURES, GCPlacedFeatures.MOON_BOULDER_SPARSE);
        MobSpawnSettings.Builder spawns = new MobSpawnSettings.Builder();
        monsters(spawns, 95, 5, 100);
        return moon(generation, spawns, 0x3A3A40);
    }

    public static Biome createLunarHighlands(HolderGetter<PlacedFeature> featureLookup, HolderGetter<ConfiguredWorldCarver<?>> carverLookup) {
        var generation = baseGeneration(featureLookup, carverLookup);
        generation.addCarver(GenerationStep.Carving.AIR, GCConfiguredCarvers.MOON_HIGHLANDS_CAVE_CARVER);
        generation.addCarver(GenerationStep.Carving.AIR, GCConfiguredCarvers.MOON_LARGE_CRATER_CARVER);
        generation.addFeature(GenerationStep.Decoration.SURFACE_STRUCTURES, GCPlacedFeatures.MOON_BOULDER);
        MobSpawnSettings.Builder spawns = new MobSpawnSettings.Builder();
        monsters(spawns, 95, 5, 100);
        return moon(generation, spawns, 0x8A8A86);
    }

    public static Biome createLunarLowlands(HolderGetter<PlacedFeature> featureLookup, HolderGetter<ConfiguredWorldCarver<?>> carverLookup) {
        var generation = baseGeneration(featureLookup, carverLookup);
        generation.addFeature(GenerationStep.Decoration.SURFACE_STRUCTURES, GCPlacedFeatures.MOON_BOULDER_SPARSE);
        MobSpawnSettings.Builder spawns = new MobSpawnSettings.Builder();
        monsters(spawns, 95, 5, 100);
        return moon(generation, spawns, 0x6B6B70);
    }

    public static Biome createOlivineSpikes(HolderGetter<PlacedFeature> featureLookup, HolderGetter<ConfiguredWorldCarver<?>> carverLookup) {
        var generation = baseGeneration(featureLookup, carverLookup);
        generation.addFeature(GenerationStep.Decoration.SURFACE_STRUCTURES, GCPlacedFeatures.MOON_OLIVINE_SPIRE);
        generation.addFeature(GenerationStep.Decoration.SURFACE_STRUCTURES, GCPlacedFeatures.MOON_BOULDER_SPARSE);
        MobSpawnSettings.Builder spawns = new MobSpawnSettings.Builder();
        monsters(spawns, 95, 5, 100);
        return moon(generation, spawns, 0x4A5A46);
    }

    /** Fresh impact field with large craters and meteorites. */
    public static Biome createRayCraterField(HolderGetter<PlacedFeature> featureLookup, HolderGetter<ConfiguredWorldCarver<?>> carverLookup) {
        var generation = baseGeneration(featureLookup, carverLookup);
        generation.addCarver(GenerationStep.Carving.AIR, GCConfiguredCarvers.MOON_LARGE_CRATER_CARVER);
        generation.addFeature(GenerationStep.Decoration.SURFACE_STRUCTURES, GCPlacedFeatures.MOON_BOULDER);
        generation.addFeature(GenerationStep.Decoration.SURFACE_STRUCTURES, GCPlacedFeatures.MOON_FALLEN_METEOR);
        MobSpawnSettings.Builder spawns = new MobSpawnSettings.Builder();
        monsters(spawns, 95, 5, 100);
        return moon(generation, spawns, 0x9A968E);
    }

    /** Rare biome for moon-cheese vegetation. */
    public static Biome createCheeseGrove(HolderGetter<PlacedFeature> featureLookup, HolderGetter<ConfiguredWorldCarver<?>> carverLookup) {
        var generation = baseGeneration(featureLookup, carverLookup);
        generation.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION, GCPlacedFeatures.MOON_CHEESE_TREE);
        generation.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION, GCPlacedFeatures.MOON_CHEESE_FLORA);
        MobSpawnSettings.Builder spawns = new MobSpawnSettings.Builder();
        monsters(spawns, 60, 5, 60);
        return moon(generation, spawns, 0x8A7A50);
    }

    public static void monsters(MobSpawnSettings.Builder builder, int zombieWeight, int zombieVillagerWeight, int skeletonWeight) {
        builder.addSpawn(MobCategory.MONSTER, new MobSpawnSettings.SpawnerData(GCEntityTypes.EVOLVED_SPIDER, 100, 4, 4));
        builder.addSpawn(MobCategory.MONSTER, new MobSpawnSettings.SpawnerData(GCEntityTypes.EVOLVED_ZOMBIE, zombieWeight, 4, 4));
//        builder.addSpawn(MobCategory.MONSTER, new MobSpawnSettings.SpawnerData(GCEntityTypes.EVOLVED_ZOMBIE_VILLAGER, zombieVillagerWeight, 1, 1));
        builder.addSpawn(MobCategory.MONSTER, new MobSpawnSettings.SpawnerData(GCEntityTypes.EVOLVED_SKELETON, skeletonWeight, 4, 4));
        builder.addSpawn(MobCategory.MONSTER, new MobSpawnSettings.SpawnerData(GCEntityTypes.EVOLVED_CREEPER, 100, 4, 4));
//        builder.addSpawn(MobCategory.MONSTER, new MobSpawnSettings.SpawnerData(GCEntityTypes.EVOLVED_SLIME, 100, 4, 4));
        builder.addSpawn(MobCategory.MONSTER, new MobSpawnSettings.SpawnerData(GCEntityTypes.EVOLVED_ENDERMAN, 10, 1, 4));
        builder.addSpawn(MobCategory.MONSTER, new MobSpawnSettings.SpawnerData(GCEntityTypes.EVOLVED_WITCH, 5, 1, 1));
    }

    public static void addDefaultMoonOres(BiomeGenerationSettings.Builder builder) {
        builder.addFeature(GenerationStep.Decoration.UNDERGROUND_ORES, GCOrePlacedFeatures.ORE_COPPER_MOON);
        builder.addFeature(GenerationStep.Decoration.UNDERGROUND_ORES, GCOrePlacedFeatures.ORE_COPPER_LARGE_MOON);

        builder.addFeature(GenerationStep.Decoration.UNDERGROUND_ORES, GCOrePlacedFeatures.ORE_TIN_SMALL_MOON);
        builder.addFeature(GenerationStep.Decoration.UNDERGROUND_ORES, GCOrePlacedFeatures.ORE_TIN_MIDDLE_MOON);
        builder.addFeature(GenerationStep.Decoration.UNDERGROUND_ORES, GCOrePlacedFeatures.ORE_TIN_UPPER_MOON);

        builder.addFeature(GenerationStep.Decoration.UNDERGROUND_ORES, GCOrePlacedFeatures.ORE_CHEESE_MOON);
        builder.addFeature(GenerationStep.Decoration.UNDERGROUND_ORES, GCOrePlacedFeatures.ORE_CHEESE_LARGE_MOON);

        builder.addFeature(GenerationStep.Decoration.UNDERGROUND_ORES, GCOrePlacedFeatures.ORE_LUNAR_SAPPHIRE_MOON);
    }

    public static void addDefaultSoftDisks(BiomeGenerationSettings.Builder builder) {
        // builder.addFeature(GenerationStep.Decoration.UNDERGROUND_ORES, GCOrePlacedFeatures.BASALT_DISK_MOON);
    }

    private static BiomeGenerationSettings.Builder baseGeneration(
            HolderGetter<PlacedFeature> featureGetter, HolderGetter<ConfiguredWorldCarver<?>> carverGetter
    ) {
        BiomeGenerationSettings.Builder generation = new BiomeGenerationSettings.Builder(featureGetter, carverGetter);
        addDefaultMoonOres(generation);
        addDefaultSoftDisks(generation);
        generation.addCarver(GenerationStep.Carving.AIR, GCConfiguredCarvers.MOON_CRATER_CARVER);
        generation.addCarver(GenerationStep.Carving.AIR, GCConfiguredCarvers.MOON_CANYON_CARVER);
        return generation;
    }

    private static Biome moon(BiomeGenerationSettings.Builder generation, MobSpawnSettings.Builder spawnBuilder, int fogColor) {
        BiomeSpecialEffects.Builder specialEffects = new BiomeSpecialEffects.Builder();
        specialEffects.waterColor(4159204)
                .waterFogColor(329011)
                .fogColor(fogColor)
                .skyColor(SKY)
                .ambientMoodSound(AmbientMoodSettings.LEGACY_CAVE_SETTINGS)
                .backgroundMusic(Musics.createGameMusic(GCSounds.MUSIC_MOON));

        return new Biome.BiomeBuilder()
                .mobSpawnSettings(spawnBuilder.build())
                .hasPrecipitation(false)
                .temperature(2.0F) // temp is hot to prevent snow
                .downfall(0.0F)
                .specialEffects(specialEffects.build())
                .generationSettings(generation.build())
                .build();
    }
}
