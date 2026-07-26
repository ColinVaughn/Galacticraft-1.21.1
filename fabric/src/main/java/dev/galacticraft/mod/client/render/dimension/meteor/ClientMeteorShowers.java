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

package dev.galacticraft.mod.client.render.dimension.meteor;

import dev.galacticraft.mod.network.s2c.MeteorShowerSyncPayload;
import dev.galacticraft.mod.world.dimension.meteor.AtmosphereProfile;
import dev.galacticraft.mod.world.dimension.meteor.MeteorShowerCurve;
import dev.galacticraft.mod.world.dimension.meteor.MeteorShowerPhase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Client-side mirror of a dimension's meteor shower state. Holds the most recent server snapshot,
 * advances its counters locally between syncs, and exposes a smoothed intensity plus the shower's
 * radiant for {@link MeteorSkyStreaks}.
 *
 * <p>Also decides whether meteors are visible in this sky at all. On an airless body there is
 * nothing to make a meteoroid glow, so no streaks are drawn there however busy the shower is — the
 * only thing you see on the Moon is the impact.
 */
public final class ClientMeteorShowers {
    /** Surface density below which a sky cannot produce a visible burn-up trail, kg/m^3. */
    private static final double VISIBLE_DENSITY_THRESHOLD = 1.0e-4;

    private static byte phaseId = (byte) MeteorShowerPhase.DORMANT.ordinal();
    private static float peakIntensity = 0.0f;
    private static int ticksIntoPhase = 0;
    private static int phaseDuration = 0;
    private static int remainingShowerTicks = 0;
    private static float radiantYaw = 0.0f;
    private static float radiantPitch = 60.0f;

    private static float renderIntensity = 0.0f;
    private static float prevRenderIntensity = 0.0f;

    private static ResourceKey<Level> boundDimension;
    private static boolean atmosphereVisible;

    private ClientMeteorShowers() {
    }

    /** Applies a fresh server snapshot. */
    public static void accept(MeteorShowerSyncPayload payload) {
        ClientLevel level = Minecraft.getInstance().level;
        boundDimension = level == null ? null : level.dimension();

        phaseId = payload.phase();
        peakIntensity = payload.peakIntensity();
        ticksIntoPhase = payload.ticksIntoPhase();
        phaseDuration = payload.phaseDuration();
        remainingShowerTicks = payload.remainingShowerTicks();
        radiantYaw = payload.radiantYaw();
        radiantPitch = payload.radiantPitch();
    }

    /** Advances local counters, eases the render intensity and ticks the streaks. */
    public static void clientTick() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || boundDimension == null || !level.dimension().equals(boundDimension)) {
            reset();
            return;
        }

        atmosphereVisible = AtmosphereProfile.of(level).surfaceDensity() > VISIBLE_DENSITY_THRESHOLD;

        if (ticksIntoPhase < phaseDuration) ticksIntoPhase++;
        if (remainingShowerTicks > 0) remainingShowerTicks--;

        prevRenderIntensity = renderIntensity;
        renderIntensity += (targetIntensity() - renderIntensity) * 0.05f;

        MeteorSkyStreaks.clientTick(level, atmosphereVisible ? renderIntensity : 0.0f, radiantYaw, radiantPitch);
    }

    private static float targetIntensity() {
        return MeteorShowerCurve.intensity(phase(), ticksIntoPhase, phaseDuration, peakIntensity);
    }

    public static MeteorShowerPhase phase() {
        return MeteorShowerPhase.byId(phaseId);
    }

    public static boolean isShowerActive() {
        return phase().isShowerActive();
    }

    /** Whether this sky is thick enough for meteors to visibly burn in it. */
    public static boolean hasVisibleSky() {
        return atmosphereVisible;
    }

    /** Smoothed shower intensity in {@code [0, 1]}, interpolated for the given partial tick. */
    public static float intensity(float partialTick) {
        return prevRenderIntensity + (renderIntensity - prevRenderIntensity) * partialTick;
    }

    public static float intensity() {
        return renderIntensity;
    }

    public static float radiantYaw() {
        return radiantYaw;
    }

    public static float radiantPitch() {
        return radiantPitch;
    }

    /** Seconds until the shower starts (only meaningful during INCOMING), else -1. */
    public static int secondsUntilShower() {
        if (phase() != MeteorShowerPhase.INCOMING) return -1;
        return Math.max(0, (phaseDuration - ticksIntoPhase) + 19) / 20;
    }

    /** Seconds until the shower subsides (only meaningful while active), else -1. */
    public static int secondsUntilCalm() {
        if (!phase().isShowerActive()) return -1;
        return (remainingShowerTicks + 19) / 20;
    }

    public static void reset() {
        phaseId = (byte) MeteorShowerPhase.DORMANT.ordinal();
        peakIntensity = 0.0f;
        ticksIntoPhase = 0;
        phaseDuration = 0;
        remainingShowerTicks = 0;
        radiantYaw = 0.0f;
        radiantPitch = 60.0f;
        renderIntensity = 0.0f;
        prevRenderIntensity = 0.0f;
        boundDimension = null;
        atmosphereVisible = false;
        MeteorSkyStreaks.clear();
    }
}
