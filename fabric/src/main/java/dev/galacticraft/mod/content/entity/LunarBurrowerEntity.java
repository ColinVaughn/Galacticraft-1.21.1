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

package dev.galacticraft.mod.content.entity;

import dev.galacticraft.mod.content.GCBlocks;
import dev.galacticraft.mod.content.GCEntityTypes;
import dev.galacticraft.mod.content.item.GCItems;
import dev.galacticraft.mod.tag.GCBlockTags;
import dev.galacticraft.mod.util.BurrowerPath;
import dev.galacticraft.mod.util.BurrowerGrowth;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraft.world.*;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.tags.BlockTags;

import java.util.*;

/** A territorial cave resident. Combat and capture deadlines are authoritative on the server. */
public class LunarBurrowerEntity extends Monster {
    public static final int GRAB_TICK = 19;
    public static final int SWALLOW_TICK = 37;
    public static final int PROXY_END_TICK = 56;
    private static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(LunarBurrowerEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> START = SynchedEntityData.defineId(LunarBurrowerEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> CAPTURED = SynchedEntityData.defineId(LunarBurrowerEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> HATCHED = SynchedEntityData.defineId(LunarBurrowerEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> GROWTH_AGE = SynchedEntityData.defineId(LunarBurrowerEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(LunarBurrowerEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Boolean> SADDLED = SynchedEntityData.defineId(LunarBurrowerEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> CHESTED = SynchedEntityData.defineId(LunarBurrowerEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> SITTING = SynchedEntityData.defineId(LunarBurrowerEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> TUNNEL_MODE = SynchedEntityData.defineId(LunarBurrowerEntity.class, EntityDataSerializers.BOOLEAN);
    private int growthAge;
    private final SimpleContainer cargo = new SimpleContainer(27) {
        @Override public boolean stillValid(Player player) {
            return isAlive() && hasChest() && isOwnedBy(player) && player.level() == level() && distanceToSqr(player) < 64;
        }
    };
    private final List<Vec3> trail = new ArrayList<>();
    private Vec3[] bodyPath = new Vec3[41];
    private Vec3[] previousBodyPath = new Vec3[41];
    private final BurrowerBodyEntity[] parts = new BurrowerBodyEntity[BurrowerGrowth.MAX_SECTIONS + 1];
    private BlockPos home;
    private UUID captured;
    private LivingEntity attackVictim;
    private Vec3 escapePosition;
    private int angerTicks;
    private int attackCooldown;
    private int digCooldown;
    private int danceCooldown = 1200;
    private int blockedTicks;
    private boolean biteApplied;
    private boolean swallowed;
    private boolean releasing;
    private double previousTargetDistance = Double.MAX_VALUE;
    private final List<BlockPos> digBlocks = new ArrayList<>();
    private Vec3 digExit;
    private double travel;
    private float rideWeight, previousRideWeight;
    private double previousTravel;
    private Vec3 previousPosition;

    public LunarBurrowerEntity(EntityType<? extends LunarBurrowerEntity> type, Level level) {
        super(type, level);
        this.xpReward = 12;
        this.lookControl = new LookControl(this) {
            @Override public void tick() {
                // Vanilla idle look control resets pitch, including the rider's tunnel aim.
                if (getControllingPassenger() == null) super.tick();
            }
        };
        this.moveControl = new MoveControl(this) {
            @Override protected float rotlerp(float source, float target, float maximumChange) {
                // Steering must bend the neck before the head can reverse, even on a sharp navigation corner.
                return BurrowerPath.steerYaw(source, target, getNeckYaw(1));
            }
        };
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 48)
                .add(Attributes.MOVEMENT_SPEED, 0.18).add(Attributes.ATTACK_DAMAGE, 7)
                .add(Attributes.FOLLOW_RANGE, 16).add(Attributes.KNOCKBACK_RESISTANCE, 0.8).add(Attributes.STEP_HEIGHT, 1);
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(STATE, BurrowerState.IDLE.ordinal());
        builder.define(START, 0L);
        builder.define(CAPTURED, -1);
        builder.define(HATCHED, false);
        builder.define(GROWTH_AGE, BurrowerGrowth.ADULT_AGE);
        builder.define(OWNER, Optional.empty());
        builder.define(SADDLED, false);
        builder.define(CHESTED, false);
        builder.define(SITTING, false);
        builder.define(TUNNEL_MODE, false);
    }
    @Override protected void registerGoals() { goalSelector.addGoal(0, new FloatGoal(this)); }
    public BurrowerState getBurrowerState() { return BurrowerState.values()[entityData.get(STATE)]; }
    public int getStateTicks() { return (int)Math.max(0, level().getGameTime() - entityData.get(START)); }
    public int getCapturedId() { return entityData.get(CAPTURED); }
    public boolean canEscape() { return getBurrowerState() == BurrowerState.EAT && getStateTicks() < SWALLOW_TICK; }
    public BlockPos getDenPosition() { return home == null ? blockPosition() : home; }
    public void setDenPosition(BlockPos pos) { home = pos.immutable(); setPersistenceRequired(); }
    public double getTravel(float partialTick) { return previousTravel + (travel - previousTravel) * partialTick; }

    public boolean isHatched() { return entityData.get(HATCHED); }
    @Override public boolean isBaby() { return isHatched() && getGrowthAge() < BurrowerGrowth.ADULT_AGE; }
    public boolean isTame() { return entityData.get(OWNER).isPresent(); }
    public boolean isOwnedBy(Player player) { return entityData.get(OWNER).filter(player.getUUID()::equals).isPresent(); }
    public boolean isSaddled() { return entityData.get(SADDLED); }
    public boolean hasChest() { return entityData.get(CHESTED); }
    public boolean isSitting() { return entityData.get(SITTING); }
    public boolean isTunnelMode() { return entityData.get(TUNNEL_MODE); }
    public void toggleTunnelMode(Player player) {
        if (level().isClientSide || !isAlive() || getControllingPassenger() != player) return;
        entityData.set(TUNNEL_MODE, !isTunnelMode());
    }
    public SimpleContainer getCargo() { return cargo; }
    public int getGrowthAge() { return entityData.get(GROWTH_AGE); }
    public int getBodySectionCount() { return isHatched() ? BurrowerGrowth.sections(getGrowthAge()) : 6; }
    public float getModelScale() { return isHatched() ? BurrowerGrowth.scale(getGrowthAge()) : 1; }
    public void hatch() {
        entityData.set(HATCHED, true);
        setGrowthAge(0);
        setPersistenceRequired();
    }
    public void setGrowingTicks(int ticks) {
        setGrowthAge(BurrowerGrowth.ADULT_AGE - Math.clamp(ticks, 0, BurrowerGrowth.ADULT_AGE));
    }
    public void setGrowthAge(int age) {
        growthAge = Math.clamp(age, 0, BurrowerGrowth.MAX_AGE);
        entityData.set(GROWTH_AGE, growthAge);
        refreshDimensions();
    }
    @Override protected EntityDimensions getDefaultDimensions(Pose pose) {
        return super.getDefaultDimensions(pose).scale(getModelScale());
    }
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (GROWTH_AGE.equals(accessor) || HATCHED.equals(accessor)) refreshDimensions();
    }
    @Override protected boolean shouldDespawnInPeaceful() { return !isHatched(); }
    @Override public boolean removeWhenFarAway(double distance) { return !isHatched(); }

    @Override public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!isHatched() || !isAlive()) return super.mobInteract(player, hand);
        ItemStack stack = player.getItemInHand(hand);
        boolean food = stack.is(GCItems.MOON_CHEESE_CURD) || stack.is(GCItems.MOON_CHEESE_SLICE);
        if (!isTame() && food) {
            if (!level().isClientSide) {
                stack.consume(1, player);
                entityData.set(OWNER, Optional.of(player.getUUID()));
                setTarget(null);
                begin(BurrowerState.IDLE);
                level().broadcastEntityEvent(this, (byte)7);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (!isOwnedBy(player)) return InteractionResult.PASS;
        return this.interactWithOwnedBurrower(player, stack, food);
    }

    private InteractionResult interactWithOwnedBurrower(Player player, ItemStack stack, boolean food) {
        if (food && (getGrowthAge() < BurrowerGrowth.MAX_AGE || getHealth() < getMaxHealth())) {
            if (!level().isClientSide) {
                stack.consume(1, player);
                heal(6);
                setGrowthAge(growthAge + 1200);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (this.interactWithEquipment(player, stack) || this.interactWithCommands(player, stack)) {
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (!isBaby() && isSaddled() && !isVehicle()) {
            if (!level().isClientSide) { entityData.set(SITTING, false); player.startRiding(this); }
        } else return InteractionResult.PASS;
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    private boolean interactWithEquipment(Player player, ItemStack stack) {
        if (!isBaby() && stack.is(Items.SADDLE) && !isSaddled()) {
            if (!level().isClientSide) { entityData.set(SADDLED, true); stack.consume(1, player); playSound(SoundEvents.HORSE_SADDLE); }
        } else if (!isBaby() && stack.is(Items.CHEST) && !hasChest()) {
            if (!level().isClientSide) { entityData.set(CHESTED, true); stack.consume(1, player); playSound(SoundEvents.DONKEY_CHEST); }
        } else if (player.isShiftKeyDown() && stack.is(Items.SHEARS) && !isVehicle()) {
            if (!level().isClientSide) {
                if (hasChest()) { dropCargo(); spawnAtLocation(Items.CHEST); entityData.set(CHESTED, false); }
                else if (isSaddled()) { spawnAtLocation(Items.SADDLE); entityData.set(SADDLED, false); }
            }
        } else return false;
        return true;
    }

    private boolean interactWithCommands(Player player, ItemStack stack) {
        if (player.isShiftKeyDown() && stack.is(Items.STICK)) {
            if (!level().isClientSide) { entityData.set(SITTING, !isSitting()); navigation.stop(); }
        } else if (player.isShiftKeyDown() && hasChest()) {
            if (!level().isClientSide) player.openMenu(new SimpleMenuProvider((id, inventory, who) ->
                    ChestMenu.threeRows(id, inventory, cargo), Component.translatable("entity.galacticraft.lunar_burrower.cargo")));
        } else if (player.isShiftKeyDown() && stack.isEmpty()) {
            if (!level().isClientSide) { entityData.set(SITTING, !isSitting()); navigation.stop(); }
        } else return false;
        return true;
    }
    @Override public void handleEntityEvent(byte event) {
        if (event == 7) for (int i = 0; i < 7; i++) level().addParticle(ParticleTypes.HEART,
                getRandomX(1), getRandomY() + 0.5, getRandomZ(1), 0, 0.05, 0);
        else super.handleEntityEvent(event);
    }
    @Override public LivingEntity getControllingPassenger() {
        return isTame() && isSaddled() && !isBaby() && getFirstPassenger() instanceof Player player && isOwnedBy(player) ? player : null;
    }
    @Override protected Vec3 getRiddenInput(Player player, Vec3 input) {
        return new Vec3(player.xxa * 0.5, 0, player.zza < 0 ? player.zza * 0.25 : player.zza);
    }
    @Override protected float getRiddenSpeed(Player player) { return player.isSprinting() ? 0.21F : 0.15F; }
    @Override protected void tickRidden(Player player, Vec3 input) {
        super.tickRidden(player, input);
        setYRot(BurrowerPath.steerYaw(getYRot(), player.getYRot(), getNeckYaw(1)));
        yBodyRot = yHeadRot = getYRot();
        setXRot(Mth.approachDegrees(getXRot(), Mth.clamp(player.getXRot(), -60, 60), 4));
        navigation.stop();
    }
    @Override public void travel(Vec3 input) {
        if (isTunnelMode() && getControllingPassenger() instanceof Player player && touchesTunnelTerrain()) {
            Vec3 movement = tunnelMovement(player);
            if (!canClearRiderTunnel(movement, false)) movement = Vec3.ZERO;
            setDeltaMovement(movement);
            move(MoverType.SELF, movement);
            resetFallDistance();
            calculateEntityAnimation(true);
        } else super.travel(input);
    }
    private Vec3 tunnelMovement(Player player) {
        return BurrowerPath.riddenMotion(getYRot(), getXRot(), player.zza, player.xxa, getRiddenSpeed(player));
    }
    private boolean touchesTunnelTerrain() {
        return level().getBlockCollisions(this, getBoundingBox().inflate(2 * getModelScale(), 1.1, 2 * getModelScale()))
                .iterator().hasNext();
    }
    private boolean canClearRiderTunnel(Vec3 movement, boolean destroy) {
        if (movement.lengthSqr() < 0.00001) return true;
        if (!level().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) return false;
        float scale = getModelScale();
        double radius = 1.75 * scale + 0.15;
        double bottom = getY() + Math.min(0, movement.y);
        AABB tunnel = new AABB(getX() - radius, bottom, getZ() - radius,
                getX() + radius, getY() + 1.94 * scale + 1.9, getZ() + radius).expandTowards(movement.scale(3));
        // Check the whole next slice before removing anything, including space for the saddle and rider.
        List<BlockPos> blocks = new ArrayList<>();
        for (BlockPos cursor : BlockPos.betweenClosed(BlockPos.containing(tunnel.minX, tunnel.minY, tunnel.minZ),
                BlockPos.containing(tunnel.maxX, tunnel.maxY, tunnel.maxZ))) {
            if (!level().isInWorldBounds(cursor) || !level().hasChunkAt(cursor) || !level().getWorldBorder().isWithinBounds(cursor)) return false;
            var state = level().getBlockState(cursor);
            if (!state.getFluidState().isEmpty() || state.hasBlockEntity()) return false;
            if (state.isAir() || state.getCollisionShape(level(), cursor).isEmpty()) continue;
            if (state.getDestroySpeed(level(), cursor) < 0 || !(state.is(GCBlockTags.MOON_CAVE_SUBSTRATE)
                    || state.is(BlockTags.MINEABLE_WITH_PICKAXE) || state.is(BlockTags.MINEABLE_WITH_SHOVEL))) return false;
            blocks.add(cursor.immutable());
        }
        if (destroy && !blocks.isEmpty()) {
            var debris = level().getBlockState(blocks.getFirst());
            // One debris burst avoids hundreds of simultaneous block-break effects on a giant tunnel slice.
            for (BlockPos pos : blocks) level().setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            if (tickCount % 5 == 0) {
                playSound(SoundEvents.STONE_BREAK, 0.7F, 0.7F);
                ((ServerLevel)level()).sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, debris),
                        getX(), getY() + 0.6 * scale, getZ(), 8, 0.6 * scale, 0.4 * scale, 0.6 * scale, 0.03);
            }
        }
        return true;
    }
    private void tickCompanion() {
        setTarget(null);
        if (getControllingPassenger() instanceof Player player) {
            if (isTunnelMode() && player.zza > 0 && touchesTunnelTerrain()) canClearRiderTunnel(tunnelMovement(player), true);
            BurrowerState state = Math.abs(player.zza) + Math.abs(player.xxa) > 0.01
                    || getDeltaMovement().horizontalDistanceSqr() > 0.0001 ? BurrowerState.RIDE : BurrowerState.IDLE;
            if (getBurrowerState() != state) begin(state);
            return;
        }
        if (isTunnelMode()) entityData.set(TUNNEL_MODE, false);
        setXRot(Mth.approachDegrees(getXRot(), 0, 4));
        Player owner = entityData.get(OWNER).map(level()::getPlayerByUUID).orElse(null);
        if (!isSitting() && owner != null && !owner.isSpectator() && distanceToSqr(owner) > 16) {
            if (getBurrowerState() != BurrowerState.CRAWL) begin(BurrowerState.CRAWL);
            navigation.moveTo(owner, isBaby() ? 0.7 : 0.55);
        } else {
            navigation.stop();
            if (getBurrowerState() != BurrowerState.IDLE) begin(BurrowerState.IDLE);
        }
    }
    private void dropCargo() {
        for (int slot = 0; slot < cargo.getContainerSize(); slot++) spawnAtLocation(cargo.removeItemNoUpdate(slot));
    }

    public float getNeckYaw(float partialTick) {
        if (bodyPath[0] == null) return Mth.rotLerp(partialTick, yRotO, getYRot());
        Vec3 forward = bodyPositionAt(0.25 * getModelScale(), partialTick).subtract(bodyPositionAt(1.25 * getModelScale(), partialTick));
        return forward.horizontalDistanceSqr() < 0.00001 ? getYRot()
                : (float)(Math.toDegrees(Math.atan2(forward.z, forward.x)) - 90);
    }

    public float getHeadYaw(float partialTick) {
        return BurrowerPath.clampHeadYaw(Mth.rotLerp(partialTick, yRotO, getYRot()), getNeckYaw(partialTick));
    }

    private boolean isInFront(LivingEntity target) {
        Vec3 offset = target.position().subtract(position()).multiply(1, 0, 1);
        return offset.horizontalDistanceSqr() < 0.0625 || getLookAngle().multiply(1, 0, 1).normalize().dot(offset.normalize()) >= 0.5;
    }

    private void begin(BurrowerState state) {
        if (level().isClientSide) return;
        entityData.set(STATE, state.ordinal());
        entityData.set(START, level().getGameTime());
        biteApplied = false;
        if (state != BurrowerState.EAT && state != BurrowerState.BITE) attackVictim = null;
        if (state != BurrowerState.CRAWL && state != BurrowerState.CHASE && state != BurrowerState.DIG && state != BurrowerState.RIDE) {
            navigation.stop();
            setDeltaMovement(new Vec3(0, getDeltaMovement().y, 0));
        }
        if (state == BurrowerState.WARNING) playSound(SoundEvents.RAVAGER_ROAR, 0.6F, 0.55F);
    }

    public boolean beginAttack(LivingEntity victim, boolean eat) {
        if (isHatched() || level().isClientSide || !eligible(victim) || distanceToSqr(victim) > 12.25
                || !getSensing().hasLineOfSight(victim) || !isInFront(victim) || attackCooldown > 0
                || getBurrowerState() == BurrowerState.EAT || getBurrowerState() == BurrowerState.BITE) return false;
        setTarget(victim);
        attackVictim = victim;
        angerTicks = 240;
        swallowed = false;
        entityData.set(CAPTURED, -1);
        begin(eat && victim instanceof Player ? BurrowerState.EAT : BurrowerState.BITE);
        return true;
    }

    public void disturb(Player player) {
        if (isHatched() || level().isClientSide || !eligible(player) || player.distanceToSqr(Vec3.atCenterOf(getDenPosition())) > 196) return;
        setTarget(player);
        angerTicks = 240;
        if (getBurrowerState() != BurrowerState.EAT && getBurrowerState() != BurrowerState.BITE) begin(BurrowerState.WARNING);
    }

    public static void miningDisturbance(ServerLevel level, BlockPos pos, Player player) {
        for (var worm : level.getEntitiesOfClass(LunarBurrowerEntity.class, new AABB(pos).inflate(10))) worm.disturb(player);
    }

    private static boolean eligible(LivingEntity target) {
        return target != null && target.isAlive() && !target.isRemoved() && !target.isSpectator()
                && (!(target instanceof Player player) || !player.isCreative());
    }

    @Override public void tick() {
        float previousYaw = getYRot();
        super.tick();
        if (!level().isClientSide && isAlive() && isHatched() && growthAge < BurrowerGrowth.MAX_AGE) {
            growthAge++;
            if (growthAge % 20 == 0 || growthAge == BurrowerGrowth.MAX_AGE) setGrowthAge(growthAge);
        }
        previousTravel = travel;
        if (previousPosition != null) travel += isTunnelMode() ? position().distanceTo(previousPosition) : position().subtract(previousPosition).horizontalDistance();
        previousPosition = position();
        previousRideWeight = rideWeight;
        rideWeight = Mth.approach(rideWeight, getBurrowerState() == BurrowerState.RIDE ? 1 : 0, 0.125F);
        updateTrail();
        if (!level().isClientSide) {
            setYRot(BurrowerPath.steerYaw(previousYaw, getYRot(), getNeckYaw(1)));
            yBodyRot = yHeadRot = getYRot();
        }
        if (!level().isClientSide && isAlive()) updateParts();
        // Vanilla positions passengers after their tick; moving them here erases camera interpolation.
    }

    @Override public void aiStep() {
        super.aiStep();
        if (level().isClientSide || !isAlive() || isRemoved() || isNoAi()) return;
        if (home == null) { home = blockPosition(); begin(BurrowerState.IDLE); }
        if (isHatched()) { tickCompanion(); return; }
        attackCooldown = Math.max(0, attackCooldown - 1);
        digCooldown = Math.max(0, digCooldown - 1);
        danceCooldown = Math.max(0, danceCooldown - 1);
        angerTicks = Math.max(0, angerTicks - 1);
        int age = getStateTicks();
        BurrowerState state = getBurrowerState();
        if (state == BurrowerState.EAT) { tickEat(age); return; }
        if (state == BurrowerState.BITE) { tickBite(age, state); return; }
        if (state == BurrowerState.BURP) { tickBurp(age, state); return; }
        if (state == BurrowerState.DIG) { tickDig(age); return; }
        if (state == BurrowerState.DANCE) {
            if (getTarget() == null && age < state.clipTicks * 3) return;
            danceCooldown = 2400;
            begin(BurrowerState.IDLE);
        }
        tickTerritorialBehavior(age, state);
    }

    private void tickBite(int age, BurrowerState state) {
        if (age >= 12 && !biteApplied) {
            biteApplied = true;
            LivingEntity target = attackVictim;
            if (eligible(target) && distanceToSqr(target) <= 12.25 && isInFront(target) && getSensing().hasLineOfSight(target)) {
                super.doHurtTarget(target);
                playSound(SoundEvents.RAVAGER_ATTACK, 0.7F, 0.7F);
            }
        }
        if (age >= state.clipTicks) { attackCooldown = 50; begin(BurrowerState.CHASE); }
    }

    private void tickBurp(int age, BurrowerState state) {
        if (age == 15) {
            playSound(SoundEvents.PLAYER_BURP, 1.1F, 0.6F);
            Vec3 mouth = position().add(getLookAngle().multiply(0.9, 0, 0.9)).add(0, 0.65, 0);
            ((ServerLevel)level()).sendParticles(ParticleTypes.CLOUD, mouth.x, mouth.y, mouth.z, 3, 0.12, 0.1, 0.12, 0.02);
        }
        if (age >= state.clipTicks) begin(BurrowerState.IDLE);
    }

    private void tickTerritorialBehavior(int age, BurrowerState state) {
        LivingEntity target = getTarget();
        if (!eligible(target) || target.level() != level()
                || target.distanceToSqr(Vec3.atCenterOf(home)) > 196 || (angerTicks == 0 && state == BurrowerState.CHASE)) {
            setTarget(null);
            target = null;
        }
        if (state == BurrowerState.WARNING) {
            if (target == null || distanceToSqr(target) > 100) { begin(BurrowerState.IDLE); return; }
            face(target.position());
            if (age >= state.clipTicks) { angerTicks = 240; begin(BurrowerState.CHASE); }
            return;
        }
        if (target != null && angerTicks > 0) {
            if (state != BurrowerState.CHASE) begin(BurrowerState.CHASE);
            if (distanceToSqr(target) <= 9 && attackCooldown == 0 && beginAttack(target, random.nextInt(3) == 0)) return;
            moveToward(target.position(), 1.0);
            return;
        }
        tickPatrol(state);
    }

    private void tickPatrol(BurrowerState state) {
        Player nearby = level().getEntitiesOfClass(Player.class, getBoundingBox().inflate(9), LunarBurrowerEntity::eligible)
                .stream().filter(p -> p.distanceToSqr(Vec3.atCenterOf(home)) < 144 && getSensing().hasLineOfSight(p))
                .min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
        if (nearby != null) {
            if (distanceToSqr(nearby) < 12.25) { disturb(nearby); return; }
            if (!nearby.isShiftKeyDown() && nearby.position().distanceToSqr(new Vec3(nearby.xOld, nearby.yOld, nearby.zOld)) > 0.0004) {
                if (state != BurrowerState.CRAWL) begin(BurrowerState.CRAWL);
                moveToward(nearby.position(), 0.45);
                return;
            }
        }
        if (distanceToSqr(Vec3.atCenterOf(home)) > 9) {
            if (state != BurrowerState.CRAWL) begin(BurrowerState.CRAWL);
            moveToward(Vec3.atBottomCenterOf(home), 0.45);
        } else if (danceCooldown == 0 && nearby == null && random.nextInt(6000) == 0) {
            startDance();
        } else if (state != BurrowerState.IDLE) {
            begin(BurrowerState.IDLE);
        }
    }

    public boolean startDance() {
        if (level().isClientSide || getTarget() != null || getBurrowerState() != BurrowerState.IDLE) return false;
        begin(BurrowerState.DANCE);
        return true;
    }

    private void face(Vec3 target) {
        Vec3 delta = target.subtract(position());
        float yaw = (float)(Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90);
        setYRot(BurrowerPath.steerYaw(getYRot(), Mth.rotLerp(0.18F, getYRot(), yaw), getNeckYaw(1)));
        yBodyRot = getYRot();
        yHeadRot = getYRot();
    }

    private void moveToward(Vec3 target, double speed) {
        double distance = position().distanceToSqr(target);
        boolean path = navigation.moveTo(target.x, target.y, target.z, speed);
        blockedTicks = !path || (distance >= previousTargetDistance - 0.005 && horizontalCollision) ? blockedTicks + 1 : 0;
        previousTargetDistance = distance;
        if (blockedTicks > 40 && digCooldown == 0) tryStartDigging(target.subtract(position()));
    }

    private void tickEat(int age) {
        navigation.stop();
        LivingEntity target = attackVictim;
        if (age == GRAB_TICK && target instanceof ServerPlayer player && eligible(player)
                && distanceToSqr(player) <= 12.25 && isInFront(player) && getSensing().hasLineOfSight(player) && !player.isPassenger()) {
            escapePosition = player.position();
            captured = player.getUUID();
            entityData.set(CAPTURED, player.getId());
            if (!player.startRiding(this, true)) { captured = null; entityData.set(CAPTURED, -1); }
            else player.displayClientMessage(Component.translatable("entity.galacticraft.lunar_burrower.escape"), true);
        }
        if (captured != null) {
            ServerPlayer player = ((ServerLevel)level()).getServer().getPlayerList().getPlayer(captured);
            if (player == null || player.level() != level() || !eligible(player) || player.getVehicle() != this) {
                releaseCapture();
            } else if (player.isShiftKeyDown() && canEscape()) {
                releaseCapture();
                entityData.set(CAPTURED, -1);
                attackCooldown = 80;
                begin(BurrowerState.CHASE);
                return;
            } else if (age >= SWALLOW_TICK) {
                swallowed = true;
                player.hurt(damageSources().fellOutOfWorld(), Float.MAX_VALUE);
                releaseCapture();
                setTarget(null);
            }
        }
        if (age >= PROXY_END_TICK) entityData.set(CAPTURED, -1);
        if (age >= BurrowerState.EAT.clipTicks) {
            releaseCapture();
            entityData.set(CAPTURED, -1);
            attackCooldown = 100;
            begin(swallowed ? BurrowerState.BURP : BurrowerState.CHASE);
        }
    }

    @Override protected void positionRider(Entity passenger, MoveFunction move) {
        if (isTame() && passenger == getControllingPassenger()) {
            Vec3 seat = getSaddlePosition(1).subtract(passenger.getVehicleAttachmentPoint(this));
            move.accept(passenger, seat.x, seat.y, seat.z);
        } else if (passenger instanceof Player && getBurrowerState() == BurrowerState.EAT) {
            float progress = net.minecraft.util.Mth.clamp((getStateTicks() - GRAB_TICK) / 18.0F, 0, 1);
            Vec3 mouth = position().add(getLookAngle().multiply(1, 0, 1).normalize().scale(0.9 - progress * 1.3));
            move.accept(passenger, mouth.x, getY() + 0.1, mouth.z);
        } else super.positionRider(passenger, move);
    }
    public Vec3 getSaddlePosition(float partialTick) {
        float scale = getModelScale();
        double distance = 1.375 * scale;
        Vec3 forward = bodyPositionAt(distance - 0.35 * scale, partialTick)
                .subtract(bodyPositionAt(distance + 0.35 * scale, partialTick)).normalize();
        double horizontal = forward.horizontalDistance();
        Vec3 up = horizontal < 0.00001 ? new Vec3(0, 1, 0)
                : new Vec3(-forward.x * forward.y / horizontal, horizontal, -forward.z * forward.y / horizontal);
        return bodyPositionAt(distance, partialTick).add(up.scale(23.0 / 16 * scale));
    }
    @Override public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        if (isTame()) for (Direction direction : Direction.Plane.HORIZONTAL) {
            float scale = getModelScale();
            Vec3 candidate = bodyPositionAt(1.375 * scale).add(direction.getStepX() * 2 * scale, 0, direction.getStepZ() * 2 * scale);
            BlockPos floor = BlockPos.containing(candidate).below();
            if (level().getBlockState(floor).isFaceSturdy(level(), floor, Direction.UP)
                    && level().noCollision(passenger, passenger.getBoundingBox().move(candidate.subtract(passenger.position())))) return candidate;
        }
        return super.getDismountLocationForPassenger(passenger);
    }
    @Override protected boolean canAddPassenger(Entity passenger) {
        return passenger instanceof Player player && getPassengers().isEmpty()
                && (isTame() ? isOwnedBy(player) && isSaddled() && !isBaby() : getBurrowerState() == BurrowerState.EAT);
    }
    @Override protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        if (!level().isClientSide && !releasing && captured != null && captured.equals(passenger.getUUID())) {
            releaseCapture();
            entityData.set(CAPTURED, -1);
            attackCooldown = 80;
            begin(BurrowerState.CHASE);
        }
    }
    private void releaseCapture() {
        if (captured == null || !(level() instanceof ServerLevel server)) return;
        ServerPlayer player = server.getServer().getPlayerList().getPlayer(captured);
        captured = null;
        releasing = true;
        if (player != null && player.getVehicle() == this) player.stopRiding();
        if (player != null && player.isAlive() && player.level() == level() && escapePosition != null) {
            Vec3 safe = escapePosition;
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                Vec3 candidate = position().add(direction.getStepX() * 2.5, 0, direction.getStepZ() * 2.5);
                AABB box = player.getBoundingBox().move(candidate.subtract(player.position()));
                if (level().noCollision(player, box) && level().getBlockState(BlockPos.containing(candidate).below())
                        .isFaceSturdy(level(), BlockPos.containing(candidate).below(), Direction.UP)) { safe = candidate; break; }
            }
            player.teleportTo(safe.x, safe.y, safe.z);
            player.setDeltaMovement(Vec3.ZERO);
        }
        releasing = false;
    }

    @Override public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (!hurt || level().isClientSide) return hurt;
        if (isAlive() && canEscape() && amount >= 4) {
            releaseCapture();
            entityData.set(CAPTURED, -1);
            attackCooldown = 80;
            begin(BurrowerState.CHASE);
        }
        if (source.getEntity() instanceof Player player) disturb(player);
        return hurt;
    }
    @Override public void die(DamageSource source) { releaseCapture(); super.die(source); }
    @Override protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        if (!isHatched()) spawnAtLocation(GCBlocks.LUNAR_BURROWER_EGG);
        if (isSaddled()) spawnAtLocation(Items.SADDLE);
        if (hasChest()) { spawnAtLocation(Items.CHEST); dropCargo(); }
    }
    @Override public void remove(RemovalReason reason) {
        releaseCapture();
        for (var part : parts) if (part != null && !part.isRemoved()) part.discard();
        super.remove(reason);
    }

    public boolean tryStartDigging(Vec3 toward) {
        if (isHatched() || level().isClientSide || !level().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)
                || (getBurrowerState() != BurrowerState.IDLE && getBurrowerState() != BurrowerState.CRAWL
                && getBurrowerState() != BurrowerState.CHASE) || toward.horizontalDistanceSqr() < 0.01) return false;
        Direction direction = Direction.getNearest(toward.x, 0, toward.z);
        Direction side = direction.getClockWise();
        List<BlockPos> route = new ArrayList<>();
        BlockPos start = blockPosition();
        // A short straight route with a checked exit; never tunnel blindly into an unloaded chunk.
        for (int step = 1; step <= 5; step++) for (int width = -1; width <= 1; width++) {
            BlockPos column = start.relative(direction, step).relative(side, width);
            if (!level().hasChunkAt(column) || !level().getWorldBorder().isWithinBounds(column)
                    || !level().getBlockState(column.below()).isFaceSturdy(level(), column.below(), Direction.UP)) return false;
            for (int y = 0; y < 2; y++) {
                BlockPos pos = column.above(y);
                var state = level().getBlockState(pos);
                if (!state.getFluidState().isEmpty() || level().getBlockEntity(pos) != null) return false;
                if (state.isAir()) continue;
                if (step == 5 || !state.is(GCBlockTags.MOON_CAVE_SUBSTRATE) || state.is(GCBlocks.DENSE_ICE)) return false;
                route.add(pos);
            }
        }
        if (route.isEmpty()) return false;
        digBlocks.clear(); digBlocks.addAll(route);
        digExit = Vec3.atBottomCenterOf(start.relative(direction, 5));
        face(digExit);
        begin(BurrowerState.DIG);
        return true;
    }
    private void tickDig(int age) {
        if (!level().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) { finishDig(); return; }
        if (age % 4 == 0 && !digBlocks.isEmpty()) {
            for (int i = 0; i < 3 && !digBlocks.isEmpty(); i++) {
                BlockPos pos = digBlocks.removeFirst();
                if (!level().hasChunkAt(pos) || !level().getWorldBorder().isWithinBounds(pos)) { finishDig(); return; }
                var state = level().getBlockState(pos);
                BlockPos support = new BlockPos(pos.getX(), (int)digExit.y - 1, pos.getZ());
                if (!state.is(GCBlockTags.MOON_CAVE_SUBSTRATE) || state.is(GCBlocks.DENSE_ICE)
                        || level().getBlockEntity(pos) != null || !state.getFluidState().isEmpty()
                        || !level().getBlockState(support).isFaceSturdy(level(), support, Direction.UP)) { finishDig(); return; }
                ((ServerLevel)level()).sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
                        pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 6, 0.4, 0.4, 0.4, 0.03);
                level().destroyBlock(pos, false, this);
            }
            playSound(SoundEvents.STONE_BREAK, 0.7F, 0.7F);
        }
        if (digBlocks.isEmpty() && digExit != null) navigation.moveTo(digExit.x, digExit.y, digExit.z, 0.65);
        if (age >= 96 || digExit != null && position().distanceToSqr(digExit) < 1) finishDig();
    }
    private void finishDig() { digBlocks.clear(); digExit = null; digCooldown = 300; blockedTicks = 0; begin(BurrowerState.IDLE); }

    private void updateTrail() {
        int samples = Math.max(41, (int)Math.ceil((getBodySectionCount() + 3) * getModelScale() * 4) + 2);
        if (bodyPath.length != samples) {
            bodyPath = Arrays.copyOf(bodyPath, samples);
            previousBodyPath = Arrays.copyOf(previousBodyPath, samples);
        }
        boolean reset = trail.isEmpty() || position().distanceToSqr(trail.getFirst()) > 256;
        if (reset) {
            trail.clear();
            Vec3 backward = getLookAngle().multiply(-1, 0, -1).normalize();
            for (int i = 0; i < samples; i++) trail.add(position().add(backward.scale(i * 0.25)));
        } else {
            // Keep a fixed sample behind the live head so slow movement accumulates distance.
            if ((isTunnelMode() ? position().distanceToSqr(trail.get(1)) : position().subtract(trail.get(1)).horizontalDistanceSqr()) > 0.0025) {
                trail.add(1, position());
                if (trail.size() > Math.max(240, samples * 5)) trail.removeLast();
            }
            trail.set(0, position());
        }
        Vec3[] history = new Vec3[bodyPath.length];
        for (int i = 0; i < history.length; i++) history[i] = trailPosition(i * 0.25);
        history = BurrowerPath.smoothHorizontal(history);
        // Ground the rounded route before measuring body length; a fall is not a vertical body trail.
        Vec3[] ground = new Vec3[bodyPath.length];
        for (int i = 0; i < bodyPath.length; i++) {
            Vec3 historic = history[i];
            Vec3 point = isTunnelMode() ? historic : new Vec3(historic.x, getY(), historic.z);
            if (!isTunnelMode() && i > 0 && level().hasChunkAt(BlockPos.containing(point))) {
                var floor = level().clip(new ClipContext(historic.add(0, 1.01, 0), historic.add(0, -8, 0),
                        ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
                if (floor.getType() == HitResult.Type.BLOCK) point = new Vec3(point.x, floor.getLocation().y, point.z);
            }
            ground[i] = point;
        }
        // Raise the approach to a step so armor spans it rather than clipping through its vertical face.
        if (!isTunnelMode()) {
            for (int i = 1; i < ground.length; i++) ground[i] = withMinimumHeight(ground[i], ground[i - 1].y - 0.1875);
            for (int i = ground.length - 2; i > 0; i--) ground[i] = withMinimumHeight(ground[i], ground[i + 1].y - 0.1875);
        }
        for (int i = 0; i < bodyPath.length; i++) {
            previousBodyPath[i] = bodyPath[i];
            bodyPath[i] = BurrowerPath.sample(ground, i * 0.25);
            if (reset || previousBodyPath[i] == null) previousBodyPath[i] = bodyPath[i];
        }
    }
    private static Vec3 withMinimumHeight(Vec3 point, double height) {
        return height > point.y ? new Vec3(point.x, height, point.z) : point;
    }
    public Vec3 bodyPosition(int segment) {
        return bodyPositionAt((segment == getBodySectionCount() ? getBodySectionCount() + 1.0 : 1.375 + segment) * getModelScale());
    }
    public Vec3 bodyPositionAt(double distance) {
        return bodyPositionAt(distance, 1);
    }
    public Vec3 bodyPositionAt(double distance, float partialTick) {
        Vec3 point = bodyPathPositionAt(distance, partialTick);
        float weight = Mth.lerp(partialTick, previousRideWeight, rideWeight);
        if (weight == 0 || distance <= 0) return point;
        float scale = getModelScale();
        Vec3 wave = BurrowerPath.rideOffset(distance / scale, getTravel(partialTick) / scale, weight).scale(scale);
        Vec3 backward = bodyPathPositionAt(distance + 0.25 * scale, partialTick)
                .subtract(bodyPathPositionAt(Math.max(0, distance - 0.25 * scale), partialTick)).multiply(1, 0, 1).normalize();
        return point.add(backward.z * wave.x, wave.y, -backward.x * wave.x);
    }
    private Vec3 bodyPathPositionAt(double distance, float partialTick) {
        if (bodyPath[0] == null) return trailPosition(distance);
        if (distance < 0) {
            Vec3 head = bodyPathPositionAt(0, partialTick);
            return head.add(bodyPathPositionAt(0.25, partialTick).subtract(head).scale(distance * 4));
        }
        double sample = net.minecraft.util.Mth.clamp(distance * 4, 0, bodyPath.length - 1);
        int index = Math.min((int)sample, bodyPath.length - 2);
        Vec3 current = bodyPath[index].lerp(bodyPath[index + 1], sample - index);
        Vec3 previous = previousBodyPath[index].lerp(previousBodyPath[index + 1], sample - index);
        return previous.lerp(current, partialTick);
    }
    private Vec3 trailPosition(double distance) {
        if (distance <= 0) return position();
        if (trail.isEmpty()) return position().subtract(getLookAngle().multiply(distance, 0, distance));
        for (int i = 1; i < trail.size(); i++) {
            double length = isTunnelMode() ? trail.get(i - 1).distanceTo(trail.get(i)) : trail.get(i - 1).subtract(trail.get(i)).horizontalDistance();
            if (distance <= length && length > 0.00001) return trail.get(i - 1).lerp(trail.get(i), distance / length);
            distance -= length;
        }
        Vec3 tail = trail.getLast();
        Vec3 backward = trail.size() > 1 ? tail.subtract(trail.get(trail.size() - 2)).multiply(1, isTunnelMode() ? 1 : 0, 1).normalize()
                : getLookAngle().multiply(-1, 0, -1).normalize();
        return tail.add(backward.scale(distance));
    }
    private void updateParts() {
        for (int i = 0; i < parts.length; i++) {
            if (i > getBodySectionCount()) {
                if (parts[i] != null) { parts[i].discard(); parts[i] = null; }
                continue;
            }
            if (parts[i] == null || parts[i].isRemoved()) {
                parts[i] = new BurrowerBodyEntity(GCEntityTypes.BURROWER_BODY, level());
                parts[i].setOwner(this);
                parts[i].setPos(bodyPosition(i));
                level().addFreshEntity(parts[i]);
            }
            parts[i].setPos(bodyPosition(i));
        }
    }

    @Override public boolean isPushable() { return false; }
    @Override protected void doPush(Entity entity) {}
    @Override public AABB getBoundingBoxForCulling() { return getBoundingBox().inflate((getBodySectionCount() + 3) * getModelScale()); }
    @Override protected net.minecraft.sounds.SoundEvent getAmbientSound() { return SoundEvents.SILVERFISH_AMBIENT; }
    @Override protected net.minecraft.sounds.SoundEvent getHurtSound(DamageSource source) { return SoundEvents.STONE_BREAK; }
    @Override protected net.minecraft.sounds.SoundEvent getDeathSound() { return SoundEvents.RAVAGER_DEATH; }
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        BlockPos den = getDenPosition();
        tag.putLong("BurrowerDen", den.asLong());
        tag.putInt("DanceCooldown", danceCooldown);
        tag.putBoolean("Hatched", isHatched());
        tag.putInt("GrowingTicks", Math.max(0, BurrowerGrowth.ADULT_AGE - growthAge));
        tag.putInt("GrowthAge", growthAge);
        entityData.get(OWNER).ifPresent(owner -> tag.putUUID("Owner", owner));
        tag.putBoolean("Saddled", isSaddled());
        tag.putBoolean("Chested", hasChest());
        tag.putBoolean("Sitting", isSitting());
        CompoundTag inventory = new CompoundTag();
        ContainerHelper.saveAllItems(inventory, cargo.getItems(), registryAccess());
        tag.put("Cargo", inventory);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("BurrowerDen")) home = BlockPos.of(tag.getLong("BurrowerDen"));
        danceCooldown = Math.max(1200, tag.getInt("DanceCooldown"));
        entityData.set(HATCHED, tag.getBoolean("Hatched"));
        setGrowthAge(tag.contains("GrowthAge") ? tag.getInt("GrowthAge") : BurrowerGrowth.ADULT_AGE - tag.getInt("GrowingTicks"));
        entityData.set(OWNER, tag.hasUUID("Owner") ? Optional.of(tag.getUUID("Owner")) : Optional.empty());
        entityData.set(SADDLED, tag.getBoolean("Saddled"));
        entityData.set(CHESTED, tag.getBoolean("Chested"));
        entityData.set(SITTING, tag.getBoolean("Sitting"));
        cargo.clearContent();
        ContainerHelper.loadAllItems(tag.getCompound("Cargo"), cargo.getItems(), registryAccess());
        captured = null; entityData.set(CAPTURED, -1); begin(BurrowerState.IDLE);
    }
    public static boolean checkSpawnRules(EntityType<LunarBurrowerEntity> type, ServerLevelAccessor level,
                                          MobSpawnType reason, BlockPos pos, net.minecraft.util.RandomSource random) {
        if (level.getDifficulty() == Difficulty.PEACEFUL || !level.getBlockState(pos.below()).is(GCBlockTags.MOON_CAVE_SUBSTRATE)) return false;
        if (level.canSeeSky(pos) || level.getBrightness(LightLayer.BLOCK, pos) > 7) return false;
        for (int x = -1; x <= 1; x++) for (int z = 0; z <= 8; z++) {
            if (!level.getBlockState(pos.offset(x, -1, z)).is(GCBlockTags.MOON_CAVE_SUBSTRATE)) return false;
            for (int y = 0; y < 3; y++) {
            BlockPos check = pos.offset(x, y, z);
            var state = level.getBlockState(check);
            if (!state.isAir() || !state.getFluidState().isEmpty()) return false;
            }
        }
        return true;
    }
    @Override public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                                   MobSpawnType reason, SpawnGroupData data) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, data);
        setYRot(180); yBodyRot = 180; yHeadRot = 180;
        return result;
    }
}
