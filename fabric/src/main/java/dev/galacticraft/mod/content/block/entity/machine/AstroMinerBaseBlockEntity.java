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

import dev.galacticraft.mod.content.GCBlockEntityTypes;
import dev.galacticraft.machinelib.api.storage.MachineEnergyStorage;
import dev.galacticraft.machinelib.impl.platform.MachineLibPlatform;
import dev.architectury.registry.menu.ExtendedMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.RandomSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.NonNullList;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedList;
import java.util.UUID;

/**
 * The block entity backing the Astro Miner Base multiblock. Faithful port of legacy
 * {@code TileEntityMinerBase}: stores energy, a 72-slot ore hold (+ a battery slot),
 * the linked miner UUID, the base facing and the queued mining target anchors. Only the
 * "master" corner of the 2x2x2 owns one of these; deployment and docking are driven by
 * the linked {@code AstroMiner} entity (linked purely by UUID).
 */
@SuppressWarnings("UnstableApiUsage")
public class AstroMinerBaseBlockEntity extends BlockEntity implements ExtendedMenuProvider {
    public static final long MAX_ENERGY = 12000L;
    /** Number of ore-hold slots (slots 1..72). Slot 0 is the battery/charge slot. */
    public static final int HOLD_SIZE = 72;
    /** Total container size: battery slot + ore hold. */
    public static final int INVENTORY_SIZE = HOLD_SIZE + 1;
    /** Legacy {@code EntityAstroMiner.MINE_LENGTH}; anchors duplicate forward by MINE_LENGTH + 6. */
    public static final int MINE_LENGTH = 24;

    private final MachineEnergyStorage energy = MachineEnergyStorage.create(MAX_ENERGY, MAX_ENERGY, MAX_ENERGY);

    private final SimpleContainer hold = new SimpleContainer(INVENTORY_SIZE);

    private Direction facing = Direction.NORTH;
    private @Nullable UUID linkedMiner = null;
    private final LinkedList<BlockPos> targetPoints = new LinkedList<>();
    private boolean recallRequested = false;

    public AstroMinerBaseBlockEntity(BlockPos pos, BlockState state) {
        super(GCBlockEntityTypes.ASTRO_MINER_BASE, pos, state);
        this.energy.setParent(this);
        this.hold.addListener(container -> this.setChanged());
    }

    // ==== Ticking ====

    public static void serverTick(Level level, BlockPos pos, BlockState state, AstroMinerBaseBlockEntity be) {
        be.chargeFromSlot();
    }

    /** Pulls energy out of an energy item in slot 0 into this base's storage. */
    private void chargeFromSlot() {
        if (this.energy.isFull()) return;
        MachineLibPlatform.chargeFromContainerItem(this.hold, 0, this.energy);
    }

    // ==== Energy API (consumed by the AstroMiner entity) ====

    public long getEnergyStored() {
        return this.energy.getAmount();
    }

    public long getMaxEnergyStored() {
        return MAX_ENERGY;
    }

    public boolean hasEnergyToRun() {
        return !this.energy.isEmpty();
    }

    /** Extracts up to {@code max} energy from this base, returning the amount actually removed. */
    public long extractEnergyForMiner(long max) {
        if (max <= 0) return 0;
        return this.energy.extract(max);
    }

    /** Exposed to the SIDED energy lookup so adjacent cables can charge the base. */
    public MachineEnergyStorage getEnergyStorage() {
        return this.energy;
    }

    public boolean isEnergyInputSide(@Nullable Direction side) {
        return side == this.facing.getOpposite();
    }

    // ==== Inventory / hold API ====

    public SimpleContainer getHold() {
        return this.hold;
    }

    /**
     * Merges {@code stack} into the ore hold (slots 1..72) and returns whatever could not fit.
     */
    public ItemStack addToHold(ItemStack stack) {
        if (stack.isEmpty()) return stack;
        stack = stack.copy();
        // merge into matching stacks first
        for (int i = 1; i <= HOLD_SIZE && !stack.isEmpty(); i++) {
            ItemStack existing = this.hold.getItem(i);
            if (!existing.isEmpty() && ItemStack.isSameItemSameComponents(existing, stack)) {
                int limit = Math.min(existing.getMaxStackSize(), this.hold.getMaxStackSize());
                int room = limit - existing.getCount();
                if (room > 0) {
                    int move = Math.min(room, stack.getCount());
                    existing.grow(move);
                    stack.shrink(move);
                }
            }
        }
        // then fill empty slots
        for (int i = 1; i <= HOLD_SIZE && !stack.isEmpty(); i++) {
            if (this.hold.getItem(i).isEmpty()) {
                int limit = Math.min(stack.getMaxStackSize(), this.hold.getMaxStackSize());
                this.hold.setItem(i, stack.split(Math.min(limit, stack.getCount())));
            }
        }
        this.hold.setChanged();
        return stack;
    }

    /** True if a free ore-hold slot (1..72) exists. */
    public boolean hasHoldSpace() {
        for (int i = 1; i <= HOLD_SIZE; i++) {
            if (this.hold.getItem(i).isEmpty()) return true;
        }
        return false;
    }

    // ==== Linking ====

    public @Nullable UUID getLinkedMiner() {
        return this.linkedMiner;
    }

