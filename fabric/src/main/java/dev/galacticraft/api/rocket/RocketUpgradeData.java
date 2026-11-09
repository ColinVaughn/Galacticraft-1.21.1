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

package dev.galacticraft.api.rocket;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Persistent per-rocket upgrade data, such as installed chest count. Register its codec during
 * mod initialization and use a namespaced {@linkplain #typeName() type name}, e.g. {@code mymod:extra_tank}.
 *
 * @see dev.galacticraft.api.rocket.part.type.RocketUpgradeType#createData
 */
public interface RocketUpgradeData {
    Codec<RocketUpgradeData> DIRECT_CODEC = Codec.STRING
            .validate(name -> RocketUpgradeDataTypes.TYPES.containsKey(name)
                    ? DataResult.success(name)
                    : DataResult.error(() -> "Unknown rocket upgrade data type: " + name))
            .dispatch("type", RocketUpgradeData::typeName, RocketUpgradeDataTypes.TYPES::get);

    /** The name this data is saved under; must match the name it was registered with. */
    String typeName();

    /**
     * Registers the codec for one kind of upgrade data. Registering the same name again with the
     * same codec is allowed; a different codec under a taken name is an error.
     */
    static void register(String typeName, MapCodec<? extends RocketUpgradeData> codec) {
        MapCodec<? extends RocketUpgradeData> existing = RocketUpgradeDataTypes.TYPES.putIfAbsent(typeName, codec);
        if (existing != null && existing != codec) {
            throw new IllegalStateException("Rocket upgrade data type " + typeName + " is already registered");
        }
    }
}

final class RocketUpgradeDataTypes {
    static final Map<String, MapCodec<? extends RocketUpgradeData>> TYPES = new ConcurrentHashMap<>();

    private RocketUpgradeDataTypes() {
    }
}
