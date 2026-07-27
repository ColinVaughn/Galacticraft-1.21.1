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

import dev.galacticraft.mod.world.dimension.meteor.MeteorShowerCurve;
import dev.galacticraft.mod.world.dimension.meteor.MeteorShowerPhase;
import dev.galacticraft.mod.world.dimension.meteor.MeteorShowerState;
import dev.galacticraft.mod.world.dimension.meteor.MeteorShowerTuning;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Covers the shower scheduler's state machine and the intensity curve derived from it. */
class MeteorShowerStateTest {
    private static final float EPS = 1e-4f;
    private static final MeteorShowerTuning TUNING = new MeteorShowerTuning(true, 400, 200, 400, 1.0f);

    @Test
    void aFreshStateStartsDormantAndSchedulesItself() {
        MeteorShowerState state = new MeteorShowerState();
        assertEquals(MeteorShowerPhase.DORMANT, state.phase());

        state.tick(RandomSource.create(1), TUNING);
        assertTrue(state.phaseDuration() > 0, "the first dormant interval should be scheduled lazily");
    }

    @Test
    void theCycleVisitsEveryPhaseInOrder() {
        MeteorShowerState state = new MeteorShowerState();
        RandomSource random = RandomSource.create(42);
        Set<MeteorShowerPhase> seen = EnumSet.noneOf(MeteorShowerPhase.class);

        MeteorShowerPhase previous = state.phase();
        for (int tick = 0; tick < 100_000 && seen.size() < MeteorShowerPhase.values().length; tick++) {
            state.tick(random, TUNING);
            if (state.phase() != previous) {
                assertTrue(isLegalTransition(previous, state.phase()),
                        "illegal transition " + previous + " -> " + state.phase());
                previous = state.phase();
            }
            seen.add(state.phase());
        }

        assertEquals(MeteorShowerPhase.values().length, seen.size(), "every phase should be reachable: saw " + seen);
    }

    @Test
    void intensityStaysWithinZeroAndTheRolledPeak() {
        MeteorShowerState state = new MeteorShowerState();
        RandomSource random = RandomSource.create(7);
        for (int tick = 0; tick < 50_000; tick++) {
            state.tick(random, TUNING);
            float intensity = state.currentIntensity();
            assertTrue(intensity >= 0.0f && intensity <= 1.0f, "intensity out of range: " + intensity);
            if (!state.phase().isShowerActive()) {
                assertEquals(0.0f, intensity, EPS, "no activity outside an active shower");
            }
        }
    }

    @Test
    void naturalShowerPeaksVaryAndStayBelowOne() {
        MeteorShowerState state = new MeteorShowerState();
        RandomSource random = RandomSource.create(23);
        Set<Float> peaks = new HashSet<>();
        int showerCount = 0;
        MeteorShowerPhase previous = state.phase();

        for (int tick = 0; tick < 50_000 && showerCount < 8; tick++) {
            state.tick(random, TUNING);
            if (state.phase() == MeteorShowerPhase.INCOMING && previous != MeteorShowerPhase.INCOMING) {
                float peak = state.peakIntensity();
                assertTrue(peak >= 0.55f, "natural peak below its minimum: " + peak);
                assertTrue(peak < 1.0f, "natural peak was pinned to one: " + peak);
                peaks.add(peak);
                showerCount++;
            }
            previous = state.phase();
        }

        assertEquals(8, showerCount, "test should observe several natural showers");
        assertTrue(peaks.size() > 1, "natural showers should not all roll the same peak");
    }

    @Test
    void theForecastWindowRunsBeforeAnyActivity() {
        MeteorShowerState state = new MeteorShowerState();
        RandomSource random = RandomSource.create(3);
        for (int tick = 0; tick < 50_000; tick++) {
            state.tick(random, TUNING);
            if (state.phase() == MeteorShowerPhase.INCOMING) {
                assertEquals(0.0f, state.currentIntensity(), EPS, "the forecast window is quiet");
                assertTrue(state.ticksUntilShower() > 0, "players need time to react");
                return;
            }
        }
        throw new AssertionError("never reached the forecast window");
    }

    @Test
    void aRadiantIsRolledForEveryShower() {
        MeteorShowerState state = new MeteorShowerState();
        RandomSource random = RandomSource.create(11);
        for (int tick = 0; tick < 50_000; tick++) {
            state.tick(random, TUNING);
            if (!state.phase().isShowerActive()) continue;
            assertTrue(state.radiantYaw() >= 0.0f && state.radiantYaw() <= 360.0f, "yaw: " + state.radiantYaw());
            assertTrue(state.radiantPitch() > 0.0f && state.radiantPitch() < 90.0f, "pitch: " + state.radiantPitch());
            return;
        }
        throw new AssertionError("never reached an active shower");
    }

