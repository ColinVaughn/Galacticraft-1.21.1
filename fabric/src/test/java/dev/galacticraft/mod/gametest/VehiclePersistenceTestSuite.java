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

package dev.galacticraft.mod.gametest;

import dev.galacticraft.mod.api.block.entity.FuelDock;
import dev.galacticraft.mod.api.entity.Dockable;
import dev.galacticraft.mod.content.GCEntityTypes;
import dev.galacticraft.mod.content.entity.vehicle.Buggy;
import dev.galacticraft.mod.content.entity.vehicle.CargoRocketEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

public final class VehiclePersistenceTestSuite implements GalacticraftGameTest {
    @GameTest(template = EMPTY_STRUCTURE)
    public void buggyCanBeCreatedThroughEntityType(GameTestHelper context) {
        Buggy buggy = GCEntityTypes.BUGGY.create(context.getLevel());
        if (buggy == null) {
            context.fail("Buggy entity type returned null");
        } else if (buggy.getVariant() != Buggy.BuggyType.NORMAL) {
            context.fail("New buggy did not use the default variant");
        } else if (buggy.getVehicleInventory().getContainerSize() != 0) {
            context.fail("New default buggy had an unexpected inventory size");
        } else {
            context.succeed();
        }
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void buggyVariantAndInventoryPersist(GameTestHelper context) {
        TestBuggy original = new TestBuggy(context.getLevel());
        original.setVariant(Buggy.BuggyType.STORAGE_36);
        original.getVehicleInventory().setItem(35, new ItemStack(Items.DIAMOND, 7));
        CompoundTag saved = new CompoundTag();
        original.writeData(saved);

        TestBuggy restored = new TestBuggy(context.getLevel());
        restored.readData(saved);
        if (restored.getVariant() != Buggy.BuggyType.STORAGE_36) {
            context.fail("Buggy storage variant did not persist");
        } else if (restored.getVehicleInventory().getContainerSize() != 36) {
            context.fail("Buggy did not restore its 36-slot inventory");
        } else if (!restored.getVehicleInventory().getItem(35).is(Items.DIAMOND)
                || restored.getVehicleInventory().getItem(35).getCount() != 7) {
            context.fail("Buggy inventory contents did not persist");
        } else {
            context.succeed();
        }
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void cargoInventoryAndFlightStatePersist(GameTestHelper context) {
        TestCargoRocket original = new TestCargoRocket(context.getLevel());
        original.getVehicleInventory().setItem(26, new ItemStack(Items.DIAMOND, 11));
        CompoundTag loading = new CompoundTag();
        original.writeData(loading);
        loading.putString("FlightState", "returning");
        original.readData(loading);

        CompoundTag saved = new CompoundTag();
        original.writeData(saved);
        TestCargoRocket restored = new TestCargoRocket(context.getLevel());
        restored.readData(saved);
        if (restored.getFlightState() != CargoRocketEntity.FlightState.RETURNING) {
            context.fail("Cargo rocket flight state did not persist");
        } else if (!restored.getVehicleInventory().getItem(26).is(Items.DIAMOND)
                || restored.getVehicleInventory().getItem(26).getCount() != 11) {
            context.fail("Cargo rocket inventory contents did not persist");
        } else {
            context.succeed();
        }
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void cargoRocketUsesLegacySizeAndPadPosition(GameTestHelper context) {
        CargoRocketEntity rocket = GCEntityTypes.CARGO_ROCKET.create(context.getLevel());
        if (rocket == null) {
            context.fail("Cargo rocket entity type returned null");
            return;
        }

        BlockPos padPos = new BlockPos(2, 3, 4);
        rocket.placeOnPad(new TestFuelDock(padPos));
        if (Math.abs(rocket.getBbWidth() - 0.98F) > 0.0001F || Math.abs(rocket.getBbHeight() - 2.0F) > 0.0001F) {
            context.fail("Cargo rocket did not use the legacy collision dimensions");
        } else if (Math.abs(rocket.getX() - 2.5D) > 0.0001D
                || Math.abs(rocket.getY() - 3.35D) > 0.0001D
                || Math.abs(rocket.getZ() - 4.5D) > 0.0001D) {
            context.fail("Cargo rocket did not use the legacy launch-pad offset");
        } else {
            context.succeed();
        }
    }

    private static final class TestBuggy extends Buggy {
        private TestBuggy(Level level) {
            super(GCEntityTypes.BUGGY, level);
        }

        private void readData(CompoundTag tag) {
            super.readAdditionalSaveData(tag);
        }

        private void writeData(CompoundTag tag) {
            super.addAdditionalSaveData(tag);
        }
    }

    private static final class TestCargoRocket extends CargoRocketEntity {
        private TestCargoRocket(Level level) {
            super(GCEntityTypes.CARGO_ROCKET, level);
        }

        private void readData(CompoundTag tag) {
            super.readAdditionalSaveData(tag);
        }

        private void writeData(CompoundTag tag) {
            super.addAdditionalSaveData(tag);
        }
    }

    private record TestFuelDock(BlockPos pos) implements FuelDock {
        @Override
        public BlockPos getDockPos() {
            return this.pos;
        }

        @Override
        public Dockable getDockedEntity() {
            return null;
        }

        @Override
        public void setDockedEntity(Dockable entity) {
        }
    }
}
