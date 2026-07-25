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

package dev.galacticraft.mod.content.entity.vehicle;

import dev.architectury.platform.Platform;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import dev.galacticraft.api.component.GCDataComponents;
import dev.galacticraft.api.entity.ControllableEntity;
import dev.galacticraft.api.entity.IgnoreShift;
import dev.galacticraft.api.rocket.LaunchStage;
import dev.galacticraft.api.rocket.RocketData;
import dev.galacticraft.api.rocket.RocketPrefabs;
import dev.galacticraft.api.rocket.entity.Rocket;
import dev.galacticraft.api.rocket.part.*;
import dev.galacticraft.api.universe.celestialbody.CelestialBody;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.Galacticraft;
import dev.galacticraft.mod.api.block.entity.FuelDock;
import dev.galacticraft.mod.attachments.GCServerPlayer;
import dev.galacticraft.mod.content.GCBlocks;
import dev.galacticraft.mod.content.GCFluids;
import dev.galacticraft.mod.content.GCRocketParts;
import dev.galacticraft.mod.content.GCStats;
import dev.galacticraft.mod.content.advancements.GCTriggers;
import dev.galacticraft.mod.content.block.special.launchpad.AbstractLaunchPad;
import dev.galacticraft.mod.content.block.special.launchpad.LaunchPadBlockEntity;
import dev.galacticraft.mod.content.entity.data.GCEntityDataSerializers;
import dev.galacticraft.mod.content.item.GCItems;
import dev.galacticraft.mod.content.rocket.part.config.StorageUpgradeConfig;
import dev.galacticraft.mod.content.rocket.part.data.ExplosiveRocketData;
import dev.galacticraft.mod.content.rocket.part.data.StorageRocketData;
import dev.galacticraft.mod.content.rocket.part.type.StorageUpgradeType;
import dev.galacticraft.mod.events.RocketEvents;
import dev.galacticraft.mod.network.s2c.OpenCelestialScreenPayload;
import dev.galacticraft.mod.particle.EntityParticleOption;
import dev.galacticraft.mod.particle.GCParticleTypes;
import dev.galacticraft.mod.tag.GCFluidTags;
import dev.galacticraft.mod.util.FluidUtil;
import dev.galacticraft.mod.storage.SimpleFluidTank;
import dev.galacticraft.machinelib.api.storage.StorageAccess;
import dev.galacticraft.mod.util.Translations;
import dev.architectury.networking.NetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.RegistryOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.EitherHolder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

import static dev.galacticraft.mod.content.entity.damage.GCDamageTypes.CRASH_LANDING;

