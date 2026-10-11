/*
 * Copyright (c) 2019-2026 Team Galacticraft
 * Copyright (c) 2026 Colin Vaughn
 * Licensed under the MIT license. See LICENSE in the project root.
 */
package dev.galacticraft.mod.content.block.entity.machine;

import com.mojang.datafixers.util.Pair;
import dev.galacticraft.api.rocket.LaunchStage;
import dev.galacticraft.machinelib.api.block.entity.MachineBlockEntity;
import dev.galacticraft.machinelib.api.filter.ResourceFilters;
import dev.galacticraft.machinelib.api.machine.MachineStatus;
import dev.galacticraft.machinelib.api.machine.MachineStatuses;
import dev.galacticraft.machinelib.api.storage.MachineEnergyStorage;
import dev.galacticraft.machinelib.api.storage.MachineItemStorage;
import dev.galacticraft.machinelib.api.storage.StorageSpec;
import dev.galacticraft.machinelib.api.storage.slot.ItemResourceSlot;
import dev.galacticraft.machinelib.api.transfer.TransferType;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.Galacticraft;
import dev.galacticraft.mod.api.entity.Dockable;
import dev.galacticraft.mod.content.GCBlockEntityTypes;
import dev.galacticraft.mod.content.block.special.launchpad.AbstractLaunchPad;
import dev.galacticraft.mod.content.block.special.launchpad.CargoPadRegistry;
import dev.galacticraft.mod.content.block.special.launchpad.LaunchPadBlockEntity;
import dev.galacticraft.mod.content.entity.vehicle.CargoRocketEntity;
import dev.galacticraft.mod.content.entity.vehicle.ContainerVehicle;
import dev.galacticraft.mod.content.entity.vehicle.RocketEntity;
import dev.galacticraft.mod.screen.LaunchControllerMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.UUID;

/** Legacy frequency routing and launch scheduling, backed by the existing pad address book. */
public class LaunchControllerBlockEntity extends MachineBlockEntity {
    public static final long ENERGY_PER_TICK = 1;
    public static final int ENABLED = 1;
    public static final int SCHEDULING = 2;
    public static final int KEEP_PAD = 4;
    public static final int HIDE_DESTINATION = 8;
    private static final TicketType<BlockPos> TICKET = TicketType.create("galacticraft_launch_controller",
            Comparator.comparingLong(BlockPos::asLong), 60);
    private static final StorageSpec SPEC = StorageSpec.of(
            MachineItemStorage.builder().add(ItemResourceSlot.builder(TransferType.TRANSFER)
                    .pos(152, 105).filter(ResourceFilters.CAN_EXTRACT_ENERGY)
                    .icon(Pair.of(InventoryMenu.BLOCK_ATLAS, Constant.SlotSprite.ENERGY))),
            MachineEnergyStorage.spec(Galacticraft.CONFIG.machineEnergyStorageSize(), 120, 0));

    private int frequency = -1;
    private int destination = -1;
    private int options = ENABLED | KEEP_PAD | HIDE_DESTINATION;
    private LaunchCondition condition = LaunchCondition.CARGO_UNLOADED;
    private @Nullable UUID scheduledRocket;
    private int scheduledTicks;

    public LaunchControllerBlockEntity(BlockPos pos, BlockState state) {
        super(GCBlockEntityTypes.LAUNCH_CONTROLLER, pos, state, SPEC);
    }

    @Override
    protected void tickConstant(ServerLevel level, BlockPos pos, BlockState state, ProfilerFiller profiler) {
        this.chargeFromSlot(0);
        if (level.getGameTime() % 20 == 0) {
            this.keepLoaded(level);
            if (CargoPadRegistry.isValidAddress(this.frequency) && !this.hasValidFrequency()) {
                CargoPadRegistry.get(level.getServer()).assign(level.getServer(), this.frequency,
                        CargoPadRegistry.PadTarget.of(level, pos));
            }
        }
    }

    /** Short-lived tickets also expire automatically when the controller is broken. */
    public void keepLoaded(ServerLevel level) {
        // Vanilla otherwise stops both entities and machines after 300 ticks without players.
        level.resetEmptyTime();
        for (int x = (this.worldPosition.getX() - 2) >> 4; x <= (this.worldPosition.getX() + 2) >> 4; x++) {
            for (int z = (this.worldPosition.getZ() - 2) >> 4; z <= (this.worldPosition.getZ() + 2) >> 4; z++) {
                level.getChunkSource().addRegionTicket(TICKET, new ChunkPos(x, z), 2, this.worldPosition);
            }
        }
    }

