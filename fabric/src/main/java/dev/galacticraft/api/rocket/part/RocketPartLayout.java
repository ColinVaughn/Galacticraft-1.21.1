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

package dev.galacticraft.api.rocket.part;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;

/**
 * Part dimensions in model units (16 per block). Parts stack engine, fins, body, cone; boosters
 * share the fin mount. Height places the next part, and the cone top and widest part define the
 * hitbox. Empty values use {@link dev.galacticraft.api.entity.rocket.render.RocketModelSlot} defaults.
 *
 * @param height extent along the rocket axis
 * @param width extent across the rocket
 */
public record RocketPartLayout(Optional<Float> height, Optional<Float> width) {
    public static final RocketPartLayout DEFAULT = new RocketPartLayout(Optional.empty(), Optional.empty());

    public static final Codec<RocketPartLayout> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("height").forGetter(RocketPartLayout::height),
            Codec.FLOAT.optionalFieldOf("width").forGetter(RocketPartLayout::width)
    ).apply(instance, RocketPartLayout::new));

    public static RocketPartLayout height(float height) {
        return new RocketPartLayout(Optional.of(height), Optional.empty());
    }

    public static RocketPartLayout width(float width) {
        return new RocketPartLayout(Optional.empty(), Optional.of(width));
    }
}
