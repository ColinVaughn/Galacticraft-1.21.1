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

import dev.galacticraft.machinelib.api.transfer.FluidConstants;

/**
 * Oil-to-fuel conversion maths for {@link RefineryBlockEntity}, kept free of Minecraft types so the
 * ratio can be checked in a plain unit test. {@code RefineryBlockEntity} itself cannot be loaded
 * without a bootstrapped registry, because its storage spec reaches for fluids and block states.
 *
 * <p>All quantities are in droplets.
 */
public final class RefineryFuelLogic {
    /** Buckets of fuel yielded per bucket of crude oil, before the config is consulted. */
    public static final double DEFAULT_OIL_TO_FUEL_RATIO = 1.0;

    /** Droplets of fuel the refinery tries to produce on a single tick. */
    public static final long REFINE_RATE = FluidConstants.BUCKET / 20 / 5;

    private RefineryFuelLogic() {
    }

    /**
     * Droplets of oil to draw in order to fill {@code fuelSpace} droplets of headroom in the fuel
     * tank. At the default 1:1 ratio this is simply {@code fuelSpace}, reproducing the behaviour
     * the refinery had before the ratio was configurable.
     *
     * <p>A non-positive ratio means oil cannot become fuel at all, so nothing is drawn.
     */
    public static long oilDrawFor(long fuelSpace, double oilToFuelRatio) {
        if (fuelSpace <= 0L || oilToFuelRatio <= 0.0) return 0L;
        // At least one droplet, so a very generous ratio still makes progress rather than stalling.
        return Math.max(1L, (long) (fuelSpace / oilToFuelRatio));
    }

    /**
     * Droplets of fuel yielded by {@code oilDrawn} droplets of oil, never exceeding the headroom
     * that was measured before the oil was drawn.
     */
    public static long fuelFrom(long oilDrawn, double oilToFuelRatio, long fuelSpace) {
        if (oilDrawn <= 0L || oilToFuelRatio <= 0.0) return 0L;
        return Math.min(fuelSpace, (long) (oilDrawn * oilToFuelRatio));
    }
}
