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

package dev.galacticraft.mod.world.dimension.meteor;

/**
 * The phases of a meteor shower.
 *
 * <p>The cycle is {@code DORMANT -> INCOMING -> WAXING -> PEAK -> WANING -> DORMANT}. The sky is
 * never truly empty: a low sporadic rate runs in every phase, exactly as it does in reality, and a
 * shower simply multiplies it. {@link #INCOMING} is the forecast window, where the rate has not
 * risen yet but players have been told what is coming.
 */
public enum MeteorShowerPhase {
    /** No shower; only the sporadic background rate, counting down to the next one. */
    DORMANT,
    /** Forecast window: a shower is imminent but the rate has not climbed yet. */
    INCOMING,
    /** Rate climbing from the background toward the shower's peak. */
    WAXING,
    /** Rate holding near the shower's peak. */
    PEAK,
    /** Rate falling back toward the background. */
    WANING;

    private static final MeteorShowerPhase[] BY_ID = values();

    /** Whether meteor activity is elevated above the sporadic background right now. */
    public boolean isShowerActive() {
        return this == WAXING || this == PEAK || this == WANING;
    }

    public byte id() {
        return (byte) this.ordinal();
    }

    public static MeteorShowerPhase byId(byte id) {
        if (id < 0 || id >= BY_ID.length) return DORMANT;
        return BY_ID[id];
    }
}
