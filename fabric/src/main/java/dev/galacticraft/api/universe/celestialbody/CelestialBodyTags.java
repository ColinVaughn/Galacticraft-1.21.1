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

import dev.galacticraft.api.registry.AddonRegistries;
import dev.galacticraft.mod.Constant;
import net.minecraft.core.Holder;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Planetary hazard tags. Add celestial body IDs to the matching Galacticraft tag, for example
 * {@code data/galacticraft/tags/galacticraft/celestial_body/dust_storms.json}.
 */
public final class CelestialBodyTags {
    /** Periodic dust storms that cut visibility and sandblast unprotected players (stock: Mars). */
    public static final TagKey<CelestialBody<?, ?>> DUST_STORMS = key("dust_storms");

    /** Solar flares that scorch anyone caught in the open (stock: Mercury). */
    public static final TagKey<CelestialBody<?, ?>> SOLAR_FLARES = key("solar_flares");

    /**
     * A gas giant: no solid surface, pressure and temperature that climb with depth, an entry
     * shield needed to descend, thick cloud fog and volumetric clouds (stock: Jupiter).
     */
    public static final TagKey<CelestialBody<?, ?>> GAS_GIANT = key("gas_giant");

    private CelestialBodyTags() {
    }

    /** Whether {@code level}'s celestial body is in {@code tag}; false for levels that are not a celestial body. */
    public static boolean is(@Nullable Level level, TagKey<CelestialBody<?, ?>> tag) {
        if (level == null) return false;
        Holder<CelestialBody<?, ?>> body = level.galacticraft$getCelestialBody();
        return body != null && body.is(tag);
    }

    private static TagKey<CelestialBody<?, ?>> key(String path) {
        return TagKey.create(AddonRegistries.CELESTIAL_BODY, Constant.id(path));
    }
}
