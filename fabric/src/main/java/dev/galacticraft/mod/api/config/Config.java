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

package dev.galacticraft.mod.api.config;

import net.minecraft.resources.ResourceLocation;

import java.util.List;

public interface Config {
    boolean isAlphaWarningHidden();

    boolean isDebugLogEnabled();

    long wireTransferLimit();

    long heavyWireTransferLimit();

    long machineEnergyStorageSize();

    long energyStorageModuleStorageSize();

    long coalGeneratorEnergyProductionRate();

    long solarPanelEnergyProductionRate();

    long circuitFabricatorEnergyConsumptionRate();

    long electricCompressorEnergyConsumptionRate();

    long electricFurnaceEnergyConsumptionRate();

    long electricArcFurnaceEnergyConsumptionRate();

    float electricArcFurnaceBonusChance();

    long oxygenCollectorEnergyConsumptionRate();

    long oxygenCompressorEnergyConsumptionRate();

    long oxygenDecompressorEnergyConsumptionRate();

    long oxygenSealerEnergyConsumptionRate();

    long oxygenSealerOxygenConsumptionRate();

    long oxygenSealerUnsealedOxygenConsumptionRate();

    long maxSealingPower();

    long refineryEnergyConsumptionRate();

    long fuelLoaderEnergyConsumptionRate();

    long foodCannerEnergyConsumptionRate();

    int astroMinerMax();

    boolean squareCannedFood();

    long fluidCanisterCapacity();

    long smallOxygenTankCapacity();

    long mediumOxygenTankCapacity();

    long largeOxygenTankCapacity();

    long playerOxygenConsumptionRate();

    long wolfOxygenConsumptionRate();

    long catOxygenConsumptionRate();

    long parrotOxygenConsumptionRate();

    boolean cannotEatWithMask();

    boolean cannotEatInNoAtmosphere();

    float meteorSpawnMultiplier();

    boolean meteorsEnabled();

    int meteorSporadicInterval();

    int meteorShowerMeanInterval();

    int meteorShowerMinDuration();

    int meteorShowerMaxDuration();

    float meteorShowerIntensity();

    float meteorShowerPeakMultiplier();

    int meteorMaxConcurrent();

    int meteorMaxCraterRadius();

    boolean meteorImpactBlockDamage();

    /**
     * Dimension IDs (as strings, e.g. {@code "minecraft:overworld"}) that do the opposite of
     * {@link #meteorImpactBlockDamage()}. Lets a pack leave craters on for the planets while
     * sparing the Overworld, or the reverse. Impacts in a spared dimension still flash, hurt and
     * leave their meteorite; only the terrain is protected.
     */
    List<String> meteorImpactBlockDamageExceptions();

    boolean meteorFragmentation();

    boolean dustStormsEnabled();

    int dustStormMeanInterval();

    int dustStormMinDuration();

    int dustStormMaxDuration();

    float dustStormIntensity();

    boolean dustStormDamage();

    float dustStormSolarPenalty();

    boolean solarFlaresEnabled();

    int solarFlareMeanInterval();

    int solarFlareMinDuration();

    int solarFlareMaxDuration();

    float solarFlareIntensity();

    boolean solarFlareDamage();

    boolean machineDustEnabled();

    boolean terrainDustEnabled();

    double bossHealthMultiplier();

    /**
     * Capacity of a rocket's fuel tank, in buckets. Applied when a rocket is constructed, so rockets
     * already present in a save keep the capacity they were built with.
     */
    int rocketFuelTankCapacity();

    /**
     * Ticks of powered flight one bucket of fuel provides. Raising this makes every launch cost
     * proportionally less fuel; at the default of 50 an Earth launch burns roughly 32 buckets.
     */
    int rocketBurnTicksPerBucket();

    /**
     * Buckets of fuel a refinery yields per bucket of crude oil. Raising this makes fuel cheaper to
     * produce without changing how far a bucket goes.
     */
    double refineryOilToFuelRatio();

    boolean enableGcHouston();

    boolean enableCreativeGearInv();

    boolean disableSpaceStationCreation();

    /**
     * Celestial body IDs (as strings, e.g. {@code "galacticraft:earth"}) where space stations may be created.
     * An empty list means every orbitable body is allowed (backward-compatible default).
     */
    List<String> spaceStationAllowedBodies();

    /**
     * Celestial body IDs (as strings) whose space stations all share a single communal dimension,
     * with each player's station scattered at a random far-apart location. Bodies not listed here
     * use the default behavior of one private dimension per player.
     */
    List<String> spaceStationSharedBodies();

    /**
     * Whether a space station may be created orbiting the given body, honoring both the master
     * {@link #disableSpaceStationCreation()} switch and the {@link #spaceStationAllowedBodies()} allow-list.
     */
    default boolean isSpaceStationCreationAllowed(ResourceLocation bodyId) {
        if (this.disableSpaceStationCreation()) {
            return false;
        }
        List<String> allowed = this.spaceStationAllowedBodies();
        return allowed.isEmpty() || allowed.contains(bodyId.toString());
    }

    /**
     * Whether stations orbiting the given body share a single communal dimension.
     */
    default boolean isSpaceStationShared(ResourceLocation bodyId) {
        return this.spaceStationSharedBodies().contains(bodyId.toString());
    }

    void load();

    void save();
}
