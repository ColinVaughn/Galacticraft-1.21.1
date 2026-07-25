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

package dev.galacticraft.mod.content.block.entity;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static dev.galacticraft.mod.content.block.entity.AirLockProtocol.incompleteFrameHorizontal;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A horizontal airlock is a hatch lying flat in a floor or ceiling. It seals with a flat pane rather
 * than the upright pane used by a doorway, and its frame is a ring in the XZ plane rather than a
 * rectangle standing on edge.
 */
class HorizontalAirlockTest {
    // Negative and positive coordinates on either axis, so sign handling is covered too.
    private static final int MIN_X = -3;
    private static final int MAX_X = 1;
    private static final int MIN_Z = 8;
    private static final int MAX_Z = 12;

    private static final List<String> FACINGS = List.of("north", "south", "east", "west", "up", "down");

    // -- frame validation ---------------------------------------------------

    @Test
    void completeRingSeals() {
        assertFalse(incomplete(ring()));
    }

    @Test
    void gapInNegativeZEdgeIsDetected() {
        // Both edge checks used to read maxZ, so a hole along minZ was never noticed and the hatch sealed anyway.
        assertTrue(incomplete(ringWithout(MIN_X + 1, MIN_Z)));
    }

    @Test
    void gapInNegativeXEdgeIsDetected() {
        // Likewise, both checks in the second loop read maxX, leaving the minX edge unvalidated.
        assertTrue(incomplete(ringWithout(MIN_X, MIN_Z + 1)));
    }

    @Test
    void gapInPositiveZEdgeIsDetected() {
        assertTrue(incomplete(ringWithout(MIN_X + 1, MAX_Z)));
    }

    @Test
    void gapInPositiveXEdgeIsDetected() {
        assertTrue(incomplete(ringWithout(MAX_X, MIN_Z + 1)));
    }

    // -- seal block states --------------------------------------------------

    @Test
    void sealBlockstateCoversEveryFacing() {
        JsonObject variants = sealVariants();
        for (String facing : FACINGS) {
            assertTrue(variants.has("facing=" + facing),
                    "air_lock_seal.json has no variant for facing=" + facing);
        }
        assertEquals(FACINGS.size(), variants.size(), "unexpected variants in air_lock_seal.json");
    }

    @Test
    void everySealVariantPointsAtAnExistingModel() {
        for (Map.Entry<String, com.google.gson.JsonElement> variant : sealVariants().entrySet()) {
            String model = variant.getValue().getAsJsonObject().get("model").getAsString();
            assertTrue(Files.exists(modelPath(model)),
                    variant.getKey() + " references a missing model: " + model);
        }
    }

    @Test
    void horizontalSealVariantsUseAFlatPane() {
        JsonObject variants = sealVariants();
        for (String facing : List.of("up", "down")) {
            JsonObject variant = variants.getAsJsonObject("facing=" + facing);
            String model = variant.get("model").getAsString();

            JsonArray elements = read(modelPath(model)).getAsJsonArray("elements");
            assertEquals(1, elements.size(), model + " should be a single pane");
            JsonObject pane = elements.get(0).getAsJsonObject();
            JsonArray from = pane.getAsJsonArray("from");
            JsonArray to = pane.getAsJsonArray("to");

            // Thin on Y and full on X/Z, otherwise the hatch is filled with upright panes standing in the opening.
            assertTrue(to.get(1).getAsInt() - from.get(1).getAsInt() < 16, model + " should be thin on Y");
            assertEquals(0, from.get(0).getAsInt(), model + " should span the full block on X");
            assertEquals(16, to.get(0).getAsInt(), model + " should span the full block on X");
            assertEquals(0, from.get(2).getAsInt(), model + " should span the full block on Z");
            assertEquals(16, to.get(2).getAsInt(), model + " should span the full block on Z");

            // An x rotation would tip the flat pane back upright.
            assertFalse(variant.has("x"), "facing=" + facing + " must not rotate the flat pane about X");
        }
    }

    // -- helpers ------------------------------------------------------------

    private static boolean incomplete(Set<Long> frame) {
        return incompleteFrameHorizontal(MIN_X, MAX_X, MIN_Z, MAX_Z, (x, z) -> frame.contains(key(x, z)));
    }

    private static Set<Long> ring() {
        Set<Long> frame = new LinkedHashSet<>();
        for (int x = MIN_X; x <= MAX_X; x++) {
            frame.add(key(x, MIN_Z));
            frame.add(key(x, MAX_Z));
        }
        for (int z = MIN_Z; z <= MAX_Z; z++) {
            frame.add(key(MIN_X, z));
            frame.add(key(MAX_X, z));
        }
        return frame;
    }

    private static Set<Long> ringWithout(int x, int z) {
        Set<Long> frame = ring();
        assertTrue(frame.remove(key(x, z)), "(" + x + ", " + z + ") is not on the frame");
        return frame;
    }

    private static long key(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    private static JsonObject sealVariants() {
        return read(ASSETS.resolve("blockstates/air_lock_seal.json")).getAsJsonObject("variants");
    }

    /** Resolves {@code galacticraft:block/air_lock_seal} to its file under the assets root. */
    private static Path modelPath(String model) {
        return ASSETS.resolve("models/" + model.substring(model.indexOf(':') + 1) + ".json");
    }

    private static JsonObject read(Path file) {
        try {
            return JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static final Path ASSETS = repoRoot().resolve("fabric/src/main/resources/assets/galacticraft");

    private static Path repoRoot() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null && Files.notExists(dir.resolve("fabric/src/main/generated"))) {
            dir = dir.getParent();
        }
        if (dir == null) throw new IllegalStateException("could not locate the repository root");
        return dir;
    }
}
