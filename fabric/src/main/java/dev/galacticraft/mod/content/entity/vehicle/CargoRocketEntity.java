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

package dev.galacticraft.mod.content.entity.vehicle;

import dev.architectury.registry.menu.ExtendedMenuProvider;
import dev.architectury.registry.menu.MenuRegistry;
import dev.galacticraft.machinelib.api.storage.StorageAccess;
import dev.galacticraft.machinelib.api.transfer.MLFluidStack;
import dev.galacticraft.api.component.GCDataComponents;
import dev.galacticraft.api.fluid.FluidData;
import dev.galacticraft.mod.api.block.entity.FuelDock;
import dev.galacticraft.mod.api.entity.Dockable;
import dev.galacticraft.mod.content.item.GCItems;
import dev.galacticraft.mod.content.block.special.launchpad.AbstractLaunchPad;
import dev.galacticraft.mod.content.block.special.launchpad.CargoPadRegistry;
import dev.galacticraft.mod.content.block.special.launchpad.LaunchPadBlockEntity;
import dev.galacticraft.mod.screen.VehicleInventoryMenu;
import dev.galacticraft.mod.storage.SimpleFluidTank;
import dev.galacticraft.mod.util.FluidUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerListener;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import static dev.galacticraft.api.universe.celestialbody.landable.teleporter.type.CelestialTeleporterType.NO_RELATIVE_MOVEMENT;

