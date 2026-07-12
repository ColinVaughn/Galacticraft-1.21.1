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

import dev.architectury.registry.menu.ExtendedMenuProvider;
import dev.galacticraft.mod.content.GCBlockEntityTypes;
import dev.galacticraft.mod.content.GCBlocks;
import dev.galacticraft.mod.content.GCFluids;
import dev.galacticraft.mod.content.entity.ScalableFuelLevel;
import dev.galacticraft.mod.screen.ParachestMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class ParachestBlockEntity extends RandomizableContainerBlockEntity
        implements ExtendedMenuProvider, ScalableFuelLevel {
    public final FluidTank tank = new FluidTank(5000) {
        @Override
        protected void onContentsChanged() {
            ParachestBlockEntity.this.setChanged();
        }
    };
    private NonNullList<ItemStack> inventory = NonNullList.withSize(3, ItemStack.EMPTY);

    public ParachestBlockEntity(BlockPos pos, BlockState state) {
        super(GCBlockEntityTypes.PARACHEST, pos, state);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
        super.loadAdditional(tag, lookup);
        inventory = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, inventory, lookup);
        tank.readFromNBT(lookup, tag);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
        super.saveAdditional(tag, lookup);
        ContainerHelper.saveAllItems(tag, inventory, lookup);
        tank.writeToNBT(lookup, tag);
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider lookup) { return saveWithoutMetadata(lookup); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override protected Component getDefaultName() { return Component.translatable(GCBlocks.PARACHEST.getDescriptionId()); }
    @Override protected AbstractContainerMenu createMenu(int id, Inventory inventory) { return new ParachestMenu(id, inventory, this); }
    @Override public NonNullList<ItemStack> getItems() { return inventory; }
    @Override public void setItems(NonNullList<ItemStack> items) { inventory = items; }
    @Override public int getContainerSize() { return inventory.size(); }

    @Override
    public float getScaledFuelLevel(float scale) {
        return tank.isEmpty() ? 0 : tank.getFluidAmount() * scale / tank.getCapacity();
    }

    public void setFuel(long amount) {
        int milliBuckets = (int) Math.min(Integer.MAX_VALUE, amount * 1000L / 81_000L);
        tank.fill(new FluidStack(GCFluids.FUEL, milliBuckets),
                IFluidHandler.FluidAction.EXECUTE);
    }

    public void tick() {
        ItemStack stack = inventory.get(inventory.size() - 1);
        IFluidHandler item = stack.getCapability(Capabilities.FluidHandler.ITEM);
        if (item == null || tank.isEmpty()) return;
        FluidStack available = tank.drain(tank.getFluidAmount(), IFluidHandler.FluidAction.SIMULATE);
        int accepted = item.fill(available, IFluidHandler.FluidAction.EXECUTE);
        if (accepted > 0) tank.drain(accepted, IFluidHandler.FluidAction.EXECUTE);
    }

    @Override
    public void saveExtraData(net.minecraft.network.FriendlyByteBuf buf) {
        ParachestMenu.OpeningData.STREAM_CODEC.encode(buf, new ParachestMenu.OpeningData(getBlockPos()));
    }
}
