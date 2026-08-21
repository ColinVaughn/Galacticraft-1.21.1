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

package dev.galacticraft.mod.world.dimension.meteor;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Server-authoritative, persistent state of one dimension's meteor shower cycle.
 *
 * Advanced once per world tick by {@link MeteorShowerManager}. Holds the current
 * {@link MeteorShowerPhase}, progress through it, the target peak intensity, the pre-rolled
 * phase lengths, and the shower's radiant - the direction in the sky its meteors appear to stream
 * from, which both the spawner and the sky-streak renderer read so they agree.
 *
 * All fields are plain primitives so the NBT round-trip needs no registry lookup.
 */
public class MeteorShowerState extends SavedData {
    public static final String ID = "galacticraft_meteor_shower";

    /** Length of the forecast (INCOMING) window in ticks (~30s at 20 tps). */
    public static final int INCOMING_TICKS = 600;

    private MeteorShowerPhase phase = MeteorShowerPhase.DORMANT;
    private int ticksIntoPhase = 0;
    /** Length of the current phase; while DORMANT this is the wait until the next shower. */
    private int phaseDuration = 0;
    private float peakIntensity = 0.0f;
    private int waxingDuration = 0;
    private int peakDuration = 0;
    private int waningDuration = 0;
    /** Compass bearing the shower streams from, in degrees. */
    private float radiantYaw = 0.0f;
    /** Elevation of the radiant above the horizon, in degrees. */
    private float radiantPitch = 45.0f;
    /** An op's answer for this dimension on whether strikes may damage terrain or entities. */
    private MeteorImpactRules.Override blockDamageOverride = MeteorImpactRules.Override.DEFAULT;

    public MeteorShowerState() {
        super();
    }

    /**
     * Advances the shower cycle by one tick.
     *
     * @return {@code true} if the phase changed this tick (callers should re-sync clients).
     */
    public boolean tick(RandomSource random, MeteorShowerTuning tuning) {
        // Lazily schedule the first dormant interval for a fresh (unscheduled) state.
        if (this.phase == MeteorShowerPhase.DORMANT && this.phaseDuration <= 0) {
            this.phaseDuration = rollInterval(random, tuning);
            this.ticksIntoPhase = 0;
            setDirty();
            return false;
        }

        this.ticksIntoPhase++;
        if (this.ticksIntoPhase < this.phaseDuration) {
            return false;
        }

        advance(random, tuning);
        this.ticksIntoPhase = 0;
        setDirty();
        return true;
    }

    private void advance(RandomSource random, MeteorShowerTuning tuning) {
        switch (this.phase) {
            case DORMANT -> {
                // Schedule a new shower: roll its peak, radiant, and split its length into ramps.
                this.peakIntensity = Mth.clamp((0.55f + random.nextFloat() * 0.45f) * tuning.intensityMul(), 0.0f, 1.0f);
                int span = Math.max(1, tuning.maxDuration() - tuning.minDuration() + 1);
                int total = tuning.minDuration() + random.nextInt(span);
                this.waxingDuration = Math.max(1, Math.round(total * 0.25f));
                this.waningDuration = Math.max(1, Math.round(total * 0.25f));
                this.peakDuration = Math.max(1, total - this.waxingDuration - this.waningDuration);
                this.radiantYaw = random.nextFloat() * 360.0f;
                this.radiantPitch = 25.0f + random.nextFloat() * 50.0f;
                this.phase = MeteorShowerPhase.INCOMING;
                this.phaseDuration = INCOMING_TICKS;
            }
            case INCOMING -> {
                this.phase = MeteorShowerPhase.WAXING;
                this.phaseDuration = this.waxingDuration;
            }
            case WAXING -> {
                this.phase = MeteorShowerPhase.PEAK;
                this.phaseDuration = this.peakDuration;
            }
            case PEAK -> {
                this.phase = MeteorShowerPhase.WANING;
                this.phaseDuration = this.waningDuration;
            }
            case WANING -> {
                this.phase = MeteorShowerPhase.DORMANT;
                this.phaseDuration = rollInterval(random, tuning);
                this.peakIntensity = 0.0f;
                this.waxingDuration = 0;
                this.peakDuration = 0;
                this.waningDuration = 0;
            }
        }
    }

    /** Rolls a dormant wait centred on the mean interval (average ~= meanInterval). */
    private static int rollInterval(RandomSource random, MeteorShowerTuning tuning) {
        int mean = Math.max(1, tuning.meanInterval());
        return Math.max(1, mean / 2 + random.nextInt(mean));
    }

    /** The current shower intensity in {@code [0, 1]} derived from phase + progress. */
    public float currentIntensity() {
        return MeteorShowerCurve.intensity(this.phase, this.ticksIntoPhase, this.phaseDuration, this.peakIntensity);
    }