    @Test
    void remainingTicksCountDownToZeroAcrossTheShower() {
        MeteorShowerState state = new MeteorShowerState();
        RandomSource random = RandomSource.create(19);
        int previous = Integer.MAX_VALUE;
        boolean sawActive = false;

        for (int tick = 0; tick < 50_000; tick++) {
            state.tick(random, TUNING);
            if (state.phase().isShowerActive()) {
                sawActive = true;
                int remaining = state.remainingShowerTicks();
                assertTrue(remaining >= 0, "remaining must not go negative");
                assertTrue(remaining <= previous, "remaining must not climb mid-shower");
                previous = remaining;
            } else if (sawActive) {
                assertEquals(0, state.remainingShowerTicks(), "no time remains once the shower is over");
                return;
            }
        }
        throw new AssertionError("never completed a shower");
    }

    @Test
    void debugStartJumpsStraightToAnActivePeak() {
        MeteorShowerState state = new MeteorShowerState();
        state.debugStart(RandomSource.create(5), TUNING, 0.8f);
        assertEquals(MeteorShowerPhase.PEAK, state.phase());
        assertEquals(0.8f, state.currentIntensity(), EPS);
    }

    @Test
    void debugStopReturnsToTheSporadicBackground() {
        MeteorShowerState state = new MeteorShowerState();
        state.debugStart(RandomSource.create(5), TUNING, 0.8f);
        state.debugStop(RandomSource.create(5), TUNING);
        assertEquals(MeteorShowerPhase.DORMANT, state.phase());
        assertEquals(0.0f, state.currentIntensity(), EPS);
        assertFalse(state.phase().isShowerActive());
    }

    @Test
    void curveIsQuietWhileDormantAndPeaksAtThePeak() {
        assertEquals(0.0f, MeteorShowerCurve.intensity(MeteorShowerPhase.DORMANT, 10, 100, 0.9f), EPS);
        assertEquals(0.0f, MeteorShowerCurve.intensity(MeteorShowerPhase.INCOMING, 50, 100, 0.9f), EPS);
        assertEquals(0.9f, MeteorShowerCurve.intensity(MeteorShowerPhase.PEAK, 50, 100, 0.9f), EPS);
    }

    @Test
    void curveRampsUpThenBackDown() {
        float waxStart = MeteorShowerCurve.intensity(MeteorShowerPhase.WAXING, 0, 100, 0.9f);
        float waxMid = MeteorShowerCurve.intensity(MeteorShowerPhase.WAXING, 50, 100, 0.9f);
        float waxEnd = MeteorShowerCurve.intensity(MeteorShowerPhase.WAXING, 100, 100, 0.9f);
        assertEquals(0.0f, waxStart, EPS);
        assertTrue(waxMid > waxStart && waxEnd > waxMid);
        assertEquals(0.9f, waxEnd, EPS);

        float waneStart = MeteorShowerCurve.intensity(MeteorShowerPhase.WANING, 0, 100, 0.9f);
        float waneEnd = MeteorShowerCurve.intensity(MeteorShowerPhase.WANING, 100, 100, 0.9f);
        assertEquals(0.9f, waneStart, EPS);
        assertEquals(0.0f, waneEnd, EPS);
    }

    @Test
    void phaseIdsSurviveTheNetworkAndNbtRoundTrip() {
        for (MeteorShowerPhase phase : MeteorShowerPhase.values()) {
            assertEquals(phase, MeteorShowerPhase.byId(phase.id()));
        }
        assertEquals(MeteorShowerPhase.DORMANT, MeteorShowerPhase.byId((byte) -1), "out-of-range ids fall back safely");
        assertEquals(MeteorShowerPhase.DORMANT, MeteorShowerPhase.byId((byte) 99));
    }

    private static boolean isLegalTransition(MeteorShowerPhase from, MeteorShowerPhase to) {
        return switch (from) {
            case DORMANT -> to == MeteorShowerPhase.INCOMING;
            case INCOMING -> to == MeteorShowerPhase.WAXING;
            case WAXING -> to == MeteorShowerPhase.PEAK;
            case PEAK -> to == MeteorShowerPhase.WANING;
            case WANING -> to == MeteorShowerPhase.DORMANT;
        };
    }
}
