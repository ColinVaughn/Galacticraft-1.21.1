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

import dev.galacticraft.mod.world.dimension.duststorm.DustStormPhase;
import dev.galacticraft.mod.world.dimension.duststorm.DustStormTuning;
import dev.galacticraft.mod.world.dimension.duststorm.MarsDustStormState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarsDustStormStateTest {
    private static final DustStormTuning TUNING = new DustStormTuning(true, 100, 200, 400, 1.0f);

    /** Ticks until the phase changes, returning the new phase. Caps iterations to avoid a hang. */
    private static DustStormPhase tickUntilPhaseChange(MarsDustStormState state, RandomSource rng) {
        for (int i = 0; i < 1_000_000; i++) {
            if (state.tick(rng, TUNING)) return state.phase();
        }
        throw new AssertionError("phase never changed");
    }

    @Test
    void freshStateSchedulesThenStaysClear() {
        MarsDustStormState state = new MarsDustStormState();
        RandomSource rng = RandomSource.create(1L);

        boolean changed = state.tick(rng, TUNING); // first tick schedules the wait
        assertFalse(changed, "scheduling the initial interval is not a phase change");
        assertEquals(DustStormPhase.CLEAR, state.phase());
        assertTrue(state.phaseDuration() > 0, "an interval should have been rolled");
        assertEquals(0.0f, state.currentIntensity(), 1e-4);
    }

    @Test
    void clearTransitionsToIncomingWithRolledStorm() {
        MarsDustStormState state = new MarsDustStormState();
        RandomSource rng = RandomSource.create(2L);
        state.tick(rng, TUNING); // schedule

        DustStormPhase next = tickUntilPhaseChange(state, rng);
        assertEquals(DustStormPhase.INCOMING, next);
        assertTrue(state.peakIntensity() > 0.0f && state.peakIntensity() <= 1.0f);
        assertEquals(0.0f, state.currentIntensity(), 1e-4, "no dust during the forecast window");
        assertTrue(state.ticksUntilStorm() > 0);
    }

    @Test
    void fullCycleVisitsEveryPhaseInOrderThenReschedules() {
        MarsDustStormState state = new MarsDustStormState();
        RandomSource rng = RandomSource.create(3L);
        state.tick(rng, TUNING); // schedule

        List<DustStormPhase> order = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            order.add(tickUntilPhaseChange(state, rng));
        }
        assertEquals(List.of(
                DustStormPhase.INCOMING,
                DustStormPhase.BUILDING,
                DustStormPhase.PEAK,
                DustStormPhase.DYING,
                DustStormPhase.CLEAR,
                DustStormPhase.INCOMING
        ), order);
        // After returning to CLEAR a fresh interval must be scheduled.
        assertTrue(state.phaseDuration() > 0);
    }

    @Test
    void intensityIsPositiveDuringPeak() {
        MarsDustStormState state = new MarsDustStormState();
        RandomSource rng = RandomSource.create(4L);
        state.tick(rng, TUNING);
        // advance to PEAK
        DustStormPhase p = tickUntilPhaseChange(state, rng);
        while (p != DustStormPhase.PEAK) {
            p = tickUntilPhaseChange(state, rng);
        }
        assertEquals(DustStormPhase.PEAK, state.phase());
        assertTrue(state.currentIntensity() > 0.0f);
        assertTrue(state.remainingStormTicks() > 0);
    }

    @Test
    void nbtRoundTripPreservesState() {
        MarsDustStormState state = new MarsDustStormState();
        RandomSource rng = RandomSource.create(5L);
        state.tick(rng, TUNING);
        // advance into an active storm so the durations/peak are non-trivial
        DustStormPhase p = tickUntilPhaseChange(state, rng);
        while (!p.isStormActive()) {
            p = tickUntilPhaseChange(state, rng);
        }
        // tick a few more times so ticksIntoPhase is non-zero
        for (int i = 0; i < 5; i++) state.tick(rng, TUNING);

        CompoundTag tag = state.save(new CompoundTag(), null);
        MarsDustStormState loaded = MarsDustStormState.load(tag, null);

        assertEquals(state.phase(), loaded.phase());
        assertEquals(state.ticksIntoPhase(), loaded.ticksIntoPhase());
        assertEquals(state.phaseDuration(), loaded.phaseDuration());
        assertEquals(state.peakIntensity(), loaded.peakIntensity(), 1e-6);
        assertEquals(state.currentIntensity(), loaded.currentIntensity(), 1e-6);
        assertEquals(state.remainingStormTicks(), loaded.remainingStormTicks());
    }

    @Test
    void debugStartEntersActiveStorm() {
        MarsDustStormState state = new MarsDustStormState();
        RandomSource rng = RandomSource.create(6L);
        state.debugStart(rng, TUNING, 0.9f);
        assertTrue(state.phase().isStormActive());
        assertEquals(0.9f, state.peakIntensity(), 1e-4);
    }
}
