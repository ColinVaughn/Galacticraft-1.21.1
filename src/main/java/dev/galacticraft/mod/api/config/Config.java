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

    boolean dustStormsEnabled();

    int dustStormMeanInterval();

    int dustStormMinDuration();

    int dustStormMaxDuration();

    float dustStormIntensity();

    boolean dustStormDamage();

    float dustStormSolarPenalty();

    boolean machineDustEnabled();

    boolean terrainDustEnabled();

    double bossHealthMultiplier();

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
