/* Copyright (c) 2026 Team Galacticraft. Licensed under the MIT license. */
package dev.galacticraft.mod.content.block.entity.machine;

import com.mojang.datafixers.util.Pair;
import dev.galacticraft.machinelib.api.block.entity.MachineBlockEntity;
import dev.galacticraft.machinelib.api.filter.ResourceFilters;
import dev.galacticraft.machinelib.api.machine.MachineStatus;
import dev.galacticraft.machinelib.api.machine.MachineStatuses;
import dev.galacticraft.machinelib.api.storage.MachineEnergyStorage;
import dev.galacticraft.machinelib.api.storage.MachineItemStorage;
import dev.galacticraft.machinelib.api.storage.StorageSpec;
import dev.galacticraft.machinelib.api.storage.slot.ItemResourceSlot;
import dev.galacticraft.machinelib.api.transfer.TransferType;
import dev.galacticraft.machinelib.api.transfer.ResourceFlow;
import dev.galacticraft.machinelib.api.transfer.ResourceType;
import dev.galacticraft.machinelib.api.util.BlockFace;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.Galacticraft;
import dev.galacticraft.mod.content.GCBlockEntityTypes;
import dev.galacticraft.mod.content.GCBlocks;
import dev.galacticraft.mod.content.block.special.telepad.TelepadRegistry;
import dev.galacticraft.mod.screen.ShortRangeTelepadMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class ShortRangeTelepadBlockEntity extends MachineBlockEntity {
    public static final int TELEPORT_TIME = 150, RANGE = 256, ENERGY_PER_TELEPORT = 2500, ENERGY_PER_TICK = 50;
    public enum TargetResult { VALID, NOT_FOUND, TOO_FAR, WRONG_DIMENSION, DISABLED, NO_ENERGY, BLOCKED, SELF }
    private static final StorageSpec SPEC = StorageSpec.of(
            MachineItemStorage.spec(ItemResourceSlot.builder(TransferType.TRANSFER).pos(152, 105).capacity(1)
                    .filter(ResourceFilters.CAN_EXTRACT_ENERGY).icon(Pair.of(InventoryMenu.BLOCK_ATLAS, Constant.SlotSprite.ENERGY))),
            MachineEnergyStorage.spec(Galacticraft.CONFIG.machineEnergyStorageSize(), 120, 0));
    private int address = -1, destination = -1, ticks, teleportTime, cooldown;
    private boolean addressValid, manuallyDisabled, teleporting, previousTopInput;
    private TargetResult targetResult = TargetResult.NOT_FOUND;

    public ShortRangeTelepadBlockEntity(BlockPos pos, BlockState state) {
        super(GCBlockEntityTypes.SHORT_RANGE_TELEPAD, pos, state, SPEC);
    }

    private GlobalPos location() { return GlobalPos.of(level.dimension(), worldPosition); }
    public int getAddress() { return address; }
    public int getDestination() { return destination; }
    public boolean hasValidAddress() { return addressValid; }
    public TargetResult getTargetResult() { return targetResult; }
    public int getTeleportTime() { return teleportTime; }
    public boolean isManuallyDisabled() { return manuallyDisabled; }
    public int getCooldown() { return cooldown; }
    public boolean canEdit(Player player) { return getSecurity().isOwner(player); }
    public boolean isEnabled() { return !manuallyDisabled && !isDisabled(); }
    public boolean hasTopEnergyInput() {
        var face = getIOConfig().get(BlockFace.TOP);
        return face.getType() == ResourceType.ENERGY && face.getFlow() != ResourceFlow.OUTPUT;
    }

    public void configure(int address, int destination) {
        if (!(level instanceof ServerLevel serverLevel)
                || address < -1 || address > 999_999 || destination < -1 || destination > 999_999) return;
        TelepadRegistry registry = TelepadRegistry.get(serverLevel.getServer());
        if (this.address != address) registry.remove(this.address, location());
        this.address = address;
        this.destination = destination;
        teleportTime = 0;
        teleporting = false;
        refresh(serverLevel);
        setChanged();
    }

    public void toggleDisabled() {
        if (cooldown != 0 || !(level instanceof ServerLevel serverLevel)) return;
        manuallyDisabled = !manuallyDisabled;
        cooldown = 10;
        refresh(serverLevel);
        setChanged();
    }

    public void unregister() {
        if (level instanceof ServerLevel serverLevel) TelepadRegistry.get(serverLevel.getServer()).remove(address, location());
    }

    private void refresh(ServerLevel level) {
        TelepadRegistry registry = TelepadRegistry.get(level.getServer());
        var previous = registry.get(address);
        if (previous != null && !previous.location().equals(location())) {
            var ownerLevel = level.getServer().getLevel(previous.location().dimension());
            // Preserve unloaded owners without loading chunks just to edit an address.
            if (ownerLevel == null || ownerLevel.hasChunkAt(previous.location().pos())
                    && (!(ownerLevel.getBlockEntity(previous.location().pos()) instanceof ShortRangeTelepadBlockEntity owner) || owner.address != address)) {
                registry.remove(address, previous.location());
            }
        }
        addressValid = registry.claim(address, location(), isEnabled());
        var target = registry.get(destination);
        targetResult = target == null ? TargetResult.NOT_FOUND
                : target.location().equals(location()) ? TargetResult.SELF
                : !target.location().dimension().equals(level.dimension()) ? TargetResult.WRONG_DIMENSION
                : worldPosition.distSqr(target.location().pos()) >= (double) RANGE * RANGE ? TargetResult.TOO_FAR
                : !target.enabled() ? TargetResult.DISABLED : TargetResult.VALID;
    }

    @Override protected void tickConstant(ServerLevel level, BlockPos pos, BlockState state, ProfilerFiller profiler) {
        super.tickConstant(level, pos, state, profiler);
        ticks++;
        boolean topInput = hasTopEnergyInput();
        if (topInput != previousTopInput) {
            previousTopInput = topInput;
            level.updateNeighborsAt(pos.above(2), GCBlocks.TELEPAD_FRAME);
        }
        chargeFromSlot(0);
        if (cooldown > 0) cooldown--;
        if (ticks == 1 || ticks % 40 == 0) refresh(level);
        if (ticks % 10 == 0) level.sendBlockUpdated(pos, state, state, 2);
    }

    private void decay() {
        teleporting = false;
        teleportTime = Math.max(0, teleportTime - 1);
    }

    @Override protected void tickDisabled(ServerLevel level, BlockPos pos, BlockState state, ProfilerFiller profiler) { decay(); }

    @Override protected MachineStatus tick(ServerLevel level, BlockPos pos, BlockState state, ProfilerFiller profiler) {
        if (manuallyDisabled) { decay(); return MachineStatuses.IDLE; }
        boolean ready = addressValid && targetResult == TargetResult.VALID && energyStorage().getAmount() >= ENERGY_PER_TELEPORT;
        if (!ready) decay();
        else if (teleporting || ticks % 5 == 0) {
            var entities = occupants(level);
            teleporting = !entities.isEmpty();
            if (teleporting) {
                if (++teleportTime >= TELEPORT_TIME) {
                    teleport(level, entities);
                    teleportTime = 0;
                    teleporting = false;
                }
            } else teleportTime = Math.max(0, teleportTime - 1);
        } else teleportTime = Math.max(0, teleportTime - 1);
        // GC4 consumes 50 gJ/t whenever enabled, including while waiting for an occupant.
        energyStorage().extract(Math.min(ENERGY_PER_TICK, energyStorage().getAmount()));
        return teleporting ? MachineStatuses.ACTIVE : MachineStatuses.IDLE;
    }

    private java.util.List<LivingEntity> occupants(ServerLevel level) {
        return level.getEntitiesOfClass(LivingEntity.class, new AABB(worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(),
                worldPosition.getX() + 1, worldPosition.getY() + 2, worldPosition.getZ() + 1), LivingEntity::isAlive);
    }

    private void teleport(ServerLevel level, java.util.List<LivingEntity> entities) {
        refresh(level);
        var entry = TelepadRegistry.get(level.getServer()).get(destination);
        if (!addressValid || targetResult != TargetResult.VALID || entry == null) return;
        BlockPos targetPos = entry.location().pos();
        level.getChunkAt(targetPos);
        if (!(level.getBlockEntity(targetPos) instanceof ShortRangeTelepadBlockEntity target) || target.address != destination) {
            TelepadRegistry.get(level.getServer()).remove(destination, entry.location());
            targetResult = TargetResult.NOT_FOUND;
            return;
        }
        target.refresh(level);
        if (!target.addressValid || !target.isEnabled()) { targetResult = TargetResult.DISABLED; return; }
        if (energyStorage().getAmount() < ENERGY_PER_TELEPORT || target.energyStorage().getAmount() < ENERGY_PER_TELEPORT) {
            targetResult = TargetResult.NO_ENERGY;
            return;
        }
        Vec3 arrival = new Vec3(targetPos.getX() + 0.5, targetPos.getY() + 0.45, targetPos.getZ() + 0.5);
        for (LivingEntity entity : entities) {
            AABB bounds = entity.getBoundingBox().move(arrival.subtract(entity.position()));
            if (!level.getWorldBorder().isWithinBounds(bounds) || level.getBlockCollisions(entity, bounds).iterator().hasNext()) {
                targetResult = TargetResult.BLOCKED;
                return;
            }
        }
        for (LivingEntity entity : entities) {
            entity.stopRiding();
            entity.teleportTo(arrival.x, arrival.y, arrival.z);
            entity.setDeltaMovement(Vec3.ZERO);
            entity.fallDistance = 0;
        }
        energyStorage().extract(ENERGY_PER_TELEPORT);
        target.energyStorage().extract(ENERGY_PER_TELEPORT);
        setChanged();
        target.setChanged();
    }

    @Override public ShortRangeTelepadMenu createMenu(int id, Inventory inventory, Player player) {
        return new ShortRangeTelepadMenu(id, player, this);
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
        super.saveAdditional(tag, lookup);
        tag.putInt("address", address);
        tag.putInt("destination", destination);
        tag.putBoolean("disabled", manuallyDisabled);
    }

    @Override public void loadAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
        super.loadAdditional(tag, lookup);
        address = tag.contains("address") ? tag.getInt("address") : -1;
        destination = tag.contains("destination") ? tag.getInt("destination") : -1;
        manuallyDisabled = tag.getBoolean("disabled");
        addressValid = tag.getBoolean("address_valid");
        teleportTime = tag.getInt("teleport_time");
        ticks = cooldown = 0;
        teleporting = false;
        int result = tag.getInt("target_result");
        targetResult = tag.contains("target_result") && result >= 0 && result < TargetResult.values().length ? TargetResult.values()[result] : TargetResult.NOT_FOUND;
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider lookup) {
        CompoundTag tag = saveWithoutMetadata(lookup);
        tag.putBoolean("address_valid", addressValid);
        tag.putInt("teleport_time", teleportTime);
        tag.putInt("target_result", targetResult.ordinal());
        return tag;
    }
}
