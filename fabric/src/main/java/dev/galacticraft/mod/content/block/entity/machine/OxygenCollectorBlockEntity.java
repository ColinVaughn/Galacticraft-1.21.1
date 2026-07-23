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

package dev.galacticraft.mod.content.block.entity.machine;

import com.mojang.datafixers.util.Pair;
import dev.galacticraft.api.gas.Gases;
import dev.galacticraft.api.universe.celestialbody.CelestialBody;
import dev.galacticraft.machinelib.api.block.entity.MachineBlockEntity;
import dev.galacticraft.machinelib.api.filter.ResourceFilters;
import dev.galacticraft.machinelib.api.machine.MachineStatus;
import dev.galacticraft.machinelib.api.machine.MachineStatuses;
import dev.galacticraft.machinelib.api.menu.MachineMenu;
import dev.galacticraft.machinelib.api.storage.MachineEnergyStorage;
import dev.galacticraft.machinelib.api.storage.MachineFluidStorage;
import dev.galacticraft.machinelib.api.storage.MachineItemStorage;
import dev.galacticraft.machinelib.api.storage.StorageSpec;
import dev.galacticraft.machinelib.api.storage.slot.FluidResourceSlot;
import dev.galacticraft.machinelib.api.storage.slot.ItemResourceSlot;
import dev.galacticraft.machinelib.api.transfer.TransferType;
import dev.galacticraft.machinelib.api.util.FluidSource;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.Galacticraft;
import dev.galacticraft.mod.content.GCBlockEntityTypes;
import dev.galacticraft.mod.data.OxygenBlockDataManager;
import dev.galacticraft.mod.machine.GCMachineStatuses;
import dev.galacticraft.mod.screen.OxygenCollectorMenu;
import dev.galacticraft.mod.util.FluidUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class OxygenCollectorBlockEntity extends MachineBlockEntity {
    private static final int COLLECTION_SCAN_INTERVAL = 20;
    private static final ResourceKey<Fluid> OXYGEN_KEY = ResourceKey.create(Registries.FLUID, Gases.OXYGEN_ID);

    public static final int CHARGE_SLOT = 0;
    public static final int OXYGEN_TANK = 0;

    public static final long MAX_OXYGEN = FluidUtil.bucketsToDroplets(50);

    private static final StorageSpec SPEC = StorageSpec.of(
            MachineItemStorage.spec(
                    ItemResourceSlot.builder(TransferType.TRANSFER)
                            .pos(8, 62)
                            .capacity(1)
                            .filter(ResourceFilters.CAN_EXTRACT_ENERGY)
                            .icon(Pair.of(InventoryMenu.BLOCK_ATLAS, Constant.SlotSprite.ENERGY))
            ),
            MachineEnergyStorage.spec(
                    Galacticraft.CONFIG.machineEnergyStorageSize(),
                    Galacticraft.CONFIG.oxygenCollectorEnergyConsumptionRate() * 2,
                    0
            ),
            MachineFluidStorage.spec(
                    FluidResourceSlot.builder(TransferType.STRICT_OUTPUT)
                            .pos(31, 8)
                            .capacity(OxygenCollectorBlockEntity.MAX_OXYGEN)
                            .filter(ResourceFilters.ofResource(Gases.OXYGEN))
            )
    );

    private final FluidSource fluidSource = new FluidSource(this);
    public int collectionAmount = 0;
    private boolean collectionScanInitialized;

    public OxygenCollectorBlockEntity(BlockPos pos, BlockState state) {
        super(GCBlockEntityTypes.OXYGEN_COLLECTOR, pos, state, SPEC);
    }

    int collectOxygen(@NotNull ServerLevel level, @NotNull BlockPos pos) {
        Holder<CelestialBody<?, ?>> body = level.galacticraft$getCelestialBody();
        boolean atmosphericOxygen = body == null || body.value().atmosphere().composition().getDouble(OXYGEN_KEY) > 0;
        return this.collectOxygen(level, pos, atmosphericOxygen);
    }

    int collectOxygen(@NotNull ServerLevel level, @NotNull BlockPos pos, boolean atmosphericOxygen) {
        if (atmosphericOxygen) {
            return OxygenCollectorLogic.collectionPerSecond(true, 0);
        }

        int minX = pos.getX() - 5;
        int minY = Math.max(pos.getY() - 5, level.getMinBuildHeight());
        int minZ = pos.getZ() - 5;
        int maxX = pos.getX() + 5;
        int maxY = Math.min(pos.getY() + 5, level.getMaxBuildHeight() - 1);
        int maxZ = pos.getZ() + 5;

        float nearbyOxygen = 0;

        for (BlockPos providerPos : BlockPos.betweenClosed(minX, minY, minZ, maxX, maxY, maxZ)) {
            BlockState providerState = level.getBlockState(providerPos);
            if (!providerState.isAir()) {
                nearbyOxygen += OxygenBlockDataManager.getOxygen(level, providerPos, providerState);
            }
        }

        return OxygenCollectorLogic.collectionPerSecond(false, nearbyOxygen);
    }

    @Override
    protected void tickConstant(@NotNull ServerLevel level, @NotNull BlockPos pos, @NotNull BlockState state, @NotNull ProfilerFiller profiler) {
        super.tickConstant(level, pos, state, profiler);
        this.chargeFromSlot(CHARGE_SLOT);
    }

    @Override
    protected @NotNull MachineStatus tick(@NotNull ServerLevel level, @NotNull BlockPos pos, @NotNull BlockState state, @NotNull ProfilerFiller profiler) {
        profiler.push("transfer");
        this.fluidSource.trySpreadFluids(level, pos, state);

        if (this.fluidStorage().slot(OXYGEN_TANK).isFull()) {
            profiler.pop();
            return GCMachineStatuses.OXYGEN_TANK_FULL;
        }
        profiler.popPush("transaction");
        try {
            if (this.energyStorage().canExtract(Galacticraft.CONFIG.oxygenCollectorEnergyConsumptionRate())) {
                profiler.push("collect");
                if (!this.collectionScanInitialized
                        || (level.getGameTime() + pos.asLong()) % COLLECTION_SCAN_INTERVAL == 0) {
                    this.collectionAmount = this.collectOxygen(level, pos);
                    this.collectionScanInitialized = true;
                }
                profiler.pop();
                if (this.collectionAmount > 0) {
                    this.energyStorage().extract(Galacticraft.CONFIG.oxygenCollectorEnergyConsumptionRate());
                    this.fluidStorage().slot(OXYGEN_TANK).insert(Gases.OXYGEN, FluidUtil.bucketsToDroplets(this.collectionAmount) / 20L);
                    return GCMachineStatuses.COLLECTING;
                } else {
                    return GCMachineStatuses.NOT_ENOUGH_OXYGEN;
                }
            } else {
                this.collectionAmount = 0;
                return MachineStatuses.NOT_ENOUGH_ENERGY;
            }
        } finally {
            profiler.pop();
        }
    }

    @Nullable
    @Override
    public MachineMenu<? extends MachineBlockEntity> createMenu(int syncId, Inventory inv, Player player) {
        return new OxygenCollectorMenu(syncId, player, this);
    }

    public int getCollectionAmount() {
        return this.collectionAmount;
    }
}
