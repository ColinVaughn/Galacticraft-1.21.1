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

package dev.galacticraft.mod.client.resources;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Ensures the Sensor Glasses still ship the exact Galacticraft Legacy art. */
class SensorGlassesAssetsTest {
    private static final Map<String, String> LEGACY_SHA256 = Map.of(
            "misc/sensor_mobs.png", "73fbfb1fe9cc1ab1ad8c649fa3e6667d84c1ccead2cfb409792b718f0d207cd3",
            "gui/sensor_glasses_hud.png", "077be739484320f55a58b1d953f100c14399fb79a43ba67ab134a0efbc58426e",
            "gui/sensor_glasses_indicator.png", "2771ebea9654ebebf8ab135556f6f4b5d1984a5d9e57bc8e6afe05f044407ce7",
            "item/sensor_glasses.png", "1e75eec3eb701022773bb8f7144aeec11945ee071fdfd14cd55812f503e3b204",
            "item/sensor_lens.png", "a81024e9e21b4b28fda66efe8733417978589e7b959ad89f4d427e6b0677641b",
            "models/armor/sensor_glasses_layer_1.png", "164092a486f6c66fc9f22da0659570b572d0151316db4aaecfe0a9396cc927c8"
    );

    @Test
    void sensorGlassesArtMatchesLegacyByteForByte() throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (Map.Entry<String, String> asset : LEGACY_SHA256.entrySet()) {
            String path = "/assets/galacticraft/textures/" + asset.getKey();
            try (InputStream stream = SensorGlassesAssetsTest.class.getResourceAsStream(path)) {
                assertNotNull(stream, "missing Sensor Glasses texture " + path);
                assertEquals(asset.getValue(), java.util.HexFormat.of().formatHex(digest.digest(stream.readAllBytes())),
                        "Sensor Glasses texture no longer matches Galacticraft Legacy: " + path);
            }
        }
    }
}
