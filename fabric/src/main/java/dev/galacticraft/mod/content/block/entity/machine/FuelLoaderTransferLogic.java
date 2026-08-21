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

/**
 * Fuel transfer maths for {@link FuelLoaderBlockEntity}, kept free of Minecraft types so it can be
 * checked in a plain unit test. {@code FuelLoaderBlockEntity} itself cannot be loaded without a
 * bootstrapped registry, because its storage spec reaches for fluids and block states.
 *
 * All quantities are in droplets.
 */
public final class FuelLoaderTransferLogic {
    /** Droplets the loader moves into a docked vehicle per tick, once it is up to speed. */
    public static final long TRANSFER_RATE = 500;

    /** Ticks of spin-up before any fuel actually moves. */
    public static final int MAX_PROGRESS = 81 * 2;

    private FuelLoaderTransferLogic() {
    }

    /**
     * Droplets that may move this tick.
     *
     * Zero while the loader is still spinning up: the caller must not commit a transfer it is
     * not going to account for on both sides. Otherwise the amount is bounded by the rate, by what
     * the loader holds, and by the room left in the vehicle, so the caller can safely extract
     * exactly what its insert reported.
     *
     * @param available droplets in the loader's own tank
     * @param room      droplets of headroom left in the vehicle's tank
     * @param progress  ticks of spin-up completed
     */
    public static long transferableFuel(long available, long room, int progress) {
        if (progress < MAX_PROGRESS) return 0L;
        if (available <= 0L || room <= 0L) return 0L;
        return Math.min(TRANSFER_RATE, Math.min(available, room));
    }
}
