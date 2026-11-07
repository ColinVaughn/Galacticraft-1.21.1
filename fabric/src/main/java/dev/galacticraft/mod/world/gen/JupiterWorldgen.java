package dev.galacticraft.mod.world.gen;

import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.content.GCBlocks;
import dev.galacticraft.mod.world.gen.feature.GCPlacedFeatures;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.Noises;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

import java.util.List;

/** Permanent settlement banks above a navigable lower atmosphere of sparse suspended formations. */
public final class JupiterWorldgen {
    public static final int MIN_Y = -64;
    public static final int HEIGHT = 384;
    public static final int CLOUD_CENTER = 222;
    /** The White Zones: broad settlement banks. Keeps its original id so existing worlds keep their biome. */
    public static final ResourceKey<Biome> CLOUDLANDS = Constant.key(Registries.BIOME, "jupiter_cloudlands");
    /** Broken banks, stronger currents and more salvage. */
    public static final ResourceKey<Biome> AMBER_BELTS = Constant.key(Registries.BIOME, "jupiter_amber_belts");
    /** Resource-rich shallow formations along every zone/belt edge. */
    public static final ResourceKey<Biome> CRYSTAL_FRONTS = Constant.key(Registries.BIOME, "jupiter_crystal_fronts");
    /** Occasional calm refuges inside the belts. */
    public static final ResourceKey<Biome> CALM_EYE = Constant.key(Registries.BIOME, "jupiter_calm_eye");
    /** Endgame storm wells holding the Stormheart citadels. */
    public static final ResourceKey<Biome> RED_VORTEX = Constant.key(Registries.BIOME, "jupiter_red_vortex");
    public static final List<ResourceKey<Biome>> BIOMES = List.of(CLOUDLANDS, AMBER_BELTS, CRYSTAL_FRONTS, CALM_EYE, RED_VORTEX);

    private JupiterWorldgen() {
    }

    /** Ammonia Veil shelves: shallow formations for the first suited expeditions. */
    public static final int VEIL_CENTER = 178;
    /** Storm Belt shards: broken, charged formations reached by Cloud Diver. */
    public static final int STORM_CENTER = 104;
    /** Deep Tempest anchors: rare ancient shelves around the facilities. */
    public static final int DEEP_CENTER = 18;
    /** Nothing generates below the top of the Crushing Depths. */
    public static final int CRUSHING_TOP = -33;

    public static NoiseGeneratorSettings settings(HolderGetter<NormalNoise.NoiseParameters> noises) {
        SurfaceRules.RuleSource surfaceRules = SurfaceRules.sequence(
                SurfaceRules.ifTrue(SurfaceRules.ON_FLOOR,
                        SurfaceRules.ifTrue(SurfaceRules.yBlockCheck(VerticalAnchor.absolute(200), 0),
                                SurfaceRules.ifTrue(SurfaceRules.noiseCondition(Noises.SURFACE, 0.35),
                                        SurfaceRules.state(GCBlocks.AMMONIA_FROST.defaultBlockState())))),
                SurfaceRules.state(GCBlocks.DENSE_CLOUD.defaultBlockState()));

        return new NoiseGeneratorSettings(NoiseSettings.create(MIN_Y, HEIGHT, 1, 1),
                GCBlocks.DENSE_CLOUD.defaultBlockState(), Blocks.AIR.defaultBlockState(), router(noises),
                surfaceRules, List.of(), MIN_Y, true, false, false, false);
    }