    public void setLinkedMiner(@Nullable UUID uuid) {
        this.linkedMiner = uuid;
        this.setChanged();
    }

    public boolean hasLinkedMiner() {
        return this.linkedMiner != null;
    }

    // ==== Facing / position ====

    public Direction getFacing() {
        return this.facing;
    }

    public void setFacing(Direction facing) {
        this.facing = facing;
        this.setChanged();
        if (this.level != null && !this.level.isClientSide()) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }

    public BlockPos getMasterPos() {
        return this.worldPosition;
    }

    // ==== Target planning (legacy findTargetPoints) ====

    /**
     * Builds the mining-area anchors: a lattice ahead of the base along {@link #facing},
     * each anchor duplicated shifted forward by {@code MINE_LENGTH + 6}. Ported 1:1 from
     * legacy {@code TileEntityMinerBase.findTargetPoints}.
     */
    public void planTargets() {
        this.targetPoints.clear();
        if (this.level == null) return;
        RandomSource rand = this.level.random;
        BlockPos master = this.worldPosition;

        BlockPos horizontal = master.relative(this.facing, rand.nextInt(16) + 32);
        int miny = Math.min(master.getY() * 2 - 90, master.getY() - 22);
        if (miny < 5) miny = 5;
        int y = miny + 5 + rand.nextInt(4);
        BlockPos anchor = new BlockPos(horizontal.getX(), y, horizontal.getZ());

        this.targetPoints.add(anchor);

        Direction lateral = this.facing.getAxis() == Direction.Axis.Z ? Direction.WEST : Direction.NORTH;

        this.targetPoints.add(anchor.relative(lateral, 13));
        this.targetPoints.add(anchor.relative(lateral, -13));
        if (y > 17) {
            this.targetPoints.add(anchor.relative(lateral, 7).below(11));
            this.targetPoints.add(anchor.relative(lateral, -7).below(11));
        } else {
            this.targetPoints.add(anchor.relative(lateral, 26));
            this.targetPoints.add(anchor.relative(lateral, -26));
        }
        this.targetPoints.add(anchor.relative(lateral, 7).above(11));
        this.targetPoints.add(anchor.relative(lateral, -7).above(11));
        if (y < master.getY() - 38) {
            this.targetPoints.add(anchor.relative(lateral, 13).above(22));
            this.targetPoints.add(anchor.above(22));
            this.targetPoints.add(anchor.relative(lateral, -13).above(22));
        }

        int s = this.targetPoints.size();
        for (int i = 0; i < s; i++) {
            this.targetPoints.add(this.targetPoints.get(i).relative(this.facing, MINE_LENGTH + 6));
        }
        this.setChanged();
    }

    /** Pops and returns the next mining anchor, or null when the queue is empty. */
    public @Nullable BlockPos pollNextTarget() {
        if (this.targetPoints.isEmpty()) return null;
        BlockPos next = this.targetPoints.removeFirst();
        this.setChanged();
        return next;
    }

    // ==== Recall ====

    public boolean isRecallRequested() {
        return this.recallRequested;
    }

    public void requestRecall() {
        this.recallRequested = true;
        this.setChanged();
    }

    public void clearRecall() {
        this.recallRequested = false;
        this.setChanged();
    }

    // ==== Persistence ====

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putLong("Energy", this.energy.getAmount());
        tag.putInt("Facing", this.facing.get3DDataValue());
        tag.putBoolean("Recall", this.recallRequested);
        if (this.linkedMiner != null) {
            tag.putUUID("LinkedMiner", this.linkedMiner);
        }

        NonNullList<ItemStack> items = NonNullList.withSize(INVENTORY_SIZE, ItemStack.EMPTY);
        for (int i = 0; i < INVENTORY_SIZE; i++) {
            items.set(i, this.hold.getItem(i));
        }
        ContainerHelper.saveAllItems(tag, items, registries);

        long[] targets = new long[this.targetPoints.size()];
        int i = 0;
        for (BlockPos p : this.targetPoints) {
            targets[i++] = p.asLong();
        }
        tag.putLongArray("Targets", targets);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.energy.setEnergy(Math.min(tag.getLong("Energy"), MAX_ENERGY));
        this.facing = Direction.from3DDataValue(tag.getInt("Facing"));
        this.recallRequested = tag.getBoolean("Recall");
        this.linkedMiner = tag.hasUUID("LinkedMiner") ? tag.getUUID("LinkedMiner") : null;

        NonNullList<ItemStack> items = NonNullList.withSize(INVENTORY_SIZE, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items, registries);
        for (int i = 0; i < INVENTORY_SIZE; i++) {
            this.hold.setItem(i, items.get(i));
        }

        this.targetPoints.clear();
        for (long l : tag.getLongArray("Targets")) {
            this.targetPoints.add(BlockPos.of(l));
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Facing", this.facing.get3DDataValue());
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // ==== Menu ====

    @Override
    public Component getDisplayName() {
        return this.getBlockState().getBlock().getName();
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player player) {
        return new dev.galacticraft.mod.screen.AstroMinerBaseMenu(syncId, inventory, this);
    }

    @Override
    public void saveExtraData(net.minecraft.network.FriendlyByteBuf buf) {
        BlockPos.STREAM_CODEC.encode(buf, this.worldPosition);
    }
}
