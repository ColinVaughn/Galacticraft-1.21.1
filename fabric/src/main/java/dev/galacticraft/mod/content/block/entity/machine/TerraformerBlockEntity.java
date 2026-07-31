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
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.Galacticraft;
import dev.galacticraft.mod.content.GCBlockEntityTypes;
import dev.galacticraft.mod.content.GCBlocks;
import dev.galacticraft.mod.machine.GCMachineStatuses;
import dev.galacticraft.mod.network.s2c.TerraformerUpdatePayload;
import dev.galacticraft.mod.screen.TerraformerMenu;
import dev.galacticraft.mod.tag.GCBlockTags;
import dev.galacticraft.mod.util.FluidUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The Galacticraft Legacy terraformer, ported from the MC1.12 implementation.
 */
public class TerraformerBlockEntity extends MachineBlockEntity {
    public static final int WATER_CONTAINER_SLOT = 0;
    public static final int CHARGE_SLOT = 1;
    public static final int BONE_MEAL_SLOT_START = 2;
    public static final int BONE_MEAL_SLOT_END = 6;
    public static final int SAPLING_SLOT_START = 6;
    public static final int SAPLING_SLOT_END = 10;
    public static final int SEED_SLOT_START = 10;
    public static final int SEED_SLOT_END = 14;
    public static final int WATER_TANK = 0;

    public static final double MAX_SIZE = 15.0D;
    public static final long MAX_WATER = FluidUtil.bucketsToDroplets(2);
    public static final long ONE_MILLIBUCKET = MAX_WATER / 2000L;
    public static final long FIFTY_MILLIBUCKETS = ONE_MILLIBUCKET * 50L;
    public static final long ENERGY_PER_TICK = 1L;

    private static final int SCAN_INTERVAL = 60;
    private static final int GRASS_INTERVAL = 15;
    private static final int TREE_INTERVAL = 50;
    private static final int SYNC_INTERVAL = 10;

    private static final StorageSpec SPEC = StorageSpec.of(
            MachineItemStorage.spec(
                    itemSlot(TransferType.PROCESSING, 25, 19, ResourceFilters.canExtractFluid(Fluids.WATER), 1),
                    itemSlot(TransferType.TRANSFER, 25, 39, ResourceFilters.CAN_EXTRACT_ENERGY, 1),
                    itemSlot(TransferType.INPUT, 25, 63, ResourceFilters.ofResource(Items.BONE_MEAL), 64),
                    itemSlot(TransferType.INPUT, 43, 63, ResourceFilters.ofResource(Items.BONE_MEAL), 64),
                    itemSlot(TransferType.INPUT, 61, 63, ResourceFilters.ofResource(Items.BONE_MEAL), 64),
                    itemSlot(TransferType.INPUT, 79, 63, ResourceFilters.ofResource(Items.BONE_MEAL), 64),
                    itemSlot(TransferType.INPUT, 25, 87, ResourceFilters.itemTag(ItemTags.SAPLINGS), 64),
                    itemSlot(TransferType.INPUT, 43, 87, ResourceFilters.itemTag(ItemTags.SAPLINGS), 64),
                    itemSlot(TransferType.INPUT, 61, 87, ResourceFilters.itemTag(ItemTags.SAPLINGS), 64),
                    itemSlot(TransferType.INPUT, 79, 87, ResourceFilters.itemTag(ItemTags.SAPLINGS), 64),
                    itemSlot(TransferType.INPUT, 25, 111, ResourceFilters.ofResource(Items.WHEAT_SEEDS), 64),
                    itemSlot(TransferType.INPUT, 43, 111, ResourceFilters.ofResource(Items.WHEAT_SEEDS), 64),
                    itemSlot(TransferType.INPUT, 61, 111, ResourceFilters.ofResource(Items.WHEAT_SEEDS), 64),
                    itemSlot(TransferType.INPUT, 79, 111, ResourceFilters.ofResource(Items.WHEAT_SEEDS), 64)
            ),
            MachineEnergyStorage.spec(Galacticraft.CONFIG.machineEnergyStorageSize(), 30, 0),
            MachineFluidStorage.spec(
                    FluidResourceSlot.builder(TransferType.STRICT_INPUT)
                            .pos(56, 17)
                            .width(39)
                            .height(27)
                            .unmarked()
                            .capacity(MAX_WATER)
                            .filter(ResourceFilters.ofResource(Fluids.WATER))
            )
    );