    /** Settlement banks plus sparse suspended formations in each expedition layer; most of the volume stays air. */
    public static NoiseRouter router(HolderGetter<NormalNoise.NoiseParameters> noises) {
        DensityFunction banks = DensityFunctions.noise(noises.getOrThrow(Noises.CONTINENTALNESS), 2.5, 0.0);
        DensityFunction surface = DensityFunctions.noise(noises.getOrThrow(Noises.SURFACE), 0.65, 0.0);
        DensityFunction drift = DensityFunctions.noise(noises.getOrThrow(Noises.SURFACE_SECONDARY), 0.8, 0.0);
        DensityFunction billows = DensityFunctions.noise(noises.getOrThrow(Noises.GRAVEL), 0.65, 0.8);

        DensityFunction band = bands(noises);
        DensityFunction vortex = vortex(noises);
        DensityFunction beltness = DensityFunctions.mul(DensityFunctions.add(band, DensityFunctions.constant(-RED_VORTEX_BAND)),
                DensityFunctions.constant(4.0)).clamp(0.0, 1.0);
        DensityFunction storm = DensityFunctions.mul(DensityFunctions.add(DensityFunctions.constant(STORM_REGION), vortex),
                DensityFunctions.constant(-6.0)).clamp(0.0, 1.0);
        DensityFunction eye = DensityFunctions.mul(DensityFunctions.add(vortex, DensityFunctions.constant(-STORM_REGION)),
                DensityFunctions.constant(6.0)).clamp(0.0, 1.0);

        // Negative thickness leaves real descent channels; positive thickness tapers into banks. White Zones carry
        // broad banks and Amber Belts broken ones; a Red Vortex opens a storm well and a Calm Eye gathers a refuge bank.
        DensityFunction thickness = DensityFunctions.add(DensityFunctions.constant(5.0),
                DensityFunctions.mul(banks, DensityFunctions.constant(32.0))).clamp(-16.0, 13.0);
        thickness = DensityFunctions.add(thickness, DensityFunctions.add(
                DensityFunctions.mul(band, DensityFunctions.constant(-3.0)),
                DensityFunctions.add(DensityFunctions.mul(DensityFunctions.mul(storm, beltness), DensityFunctions.constant(-22.0)),
                        DensityFunctions.mul(eye, DensityFunctions.constant(9.0))))).clamp(-28.0, 15.0);
        DensityFunction altitude = DensityFunctions.add(height(CLOUD_CENTER),
                DensityFunctions.mul(surface, DensityFunctions.constant(7.0)));
        DensityFunction density = DensityFunctions.add(thickness, DensityFunctions.add(
                DensityFunctions.mul(altitude.abs(), DensityFunctions.constant(-1.0)),
                DensityFunctions.mul(billows, DensityFunctions.constant(1.5))));

        // Each layer reads its own noise, so formations are not stacked under the banks or each other.
        density = DensityFunctions.max(density, formation(noises.getOrThrow(Noises.EROSION), 4.0, FormationShape.VEIL,
                VEIL_CENTER, surface, billows));
        density = DensityFunctions.max(density, formation(noises.getOrThrow(Noises.RIDGE), 1.6, FormationShape.STORM,
                STORM_CENTER, drift, billows));
        density = DensityFunctions.max(density, formation(noises.getOrThrow(Noises.VEGETATION), 2.2, FormationShape.DEEP,
                DEEP_CENTER, drift, billows));

        // Biomes read the bands as temperature and the storm regions as vegetation.
        DensityFunction zero = DensityFunctions.zero();
        return new NoiseRouter(zero, zero, zero, zero, DensityFunctions.flatCache(band), DensityFunctions.flatCache(vortex),
                zero, zero, zero, zero, density, DensityFunctions.interpolated(density), zero, zero, zero);
    }

    /** Band value at which a belt can hold a Red Vortex; Crystal Fronts and Amber Belts begin at the other edges. */
    public static final double RED_VORTEX_BAND = 0.5, FRONT_BAND = -0.3, BELT_BAND = 0.2;
    /** Storm-region noise beyond which a belt holds a Red Vortex (below minus) or a Calm Eye (above plus). */
    public static final double STORM_REGION = 0.55;
    /** Same period as the sky haze and storm activity. */
    public static final double BAND_PERIOD = 2048.0;

    private static DensityFunction bands(HolderGetter<NormalNoise.NoiseParameters> noises) {
        return new JupiterBands(DensityFunctions.noise(noises.getOrThrow(Noises.ICE), 0.5, 0.0), BAND_PERIOD, 160.0);
    }

    private static DensityFunction vortex(HolderGetter<NormalNoise.NoiseParameters> noises) {
        return DensityFunctions.noise(noises.getOrThrow(Noises.TEMPERATURE), 0.6, 0.0);
    }

