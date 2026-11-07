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
import dev.galacticraft.mod.content.rocket.RocketPrefabs;
import dev.galacticraft.api.rocket.entity.Rocket;
import dev.galacticraft.api.event.RocketEvents;
import dev.galacticraft.api.event.CelestialEvents;
import dev.galacticraft.api.registry.AddonRegistries;
import dev.galacticraft.mod.world.dimension.GCDimensions;
import dev.galacticraft.mod.world.dimension.SpaceTravel;
import dev.galacticraft.mod.world.dimension.SpaceflightHandoff;
import dev.galacticraft.mod.network.s2c.SpaceflightHandoffPayload;
import dev.galacticraft.api.rocket.RocketGeometry;
import dev.galacticraft.api.rocket.part.*;
import dev.galacticraft.api.universe.celestialbody.CelestialBody;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.Galacticraft;
import dev.galacticraft.mod.api.block.entity.FuelDock;
import dev.galacticraft.mod.attachments.GCServerPlayer;
import dev.galacticraft.mod.content.GCBlocks;
import dev.galacticraft.mod.content.GCCelestialBodies;
import dev.galacticraft.mod.content.teleporters.JupiterCelestialTeleporterType;
import dev.galacticraft.mod.content.teleporters.DestinationArrival;
import dev.galacticraft.mod.network.s2c.SpaceflightArrivalPayload;
import dev.galacticraft.mod.world.dimension.jupiter.JupiterLanding;
import dev.galacticraft.mod.world.gen.custom.AsteroidChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import dev.galacticraft.mod.content.GCFluids;
import dev.galacticraft.mod.content.GCRocketParts;
import dev.galacticraft.mod.content.GCStats;
import dev.galacticraft.mod.content.advancements.GCTriggers;
import dev.galacticraft.mod.content.block.special.launchpad.AbstractLaunchPad;
import dev.galacticraft.mod.content.block.special.launchpad.CargoPadRegistry;
import dev.galacticraft.mod.content.block.special.launchpad.LaunchPadBlockEntity;
import dev.galacticraft.mod.content.entity.data.GCEntityDataSerializers;
import dev.galacticraft.mod.content.item.GCItems;
import dev.galacticraft.mod.content.rocket.part.config.StorageUpgradeConfig;
import dev.galacticraft.mod.content.rocket.part.data.ExplosiveRocketData;
import dev.galacticraft.mod.content.rocket.part.data.StorageRocketData;
import dev.galacticraft.mod.content.rocket.part.type.StorageUpgradeType;
import dev.galacticraft.mod.network.s2c.OpenCelestialScreenPayload;
import dev.galacticraft.mod.particle.EntityParticleOption;
import dev.galacticraft.mod.particle.GCParticleTypes;
import dev.galacticraft.mod.tag.GCFluidTags;
import dev.galacticraft.mod.util.FluidUtil;
import dev.galacticraft.mod.storage.SimpleFluidTank;
import dev.galacticraft.machinelib.api.storage.StorageAccess;
import dev.galacticraft.mod.util.Translations;
import dev.galacticraft.mod.util.Tier1RocketAnimationState;
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
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
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
    private static final EntityDataAccessor<Long> FUEL_CAPACITY = SynchedEntityData.defineId(RocketEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Boolean> CREATIVE = SynchedEntityData.defineId(RocketEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> MANUAL_LANDING = SynchedEntityData.defineId(RocketEntity.class, EntityDataSerializers.BOOLEAN);
    /** Spaceflight cruise control: boost stays on without holding the key. Synced for the HUD. */
    private static final EntityDataAccessor<Boolean> BOOST_LOCKED = SynchedEntityData.defineId(RocketEntity.class, EntityDataSerializers.BOOLEAN);
    /** Two boost presses this close together (vanilla's double-tap window) toggle the boost lock. */
    private static final int BOOST_LOCK_TAP_TICKS = 7;

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
     * Saved pad position retained when the block entity is unavailable during chunk loading.
     * Dropping it would lose the dock link on the next save.
     */
    private @Nullable BlockPos linkedPadPos = null;
    private boolean gc4FuelMode = Galacticraft.CONFIG.gc4RocketFuelMode();
    private SimpleFluidTank tank = new SimpleFluidTank(initialFuelTankCapacity(), () -> {
        this.entityData.set(FUEL, getTank().getAmount());
    });

    /**
     * Cargo hold. Sized {@code storage slots + RESERVED_RETURN_STACKS}; the trailing reserved pair is
     * never shown in the GUI and stays empty until the stacks are handed to the player on departure.
     */
    private SimpleContainer inventory = new SimpleContainer(GCServerPlayer.RESERVED_RETURN_STACKS);

    private int timeBeforeLaunch;
    private int controllerDestination = -1;
    private float timeSinceLaunch;
    private @Nullable ResourceLocation spaceOrigin;
    private double spaceSurfaceX, spaceSurfaceZ;
    private int spaceInputTick = -100;
    private boolean spaceBoost, spaceBrake;
    private int lastBoostTap = -100;
    private int landingTicks;
    private int landingInputTick;
    private float landingForward, landingStrafe;
    private boolean landingHover, landingCutThrust;
    private final Tier1RocketAnimationState tier1AnimationState = new Tier1RocketAnimationState();

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
        this.entityData.set(FUEL_CAPACITY, this.tank.getCapacity());
    }

    public Tier1RocketAnimationState getTier1AnimationState() { return tier1AnimationState; }

    public boolean isLanding() { return getLaunchStage() == LaunchStage.LAUNCHED && this.entityData.get(MANUAL_LANDING); }

    private @Nullable SpaceflightHandoff spaceflightHandoff;
    private @Nullable FlightTransfer preparedFlight;

    private record FlightTransfer(ServerLevel destination, Vec3 position, float pitch) { }

    public boolean isPreparingSpaceflight() { return preparedFlight != null; }

    public @Nullable UUID pendingSpaceflightHandoff() {
        return spaceflightHandoff == null ? null : spaceflightHandoff.token();
    }

    public void acknowledgeSpaceflight(ServerPlayer player, UUID token) {
        acknowledgeSpaceflight(player, token, preparedFlight == null);
    }

    public boolean acknowledgeSpaceflight(ServerPlayer player, UUID token, boolean terrainReady) {
        return spaceflightHandoff != null && player.getVehicle() == this && terrainReady == (preparedFlight == null)
                && spaceflightHandoff.acknowledge(player.getUUID(), token);
    }

    public boolean isInSpaceflight() { return level().dimension().equals(GCDimensions.SOLAR_SYSTEM); }

    @Override
    protected int interpolationSteps(int requested) {
        return isInSpaceflight() ? Math.max(1, requested) : super.interpolationSteps(requested);
    }

    public void toggleLanding(ServerPlayer pilot) {
        if (isInSpaceflight()) return;
        if (this.level().isClientSide() || this.getFirstPassenger() != pilot || getLaunchStage() != LaunchStage.LAUNCHED) return;
        if (isTankEmpty()) {
            pilot.sendSystemMessage(Component.translatable(Translations.Ui.ROCKET_NO_FUEL), true);
            return;
        }
        this.entityData.set(MANUAL_LANDING, !isLanding());
        this.landingTicks = 0;
        this.landingForward = this.landingStrafe = 0;
        this.landingHover = this.landingCutThrust = false;
    }

    public int getTimeAsState() {
        return this.entityData.get(TIME_AS_STATE);
    }

    public void setTimeAsState(int time) {
        this.entityData.set(TIME_AS_STATE, time);
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        if (this.getPassengers().isEmpty()) return true;
        // Extra seats carry players; physical spaceflight keeps the crew together.
        return passenger instanceof Player && this.getPassengers().size() < this.maxPassengers();
    }

    /** Seats on this rocket, the pilot's included, as set by its body. */
    public int maxPassengers() {
        Holder<RocketBody<?, ?>> body = this.body();
        return body != null ? Math.max(1, body.value().getMaxPassengers()) : 1;
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
            if (launchStage != LaunchStage.LAUNCHED) this.entityData.set(MANUAL_LANDING, false);
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
        return !this.inFlight() && dock instanceof LaunchPadBlockEntity pad && pad.getPadType() == LaunchPadBlockEntity.Type.ROCKET;
    }

    @Override
    public boolean inFlight() {
        return this.getLaunchStage().ordinal() >= LaunchStage.LAUNCHED.ordinal();
    }

    public boolean igniteFromController() {
        if (this.level().isClientSide || this.getLaunchStage().ordinal() >= LaunchStage.IGNITED.ordinal()
                || this.getPassengers().isEmpty() || this.isTankEmpty()) return false;
        int destination = this.controllerRoute();
        if (destination < 0) return false;
        return this.ignite(destination);
    }

    /** @return whether the countdown started; a {@link RocketEvents#PRE_LAUNCH} listener may refuse it */
    private boolean ignite(int destination) {
        if (RocketEvents.PRE_LAUNCH.invoker().preLaunch(this, this.getFirstPassenger()).isFalse()) {
            this.cancelPreLaunch();
            return false;
        }
        this.controllerDestination = destination;
        this.timeBeforeLaunch = this.getPreLaunchWait();
        this.setLaunchStage(LaunchStage.IGNITED);
        this.launchConfirmState = LaunchConfirmState.IDLE;
        return true;
    }

    private void igniteFromPad() {
        if (!this.isRemoved() && !this.getPassengers().isEmpty() && (!this.isTankEmpty() || debugMode)
                && this.launchConfirmState == LaunchConfirmState.WAITING_SECOND_PRESS
                && this.getLaunchStage().ordinal() < LaunchStage.IGNITED.ordinal()) {
            this.ignite(this.controllerRoute());
        }
    }

    private int controllerRoute() {
        if (!(this.level() instanceof ServerLevel level) || !(this.getLandingPad() instanceof LaunchPadBlockEntity pad)
                || pad.getController() == null) return -1;
        int address = pad.getDestinationAddress();
        CargoPadRegistry.ResolvedPad destination = CargoPadRegistry.get(level.getServer()).resolve(level.getServer(), address).orElse(null);
        return destination != null && destination.pad() != pad && !destination.pad().hasDockedEntity()
                && this.canTravelTo(destination.level()) ? address : -1;
    }

    private boolean canTravelTo(ServerLevel destination) {
        if (destination == this.level()) return true;
        Holder<CelestialBody<?, ?>> from = this.level().galacticraft$getCelestialBody();
        Holder<CelestialBody<?, ?>> to = destination.galacticraft$getCelestialBody();
        return to != null && this.getRocketData().canTravel(this.registryAccess(), from == null ? null : from.value(), to.value());
    }

    private boolean landAtController() {
        if (this.controllerDestination < 0 || !(this.level() instanceof ServerLevel level)) return false;
        CargoPadRegistry.ResolvedPad destination = CargoPadRegistry.get(level.getServer())
                .resolve(level.getServer(), this.controllerDestination).orElse(null);
        if (destination == null || destination.pad().hasDockedEntity() || !this.canTravelTo(destination.level())) return false;
        BlockPos pos = destination.pad().getBlockPos();
        Vec3 landing = new Vec3(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
        if (!destination.level().noCollision(this, this.getBoundingBox().move(landing.subtract(this.position())))) return false;
        for (Entity passenger : this.getPassengers()) {
            if (passenger instanceof ServerPlayer player) player.closeContainer();
        }
        // Dimension changes replace the rocket instance and carry its passengers through vanilla's transition.
        Entity moved = this.changeDimension(new DimensionTransition(destination.level(), landing, Vec3.ZERO,
                this.getYRot(), 0.0F, DimensionTransition.DO_NOTHING));
        if (!(moved instanceof RocketEntity arrived)) return false;
        arrived.controllerDestination = -1;
        arrived.setLaunchStage(LaunchStage.IDLE);
        arrived.setThrust(0.0F);
        arrived.setXRot(0.0F);
        arrived.setZRot(0.0F);
        arrived.resetCrashSystem();
        arrived.setPad(destination.pad());
        arrived.setOnGround(true);
        destination.pad().setDockedEntity(arrived);
        RocketEvents.LANDED.invoker().landed(arrived);
        for (Entity passenger : arrived.getPassengers()) {
            if (passenger instanceof ServerPlayer player) {
                GCServerPlayer data = GCServerPlayer.get(player);
                player.getInventory().placeItemBackInInventory(data.getLaunchpadStack());
                data.setLaunchpadStack(ItemStack.EMPTY);
            }
        }
        return true;
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
        return this.entityData.get(FUEL_CAPACITY);
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
        // upgrade, and anything that stops that upgrade resolving for even one call - data not
        // synced yet, a registry not reachable yet - would otherwise silently destroy the hold, with
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
        if (onGround() && vec3d.y < 0) {
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
        builder.define(FUEL_CAPACITY, FluidUtil.bucketsToDroplets(RocketFlightLogic.DEFAULT_FUEL_TANK_CAPACITY_BUCKETS));
        builder.define(CREATIVE, false);
        builder.define(MANUAL_LANDING, false);
        builder.define(BOOST_LOCKED, false);
    }

    public boolean isBoostLocked() {
        return this.entityData.get(BOOST_LOCKED);
    }

    /** Flight handling comes from the engine type, including addon engines. */
    private int engineTier() {
        var engine = engine();
        return engine == null ? 1 : Math.max(1, engine.value().getFlightTier());
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
        // Crew sit in a column above the pilot, kept inside the hull.
        int seat = Math.max(0, this.getPassengers().indexOf(entity));
        float y = Math.min(1.8125F + seat * 0.9F, Math.max(1.8125F, dimensions.height() - 1.8F));
        return new Vec3(0F, y, 0F);
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
     * Detonates through the payload block's TNT behavior so modded primed entities remain supported.
     * Uses a zero fuse and bypasses the default rocket crash explosion.
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

        if (isLanding() && landingTicks >= RocketFlightLogic.LANDING_DEPLOY_TICKS && this.onGround()
                && this.verticalCollision && !this.horizontalCollision && !this.isInWall()
                && RocketFlightLogic.safeTouchdown(preMoveDelta.y, preMoveDelta.horizontalDistanceSqr(), getXRot(), getZRot())) {
            finishManualLanding();
            return;
        }

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
        this.tickParts();

        int particleChance = (this.timeBeforeLaunch >= 100) ? Math.abs(this.timeBeforeLaunch / 100) : 1;
        if ((this.getLaunchStage() == LaunchStage.LAUNCHED || this.getLaunchStage() == LaunchStage.IGNITED)
                && this.random.nextInt(particleChance) == 0) {
            this.spawnParticles();
        }

        if (level().isClientSide()) {
            if (isLanding()) setZRot(Mth.approachDegrees(getZRot(), 0, 3));
            this.tier1AnimationState.tick(getLaunchStage(), !getPassengers().isEmpty(), isLanding());
            return;
        }

        if (tickSpaceflightHandoff()) return;

        // The pad's chunk may have finished loading after this rocket did.
        this.resolveLinkedPad();

        if (getLaunchStage() == LaunchStage.LAUNCHED) this.timeSinceLaunch++;
        else this.timeSinceLaunch = 0;

        if (isOnFire()) {
            explodeBurningRocket();
            return;
        }

        Entity passenger = getFirstPassenger();

        if (isInSpaceflight() && getLaunchStage() == LaunchStage.LAUNCHED) {
            tickSpaceflight();
            return;
        }

        if (getLaunchStage() == LaunchStage.IGNITED) {
            if (tickIgnition(passenger)) return;
        } else if (getLaunchStage() == LaunchStage.LAUNCHED) {
            if (tickLaunched()) return;
        } else if (!onGround()) {
            this.setThrust(this.getThrust() - 0.05F);
            this.tickInAir();
        }

        Vec3 preMoveDelta = this.getDeltaMovement();
        this.move(MoverType.SELF, preMoveDelta);

        checkCrashAfterMove(preMoveDelta);

        if (getLaunchStage() == LaunchStage.FAILED) {
            tickFailedFlight();
        }

        if (getLaunchStage().ordinal() >= LaunchStage.LAUNCHED.ordinal() && getFirstPassenger() instanceof ServerPlayer player) {
            GCTriggers.LAUNCH_ROCKET.trigger(player);
        }
    }

    /** Runs each fitted part's {@link RocketPart#tick} hook, on both sides. */
    private void tickParts() {
        tickPart(this.cone());
        tickPart(this.body());
        tickPart(this.fin());
        tickPart(this.booster());
        tickPart(this.engine());
        tickPart(this.upgrade());
    }

    private void tickPart(@Nullable Holder<? extends RocketPart<?, ?>> part) {
        if (part != null && part.isBound()) part.value().tick(this);
    }

    private void explodeBurningRocket() {
        for (int i = 0; i < 4; i++) {
            level().explode(this,
                    this.position().x + (level().random.nextDouble() - 0.5 * 4),
                    this.position().y + (level().random.nextDouble() * 3),
                    this.position().z + (level().random.nextDouble() - 0.5 * 4),
                    10.0F,
                    Level.ExplosionInteraction.TNT);
        }
        this.remove(RemovalReason.KILLED);
    }

    private boolean tickIgnition(Entity passenger) {
        timeBeforeLaunch--;

        if (isTankEmpty() && !debugMode) {
            this.setLaunchStage(LaunchStage.IDLE);
            if (passenger instanceof ServerPlayer player) {
                player.sendSystemMessage(Component.translatable(Translations.Ui.ROCKET_NO_FUEL), false);
            }
            return true;
        }

        if (!this.isCreative() && RocketFlightLogic.BURNS_FUEL_DURING_COUNTDOWN) {
            this.getTank().extract(fuelBurnPerTick());
        }

        if (getTimeAsState() >= getPreLaunchWait()) {
            this.setLaunchStage(LaunchStage.LAUNCHED);
            this.setThrust(Mth.SQRT_OF_TWO / 2.0F);
            RocketEvents.LAUNCH.invoker().launch(this);

            resetCrashSystem();

            if (passenger instanceof ServerPlayer player) {
                player.awardStat(GCStats.LAUNCH_ROCKET);
            }

            releaseLaunchPad(passenger);
        }

        return false;
    }

    private void releaseLaunchPad(Entity passenger) {
        BlockPos dockPos = this.getLinkedPad();
        if (dockPos != BlockPos.ZERO) {
            boolean keepPad = this.linkedPad instanceof LaunchPadBlockEntity pad && pad.keepOnLaunch();
            if (passenger instanceof ServerPlayer player) {
                GCServerPlayer gcPlayer = GCServerPlayer.get(player);
                gcPlayer.setRocketData(this.getRocketData());
                gcPlayer.setLaunchpadStack(keepPad ? ItemStack.EMPTY : new ItemStack(GCBlocks.ROCKET_LAUNCH_PAD, ROCKET_LAUNCH_PAD_BLOCKS));
            }

            this.linkedPad.setDockedEntity(null);

            if (!keepPad && level().getBlockState(dockPos).getBlock() == GCBlocks.ROCKET_LAUNCH_PAD
                    && level().getBlockState(dockPos).getValue(AbstractLaunchPad.PART) != AbstractLaunchPad.Part.NONE) {
                level().destroyBlock(dockPos, false);
            }
            this.setPad(null);
        }
    }

    private boolean tickLaunched() {
        if (!isLanding() && Galacticraft.CONFIG.seamlessSpaceTravel() && controllerDestination < 0
                && getY() >= Constant.ESCAPE_HEIGHT - 256) {
            var departure = SpaceTravel.fromLevel(level());
            var space = ((ServerLevel) level()).getServer().getLevel(GCDimensions.SOLAR_SYSTEM);
            if (departure != null && space != null) {
                SpaceTravel.prepareArrival(space, departure.departurePosition(), getId());
                spaceOrigin = departure.body().location();
                spaceSurfaceX = getX();
                spaceSurfaceZ = getZ();
                // Space entry faces +Z; warm that flight path while the rocket is still ascending.
                var ahead = SpaceTravel.preloadTarget(registryAccess(), departure.departurePosition(), SpaceTravel.forward(90, 0));
                if (ahead != null) tryEnterAtmosphere(ahead, false);
            }
        }
        if (!this.isCreative() && !debugMode && (isTankEmpty() || !this.getTank().getResource().is(GCFluidTags.FUEL))) {
            this.setLaunchStage(LaunchStage.FAILED);
        } else {
            if (!this.isCreative()) {
                this.getTank().extract(fuelBurnPerTick());
            }

            if (isLanding()) {
                this.landingTicks = Math.min(this.landingTicks + 1, RocketFlightLogic.LANDING_DEPLOY_TICKS);
                this.tickLanding();
            } else {
                this.setThrust(this.getThrust() + 0.005F);
                this.tickInAir();
            }
        }

        if (!isLanding() && getLaunchStage() == LaunchStage.LAUNCHED && this.position().y() >= Constant.ESCAPE_HEIGHT) {
            this.completeSpaceTravel((ServerLevel) this.level());
            return true;
        }

        return false;
    }

    private void completeSpaceTravel(ServerLevel level) {
        if (this.isRemoved() || this.level() != level || this.getLaunchStage() != LaunchStage.LAUNCHED
                || this.isLanding() || this.getY() < Constant.ESCAPE_HEIGHT) return;
        if (Galacticraft.CONFIG.seamlessSpaceTravel() && this.controllerDestination < 0 && beginSpaceflight(level)) return;
        RocketEvents.REACHED_SPACE.invoker().reachedSpace(this);
        if (this.landAtController()) return;
        if (this.getPassengers().isEmpty()) {
            this.remove(RemovalReason.DISCARDED);
            return;
        }
        ServerPlayer pilot = null;
        for (Entity entity : this.getPassengers()) {
            if (entity instanceof ServerPlayer serverPlayer) {
                if (pilot == null) pilot = serverPlayer;
                else this.openCrewCelestialScreen(serverPlayer);
            }
        }
        if (pilot != null) this.openCelestialScreen(pilot);
    }

    private boolean beginSpaceflight(ServerLevel source) {
        SpaceTravel.Destination departure = SpaceTravel.fromLevel(source);
        ServerLevel space = source.getServer().getLevel(GCDimensions.SOLAR_SYSTEM);
        if (departure == null || space == null || getPassengers().isEmpty()) return false;
        setDeltaMovement(Vec3.ZERO);
        SpaceTravel.prepareArrival(space, departure.departurePosition(), getId());
        spaceOrigin = departure.body().location();
        spaceSurfaceX = getX();
        spaceSurfaceZ = getZ();
        RocketEvents.REACHED_SPACE.invoker().reachedSpace(this);
        prepareSpaceflightTransfer(space, departure.departurePosition(), 90);
        return true;
    }

    private void prepareSpaceflightTransfer(ServerLevel destination, Vec3 position, float pitch) {
        UUID token = UUID.randomUUID();
        var passengers = getPassengers().stream().filter(ServerPlayer.class::isInstance).map(Entity::getUUID)
                .collect(java.util.stream.Collectors.toSet());
        boolean departure = destination.dimension().equals(GCDimensions.SOLAR_SYSTEM);
        Vec3 flightPosition = departure ? position : position();
        preparedFlight = new FlightTransfer(destination, position, pitch);
        this.entityData.set(BOOST_LOCKED, false);
        spaceflightHandoff = new SpaceflightHandoff(token, passengers);
        setDeltaMovement(Vec3.ZERO);
        setThrust(0);
        for (Entity passenger : getPassengers()) {
            if (passenger instanceof ServerPlayer player) {
                if (player.containerMenu != player.inventoryMenu) player.closeContainer();
                NetworkManager.sendToPlayer(player, new SpaceflightHandoffPayload(getUUID(), token,
                        destination.dimension(), flightPosition, position, departure ? 0 : getYRot(), departure ? 90 : getXRot(), false));
            }
        }
    }

    private boolean tickSpaceflightHandoff() {
        if (spaceflightHandoff == null) return false;
        spaceflightHandoff.retainPassengers(getPassengers().stream().map(Entity::getUUID)
                .collect(java.util.stream.Collectors.toSet()));
        boolean chunksReady = preparedFlight == null || SpaceTravel.prepareArrival(preparedFlight.destination(), preparedFlight.position(), getId());
        if (spaceflightHandoff.ready() && chunksReady) {
            if (preparedFlight != null) {
                completePreparedSpaceflight();
                return true;
            }
            UUID token = spaceflightHandoff.token();
            spaceflightHandoff = null;
            if (isLanding() && !getPassengers().isEmpty()) {
                deployDestinationArrival(token);
                return true;
            }
            return false;
        }
        // Keep the craft and fuel safe while client chunks, lighting, meshes and seats arrive.
        setDeltaMovement(Vec3.ZERO);
        setThrust(0);
        resetCrashSystem();
        SpaceTravel.prepareArrival((ServerLevel) level(), position(), getId());
        return true;
    }

    private void completePreparedSpaceflight() {
        FlightTransfer flight = preparedFlight;
        UUID token = spaceflightHandoff.token();
        var clients = getPassengers().stream().filter(ServerPlayer.class::isInstance).map(ServerPlayer.class::cast).toList();
        preparedFlight = null;
        spaceflightHandoff = null;
        if (clients.isEmpty()) return;
        boolean departure = flight.destination().dimension().equals(GCDimensions.SOLAR_SYSTEM);
        Vec3 flightPosition = departure ? flight.position() : position();
        Entity moved = changeDimension(new DimensionTransition(flight.destination(), flight.position(), Vec3.ZERO,
                departure ? 0 : getYRot(), flight.pitch(), DimensionTransition.DO_NOTHING));
        if (!(moved instanceof RocketEntity arrived)) {
            for (ServerPlayer player : clients) NetworkManager.sendToPlayer(player, new SpaceflightHandoffPayload(getUUID(), token,
                    flight.destination().dimension(), flightPosition, flight.position(), 0, flight.pitch(), true));
            return;
        }
        arrived.setXRot(flight.pitch());
        arrived.setZRot(0);
        arrived.resetCrashSystem();
        arrived.setDeltaMovement(Vec3.ZERO);
        arrived.spaceflightHandoff = new SpaceflightHandoff(token, clients.stream().map(Entity::getUUID)
                .collect(java.util.stream.Collectors.toSet()));
        if (departure) {
            arrived.setYRot(0);
            for (ServerPlayer player : clients)
                player.sendSystemMessage(Component.translatable("ui.galacticraft.space.departure"), true);
        } else {
            arrived.entityData.set(MANUAL_LANDING, true);
            arrived.landingTicks = 0;
            arrived.setThrust(0);
        }
    }

    private void deployDestinationArrival(UUID token) {
        var crew = getPassengers().stream().filter(ServerPlayer.class::isInstance).map(ServerPlayer.class::cast).toList();
        if (crew.isEmpty()) return;
        ServerPlayer owner = crew.getFirst();
        packReturnInventory(owner);
        var destination = SpaceTravel.fromLevel((ServerLevel) level());
        var body = destination == null ? null : destination.resolve(registryAccess());
        var from = spaceOrigin == null ? null : registryAccess().registryOrThrow(AddonRegistries.CELESTIAL_BODY).get(spaceOrigin);
        for (int i = 0; i < crew.size(); i++) {
            var player = crew.get(i);
            // Separate seats/vehicles keep every rider in control of their own descent, without copying the hold.
            Vec3 seat = position().add(DestinationArrival.crewOffset(i));
            Entity arrival = i == 0
                    ? DestinationArrival.fromStoredRocket(player, seat)
                    : DestinationArrival.spawn(player, seat, NonNullList.create(), 0);
            NetworkManager.sendToPlayer(player, new SpaceflightArrivalPayload(token, arrival.getUUID()));
            if (body != null && from != null) CelestialEvents.AFTER_TRAVEL.invoker().afterTravel(player, body, from);
        }
        setFuel(0);
        discard();
    }

    private void packReturnInventory(ServerPlayer serverPlayer) {
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
    }

    private void tickSpaceflight() {
        boolean freshInput = !getPassengers().isEmpty() && tickCount - spaceInputTick <= 10;
        boolean powered = freshInput && !spaceBrake && (isCreative() || !isTankEmpty() && getTank().getResource().is(GCFluidTags.FUEL));
        setThrust(powered ? 1 : 0);
        Vec3 velocity = getDeltaMovement();
        if (powered) {
            if (!isCreative()) getTank().extract(fuelBurnPerTick());
            int tier = engineTier();
            velocity = SpaceTravel.accelerate(velocity, SpaceTravel.forward(getXRot(), getYRot())
                    .scale(spaceBoost ? SpaceTravel.boostSpeed(tier) : SpaceTravel.CRUISE_SPEED), SpaceTravel.acceleration(tier));
        } else if (!freshInput || spaceBrake) {
            velocity = SpaceTravel.accelerate(velocity, Vec3.ZERO, SpaceTravel.acceleration(engineTier()));
        }
        // Empty tanks coast without applying surface gravity; the pilot can still brake.
        setDeltaMovement(velocity);
        double days = SpaceTravel.days(level());
        SpaceTravel.Destination nearest = SpaceTravel.nearest(registryAccess(), position(), days);
        var ahead = SpaceTravel.preloadTarget(registryAccess(), position(), velocity);
        if (ahead != null && ahead.distance(position(), days) > SpaceTravel.PRELOAD_DISTANCE) tryEnterAtmosphere(ahead, false);
        if (nearest == null) { move(MoverType.SELF, getDeltaMovement()); return; }
        double distance = nearest.distance(position(), days);
        if (distance <= SpaceTravel.PRELOAD_DISTANCE && tryEnterAtmosphere(nearest, distance <= SpaceTravel.ENTRY_DISTANCE)) return;
        move(MoverType.SELF, getDeltaMovement());
        checkCrashAfterMove(velocity);
    }

    private boolean tryEnterAtmosphere(SpaceTravel.Destination target, boolean entering) {
        ServerLevel destination = target.level((ServerLevel) level());
        CelestialBody<?, ?> body = target.resolve(registryAccess());
        CelestialBody<?, ?> from = spaceOrigin == null ? null : registryAccess().registryOrThrow(AddonRegistries.CELESTIAL_BODY).get(spaceOrigin);
        if (destination == null || body == null || from == null || !getRocketData().canTravel(registryAccess(), from, body)) {
            if (entering) holdOutsideAtmosphere(target, "ui.galacticraft.space.unreachable");
            return entering;
        }
        double x = Mth.clamp(spaceSurfaceX, destination.getWorldBorder().getMinX() + 32, destination.getWorldBorder().getMaxX() - 32);
        double z = Mth.clamp(spaceSurfaceZ, destination.getWorldBorder().getMinZ() + 32, destination.getWorldBorder().getMaxZ() - 32);
        if (destination.getChunkSource().getGenerator() instanceof AsteroidChunkGenerator asteroids) {
            var asteroid = asteroids.isLargeAsteroidAt(Mth.floor(x), Mth.floor(z));
            if (asteroid != null) { x = asteroid.x + .5; z = asteroid.z + .5; }
        }
        Vec3 arrival = new Vec3(x, destination.getMaxBuildHeight() + 32, z);
        SpaceTravel.prepareArrival(destination, arrival, getId());
        if (!entering) return false;
        // Only the center is needed to choose the height; the armed handoff waits for the full send-ready area.
        if (getPassengers().isEmpty() || destination.getChunkSource().getChunkNow(Mth.floor(x) >> 4, Mth.floor(z) >> 4) == null) {
            setDeltaMovement(Vec3.ZERO);
            return true;
        }
        int ground = destination.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
        arrival = new Vec3(x, SpaceTravel.landingHeight(destination.dimension(), ground), z);
        if (target.body().equals(GCCelestialBodies.JUPITER)) {
            if (getPassengers().stream().anyMatch(passenger -> passenger instanceof ServerPlayer player
                    && !JupiterCelestialTeleporterType.hasEntryShield(player))) {
                holdOutsideAtmosphere(target, "message.galacticraft.jupiter.entry_shield");
                return true;
            }
            var landing = JupiterLanding.prepare(destination, x, z);
            if (landing.isEmpty()) { holdOutsideAtmosphere(target, "message.galacticraft.jupiter.no_landing"); return true; }
            var pos = landing.get();
            arrival = new Vec3(pos.getX() + .5, Math.min(308, pos.getY() + 18), pos.getZ() + .5);
            SpaceTravel.prepareArrival(destination, arrival, getId());
        }
        for (Entity passenger : getPassengers()) {
            if (passenger instanceof ServerPlayer player && CelestialEvents.BEFORE_TRAVEL.invoker().beforeTravel(player, body, from).isFalse()) {
                holdOutsideAtmosphere(target, "ui.galacticraft.space.denied");
                return true;
            }
        }
        if (destination.dimension().equals(GCDimensions.ASTEROID)) {
            for (int seat = 0; seat < getPassengers().size(); seat++) {
                Vec3 offset = DestinationArrival.crewOffset(seat);
                ground = Math.max(ground, DestinationArrival.landingSurface(destination, Mth.floor(x + offset.x), Mth.floor(z + offset.z)));
            }
            arrival = new Vec3(x, SpaceTravel.landingHeight(destination.dimension(), ground), z);
        }
        prepareSpaceflightTransfer(destination, arrival, 0);
        return true;
    }

    private void holdOutsideAtmosphere(SpaceTravel.Destination target, String message) {
        setPos(target.approachPosition(position(), SpaceTravel.ENTRY_DISTANCE + 16, SpaceTravel.days(level())));
        setDeltaMovement(Vec3.ZERO);
        if (tickCount % 20 == 0) {
            for (Entity passenger : getPassengers()) {
                if (passenger instanceof ServerPlayer player) player.sendSystemMessage(Component.translatable(message), true);
            }
        }
    }

    /**
     * Sends a crew member to the celestial map. Crew pick their own destination within the rocket's
     * reach and land in their own lander; the rocket, its fuel and its cargo stay with the pilot.
     */
    private void openCrewCelestialScreen(ServerPlayer crew) {
        if (crew.containerMenu != crew.inventoryMenu) {
            crew.closeContainer();
        }
        crew.galacticraft$openCelestialScreen(this.getRocketData());
        NetworkManager.sendToPlayer(crew, new OpenCelestialScreenPayload(this.getRocketData(), this.level().galacticraft$getCelestialBody()));
    }

    private void openCelestialScreen(ServerPlayer serverPlayer) {
        // The rocket menu is backed by this entity, so removing the rocket below
        // stops it being valid and the server's own stillValid sweep fires a
        // container-close a moment later - which would tear down the celestial map
        // we are about to open. Close it here instead, so the close reaches the
        // client ahead of the map packet, and so anything held on the cursor is
        // returned before the cargo transfer reads the hold.
        if (serverPlayer.containerMenu != serverPlayer.inventoryMenu) {
            serverPlayer.closeContainer();
        }

        packReturnInventory(serverPlayer);
        RocketData d = this.getRocketData();

        serverPlayer.galacticraft$openCelestialScreen(d);
        NetworkManager.sendToPlayer(serverPlayer, new OpenCelestialScreenPayload(this.getRocketData(), this.level().galacticraft$getCelestialBody()));

        remove(RemovalReason.UNLOADED_WITH_PLAYER);
    }

    private void tickFailedFlight() {
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

    public void tickInAir() {
        double horizontal = -1.58227848D * 0.632D * this.getThrust();
        double sinPitch = Mth.sin(this.getXRot() * Mth.DEG_TO_RAD);
        double velX = horizontal * sinPitch * Mth.sin(this.getYRot() * Mth.DEG_TO_RAD);
        double velZ = horizontal * -sinPitch * Mth.cos(this.getYRot() * Mth.DEG_TO_RAD);

        // The coefficient of this.getDeltaMovement().y() controls the terminal velocity
        // You might have to solve a differential equation to obtain a specific value
        double velY = 0.955D * this.getDeltaMovement().y()
                + 0.08D * Mth.SQRT_OF_TWO * this.getThrust() * Mth.cos(this.getXRot() * Mth.DEG_TO_RAD);

        Holder<RocketBooster<?, ?>> booster = this.getLaunchStage() == LaunchStage.LAUNCHED ? this.booster() : null;
        if (booster != null) {
            velY += booster.value().getAccelerationPerTick() * this.getThrust() * Mth.cos(this.getXRot() * Mth.DEG_TO_RAD);
        }

        if (!this.onGround()) {
            Holder<CelestialBody<?, ?>> holder = this.level().galacticraft$getCelestialBody();
            velY -= (holder != null ? holder.value().gravity() : 1.0D) * 0.08D;
        }

        if (booster != null) {
            velY = Math.min(velY, booster.value().getMaximumVelocity());
        }

        this.setDeltaMovement(new Vec3(velX, velY, velZ));
    }

    private void tickLanding() {
        setXRot(Mth.approachDegrees(getXRot(), 0, 3));
        setZRot(Mth.approachDegrees(getZRot(), 0, 3));
        if (getPassengers().isEmpty() || tickCount - landingInputTick > 10) {
            landingForward = landingStrafe = 0;
            landingHover = landingCutThrust = false;
        }
        Holder<CelestialBody<?, ?>> body = this.level().galacticraft$getCelestialBody();
        double gravity = body != null ? body.value().gravity() : 1.0;
        Vec3 velocity = getDeltaMovement();
        setThrust((float) RocketFlightLogic.landingThrust(velocity.y, landingHover, landingCutThrust, gravity));
        double yaw = getYRot() * Mth.DEG_TO_RAD;
        double scale = RocketFlightLogic.LANDING_HORIZONTAL_SPEED / Math.max(1, Math.hypot(landingForward, landingStrafe));
        double targetX = (landingStrafe * Math.cos(yaw) - landingForward * Math.sin(yaw)) * scale;
        double targetZ = (landingForward * Math.cos(yaw) + landingStrafe * Math.sin(yaw)) * scale;
        setDeltaMovement(RocketFlightLogic.nextLandingHorizontalVelocity(velocity.x, targetX),
                RocketFlightLogic.nextVerticalVelocity(velocity.y, getThrust(), 0, gravity),
                RocketFlightLogic.nextLandingHorizontalVelocity(velocity.z, targetZ));
    }

    private void finishManualLanding() {
        boolean jumpHeld = landingHover;
        setLaunchStage(LaunchStage.IDLE);
        this.lastJumping = jumpHeld;
        this.controllerDestination = -1;
        setThrust(0);
        setDeltaMovement(Vec3.ZERO);
        setXRot(0);
        setZRot(0);
        resetCrashSystem();
        for (Entity passenger : getPassengers()) if (passenger instanceof ServerPlayer player) {
            GCServerPlayer data = GCServerPlayer.get(player);
            player.getInventory().placeItemBackInInventory(data.getLaunchpadStack());
            data.setLaunchpadStack(ItemStack.EMPTY);
        }
        RocketEvents.LANDED.invoker().landed(this);
    }

    protected void spawnParticles() {
        if (!this.isAlive()) return;
        if (isLanding() && getThrust() <= 0) return;

        // Exhaust follows the nozzle axis; retain the upright and horizontal launch speeds.
        Vec3 nose = SpaceTravel.forward(this.getXRot(), this.getYRot());
        double sinPitch = Mth.sin(this.getXRot() * Mth.DEG_TO_RAD), cosPitch = Mth.cos(this.getXRot() * Mth.DEG_TO_RAD);
        Vec3 exhaust = nose.scale(-Math.sqrt(4 * sinPitch * sinPitch + cosPitch * cosPitch));

        if (this.getLaunchStage() == LaunchStage.FAILED && this.linkedPad != null) {
            double modifier = Mth.clamp(this.getY() - this.linkedPad.getDockPos().getY(), 120.0, 300.0);
            exhaust = exhaust.scale(modifier / 100.0D);
        }
        double x1 = exhaust.x, y1 = exhaust.y, z1 = exhaust.z;

        // Use actual displacement: synced velocity is clamped to 3.9 blocks per axis.
        Vec3 motion = new Vec3(this.getX() - this.xo, this.getY() - this.yo, this.getZ() - this.zo);
        // Space exhaust inherits craft motion; atmospheric flames stay behind the previous nozzle position.
        Vec3 carrier = this.isInSpaceflight() ? motion : Vec3.ZERO;
        Vec3 nozzle = this.position().add(0, 1.6D, 0).subtract(nose.scale(1.4D)).subtract(motion.subtract(carrier));

        double y = nozzle.y;
        final double x2 = nozzle.x;
        final double z2 = nozzle.z;

        LivingEntity riddenBy = (!this.getPassengers().isEmpty() && this.getPassengers().get(0) instanceof LivingEntity le) ? le : null;

        if (getLaunchStage() == LaunchStage.LAUNCHED) {
            EntityParticleOption particleData = new EntityParticleOption(GCParticleTypes.LAUNCH_FLAME_LAUNCHED, riddenBy == null ? null : riddenBy.getUUID(), carrier);
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
        // Pre-mode saves retain the configurable balance; changing the setting never spills fuel.
        this.gc4FuelMode = tag.getBoolean("Gc4FuelMode");
        // Registry-aware ops are required, not optional: the part codecs are RegistryFileCodecs, and
        // without a registry they cannot turn a saved id such as "galacticraft:tier_1" back into a
        // holder. That failure sinks the whole record, and the fallback below would quietly hand
        // back a bare tier 1 rocket - stripping the storage upgrade and every part with it.
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
        long savedFuel = tag.getLong("Fuel");
        long savedCapacity = tag.contains("FuelCapacity") ? tag.getLong("FuelCapacity")
                : FluidUtil.bucketsToDroplets(Galacticraft.CONFIG.rocketFuelTankCapacity());
        resizeFuelTank(Math.max(this.getFuelTankCapacity(), Math.max(savedCapacity, savedFuel)));
        if (tag.contains("Fuel")) this.setFuel(savedFuel);
        if (tag.contains("Creative")) this.setCreative(tag.getBoolean("Creative"));
        this.entityData.set(MANUAL_LANDING, getLaunchStage() == LaunchStage.LAUNCHED && tag.getBoolean("ManualLanding"));
        this.landingTicks = Math.clamp(tag.getInt("LandingTicks"), 0, RocketFlightLogic.LANDING_DEPLOY_TICKS);
        this.controllerDestination = tag.contains("ControllerDestination") ? tag.getInt("ControllerDestination") : -1;
        this.spaceOrigin = ResourceLocation.tryParse(tag.getString("SpaceOrigin"));
        this.spaceSurfaceX = tag.getDouble("SpaceSurfaceX");
        this.spaceSurfaceZ = tag.getDouble("SpaceSurfaceZ");

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
        tag.putLong("FuelCapacity", this.getFuelTankCapacity());
        tag.putBoolean("Gc4FuelMode", this.gc4FuelMode);
        tag.putInt("ControllerDestination", this.controllerDestination);
        if (spaceOrigin != null) tag.putString("SpaceOrigin", spaceOrigin.toString());
        tag.putDouble("SpaceSurfaceX", spaceSurfaceX);
        tag.putDouble("SpaceSurfaceZ", spaceSurfaceZ);
        tag.putBoolean("Creative", this.isCreative());
        tag.putBoolean("ManualLanding", isLanding());
        tag.putInt("LandingTicks", this.landingTicks);

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

    /**
     * Droplets of fuel the engines burn on a single tick of powered flight.
     *
     * In GC4 fuel mode the fitted engine and booster set the burn rate. With GC4 mode off the server
     * config sets one uniform burn rate for every rocket instead.
     */
    private long fuelBurnPerTick() {
        Holder<RocketEngine<?, ?>> engine = this.engine();
        if (this.gc4FuelMode && engine != null) {
            Holder<RocketBooster<?, ?>> booster = this.booster();
            long boosterUsage = booster != null ? booster.value().getFuelUsagePerTick() : 0;
            return Math.max(0, engine.value().getFuelUsagePerTick()) + Math.max(0, boosterUsage);
        }
        int burnTicksPerBucket = Galacticraft.CONFIG.rocketBurnTicksPerBucket();
        if (burnTicksPerBucket <= 0) return FluidUtil.bucketsToDroplets(1);
        return FluidUtil.bucketsToDroplets(1) / burnTicksPerBucket;
    }

    private static long initialFuelTankCapacity() {
        return FluidUtil.bucketsToDroplets(Galacticraft.CONFIG.gc4RocketFuelMode() ? 1 : Galacticraft.CONFIG.rocketFuelTankCapacity());
    }

    private void resizeFuelTank(long capacity) {
        capacity = Math.max(Math.max(1, capacity), this.tank.getAmount());
        SimpleFluidTank previous = this.tank;
        this.tank = new SimpleFluidTank(capacity, () -> this.entityData.set(FUEL, getTank().getAmount()));
        if (!previous.isEmpty()) this.tank.set(previous.getResource(), previous.getComponents(), previous.getAmount());
        this.entityData.set(FUEL_CAPACITY, capacity);
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
        Holder<RocketEngine<?, ?>> engine = this.engine();
        long capacity = Math.max(1, this.gc4FuelMode && engine != null
                ? engine.value().getFuelCapacity()
                : FluidUtil.bucketsToDroplets(Galacticraft.CONFIG.rocketFuelTankCapacity()));
        var upgrade = this.upgrade();
        long extra = upgrade == null ? 0 : Math.max(0, upgrade.value().additionalFuelCapacity(data));
        resizeFuelTank(capacity + Math.min(extra, Long.MAX_VALUE - capacity));
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (ROCKET_DATA.equals(accessor)) this.refreshDimensions();
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        if (this.entityData == null) return super.getDimensions(pose);
        RocketGeometry geometry = RocketGeometry.of(this.getRocketData(), this.registryAccess());
        return EntityDimensions.scalable(geometry.width() / 16F, geometry.top() / 16F).withEyeHeight(2.525F);
    }

    @Override
    public void inputTick(float leftImpulse, float forwardImpulse, boolean up, boolean down, boolean left, boolean right,
                          boolean jumping, boolean shiftKeyDown, boolean invertControls) {
        if (!level().isClientSide() && spaceflightHandoff != null && !spaceflightHandoff.ready()) return;
        boolean risingEdge = jumping && !this.lastJumping;
        this.lastJumping = jumping;

        LaunchStage stage = this.getLaunchStage();

        if (isInSpaceflight() && stage == LaunchStage.LAUNCHED) {
            this.spaceInputTick = tickCount;
            if (!level().isClientSide()) {
                // Double-tap boost toggles the lock; braking releases it.
                if (risingEdge) {
                    if (tickCount - this.lastBoostTap <= BOOST_LOCK_TAP_TICKS) {
                        this.entityData.set(BOOST_LOCKED, !isBoostLocked());
                        this.lastBoostTap = -100;
                    } else {
                        this.lastBoostTap = tickCount;
                    }
                }
                if (shiftKeyDown) this.entityData.set(BOOST_LOCKED, false);
            }
            this.spaceBoost = jumping || isBoostLocked();
            this.spaceBrake = shiftKeyDown;
            steerFromInput(up, down, left, right, false, false, false);
            return;
        }

        if (isLanding()) {
            this.landingInputTick = tickCount;
            // Use the same key states as ascent; mounted movement impulses may already be zeroed.
            this.landingForward = (up ? 1 : 0) - (down ? 1 : 0);
            this.landingStrafe = (left ? 1 : 0) - (right ? 1 : 0);
            this.landingHover = jumping;
            this.landingCutThrust = shiftKeyDown;
            return;
        }

        // Launch confirmation is server-authoritative and only happens on press-down.
        if (!this.level().isClientSide() && stage.ordinal() < LaunchStage.IGNITED.ordinal()) {
            handleLaunchConfirmation(risingEdge);
        }

        // Re-read stage in case it changed above.
        stage = this.getLaunchStage();

        if (stage.ordinal() >= LaunchStage.LAUNCHED.ordinal() && this.canManeuver()) {
            steerFromInput(up, down, left, right, jumping, shiftKeyDown, invertControls);
        }
    }

    /** Whether the fitted fins let the pilot steer during powered flight. */
    public boolean canManeuver() {
        Holder<RocketFin<?, ?>> fin = this.fin();
        return fin != null && fin.value().canManeuver();
    }

    private void handleLaunchConfirmation(boolean risingEdge) {
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

                    case WAITING_SECOND_PRESS -> this.igniteFromPad();
                }
            }
        }
    }

    private void steerFromInput(boolean up, boolean down, boolean left, boolean right,
                                boolean jumping, boolean shiftKeyDown, boolean invertControls) {
        float turnFactor = 2.0F;
        float angle = 180.0F;

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

    @Override
    public boolean shouldIgnoreShiftExit() {
        return getLaunchStage().ordinal() >= LaunchStage.LAUNCHED.ordinal();
    }

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
    }
}
