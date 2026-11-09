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

package dev.galacticraft.mod;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks mixin parity between loaders, catching missing NeoForge registrations that compilation
 * cannot detect, including accessors that would fail only at runtime.
 */
class MixinConfigParityTest {
    /** Mixins deliberately left out of NeoForge: excluded from its build, or replaced by a Neo* counterpart. */
    private static final Set<String> FABRIC_ONLY = Set.of(
            "ModelProviderMixin",
            "client.AbstractContainerScreenMixin",
            "client.BlockModelMixin",
            "client.BlockModelMixin$DeserializerMixin",
            "client.MinecraftMixin",
            // Known NeoForge omission; retain it here while guarding other registrations.
            "client.ClientLevelDataMixin");

    @Test
    void everyGalacticraftMixinIsAlsoListedForNeoForge() throws IOException {
        assertParity("fabric/src/main/resources/galacticraft.mixins.json", "neoforge/src/main/resources/galacticraft-neoforge.mixins.json");
    }

    @Test
    void everyApiMixinIsAlsoListedForNeoForge() throws IOException {
        assertParity("fabric/src/main/resources/galacticraft-api.mixins.json", "neoforge/src/main/resources/galacticraft-api-neoforge.mixins.json");
    }

    private static void assertParity(String fabricPath, String neoForgePath) throws IOException {
        Path root = repositoryRoot();
        Set<String> fabric = entries(root.resolve(fabricPath));
        Set<String> neoForge = entries(root.resolve(neoForgePath));
        List<String> missing = new ArrayList<>();
        for (String mixin : fabric) {
            if (!neoForge.contains(mixin) && !FABRIC_ONLY.contains(mixin.substring(mixin.indexOf(':') + 1))) missing.add(mixin);
        }
        assertTrue(missing.isEmpty(), "listed for Fabric but not in " + neoForgePath + ": " + missing);
    }

    /** Each entry as {@code side:name}, so a mixin listed under the wrong side counts as missing. */
    private static Set<String> entries(Path config) throws IOException {
        Set<String> entries = new TreeSet<>();
        try (Reader reader = Files.newBufferedReader(config)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            for (String side : List.of("mixins", "client", "server")) {
                JsonArray list = json.getAsJsonArray(side);
                if (list == null) continue;
                for (JsonElement entry : list) entries.add(side + ":" + entry.getAsString());
            }
        }
        return entries;
    }

    private static Path repositoryRoot() {
        for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
            if (Files.isDirectory(dir.resolve("neoforge/src/main/resources")) && Files.isDirectory(dir.resolve("fabric/src/main/resources"))) {
                return dir;
            }
        }
        throw new IllegalStateException("Could not find the repository root from " + Path.of("").toAbsolutePath());
    }
}
