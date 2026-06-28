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

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Server-authoritative, persistent state of the Mercury solar-flare cycle for one dimension.
 *
 * <p>Advanced once per Mercury world tick by {@code MercurySolarFlareManager}. Holds the current
 * {@link SolarFlarePhase}, how far into that phase we are, the target peak intensity, and the
 * pre-rolled BUILDING/PEAK/DYING lengths of the active flare. All fields are plain primitives so
 * the NBT round-trip does not need the registry lookup.
 */
public class MercurySolarFlareState extends SavedData {
    public static final String ID = "galacticraft_solar_flare";

    /** Length of the forecast (INCOMING) window in ticks (~35s at 20 tps). */
    public static final int INCOMING_TICKS = 700;

    private SolarFlarePhase phase = SolarFlarePhase.DORMANT;
    private int ticksIntoPhase = 0;
    /** Length of the current phase; in DORMANT this is the wait until the next flare. */
    private int phaseDuration = 0;
    private float peakIntensity = 0.0f;
    private int buildingDuration = 0;
    private int peakDuration = 0;
    private int dyingDuration = 0;

    public MercurySolarFlareState() {
        super();
    }

    /**
     * Advances the flare cycle by one tick.
     *
     * @return {@code true} if the phase changed this tick (callers should re-sync clients).
     */
    public boolean tick(RandomSource random, SolarFlareTuning tuning) {
        // Lazily schedule the first dormant interval for a fresh (unscheduled) state.
        if (this.phase == SolarFlarePhase.DORMANT && this.phaseDuration <= 0) {
            this.phaseDuration = rollInterval(random, tuning);
            this.ticksIntoPhase = 0;
            this.setDirty();
            return false;
        }

        this.ticksIntoPhase++;
        if (this.ticksIntoPhase < this.phaseDuration) {
            return false;
        }

        advance(random, tuning);
        this.ticksIntoPhase = 0;
        this.setDirty();
        return true;
    }

    private void advance(RandomSource random, SolarFlareTuning tuning) {
        switch (this.phase) {
            case DORMANT -> {
                // Schedule a new flare: roll its peak and split its total length into ramps + peak.
                this.peakIntensity = Mth.clamp((0.6f + random.nextFloat() * 0.4f) * tuning.intensityMul(), 0.0f, 1.0f);
                int span = Math.max(1, tuning.maxDuration() - tuning.minDuration() + 1);
                int total = tuning.minDuration() + random.nextInt(span);
                this.buildingDuration = Math.max(1, Math.round(total * 0.2f));
                this.dyingDuration = Math.max(1, Math.round(total * 0.2f));
                this.peakDuration = Math.max(1, total - this.buildingDuration - this.dyingDuration);
                this.phase = SolarFlarePhase.INCOMING;
                this.phaseDuration = INCOMING_TICKS;
            }
            case INCOMING -> {
                this.phase = SolarFlarePhase.BUILDING;
                this.phaseDuration = this.buildingDuration;
            }
            case BUILDING -> {
                this.phase = SolarFlarePhase.PEAK;
                this.phaseDuration = this.peakDuration;
            }
            case PEAK -> {
                this.phase = SolarFlarePhase.DYING;
                this.phaseDuration = this.dyingDuration;
            }
            case DYING -> {
                this.phase = SolarFlarePhase.DORMANT;
                this.phaseDuration = rollInterval(random, tuning);
                this.peakIntensity = 0.0f;
                this.buildingDuration = 0;
                this.peakDuration = 0;
                this.dyingDuration = 0;
            }
        }
    }

    /** Rolls a dormant wait centred on the mean interval (average ≈ meanInterval). */
    private static int rollInterval(RandomSource random, SolarFlareTuning tuning) {
        int mean = Math.max(1, tuning.meanInterval());
        return Math.max(1, mean / 2 + random.nextInt(mean));
    }

    /** The current flare intensity in {@code [0, 1]} derived from phase + progress. */
    public float currentIntensity() {
        return SolarFlareCurve.intensity(this.phase, this.ticksIntoPhase, this.phaseDuration, this.peakIntensity);
    }

    /** Ticks remaining until the flare fully subsides (0 when no flare is active). */
    public int remainingFlareTicks() {
        return switch (this.phase) {
            case BUILDING -> (this.phaseDuration - this.ticksIntoPhase) + this.peakDuration + this.dyingDuration;
            case PEAK -> (this.phaseDuration - this.ticksIntoPhase) + this.dyingDuration;
            case DYING -> this.phaseDuration - this.ticksIntoPhase;
            default -> 0;
        };
    }

    /** Ticks remaining in the forecast window (0 unless INCOMING). */
    public int ticksUntilFlare() {
        return this.phase == SolarFlarePhase.INCOMING ? Math.max(0, this.phaseDuration - this.ticksIntoPhase) : 0;
    }

    public SolarFlarePhase phase() {
        return this.phase;
    }

    public int ticksIntoPhase() {
        return this.ticksIntoPhase;
    }

    public int phaseDuration() {
        return this.phaseDuration;
    }

    public float peakIntensity() {
        return this.peakIntensity;
    }

    // --- Persistence ---

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putByte("phase", this.phase.id());
        tag.putInt("ticks_into_phase", this.ticksIntoPhase);
        tag.putInt("phase_duration", this.phaseDuration);
        tag.putFloat("peak", this.peakIntensity);
        tag.putInt("building", this.buildingDuration);
        tag.putInt("peak_duration", this.peakDuration);
        tag.putInt("dying", this.dyingDuration);
        return tag;
    }

    public static MercurySolarFlareState load(CompoundTag tag, HolderLookup.Provider registries) {
        MercurySolarFlareState state = new MercurySolarFlareState();
        state.phase = SolarFlarePhase.byId(tag.getByte("phase"));
        state.ticksIntoPhase = tag.getInt("ticks_into_phase");
        state.phaseDuration = tag.getInt("phase_duration");
        state.peakIntensity = tag.getFloat("peak");
        state.buildingDuration = tag.getInt("building");
        state.peakDuration = tag.getInt("peak_duration");
        state.dyingDuration = tag.getInt("dying");
        return state;
    }

    /** Fetches (or creates) the persistent solar-flare state for the given level. */
    public static MercurySolarFlareState get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(MercurySolarFlareState::new, MercurySolarFlareState::load, null),
                ID);
    }
}
