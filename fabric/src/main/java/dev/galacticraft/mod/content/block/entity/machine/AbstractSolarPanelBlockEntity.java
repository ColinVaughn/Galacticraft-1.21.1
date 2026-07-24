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

import dev.galacticraft.api.universe.celestialbody.CelestialBody;
import dev.galacticraft.machinelib.api.block.entity.MachineBlockEntity;
import dev.galacticraft.machinelib.api.machine.MachineStatus;
import dev.galacticraft.machinelib.api.machine.MachineStatuses;
import dev.galacticraft.machinelib.api.storage.StorageSpec;
import dev.galacticraft.machinelib.api.util.EnergySource;
import dev.galacticraft.mod.Galacticraft;
import dev.galacticraft.mod.api.block.entity.Dustable;
import dev.galacticraft.mod.api.block.entity.SolarPanel;
import dev.galacticraft.mod.machine.GCMachineStatuses;
import dev.galacticraft.mod.world.dimension.duststorm.MarsDustStormManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

import java.util.function.IntPredicate;

public abstract class AbstractSolarPanelBlockEntity extends MachineBlockEntity implements SolarPanel, Dustable {
    private static final int BLOCKAGE_SCAN_INTERVAL = 20;
    /** Shade search range for skylight-free dimensions: ignores the distant asteroid field, catches a built roof. */
    static final int NO_SKYLIGHT_SCAN_HEIGHT = 16;

    public static final float SPEED = Mth.DEG_TO_RAD * 0.5F;
    public static final float DAWN = 4.0F * Mth.PI / 3.0F;
    public static final float SUNRISE = 1.5F * Mth.PI;
    public static final float NOON = 0.0F;
    public static final float SUNSET = 0.5F * Mth.PI;
    public static final float DUSK = 2.0F * Mth.PI / 3.0F;
    public static final float MIDNIGHT = Mth.PI;
    public static final float MIN = 5.0F * Mth.PI / 3.0F;
    public static final float MAX = Mth.PI / 3.0F;

    public static final int CHARGE_SLOT = 0;
    protected final boolean[] blockage = new boolean[9];
    protected int blocked = 0;
    private boolean blockageScanInitialized;
    private final EnergySource energySource = new EnergySource(this);
    public long currentEnergyGeneration = 0;
    private long dayLength = 24000;
    private float tilt = NOON;
    // Martian dust that settles on the panel during storms and lingers, cutting output until it weathers off.
    private float dustLevel = 0.0f;

