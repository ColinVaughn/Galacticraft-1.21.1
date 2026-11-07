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

import dev.galacticraft.machinelib.api.gametest.RecipeGameTest;
import dev.galacticraft.machinelib.api.gametest.annotation.MachineTest;
import dev.galacticraft.machinelib.api.gametest.annotation.TestSuite;
import dev.galacticraft.machinelib.api.storage.slot.ItemResourceSlot;
import dev.galacticraft.mod.content.GCBlocks;
import dev.galacticraft.mod.content.block.entity.machine.CompressorBlockEntity;
import dev.galacticraft.mod.machine.GCMachineStatuses;
import dev.galacticraft.mod.recipe.CompressingRecipe;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;

import java.util.List;

@TestSuite("compressor")
public final class CompressorTestSuite extends RecipeGameTest<CraftingInput, CompressingRecipe, CompressorBlockEntity> {
    /**
     * Simulates the Moon by disabling default breathability. Compressors must still burn fuel.
     */
    private static final String AIRLESS = "galacticraft:compressor_airless";
    private static final int AIR_WINDOW = 20;

    private static boolean levelWasBreathable = true;

    public CompressorTestSuite() {
        super(GCBlocks.COMPRESSOR, List.of(
                machine -> machine.fuelLength = machine.fuelTime = 5000,
                machine -> machine.itemStorage().slot(CompressorBlockEntity.INPUT_SLOTS).set(Items.IRON_INGOT, 1),
                machine -> machine.itemStorage().slot(CompressorBlockEntity.INPUT_SLOTS + 1).set(Items.IRON_INGOT, 1)
        ), CompressorBlockEntity.OUTPUT_SLOT, 200);
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

    /** Checked a second after lighting, so a compressor that lit and then went out again is caught too. */
    @MachineTest(batch = AIRLESS, workTime = AIR_WINDOW)
    public Runnable compressesWithoutOxygen(CompressorBlockEntity machine) {
        ItemResourceSlot fuel = machine.itemStorage().slot(CompressorBlockEntity.FUEL_SLOT);
        fuel.set(Items.COAL, 1);
        machine.itemStorage().slot(CompressorBlockEntity.INPUT_SLOTS).set(Items.IRON_INGOT, 1);
        machine.itemStorage().slot(CompressorBlockEntity.INPUT_SLOTS + 1).set(Items.IRON_INGOT, 1);
        return () -> {
            Assertions.assertTrue(fuel.isEmpty(), "Did not burn fuel with no oxygen around it");
            Assertions.assertNotEquals(0, machine.getFuelLength(), "Went out with no oxygen around it");
            Assertions.assertNotSame(GCMachineStatuses.NOT_ENOUGH_OXYGEN, machine.getState().getStatus(),
                    "Should not ask for oxygen");
            Assertions.assertTrue(machine.getProgress() > 0, "Made no progress with no oxygen around it");
        };
    }

    @MachineTest
    public Runnable fuelConsumption(CompressorBlockEntity machine) {
        ItemResourceSlot slot = machine.itemStorage().slot(CompressorBlockEntity.FUEL_SLOT);
        slot.set(Items.COAL, 1);
        machine.itemStorage().slot(CompressorBlockEntity.INPUT_SLOTS).set(Items.IRON_INGOT, 1);
        machine.itemStorage().slot(CompressorBlockEntity.INPUT_SLOTS + 1).set(Items.IRON_INGOT, 1);
        return () -> {
            Assertions.assertTrue(slot.isEmpty(), "Failed to consume fuel");
            Assertions.assertNotEquals(0, machine.getFuelLength(), "Failed to burn fuel");
        };
    }

    @Override
    @GameTestGenerator
    public @NotNull List<TestFunction> registerTests() {
        return super.registerTests();
    }
}