    /** Ticks remaining until the shower fully subsides (0 when none is active). */
    public int remainingShowerTicks() {
        return switch (this.phase) {
            case WAXING -> (this.phaseDuration - this.ticksIntoPhase) + this.peakDuration + this.waningDuration;
            case PEAK -> (this.phaseDuration - this.ticksIntoPhase) + this.waningDuration;
            case WANING -> this.phaseDuration - this.ticksIntoPhase;
            default -> 0;
        };
    }

    /** Ticks remaining in the forecast window (0 unless INCOMING). */
    public int ticksUntilShower() {
        return this.phase == MeteorShowerPhase.INCOMING ? Math.max(0, this.phaseDuration - this.ticksIntoPhase) : 0;
    }

    public MeteorShowerPhase phase() {
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

    public float radiantYaw() {
        return this.radiantYaw;
    }

    public float radiantPitch() {
        return this.radiantPitch;
    }

    /** This dimension's block-damage override; {@code DEFAULT} leaves the decision to the config. */
    public MeteorImpactRules.Override blockDamageOverride() {
        return this.blockDamageOverride;
    }

    public void setBlockDamageOverride(MeteorImpactRules.Override override) {
        this.blockDamageOverride = override;
        setDirty();
    }

    // --- Debug hooks (used by the /meteorshower command) ---

    /** Jumps straight to the peak of a shower at the given intensity, skipping the forecast. */
    public void debugStart(RandomSource random, MeteorShowerTuning tuning, float peak) {
        int span = Math.max(1, tuning.maxDuration() - tuning.minDuration() + 1);
        int total = tuning.minDuration() + random.nextInt(span);
        this.waxingDuration = Math.max(1, Math.round(total * 0.25f));
        this.waningDuration = Math.max(1, Math.round(total * 0.25f));
        this.peakDuration = Math.max(1, total - this.waxingDuration - this.waningDuration);
        this.peakIntensity = Mth.clamp(peak, 0.0f, 1.0f);
        this.radiantYaw = random.nextFloat() * 360.0f;
        this.radiantPitch = 25.0f + random.nextFloat() * 50.0f;
        this.phase = MeteorShowerPhase.PEAK;
        this.phaseDuration = this.peakDuration;
        this.ticksIntoPhase = 0;
        setDirty();
    }

    /** Forces the forecast window to begin on the next tick. */
    public void debugForecast() {
        this.phase = MeteorShowerPhase.DORMANT;
        this.phaseDuration = 1;
        this.ticksIntoPhase = 1; // next tick expires DORMANT and schedules the shower
        setDirty();
    }

    /** Immediately ends any shower and returns to the sporadic background. */
    public void debugStop(RandomSource random, MeteorShowerTuning tuning) {
        this.phase = MeteorShowerPhase.DORMANT;
        this.phaseDuration = rollInterval(random, tuning);
        this.ticksIntoPhase = 0;
        this.peakIntensity = 0.0f;
        this.waxingDuration = 0;
        this.peakDuration = 0;
        this.waningDuration = 0;
        setDirty();
    }

    // --- Persistence ---

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putByte("phase", this.phase.id());
        tag.putInt("ticks_into_phase", this.ticksIntoPhase);
        tag.putInt("phase_duration", this.phaseDuration);
        tag.putFloat("peak", this.peakIntensity);
        tag.putInt("waxing", this.waxingDuration);
        tag.putInt("peak_duration", this.peakDuration);
        tag.putInt("waning", this.waningDuration);
        tag.putFloat("radiant_yaw", this.radiantYaw);
        tag.putFloat("radiant_pitch", this.radiantPitch);
        tag.putByte("block_damage_override", this.blockDamageOverride.id());
        return tag;
    }

    public static MeteorShowerState load(CompoundTag tag, HolderLookup.Provider registries) {
        MeteorShowerState state = new MeteorShowerState();
        state.phase = MeteorShowerPhase.byId(tag.getByte("phase"));
        state.ticksIntoPhase = tag.getInt("ticks_into_phase");
        state.phaseDuration = tag.getInt("phase_duration");
        state.peakIntensity = tag.getFloat("peak");
        state.waxingDuration = tag.getInt("waxing");
        state.peakDuration = tag.getInt("peak_duration");
        state.waningDuration = tag.getInt("waning");
        state.radiantYaw = tag.getFloat("radiant_yaw");
        state.radiantPitch = tag.contains("radiant_pitch") ? tag.getFloat("radiant_pitch") : 45.0f;
        // Absent in saves written before the override existed, and a missing byte reads as 0 (DEFAULT).
        state.blockDamageOverride = MeteorImpactRules.Override.byId(tag.getByte("block_damage_override"));
        return state;
    }

    /** Fetches (or creates) the persistent shower state for the given level. */
    public static MeteorShowerState get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(MeteorShowerState::new, MeteorShowerState::load, null),
                ID);
    }
}
