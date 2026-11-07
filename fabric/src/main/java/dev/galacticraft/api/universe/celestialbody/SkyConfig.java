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

package dev.galacticraft.api.universe.celestialbody;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/**
 * How a celestial body's sky looks from its surface. Used by the {@code galacticraft:space}
 * dimension effects, which any dimension type can name in its {@code "effects"} field to get a
 * space sky: the body's star, the other bodies of its system drawn from the celestial registry, and
 * a starfield, all with no client code.
 *
 * @param skyColor       RGB sky colour at noon; 0 (black) for an airless body
 * @param fogColor       RGB fog colour at noon
 * @param sunSize        apparent size of the star, as a half-width at 100 blocks (the Moon's is 2)
 * @param starBrightness 0 to 1; airless bodies show stars by day, others only at night
 * @param postEffect     an optional full-screen post effect, for example
 *                       {@code galacticraft:shaders/post/mars.json}
 */
public record SkyConfig(int skyColor, int fogColor, float sunSize, float starBrightness, Optional<ResourceLocation> postEffect) {
    public static final SkyConfig DEFAULT = new SkyConfig(0x000000, 0x000000, 2.0F, 1.0F, Optional.empty());

    public static final Codec<SkyConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("sky_color", DEFAULT.skyColor()).forGetter(SkyConfig::skyColor),
            Codec.INT.optionalFieldOf("fog_color", DEFAULT.fogColor()).forGetter(SkyConfig::fogColor),
            Codec.FLOAT.optionalFieldOf("sun_size", DEFAULT.sunSize()).forGetter(SkyConfig::sunSize),
            Codec.floatRange(0.0F, 1.0F).optionalFieldOf("star_brightness", DEFAULT.starBrightness()).forGetter(SkyConfig::starBrightness),
            ResourceLocation.CODEC.optionalFieldOf("post_effect").forGetter(SkyConfig::postEffect)
    ).apply(instance, SkyConfig::new));

    /** Whether the sky is black, as on an airless body. */
    public boolean airless() {
        return (this.skyColor & 0xFFFFFF) == 0;
    }
}
