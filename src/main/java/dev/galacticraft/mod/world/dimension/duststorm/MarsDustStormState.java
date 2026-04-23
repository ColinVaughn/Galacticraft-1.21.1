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

package dev.galacticraft.mod.world.dimension.duststorm;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Server-authoritative, persistent state of the Mars dust-storm cycle for one dimension.
 *
 * <p>Advanced once per Mars world tick by {@code MarsDustStormManager}. Holds the current
 * {@link DustStormPhase}, how far into that phase we are, the target peak intensity, and the
 * pre-rolled BUILDING/PEAK/DYING lengths of the active storm. All fields are plain primitives so
 * the NBT round-trip does not need the registry lookup.
 */
public class MarsDustStormState extends SavedData {
    public static final String ID = "galacticraft_dust_storm";

    /** Length of the forecast (INCOMING) window in ticks (~35s at 20 tps). */
    public static final int INCOMING_TICKS = 700;

    private DustStormPhase phase = DustStormPhase.CLEAR;
    private int ticksIntoPhase = 0;
    /** Length of the current phase; in CLEAR this is the wait until the next storm. */
    private int phaseDuration = 0;
    private float peakIntensity = 0.0f;
    private int buildingDuration = 0;
    private int peakDuration = 0;
    private int dyingDuration = 0;

    public MarsDustStormState() {
        super();
    }

    /**
     * Advances the storm cycle by one tick.
     *
     * @return {@code true} if the phase changed this tick (callers should re-sync clients).
     */
    public boolean tick(RandomSource random, DustStormTuning tuning) {
        // Lazily schedule the first clear-weather interval for a fresh (unscheduled) state.
        if (this.phase == DustStormPhase.CLEAR && this.phaseDuration <= 0) {
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

    private void advance(RandomSource random, DustStormTuning tuning) {
        switch (this.phase) {
            case CLEAR -> {
                // Schedule a new storm: roll its peak and split its total length into ramps + peak.
                this.peakIntensity = Mth.clamp((0.6f + random.nextFloat() * 0.4f) * tuning.intensityMul(), 0.0f, 1.0f);
                int span = Math.max(1, tuning.maxDuration() - tuning.minDuration() + 1);
                int total = tuning.minDuration() + random.nextInt(span);
                this.buildingDuration = Math.max(1, Math.round(total * 0.2f));
                this.dyingDuration = Math.max(1, Math.round(total * 0.2f));
                this.peakDuration = Math.max(1, total - this.buildingDuration - this.dyingDuration);
                this.phase = DustStormPhase.INCOMING;
                this.phaseDuration = INCOMING_TICKS;
            }
            case INCOMING -> {
                this.phase = DustStormPhase.BUILDING;
                this.phaseDuration = this.buildingDuration;
            }
            case BUILDING -> {
                this.phase = DustStormPhase.PEAK;
                this.phaseDuration = this.peakDuration;
            }
            case PEAK -> {
                this.phase = DustStormPhase.DYING;
                this.phaseDuration = this.dyingDuration;
            }
            case DYING -> {
                this.phase = DustStormPhase.CLEAR;
                this.phaseDuration = rollInterval(random, tuning);
                this.peakIntensity = 0.0f;
                this.buildingDuration = 0;
                this.peakDuration = 0;
                this.dyingDuration = 0;
            }
        }
    }

    /** Rolls a clear-weather wait centred on the mean interval (average ≈ meanInterval). */
    private static int rollInterval(RandomSource random, DustStormTuning tuning) {
        int mean = Math.max(1, tuning.meanInterval());
        return Math.max(1, mean / 2 + random.nextInt(mean));
    }

    /** The current dust intensity in {@code [0, 1]} derived from phase + progress. */
    public float currentIntensity() {
        return DustStormCurve.intensity(this.phase, this.ticksIntoPhase, this.phaseDuration, this.peakIntensity);
    }

    /** Ticks remaining until the storm fully clears (0 when no storm is active). */
    public int remainingStormTicks() {
        return switch (this.phase) {
            case BUILDING -> (this.phaseDuration - this.ticksIntoPhase) + this.peakDuration + this.dyingDuration;
            case PEAK -> (this.phaseDuration - this.ticksIntoPhase) + this.dyingDuration;
            case DYING -> this.phaseDuration - this.ticksIntoPhase;
            default -> 0;
        };
    }

    /** Ticks remaining in the forecast window (0 unless INCOMING). */
    public int ticksUntilStorm() {
        return this.phase == DustStormPhase.INCOMING ? Math.max(0, this.phaseDuration - this.ticksIntoPhase) : 0;
    }

    public DustStormPhase phase() {
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

    // --- Debug hooks (used by the /duststorm command) ---

    /** Jumps straight into a storm at the given peak, skipping the forecast window. */
    public void debugStart(RandomSource random, DustStormTuning tuning, float peak) {
        int span = Math.max(1, tuning.maxDuration() - tuning.minDuration() + 1);
        int total = tuning.minDuration() + random.nextInt(span);
        this.buildingDuration = Math.max(1, Math.round(total * 0.2f));
        this.dyingDuration = Math.max(1, Math.round(total * 0.2f));
        this.peakDuration = Math.max(1, total - this.buildingDuration - this.dyingDuration);
        this.peakIntensity = Mth.clamp(peak, 0.0f, 1.0f);
        this.phase = DustStormPhase.BUILDING;
        this.phaseDuration = this.buildingDuration;
        this.ticksIntoPhase = 0;
        this.setDirty();
    }

    /** Forces the forecast window to begin now. */
    public void debugForecast(RandomSource random, DustStormTuning tuning) {
        this.phase = DustStormPhase.CLEAR;
        this.phaseDuration = 1;
        this.ticksIntoPhase = 1; // next tick expires CLEAR and schedules the storm
        this.setDirty();
    }

    /** Immediately ends any storm and returns to clear weather. */
    public void debugStop(RandomSource random, DustStormTuning tuning) {
        this.phase = DustStormPhase.CLEAR;
        this.phaseDuration = rollInterval(random, tuning);
        this.ticksIntoPhase = 0;
        this.peakIntensity = 0.0f;
        this.buildingDuration = 0;
        this.peakDuration = 0;
        this.dyingDuration = 0;
        this.setDirty();
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

    public static MarsDustStormState load(CompoundTag tag, HolderLookup.Provider registries) {
        MarsDustStormState state = new MarsDustStormState();
        state.phase = DustStormPhase.byId(tag.getByte("phase"));
        state.ticksIntoPhase = tag.getInt("ticks_into_phase");
        state.phaseDuration = tag.getInt("phase_duration");
        state.peakIntensity = tag.getFloat("peak");
        state.buildingDuration = tag.getInt("building");
        state.peakDuration = tag.getInt("peak_duration");
        state.dyingDuration = tag.getInt("dying");
        return state;
    }

    /** Fetches (or creates) the persistent dust-storm state for the given level. */
    public static MarsDustStormState get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(MarsDustStormState::new, MarsDustStormState::load, null),
                ID);
    }
}
