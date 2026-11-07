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

package dev.galacticraft.mod.duststorm;

import dev.galacticraft.mod.world.dimension.duststorm.DustStormExposure;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** How loud a dust storm's wind is, from what reaches the listener (issue #21). */
class DustStormExposureTest {
    private static final float EPSILON = 1.0e-4F;

    @Test
    void openSkyHearsTheWholeStorm() {
        assertEquals(1.0F, DustStormExposure.wind(15, 0, false), EPSILON);
    }

    @Test
    void deepRockHearsNothing() {
        assertEquals(0.0F, DustStormExposure.wind(0, 60, false), EPSILON);
    }

    @Test
    void walkingIntoACaveFadesTheWindStepByStep() {
        float previous = Float.MAX_VALUE;
        for (int skyLight = 15; skyLight >= 0; skyLight--) {
            float wind = DustStormExposure.wind(skyLight, 40, false);
            assertTrue(wind < previous, "the wind did not drop as sky light fell to " + skyLight);
            previous = wind;
        }
        assertEquals(0.0F, previous, EPSILON);
    }

    @Test
    void aRoofOverheadStillLetsSomeWindThrough() {
        float underARoof = DustStormExposure.wind(0, 3, false);
        assertTrue(underARoof > 0.2F && underARoof < DustStormExposure.THROUGH_COVER_WIND,
                "a closed room just under its roof should hear a muffled storm, not " + underARoof);
    }

    @Test
    void theWindThroughCoverDiesAwayWithDepth() {
        assertTrue(DustStormExposure.wind(0, 12, false) < DustStormExposure.wind(0, 3, false));
        assertEquals(0.0F, DustStormExposure.wind(0, DustStormExposure.COVER_FADE_DEPTH, false), EPSILON);
    }

    @Test
    void aSealedSpaceKeepsOutAllButAMuffle() {
        assertEquals(DustStormExposure.SEALED_WIND, DustStormExposure.wind(15, 0, true), EPSILON);
        assertEquals(0.0F, DustStormExposure.wind(0, 60, true), EPSILON);
    }
}
