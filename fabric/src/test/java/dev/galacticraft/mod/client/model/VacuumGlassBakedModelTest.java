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

package dev.galacticraft.mod.client.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.lang.reflect.Proxy;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VacuumGlassBakedModelTest {
    @Test void cornerQuadsMatchThePreviousGeometryForEveryConnectionCombination() throws Exception {
        // The fixture records the pre-refactor emitter calls, including vertex winding and UV order.
        try (var reader = new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream("/fixtures/vacuum-glass-corner-quads.json")))) {
            var expected = JsonParser.parseReader(reader).getAsJsonArray();
            var emit = VacuumGlassBakedModel.class.getDeclaredMethod("emitCornerPane", QuadEmitter.class, boolean.class, boolean.class, boolean.class, boolean.class);
            emit.setAccessible(true);
            for (int mask = 0; mask < 16; mask++) {
                var calls = new JsonArray();
                var emitter = (QuadEmitter) Proxy.newProxyInstance(QuadEmitter.class.getClassLoader(), new Class<?>[]{QuadEmitter.class}, (proxy, method, arguments) -> {
                    var call = new JsonArray();
                    call.add(method.getName());
                    if (arguments != null) for (Object argument : arguments) call.add(((Number) argument).floatValue());
                    calls.add(call);
                    return proxy;
                });
                emit.invoke(null, emitter, (mask & 1) != 0, (mask & 2) != 0, (mask & 4) != 0, (mask & 8) != 0);
                assertEquals(expected.get(mask), calls, "Connection mask " + mask);
            }
        }
    }
}
