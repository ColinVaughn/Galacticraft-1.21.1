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

package dev.galacticraft.mod.content.block.jupiter.machine;

import com.mojang.datafixers.util.Pair;
import dev.galacticraft.machinelib.api.block.MachineBlock;
import dev.galacticraft.machinelib.api.block.entity.MachineBlockEntity;
import dev.galacticraft.machinelib.api.filter.ResourceFilters;
import dev.galacticraft.machinelib.api.machine.MachineStatus;
import dev.galacticraft.machinelib.api.machine.MachineStatuses;
import dev.galacticraft.machinelib.api.menu.MachineMenu;
import dev.galacticraft.machinelib.api.storage.MachineEnergyStorage;
import dev.galacticraft.machinelib.api.storage.MachineItemStorage;
import dev.galacticraft.machinelib.api.storage.StorageSpec;
import dev.galacticraft.machinelib.api.storage.slot.ItemResourceSlot;
import dev.galacticraft.machinelib.api.transfer.TransferType;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.Galacticraft;
import dev.galacticraft.mod.content.GCBlockEntityTypes;
import dev.galacticraft.mod.content.block.jupiter.JupiterAnimated;
import dev.galacticraft.mod.content.block.jupiter.JupiterMachineStatuses;
import dev.galacticraft.mod.content.block.jupiter.JupiterReadout;
import dev.galacticraft.mod.screen.GCMenuTypes;
import dev.galacticraft.mod.screen.jupiter.JupiterMachineMenu;
import dev.galacticraft.mod.tag.GCBlockTags;
import dev.galacticraft.mod.world.dimension.jupiter.PressureHabitats;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Protects a powered habitat enclosed by pressure-rated walls and closed hatches. Flood fill is
 * bounded by {@link #MAX_RADIUS} and {@link #MAX_VOLUME}; occupants receive {@link PressureHabitats#RATING}.
 */
public class PressureStabilizerBlockEntity extends MachineBlockEntity implements JupiterReadout, JupiterAnimated {
    public static final int CHARGE_SLOT = 0;
    public static final int ENERGY_PER_TICK = 40;
    public static final int SCAN_INTERVAL = 40;
    public static final int MAX_VOLUME = 2048;
    public static final int MAX_RADIUS = 16;
    private static final StorageSpec SPEC = StorageSpec.of(
            MachineItemStorage.spec(
                    ItemResourceSlot.builder(TransferType.TRANSFER).pos(8, 62).capacity(1)
                            .filter(ResourceFilters.CAN_EXTRACT_ENERGY).icon(Pair.of(InventoryMenu.BLOCK_ATLAS, Constant.SlotSprite.ENERGY))
            ),
            MachineEnergyStorage.spec(Galacticraft.CONFIG.machineEnergyStorageSize(), ENERGY_PER_TICK * 2, 0)
    );

    /** What the last flood found. */
    public enum Result { SEALED, LEAK, UNRATED_WALL, NO_HABITAT }

    private Result result = Result.NO_HABITAT;
    private LongSet volume = new LongOpenHashSet();
    private @Nullable BlockPos badWall;
    private String badWallName = "";
    private boolean scanned;

    public PressureStabilizerBlockEntity(BlockPos pos, BlockState state) {
        super(GCBlockEntityTypes.PRESSURE_STABILIZER, pos, state, SPEC);
    }

    public Result result() {
        return this.result;
    }

    public int volume() {
        return this.result == Result.SEALED ? this.volume.size() : 0;
    }

    @Override
    protected void tickConstant(@NotNull ServerLevel level, @NotNull BlockPos pos, @NotNull BlockState state, @NotNull ProfilerFiller profiler) {
        super.tickConstant(level, pos, state, profiler);
        this.chargeFromSlot(CHARGE_SLOT);
        if (!this.scanned || (level.getGameTime() + pos.asLong()) % SCAN_INTERVAL == 0) {
            profiler.push("habitat_scan");
            this.scan(level, pos);
            this.scanned = true;
            profiler.pop();
        }
    }

    @Override
    protected @NotNull MachineStatus tick(@NotNull ServerLevel level, @NotNull BlockPos pos, @NotNull BlockState state, @NotNull ProfilerFiller profiler) {
        if (this.result != Result.SEALED) {
            PressureHabitats.remove(level, pos);
            return switch (this.result) {
                case LEAK -> JupiterMachineStatuses.HABITAT_LEAKING;
                case UNRATED_WALL -> JupiterMachineStatuses.UNRATED_WALL;
                default -> JupiterMachineStatuses.NO_HABITAT;
            };
        }
        if (!this.energyStorage().canExtract(ENERGY_PER_TICK)) {
            PressureHabitats.remove(level, pos);
            return MachineStatuses.NOT_ENOUGH_ENERGY;
        }
        this.energyStorage().extract(ENERGY_PER_TICK);
        PressureHabitats.set(level, pos, this.volume);
        return JupiterMachineStatuses.HABITAT_SEALED;
    }

    @Override
    protected void tickDisabled(@NotNull ServerLevel level, @NotNull BlockPos pos, @NotNull BlockState state, @NotNull ProfilerFiller profiler) {
        super.tickDisabled(level, pos, state, profiler);
        PressureHabitats.remove(level, pos);
    }

    /** Floods the open cells around the stabilizer and classifies what encloses them. */
    public void scan(Level level, BlockPos origin) {
        LongOpenHashSet interior = new LongOpenHashSet();
        LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        this.badWall = null;
        this.badWallName = "";
        for (Direction direction : Direction.values()) {
            BlockPos start = origin.relative(direction);
            if (this.isOpen(level, start) && interior.add(start.asLong())) queue.enqueue(start.asLong());
        }
        if (interior.isEmpty()) {
            this.finish(Result.NO_HABITAT, interior);
            return;
        }
        while (!queue.isEmpty()) {
            long packed = queue.dequeueLong();
            for (Direction direction : Direction.values()) {
                cursor.set(packed).move(direction);
                long next = cursor.asLong();
                if (interior.contains(next) || cursor.equals(origin)) continue;
                if (Math.max(Math.abs(cursor.getX() - origin.getX()), Math.max(Math.abs(cursor.getY() - origin.getY()), Math.abs(cursor.getZ() - origin.getZ()))) > MAX_RADIUS
                        || !level.isLoaded(cursor) || level.isOutsideBuildHeight(cursor)) {
                    this.finish(Result.LEAK, interior);
                    return;
                }
                if (this.isOpen(level, cursor)) {
                    interior.add(next);
                    if (interior.size() > MAX_VOLUME) {
                        this.finish(Result.LEAK, interior);
                        return;
                    }
                    queue.enqueue(next);
                } else if (this.badWall == null && !this.isRated(level, cursor)) {
                    this.badWall = cursor.immutable();
                    this.badWallName = level.getBlockState(cursor).getBlock().getName().getString();
                }
            }
        }
        interior.add(origin.asLong());
        this.finish(this.badWall != null ? Result.UNRATED_WALL : Result.SEALED, interior);
    }

    private void finish(Result result, LongOpenHashSet interior) {
        this.result = result;
        this.volume = result == Result.SEALED ? interior : new LongOpenHashSet();
        if (result != Result.UNRATED_WALL) {
            this.badWall = null;
            this.badWallName = "";
        }
    }

    /**
     * Whether air flows through {@code pos}: anything without a full collision box (air, torches, slabs, open hatches),
     * except a closed pressure-rated hatch, which seals however thin it is.
     */
    private boolean isOpen(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.is(GCBlockTags.PRESSURE_RATED)) return state.hasProperty(BlockStateProperties.OPEN) && state.getValue(BlockStateProperties.OPEN);
        return !state.isCollisionShapeFullBlock(level, pos);
    }

    /** A solid wall that can hold the pressure: tagged rated, or a machine built into the wall. */
    private boolean isRated(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.is(GCBlockTags.PRESSURE_RATED) || state.hasBlockEntity();
    }

    /** [volume, max volume, rating, result ordinal, wall x, wall y, wall z]. */
    @Override
    public List<Integer> readout() {
        BlockPos wall = this.badWall;
        return List.of(this.volume(), MAX_VOLUME, (int) PressureHabitats.RATING, this.result.ordinal(),
                wall != null ? wall.getX() : 0, wall != null ? wall.getY() : 0, wall != null ? wall.getZ() : 0);
    }

    @Override
    public List<String> readoutLines() {
        return List.of(this.badWallName);
    }

    @Override
    public boolean isAnimating() {
        return MachineBlock.isActive(this.getBlockState());
    }

    @Override
    public @Nullable MachineMenu<? extends MachineBlockEntity> createMenu(int syncId, Inventory inv, Player player) {
        return new JupiterMachineMenu<>(GCMenuTypes.PRESSURE_STABILIZER, syncId, player, this);
    }
}
