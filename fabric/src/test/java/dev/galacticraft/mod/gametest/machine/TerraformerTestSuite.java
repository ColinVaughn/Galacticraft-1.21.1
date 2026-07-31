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
import dev.galacticraft.mod.content.GCBlocks;
import dev.galacticraft.mod.content.block.entity.machine.TerraformerBlockEntity;
import dev.galacticraft.mod.content.item.GCItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;

import java.util.List;

@TestSuite("terraformer")
public final class TerraformerTestSuite extends MachineGameTest<TerraformerBlockEntity> {
    private static final BlockPos TARGET = new BlockPos(2, 1, 1);

    public TerraformerTestSuite() {
        super(GCBlocks.TERRAFORMER);
    }

    @Override
    @GameTestGenerator
    public @NotNull List<TestFunction> registerTests() {
        List<TestFunction> tests = super.registerTests();
        tests.add(this.createChargeFromEnergyItemTest(TerraformerBlockEntity.CHARGE_SLOT, GCItems.INFINITE_BATTERY));
        tests.add(this.createTakeFromFluidItemTest(
                TerraformerBlockEntity.WATER_CONTAINER_SLOT,
                Items.WATER_BUCKET,
                TerraformerBlockEntity.WATER_TANK
        ));
        return tests;
    }

    @MachineTest
    public Runnable waterBucketInvalidatesSlotCache(TerraformerBlockEntity machine) {
        var waterInput = machine.itemStorage().slot(TerraformerBlockEntity.WATER_CONTAINER_SLOT);
        waterInput.set(Items.WATER_BUCKET, 1);
        long modifications = waterInput.getModifications();

        return () -> {
            Assertions.assertEquals(Items.BUCKET, waterInput.getResource(),
                    "Expected the water bucket to be emptied");
            Assertions.assertTrue(waterInput.getModifications() > modifications,
                    "Expected the emptied bucket to invalidate the menu slot cache");
        };
    }

    @MachineTest(batch = "terraformer_ground", workTime = 170)
    public Runnable terraformsLegacyPlanetaryGround(TerraformerBlockEntity machine, GameTestHelper helper) {
        helper.setBlock(TARGET, GCBlocks.MARS_SURFACE_ROCK);
        machine.energyStorage().setEnergy(machine.energyStorage().getCapacity());
        machine.fluidStorage().slot(TerraformerBlockEntity.WATER_TANK)
                .set(Fluids.WATER, TerraformerBlockEntity.MAX_WATER);
        machine.itemStorage().slot(TerraformerBlockEntity.BONE_MEAL_SLOT_START).set(Items.BONE_MEAL, 8);
        machine.itemStorage().slot(TerraformerBlockEntity.SEED_SLOT_START).set(Items.WHEAT_SEEDS, 8);

        return () -> {
            boolean terraformed = helper.getBlockState(TARGET).is(Blocks.GRASS_BLOCK)
                    || helper.getBlockState(TARGET).is(Blocks.WATER);
            Assertions.assertTrue(terraformed, "Expected Mars surface rock to become grass or water");
            Assertions.assertEquals(TerraformerBlockEntity.MAX_SIZE, machine.getBubbleSize(),
                    "Expected the Legacy 15-block bubble to finish expanding");
            Assertions.assertTrue(
                    machine.fluidStorage().slot(TerraformerBlockEntity.WATER_TANK).getAmount()
                            < TerraformerBlockEntity.MAX_WATER,
                    "Expected terraforming to consume water"
            );
        };
    }

    @MachineTest(batch = "terraformer_trees", workTime = 60)
    public Runnable plantsSaplingsInTreeOnlyMode(TerraformerBlockEntity machine, GameTestHelper helper) {
        helper.setBlock(TARGET, Blocks.GRASS_BLOCK);
        machine.setBubbleSize(TerraformerBlockEntity.MAX_SIZE);
        machine.toggle(1); // Disable grass, leaving trees enabled.
        machine.energyStorage().setEnergy(machine.energyStorage().getCapacity());
        machine.fluidStorage().slot(TerraformerBlockEntity.WATER_TANK)
                .set(Fluids.WATER, TerraformerBlockEntity.MAX_WATER);
        machine.itemStorage().slot(TerraformerBlockEntity.BONE_MEAL_SLOT_START).set(Items.BONE_MEAL, 8);
        machine.itemStorage().slot(TerraformerBlockEntity.SAPLING_SLOT_START).set(Items.OAK_SAPLING, 1);

        return () -> {
            Assertions.assertEquals(0,
                    machine.itemStorage().slot(TerraformerBlockEntity.SAPLING_SLOT_START).getAmount(),
                    "Expected one sapling to be consumed per planting");
            Assertions.assertEquals(
                    TerraformerBlockEntity.MAX_WATER - TerraformerBlockEntity.FIFTY_MILLIBUCKETS,
                    machine.fluidStorage().slot(TerraformerBlockEntity.WATER_TANK).getAmount(),
                    "Expected tree planting to consume exactly 50 mB"
            );
        };
    }
}