    @Override
    protected MachineStatus tick(ServerLevel level, BlockPos pos, BlockState state, ProfilerFiller profiler) {
        if (!this.hasOption(ENABLED)) {
            this.resetSchedule();
            return MachineStatuses.IDLE;
        }
        if (!this.energyStorage().canExtract(ENERGY_PER_TICK)) {
            this.resetSchedule();
            return MachineStatuses.NOT_ENOUGH_ENERGY;
        }
        this.tickSchedule(level);
        this.energyStorage().extract(ENERGY_PER_TICK);
        return MachineStatuses.ACTIVE;
    }

    @Override
    protected void tickDisabled(ServerLevel level, BlockPos pos, BlockState state, ProfilerFiller profiler) {
        this.resetSchedule();
    }

    private void tickSchedule(ServerLevel level) {
        LaunchPadBlockEntity pad = this.getConnectedPad();
        Dockable rocket = pad == null ? null : pad.getDockedEntity();
        if (!this.hasOption(SCHEDULING) || !this.hasValidFrequency() || !this.hasValidDestination()
                || !(rocket instanceof CargoRocketEntity || rocket instanceof RocketEntity)
                || rocket.inFlight()
                || rocket instanceof RocketEntity crewed && (crewed.getPassengers().isEmpty()
                || crewed.getLaunchStage().ordinal() >= LaunchStage.IGNITED.ordinal())) {
            this.resetSchedule();
            return;
        }
        UUID id = rocket.asEntity().getUUID();
        if (!id.equals(this.scheduledRocket)) {
            this.scheduledRocket = id;
            this.scheduledTicks = 0;
        }
        this.scheduledTicks++;
        if (this.condition.isMet((ContainerVehicle) rocket, rocket.getFuelTankAmount(),
                rocket.getFuelTankCapacity(), this.scheduledTicks, level.hasNeighborSignal(this.worldPosition))
                && rocket.getFuelTankAmount() > rocket.getFuelTankCapacity() * 2 / 5) {
            if (rocket instanceof CargoRocketEntity cargo) cargo.ignite();
            if (rocket instanceof RocketEntity crewed) crewed.igniteFromController();
        }
    }

    private void resetSchedule() {
        this.scheduledRocket = null;
        this.scheduledTicks = 0;
    }

    public boolean configure(int frequency, int destination, int condition, int options) {
        if (!(this.level instanceof ServerLevel level) || !validSettings(frequency, destination, condition, options)) {
            return false;
        }
        CargoPadRegistry registry = CargoPadRegistry.get(level.getServer());
        CargoPadRegistry.PadTarget target = CargoPadRegistry.PadTarget.of(level, this.worldPosition);
        if (frequency >= 0 && !registry.assign(level.getServer(), frequency, target)) return false;
        if (frequency == -1) registry.unregister(target);
        this.frequency = frequency;
        this.destination = destination;
        this.condition = LaunchCondition.values()[condition];
        this.options = options;
        this.resetSchedule();
        this.keepLoaded(level);
        this.setChanged();
        return true;
    }

    public static boolean validSettings(int frequency, int destination, int condition, int options) {
        return frequency >= -1 && frequency <= CargoPadRegistry.MAX_ADDRESS
                && destination >= -1 && destination <= CargoPadRegistry.MAX_ADDRESS
                && (frequency == -1 || frequency != destination)
                && condition >= 0 && condition < LaunchCondition.values().length
                && options >= 0 && options <= 15;
    }

    public boolean canEdit(Player player) {
        return this.getSecurity().isOwner(player);
    }

    public int getFrequency() { return this.frequency; }
    public int getDestination() { return this.destination; }
    public int getOptions() { return this.options; }
    public LaunchCondition getCondition() { return this.condition; }
    public boolean hasOption(int option) { return (this.options & option) != 0; }

    public boolean hasValidFrequency() {
        return this.level instanceof ServerLevel level && CargoPadRegistry.get(level.getServer()).isAssigned(
                this.frequency, CargoPadRegistry.PadTarget.of(level, this.worldPosition));
    }

