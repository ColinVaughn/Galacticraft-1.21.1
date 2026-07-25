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

package dev.galacticraft.mod.content.rocket.part.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * How many chests were installed in the rocket workbench when this rocket was built.
 *
 * <p>The registered storage upgrade only carries the <em>maximum</em> chest count, so the
 * per-rocket count has to travel with the rocket itself; without it every rocket built with
 * the storage upgrade would end up the same size.</p>
 */
public record StorageRocketData(
        int chests
) implements RocketUpgradeData {

    public static final String TYPE_NAME = "storage";

    public static final MapCodec<StorageRocketData> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.INT.fieldOf("chests").forGetter(StorageRocketData::chests)
    ).apply(instance, StorageRocketData::new));

    public static final Codec<StorageRocketData> CODEC = MAP_CODEC.codec();

    @Override
    public String typeName() {
        return TYPE_NAME;
    }
}
