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

package dev.galacticraft.mod.meteor;

import dev.galacticraft.mod.world.dimension.meteor.MeteorImpact;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the deposit maths the crater and the no-damage strewn field share, so the two payouts
 * cannot drift apart: a protected dimension must hand over the same meteorite a cratering one does.
 */
class MeteorImpactTest {
    @Test
    void theSurvivingBodyIsWhatGetsDeposited() {
        assertEquals(5, MeteorImpact.depositCount(5, 20, 50));
    }

    @Test
    void aBodyCannotDepositMoreThanItWasMadeOf() {
        assertEquals(20, MeteorImpact.depositCount(30, 20, 50), "surviving voxels above the total are capped");
    }

    @Test
    void aDepositCannotOutgrowTheSpaceAvailableForIt() {
        assertEquals(3, MeteorImpact.depositCount(30, 20, 3));
    }

    @Test
    void somethingIsAlwaysLeftBehindWhereThereIsRoom() {
        assertEquals(1, MeteorImpact.depositCount(0, 20, 50), "even a fully ablated body leaves a fragment");
        assertEquals(1, MeteorImpact.depositCount(-4, 20, 50), "a negative count cannot invert the deposit");
    }

    @Test
    void noRoomMeansNoDeposit() {
        assertEquals(0, MeteorImpact.depositCount(5, 20, 0));
    }

    @Test
    void theStrewnFieldStaysInsideTheCraterItReplaces() {
        for (int craterRadius = 1; craterRadius <= 32; craterRadius++) {
            int strewn = MeteorImpact.strewnRadius(craterRadius);
            assertTrue(strewn >= 1, "a strike always scatters over at least one column: " + strewn);
            assertTrue(strewn <= craterRadius,
                    "the strewn field must not reach further than the crater would have: "
                            + strewn + " > " + craterRadius);
        }
    }

    @Test
    void aBiggerImpactorScattersWider() {
        assertTrue(MeteorImpact.strewnRadius(12) > MeteorImpact.strewnRadius(2));
    }
}
