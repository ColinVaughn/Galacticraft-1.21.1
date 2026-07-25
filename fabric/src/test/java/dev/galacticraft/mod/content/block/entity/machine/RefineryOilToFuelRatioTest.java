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

import static dev.galacticraft.mod.content.block.entity.machine.RefineryFuelLogic.DEFAULT_OIL_TO_FUEL_RATIO;
import static dev.galacticraft.mod.content.block.entity.machine.RefineryFuelLogic.REFINE_RATE;
import static dev.galacticraft.mod.content.block.entity.machine.RefineryFuelLogic.fuelFrom;
import static dev.galacticraft.mod.content.block.entity.machine.RefineryFuelLogic.oilDrawFor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The refinery's configurable oil-to-fuel ratio. The default of 1.0 must reproduce the flat 1:1
 * conversion the refinery had before the ratio existed.
 */
class RefineryOilToFuelRatioTest {
    @Test
    void theDefaultRatioIsTheOriginalOneToOne() {
        assertEquals(1.0, DEFAULT_OIL_TO_FUEL_RATIO);

        // Before the ratio existed the refinery ran `insert(FUEL, extract(OIL, space))`.
        long space = REFINE_RATE;
        assertEquals(space, oilDrawFor(space, DEFAULT_OIL_TO_FUEL_RATIO));
        assertEquals(space, fuelFrom(space, DEFAULT_OIL_TO_FUEL_RATIO, space));
    }

    @Test
    void aGenerousRatioDrawsProportionallyLessOil() {
        assertEquals(REFINE_RATE / 2, oilDrawFor(REFINE_RATE, 2.0));
        assertEquals(REFINE_RATE / 4, oilDrawFor(REFINE_RATE, 4.0));
        // ...and that smaller draw still fills the same headroom.
        assertEquals(REFINE_RATE, fuelFrom(REFINE_RATE / 2, 2.0, REFINE_RATE));
    }

    @Test
    void aStingyRatioDrawsProportionallyMoreOil() {
        assertEquals(REFINE_RATE * 2, oilDrawFor(REFINE_RATE, 0.5));
        assertEquals(REFINE_RATE, fuelFrom(REFINE_RATE * 2, 0.5, REFINE_RATE));
    }

    @Test
    void aPartialOilDrawYieldsProportionallyLessFuel() {
        // The tank may hold less oil than we asked for; fuel must scale with what was actually drawn.
        assertEquals(REFINE_RATE / 2, fuelFrom(REFINE_RATE / 2, 1.0, REFINE_RATE));
        assertEquals((REFINE_RATE / 4) * 2, fuelFrom(REFINE_RATE / 4, 2.0, REFINE_RATE));
        assertEquals(0L, fuelFrom(0L, 2.0, REFINE_RATE));
    }

    @Test
    void fuelNeverExceedsTheMeasuredHeadroom() {
        for (double ratio : new double[]{0.1, 0.5, 1.0, 2.0, 7.5, 100.0, 1.0E6}) {
            long oil = oilDrawFor(REFINE_RATE, ratio);
            long fuel = fuelFrom(oil, ratio, REFINE_RATE);
            assertTrue(fuel <= REFINE_RATE,
                    "ratio " + ratio + " overfilled the tank: " + fuel + " > " + REFINE_RATE);
        }
    }

    @Test
    void fuelNeverExceedsWhatTheRatioPromises() {
        // Rounding must never mint fuel out of nothing, at any ratio.
        for (double ratio : new double[]{0.1, 0.33, 0.5, 1.0, 1.5, 2.0, 7.5, 100.0, 1.0E6}) {
            for (long space : new long[]{1L, 7L, 100L, REFINE_RATE, REFINE_RATE * 3}) {
                long oil = oilDrawFor(space, ratio);
                long fuel = fuelFrom(oil, ratio, space);
                assertTrue(fuel <= oil * ratio,
                        "ratio " + ratio + " with headroom " + space + " turned " + oil
                                + " oil into " + fuel + " fuel");
            }
        }
    }

    @Test
    void oilIsNeverDrawnBeyondWhatTheHeadroomCanHold() {
        // Drawing more oil than the fuel tank can accept would silently destroy it.
        for (double ratio : new double[]{0.1, 0.5, 1.0, 1.5, 2.0, 7.5, 100.0}) {
            long space = REFINE_RATE;
            long oil = oilDrawFor(space, ratio);
            assertTrue(oil * ratio <= space + 1,
                    "ratio " + ratio + " drew " + oil + " oil, worth more than the " + space + " headroom");
        }
    }

    @Test
    void aNonPositiveRatioRefinesNothingRatherThanCrashing() {
        for (double ratio : new double[]{0.0, -1.0}) {
            assertEquals(0L, oilDrawFor(REFINE_RATE, ratio));
            assertEquals(0L, fuelFrom(REFINE_RATE, ratio, REFINE_RATE));
        }
    }

    @Test
    void noHeadroomDrawsNoOil() {
        assertEquals(0L, oilDrawFor(0L, 1.0));
        assertEquals(0L, oilDrawFor(-1L, 1.0));
    }

    @Test
    void aVeryGenerousRatioStillMakesProgress() {
        // Rounding down could ask for zero oil and stall the machine forever.
        assertTrue(oilDrawFor(REFINE_RATE, 1.0E9) > 0L);
    }
}
