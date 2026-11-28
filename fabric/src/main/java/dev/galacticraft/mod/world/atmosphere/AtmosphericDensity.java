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

package dev.galacticraft.mod.world.atmosphere;

import dev.galacticraft.api.universe.celestialbody.CelestialBody;
import dev.galacticraft.mod.tag.GCDimensionTypeTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/** Air density in Earth atmospheres. */
public final class AtmosphericDensity {
    // Elytra drag tops out at 0.02/tick here; Venus's 92 atm is treated as this.
    public static final float MAX_PRESSURE = 40.0F;
    public static final float HABITAT_PRESSURE = 1.0F;
    private static final float ATMOSPHERES_PER_UNIT_ELYTRA_DRAG = 2000.0F;

    private AtmosphericDensity() {
    }

    public static float of(Level level) {
        return of(level, level.galacticraft$getCelestialBody());
    }

    private static float of(Level level, @Nullable Holder<CelestialBody<?, ?>> body) {
        if (body == null) return 1.0F;
        if (isVacuum(level)) return 0.0F;
        return clamp(body.value().atmosphere().pressure());
    }

    // isBreathable would load an unloaded chunk, so those read as open air.
    public static float at(Level level, BlockPos pos) {
        float open = of(level);
        if (open == HABITAT_PRESSURE || level.getDefaultBreathable() || !level.isLoaded(pos)) return open;
        return level.isBreathable(pos) ? HABITAT_PRESSURE : open;
    }

    public static boolean isVacuum(Level level) {
        return level.galacticraft$hasDimensionTypeTag(GCDimensionTypeTags.VACUUM);
    }

    public static float elytraDrag(Level level) {
        Holder<CelestialBody<?, ?>> body = level.galacticraft$getCelestialBody();
        return body == null ? 0.0F : of(level, body) / ATMOSPHERES_PER_UNIT_ELYTRA_DRAG;
    }

    public static float clamp(float pressure) {
        if (!(pressure > 0.0F)) return 0.0F; // also catches NaN
        return Math.min(pressure, MAX_PRESSURE);
    }
}