/** A persistent, addressable cargo rocket with a 27-slot automated cargo hold. */
public class CargoRocketEntity extends GCVehicle
        implements ContainerListener, ContainerVehicle, ExtendedMenuProvider, Dockable {
    public static final int INVENTORY_SIZE = 27;
    public static final int TANK_CAPACITY = 2;

    private static final int TRAVEL_HEIGHT = 300;
    private static final double MAX_ASCENT_SPEED = 1.2D;
    private static final int MAX_LAUNCH_TICKS = 20 * 60;
    private static final long FLIGHT_FUEL = FluidUtil.bucketsToDroplets(1) / 4L;
    private static final double PAD_Y_OFFSET = 0.35D;

    protected SimpleContainer inventory;
    private final SimpleFluidTank tank = new SimpleFluidTank(
            FluidUtil.bucketsToDroplets(TANK_CAPACITY), () -> {
            });

    private FlightState flightState = FlightState.IDLE;
    private int launchTicks;
    private int targetAddress = -1;
    private @Nullable ResourceKey<Level> originDimension;
    private @Nullable BlockPos originPadPos;
    private @Nullable BlockPos linkedPadPos;
    private @Nullable FuelDock linkedPad;
    private String lastFailure = "";

    public CargoRocketEntity(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    protected int getInventorySize() {
        return INVENTORY_SIZE;
    }

    protected void createInventory() {
        SimpleContainer old = this.inventory;
        this.inventory = new SimpleContainer(this.getInventorySize());
        if (old != null) {
            old.removeListener(this);
            int size = Math.min(old.getContainerSize(), this.inventory.getContainerSize());
            for (int index = 0; index < size; index++) {
                this.inventory.setItem(index, old.getItem(index).copy());
            }
        }
        this.inventory.addListener(this);
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        this.createInventory();
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag nbt) {
        this.flightState = nbt.contains("FlightState")
                ? FlightState.byName(nbt.getString("FlightState"))
                : nbt.getBoolean("Launching") ? FlightState.ASCENDING : FlightState.IDLE;
        this.launchTicks = nbt.getInt("LaunchTicks");
        this.targetAddress = nbt.contains("TargetAddress") ? nbt.getInt("TargetAddress") : -1;
        this.originPadPos = nbt.contains("OriginPad") ? BlockPos.of(nbt.getLong("OriginPad")) : null;
        this.linkedPadPos = nbt.contains("DockedPad") ? BlockPos.of(nbt.getLong("DockedPad")) : null;
        this.lastFailure = nbt.getString("LastFailure");
        if (nbt.contains("OriginDimension")) {
            ResourceLocation id = ResourceLocation.tryParse(nbt.getString("OriginDimension"));
            this.originDimension = id == null ? null : ResourceKey.create(Registries.DIMENSION, id);
        } else {
            this.originDimension = null;
        }
        this.tank.readNbt(nbt, this.registryAccess());
        this.createInventory();
        ContainerVehicle.loadInventory(nbt, this.inventory, this.registryAccess());
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag nbt) {
        nbt.putString("FlightState", this.flightState.getSerializedName());
        nbt.putInt("LaunchTicks", this.launchTicks);
        nbt.putInt("TargetAddress", this.targetAddress);
        if (this.originPadPos != null) nbt.putLong("OriginPad", this.originPadPos.asLong());
        if (this.linkedPadPos != null) nbt.putLong("DockedPad", this.linkedPadPos.asLong());
        if (this.originDimension != null) {
            nbt.putString("OriginDimension", this.originDimension.location().toString());
        }
        if (!this.lastFailure.isEmpty()) nbt.putString("LastFailure", this.lastFailure);
        this.tank.writeNbt(nbt, this.registryAccess());
        ContainerVehicle.saveInventory(nbt, this.inventory, this.registryAccess());
    }

    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    @Override
    public boolean isPickable() {
        return !this.isRemoved();
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (!this.level().isClientSide) {
            if (player.isSecondaryUseActive()) {
                if (!this.ignite()) {
                    player.displayClientMessage(Component.translatable(this.lastFailure), true);
                }
                return InteractionResult.CONSUME;
            }
            if (player instanceof ServerPlayer serverPlayer) {
                MenuRegistry.openExtendedMenu(serverPlayer, this);
            }
            return InteractionResult.CONSUME;
        }
        return InteractionResult.SUCCESS;
    }

    public boolean ignite() {
        if (this.flightState != FlightState.IDLE) {
            return this.failIgnition("ui.galacticraft.cargo_rocket.already_flying");
        }
        if (!(this.level() instanceof ServerLevel level)
                || !(this.getLandingPad() instanceof LaunchPadBlockEntity pad)) {
            return this.failIgnition("ui.galacticraft.cargo_rocket.no_pad");
        }
        int destination = pad.getDestinationAddress();
        if (!CargoPadRegistry.isValidAddress(destination)) {
            return this.failIgnition("ui.galacticraft.cargo_rocket.no_route");
        }
        if (CargoPadRegistry.get(level.getServer()).resolve(level.getServer(), destination).isEmpty()) {
            return this.failIgnition("ui.galacticraft.cargo_rocket.destination_unavailable");
        }
        if (this.tank.getAmount() < FLIGHT_FUEL) {
            return this.failIgnition("ui.galacticraft.cargo_rocket.no_fuel");
        }

        this.targetAddress = destination;
        this.originDimension = level.dimension();
        this.originPadPos = pad.getBlockPos().immutable();
        this.launchTicks = 0;
        this.flightState = FlightState.ASCENDING;
        this.lastFailure = "";
        this.tank.extract(FLIGHT_FUEL);
        pad.setDockedEntity(null);
        this.linkedPad = null;
        this.linkedPadPos = null;
        this.ejectPassengers();
        level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.FIREWORK_ROCKET_LAUNCH,
                SoundSource.NEUTRAL, 1.0F, 1.0F);
        return true;
    }

    private boolean failIgnition(String translationKey) {
        this.lastFailure = translationKey;
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide || !(this.level() instanceof ServerLevel level)) return;

        FuelDock pad = this.getLandingPad();
        if (this.flightState == FlightState.IDLE && pad != null) {
            this.snapToPad(pad);
        }

        if (this.flightState == FlightState.RETURNING) {
            String reason = this.lastFailure.isEmpty()
                    ? "ui.galacticraft.cargo_rocket.destination_blocked"
                    : this.lastFailure;
            this.returnToOrigin(level, reason);
            return;
        }
        if (this.flightState != FlightState.ASCENDING) return;

        this.launchTicks++;
        double ascentSpeed = Math.min(MAX_ASCENT_SPEED, 0.05D + this.launchTicks * 0.01D);
        this.setDeltaMovement(new Vec3(0.0D, ascentSpeed, 0.0D));
        this.move(MoverType.SELF, this.getDeltaMovement());
        level.sendParticles(ParticleTypes.FLAME, this.getX(), this.getY(), this.getZ(),
                6, 0.2D, 0.0D, 0.2D, 0.01D);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY(), this.getZ(),
                4, 0.2D, 0.0D, 0.2D, 0.01D);

        int baseY = this.originPadPos != null ? this.originPadPos.getY() : Mth.floor(this.getY());
        if (this.getY() >= baseY + TRAVEL_HEIGHT || this.launchTicks >= MAX_LAUNCH_TICKS) {
            this.travelToDestination(level);
        }
    }

    private void travelToDestination(ServerLevel currentLevel) {
        CargoPadRegistry.ResolvedPad resolved = CargoPadRegistry.get(currentLevel.getServer())
                .resolve(currentLevel.getServer(), this.targetAddress).orElse(null);
        if (resolved == null || resolved.pad().hasDockedEntity()) {
            this.flightState = FlightState.RETURNING;
            this.lastFailure = resolved == null
                    ? "ui.galacticraft.cargo_rocket.destination_unavailable"
                    : "ui.galacticraft.cargo_rocket.destination_blocked";
            return;
        }
        if (!this.teleportAndDock(resolved.level(), resolved.pad())) {
            this.returnToOrigin(currentLevel, "ui.galacticraft.cargo_rocket.teleport_failed");
        }
    }

    private void returnToOrigin(ServerLevel currentLevel, String reason) {
        this.lastFailure = reason;
        if (this.originDimension == null || this.originPadPos == null) {
            this.failSafely(currentLevel, reason);
            return;
        }

        ServerLevel originLevel = currentLevel.getServer().getLevel(this.originDimension);
        if (originLevel == null) {
            this.failSafely(currentLevel, reason);
            return;
        }
        originLevel.getChunkAt(this.originPadPos);
        if (!(originLevel.getBlockEntity(this.originPadPos) instanceof LaunchPadBlockEntity pad)
                || pad.getPadType() != LaunchPadBlockEntity.Type.ROCKET
                || pad.hasDockedEntity()
                || !this.teleportAndDock(originLevel, pad)) {
            this.failSafely(currentLevel, reason);
        }
    }

    private boolean teleportAndDock(ServerLevel destination, LaunchPadBlockEntity pad) {
        BlockPos pos = pad.getBlockPos();
        boolean teleported = this.teleportTo(destination, pos.getX() + 0.5D, pos.getY() + PAD_Y_OFFSET,
                pos.getZ() + 0.5D, NO_RELATIVE_MOVEMENT, this.getYRot(), 0.0F);
        if (!teleported) return false;

        this.setDeltaMovement(Vec3.ZERO);
        this.flightState = FlightState.IDLE;
        this.launchTicks = 0;
        this.targetAddress = -1;
        this.originDimension = null;
        this.originPadPos = null;
        this.placeOnPad(pad);
        pad.setDockedEntity(this);
        return true;
    }

    private void failSafely(ServerLevel level, String reason) {
        int x = Mth.floor(this.getX());
        int z = Mth.floor(this.getZ());
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) + 1;
        this.teleportTo(level, x + 0.5D, y, z + 0.5D, NO_RELATIVE_MOVEMENT, this.getYRot(), 0.0F);
        this.setDeltaMovement(Vec3.ZERO);
        this.flightState = FlightState.FAILED;
        this.launchTicks = 0;
        this.lastFailure = reason;
        this.targetAddress = -1;
        this.linkedPad = null;
        this.linkedPadPos = null;
    }

    @Override
    public SlotAccess getSlot(int mappedIndex) {
        return SlotAccess.forContainer(this.inventory, mappedIndex);
    }

    @Override
    public Container getVehicleInventory() {
        return this.inventory;
    }

    @Override
    public void saveExtraData(net.minecraft.network.FriendlyByteBuf buf) {
        buf.writeVarInt(this.getId());
    }

    @Override
    public AbstractContainerMenu createMenu(int syncId, Inventory playerInventory, Player player) {
        return new VehicleInventoryMenu(syncId, playerInventory, this.inventory);
    }

    @Override
    public void containerChanged(Container sender) {
    }

    @Override
    public void setPad(FuelDock pad) {
        this.linkedPad = pad;
        this.linkedPadPos = pad.getDockPos().immutable();
    }

    public void placeOnPad(FuelDock pad) {
        this.setPad(pad);
        this.snapToPad(pad);
    }

    private void snapToPad(FuelDock pad) {
        BlockPos pos = pad.getDockPos();
        double x = pos.getX() + 0.5D;
        double y = pos.getY() + PAD_Y_OFFSET;
        double z = pos.getZ() + 0.5D;
        if (this.getX() != x || this.getY() != y || this.getZ() != z) {
            this.setPos(x, y, z);
        }
    }

    @Override
    public @Nullable FuelDock getLandingPad() {
        if (this.linkedPad == null && this.linkedPadPos != null
                && this.level().getBlockEntity(this.linkedPadPos) instanceof FuelDock pad) {
            this.linkedPad = pad;
        }
        return this.linkedPad;
    }

    @Override
    public void onPadDestroyed() {
        if (!this.level().isClientSide) {
            Containers.dropContents(this.level(), this.blockPosition(), this.inventory);
            this.inventory.clearContent();
            this.spawnAtLocation(this.getDropItem());
        }
        this.linkedPad = null;
        this.linkedPadPos = null;
        this.remove(RemovalReason.DISCARDED);
    }

    @Override
    public boolean isDockValid(FuelDock dock) {
        return !this.inFlight() && dock instanceof LaunchPadBlockEntity pad
                && pad.getPadType() == LaunchPadBlockEntity.Type.ROCKET;
    }

    @Override
    public boolean inFlight() {
        return this.flightState == FlightState.ASCENDING || this.flightState == FlightState.RETURNING;
    }

    @Override
    public Entity asEntity() {
        return this;
    }

    @Override
    public @Nullable Fluid getFuelTankFluid() {
        return this.tank.getResource();
    }

    @Override
    public long getFuelTankAmount() {
        return this.tank.getAmount();
    }

    @Override
    public long getFuelTankCapacity() {
        return this.tank.getCapacity();
    }

    @Override
    public StorageAccess<Fluid> getFuelTank() {
        return this.tank;
    }

    @Override
    public ItemStack getDropItem() {
        ItemStack stack = new ItemStack(GCItems.CARGO_ROCKET);
        if (!this.tank.isEmpty()) {
            stack.set(GCDataComponents.FLUID_DATA,
                    new FluidData(new MLFluidStack(this.tank.getResource(), this.tank.getComponents()), this.tank.getAmount()));
        }
        return stack;
    }

    @Override
    public ItemStack getPickResult() {
        return this.getDropItem();
    }

    @Override
    protected void destroy(DamageSource source) {
        Containers.dropContents(this.level(), this.blockPosition(), this.inventory);
        this.inventory.clearContent();
        super.destroy(source);
    }

    @Override
    public void remove(RemovalReason reason) {
        super.remove(reason);
        if (this.linkedPad != null && (reason == RemovalReason.KILLED || reason == RemovalReason.DISCARDED)) {
            this.linkedPad.setDockedEntity(null);
        }
    }

    public FlightState getFlightState() {
        return this.flightState;
    }

    public static @Nullable CargoRocketEntity findDockedRocket(Level level, BlockPos machinePos) {
        for (Direction direction : Direction.values()) {
            BlockPos partPos = machinePos.relative(direction);
            if (!(level.getBlockState(partPos).getBlock() instanceof AbstractLaunchPad)) continue;
            BlockPos center = partPos.offset(AbstractLaunchPad.partToCenterPos(
                    level.getBlockState(partPos).getValue(AbstractLaunchPad.PART)));
            if (level.getBlockEntity(center) instanceof LaunchPadBlockEntity pad
                    && pad.getDockedEntity() instanceof CargoRocketEntity rocket
                    && !rocket.inFlight()) {
                return rocket;
            }
        }
        return null;
    }

    public enum FlightState {
        IDLE("idle"),
        ASCENDING("ascending"),
        RETURNING("returning"),
        FAILED("failed");

        private final String serializedName;

        FlightState(String serializedName) {
            this.serializedName = serializedName;
        }

        public String getSerializedName() {
            return this.serializedName;
        }

        public static FlightState byName(String name) {
            for (FlightState state : values()) {
                if (state.serializedName.equals(name)) return state;
            }
            return IDLE;
        }
    }
}
