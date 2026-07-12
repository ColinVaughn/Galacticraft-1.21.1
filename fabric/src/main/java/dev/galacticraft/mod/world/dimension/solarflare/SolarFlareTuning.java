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

package dev.galacticraft.mod.world.dimension.solarflare;

/**
 * An immutable snapshot of the config knobs the flare scheduler reads, decoupling the
 * {@link MercurySolarFlareState} state machine from the live config so it can be unit tested.
 *
 * @param enabled       whether flares should be scheduled at all
 * @param meanInterval  the average number of ticks of quiet between flares
 * @param minDuration   the minimum active-flare length (BUILDING+PEAK+DYING) in ticks
 * @param maxDuration   the maximum active-flare length in ticks
 * @param intensityMul  a multiplier applied to each flare's rolled peak intensity
 */
public record SolarFlareTuning(boolean enabled, int meanInterval, int minDuration, int maxDuration, float intensityMul) {
}