    private final List<BlockPos> terraformableBlocks = new ArrayList<>();
    private final List<BlockPos> grassBlocks = new ArrayList<>();
    private final List<BlockPos> grownTrees = new ArrayList<>();
    private final int[] useCount = new int[2];

    private boolean treesDisabled;
    private boolean grassDisabled;
    private boolean bubbleVisible = true;
    private boolean lastActive;
    private double bubbleSize;
    private int saplingIndex = SAPLING_SLOT_START;
    private int disableCooldown;
    private long lastSyncTick = -SYNC_INTERVAL;

    public TerraformerBlockEntity(BlockPos pos, BlockState state) {
        super(GCBlockEntityTypes.TERRAFORMER, pos, state, SPEC);
    }

    private static ItemResourceSlot.Spec itemSlot(TransferType type, int x, int y,
                                                   dev.galacticraft.machinelib.api.filter.ResourceFilter<Item> filter,
                                                   int capacity) {
        return ItemResourceSlot.builder(type).pos(x, y).filter(filter).capacity(capacity);
    }

    @Override
    protected void tickConstant(@NotNull ServerLevel level, @NotNull BlockPos pos, @NotNull BlockState state,
                                @NotNull ProfilerFiller profiler) {
        super.tickConstant(level, pos, state, profiler);
        this.chargeFromSlot(CHARGE_SLOT);

        ItemResourceSlot waterInput = this.itemStorage().slot(WATER_CONTAINER_SLOT);
        Item previousItem = waterInput.getResource();
        DataComponentPatch previousComponents = waterInput.getComponents();
        long previousAmount = waterInput.getAmount();
        long previousModifications = waterInput.getModifications();

        this.takeFluidFromSlot(WATER_CONTAINER_SLOT, WATER_TANK, Fluids.WATER);

        // NeoForge fluid handlers mutate a copied ItemStack and replace the slot contents
        // without incrementing MachineLib's modification counter. Invalidate the menu cache
        // explicitly so the water bucket is rendered as an empty bucket immediately.
        if (waterInput.getModifications() == previousModifications
                && (waterInput.getResource() != previousItem
                || waterInput.getAmount() != previousAmount
                || !waterInput.getComponents().equals(previousComponents))) {
            waterInput.markModified();
        }

        if (this.disableCooldown > 0) {
            this.disableCooldown--;
        }
    }

    @Override
    protected @NotNull MachineStatus tick(@NotNull ServerLevel level, @NotNull BlockPos pos,
                                           @NotNull BlockState state, @NotNull ProfilerFiller profiler) {
        boolean hasEnergy = this.energyStorage().canExtract(ENERGY_PER_TICK);
        boolean hasMode = !this.grassDisabled || !this.treesDisabled;

        if (!hasEnergy) {
            this.shrinkBubble();
            this.deactivate();
            this.trySync(level);
            return MachineStatuses.NOT_ENOUGH_ENERGY;
        }

        if (!hasMode) {
            this.shrinkBubble();
            this.deactivate();
            this.trySync(level);
            return GCMachineStatuses.TERRAFORMER_DISABLED;
        }

        // Legacy draws power continuously whenever at least one mode is enabled.
        this.energyStorage().extractExact(ENERGY_PER_TICK);
        this.growBubble();

        boolean active = this.bubbleSize == MAX_SIZE
                && this.firstOccupied(BONE_MEAL_SLOT_START, BONE_MEAL_SLOT_END) >= 0
                && !this.fluidStorage().slot(WATER_TANK).isEmpty();

        if (active != this.lastActive || level.getGameTime() % SCAN_INTERVAL == 0) {
            this.scan(level, pos, active);
        }

        if (active) {
            if (level.getGameTime() % GRASS_INTERVAL == 0) {
                this.terraformGround(level);
            }
            if (!this.treesDisabled && level.getGameTime() % TREE_INTERVAL == 0) {
                this.growTree(level);
            }
        }

        this.lastActive = active;
        this.trySync(level);

        if (this.fluidStorage().slot(WATER_TANK).isEmpty()) {
            return GCMachineStatuses.NOT_ENOUGH_WATER;
        }
        if (this.firstOccupied(BONE_MEAL_SLOT_START, BONE_MEAL_SLOT_END) < 0) {
            return GCMachineStatuses.NOT_ENOUGH_BONE_MEAL;
        }
        if (!this.grassDisabled && this.firstOccupied(SEED_SLOT_START, SEED_SLOT_END) < 0) {
            return GCMachineStatuses.NOT_ENOUGH_SEEDS;
        }
        if (!this.treesDisabled && this.firstOccupied(SAPLING_SLOT_START, SAPLING_SLOT_END) < 0) {
            return GCMachineStatuses.NOT_ENOUGH_SAPLINGS;
        }
        if (this.bubbleSize < MAX_SIZE - 0.5D) {
            return GCMachineStatuses.BUBBLE_EXPANDING;
        }
        if (!this.treesDisabled && this.grassBlocks.isEmpty()) {
            return GCMachineStatuses.NO_VALID_TREE_BLOCKS;
        }
        if (!this.grassDisabled && this.terraformableBlocks.isEmpty()) {
            return GCMachineStatuses.NO_VALID_GRASS_BLOCKS;
        }
        return GCMachineStatuses.TERRAFORMING;
    }