@SuppressWarnings("UnstableApiUsage")
public class RocketEntity extends AdvancedVehicle implements Rocket, IgnoreShift, ControllableEntity, ContainerVehicle {
    private static final EntityDataAccessor<LaunchStage> STAGE = SynchedEntityData.defineId(RocketEntity.class, GCEntityDataSerializers.LAUNCH_STAGE);
    private static final EntityDataAccessor<Integer> TIME_AS_STATE = SynchedEntityData.defineId(RocketEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> THRUST = SynchedEntityData.defineId(RocketEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<RocketData> ROCKET_DATA = SynchedEntityData.defineId(RocketEntity.class, GCEntityDataSerializers.ROCKET_DATA);
    private static final EntityDataAccessor<Long> FUEL = SynchedEntityData.defineId(RocketEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Boolean> CREATIVE = SynchedEntityData.defineId(RocketEntity.class, EntityDataSerializers.BOOLEAN);

    private static final int CRASH_ARM_DELAY_TICKS = 20;
    private static final int ROCKET_LAUNCH_PAD_BLOCKS = 9;

    private static final double CRASH_MIN_SPEED_SQR = 0.0 * 0.0; //rockets now explode when they hit anything at any speed
    private static final double CRASH_MIN_HORIZ_SQR = 0.0 * 0.0;
    private static final double CRASH_MIN_UP_SQR = 0.0 * 0.0;
    private static final double CRASH_GROUND_Y_SPEED = -0.0;

    private int crashArmTimer = 0;
    private boolean crashed = false;

    private final boolean debugMode = false && Platform.isDevelopmentEnvironment();

    private FuelDock linkedPad = null;
    /**
     * Where the pad was when the rocket was last saved. Kept separately because the dock itself
     * cannot always be resolved at load time — an entity is deserialized while its chunk is still
     * coming up, so the block entity lookup can come back empty — and without this the position
     * would be dropped from the next save and the rocket would be orphaned for good.
     */
    private @Nullable BlockPos linkedPadPos = null;
    // Read once, when the rocket is built: resizing a tank that already holds fuel would either
    // spill it or invent it, so rockets already in a save keep the capacity they were built with.
    private final SimpleFluidTank tank = new SimpleFluidTank(FluidUtil.bucketsToDroplets(Galacticraft.CONFIG.rocketFuelTankCapacity()), () -> {
        this.entityData.set(FUEL, getTank().getAmount());
    });

    /**
     * Cargo hold. Sized {@code storage slots + RESERVED_RETURN_STACKS}; the trailing reserved pair is
     * never shown in the GUI and stays empty until the stacks are handed to the player on departure.
     */
    private SimpleContainer inventory = new SimpleContainer(GCServerPlayer.RESERVED_RETURN_STACKS);

    private int timeBeforeLaunch;
    private float timeSinceLaunch;

    private float zRot;
    public float zRotO;
    private float initialYRot;

    private enum LaunchConfirmState {
        IDLE,
        WAITING_SECOND_PRESS
    }

    private boolean lastJumping = false;
    private LaunchConfirmState launchConfirmState = LaunchConfirmState.IDLE;

    public RocketEntity(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    public int getTimeAsState() {
        return this.entityData.get(TIME_AS_STATE);
    }

    public void setTimeAsState(int time) {
        this.entityData.set(TIME_AS_STATE, time);
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return this.getPassengers().isEmpty();
    }

    @Override
    public LivingEntity getControllingPassenger() {
        return null;
    }

    @Override
    public LaunchStage getLaunchStage() {
        return this.entityData.get(STAGE);
    }

    @Override
    public void setLaunchStage(LaunchStage launchStage) {
        LaunchStage oldStage = getLaunchStage();
        if (oldStage != launchStage) {
            this.entityData.set(STAGE, launchStage);
            setTimeAsState(0);
            RocketEvents.STAGE_CHANGED.invoker().onStageChanged(this, oldStage);

            if (launchStage == LaunchStage.IDLE) {
                resetLaunchConfirmation();
            }
        }
    }

    @Override
    public @NotNull RocketData getRocketData() {
        return this.entityData.get(ROCKET_DATA);
    }

    @Override
    public @NotNull BlockPos getLinkedPad() {
        return this.linkedPad != null ? this.linkedPad.getDockPos() : BlockPos.ZERO;
    }

    @Override
    public void remove(RemovalReason reason) {
        super.remove(reason);
        if (this.linkedPad != null && (reason == RemovalReason.KILLED || reason == RemovalReason.DISCARDED)) {
            this.linkedPad.setDockedEntity(null);
        }
    }

    private void resetLaunchConfirmation() {
        this.launchConfirmState = LaunchConfirmState.IDLE;
        this.lastJumping = false;
    }

    private void cancelPreLaunch() {
        resetLaunchConfirmation();

        if (this.getLaunchStage().ordinal() < LaunchStage.IGNITED.ordinal()) {
            this.setLaunchStage(LaunchStage.IDLE);
        }
    }

    @Override
    protected boolean canRide(Entity ridable) {
        return false;
    }

    public SimpleFluidTank getTank() {
        return this.tank;
    }

    public boolean isTankEmpty() {
        if (this.isCreative()) {
            return false;
        }
        return this.getTank().isEmpty();
    }

    @Override
    public void onJump() {
    }

    public void setFuel(long fuel) {
        this.tank.set(GCFluids.FUEL, fuel);
    }

    public void setCreative(boolean creative) {
        this.entityData.set(CREATIVE, creative);
        if (creative) {
            this.setFuel(this.getFuelTankCapacity());
        }
    }

    public boolean isCreative() {
        return this.entityData.get(CREATIVE);
    }

    public long getFuel() {
        return this.entityData.get(FUEL);
    }

    @Override
    public void setPad(FuelDock pad) {
        this.linkedPad = pad;
        this.linkedPadPos = pad != null ? pad.getDockPos() : null;
    }

    /**
     * Retries the dock lookup that {@link #readAdditionalSaveData} may not have been able to make.
     */
    private void resolveLinkedPad() {
        if (this.linkedPad != null || this.linkedPadPos == null) return;
        if (this.level().getBlockEntity(this.linkedPadPos) instanceof FuelDock pad) {
            this.linkedPad = pad;
        }
    }

    @Override
    public FuelDock getLandingPad() {
        return this.linkedPad;
    }

    @Override
    public void onPadDestroyed() {
        this.spawnAtLocation(this.getDropItem());
        this.remove(RemovalReason.DISCARDED);
    }

    @Override
    public boolean isDockValid(FuelDock dock) {
        // Must be a real answer, not a constant: LaunchPadBlockEntity#getDockedEntity relies on this
        // to re-attach a rocket that resolved its own link to nothing, which is what every reload
        // looks like when the pad's block entity is not reachable yet.
        return dock instanceof LaunchPadBlockEntity pad && pad.getPadType() == LaunchPadBlockEntity.Type.ROCKET;
    }

    @Override
    public boolean inFlight() {
        return false;
    }

    @Override
    public ItemStack getPickResult() {
        return this.getDropItem();
    }

    @Override
    public ItemStack getDropItem() {
        ItemStack rocket = new ItemStack(GCItems.ROCKET);
        rocket.applyComponents(this.getRocketData().asPatch());
        if (this.isCreative()) {
            rocket.set(GCDataComponents.CREATIVE, true);
        }
        return rocket;
    }

    @Override
    public void dropItems(DamageSource damageSource, boolean exploded) {
        if (!exploded) {
            this.spawnAtLocation(this.getDropItem());
        }
        // Cargo spills whether or not the rocket survived; it should never just vanish. Unlike
        // spawnAtLocation, dropContents has no client-side guard of its own.
        if (!this.level().isClientSide) {
            Containers.dropContents(this.level(), this, this.getVehicleInventory());
        }
        this.remove(RemovalReason.KILLED);
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

    public float getScaledFuelLevel(float scale) {
        if (this.getFuelTankCapacity() <= 0) return 0;
        return this.getFuel() * scale / this.getFuelTankCapacity();
    }

    @Override
    public StorageAccess<Fluid> getFuelTank() {
        return this.tank;
    }

    /**
     * Cargo slots the player can actually see and use, driven by how many chests were installed at
     * the workbench. Zero for a rocket built without the storage upgrade.
     */
    public int getStorageSlotCount() {
        Holder<RocketUpgrade<?, ?>> upgrade = this.upgrade();
        if (upgrade != null && upgrade.value().type() instanceof StorageUpgradeType storageType && upgrade.value().config() instanceof StorageUpgradeConfig config) {
            return storageType.getSlots(config, this.getStorageChestCount());
        }
        return 0;
    }

    private int getStorageChestCount() {
        // Rockets built before storage tiers existed carry no chest count; they were built from the
        // workbench's single chest slot, so treat them as one chest.
        return this.getRocketData().upgradeData()
                .filter(StorageRocketData.class::isInstance)
                .map(data -> ((StorageRocketData) data).chests())
                .orElse(1);
    }

    /**
     * The size of the stack list handed to the player on leaving the dimension: every cargo slot plus
     * the two reserved slots that carry the rocket item and launch pad back down.
     */
    public int getReturnCargoSlotCount() {
        return GCServerPlayer.RESERVED_RETURN_STACKS + this.getStorageSlotCount();
    }

    @Override
    public int getCargoSlotCount() {
        return this.getStorageSlotCount();
    }

    @Override
    public Container getVehicleInventory() {
        // The rocket's parts arrive by entity-data sync on the client and by NBT on the server, so the
        // container is sized on demand rather than in the constructor.
        int expected = this.getReturnCargoSlotCount();
        if (this.inventory.getContainerSize() != expected) {
            this.resizeInventory(expected);
        }
        return this.inventory;
    }

    /**
     * Snapshots the hold for the trip down and empties it, so the cargo lives in exactly one place
     * while the player is between dimensions.
     */
    private NonNullList<ItemStack> collectCargoForTransfer() {
        Container hold = this.getVehicleInventory();
        NonNullList<ItemStack> stacks = RocketCargoLogic.collectForTransfer(hold, this.getReturnCargoSlotCount());
        hold.clearContent();
        return stacks;
    }

    private void resizeInventory(int size) {
        SimpleContainer previous = this.inventory;

        // Never shrink past cargo that is actually there. The size is derived from the storage
        // upgrade, and anything that stops that upgrade resolving for even one call — data not
        // synced yet, a registry not reachable yet — would otherwise silently destroy the hold, with
        // no way to get it back once the upgrade resolves again.
        int occupied = 0;
        for (int slot = 0; slot < previous.getContainerSize(); ++slot) {
            if (!previous.getItem(slot).isEmpty()) {
                occupied = slot + 1;
            }
        }
        size = Math.max(size, occupied);
        if (size == previous.getContainerSize()) return;

        this.inventory = new SimpleContainer(size);
        int shared = Math.min(previous.getContainerSize(), size);
        for (int slot = 0; slot < shared; ++slot) {
            ItemStack stack = previous.getItem(slot);
            if (!stack.isEmpty()) {
                this.inventory.setItem(slot, stack.copy());
            }
        }
    }

    @Override
    public Entity asEntity() {
        return this;
    }

    @Override
    public void setDeltaMovement(double x, double y, double z) {
        this.setDeltaMovement(new Vec3(x, y, z));
    }

    @Override
    public void setDeltaMovement(Vec3 vec3d) {
        super.setDeltaMovement(vec3d);
        this.hasImpulse = true;
    }

    public float getZRot() {
        return this.zRot;
    }

    public void setZRot(float roll) {
        this.zRot = roll;
    }

    public float getViewZRot(float f) {
        if (f == 1.0F) return this.getZRot();
        return Mth.lerp(f, this.zRotO, this.getZRot());
    }

    public float getInitialYRot() {
        return this.initialYRot;
    }

    public void setInitialYRot(float yaw) {
        this.initialYRot = yaw;
    }

    @Override
    public void move(MoverType type, Vec3 vec3d) {
        if (onGround()) {
            vec3d = vec3d.multiply(1.0D, 0.0D, 1.0D);
        }
        super.move(type, vec3d);
        this.getPassengers().forEach(this::positionRider);
    }

    @Override
    protected void reapplyPosition() {
        super.reapplyPosition();
        this.getPassengers().forEach(this::positionRider);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);

        builder.define(STAGE, LaunchStage.IDLE);
        builder.define(THRUST, 0.0F);
        builder.define(TIME_AS_STATE, 0);
        builder.define(ROCKET_DATA, RocketPrefabs.TIER_1);
        builder.define(FUEL, 0L);
        builder.define(CREATIVE, false);
    }

    @Override
    public InteractionResult interactAt(Player player, Vec3 vec3d, InteractionHand hand) {
        return interact(player, hand);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (this.getPassengers().isEmpty()) {
            this.setInitialYRot(this.getYRot());
            player.absRotateTo(this.getYRot(), this.getXRot());
            player.startRiding(this);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.FAIL;
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity entity, EntityDimensions dimensions, float scale) {
        return new Vec3(0F, 1.8125F, 0F);
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        return new Vec3(getX(), getY(), getZ() + 1f);
    }

    @Override
    public void removePassenger(Entity entity) {
        super.removePassenger(entity);

        if (this.level().isClientSide()) {
            return;
        }

        if (this.getLaunchStage() == LaunchStage.IGNITED) {
            if (entity instanceof ServerPlayer player) {
                GCTriggers.LEAVE_ROCKET_DURING_COUNTDOWN.trigger(player);
            }
            return;
        }

        if (this.getLaunchStage().ordinal() < LaunchStage.IGNITED.ordinal()) {
            cancelPreLaunch();
        }
    }

    private void resetCrashSystem() {
        this.crashed = false;
        this.crashArmTimer = 0;
    }

    private boolean isCrashArmed() {
        return this.crashArmTimer >= CRASH_ARM_DELAY_TICKS;
    }

    private boolean hasExplosiveUpgradeAndPayload() {
        RocketData data = this.getRocketData();
        if (!data.hasUpgradeData(ExplosiveRocketData.class)) return false;
        if (!data.hasUpgrade(GCRocketParts.EXPLOSIVE_UPGRADE)) return false;

        ResourceKey<?> key = data.upgrade().get().key();

        ResourceLocation expectedRegistry = Constant.id("rocket_upgrade");
        ResourceLocation expectedId = Constant.id("explosive");

        return key.registry().equals(expectedRegistry) && key.location().equals(expectedId);
    }

    private void crashNow(@NotNull Vec3 preMoveDelta) {
        if (this.crashed) return;
        this.crashed = true;

        if (hasExplosiveUpgradeAndPayload()) {
            doExplosivePayloadCrashInstant();
        } else {
            doDefaultCrashExplosion();
        }

        this.remove(RemovalReason.KILLED);
    }

    private void doDefaultCrashExplosion() {
        boolean createFire = this.level().getDefaultBreathable();

        for (int i = 0; i < 4; i++) {
            this.level().explode(
                    this,
                    this.damageSources().source(CRASH_LANDING),
                    new ExplosionDamageCalculator(),
                    this.getX() + (this.level().random.nextDouble() - 0.5 * 4),
                    this.getY() + (this.level().random.nextDouble() * 3),
                    this.getZ() + (this.level().random.nextDouble() - 0.5 * 4),
                    10.0F,
                    createFire,
                    Level.ExplosionInteraction.TNT
            );
        }
    }

    /**
     * Explosive-upgrade crash behavior:
     * - DO NOT do default crash explosions
     * - Use the payload block's TNT behavior so modded TNT (extending TntBlock) works
     * - Detonate instantly (fuse 0)
     *
     * This supports:
     *  - Vanilla TNT (spawns PrimedTnt in TntBlock.wasExploded)
     *  - Modded TNT that extends TntBlock and overrides wasExploded to spawn its own primed entity
     */
    private void doExplosivePayloadCrashInstant() {
        RocketData data = this.getRocketData();
        Optional<ExplosiveRocketData> explosiveRocketData = data.getUpgradeData(ExplosiveRocketData.class);
        if (explosiveRocketData.isEmpty()) {
            doDefaultCrashExplosion();
            return;
        }

        Block payload = BuiltInRegistries.BLOCK.get(explosiveRocketData.get().explosiveBlock());
        if (!(payload instanceof TntBlock tntBlock)) {
            boolean createFire = this.level().getDefaultBreathable();
            this.level().explode(
                    this,
                    this.damageSources().source(CRASH_LANDING),
                    new ExplosionDamageCalculator(),
                    this.getX(),
                    this.getY(),
                    this.getZ(),
                    10.0F,
                    createFire,
                    Level.ExplosionInteraction.TNT
            );
            return;
        }

        Level level = this.level();
        if (level.isClientSide) return;

        BlockPos pos = this.blockPosition();

        final double scanRadius = 10.0;
        List<PrimedTnt> before = level.getEntitiesOfClass(
                PrimedTnt.class,
                this.getBoundingBox().inflate(scanRadius)
        );

        BlockState oldState = level.getBlockState(pos);
        BlockState payloadState = tntBlock.defaultBlockState();
        level.setBlock(pos, payloadState, Block.UPDATE_ALL);

        try {
            Explosion dummy = level.explode(
                    this,
                    pos.getX() + 0.5,
                    pos.getY() + 0.5,
                    pos.getZ() + 0.5,
                    0.0F,
                    false,
                    Level.ExplosionInteraction.NONE
            );

            tntBlock.wasExploded(level, pos, dummy);

            List<PrimedTnt> after = level.getEntitiesOfClass(
                    PrimedTnt.class,
                    this.getBoundingBox().inflate(scanRadius)
            );

            boolean spawned = false;
            for (PrimedTnt primed : after) {
                if (!before.contains(primed)) {
                    spawned = true;
                    primed.setFuse(0);
                    primed.setDeltaMovement(Vec3.ZERO);
                    primed.tick();
                }
            }

            if (!spawned) {
                LivingEntity igniter = (this.getFirstPassenger() instanceof LivingEntity le) ? le : null;
                PrimedTnt primed = new PrimedTnt(level, this.getX(), this.getY(), this.getZ(), igniter);
                primed.setFuse(0);
                level.addFreshEntity(primed);
                primed.tick();
            }
        } finally {
            level.setBlock(pos, oldState, Block.UPDATE_ALL);
        }
    }

    private void checkCrashAfterMove(@NotNull Vec3 preMoveDelta) {
        if (this.crashed) return;
        if (this.getLaunchStage().ordinal() < LaunchStage.LAUNCHED.ordinal()) return;

        this.crashArmTimer++;
        if (!isCrashArmed()) return;

        boolean collided = this.horizontalCollision || this.verticalCollision || this.isInWall();

        double speedSqr = preMoveDelta.lengthSqr();
        double horizSqr = preMoveDelta.horizontalDistanceSqr();
        double upSqr = (preMoveDelta.y > 0) ? (preMoveDelta.y * preMoveDelta.y) : 0.0;

        if (collided && speedSqr >= CRASH_MIN_SPEED_SQR) {
            if (horizSqr >= CRASH_MIN_HORIZ_SQR || upSqr >= CRASH_MIN_UP_SQR || preMoveDelta.y <= CRASH_GROUND_Y_SPEED) {
                crashNow(preMoveDelta);
                return;
            }
        }

        if (!this.crashed && this.onGround() && preMoveDelta.y <= CRASH_GROUND_Y_SPEED) {
            crashNow(preMoveDelta);
        }
    }

    @Override
    public void tick() {
        this.noPhysics = false;
        this.zRotO = this.getZRot();
        setTimeAsState(getTimeAsState() + 1);

        super.tick();

        int particleChance = (this.timeBeforeLaunch >= 100) ? Math.abs(this.timeBeforeLaunch / 100) : 1;
        if ((this.getLaunchStage() == LaunchStage.LAUNCHED || this.getLaunchStage() == LaunchStage.IGNITED)
                && this.random.nextInt(particleChance) == 0) {
            this.spawnParticles();
        }

        if (level().isClientSide()) return;

        // The pad's chunk may have finished loading after this rocket did.
        this.resolveLinkedPad();

        if (getLaunchStage() == LaunchStage.LAUNCHED) this.timeSinceLaunch++;
        else this.timeSinceLaunch = 0;

        if (isOnFire()) {
            for (int i = 0; i < 4; i++) {
                level().explode(this,
                        this.position().x + (level().random.nextDouble() - 0.5 * 4),
                        this.position().y + (level().random.nextDouble() * 3),
                        this.position().z + (level().random.nextDouble() - 0.5 * 4),
                        10.0F,
                        Level.ExplosionInteraction.TNT);
            }
            this.remove(RemovalReason.KILLED);
            return;
        }

        Entity passenger = getFirstPassenger();

        if (getLaunchStage() == LaunchStage.IGNITED) {
            timeBeforeLaunch--;

            if (isTankEmpty() && !debugMode) {
                this.setLaunchStage(LaunchStage.IDLE);
                if (passenger instanceof ServerPlayer player) {
                    player.sendSystemMessage(Component.translatable(Translations.Ui.ROCKET_NO_FUEL), false);
                }
                return;
            }

            if (!this.isCreative() && RocketFlightLogic.BURNS_FUEL_DURING_COUNTDOWN) {
                this.getTank().extract(fuelBurnPerTick());
            }

            if (getTimeAsState() >= getPreLaunchWait()) {
                this.setLaunchStage(LaunchStage.LAUNCHED);
                this.setThrust(Mth.SQRT_OF_TWO / 2.0F);

                resetCrashSystem();

                if (passenger instanceof ServerPlayer player) {
                    player.awardStat(GCStats.LAUNCH_ROCKET);
                }

                BlockPos dockPos = this.getLinkedPad();
                if (dockPos != BlockPos.ZERO) {
                    if (passenger instanceof ServerPlayer player) {
                        GCServerPlayer gcPlayer = GCServerPlayer.get(player);
                        gcPlayer.setRocketData(this.getRocketData());
                        gcPlayer.setLaunchpadStack(new ItemStack(GCBlocks.ROCKET_LAUNCH_PAD, ROCKET_LAUNCH_PAD_BLOCKS));
                    }

                    this.linkedPad.setDockedEntity(null);

                    if (level().getBlockState(dockPos).getBlock() == GCBlocks.ROCKET_LAUNCH_PAD
                            && level().getBlockState(dockPos).getValue(AbstractLaunchPad.PART) != AbstractLaunchPad.Part.NONE) {
                        level().destroyBlock(dockPos, false);
                    }
                }
            }

        } else if (getLaunchStage() == LaunchStage.LAUNCHED) {
            if (!this.isCreative() && !debugMode && (isTankEmpty() || !this.getTank().getResource().is(GCFluidTags.FUEL))) {
                this.setLaunchStage(LaunchStage.FAILED);
            } else {
                if (!this.isCreative()) {
                    this.getTank().extract(fuelBurnPerTick());
                }

                this.setThrust(this.getThrust() + 0.005F);
                this.tickInAir();
            }

            if (this.position().y() >= Constant.ESCAPE_HEIGHT) {
                if (this.getPassengers().isEmpty()) {
                    this.remove(RemovalReason.DISCARDED);
                    return;
                }

                for (Entity entity : getPassengers()) {
                    if (entity instanceof ServerPlayer serverPlayer) {
                        GCServerPlayer gcPlayer = GCServerPlayer.get(serverPlayer);
                        gcPlayer.setRocketStacks(this.collectCargoForTransfer());
                        gcPlayer.setFuel(this.tank.getAmount());

                        var rocket = new ItemStack(GCItems.ROCKET);
                        RocketData d = this.getRocketData();
                        rocket.applyComponents(d.asPatch());
                        if (this.isCreative()) {
                            rocket.set(GCDataComponents.CREATIVE, true);
                        }
                        gcPlayer.finishReturnInventory(rocket);

                        serverPlayer.galacticraft$openCelestialScreen(d);
                        NetworkManager.sendToPlayer(serverPlayer, new OpenCelestialScreenPayload(this.getRocketData(), this.level().galacticraft$getCelestialBody()));

                        remove(RemovalReason.UNLOADED_WITH_PLAYER);
                        break;
                    }
                }
                return;
            }

        } else if (!onGround()) {
            this.setThrust(this.getThrust() - 0.05F);
            this.tickInAir();
        }

        Vec3 preMoveDelta = this.getDeltaMovement();
        this.move(MoverType.SELF, preMoveDelta);

        checkCrashAfterMove(preMoveDelta);

        if (getLaunchStage() == LaunchStage.FAILED) {
            setRot(
                    (this.getYRot() + level().random.nextFloat() - 0.5F * 8.0F) % 360.0F,
                    (this.getXRot() + level().random.nextFloat() - 0.5F * 8.0F) % 360.0F
            );

            ServerLevel serverLevel = (ServerLevel) this.level();
            for (int i = 0; i < 4; i++) {
                serverLevel.sendParticles(
                        ParticleTypes.FLAME,
                        this.getX() + (level().random.nextDouble() - 0.5) * 0.12F,
                        this.getY() + 2,
                        this.getZ() + (level().random.nextDouble() - 0.5),
                        0,
                        level().random.nextDouble() - 0.5,
                        1,
                        level().random.nextDouble() - 0.5,
                        0.12000000596046448D
                );
            }
        }

        if (getLaunchStage().ordinal() >= LaunchStage.LAUNCHED.ordinal()) {
            if (!getPassengers().isEmpty() && getPassengers().get(0) instanceof ServerPlayer player) {
                GCTriggers.LAUNCH_ROCKET.trigger(player);
            }
        }
    }

    public void tickInAir() {
        double horizontal = -1.58227848D * 0.632D * this.getThrust();
        double sinPitch = Mth.sin(this.getXRot() * Mth.DEG_TO_RAD);
        double velX = horizontal * sinPitch * Mth.sin(this.getYRot() * Mth.DEG_TO_RAD);
        double velZ = horizontal * -sinPitch * Mth.cos(this.getYRot() * Mth.DEG_TO_RAD);

        // The coefficient of this.getDeltaMovement().y() controls the terminal velocity
        // You might have to solve a differential equation to obtain a specific value
        double velY = 0.955D * this.getDeltaMovement().y()
                + 0.08D * Mth.SQRT_OF_TWO * this.getThrust() * Mth.cos(this.getXRot() * Mth.DEG_TO_RAD);

        if (!this.onGround()) {
            Holder<CelestialBody<?, ?>> holder = this.level().galacticraft$getCelestialBody();
            velY -= (holder != null ? holder.value().gravity() : 1.0D) * 0.08D;
        }

        this.setDeltaMovement(new Vec3(velX, velY, velZ));
    }

    protected void spawnParticles() {
        if (!this.isAlive()) return;

        double sinPitch = Mth.sin(this.getXRot() * Mth.DEG_TO_RAD);
        double x1 = 2 * sinPitch * Mth.sin(this.getYRot() * Mth.DEG_TO_RAD);
        double z1 = -2 * sinPitch * Mth.cos(this.getYRot() * Mth.DEG_TO_RAD);
        double y1 = -Mth.cos(this.getXRot() * Mth.DEG_TO_RAD);

        if (this.getLaunchStage() == LaunchStage.FAILED && this.linkedPad != null) {
            double modifier = Mth.clamp(this.getY() - this.linkedPad.getDockPos().getY(), 120.0, 300.0);
            x1 *= modifier / 100.0D;
            y1 *= modifier / 100.0D;
            z1 *= modifier / 100.0D;
        }

        Vec3 delta = this.getDeltaMovement();
        double y = this.getY() + y1 - delta.y() + 1.2D;

        final double x2 = this.getX() + x1 - delta.x();
        final double z2 = this.getZ() + z1 - delta.z();

        LivingEntity riddenBy = (!this.getPassengers().isEmpty() && this.getPassengers().get(0) instanceof LivingEntity le) ? le : null;

        if (getLaunchStage() == LaunchStage.LAUNCHED) {
            EntityParticleOption particleData = new EntityParticleOption(GCParticleTypes.LAUNCH_FLAME_LAUNCHED, riddenBy == null ? null : riddenBy.getUUID());
            this.level().addParticle(particleData, x2 + 0.4 - this.random.nextDouble() / 10D, y, z2 + 0.4 - this.random.nextDouble() / 10D, x1, y1, z1);
            this.level().addParticle(particleData, x2 - 0.4 + this.random.nextDouble() / 10D, y, z2 + 0.4 - this.random.nextDouble() / 10D, x1, y1, z1);
            this.level().addParticle(particleData, x2 - 0.4 + this.random.nextDouble() / 10D, y, z2 - 0.4 + this.random.nextDouble() / 10D, x1, y1, z1);
            this.level().addParticle(particleData, x2 + 0.4 - this.random.nextDouble() / 10D, y, z2 - 0.4 + this.random.nextDouble() / 10D, x1, y1, z1);
            this.level().addParticle(particleData, x2, y, z2, x1, y1, z1);
            this.level().addParticle(particleData, x2 + 0.4, y, z2, x1, y1, z1);
            this.level().addParticle(particleData, x2, y, z2 + 0.4D, x1, y1, z1);
            this.level().addParticle(particleData, x2, y, z2 - 0.4D, x1, y1, z1);
        } else if (this.tickCount % 2 == 0) {
            y += 0.6D;
            EntityParticleOption particleData = new EntityParticleOption(GCParticleTypes.LAUNCH_FLAME_LAUNCHED, riddenBy == null ? null : riddenBy.getUUID());
            this.level().addParticle(particleData, x2 + 0.4 - this.random.nextDouble() / 10D, y, z2 + 0.4 - this.random.nextDouble() / 10D, this.random.nextDouble() / 2.0 - 0.25, 0.0, this.random.nextDouble() / 2.0 - 0.25);
            this.level().addParticle(particleData, x2 - 0.4 + this.random.nextDouble() / 10D, y, z2 + 0.4 - this.random.nextDouble() / 10D, this.random.nextDouble() / 2.0 - 0.25, 0.0, this.random.nextDouble() / 2.0 - 0.25);
            this.level().addParticle(particleData, x2 - 0.4 + this.random.nextDouble() / 10D, y, z2 - 0.4 + this.random.nextDouble() / 10D, this.random.nextDouble() / 2.0 - 0.25, 0.0, this.random.nextDouble() / 2.0 - 0.25);
            this.level().addParticle(particleData, x2 + 0.4 - this.random.nextDouble() / 10D, y, z2 - 0.4 + this.random.nextDouble() / 10D, this.random.nextDouble() / 2.0 - 0.25, 0.0, this.random.nextDouble() / 2.0 - 0.25);
        }
    }

    public float getThrust() {
        return this.getEntityData().get(THRUST);
    }

    public void setThrust(float thrust) {
        this.getEntityData().set(THRUST, Mth.clamp(thrust, 0.0F, 1.0F));
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        // Registry-aware ops are required, not optional: the part codecs are RegistryFileCodecs, and
        // without a registry they cannot turn a saved id such as "galacticraft:tier_1" back into a
        // holder. That failure sinks the whole record, and the fallback below would quietly hand
        // back a bare tier 1 rocket — stripping the storage upgrade and every part with it.
        RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, this.registryAccess());
        this.setData(RocketData.CODEC.decode(ops, tag.getCompound("data")).mapOrElse(Pair::getFirst, error -> {
            Constant.LOGGER.error("Failed to load rocket data for {}, falling back to a tier 1 rocket: {}",
                    this.getUUID(), error.message());
            return RocketPrefabs.TIER_1;
        }));

        if (tag.contains("Stage")) this.setLaunchStage(LaunchStage.valueOf(tag.getString("Stage")));
        if (tag.contains("Thrust")) this.setThrust(tag.getFloat("Thrust"));
        if (tag.contains("ZRot")) this.setZRot(tag.getFloat("ZRot"));
        if (tag.contains("InitialYRot")) this.setInitialYRot(tag.getFloat("InitialYRot"));
        if (tag.contains("Fuel")) this.setFuel(tag.getLong("Fuel"));
        if (tag.contains("Creative")) this.setCreative(tag.getBoolean("Creative"));

        if (tag.contains("CrashArmTimer")) this.crashArmTimer = tag.getInt("CrashArmTimer");
        if (tag.contains("Crashed")) this.crashed = tag.getBoolean("Crashed");

        if (tag.contains("Linked")) {
            this.linkedPadPos = BlockPos.of(tag.getLong("Linked"));
            this.resolveLinkedPad();
        }

        // Sized off the rocket data decoded above, so this has to come after setData.
        ContainerVehicle.loadInventory(tag, this.getVehicleInventory(), this.registryAccess());
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, this.registryAccess());
        DataResult<Tag> result = RocketData.CODEC.encodeStart(ops, getRocketData());
        result.error().ifPresent(error -> Constant.LOGGER.error(
                "Failed to save rocket data for {}: {}", this.getUUID(), error.message()));
        tag.put("data", result.getPartialOrThrow());

        tag.putString("Stage", getLaunchStage().name());
        tag.putFloat("Thrust", this.getThrust());
        tag.putFloat("ZRot", this.getZRot());
        tag.putFloat("InitialYRot", this.getInitialYRot());
        tag.putLong("Fuel", this.getFuel());
        tag.putBoolean("Creative", this.isCreative());

        tag.putInt("CrashArmTimer", this.crashArmTimer);
        tag.putBoolean("Crashed", this.crashed);

        BlockPos padPos = this.linkedPad != null ? this.linkedPad.getDockPos() : this.linkedPadPos;
        if (padPos != null) tag.putLong("Linked", padPos.asLong());

        ContainerVehicle.saveInventory(tag, this.getVehicleInventory(), this.registryAccess());
    }

    public int getTimeBeforeLaunch() {
        return timeBeforeLaunch;
    }

    public int getPreLaunchWait() {
        return RocketFlightLogic.PRE_LAUNCH_WAIT_TICKS;
    }

    /** Droplets of fuel the engines burn on a single tick of powered flight. */
    private static long fuelBurnPerTick() {
        int burnTicksPerBucket = Galacticraft.CONFIG.rocketBurnTicksPerBucket();
        if (burnTicksPerBucket <= 0) return FluidUtil.bucketsToDroplets(1);
        return FluidUtil.bucketsToDroplets(1) / burnTicksPerBucket;
    }

    @Override public @Nullable Holder<RocketCone<?, ?>> cone() { return maybeGet(getRocketData().cone()); }
    @Override public @Nullable Holder<RocketBody<?, ?>> body() { return maybeGet(getRocketData().body()); }
    @Override public @Nullable Holder<RocketFin<?, ?>> fin() { return maybeGet(getRocketData().fin()); }
    @Override public @Nullable Holder<RocketBooster<?, ?>> booster() { return maybeGet(getRocketData().booster()); }
    @Override public @Nullable Holder<RocketEngine<?, ?>> engine() { return maybeGet(getRocketData().engine()); }
    @Override public @Nullable Holder<RocketUpgrade<?, ?>> upgrade() { return maybeGet(getRocketData().upgrade()); }

    private <T> @Nullable Holder<T> maybeGet(Optional<EitherHolder<T>> holder) {
        return holder.flatMap(tEitherHolder -> tEitherHolder.unwrap(this.registryAccess())).orElse(null);
    }

    public void setData(RocketData data) {
        this.entityData.set(ROCKET_DATA, data);
    }

    @Override
    public void inputTick(float leftImpulse, float forwardImpulse, boolean up, boolean down, boolean left, boolean right,
                          boolean jumping, boolean shiftKeyDown, boolean invertControls) {
        float turnFactor = 2.0F;
        float angle = 180.0F;

        boolean risingEdge = jumping && !this.lastJumping;
        this.lastJumping = jumping;

        LaunchStage stage = this.getLaunchStage();

        // Launch confirmation is server-authoritative and only happens on press-down.
        if (!this.level().isClientSide() && stage.ordinal() < LaunchStage.IGNITED.ordinal()) {
            if (isTankEmpty() && !debugMode) {
                cancelPreLaunch();
                if (risingEdge && this.getFirstPassenger() instanceof ServerPlayer player) {
                    player.sendSystemMessage(Component.translatable(Translations.Ui.ROCKET_NO_FUEL), true);
                }
            } else if (!this.getPassengers().isEmpty() && this.getFirstPassenger() instanceof ServerPlayer player) {
                if (risingEdge) {
                    switch (this.launchConfirmState) {
                        case IDLE -> {
                            this.timeBeforeLaunch = getPreLaunchWait();
                            this.setLaunchStage(LaunchStage.WARNING);
                            this.launchConfirmState = LaunchConfirmState.WAITING_SECOND_PRESS;

                            player.sendSystemMessage(
                                    Component.translatable(Translations.Chat.ROCKET_WARNING),
                                    true
                            );
                        }

                        case WAITING_SECOND_PRESS -> {
                            this.timeBeforeLaunch = getPreLaunchWait();
                            this.setLaunchStage(LaunchStage.IGNITED);
                            this.launchConfirmState = LaunchConfirmState.IDLE;
                        }
                    }
                }
            }
        }

        // Re-read stage in case it changed above.
        stage = this.getLaunchStage();

        if (stage.ordinal() >= LaunchStage.LAUNCHED.ordinal()) {
            if (invertControls ? down : up) {
                this.setXRot(Mth.clamp(this.getXRot() - 0.5F * turnFactor, -angle, angle));
            } else if (invertControls ? up : down) {
                this.setXRot(Mth.clamp(this.getXRot() + 0.5F * turnFactor, -angle, angle));
            }

            if (invertControls ? left : right) {
                this.setYRot(this.getYRot() - turnFactor);
            } else if (invertControls ? right : left) {
                this.setYRot(this.getYRot() + turnFactor);
            }

            if (invertControls ? shiftKeyDown && !left : jumping && !right) {
                this.setZRot(this.getZRot() - turnFactor);
            } else if (invertControls ? jumping && !right : shiftKeyDown && !left) {
                this.setZRot(this.getZRot() + turnFactor);
            }
        }
    }

    @Override
    public boolean shouldIgnoreShiftExit() {
        return getLaunchStage().ordinal() >= LaunchStage.LAUNCHED.ordinal();
    }

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
    }
}