    /** Broad warped bands rather than biome blobs: temperature is the band value, vegetation the storm region. */
    public static Climate.ParameterList<ResourceKey<Biome>> biomeParameters() {
        Climate.Parameter any = Climate.Parameter.span(-1.0F, 1.0F);
        Climate.Parameter zone = Climate.Parameter.span(-1.0F, (float) FRONT_BAND);
        Climate.Parameter front = Climate.Parameter.span((float) FRONT_BAND, (float) BELT_BAND);
        Climate.Parameter belt = Climate.Parameter.span((float) BELT_BAND, 1.0F);
        Climate.Parameter calm = Climate.Parameter.span((float) STORM_REGION, 1.0F);
        Climate.Parameter stormy = Climate.Parameter.span(-1.0F, (float) -STORM_REGION);
        Climate.Parameter ordinary = Climate.Parameter.span((float) -STORM_REGION, (float) STORM_REGION);
        return new Climate.ParameterList<>(List.of(
                biome(zone, any, CLOUDLANDS),
                biome(front, any, CRYSTAL_FRONTS),
                biome(belt, ordinary, AMBER_BELTS),
                biome(Climate.Parameter.span((float) BELT_BAND, (float) RED_VORTEX_BAND), stormy, AMBER_BELTS),
                biome(Climate.Parameter.span((float) RED_VORTEX_BAND, 1.0F), stormy, RED_VORTEX),
                biome(belt, calm, CALM_EYE)));
    }

    private static com.mojang.datafixers.util.Pair<Climate.ParameterPoint, ResourceKey<Biome>> biome(
            Climate.Parameter band, Climate.Parameter storm, ResourceKey<Biome> biome) {
        Climate.Parameter point = Climate.Parameter.point(0.0F);
        return com.mojang.datafixers.util.Pair.of(Climate.parameters(band, storm, point, point, point, point, 0.0F), biome);
    }

    /**
     * Footprint threshold, footprint steepness, peak half-thickness and vertical jitter of a formation layer.
     * Tops are flatter than the hanging, rounded undersides so formations read as suspended rather than grounded.
     */
    enum FormationShape {
        VEIL(0.45, 40.0, 6.0, 5.0),
        STORM(0.46, 36.0, 7.0, 14.0),
        DEEP(0.40, 30.0, 9.0, 8.0);

        final double threshold, steepness, halfThickness, jitter;

        FormationShape(double threshold, double steepness, double halfThickness, double jitter) {
            this.threshold = threshold;
            this.steepness = steepness;
            this.halfThickness = halfThickness;
            this.jitter = jitter;
        }
    }

    private static DensityFunction formation(net.minecraft.core.Holder<NormalNoise.NoiseParameters> footprintNoise, double scale,
                                             FormationShape shape, int center, DensityFunction jitter, DensityFunction billows) {
        DensityFunction footprint = DensityFunctions.noise(footprintNoise, scale, 0.0);
        DensityFunction thickness = DensityFunctions.mul(
                DensityFunctions.add(footprint, DensityFunctions.constant(-shape.threshold)),
                DensityFunctions.constant(shape.steepness)).clamp(-24.0, shape.halfThickness);
        DensityFunction offset = DensityFunctions.add(height(center),
                DensityFunctions.mul(jitter, DensityFunctions.constant(shape.jitter)));
        // |d| + d/2 weighs the top at 1.5x and the underside at 0.5x.
        DensityFunction profile = DensityFunctions.add(offset.abs(), DensityFunctions.mul(offset, DensityFunctions.constant(0.5)));
        return DensityFunctions.add(thickness, DensityFunctions.add(
                DensityFunctions.mul(profile, DensityFunctions.constant(-1.0)),
                DensityFunctions.mul(billows, DensityFunctions.constant(1.5))));
    }

    /** Signed height above {@code center}. */
    private static DensityFunction height(int center) {
        return DensityFunctions.yClampedGradient(MIN_Y, MIN_Y + HEIGHT, MIN_Y - center, MIN_Y + HEIGHT - center);
    }

