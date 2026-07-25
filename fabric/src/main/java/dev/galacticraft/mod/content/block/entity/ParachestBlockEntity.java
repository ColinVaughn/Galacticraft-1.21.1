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

package dev.galacticraft.mod.content.block.entity;

import dev.galacticraft.mod.content.GCBlockEntityTypes;
import dev.galacticraft.mod.content.GCBlocks;
import dev.galacticraft.mod.content.entity.ScalableFuelLevel;
import dev.galacticraft.mod.screen.ParachestMenu;
import dev.architectury.registry.menu.ExtendedMenuProvider;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.fluid.base.SingleFluidStorage;
import net.fabricmc.fabric.api.transfer.v1.item.InventoryStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SidedStorageBlockEntity;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import dev.galacticraft.mod.content.entity.vehicle.RocketCargoLogic;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("UnstableApiUsage")
public class ParachestBlockEntity extends RandomizableContainerBlockEntity implements SidedStorageBlockEntity, ExtendedMenuProvider, ScalableFuelLevel {

    public final SingleFluidStorage tank = SingleFluidStorage.withFixedCapacity(FluidConstants.BUCKET * 5, () -> {
    });
    /** The launch pad, the rocket and the fuel container; everything before them is rocket cargo. */
    public static final int NON_CARGO_SLOTS = 3;
    private static final String SIZE_KEY = "size";

    private NonNullList<ItemStack> inventory = NonNullList.withSize(NON_CARGO_SLOTS, ItemStack.EMPTY);

    public ParachestBlockEntity(BlockPos blockPos, BlockState blockState) {
        super(GCBlockEntityTypes.PARACHEST, blockPos, blockState);
    }

    @Override
    protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        super.loadAdditional(nbt, registryLookup);
        // A parachest is however large the cargo it was dropped with, so the size has to be restored
        // before the items are read into it — sizing off getContainerSize() here would only ever
        // report the empty default this field starts at.
        this.inventory = NonNullList.withSize(readSize(nbt), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(nbt, this.inventory, registryLookup);
        this.tank.readNbt(nbt, registryLookup);
    }

    @Override
    protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        super.saveAdditional(nbt, registryLookup);
        nbt.putInt(SIZE_KEY, this.inventory.size());
        ContainerHelper.saveAllItems(nbt, this.inventory, registryLookup);
        this.tank.writeNbt(nbt, registryLookup);
    }

    /**
     * Parachests saved before the size was written have to be sized from the items themselves.
     * Rounded up to whole rows so the result is a shape {@code ParachestMenu} can lay out.
     */
    private static int readSize(CompoundTag nbt) {
        if (nbt.contains(SIZE_KEY)) {
            return Math.max(NON_CARGO_SLOTS, nbt.getInt(SIZE_KEY));
        }

        int highestSlot = -1;
        ListTag items = nbt.getList("Items", Tag.TAG_COMPOUND);
        for (int i = 0; i < items.size(); i++) {
            highestSlot = Math.max(highestSlot, items.getCompound(i).getByte("Slot") & 255);
        }

        int cargo = Math.max(0, highestSlot + 1 - NON_CARGO_SLOTS);
        int rows = (cargo + RocketCargoLogic.SLOTS_PER_ROW - 1) / RocketCargoLogic.SLOTS_PER_ROW;
        return rows * RocketCargoLogic.SLOTS_PER_ROW + NON_CARGO_SLOTS;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registryLookup) {
        return this.saveWithoutMetadata(registryLookup);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable(GCBlocks.PARACHEST.getDescriptionId());
    }

    @Override
    public Storage<FluidVariant> getFluidStorage(@Nullable Direction side) {
        return tank;
    }

    @Override
    protected AbstractContainerMenu createMenu(int syncId, Inventory inventory) {
        return new ParachestMenu(syncId, inventory, this);
    }

    @Override
    public NonNullList<ItemStack> getItems() {
        return this.inventory;
    }

    @Override
    public void setItems(NonNullList<ItemStack> items) {
        this.inventory = items;
    }

    @Override
    public int getContainerSize() {
        return this.inventory.size();
    }

    @Override
    public float getScaledFuelLevel(float scale) {
        final float fuelLevel = this.tank.getResource().isBlank() ? 0 : this.tank.getAmount();

        return fuelLevel * scale / this.tank.getCapacity();
    }

    public void tick() {
        ContainerItemContext context = ContainerItemContext.ofSingleSlot(InventoryStorage.of(this, null).getSlot(this.inventory.size() - 1));
        Storage<FluidVariant> fluidStorage = context.find(FluidStorage.ITEM);
        if (fluidStorage != null && !tank.isResourceBlank() && tank.getAmount() > 0) {
            try (Transaction tx = Transaction.openOuter()) {
                tank.extract(tank.getResource(), fluidStorage.insert(tank.getResource(), tank.getAmount(), tx), tx);
                tx.commit();
            }
        }
    }

    public void setFuel(long amount) {
        try (Transaction tx = Transaction.openOuter()) {
            tank.insert(FluidVariant.of(dev.galacticraft.mod.content.GCFluids.FUEL), amount, tx);
            tx.commit();
        }
    }

    @Override
    public void saveExtraData(net.minecraft.network.FriendlyByteBuf buf) {
        ParachestMenu.OpeningData.STREAM_CODEC.encode(buf, new ParachestMenu.OpeningData(this.getBlockPos()));
    }
}