    public AbstractSolarPanelBlockEntity(BlockEntityType<? extends AbstractSolarPanelBlockEntity> type, BlockPos pos, BlockState state, StorageSpec spec) {
        super(type, pos, state, spec);
    }

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        Holder<CelestialBody<?, ?>> holder = level.galacticraft$getCelestialBody();
        if (holder != null) {
            this.dayLength = holder.value().dayLength();
        }
    }

    @Override
    public void tickConstant(@NotNull ServerLevel level, @NotNull BlockPos pos, @NotNull BlockState state, @NotNull ProfilerFiller profiler) {
        profiler.push("charge");
        this.drainPowerToSlot(CHARGE_SLOT);
        profiler.popPush("blockage");
        if (!this.blockageScanInitialized
                || (level.getGameTime() + pos.asLong()) % BLOCKAGE_SCAN_INTERVAL == 0) {
            this.rescanBlockage(level, pos);
        }
        profiler.popPush("dust");
        if (Galacticraft.CONFIG.machineDustEnabled()) {
            this.tickDust(level);
        }
        profiler.pop();
    }

    /** Re-samples the nine plate positions to work out how much of the panel is shaded. */
    private void rescanBlockage(@NotNull ServerLevel level, @NotNull BlockPos pos) {
        this.blocked = 0;
        boolean hasSkyLight = level.dimensionType().hasSkyLight();
        for (int x = -1; x < 2; x++) {
            for (int z = -1; z < 2; z++) {
                BlockPos sample = pos.offset(x, 2, z);
                //noinspection AssignmentUsedAsCondition
                if (this.blockage[(z + 1) * 3 + (x + 1)] = hasSkyLight
                        ? !level.canSeeSky(sample)
                        : isShaded(level, sample)) {
                    this.blocked++;
                }
            }
        }
        this.blockageScanInitialized = true;
    }

    /**
     * Sky brightness is fixed at zero in dimensions without skylight, so {@link Level#canSeeSky}
     * reports every position as blocked there and a panel would never generate. Walk the column
     * overhead instead, counting only blocks that absorb light.
     */
    private static boolean isShaded(@NotNull ServerLevel level, @NotNull BlockPos sample) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        return isColumnShaded(sample.getY(), level.getMaxBuildHeight(), y -> {
            cursor.set(sample.getX(), y, sample.getZ());
            return level.getBlockState(cursor).getLightBlock(level, cursor) > 0;
        });
    }

    /**
     * Scans a bounded column above a panel plate for shade. Starts one block above {@code sampleY}
     * because the sampled positions are the panel's own multiblock parts.
     */
    static boolean isColumnShaded(int sampleY, int worldTop, IntPredicate absorbsLight) {
        int from = sampleY + 1;
        int to = Math.min(worldTop, from + NO_SKYLIGHT_SCAN_HEIGHT);
        for (int y = from; y < to; y++) {
            if (absorbsLight.test(y)) return true;
        }
        return false;
    }

    /** Accrues dust while a storm blows over a sky-exposed panel; slowly weathers it off when clear. */
    private void tickDust(@NotNull ServerLevel level) {
        float intensity = MarsDustStormManager.currentIntensity(level);
        float before = this.dustLevel;
        if (intensity > 0.0f && this.blocked < 9) {
            this.dustLevel = Math.min(1.0f, this.dustLevel + intensity * 0.0006f);
        } else if (intensity <= 0.0f && this.dustLevel > 0.0f) {
            this.dustLevel = Math.max(0.0f, this.dustLevel - 0.00008f);
        }
        if (this.dustLevel != before) this.setChanged();
    }

    @Override
    public @NotNull MachineStatus tick(@NotNull ServerLevel level, @NotNull BlockPos pos, @NotNull BlockState state, @NotNull ProfilerFiller profiler) {
        profiler.push("push_energy");
        this.energySource.trySpreadEnergy(level, pos, state);
        profiler.pop();
        if (this.blocked >= 9) return GCMachineStatuses.BLOCKED;
        if (this.energyStorage().isFull()) return MachineStatuses.CAPACITOR_FULL;
        MachineStatus status = null;
        double multiplier = this.blocked == 0 ? 1 : (9.0 - this.blocked) / 9.0;
        if (this.blocked > 0) status = GCMachineStatuses.PARTIALLY_GENERATING;
        if (level.isThundering()) {
            if (status == null) status = GCMachineStatuses.PARTIALLY_GENERATING;
            multiplier *= 0.1;
        } else if (level.isRaining()) {
            if (status == null) status = GCMachineStatuses.PARTIALLY_GENERATING;
            multiplier *= 0.5;
        }
        // A Mars dust storm blots out the sun, cutting solar output while it rages.
        double stormMultiplier = MarsDustStormManager.solarMultiplier(level);
        if (stormMultiplier < 1.0) {
            if (status == null) status = GCMachineStatuses.PARTIALLY_GENERATING;
            multiplier *= stormMultiplier;
        }
        // Dust that has settled on the panel keeps output down even after the storm passes.
        if (this.dustLevel > 0.0f) {
            if (status == null) status = GCMachineStatuses.PARTIALLY_GENERATING;
            multiplier *= (1.0 - this.dustLevel * 0.8);
        }
        long time = level.getDayTime() % this.dayLength;
        // Don't use this.isDay() because it returns false when it is thundering
        if (time > this.dayLength / 2) status = GCMachineStatuses.NOT_GENERATING;
        if (time > this.dayLength / 4) time = this.dayLength / 2 - time;

        profiler.push("transaction");
        this.currentEnergyGeneration = this.calculateEnergyProduction(time, multiplier);
        this.energyStorage().insert(this.currentEnergyGeneration);
        profiler.pop();
        return status == null ? GCMachineStatuses.GENERATING : status;
    }

    protected abstract long calculateEnergyProduction(long time, double multiplier);

    @Override
    public float getTilt(float tickDelta) {
        float angle = NOON;
        if (!this.followsSun()) return angle;

        if (this.isActive()) {
            // Angle in radians - 0 noon, pi/2 sunset, pi midnight, 3pi/2 sunrise
            angle = this.level.getSunAngle(tickDelta);

            if (angle > DUSK && angle < DAWN) {
                if (this.nightCollection()) {
                    angle -= Mth.PI;
                } else {
                    angle = NOON;
                }
            } else if ((angle > SUNSET && angle <= DUSK) || (angle >= DAWN && angle < SUNRISE)) {
                if (this.nightCollection()) {
                    angle = -MAX;
                } else {
                    angle = NOON;
                }
            } else if (angle >= SUNRISE && angle < MIN) {
                angle = -MAX;
            } else if (angle <= SUNSET && angle > MAX) {
                angle = MAX;
            } else if (angle >= SUNRISE) {
                angle -= 2 * Mth.PI;
            }
        }

        this.tilt += Mth.clamp(angle - this.tilt, -SPEED, SPEED);
        return this.tilt;
    }

    @Override
    public boolean @NotNull [] getBlockage() {
        return this.blockage;
    }

    @Override
    public SolarPanelSource getSource() {
        if (this.level.dimensionType().hasCeiling()) return SolarPanelSource.NO_LIGHT_SOURCE;
        // Don't use this.isDay() because it returns false when it is thundering
        if ((this.level.getDayTime() % this.dayLength) > this.dayLength / 2) return SolarPanelSource.NIGHT;
        if (this.level.isThundering()) return SolarPanelSource.STORMY;
        if (this.level.isRaining()) return SolarPanelSource.OVERCAST;
        return SolarPanelSource.DAY;
    }

    @Override
    public long getCurrentEnergyGeneration() {
        return this.currentEnergyGeneration;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
        super.saveAdditional(tag, lookup);
        tag.putFloat("DustLevel", this.dustLevel);
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
        super.loadAdditional(tag, lookup);
        this.dustLevel = tag.getFloat("DustLevel");
    }

    @Override
    public float galacticraft$getDustLevel() {
        return this.dustLevel;
    }

    @Override
    public void galacticraft$setDustLevel(float level) {
        this.dustLevel = Mth.clamp(level, 0.0f, 1.0f);
        this.setChanged();
    }
}