    /** Every Jupiter feature in one fixed order; each biome takes a subset, which keeps vanilla's feature sort acyclic. */
    private static final List<ResourceKey<PlacedFeature>> STRUCTURE_ORDER = List.of(GCPlacedFeatures.STORMHEART_CITADEL,
            GCPlacedFeatures.JUPITER_RESEARCH_FACILITY, GCPlacedFeatures.JUPITER_WEATHER_STATION, GCPlacedFeatures.JUPITER_WEATHER_STATION_REFUGE,
            GCPlacedFeatures.JUPITER_STORM_HARVESTER, GCPlacedFeatures.JUPITER_STORM_HARVESTER_DENSE, GCPlacedFeatures.JUPITER_SURVEY_WRECK);
    private static final List<ResourceKey<PlacedFeature>> DECORATION_ORDER = List.of(GCPlacedFeatures.JUPITER_HYDROSULFIDE_CRYSTALS,
            GCPlacedFeatures.JUPITER_HYDROSULFIDE_FRONT, GCPlacedFeatures.JUPITER_CHARGED_CONDENSATE);

    public static Biome biome(ResourceKey<Biome> key, HolderGetter<PlacedFeature> features,
                              HolderGetter<ConfiguredWorldCarver<?>> carvers) {
        List<ResourceKey<PlacedFeature>> placed;
        int fog, sky;
        if (key == AMBER_BELTS) {
            placed = List.of(GCPlacedFeatures.JUPITER_RESEARCH_FACILITY, GCPlacedFeatures.JUPITER_STORM_HARVESTER_DENSE,
                    GCPlacedFeatures.JUPITER_SURVEY_WRECK, GCPlacedFeatures.JUPITER_HYDROSULFIDE_CRYSTALS, GCPlacedFeatures.JUPITER_CHARGED_CONDENSATE);
            fog = 0xC08A5A;
            sky = 0xA87D58;
        } else if (key == CRYSTAL_FRONTS) {
            placed = List.of(GCPlacedFeatures.JUPITER_STORM_HARVESTER, GCPlacedFeatures.JUPITER_SURVEY_WRECK,
                    GCPlacedFeatures.JUPITER_HYDROSULFIDE_CRYSTALS, GCPlacedFeatures.JUPITER_HYDROSULFIDE_FRONT, GCPlacedFeatures.JUPITER_CHARGED_CONDENSATE);
            fog = 0xD3BFA0;
            sky = 0xB9AC96;
        } else if (key == CALM_EYE) {
            placed = List.of(GCPlacedFeatures.JUPITER_WEATHER_STATION_REFUGE, GCPlacedFeatures.JUPITER_SURVEY_WRECK,
                    GCPlacedFeatures.JUPITER_HYDROSULFIDE_CRYSTALS, GCPlacedFeatures.JUPITER_CHARGED_CONDENSATE);
            fog = 0xE2CFB0;
            sky = 0xCDBDA2;
        } else if (key == RED_VORTEX) {
            placed = List.of(GCPlacedFeatures.STORMHEART_CITADEL, GCPlacedFeatures.JUPITER_RESEARCH_FACILITY,
                    GCPlacedFeatures.JUPITER_STORM_HARVESTER_DENSE, GCPlacedFeatures.JUPITER_HYDROSULFIDE_CRYSTALS, GCPlacedFeatures.JUPITER_CHARGED_CONDENSATE);
            fog = 0xA24E34;
            sky = 0x8C4A36;
        } else {
            placed = List.of(GCPlacedFeatures.JUPITER_WEATHER_STATION, GCPlacedFeatures.JUPITER_STORM_HARVESTER,
                    GCPlacedFeatures.JUPITER_SURVEY_WRECK, GCPlacedFeatures.JUPITER_HYDROSULFIDE_CRYSTALS, GCPlacedFeatures.JUPITER_CHARGED_CONDENSATE);
            fog = 0xD7B994;
            sky = 0xBCA88D;
        }
        BiomeGenerationSettings.Builder generation = new BiomeGenerationSettings.Builder(features, carvers);
        for (var feature : STRUCTURE_ORDER) {
            if (placed.contains(feature)) generation.addFeature(GenerationStep.Decoration.SURFACE_STRUCTURES, feature);
        }
        for (var feature : DECORATION_ORDER) {
            if (placed.contains(feature)) generation.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION, feature);
        }
        return new Biome.BiomeBuilder()
                .hasPrecipitation(false).temperature(0.2F).downfall(0.0F)
                .specialEffects(new BiomeSpecialEffects.Builder()
                        .fogColor(fog).skyColor(sky)
                        .waterColor(0x9AAEB8).waterFogColor(0x697E89).build())
                .mobSpawnSettings(new MobSpawnSettings.Builder().build())
                .generationSettings(generation.build())
                .build();
    }
}