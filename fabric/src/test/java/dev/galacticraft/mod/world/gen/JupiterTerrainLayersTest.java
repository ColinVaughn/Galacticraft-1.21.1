/*
 * Copyright (c) 2026 Colin Vaughn
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

package dev.galacticraft.mod.world.gen;

import net.minecraft.SharedConstants;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.SurfaceRules;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** The lower atmosphere holds sparse suspended formations in each expedition layer, never a floor. */
class JupiterTerrainLayersTest {
    private static final int SPAN = 2048, STEP = 24;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void everyExpeditionLayerHasSparseFormationsAndOpenDescentShafts() {
        var noises = VanillaRegistries.createLookup().lookupOrThrow(Registries.NOISE);
        var settings = new NoiseGeneratorSettings(NoiseSettings.create(JupiterWorldgen.MIN_Y, JupiterWorldgen.HEIGHT, 1, 1),
                Blocks.STONE.defaultBlockState(), Blocks.AIR.defaultBlockState(), JupiterWorldgen.router(noises),
                SurfaceRules.state(Blocks.STONE.defaultBlockState()), List.of(), JupiterWorldgen.MIN_Y, true, false, false, false);
        int[][] layers = {{JupiterWorldgen.VEIL_CENTER, 144, 207}, {JupiterWorldgen.STORM_CENTER, 64, 143}, {JupiterWorldgen.DEEP_CENTER, -32, 63}};
        for (long seed : new long[]{23L, 8675309L, -140153L}) {
            DensityFunction density = RandomState.create(settings, noises, seed).router().finalDensity();
            int columns = 0, shafts = 0;
            int[] hits = new int[layers.length], thickness = new int[layers.length];
            for (int x = -SPAN; x < SPAN; x += STEP) {
                for (int z = -SPAN; z < SPAN; z += STEP) {
                    columns++;
                    boolean clear = true;
                    for (int i = 0; i < layers.length; i++) {
                        int solid = 0;
                        for (int y = layers[i][1]; y <= layers[i][2]; y++) {
                            if (solid(density, x, y, z)) solid++;
                        }
                        if (solid > 0) {
                            hits[i]++;
                            thickness[i] += solid;
                            clear = false;
                        }
                    }
                    if (clear) shafts++;
                    for (int y = JupiterWorldgen.MIN_Y; y <= JupiterWorldgen.CRUSHING_TOP; y += 3) {
                        assertTrue(!solid(density, x, y, z), "Terrain in the Crushing Depths at " + x + "," + y + "," + z);
                    }
                    for (int y = 252; y < JupiterWorldgen.MIN_Y + JupiterWorldgen.HEIGHT; y += 6) {
                        assertTrue(!solid(density, x, y, z), "Terrain in the upper sky at " + x + "," + y + "," + z);
                    }
                }
            }
            for (int i = 0; i < layers.length; i++) {
                double coverage = (double) hits[i] / columns;
                System.out.printf("seed %d layer %d: coverage %.3f, mean thickness %.1f%n", seed, layers[i][0], coverage,
                        hits[i] == 0 ? 0.0 : (double) thickness[i] / hits[i]);
                assertTrue(coverage > 0.03 && coverage < 0.18, "Layer " + layers[i][0] + " coverage " + coverage);
            }
            double open = (double) shafts / columns;
            System.out.printf("seed %d: open shafts below the banks %.3f%n", seed, open);
            assertTrue(open > 0.6, "Lower layers block descent: " + open);
        }
    }

    @Test
    void bandBiomesFollowTheCloudBandsAndShapeTheBanks() {
        var noises = VanillaRegistries.createLookup().lookupOrThrow(Registries.NOISE);
        var settings = new NoiseGeneratorSettings(NoiseSettings.create(JupiterWorldgen.MIN_Y, JupiterWorldgen.HEIGHT, 1, 1),
                Blocks.STONE.defaultBlockState(), Blocks.AIR.defaultBlockState(), JupiterWorldgen.router(noises),
                SurfaceRules.state(Blocks.STONE.defaultBlockState()), List.of(), JupiterWorldgen.MIN_Y, true, false, false, false);
        var parameters = JupiterWorldgen.biomeParameters();
        for (long seed : new long[]{23L, 8675309L}) {
            var router = RandomState.create(settings, noises, seed).router();
            Map<ResourceKey<Biome>, int[]> stats = new HashMap<>(); // samples, bank columns
            int zoneAtZoneCentre = 0, beltAtBeltCentre = 0, centres = 0;
            for (int x = -8192; x < 8192; x += 64) {
                for (int z = -8192; z < 8192; z += 32) {
                    var point = new DensityFunction.SinglePointContext(x, JupiterWorldgen.CLOUD_CENTER, z);
                    var biome = parameters.findValue(Climate.target((float) router.temperature().compute(point),
                            (float) router.vegetation().compute(point), 0, 0, 0, 0));
                    int[] entry = stats.computeIfAbsent(biome, key -> new int[2]);
                    entry[0]++;
                    if (bank(router.finalDensity(), x, z)) entry[1]++;
                    int phase = Math.floorMod(z, 2048);
                    if (phase == 0) {
                        centres++;
                        if (biome != JupiterWorldgen.CLOUDLANDS && biome != JupiterWorldgen.CRYSTAL_FRONTS) beltAtBeltCentre++;
                    } else if (phase == 1024 && biome == JupiterWorldgen.CLOUDLANDS) zoneAtZoneCentre++;
                }
            }
            int total = stats.values().stream().mapToInt(e -> e[0]).sum();
            for (var biome : JupiterWorldgen.BIOMES) {
                int[] entry = stats.getOrDefault(biome, new int[2]);
                System.out.printf("seed %d %s: %.3f of area, bank coverage %.3f%n", seed, biome.location().getPath(),
                        (double) entry[0] / total, entry[0] == 0 ? 0.0 : (double) entry[1] / entry[0]);
                assertTrue(entry[0] > 0, "Biome never generates: " + biome.location());
            }
            assertTrue(zoneAtZoneCentre > centres * 0.9 && beltAtBeltCentre > centres * 0.9,
                    "Biome bands drift from the cloud bands: " + zoneAtZoneCentre + "/" + beltAtBeltCentre + " of " + centres);
            double vortex = (double) stats.get(JupiterWorldgen.RED_VORTEX)[0] / total;
            assertTrue(vortex > 0.005 && vortex < 0.08, "Red Vortices should be rare endgame regions: " + vortex);
            double zoneBanks = coverage(stats, JupiterWorldgen.CLOUDLANDS), beltBanks = coverage(stats, JupiterWorldgen.AMBER_BELTS);
            assertTrue(zoneBanks > beltBanks, "White Zones should carry broader banks than Amber Belts");
            assertTrue(coverage(stats, JupiterWorldgen.RED_VORTEX) < beltBanks * 0.6, "A Red Vortex should open a storm well");
            assertTrue(coverage(stats, JupiterWorldgen.CALM_EYE) > beltBanks, "A Calm Eye should gather a refuge bank");
        }
    }

    private static double coverage(Map<ResourceKey<Biome>, int[]> stats, ResourceKey<Biome> biome) {
        int[] entry = stats.get(biome);
        return (double) entry[1] / entry[0];
    }

    /** Any settlement-bank cloud in this column. */
    private static boolean bank(DensityFunction density, int x, int z) {
        for (int y = 200; y <= 245; y += 3) {
            if (solid(density, x, y, z)) return true;
        }
        return false;
    }

    private static boolean solid(DensityFunction density, int x, int y, int z) {
        return density.compute(new DensityFunction.SinglePointContext(x, y, z)) > 0.0;
    }
}
