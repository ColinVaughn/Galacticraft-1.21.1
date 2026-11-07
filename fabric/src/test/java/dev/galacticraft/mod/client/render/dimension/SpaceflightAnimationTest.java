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

package dev.galacticraft.mod.client.render.dimension;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SpaceflightAnimationTest {
    @Test void approachFillsTheViewAndKeepsMovingDuringSlowLoads() {
        assertEquals(128, SpaceflightAnimation.approachAltitude(128, 26, 0, 0), 1e-6);
        assertTrue(SpaceflightAnimation.approachAltitude(128, 26, 1, 0) < 26,
                "The Moon should fill the forward view within a second");
        double previous = 128;
        for (double seconds = .05; seconds < 60; seconds += .05) {
            double altitude = SpaceflightAnimation.approachAltitude(128, 26, seconds, 0);
            assertTrue(altitude < previous, "A slow destination must not freeze the approach");
            assertTrue(altitude > .1, "Keep the surface beyond the camera near plane");
            previous = altitude;
        }
        assertTrue(SpaceflightAnimation.approachAltitude(-5, 26, 0, 0) > .1);
    }

    @Test void largePlanetsStayBeyondTheShaderNearPlaneDuringLongLoads() {
        for (double radius : new double[]{7, 26, 96, 1053}) {
            double altitude = SpaceflightAnimation.approachAltitude(128, radius, 60, 1);
            // The sky shader scales each planet's centre to 100 units; the farthest corner is sqrt(3) radii away.
            double depth = altitude * 100 / (radius * Math.sqrt(3) + altitude);
            assertTrue(depth > .05, "A close planet must not be clipped into an empty frame");
        }
    }

    @Test void revealStartsImmediatelyAndFinishesWithoutAMinimumFlightDuration() {
        assertEquals(0, SpaceflightAnimation.reveal(0));
        assertTrue(SpaceflightAnimation.reveal(.01F) > 0);
        assertEquals(.5F, SpaceflightAnimation.reveal(.225F), 1e-5);
        assertEquals(1, SpaceflightAnimation.reveal(.45F));
        assertEquals(1, SpaceflightAnimation.reveal(10));
        assertTrue(SpaceflightAnimation.approachAltitude(128, 26, .1, 1)
                < SpaceflightAnimation.approachAltitude(128, 26, .1, 0));
    }
}