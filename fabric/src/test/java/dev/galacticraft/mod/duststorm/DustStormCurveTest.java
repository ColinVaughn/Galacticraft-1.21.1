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

package dev.galacticraft.mod.duststorm;

import dev.galacticraft.mod.world.dimension.duststorm.DustStormCurve;
import dev.galacticraft.mod.world.dimension.duststorm.DustStormPhase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DustStormCurveTest {
    private static final float EPS = 1e-4f;

    @Test
    void clearAndIncomingAreZero() {
        assertEquals(0.0f, DustStormCurve.intensity(DustStormPhase.CLEAR, 10, 100, 0.8f), EPS);
        assertEquals(0.0f, DustStormCurve.intensity(DustStormPhase.INCOMING, 50, 100, 0.8f), EPS);
    }

    @Test
    void buildingRampsUpMonotonicallyToPeak() {
        float a = DustStormCurve.intensity(DustStormPhase.BUILDING, 0, 100, 0.8f);
        float b = DustStormCurve.intensity(DustStormPhase.BUILDING, 50, 100, 0.8f);
        float c = DustStormCurve.intensity(DustStormPhase.BUILDING, 100, 100, 0.8f);
        assertEquals(0.0f, a, EPS);
        assertTrue(b > a, "midpoint should exceed start");
        assertTrue(c > b, "end should exceed midpoint");
        assertEquals(0.8f, c, EPS);
    }

    @Test
    void peakHoldsAtPeak() {
        assertEquals(0.8f, DustStormCurve.intensity(DustStormPhase.PEAK, 0, 100, 0.8f), EPS);
        assertEquals(0.8f, DustStormCurve.intensity(DustStormPhase.PEAK, 999, 100, 0.8f), EPS);
    }

    @Test
    void dyingRampsDownMonotonicallyToZero() {
        float a = DustStormCurve.intensity(DustStormPhase.DYING, 0, 100, 0.8f);
        float b = DustStormCurve.intensity(DustStormPhase.DYING, 50, 100, 0.8f);
        float c = DustStormCurve.intensity(DustStormPhase.DYING, 100, 100, 0.8f);
        assertEquals(0.8f, a, EPS);
        assertTrue(b < a, "midpoint should be below start");
        assertTrue(c < b, "end should be below midpoint");
        assertEquals(0.0f, c, EPS);
    }

    @Test
    void zeroDurationDoesNotDivideByZero() {
        // BUILDING with no duration is treated as fully ramped; DYING as fully faded.
        assertEquals(0.8f, DustStormCurve.intensity(DustStormPhase.BUILDING, 5, 0, 0.8f), EPS);
        assertEquals(0.0f, DustStormCurve.intensity(DustStormPhase.DYING, 5, 0, 0.8f), EPS);
    }

    @Test
    void phaseActivityFlags() {
        assertFalse(DustStormPhase.CLEAR.isStormActive());
        assertFalse(DustStormPhase.INCOMING.isStormActive());
        assertTrue(DustStormPhase.BUILDING.isStormActive());
        assertTrue(DustStormPhase.PEAK.isStormActive());
        assertTrue(DustStormPhase.DYING.isStormActive());
    }
}
