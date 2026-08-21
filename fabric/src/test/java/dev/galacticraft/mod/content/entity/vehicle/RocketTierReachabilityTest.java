/*
 * Copyright (c) 2019-2026 Team Galacticraft
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

package dev.galacticraft.mod.content.entity.vehicle;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.galacticraft.api.rocket.travelpredicate.TravelPredicateType.Result;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the shipped datagen output, so a rocket assembled from tier N parts reaches exactly the
 * celestial bodies whose access weight is at most N.
 *
 * Reads the generated JSON rather than the bootstrap code because the JSON is what the game
 * actually loads.
 */
class RocketTierReachabilityTest {
    private static final Path DATA = repoRoot().resolve("fabric/src/main/generated/data/galacticraft/galacticraft");
    private static final List<String> PART_FOLDERS = List.of("rocket_cone", "rocket_body", "rocket_fin", "rocket_engine");

    @Test
    void tierOnePartsReachOnlyTheMoon() {
        assertReaches(1, Map.of("moon", true, "mars", false, "venus", false, "mercury", false, "asteroid", false));
    }

    @Test
    void tierTwoPartsReachMarsAndVenusButNotTierThreeBodies() {
        assertReaches(2, Map.of("moon", true, "mars", true, "venus", true, "mercury", false, "asteroid", false));
    }

    @Test
    void tierThreePartsReachEveryLandableBody() {
        assertReaches(3, Map.of("moon", true, "mars", true, "venus", true, "mercury", true, "asteroid", true));
    }

    @Test
    void everyTierHasACompleteSetOfParts() {
        for (int tier = 1; tier <= 3; tier++) {
            for (String folder : PART_FOLDERS) {
                Path part = DATA.resolve(folder).resolve("tier_" + tier + ".json");
                assertTrue(Files.exists(part), "missing " + part);
                assertEquals(tier, accessWeight(part), folder + "/tier_" + tier + " has the wrong access weight");
            }
        }
    }

    /**
     * Boosters are optional and BLOCK beats ALLOW, so a booster whose weight is below its rocket's
     * tier would downgrade the whole rocket. Every tier therefore needs a booster of its own.
     */
    @Test
    void everyTierHasABoosterThatDoesNotDowngradeItsRocket() {
        Map<String, Integer> bodies = bodyAccessWeights();
        for (int tier = 1; tier <= 3; tier++) {
            Path booster = DATA.resolve("rocket_booster/tier_" + tier + ".json");
            assertTrue(Files.exists(booster), "missing " + booster);
            assertEquals(tier, accessWeight(booster), "rocket_booster/tier_" + tier + " has the wrong access weight");
        }

        int tierThreeBooster = accessWeight(DATA.resolve("rocket_booster/tier_3.json"));
        for (String body : List.of("mercury", "asteroid")) {
            assertEquals(Result.ALLOW, partResult(tierThreeBooster, bodies.get(body)),
                    "the tier 3 booster must not block " + body);
        }
    }

    /** The advanced cone is built from tier 2 plating, so it must gate at tier 2, not tier 1. */
    @Test
    void cosmeticConesGateAtTheTierOfTheirMaterials() {
        assertEquals(1, accessWeight(DATA.resolve("rocket_cone/sloped_cone.json")), "sloped cone uses tier 1 plating");
        assertEquals(2, accessWeight(DATA.resolve("rocket_cone/advanced_cone.json")), "advanced cone uses tier 2 plating");
    }

    private static void assertReaches(int tier, Map<String, Boolean> expected) {
        Map<String, Integer> bodies = bodyAccessWeights();
        expected.forEach((body, reachable) -> {
            Integer destination = bodies.get(body);
            assertTrue(destination != null, "no celestial body data for " + body);

            Result merged = Result.PASS;
            for (String folder : PART_FOLDERS) {
                merged = merged.merge(partResult(accessWeight(DATA.resolve(folder).resolve("tier_" + tier + ".json")), destination));
            }

            assertEquals(reachable ? Result.ALLOW : Result.BLOCK, merged,
                    "tier " + tier + " rocket -> " + body + " (access weight " + destination + ")");
        });
    }

    /** Mirrors {@code AccessWeightTravelPredicateType#canTravel} for a single part. */
    private static Result partResult(int partWeight, int destinationWeight) {
        return destinationWeight <= partWeight ? Result.ALLOW : Result.BLOCK;
    }

    private static int accessWeight(Path partFile) {
        JsonObject predicate = read(partFile).getAsJsonObject("config").getAsJsonObject("predicate");
        assertEquals("galacticraft:access_weight", predicate.get("type").getAsString(),
                partFile + " no longer uses an access weight predicate");
        return predicate.getAsJsonObject("config").get("weight").getAsInt();
    }

    private static Map<String, Integer> bodyAccessWeights() {
        Map<String, Integer> weights = new LinkedHashMap<>();
        try (Stream<Path> files = Files.list(DATA.resolve("celestial_body"))) {
            for (Path file : files.toList()) {
                JsonObject config = read(file).getAsJsonObject("config");
                if (config != null && config.has("access_weight")) {
                    String name = file.getFileName().toString().replace(".json", "");
                    weights.put(name, config.get("access_weight").getAsInt());
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return weights;
    }

    private static JsonObject read(Path file) {
        try {
            return JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Path repoRoot() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null && Files.notExists(dir.resolve("fabric/src/main/generated"))) {
            dir = dir.getParent();
        }
        if (dir == null) throw new IllegalStateException("could not locate the repository root");
        return dir;
    }
}
