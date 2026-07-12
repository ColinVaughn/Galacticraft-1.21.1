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

package dev.galacticraft.mod.world.dimension.duststorm;

/**
 * The phases a Mars dust storm cycles through.
 *
 * <p>The lifecycle is {@code CLEAR -> INCOMING -> BUILDING -> PEAK -> DYING -> CLEAR}.
 * {@link #INCOMING} is the fairness/forecast window: no dust is in the air yet, but
 * ambient cues and the gear-based forecast HUD warn the player a storm is imminent.
 */
public enum DustStormPhase {
    /** No storm; counting down to the next one. */
    CLEAR,
    /** Forecast window: storm imminent, but intensity is still zero. */
    INCOMING,
    /** Intensity ramps up from zero toward the storm's peak. */
    BUILDING,
    /** Intensity holds near the storm's peak. */
    PEAK,
    /** Intensity ramps back down toward zero. */
    DYING;

    private static final DustStormPhase[] BY_ID = values();

    /**
     * @return {@code true} while dust is actually in the air (BUILDING, PEAK, DYING).
     * CLEAR and INCOMING return {@code false}.
     */
    public boolean isStormActive() {
        return this == BUILDING || this == PEAK || this == DYING;
    }

    public byte id() {
        return (byte) this.ordinal();
    }

    public static DustStormPhase byId(byte id) {
        if (id < 0 || id >= BY_ID.length) return CLEAR;
        return BY_ID[id];
    }
}
