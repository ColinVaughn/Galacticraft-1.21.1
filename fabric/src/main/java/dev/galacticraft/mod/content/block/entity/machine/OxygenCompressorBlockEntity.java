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
import dev.galacticraft.mod.api.oxygencompressor.OxygenTankInside;
import dev.galacticraft.mod.content.GCBlockEntityTypes;
import dev.galacticraft.mod.content.GCSounds;
import dev.galacticraft.mod.content.block.machine.OxygenCompressorBlock;
import dev.galacticraft.mod.content.item.GCItems;
import dev.galacticraft.mod.machine.GCMachineStatuses;
import dev.galacticraft.mod.screen.OxygenCompressorMenu;
import dev.galacticraft.mod.util.FluidUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class OxygenCompressorBlockEntity extends MachineBlockEntity {
    public static final int CHARGE_SLOT = 0;
    public static final int OXYGEN_SLOT = 1;
    public static final int OXYGEN_TANK = 0;
    public static final long MAX_OXYGEN = FluidUtil.bucketsToDroplets(50);
    private static final int TRANSFER_RATE = 800;

    private final FluidSource fluidSource = new FluidSource(this);
    private final CompressionMode compressionMode;
    private boolean didGasTransfer = false;


    private static class CompressionMode {
        private boolean mode = true;
    }

    public OxygenCompressorBlockEntity(BlockPos pos, BlockState state) {
        this(pos, state, new CompressionMode());
    }

    private OxygenCompressorBlockEntity(BlockPos pos, BlockState state, CompressionMode compressionMode)
    {
        super(GCBlockEntityTypes.OXYGEN_COMPRESSOR, pos, state, StorageSpec.of(
                MachineItemStorage.spec(
                        ItemResourceSlot.builder(TransferType.TRANSFER)
                                .pos(8, 62)
                                .capacity(1)
                                .filter(ResourceFilters.CAN_EXTRACT_ENERGY)
                                .icon(Pair.of(InventoryMenu.BLOCK_ATLAS, Constant.SlotSprite.ENERGY)),
                        ItemResourceSlot.builder(TransferType.PROCESSING)
                                .pos(80, 27)
                                .capacity(1)
                                .filter((item, components) ->
                                        (compressionMode.mode ? ResourceFilters.canInsertFluid(Gases.OXYGEN) : ResourceFilters.canExtractFluid(Gases.OXYGEN))
                                                .test(item, components))
                                .icon(Pair.of(InventoryMenu.BLOCK_ATLAS, Constant.SlotSprite.OXYGEN_TANK))
                ),
                MachineEnergyStorage.spec(
                        Galacticraft.CONFIG.machineEnergyStorageSize(),
                        Galacticraft.CONFIG.oxygenCompressorEnergyConsumptionRate() * 2,
                        0
                ),
                MachineFluidStorage.spec(
                        FluidResourceSlot.builder(TransferType.STRICT_INPUT)
                                .pos(31, 8)
                                .capacity(OxygenCompressorBlockEntity.MAX_OXYGEN)
                                .filter(ResourceFilters.ofResource(Gases.OXYGEN))
                )
        ));

        this.compressionMode = compressionMode;
    }

    @Override
    protected void tickConstant(@NotNull ServerLevel world, @NotNull BlockPos pos, @NotNull BlockState state, @NotNull ProfilerFiller profiler) {
        super.tickConstant(world, pos, state, profiler);
        this.chargeFromSlot(CHARGE_SLOT);
    }

    @Override
    protected @NotNull MachineStatus tick(@NotNull ServerLevel level, @NotNull BlockPos pos, @NotNull BlockState blockstate, @NotNull ProfilerFiller profiler) {
        FluidResourceSlot oxygenStorage = this.fluidStorage().slot(OXYGEN_TANK);
        ItemResourceSlot itemStorage = this.itemStorage().slot(OXYGEN_SLOT);

        checkOxygenTankChange(level, pos, blockstate, itemStorage);
        if (itemStorage.isEmpty()) {
            if (didGasTransfer)
                triggerGasLeak(level, pos, blockstate);
            return GCMachineStatuses.MISSING_OXYGEN_TANK;
        }
        if (!this.energyStorage().canExtract(Galacticraft.CONFIG.oxygenCompressorEnergyConsumptionRate()))
            return MachineStatuses.NOT_ENOUGH_ENERGY;

        //COMPRESSING MODE
        if(compressionMode.mode) {
            if (oxygenStorage.isEmpty()) return GCMachineStatuses.NOT_ENOUGH_OXYGEN;

            long previousAmount = oxygenStorage.getAmount();
            this.drainFluidToSlot(OXYGEN_SLOT, OXYGEN_TANK, TRANSFER_RATE);
            if (oxygenStorage.getAmount() == previousAmount) return GCMachineStatuses.OXYGEN_TANK_FULL;

            this.energyStorage().extract(Galacticraft.CONFIG.oxygenCompressorEnergyConsumptionRate());
            didGasTransfer = true;
            return GCMachineStatuses.COMPRESSING_OXYGEN;
        }

        //DECOMPRESSING MODE
        this.fluidSource.trySpreadFluids(level, pos, blockstate);
        if (oxygenStorage.isFull()) return GCMachineStatuses.OXYGEN_TANK_FULL;

        long previousAmount = oxygenStorage.getAmount();
        this.takeFluidFromSlot(OXYGEN_SLOT, OXYGEN_TANK, Gases.OXYGEN, TRANSFER_RATE);
        if (oxygenStorage.getAmount() == previousAmount) return GCMachineStatuses.EMPTY_OXYGEN_TANK;

        this.energyStorage().extract(Galacticraft.CONFIG.oxygenCompressorEnergyConsumptionRate());
        didGasTransfer = true;
        return GCMachineStatuses.DECOMPRESSING;
    }

    private void triggerGasLeak(ServerLevel level, BlockPos pos, BlockState blockstate) {
        didGasTransfer = false;
        Vec3 particle_pos = Vec3.atCenterOf(pos).relative(blockstate.getValue(BlockStateProperties.HORIZONTAL_FACING), 0.3);
        level.sendParticles(ParticleTypes.WHITE_SMOKE, particle_pos.x, particle_pos.y, particle_pos.z, 80, 0.1, 0.1, 0.1, 0.04);
        level.playSound(null, pos, GCSounds.GAS_RELEASE, SoundSource.BLOCKS, 0.7f, 1.0f);
    }

    private void checkOxygenTankChange(ServerLevel level, BlockPos pos, BlockState blockState, ItemResourceSlot itemStorage) {
        OxygenTankInside currentTankType = itemToEnumOxygenTank(itemStorage.getResource());

        if(currentTankType != blockState.getValue(OxygenCompressorBlock.OXYGEN_TANK_INSIDE))
            level.setBlock(pos, blockState.setValue(OxygenCompressorBlock.OXYGEN_TANK_INSIDE, currentTankType), Block.UPDATE_CLIENTS);
    }

    private static OxygenTankInside itemToEnumOxygenTank(@Nullable Item item) {
        return item == GCItems.SMALL_OXYGEN_TANK ? OxygenTankInside.SMALL :
        item == GCItems.MEDIUM_OXYGEN_TANK ? OxygenTankInside.MEDIUM :
        item == GCItems.LARGE_OXYGEN_TANK ? OxygenTankInside.LARGE :
        OxygenTankInside.NONE;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
        super.saveAdditional(tag, lookup);
        tag.putBoolean(Constant.Nbt.COMPRESSION_MODE, this.compressionMode.mode);
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
        super.loadAdditional(tag, lookup);
        this.compressionMode.mode = tag.getBoolean(Constant.Nbt.COMPRESSION_MODE);
    }

    public boolean getCompressionMode() { return this.compressionMode.mode; }

    public void changeCompressionMode() { compressionMode.mode = !compressionMode.mode; this.setChanged(); }

    @Nullable
    @Override
    public MachineMenu<? extends MachineBlockEntity> createMenu(int syncId, Inventory inv, Player player) {
        return new OxygenCompressorMenu(syncId, player, this);
    }
}
