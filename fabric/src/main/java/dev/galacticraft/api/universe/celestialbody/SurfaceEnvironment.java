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

package dev.galacticraft.api.universe.celestialbody;

import net.minecraft.core.RegistryAccess;

public interface SurfaceEnvironment<C extends CelestialBodyConfig> {
    /**
     * Returns the approximate temperature on this celestial body
     *
     * @param access the registry access
     * @param time   the current world time
     * @param config the celestial body configuration to be queried
     * @return the approximate temperature on this celestial body
     */
    int temperature(RegistryAccess access, long time, C config);

    /** Interpolates day and night temperatures across a full local day cycle. */
    static int gradualTemperature(long time, long dayLength, int dayTemperature, int nightTemperature) {
        if (dayLength <= 0L) {
            return dayTemperature;
        }
        double phase = Math.floorMod(time, dayLength) / (double) dayLength;
        double curve = Math.cos((phase - 0.25) * 2.0 * Math.PI);
        double mid = (dayTemperature + nightTemperature) / 2.0;
        double amplitude = (dayTemperature - nightTemperature) / 2.0;
        return (int) Math.round(mid + amplitude * curve);
    }
}
