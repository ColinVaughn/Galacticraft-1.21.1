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

package dev.galacticraft.mod.client.render.dimension.solarflare;

import dev.galacticraft.mod.network.s2c.SolarFlareSyncPayload;
import dev.galacticraft.mod.world.dimension.GCDimensions;
import dev.galacticraft.mod.world.dimension.solarflare.SolarFlareCurve;
import dev.galacticraft.mod.world.dimension.solarflare.SolarFlarePhase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

/**
 * Client-side mirror of the Mercury solar-flare state. Holds the most recent server snapshot,
 * advances its counters locally between syncs, and exposes a smoothed render intensity for the
 * screen overlay.
 */
public final class ClientSolarFlares {
    private static byte phaseId = (byte) SolarFlarePhase.DORMANT.ordinal();
    private static float peakIntensity = 0.0f;
    private static int ticksIntoPhase = 0;
    private static int phaseDuration = 0;
    private static int remainingFlareTicks = 0;

    private static float renderIntensity = 0.0f;
    private static float prevRenderIntensity = 0.0f;

    private ClientSolarFlares() {
    }

    /** Applies a fresh server snapshot. */
    public static void accept(SolarFlareSyncPayload payload) {
        phaseId = payload.phase();
        peakIntensity = payload.peakIntensity();
        ticksIntoPhase = payload.ticksIntoPhase();
        phaseDuration = payload.phaseDuration();
        remainingFlareTicks = payload.remainingFlareTicks();
    }

    /** Advances local counters and eases the render intensity; call once per client tick. */
    public static void clientTick() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || !level.dimension().equals(GCDimensions.MERCURY)) {
            reset();
            return;
        }
        if (ticksIntoPhase < phaseDuration) ticksIntoPhase++;
        if (remainingFlareTicks > 0) remainingFlareTicks--;

        prevRenderIntensity = renderIntensity;
        float target = targetIntensity();
        renderIntensity += (target - renderIntensity) * 0.06f;
    }

    private static float targetIntensity() {
        return SolarFlareCurve.intensity(phase(), ticksIntoPhase, phaseDuration, peakIntensity);
    }

    public static SolarFlarePhase phase() {
        return SolarFlarePhase.byId(phaseId);
    }

    public static boolean isFlareActive() {
        return phase().isFlareActive();
    }

    /** Smoothed flare intensity in {@code [0, 1]}, interpolated for the given partial tick. */
    public static float intensity(float partialTick) {
        return prevRenderIntensity + (renderIntensity - prevRenderIntensity) * partialTick;
    }

    public static float intensity() {
        return renderIntensity;
    }

    /** Seconds until the flare hits (only meaningful during INCOMING), else -1. */
    public static int secondsUntilFlare() {
        if (phase() != SolarFlarePhase.INCOMING) return -1;
        return Math.max(0, (phaseDuration - ticksIntoPhase) + 19) / 20;
    }

    /** Seconds until the flare subsides (only meaningful while active), else -1. */
    public static int secondsUntilClear() {
        if (!phase().isFlareActive()) return -1;
        return (remainingFlareTicks + 19) / 20;
    }

    public static void reset() {
        phaseId = (byte) SolarFlarePhase.DORMANT.ordinal();
        peakIntensity = 0.0f;
        ticksIntoPhase = 0;
        phaseDuration = 0;
        remainingFlareTicks = 0;
        renderIntensity = 0.0f;
        prevRenderIntensity = 0.0f;
    }
}
