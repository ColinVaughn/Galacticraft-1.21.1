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

package dev.galacticraft.mod.client.render.dimension.duststorm;

import dev.galacticraft.mod.network.s2c.DustStormSyncPayload;
import dev.galacticraft.mod.world.dimension.GCDimensions;
import dev.galacticraft.mod.world.dimension.duststorm.DustStormCurve;
import dev.galacticraft.mod.world.dimension.duststorm.DustStormPhase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

/**
 * Client-side mirror of the Mars dust-storm state. Holds the most recent server snapshot,
 * advances its counters locally between syncs, and exposes a smoothed render intensity for
 * the weather renderer, fog/sky tint, screen overlay and forecast HUD.
 */
public final class ClientDustStorms {
    private static byte phaseId = (byte) DustStormPhase.CLEAR.ordinal();
    private static float peakIntensity = 0.0f;
    private static int ticksIntoPhase = 0;
    private static int phaseDuration = 0;
    private static int remainingStormTicks = 0;

    private static float renderIntensity = 0.0f;
    private static float prevRenderIntensity = 0.0f;

    private ClientDustStorms() {
    }

    /** Applies a fresh server snapshot. */
    public static void accept(DustStormSyncPayload payload) {
        phaseId = payload.phase();
        peakIntensity = payload.peakIntensity();
        ticksIntoPhase = payload.ticksIntoPhase();
        phaseDuration = payload.phaseDuration();
        remainingStormTicks = payload.remainingStormTicks();
    }

    /** Advances local counters and eases the render intensity; call once per client tick. */
    public static void clientTick() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || !level.dimension().equals(GCDimensions.MARS)) {
            reset();
            return;
        }
        if (ticksIntoPhase < phaseDuration) ticksIntoPhase++;
        if (remainingStormTicks > 0) remainingStormTicks--;

        prevRenderIntensity = renderIntensity;
        float target = targetIntensity();
        renderIntensity += (target - renderIntensity) * 0.06f;
    }

    private static float targetIntensity() {
        return DustStormCurve.intensity(phase(), ticksIntoPhase, phaseDuration, peakIntensity);
    }

    public static DustStormPhase phase() {
        return DustStormPhase.byId(phaseId);
    }

    public static boolean isStormActive() {
        return phase().isStormActive();
    }

    /** Smoothed dust intensity in {@code [0, 1]}, interpolated for the given partial tick. */
    public static float intensity(float partialTick) {
        return prevRenderIntensity + (renderIntensity - prevRenderIntensity) * partialTick;
    }

    public static float intensity() {
        return renderIntensity;
    }

    /** Seconds until the storm hits (only meaningful during INCOMING), else -1. */
    public static int secondsUntilStorm() {
        if (phase() != DustStormPhase.INCOMING) return -1;
        return Math.max(0, (phaseDuration - ticksIntoPhase) + 19) / 20;
    }

    /** Seconds until the storm clears (only meaningful while active), else -1. */
    public static int secondsUntilClear() {
        if (!phase().isStormActive()) return -1;
        return (remainingStormTicks + 19) / 20;
    }

    public static void reset() {
        phaseId = (byte) DustStormPhase.CLEAR.ordinal();
        peakIntensity = 0.0f;
        ticksIntoPhase = 0;
        phaseDuration = 0;
        remainingStormTicks = 0;
        renderIntensity = 0.0f;
        prevRenderIntensity = 0.0f;
    }
}
