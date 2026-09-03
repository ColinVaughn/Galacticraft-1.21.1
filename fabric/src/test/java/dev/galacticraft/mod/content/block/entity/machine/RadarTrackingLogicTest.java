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

package dev.galacticraft.mod.content.block.entity.machine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RadarTrackingLogicTest {
    @Test
    void linkedRadarsImproveAccuracyWithoutBecomingPerfect() {
        assertEquals(48, RadarTrackingLogic.uncertaintyRadius(1));
        assertEquals(24, RadarTrackingLogic.uncertaintyRadius(4));
        assertEquals(4, RadarTrackingLogic.uncertaintyRadius(10_000));
        assertTrue(RadarTrackingLogic.uncertaintyRadius(9) < RadarTrackingLogic.uncertaintyRadius(4));
    }

    @Test
    void impactCoordinatesAreReportedAtCurrentAccuracy() {
        assertEquals(144, RadarTrackingLogic.estimatedCoordinate(137.0, 48));
        assertEquals(-144, RadarTrackingLogic.estimatedCoordinate(-137.0, 48));
        assertEquals(136, RadarTrackingLogic.estimatedCoordinate(137.0, 4));
    }
}
