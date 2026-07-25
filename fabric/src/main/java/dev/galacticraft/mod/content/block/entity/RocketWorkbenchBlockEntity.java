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
import dev.galacticraft.mod.machine.storage.VariableSizedContainer;
import dev.galacticraft.mod.screen.RocketWorkbenchMenu;
import dev.galacticraft.mod.tag.GCItemTags;
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
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.TntBlock;
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
     * The rocket's upgrade slots. Three chests may be installed, one per slot, each worth two rows
     * of cargo. The explosive upgrade shares this container but only occupies the first slot, since
     * a rocket carries a single upgrade.
     */
    public final SimpleContainer chests = new SimpleContainer(CHEST_SLOTS) {
        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            if (isExplosive(stack)) {
                if (slot != 0) return false;

                // Crafting consumes every chest slot, so refuse the charge while chests are loaded
                // rather than silently eating them for an upgrade the rocket won't get.
                for (int other = 1; other < this.getContainerSize(); ++other) {
                    if (!this.getItem(other).isEmpty()) return false;
                }
                return true;
            }

            if (!stack.is(GCItemTags.ROCKET_STORAGE_UPGRADE_ITEMS)) return false;

            // Chests and the explosive charge are different upgrades; only a swap into slot 0 is allowed.
            return slot == 0 || !isExplosive(this.getItem(0));
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    };

    private static boolean isExplosive(ItemStack stack) {
        return stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof TntBlock;
    }

    // Using a VariableSizedContainer here to support different recipes in the future
    public final VariableSizedContainer ingredients = new VariableSizedContainer(14);

    public RocketWorkbenchBlockEntity(BlockPos pos, BlockState state) {
        super(GCBlockEntityTypes.ROCKET_WORKBENCH, pos, state);
        this.ingredients.addListener(this);
        this.chests.addListener(sender -> this.setChanged());
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registryLookup) {
        super.saveAdditional(tag, registryLookup);

        ListTag outputTag = this.output.createTag(registryLookup);
        if (!outputTag.isEmpty()) tag.put("Output", outputTag.getFirst());
        tag.put("Inventory", this.ingredients.toTag(registryLookup));
        tag.put("Chests", this.chests.createTag(registryLookup));
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
        RocketWorkbenchMenu.OpeningData.CODEC.encode(buf, new RocketWorkbenchMenu.OpeningData(this.getBlockPos()));
    }
}