    public boolean hasValidDestination() {
        return this.level instanceof ServerLevel level && this.destination != this.frequency
                && CargoPadRegistry.get(level.getServer()).resolve(level.getServer(), this.destination)
                .filter(target -> target.pad() != this.getConnectedPad()).isPresent();
    }

    public boolean canRoute() {
        return this.hasOption(ENABLED) && !this.isDisabled()
                && this.energyStorage().canExtract(ENERGY_PER_TICK)
                && this.hasValidFrequency() && this.hasValidDestination();
    }

    public @Nullable LaunchPadBlockEntity getConnectedPad() {
        if (this.level == null) return null;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos part = this.worldPosition.relative(direction);
            BlockState state = this.level.getBlockState(part);
            if (!(state.getBlock() instanceof AbstractLaunchPad) || state.getValue(AbstractLaunchPad.PART) == AbstractLaunchPad.Part.NONE) continue;
            BlockPos center = part.offset(AbstractLaunchPad.partToCenterPos(state.getValue(AbstractLaunchPad.PART)));
            if (this.level.getBlockEntity(center) instanceof LaunchPadBlockEntity pad
                    && pad.getPadType() == LaunchPadBlockEntity.Type.ROCKET
                    && pad.getBlockState().getValue(AbstractLaunchPad.PART) == AbstractLaunchPad.Part.CENTER) return pad;
        }
        return null;
    }

    public static @Nullable LaunchControllerBlockEntity findController(Level level, BlockPos pad) {
        for (BlockPos pos : BlockPos.betweenClosed(pad.offset(-2, 0, -2), pad.offset(2, 0, 2))) {
            if (level.getBlockEntity(pos) instanceof LaunchControllerBlockEntity controller) {
                LaunchPadBlockEntity connected = controller.getConnectedPad();
                if (connected != null && connected.getBlockPos().equals(pad)) return controller;
            }
        }
        return null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
        super.saveAdditional(tag, lookup);
        tag.putInt("ControllerFrequency", this.frequency);
        tag.putInt("TargetFrequency", this.destination);
        tag.putInt("LaunchSelection", this.condition.ordinal());
        tag.putInt("ControllerOptions", this.options);
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
        super.loadAdditional(tag, lookup);
        this.frequency = tag.contains("ControllerFrequency") ? tag.getInt("ControllerFrequency") : -1;
        this.destination = tag.contains("TargetFrequency") ? tag.getInt("TargetFrequency") : -1;
        int selection = tag.getInt("LaunchSelection");
        this.condition = selection >= 0 && selection < LaunchCondition.values().length
                ? LaunchCondition.values()[selection] : LaunchCondition.CARGO_UNLOADED;
        this.options = tag.contains("ControllerOptions") ? tag.getInt("ControllerOptions") & 15 : ENABLED | KEEP_PAD | HIDE_DESTINATION;
        this.resetSchedule();
    }

    @Override
    public LaunchControllerMenu createMenu(int syncId, Inventory inventory, Player player) {
        return new LaunchControllerMenu(syncId, inventory, this);
    }

    public enum LaunchCondition {
        CARGO_UNLOADED, CARGO_FULL, FULLY_FUELED, INSTANT, TEN_SECONDS, THIRTY_SECONDS, ONE_MINUTE, REDSTONE;

        public boolean isMet(ContainerVehicle vehicle, long fuel, long capacity, int ticks, boolean redstone) {
            return switch (this) {
                case CARGO_UNLOADED -> cargoMatches(vehicle, false);
                case CARGO_FULL -> cargoMatches(vehicle, true);
                case FULLY_FUELED -> capacity > 0 && fuel >= capacity;
                case INSTANT -> ticks >= 12;
                case TEN_SECONDS -> ticks >= 200;
                case THIRTY_SECONDS -> ticks >= 600;
                case ONE_MINUTE -> ticks >= 1200;
                case REDSTONE -> redstone;
            };
        }

        private static boolean cargoMatches(ContainerVehicle vehicle, boolean full) {
            Container inventory = vehicle.getVehicleInventory();
            int slots = vehicle.getCargoSlotCount();
            if (full && slots == 0) return false;
            for (int i = 0; i < slots; i++) {
                ItemStack stack = inventory.getItem(i);
                if (full ? stack.isEmpty() || stack.getCount() < Math.min(stack.getMaxStackSize(), inventory.getMaxStackSize())
                        : !stack.isEmpty()) return false;
            }
            return true;
        }
    }
}