    @Override
    protected void tickDisabled(@NotNull ServerLevel level, @NotNull BlockPos pos, @NotNull BlockState state,
                                @NotNull ProfilerFiller profiler) {
        this.shrinkBubble();
        this.deactivate();
        this.trySync(level);
        super.tickDisabled(level, pos, state, profiler);
    }

    private void growBubble() {
        this.setBubbleSize(Math.min(MAX_SIZE, this.bubbleSize + 0.1D));
    }

    private void shrinkBubble() {
        this.setBubbleSize(Math.max(0.0D, this.bubbleSize - 0.1D));
    }

    private void deactivate() {
        if (this.lastActive) {
            this.terraformableBlocks.clear();
            this.grassBlocks.clear();
            this.lastActive = false;
        }
    }

    private void scan(ServerLevel level, BlockPos origin, boolean active) {
        this.terraformableBlocks.clear();
        this.grassBlocks.clear();
        if (!active) {
            return;
        }

        int radius = (int) Math.ceil(this.bubbleSize);
        double radiusSquared = this.bubbleSize * this.bubbleSize;
        boolean doGrass = !this.grassDisabled && this.firstOccupied(SEED_SLOT_START, SEED_SLOT_END) >= 0;
        boolean doTrees = !this.treesDisabled && this.firstOccupied(SAPLING_SLOT_START, SAPLING_SLOT_END) >= 0;

        for (int x = origin.getX() - radius; x < origin.getX() + radius; x++) {
            for (int y = origin.getY() - radius; y < origin.getY() + radius; y++) {
                for (int z = origin.getZ() - radius; z < origin.getZ() + radius; z++) {
                    BlockPos candidate = new BlockPos(x, y, z);
                    BlockState candidateState = level.getBlockState(candidate);
                    if (candidateState.isAir() || distanceSquared(origin, x, y, z) >= radiusSquared) {
                        continue;
                    }

                    if (doGrass && this.isTerraformable(level, candidate, candidateState)) {
                        this.terraformableBlocks.add(candidate);
                    } else if (doTrees && candidateState.is(Blocks.GRASS_BLOCK)
                            && level.getBlockState(candidate.above()).isAir()) {
                        this.grassBlocks.add(candidate);
                    }
                }
            }
        }
    }

    private boolean isTerraformable(ServerLevel level, BlockPos pos, BlockState state) {
        if (!state.is(GCBlockTags.TERRAFORMABLE)) {
            return false;
        }
        BlockPos above = pos.above();
        BlockState aboveState = level.getBlockState(above);
        if (state.is(GCBlocks.MARS_SURFACE_ROCK)) {
            return !Block.isShapeFullBlock(aboveState.getCollisionShape(level, above));
        }
        return aboveState.isAir();
    }

