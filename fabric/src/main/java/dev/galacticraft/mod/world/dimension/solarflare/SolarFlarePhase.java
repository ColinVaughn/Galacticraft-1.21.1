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

package dev.galacticraft.mod.world.dimension.solarflare;

/**
 * The phases a Mercury solar flare cycles through.
 *
 * <p>The lifecycle is {@code DORMANT -> INCOMING -> BUILDING -> PEAK -> DYING -> DORMANT}.
 * {@link #INCOMING} is the fairness/forecast window: no radiation is on the surface yet, but
 * ambient cues warn the player a flare is imminent.
 */
public enum SolarFlarePhase {
    /** No flare; counting down to the next one. */
    DORMANT,
    /** Forecast window: flare imminent, but intensity is still zero. */
    INCOMING,
    /** Intensity ramps up from zero toward the flare's peak. */
    BUILDING,
    /** Intensity holds near the flare's peak. */
    PEAK,
    /** Intensity ramps back down toward zero. */
    DYING;

    private static final SolarFlarePhase[] BY_ID = values();

    /**
     * @return {@code true} while radiation is actually hitting the surface (BUILDING, PEAK, DYING).
     * DORMANT and INCOMING return {@code false}.
     */
    public boolean isFlareActive() {
        return this == BUILDING || this == PEAK || this == DYING;
    }

    public byte id() {
        return (byte) this.ordinal();
    }

    public static SolarFlarePhase byId(byte id) {
        if (id < 0 || id >= BY_ID.length) return DORMANT;
        return BY_ID[id];
    }
}
