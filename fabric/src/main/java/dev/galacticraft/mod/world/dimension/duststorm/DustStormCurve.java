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
 * Pure math mapping a storm phase and progress to a {@code [0, peak]} intensity.
 *
 * <p>Deterministic and side-effect free so the server and client compute identical
 * intensities from the same synced {@code (phase, ticksIntoPhase, phaseDuration, peak)}
 * tuple, and so it can be unit tested without a running game.
 */
public final class DustStormCurve {
    private DustStormCurve() {
    }

    /**
     * @param phase         the current storm phase
     * @param ticksIntoPhase how many ticks the phase has been active
     * @param phaseDuration  the total length of the current phase in ticks
     * @param peak           the storm's target peak intensity in {@code [0, 1]}
     * @return the current dust intensity in {@code [0, peak]}
     */
    public static float intensity(DustStormPhase phase, int ticksIntoPhase, int phaseDuration, float peak) {
        return switch (phase) {
            case CLEAR, INCOMING -> 0.0f;
            case PEAK -> peak;
            case BUILDING -> peak * smoothstep(progress(ticksIntoPhase, phaseDuration));
            case DYING -> peak * (1.0f - smoothstep(progress(ticksIntoPhase, phaseDuration)));
        };
    }

    private static float progress(int ticksIntoPhase, int phaseDuration) {
        if (phaseDuration <= 0) return 1.0f;
        return clamp01((float) ticksIntoPhase / (float) phaseDuration);
    }

    /** Classic Hermite smoothstep {@code 3t^2 - 2t^3}; monotonic non-decreasing on [0, 1]. */
    private static float smoothstep(float t) {
        return t * t * (3.0f - 2.0f * t);
    }

    private static float clamp01(float v) {
        if (v < 0.0f) return 0.0f;
        if (v > 1.0f) return 1.0f;
        return v;
    }
}
