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

package dev.galacticraft.mod.gametest.machine;

import dev.galacticraft.machinelib.api.gametest.MachineGameTest;
import dev.galacticraft.machinelib.api.gametest.annotation.MachineTest;
import dev.galacticraft.machinelib.api.gametest.annotation.TestSuite;
import dev.galacticraft.machinelib.api.storage.slot.ItemResourceSlot;
import dev.galacticraft.mod.content.GCBlocks;
import dev.galacticraft.mod.content.block.entity.machine.CoalGeneratorBlockEntity;
import dev.galacticraft.mod.content.item.GCItems;
import dev.galacticraft.mod.machine.GCMachineStatuses;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;

import java.util.List;

@TestSuite("coal_generator")
public final class CoalGeneratorTestSuite extends MachineGameTest<CoalGeneratorBlockEntity> {
    /**
     * Simulates the Moon by disabling default breathability. Coal generators must still burn fuel.
     */
    private static final String AIRLESS = "galacticraft:coal_generator_airless";
    private static final int AIR_WINDOW = 20;

    private static boolean levelWasBreathable = true;

    public CoalGeneratorTestSuite() {
        super(GCBlocks.COAL_GENERATOR);
    }

    @BeforeBatch(batch = AIRLESS)
    public void pumpTheAirOut(ServerLevel level) {
        levelWasBreathable = level.getDefaultBreathable();
        level.setDefaultBreathable(false);
    }

    @AfterBatch(batch = AIRLESS)
    public void letTheAirBackIn(ServerLevel level) {
        level.setDefaultBreathable(levelWasBreathable);
    }

    /** Checked a second after lighting, so a generator that lit and then went out again is caught too. */
    @MachineTest(batch = AIRLESS, workTime = AIR_WINDOW)
    public Runnable makesPowerWithoutOxygen(CoalGeneratorBlockEntity machine) {
        ItemResourceSlot slot = machine.itemStorage().slot(CoalGeneratorBlockEntity.INPUT_SLOT);
        slot.set(Items.COAL, 1);
        return () -> {
            Assertions.assertTrue(slot.isEmpty(), "Did not burn coal with no oxygen around it");
            Assertions.assertNotEquals(0, machine.getFuelLength(), "Went out with no oxygen around it");
            Assertions.assertNotSame(GCMachineStatuses.NOT_ENOUGH_OXYGEN, machine.getState().getStatus(),
                    "Should not ask for oxygen");
            Assertions.assertTrue(machine.energyStorage().getAmount() > 0, "Made no power with no oxygen around it");
        };
    }

    @MachineTest
    public Runnable fuelBurning(CoalGeneratorBlockEntity machine) {
        ItemResourceSlot slot = machine.itemStorage().slot(CoalGeneratorBlockEntity.INPUT_SLOT);
        slot.set(Items.COAL, 1);
        return () -> Assertions.assertTrue(slot.isEmpty(), "Failed to consume fuel");
    }

    @MachineTest
    public Runnable fuelConsumption(CoalGeneratorBlockEntity machine) {
        ItemResourceSlot slot = machine.itemStorage().slot(CoalGeneratorBlockEntity.INPUT_SLOT);

        slot.set(Items.COAL, 1);
        return () -> Assertions.assertNotEquals(0, machine.getFuelLength(), "Failed to burn fuel");
    }

    @MachineTest
    public Runnable coalBlockFuel(CoalGeneratorBlockEntity machine) {
        ItemResourceSlot slot = machine.itemStorage().slot(CoalGeneratorBlockEntity.INPUT_SLOT);
        slot.set(Items.COAL_BLOCK, 1);
        return () -> Assertions.assertTrue(slot.isEmpty(), "Failed to consume a coal block");
    }

    @MachineTest
    public Runnable furnaceFuel(CoalGeneratorBlockEntity machine) {
        ItemResourceSlot slot = machine.itemStorage().slot(CoalGeneratorBlockEntity.INPUT_SLOT);
        slot.set(Items.OAK_LOG, 1);
        return () -> Assertions.assertTrue(slot.isEmpty(), "Failed to consume a furnace fuel");
    }

    @MachineTest(workTime = 320)
    public Runnable multipleFuelBurning(CoalGeneratorBlockEntity machine) {
        ItemResourceSlot slot = machine.itemStorage().slot(CoalGeneratorBlockEntity.INPUT_SLOT);

        slot.set(Items.COAL, 2);
        return () -> Assertions.assertTrue(slot.isEmpty(), "Failed to consume two coals!");
    }

    @MachineTest(workTime = 250)
    public Runnable heat(CoalGeneratorBlockEntity machine) {
        machine.setFuelLength(CoalGeneratorBlockEntity.FUEL_MAP.getInt(Items.COAL));
        return () -> Assertions.assertEquals(1, machine.getHeat(), "Coal generator did not heat up!");
    }

    @MachineTest(workTime = 50)
    public Runnable cool(CoalGeneratorBlockEntity machine) {
        machine.setHeat(1.0);
        return () -> Assertions.assertEquals(0.0, machine.getHeat(), "Coal generator did not cool down!");
    }

    @Override
    @GameTestGenerator
    public @NotNull List<TestFunction> registerTests() {
        List<TestFunction> tests = super.registerTests();
        tests.add(this.createDrainToEnergyItemTest(CoalGeneratorBlockEntity.CHARGE_SLOT, GCItems.BATTERY));
        return tests;
    }
}
