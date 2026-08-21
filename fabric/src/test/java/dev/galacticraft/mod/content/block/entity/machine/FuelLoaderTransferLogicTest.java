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

/**
 * Covers how much fuel a fuel loader may move in one tick.
 *
 * The loader used to commit an insert into the rocket during its spin-up phase and return before
 * deducting that fuel from its own tank, so every load duplicated up to a full transfer rate per
 * tick for the whole spin-up. The rule that prevents it: while spinning up nothing may move at all,
 * and once running the amount is bounded by both sides, so the caller can extract exactly what it
 * inserted.
 */
class FuelLoaderTransferLogicTest {
    private static final int MAX_PROGRESS = FuelLoaderTransferLogic.MAX_PROGRESS;
    private static final long RATE = FuelLoaderTransferLogic.TRANSFER_RATE;

    @Test
    void nothingMovesWhileTheLoaderIsStillSpinningUp() {
        for (int progress = 0; progress < MAX_PROGRESS; progress++) {
            assertEquals(0L, FuelLoaderTransferLogic.transferableFuel(100_000L, 100_000L, progress),
                    "fuel moved at progress " + progress + ", before the loader is ready");
        }
    }

    @Test
    void aReadyLoaderMovesItsFullRate() {
        assertEquals(RATE, FuelLoaderTransferLogic.transferableFuel(100_000L, 100_000L, MAX_PROGRESS));
    }

    @Test
    void aLoaderCannotMoveMoreFuelThanItHolds() {
        assertEquals(RATE - 1, FuelLoaderTransferLogic.transferableFuel(RATE - 1, 100_000L, MAX_PROGRESS));
    }

    @Test
    void aLoaderCannotMoveMoreFuelThanTheRocketWillHold() {
        assertEquals(12L, FuelLoaderTransferLogic.transferableFuel(100_000L, 12L, MAX_PROGRESS));
    }

    @Test
    void aFullRocketTakesNothing() {
        assertEquals(0L, FuelLoaderTransferLogic.transferableFuel(100_000L, 0L, MAX_PROGRESS));
        assertEquals(0L, FuelLoaderTransferLogic.transferableFuel(100_000L, -50L, MAX_PROGRESS),
                "a rocket reporting more fuel than capacity must not pull fuel backwards");
    }

    @Test
    void anEmptyLoaderMovesNothing() {
        assertEquals(0L, FuelLoaderTransferLogic.transferableFuel(0L, 100_000L, MAX_PROGRESS));
        assertEquals(0L, FuelLoaderTransferLogic.transferableFuel(-10L, 100_000L, MAX_PROGRESS));
    }

    @Test
    void theAmountIsNeverNegativeForAnyInput() {
        long[] samples = {-1000L, -1L, 0L, 1L, RATE, 100_000L};
        for (long available : samples) {
            for (long room : samples) {
                for (int progress : new int[]{0, MAX_PROGRESS / 2, MAX_PROGRESS, MAX_PROGRESS + 10}) {
                    long moved = FuelLoaderTransferLogic.transferableFuel(available, room, progress);
                    assertTrue(moved >= 0L, "negative transfer for " + available + "/" + room + "@" + progress);
                    assertTrue(moved <= RATE, "transfer exceeded the rate limit: " + moved);
                }
            }
        }
    }
}
