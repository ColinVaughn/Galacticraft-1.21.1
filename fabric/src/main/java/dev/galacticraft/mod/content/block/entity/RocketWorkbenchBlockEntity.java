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

package dev.galacticraft.mod.content.block.entity;

import net.minecraft.world.level.Level;
import dev.galacticraft.mod.machine.workbench.WorkbenchUpgrades;
import dev.galacticraft.api.item.Schematic;
import dev.galacticraft.mod.content.GCBlockEntityTypes;
import dev.galacticraft.mod.machine.storage.VariableSizedContainer;
import dev.galacticraft.mod.screen.RocketWorkbenchMenu;
import net.minecraft.resources.ResourceLocation;
import dev.architectury.registry.menu.ExtendedMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RocketWorkbenchBlockEntity extends BlockEntity implements ExtendedMenuProvider, VariableSizedContainer.Listener {
    public static final int CHEST_SLOTS = 3;

    public final SimpleContainer output = new SimpleContainer(1) {
        @Override
        public boolean canPlaceItem(int index, ItemStack stack) {
            return false;
        }
    };

    /**
     * The rocket's upgrade slots. Which items they take is up to the registered rocket upgrades:
     * stock rockets take up to three chests, one per slot, or an explosive charge in the first
     * slot. A rocket carries a single upgrade.
     */
    public final SimpleContainer chests = new SimpleContainer(CHEST_SLOTS) {
        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            Level level = RocketWorkbenchBlockEntity.this.level;
            return level != null && WorkbenchUpgrades.canPlace(level.registryAccess(), slot, stack, this);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    };

    /** Holds the unlock-page schematic on the block entity so turning pages does not drop it. */
    public final SimpleContainer schematic = new SimpleContainer(1) {
        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return stack.getItem() instanceof Schematic;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    };

    public final SimpleContainer refitInput = new SimpleContainer(1) {
        @Override public boolean canPlaceItem(int slot, ItemStack stack) {
            return stack.is(dev.galacticraft.mod.content.item.GCItems.ROCKET);
        }
        @Override public int getMaxStackSize() { return 1; }
    };

    public int paintRevision;
    public final SimpleContainer paintInput = new SimpleContainer(1) {
        @Override public boolean canPlaceItem(int slot, ItemStack stack) {
            return RocketWorkbenchBlockEntity.this.level != null
                    && dev.galacticraft.mod.screen.RocketPaintMenu.canPaint(stack, RocketWorkbenchBlockEntity.this.level.registryAccess());
        }
        @Override public int getMaxStackSize() { return 1; }
    };

    public ExtendedMenuProvider paintMenu(ResourceLocation returnPage) {
        return new ExtendedMenuProvider() {
            @Override public Component getDisplayName() { return Component.translatable("gui.galacticraft.rocket_paint.title"); }
            @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
                return new dev.galacticraft.mod.screen.RocketPaintMenu(id, RocketWorkbenchBlockEntity.this, inventory, returnPage);
            }
            @Override public void saveExtraData(net.minecraft.network.FriendlyByteBuf buf) {
                RocketWorkbenchMenu.OpeningData.CODEC.encode(buf, new RocketWorkbenchMenu.OpeningData(getBlockPos(), java.util.Optional.ofNullable(returnPage)));
            }
        };
    }

    // Using a VariableSizedContainer here to support different recipes in the future
    public final VariableSizedContainer ingredients = new VariableSizedContainer(14);

    public RocketWorkbenchBlockEntity(BlockPos pos, BlockState state) {
        super(GCBlockEntityTypes.ROCKET_WORKBENCH, pos, state);
        this.ingredients.addListener(this);
        this.chests.addListener(sender -> this.setChanged());
        this.schematic.addListener(sender -> this.setChanged());
        this.paintInput.addListener(sender -> { this.paintRevision++; this.setChanged(); });
        this.refitInput.addListener(sender -> {
            if (this.level != null && !this.level.isClientSide && !this.refitInput.isEmpty()) this.output.clearContent();
            this.setChanged();
        });
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registryLookup) {
        super.saveAdditional(tag, registryLookup);

        ListTag outputTag = this.output.createTag(registryLookup);
        if (!outputTag.isEmpty()) tag.put("Output", outputTag.getFirst());
        tag.put("Inventory", this.ingredients.toTag(registryLookup));
        tag.put("Chests", this.chests.createTag(registryLookup));
        tag.put("Schematic", this.schematic.createTag(registryLookup));
        tag.put("PaintInput", this.paintInput.createTag(registryLookup));
        tag.put("RefitInput", this.refitInput.createTag(registryLookup));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registryLookup) {
        super.loadAdditional(tag, registryLookup);
        if (tag.contains("Output")) {
            ListTag list = new ListTag();
            list.add(tag.get("Output"));
            this.output.fromTag(list, registryLookup);
        }
        this.ingredients.readTag(tag.getCompound("Inventory"), registryLookup);
        this.chests.fromTag(tag.getList("Chests", Tag.TAG_COMPOUND), registryLookup);
        this.schematic.fromTag(tag.getList("Schematic", Tag.TAG_COMPOUND), registryLookup);
        this.paintInput.fromTag(tag.getList("PaintInput", Tag.TAG_COMPOUND), registryLookup);
        this.refitInput.fromTag(tag.getList("RefitInput", Tag.TAG_COMPOUND), registryLookup);
        if (!this.refitInput.isEmpty()) this.output.clearContent();
    }

    public void resizeInventory(int newSize) {
        if (newSize < this.ingredients.getTargetSize() && this.level != null && !this.level.isClientSide) {
            // Return any items held in the slots being removed to the world so they aren't orphaned.
            for (int slot = newSize; slot < this.ingredients.getContainerSize(); slot++) {
                ItemStack stack = this.ingredients.getItem(slot);
                if (!stack.isEmpty()) {
                    Containers.dropItemStack(this.level, this.worldPosition.getX(), this.worldPosition.getY(), this.worldPosition.getZ(), stack.copy());
                    this.ingredients.setItem(slot, ItemStack.EMPTY);
                }
            }
        }
        this.ingredients.resize(newSize);
    }

    @Override
    public @NotNull Component getDisplayName() {
        return this.getBlockState().getBlock().getName();
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int i, Inventory inventory, Player player) {
        return new RocketWorkbenchMenu(i, this, inventory);
    }

    /**
     * A provider that opens this workbench on a specific page, so flipping the book can reopen the
     * menu rather than rebuilding a live one's slots.
     */
    public ExtendedMenuProvider menuOn(ResourceLocation page) {
        RocketWorkbenchBlockEntity workbench = this;
        return new ExtendedMenuProvider() {
            @Override
            public @NotNull Component getDisplayName() {
                return workbench.getDisplayName();
            }

            @Override
            public AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player player) {
                return new RocketWorkbenchMenu(syncId, workbench, inventory, page);
            }

            @Override
            public void saveExtraData(net.minecraft.network.FriendlyByteBuf buf) {
                RocketWorkbenchMenu.OpeningData.CODEC.encode(buf, new RocketWorkbenchMenu.OpeningData(workbench.getBlockPos(), java.util.Optional.of(page)));
            }
        };
    }

    @Override
    public void onSizeChanged() {
        this.setChanged();
    }

    @Override
    public void onItemChanged() {
        this.setChanged();
    }

    @Override
    public void saveExtraData(net.minecraft.network.FriendlyByteBuf buf) {
        RocketWorkbenchMenu.OpeningData.CODEC.encode(buf, new RocketWorkbenchMenu.OpeningData(this.getBlockPos(), java.util.Optional.empty()));
    }
}
