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

package dev.galacticraft.mod.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DimensionTimeTest {
    /** {@code GCCelestialBodies.MOON}: the Moon turns once every eight vanilla days. */
    private static final long MOON_DAY = 192000L;
    private static final long VANILLA_DAY = DimensionTime.VANILLA_DAY_LENGTH;

    /**
     * The sky angle {@code LevelTimeAccessMixin} draws from a day time, copied from it so these tests can
     * say where the sun is rather than only what the arithmetic does.
     */
    private static float skyAngle(long dayTime, long dayLength) {
        float f1 = (dayTime % dayLength) / (float) dayLength - 0.25F;
        if (f1 < 0.0F) ++f1;
        if (f1 > 1.0F) --f1;
        return f1;
    }

    @Test
    void aVanillaDayIsUnchanged() {
        assertEquals(24000L, DimensionTime.nextMorning(0L, VANILLA_DAY));
        assertEquals(24000L, DimensionTime.nextMorning(13000L, VANILLA_DAY));
        assertEquals(48000L, DimensionTime.nextMorning(24000L, VANILLA_DAY));
        assertEquals(6000L, DimensionTime.sameTimeOfDay(6000L, VANILLA_DAY));
    }

    /** The sun is down between sunset (0.25) and sunrise (0.75). */
    private static boolean isNight(float skyAngle) {
        return skyAngle > 0.25F && skyAngle < 0.75F;
    }

    @Test
    void sleepingRunsOnToTheDimensionsOwnSunrise() {
        long lunarMidnight = 3 * MOON_DAY / 4;
        // Vanilla's skip - round up to a multiple of 24000 - is an eighth of a lunar day, so from the
        // middle of the lunar night it only ever wakes you into more of the same night.
        assertTrue(isNight(skyAngle(lunarMidnight + VANILLA_DAY, MOON_DAY)), "vanilla's skip does not reach a lunar sunrise");
        assertEquals(0.75F, skyAngle(DimensionTime.nextMorning(lunarMidnight, MOON_DAY), MOON_DAY), 1.0E-6F);

        assertEquals(MOON_DAY, DimensionTime.nextMorning(1L, MOON_DAY));
        assertEquals(MOON_DAY, DimensionTime.nextMorning(MOON_DAY - 1, MOON_DAY));
        assertEquals(2 * MOON_DAY, DimensionTime.nextMorning(MOON_DAY, MOON_DAY), "a morning already reached must move on to the next");
        assertEquals(0.75F, skyAngle(DimensionTime.nextMorning(100000L, MOON_DAY), MOON_DAY), 1.0E-6F, "waking must be sunrise");
    }

    @Test
    void sleepingNeverRunsTheClockBackwards() {
        for (long dayTime : new long[] {0L, 1L, 24000L, 191999L, MOON_DAY, 1_000_000L}) {
            assertTrue(DimensionTime.nextMorning(dayTime, MOON_DAY) > dayTime, "at " + dayTime);
        }
    }

    /** {@code /time set <preset>} has to put the sun where the preset is named for. */
    @Test
    void timePresetsKeepTheirMeaningOnALongerDay() {
        assertEquals(48000L, DimensionTime.sameTimeOfDay(6000L, MOON_DAY), "lunar noon");
        assertEquals(144000L, DimensionTime.sameTimeOfDay(18000L, MOON_DAY), "lunar midnight");

        for (long preset : new long[] {0L, 1000L, 6000L, 13000L, 18000L}) {
            assertEquals(skyAngle(preset, VANILLA_DAY),
                    skyAngle(DimensionTime.sameTimeOfDay(preset, MOON_DAY), MOON_DAY), 1.0E-6F,
                    "the sun must sit where " + preset + " puts it in a vanilla day");
        }
    }

    /** {@code /time query daytime} has to answer in the units {@code /time set} accepts. */
    @Test
    void queryingTheTimeUndoesTheScaling() {
        for (long preset : new long[] {0L, 1000L, 6000L, 13000L, 18000L, 23999L}) {
            long local = DimensionTime.sameTimeOfDay(preset, MOON_DAY);
            assertEquals(preset, DimensionTime.vanillaTimeOfDay(local, MOON_DAY), "round trip of " + preset);
        }
        assertEquals(6000L, DimensionTime.vanillaTimeOfDay(48000L, MOON_DAY), "lunar noon reads back as noon");
        assertEquals(6000L, DimensionTime.vanillaTimeOfDay(48000L + 5 * MOON_DAY, MOON_DAY), "later lunar days read the same");
        assertEquals(6000L, DimensionTime.vanillaTimeOfDay(6000L, VANILLA_DAY), "a vanilla day is left alone");
    }

    /**
     * Guards the test above: without the scaling, every vanilla preset falls inside the first hour after
     * the lunar sunrise, which is why none of them could bring the sun up on the Moon.
     */
    @Test
    void unscaledTimePresetsAllLandJustAfterTheLunarSunrise() {
        for (long preset : new long[] {0L, 1000L, 6000L, 13000L, 18000L}) {
            float angle = skyAngle(preset, MOON_DAY);
            assertTrue(angle >= 0.75F && angle < 0.85F, "preset " + preset + " sits at " + angle);
        }
    }
}