    private void terraformGround(ServerLevel level) {
        if (this.terraformableBlocks.isEmpty()) {
            return;
        }

        int randomIndex = level.random.nextInt(this.terraformableBlocks.size());
        BlockPos target = this.terraformableBlocks.remove(randomIndex);
        if (!level.getBlockState(target).is(GCBlockTags.TERRAFORMABLE)) {
            return;
        }

        boolean placeWater = level.random.nextInt(40) == 0
                && isFullCube(level, target.west())
                && isFullCube(level, target.east())
                && isFullCube(level, target.north())
                && isFullCube(level, target.south());

        if (placeWater) {
            level.setBlock(target, Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
            this.checkUsage(2);
        } else {
            level.setBlock(target, Blocks.GRASS_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
            this.useCount[0]++;
            this.fluidStorage().slot(WATER_TANK).extract(ONE_MILLIBUCKET);
            this.checkUsage(1);
        }
    }

    private static boolean isFullCube(ServerLevel level, BlockPos pos) {
        return Block.isShapeFullBlock(level.getBlockState(pos).getCollisionShape(level, pos));
    }

    private void growTree(ServerLevel level) {
        if (this.grassBlocks.isEmpty()) {
            return;
        }

        int randomIndex = level.random.nextInt(this.grassBlocks.size());
        BlockPos grassPos = this.grassBlocks.remove(randomIndex);
        if (!level.getBlockState(grassPos).is(Blocks.GRASS_BLOCK)) {
            return;
        }

        BlockPos saplingPos = grassPos.above();
        for (BlockPos grownTree : this.grownTrees) {
            if (grownTree.distSqr(saplingPos) < 9.0D) {
                return;
            }
        }

        this.saplingIndex = this.randomOccupied(level, SAPLING_SLOT_START, SAPLING_SLOT_END);
        if (this.saplingIndex < 0) {
            return;
        }

        Item saplingItem = this.itemStorage().slot(this.saplingIndex).getResource();
        if (saplingItem == null) {
            return;
        }
        Block saplingBlock = Block.byItem(saplingItem);
        BlockState saplingState = saplingBlock.defaultBlockState();
        if (saplingBlock == Blocks.AIR || !saplingState.canSurvive(level, saplingPos)) {
            return;
        }

        level.setBlock(saplingPos, saplingState, Block.UPDATE_ALL);
        if (saplingBlock instanceof BonemealableBlock growable
                && level.getMaxLocalRawBrightness(saplingPos) >= 9
                && growable.isValidBonemealTarget(level, saplingPos, saplingState)) {
            growable.performBonemeal(level, level.random, saplingPos, saplingState);
            this.grownTrees.add(saplingPos.immutable());
        }

        this.useCount[1]++;
        this.fluidStorage().slot(WATER_TANK).extract(FIFTY_MILLIBUCKETS);
        this.checkUsage(0);
    }

    private void checkUsage(int type) {
        if ((this.useCount[0] + this.useCount[1]) % 4 == 0) {
            this.consumeFirst(BONE_MEAL_SLOT_START, BONE_MEAL_SLOT_END);
        }

        switch (type) {
            case 0 -> this.itemStorage().slot(this.saplingIndex).consumeOne();
            case 1 -> {
                if (this.useCount[0] % 4 == 0) {
                    this.consumeFirst(SEED_SLOT_START, SEED_SLOT_END);
                }
            }
            case 2 -> this.fluidStorage().slot(WATER_TANK).extract(FIFTY_MILLIBUCKETS);
            default -> throw new IllegalArgumentException("Unknown terraformer usage type " + type);
        }
    }

    private void consumeFirst(int start, int end) {
        int slot = this.firstOccupied(start, end);
        if (slot >= 0) {
            this.itemStorage().slot(slot).consumeOne();
        }
    }

    private int firstOccupied(int start, int end) {
        for (int slot = start; slot < end; slot++) {
            if (this.itemStorage().slot(slot).getAmount() > 0) {
                return slot;
            }
        }
        return -1;
    }

    private int randomOccupied(ServerLevel level, int start, int end) {
        int occupied = 0;
        for (int slot = start; slot < end; slot++) {
            if (this.itemStorage().slot(slot).getAmount() > 0) {
                occupied++;
            }
        }
        if (occupied == 0) {
            return -1;
        }

        int selected = level.random.nextInt(occupied);
        for (int slot = start; slot < end; slot++) {
            if (this.itemStorage().slot(slot).getAmount() > 0 && selected-- == 0) {
                return slot;
            }
        }
        return -1;
    }

    private static double distanceSquared(BlockPos origin, int x, int y, int z) {
        double dx = origin.getX() + 0.5D - x;
        double dy = origin.getY() + 0.5D - y;
        double dz = origin.getZ() + 0.5D - z;
        return dx * dx + dy * dy + dz * dz;
    }

    private void trySync(ServerLevel level) {
        if (level.getGameTime() - this.lastSyncTick >= SYNC_INTERVAL) {
            this.lastSyncTick = level.getGameTime();
            level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    public void toggle(int control) {
        if (control == 2) {
            this.setBubbleVisible(!this.bubbleVisible);
            return;
        }
        if (this.disableCooldown > 0) {
            return;
        }
        if (control == 0) {
            this.treesDisabled = !this.treesDisabled;
        } else if (control == 1) {
            this.grassDisabled = !this.grassDisabled;
        } else {
            return;
        }
        this.disableCooldown = 10;
        this.setChanged();
    }

    public boolean areTreesDisabled() {
        return this.treesDisabled;
    }

    public boolean isGrassDisabled() {
        return this.grassDisabled;
    }

    public boolean isBubbleVisible() {
        return this.bubbleVisible;
    }

    public void setBubbleVisible(boolean visible) {
        this.bubbleVisible = visible;
        this.setChanged();
        if (this.level != null) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    public double getBubbleSize() {
        return this.bubbleSize;
    }

    public void setBubbleSize(double size) {
        this.bubbleSize = Math.max(0.0D, Math.min(MAX_SIZE, size));
        this.setChanged();
    }

    public int getTerraformableBlockCount() {
        return this.terraformableBlocks.size();
    }

    public int getGrassBlockCount() {
        return this.grassBlocks.size();
    }

    public int getDisableCooldown() {
        return this.disableCooldown;
    }

    @Override
    public @Nullable MachineMenu<? extends MachineBlockEntity> createMenu(int syncId, Inventory inventory, Player player) {
        return new TerraformerMenu(syncId, player, this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
        super.saveAdditional(tag, lookup);
        tag.putDouble(Constant.Nbt.BUBBLE_SIZE, this.bubbleSize);
        tag.putIntArray(Constant.Nbt.USE_COUNT, this.useCount);
        tag.putBoolean(Constant.Nbt.TREES_DISABLED, this.treesDisabled);
        tag.putBoolean(Constant.Nbt.GRASS_DISABLED, this.grassDisabled);
        tag.putBoolean(Constant.Nbt.VISIBLE, this.bubbleVisible);
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
        super.loadAdditional(tag, lookup);
        this.bubbleSize = tag.getDouble(Constant.Nbt.BUBBLE_SIZE);
        int[] loadedUseCount = tag.getIntArray(Constant.Nbt.USE_COUNT);
        if (loadedUseCount.length >= 2) {
            this.useCount[0] = loadedUseCount[0];
            this.useCount[1] = loadedUseCount[1];
        }
        this.treesDisabled = tag.getBoolean(Constant.Nbt.TREES_DISABLED);
        this.grassDisabled = tag.getBoolean(Constant.Nbt.GRASS_DISABLED);
        this.bubbleVisible = !tag.contains(Constant.Nbt.VISIBLE) || tag.getBoolean(Constant.Nbt.VISIBLE);
    }

    @Override
    public @NotNull CustomPacketPayload createUpdatePayload() {
        return new TerraformerUpdatePayload(this.worldPosition, this.bubbleSize, this.bubbleVisible);
    }

    @Override
    public @NotNull CompoundTag getUpdateTag(HolderLookup.Provider lookup) {
        CompoundTag tag = new CompoundTag();
        this.saveAdditional(tag, lookup);
        this.populateUpdateTag(tag);
        return tag;
    }
}
