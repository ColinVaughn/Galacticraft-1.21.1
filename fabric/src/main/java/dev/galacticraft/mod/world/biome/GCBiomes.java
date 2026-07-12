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

import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.content.GCSounds;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.Musics;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public final class GCBiomes {
    public static final class Moon {
        public static final ResourceKey<Biome> COMET_TUNDRA = key("comet_tundra");
        public static final ResourceKey<Biome> BASALTIC_MARE = key("basaltic_mare");
        public static final ResourceKey<Biome> LUNAR_HIGHLANDS = key("lunar_highlands");
        public static final ResourceKey<Biome> LUNAR_LOWLANDS = key("lunar_lowlands");
        public static final ResourceKey<Biome> OLIVINE_SPIKES = key("olivine_spikes");
        public static final ResourceKey<Biome> RAY_CRATER_FIELD = key("ray_crater_field");
        public static final ResourceKey<Biome> CHEESE_GROVE = key("cheese_grove");
    }

    public static final class Venus {
        public static final ResourceKey<Biome> VENUS_VOLCANIC_PLAINS = key("venus_volcanic_plains"); // regional lava plains (default)
        public static final ResourceKey<Biome> VENUS_HIGHLANDS = key("venus_highlands"); // Ishtar/Aphrodite tesserae
        public static final ResourceKey<Biome> VENUS_LAVA_CHANNELS = key("venus_lava_channels"); // Baltis Vallis lava rivers
        public static final ResourceKey<Biome> VENUS_SHIELD_VOLCANO = key("venus_shield_volcano"); // Maat/Sapas Mons
        public static final ResourceKey<Biome> VENUS_SULFUR_FLATS = key("venus_sulfur_flats"); // corrosive lowlands
        public static final ResourceKey<Biome> VENUS_LAVA_SEA = key("venus_lava_sea"); // broad volcanic basins
    }

    public static final class Mars {
        public static final ResourceKey<Biome> MARS = key("mars"); // Vastitas Borealis (dusty lowlands)
        public static final ResourceKey<Biome> MARS_HIGHLANDS = key("mars_highlands");
        public static final ResourceKey<Biome> MARS_DUNES = key("mars_dunes");
        public static final ResourceKey<Biome> MARS_CANYON = key("mars_canyon");
        public static final ResourceKey<Biome> MARS_VOLCANICS = key("mars_volcanics");
        public static final ResourceKey<Biome> MARS_POLAR = key("mars_polar");
    }

    public static final class Mercury {
        public static final ResourceKey<Biome> MERCURY_PLAINS = key("mercury_plains");
        public static final ResourceKey<Biome> MERCURY_HIGHLANDS = key("mercury_highlands");
        public static final ResourceKey<Biome> MERCURY_BASIN = key("mercury_basin");
    }

    public static final class Asteroid {
        public static final ResourceKey<Biome> ASTEROID_FIELD = key("asteroid_field");
        public static final ResourceKey<Biome> CARBONACEOUS_FIELD = key("carbonaceous_asteroid_field");
        public static final ResourceKey<Biome> METALLIC_BELT = key("metallic_asteroid_belt");
        public static final ResourceKey<Biome> FROZEN_CLUSTER = key("frozen_asteroid_cluster");
    }

    public static final ResourceKey<Biome> SPACE = ResourceKey.create(Registries.BIOME, Constant.id("space"));

    public static Biome createSpaceBiome(HolderGetter<PlacedFeature> holderGetter, HolderGetter<ConfiguredWorldCarver<?>> holderGetter2) {
        Biome.BiomeBuilder builder = new Biome.BiomeBuilder();
        MobSpawnSettings.Builder spawns = new MobSpawnSettings.Builder();
        BiomeGenerationSettings.Builder genSettings = new BiomeGenerationSettings.Builder(holderGetter, holderGetter2);
        BiomeSpecialEffects.Builder effects = new BiomeSpecialEffects.Builder();
        effects.fogColor(0).waterColor(4159204).waterFogColor(329011).skyColor(0)
                .ambientMoodSound(AmbientMoodSettings.LEGACY_CAVE_SETTINGS)
                .backgroundMusic(Musics.createGameMusic(GCSounds.MUSIC_ORBIT));

        return builder
                .downfall(0)
                .temperature(1)
                .specialEffects(effects.build())
                .mobSpawnSettings(spawns.build())
                .hasPrecipitation(false)
                .generationSettings(genSettings.build())
                .temperatureAdjustment(Biome.TemperatureModifier.NONE).build();
    }

    public static void bootstrapRegistries(BootstrapContext<Biome> context) { // moj-map typo :(
        HolderGetter<PlacedFeature> featureLookup = context.lookup(Registries.PLACED_FEATURE);
        HolderGetter<ConfiguredWorldCarver<?>> carverLookup = context.lookup(Registries.CONFIGURED_CARVER);
        context.register(SPACE, createSpaceBiome(context.lookup(Registries.PLACED_FEATURE), context.lookup(Registries.CONFIGURED_CARVER)));
        context.register(Moon.COMET_TUNDRA, MoonBiomes.createCometTundra(featureLookup, carverLookup));
        context.register(Moon.BASALTIC_MARE, MoonBiomes.createBasalticMare(featureLookup, carverLookup));
        context.register(Moon.LUNAR_HIGHLANDS, MoonBiomes.createLunarHighlands(featureLookup, carverLookup));
        context.register(Moon.LUNAR_LOWLANDS, MoonBiomes.createLunarLowlands(featureLookup, carverLookup));
        context.register(Moon.OLIVINE_SPIKES, MoonBiomes.createOlivineSpikes(featureLookup, carverLookup));
        context.register(Moon.RAY_CRATER_FIELD, MoonBiomes.createRayCraterField(featureLookup, carverLookup));
        context.register(Moon.CHEESE_GROVE, MoonBiomes.createCheeseGrove(featureLookup, carverLookup));

        context.register(Venus.VENUS_VOLCANIC_PLAINS, VenusBiomes.volcanicPlains(featureLookup, carverLookup));
        context.register(Venus.VENUS_HIGHLANDS, VenusBiomes.highlands(featureLookup, carverLookup));
        context.register(Venus.VENUS_LAVA_CHANNELS, VenusBiomes.lavaChannels(featureLookup, carverLookup));
        context.register(Venus.VENUS_SHIELD_VOLCANO, VenusBiomes.shieldVolcano(featureLookup, carverLookup));
        context.register(Venus.VENUS_SULFUR_FLATS, VenusBiomes.sulfurFlats(featureLookup, carverLookup));
        context.register(Venus.VENUS_LAVA_SEA, VenusBiomes.lavaSea(featureLookup, carverLookup));

        context.register(Mars.MARS, MarsBiomes.vastitas(featureLookup, carverLookup));
        context.register(Mars.MARS_HIGHLANDS, MarsBiomes.highlands(featureLookup, carverLookup));
        context.register(Mars.MARS_DUNES, MarsBiomes.dunes(featureLookup, carverLookup));
        context.register(Mars.MARS_CANYON, MarsBiomes.canyon(featureLookup, carverLookup));
        context.register(Mars.MARS_VOLCANICS, MarsBiomes.volcanics(featureLookup, carverLookup));
        context.register(Mars.MARS_POLAR, MarsBiomes.polar(featureLookup, carverLookup));

        context.register(Mercury.MERCURY_PLAINS, MercuryBiomes.plains(featureLookup, carverLookup));
        context.register(Mercury.MERCURY_HIGHLANDS, MercuryBiomes.highlands(featureLookup, carverLookup));
        context.register(Mercury.MERCURY_BASIN, MercuryBiomes.basin(featureLookup, carverLookup));

        context.register(Asteroid.ASTEROID_FIELD, AsteroidBiomes.asteroid(featureLookup, carverLookup));
        context.register(Asteroid.CARBONACEOUS_FIELD, AsteroidBiomes.carbonaceousField(featureLookup, carverLookup));
        context.register(Asteroid.METALLIC_BELT, AsteroidBiomes.metallicBelt(featureLookup, carverLookup));
        context.register(Asteroid.FROZEN_CLUSTER, AsteroidBiomes.frozenCluster(featureLookup, carverLookup));
    }

    @Contract(pure = true)
    public static @NotNull ResourceKey<Biome> key(String id) {
        return Constant.key(Registries.BIOME, id);
    }
}
