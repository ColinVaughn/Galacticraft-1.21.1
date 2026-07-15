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

import dev.galacticraft.api.entity.ControllableEntity;
import dev.galacticraft.api.component.GCDataComponents;
import dev.galacticraft.api.fluid.FluidData;
import dev.galacticraft.api.universe.celestialbody.CelestialBody;
import dev.galacticraft.machinelib.api.transfer.MLFluidStack;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.api.block.entity.FuelDock;
import dev.galacticraft.mod.api.entity.Dockable;
import dev.galacticraft.mod.content.block.special.launchpad.LaunchPadBlockEntity;
import dev.galacticraft.mod.content.entity.ScalableFuelLevel;
import dev.galacticraft.mod.content.item.GCItems;
import dev.galacticraft.mod.screen.BuggyMenu;
import dev.galacticraft.mod.util.FluidUtil;
import dev.galacticraft.mod.storage.SimpleFluidTank;
import dev.galacticraft.machinelib.api.storage.StorageAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.Mth;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import dev.architectury.registry.menu.ExtendedMenuProvider;
import dev.architectury.registry.menu.MenuRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.util.function.IntFunction;

public class Buggy extends GCVehicle implements ContainerListener, ContainerVehicle, ControllableEntity, Dockable,
        VariantHolder<Buggy.BuggyType>, ExtendedMenuProvider, ScalableFuelLevel {
    private static final EntityDataAccessor<Integer> DATA_TYPE_ID = SynchedEntityData.defineId(Buggy.class, EntityDataSerializers.INT);
    // Synced server -> client so the controlling client (which simulates movement) knows
    // whether there is fuel. The tank itself lives only on the server, so without this the
    // client's movement gate would always read an empty tank. Mirrors legacy PacketDynamic.
    private static final EntityDataAccessor<Integer> DATA_FUEL = SynchedEntityData.defineId(Buggy.class, EntityDataSerializers.INT);
    public static final int TANK_CAPACITY = 1;
    private final SimpleFluidTank tank = new SimpleFluidTank(FluidUtil.bucketsToDroplets(TANK_CAPACITY), () -> {
    });
    public double speed;
    public float wheelRotationZ;
    public float wheelRotationX;
    float maxSpeed = 0.5F;
    float accel = 0.2F;
    float turnFactor = 3.0F;
    private FuelDock landingPad;
    private int timeClimbing;
    private boolean shouldClimb;

    protected SimpleContainer inventory;

    public Buggy(EntityType<?> entityType, Level level) {
        super(entityType, level);
        createInventory();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_TYPE_ID, 0);
        builder.define(DATA_FUEL, 0);
    }

    /**
     * The buggy's fuel level, synced from the server. Used by the movement gate on the
     * controlling client (the tank itself only exists server-side).
     */
    public int getFuel() {
        return this.entityData.get(DATA_FUEL);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag nbt) {
        setVariant(BuggyType.byName(nbt.getString("Type")));
        tank.readNbt(nbt, registryAccess());
        createInventory();
        ContainerVehicle.loadInventory(nbt, this.inventory, this.registryAccess());
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag nbt) {
        nbt.putString("Type", getVariant().getSerializedName());
        tank.writeNbt(nbt, registryAccess());
        ContainerVehicle.saveInventory(nbt, this.inventory, this.registryAccess());
    }

    @Override
    public void inputTick(float leftImpulse, float forwardImpulse, boolean up, boolean down, boolean left, boolean right, boolean jumping, boolean shiftKeyDown, boolean invertControls) {
        if (up) { // Accelerate
            this.speed += this.accel / 20D;
            this.shouldClimb = true;
        }
        if (down) { // Deccelerate
            this.speed -= this.accel / 20D;
            this.shouldClimb = true;
        }
        if (left) { // Left
            setYRot(getYRot() - 0.5F * this.turnFactor);
            this.wheelRotationZ = Mth.clamp(this.wheelRotationZ + 0.5F, -30.0F, 30.0F);
        }
        if (right) { // Right
            setYRot(getYRot() + 0.5F * this.turnFactor);
            this.wheelRotationZ = Mth.clamp(this.wheelRotationZ - 0.5F, -30.0F, 30.0F);
        }
    }

    protected int getInventorySize() {
        return getVariant().getStorage();
    }

    protected void createInventory() {
        SimpleContainer simpleContainer = this.inventory;
        this.inventory = new SimpleContainer(this.getInventorySize());
        if (simpleContainer != null) {
            simpleContainer.removeListener(this);
            int size = Math.min(simpleContainer.getContainerSize(), this.inventory.getContainerSize());

            for (int j = 0; j < size; ++j) {
                ItemStack itemStack = simpleContainer.getItem(j);
                if (!itemStack.isEmpty()) {
                    this.inventory.setItem(j, itemStack.copy());
                }
            }
        }

        this.inventory.addListener(this);
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
            if (player.isSecondaryUseActive() && player instanceof ServerPlayer serverPlayer) {
                MenuRegistry.openExtendedMenu(serverPlayer, this);
                return InteractionResult.CONSUME;
            }
            return player.startRiding(this) ? InteractionResult.CONSUME : InteractionResult.PASS;
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            this.wheelRotationX += Mth.sqrt((float) this.getDeltaMovement().horizontalDistanceSqr()) * 150.0F * (this.speed < 0 ? 1 : -1);
            this.wheelRotationX %= 360;
            this.wheelRotationZ = Mth.clamp(this.wheelRotationZ * 0.9F, -30.0F, 30.0F);
        }

        if (!this.onGround()) {
            setDeltaMovement(getDeltaMovement().subtract(0, CelestialBody.getGravity(this) * 0.5D, 0));
        }

        if (this.wasTouchingWater && this.speed > 0.2D) {
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_BURN, SoundSource.NEUTRAL, 0.5F,
                    2.6F + (this.level().random.nextFloat() - this.level().random.nextFloat()) * 0.8F);
        }

        this.speed *= 0.98D;

        if (this.speed > this.maxSpeed) {
            this.speed = this.maxSpeed;
        }

        if (this.horizontalCollision && this.shouldClimb) {
            this.speed *= 0.9;
            setDeltaMovement(getDeltaMovement().x, Math.max(-0.15, 0.15D * ((-Math.pow((this.timeClimbing) - 1, 2)) / 250.0F) + 0.15F), getDeltaMovement().z);
            this.shouldClimb = false;
        }

        if ((getDeltaMovement().x == 0 || getDeltaMovement().z == 0) && !onGround()) {
            this.timeClimbing++;
        } else {
            this.timeClimbing = 0;
        }

        if (!this.level().isClientSide) {
            // Burn fuel while driving, then publish the tank level to clients. Legacy drained
            // 1 mB every floor(2/d)+1 ticks where d is the horizontal speed squared; the
            // server's `speed` field is kept in step by the control payload, so we reuse it.
            if (!this.tank.isResourceBlank()) {
                double d = this.speed * this.speed;
                if (d > 1.0E-6D && this.tickCount % (Mth.floor(2.0D / d) + 1) == 0) {
                    this.tank.extract(FluidUtil.bucketsToDroplets(1) / 1000L);
                }
            }
            this.entityData.set(DATA_FUEL, (int) this.tank.getAmount());
        }

        if (isControlledByLocalInstance()) {
            if (this.getFuel() > 0) {
                setDeltaMovement(-(this.speed * Math.cos((getYRot() - 90F) / Constant.RADIANS_TO_DEGREES)), getDeltaMovement().y, -(this.speed * Math.sin((getYRot() - 90F) / Constant.RADIANS_TO_DEGREES)));
            }

            move(MoverType.SELF, getDeltaMovement());
        }
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
    public LivingEntity getControllingPassenger() {
        return getFirstPassenger() instanceof LivingEntity livingEntity ? livingEntity : super.getControllingPassenger();
    }

    @Override
    public void containerChanged(Container sender) {

    }

    @Override
    public void setVariant(BuggyType type) {
        boolean changed = this.entityData.get(DATA_TYPE_ID) != type.getId();
        this.entityData.set(DATA_TYPE_ID, type.getId());
        if (changed && this.inventory != null) {
            createInventory();
        }
    }

    @Override
    public BuggyType getVariant() {
        return BuggyType.byId(this.entityData.get(DATA_TYPE_ID));
    }

    @Override
    public void setPad(FuelDock pad) {
        this.landingPad = pad;
    }

    @Override
    public FuelDock getLandingPad() {
        return this.landingPad;
    }

    @Override
    public boolean isDockValid(FuelDock dock) {
        // Legacy: the buggy only docks on its dedicated fuelling pad (TileEntityBuggyFueler).
        return dock instanceof LaunchPadBlockEntity pad && pad.getPadType() == LaunchPadBlockEntity.Type.FUEL;
    }

    @Override
    public boolean inFlight() {
        return false;
    }

    @Override
    public Entity asEntity() {
        return this;
    }

    @Override
    public Fluid getFuelTankFluid() {
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
    public float getScaledFuelLevel(float scale) {
        return this.tank.getCapacity() == 0 ? 0 : this.getFuel() * scale / this.tank.getCapacity();
    }

    @Override
    public ItemStack getDropItem() {
        ItemStack stack = new ItemStack(GCItems.BUGGY);
        stack.set(GCDataComponents.BUGGY_TYPE, this.getVariant().getId());
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
        if (this.landingPad != null && (reason == RemovalReason.KILLED || reason == RemovalReason.DISCARDED)) {
            this.landingPad.setDockedEntity(null);
        }
    }

    @Override
    public void onPadDestroyed() {
        if (!this.level().isClientSide) {
            Containers.dropContents(this.level(), this.blockPosition(), this.inventory);
            this.inventory.clearContent();
            this.spawnAtLocation(this.getDropItem());
        }
        this.remove(RemovalReason.DISCARDED);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("entity.galacticraft.buggy");
    }

    @Override
    public void saveExtraData(FriendlyByteBuf buf) {
        buf.writeVarInt(this.getId());
    }

    @Override
    public AbstractContainerMenu createMenu(int syncId, Inventory playerInventory, Player player) {
        return new BuggyMenu(syncId, playerInventory, this);
    }

    public enum BuggyType implements StringRepresentable {
        NORMAL(0, "no_storage"),
        STORAGE_18(1, "storage_18"),
        STORAGE_36(2, "storage_36");

        public static final StringRepresentable.EnumCodec<BuggyType> CODEC = StringRepresentable.fromEnum(BuggyType::values);
        private static final IntFunction<BuggyType> BY_ID = ByIdMap.continuous(BuggyType::getId, values(), ByIdMap.OutOfBoundsStrategy.ZERO);

        private final int id;
        private final String name;

        BuggyType(int id, String name) {
            this.id = id;
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }

        public int getStorage() {
            return this.id * 18;
        }

        public boolean hasStorage() {
            return getStorage() > 0;
        }

        public int getId() {
            return this.id;
        }

        public static BuggyType byName(String name) {
            return CODEC.byName(name, NORMAL);
        }

        public static BuggyType byId(int id) {
            return BY_ID.apply(id);
        }
    }
}
