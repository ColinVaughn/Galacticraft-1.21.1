/*
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

package dev.galacticraft.mod.content.entity.jupiter;

import dev.galacticraft.mod.content.block.jupiter.encounter.StormheartPedestalBlock;
import dev.galacticraft.mod.content.block.jupiter.encounter.StormheartPedestalBlockEntity;
import dev.galacticraft.mod.world.dimension.jupiter.JupiterStorms;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.FlyingMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Red Vortex boss linked to a Stormheart pedestal. Energized relays shield it while it circles
 * and telegraphs storm strikes; disabling the relays exposes its core and adds close-range bites.
 * Grounders can intercept strikes. Without a pedestal, it spawns exposed.
 */
public class StormheartLeviathanEntity extends JupiterFlyer implements Enemy {
    public static final int TELEGRAPH_TICKS = 30;
    public static final double APPROACH_RADIUS = 20, APPROACH_HEIGHT = 14, EXPOSED_RADIUS = 10, EXPOSED_HEIGHT = 5;
    /** Storm Breath: armour-piercing beam damage, range, and how long before release the aim locks (the dodge window). */
    public static final float BREATH_DAMAGE = 14, TAIL_DAMAGE = 10;
    public static final double BREATH_RANGE = 48, BREATH_WIDTH = 1.2, TAIL_REACH = 9, ROAR_PUSH_RANGE = 10, MOUTH_REACH = 5.5;
    public static final int BREATH_LOCK = 8, ROAR_DARKNESS_TICKS = 140;

    /** What the Leviathan is doing; each action plays its own clip and fires its effect partway through. */
    public enum Action {
        NONE(0), ROAR(40), BREATH_CHARGE(30), BREATH(16), TAIL_SLAM(24), BITE(20);

        public final int duration;

        Action(int duration) {
            this.duration = duration;
        }
    }

    private static final EntityDataAccessor<Boolean> EXPOSED = SynchedEntityData.defineId(StormheartLeviathanEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> ACTION = SynchedEntityData.defineId(StormheartLeviathanEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ACTION_LEFT = SynchedEntityData.defineId(StormheartLeviathanEntity.class, EntityDataSerializers.INT);
    private final ServerBossEvent bossEvent = new ServerBossEvent(this.getDisplayName(), BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.NOTCHED_10);
    private @Nullable BlockPos pedestal;
    private @Nullable Vec3 anchor;
    private @Nullable Vec3 strikeAt;
    private @Nullable Vec3 aim;
    private @Nullable ServerPlayer target;
    private double orbit;
    private boolean roarPending = true;
    private int strikeTicks, dischargeTimer = 60, biteCooldown, tailCooldown = 60, breathCooldown = 120;

    public StormheartLeviathanEntity(EntityType<? extends FlyingMob> type, Level level) {
        super(type, level);
        this.xpReward = 120;
        this.noPhysics = true;
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return flyerAttributes(300.0).add(Attributes.ARMOR, 8.0).add(Attributes.ATTACK_DAMAGE, 10.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0).add(Attributes.FOLLOW_RANGE, 64.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(EXPOSED, false);
        builder.define(ACTION, Action.NONE.ordinal());
        builder.define(ACTION_LEFT, 0);
    }

    public void bind(BlockPos pedestal) {
        this.pedestal = pedestal.immutable();
    }

    public @Nullable BlockPos pedestal() {
        return this.pedestal;
    }

    /** True once the relays are down and the core can be hurt. */
    public boolean isExposed() {
        return this.entityData.get(EXPOSED);
    }

    public Action action() {
        return Action.values()[Math.floorMod(this.entityData.get(ACTION), Action.values().length)];
    }

    /** Ticks since the current action began, for the renderer. */
    public int actionElapsed() {
        return this.action().duration - this.entityData.get(ACTION_LEFT);
    }

    /** Holds a chosen frame of an action, for showcases and rendered checks; the creature does not act on it. */
    public void showPose(Action action, int elapsed) {
        this.entityData.set(ACTION, action.ordinal());
        this.entityData.set(ACTION_LEFT, Math.max(0, action.duration - elapsed));
    }

    /** Emits one frame of the breath's charge-up sparks, for showcases. */
    public void showCharge(ServerLevel level, int elapsed) {
        this.chargeParticles(level, elapsed);
    }

    private void begin(Action action) {
        this.entityData.set(ACTION, action.ordinal());
        this.entityData.set(ACTION_LEFT, action.duration);
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!(this.level() instanceof ServerLevel level)) return;
        Vec3 centre;
        boolean exposed;
        if (this.pedestal != null) {
            if (!level.isLoaded(this.pedestal)) return;
            if (!(level.getBlockEntity(this.pedestal) instanceof StormheartPedestalBlockEntity encounter)
                    || encounter.phase() == StormheartPedestalBlock.Phase.IDLE) {
                // The encounter ended or reset; the storm takes its creature back.
                level.sendParticles(ParticleTypes.CLOUD, this.getX(), this.getY() + 1, this.getZ(), 40, 2, 1, 2, 0.05);
                this.discard();
                return;
            }
            centre = Vec3.atBottomCenterOf(this.pedestal);
            exposed = encounter.phase() == StormheartPedestalBlock.Phase.CORE_EXPOSED;
        } else {
            if (this.anchor == null) this.anchor = this.position().subtract(0, APPROACH_HEIGHT * 0.5, 0);
            centre = this.anchor;
            exposed = true;
        }
        if (exposed != this.isExposed()) {
            this.entityData.set(EXPOSED, exposed);
            if (exposed) this.roarPending = true;
        }
        if (this.biteCooldown > 0) this.biteCooldown--;
        if (this.tailCooldown > 0) this.tailCooldown--;
        if (this.breathCooldown > 0) this.breathCooldown--;

        List<ServerPlayer> fighters = level.getEntitiesOfClass(ServerPlayer.class,
                new AABB(BlockPos.containing(centre)).inflate(StormheartPedestalBlockEntity.ARENA_RANGE), p -> !p.isSpectator() && !p.isCreative());
        if (this.action() == Action.NONE) {
            double radius = exposed ? EXPOSED_RADIUS : APPROACH_RADIUS, height = exposed ? EXPOSED_HEIGHT : APPROACH_HEIGHT;
            this.orbit += exposed ? 0.02 : 0.012;
            Vec3 station = centre.add(Math.cos(this.orbit) * radius, height + Math.sin(this.tickCount * 0.04) * 1.5, Math.sin(this.orbit) * radius);
            this.steer(station, exposed ? 0.35 : 0.45, 0.08);
            this.choose(level, fighters, exposed);
        } else {
            this.setDeltaMovement(this.getDeltaMovement().scale(0.8));
            if (this.target != null && this.target.isAlive()) this.faceToward(this.target.position());
            this.act(level, fighters);
        }
        if (this.action() != Action.ROAR) this.discharge(level, fighters, exposed);
        this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());
    }

    private void choose(ServerLevel level, List<ServerPlayer> fighters, boolean exposed) {
        if (fighters.isEmpty()) return;
        if (this.roarPending) {
            this.roarPending = false;
            this.target = null;
            this.begin(Action.ROAR);
            return;
        }
        ServerPlayer nearest = fighters.stream().min(java.util.Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
        if (exposed && this.biteCooldown == 0 && nearest.distanceTo(this) < 6.5) {
            this.target = nearest;
            this.biteCooldown = 40;
            this.begin(Action.BITE);
            return;
        }
        if (exposed && this.tailCooldown == 0) {
            for (ServerPlayer player : fighters) {
                if (this.isBehindOrBeside(player) && player.distanceTo(this) < TAIL_REACH) {
                    this.target = null;
                    this.tailCooldown = 90;
                    this.begin(Action.TAIL_SLAM);
                    level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ELDER_GUARDIAN_FLOP, SoundSource.HOSTILE, 3, 0.5F);
                    return;
                }
            }
        }
        if (this.breathCooldown == 0) {
            for (ServerPlayer player : fighters) {
                if (player.distanceTo(this) < BREATH_RANGE && this.clearShot(level, player.getEyePosition())) {
                    this.target = player;
                    this.aim = player.getEyePosition();
                    this.breathCooldown = exposed ? 160 : 220;
                    this.begin(Action.BREATH_CHARGE);
                    level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.HOSTILE, 4, 0.6F);
                    return;
                }
            }
        }
    }

    private void act(ServerLevel level, List<ServerPlayer> fighters) {
        Action action = this.action();
        int elapsed = this.actionElapsed();
        switch (action) {
            case ROAR -> {
                if (elapsed == 8) this.roar(level);
                if (elapsed >= 8 && elapsed <= 20 && elapsed % 3 == 2) this.shockwave(level, (elapsed - 5) * 1.2);
            }
            case BREATH_CHARGE -> {
                // Track the target until lock, then hold aim to leave a dodge window.
                if (this.target != null && elapsed < action.duration - BREATH_LOCK) this.aim = this.target.getEyePosition();
                this.chargeParticles(level, elapsed);
            }
            case BREATH -> {
                if (elapsed == 1 && this.aim != null) this.breathe(level, this.aim);
            }
            case TAIL_SLAM -> {
                if (elapsed == 12) this.tailSweep(level);
            }
            case BITE -> {
                if (elapsed == 6 && this.target != null && this.target.distanceTo(this) < 7.0) {
                    this.doHurtTarget(this.target);
                    level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.EVOKER_FANGS_ATTACK, SoundSource.HOSTILE, 2, 0.6F);
                }
            }
            default -> { }
        }
        int left = this.entityData.get(ACTION_LEFT) - 1;
        if (left > 0) {
            this.entityData.set(ACTION_LEFT, left);
        } else if (action == Action.BREATH_CHARGE) {
            this.begin(Action.BREATH);
        } else {
            this.begin(Action.NONE);
        }
    }

    /** Where the beam leaves the jaws: ahead of the body along its heading, at head height. */
    public Vec3 mouth() {
        float yaw = this.getYRot() * net.minecraft.util.Mth.DEG_TO_RAD;
        return this.position().add(-net.minecraft.util.Mth.sin(yaw) * MOUTH_REACH, 1.5, net.minecraft.util.Mth.cos(yaw) * MOUTH_REACH);
    }

    private void faceToward(Vec3 point) {
        Vec3 offset = point.subtract(this.position());
        float yaw = (float) (net.minecraft.util.Mth.atan2(offset.z, offset.x) * net.minecraft.util.Mth.RAD_TO_DEG) - 90.0F;
        this.setYRot(net.minecraft.util.Mth.rotLerp(0.3F, this.getYRot(), yaw));
        this.yBodyRot = this.getYRot();
        this.yHeadRot = this.getYRot();
    }

    private boolean clearShot(ServerLevel level, Vec3 point) {
        return level.clip(new net.minecraft.world.level.ClipContext(this.mouth(), point, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, this)).getType() == net.minecraft.world.phys.HitResult.Type.MISS;
    }

    private boolean isBehindOrBeside(Player player) {
        float yaw = this.getYRot() * net.minecraft.util.Mth.DEG_TO_RAD;
        Vec3 forward = new Vec3(-net.minecraft.util.Mth.sin(yaw), 0, net.minecraft.util.Mth.cos(yaw));
        Vec3 to = player.position().subtract(this.position()).multiply(1, 0, 1).normalize();
        return forward.dot(to) < 0.25;
    }

    /**
     * Roar: a bellow that blinds every fighter within the arena with Darkness and throws back anyone close, like the
     * Warden's. Returns how many players it affected.
     */
    public int roar(ServerLevel level) {
        level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 6, 0.55F);
        level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 4, 0.5F);
        Vec3 mouth = this.mouth();
        level.sendParticles(ParticleTypes.SONIC_BOOM, mouth.x, mouth.y, mouth.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.FLASH, mouth.x, mouth.y, mouth.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, mouth.x, mouth.y, mouth.z, 60, 1.5, 1.0, 1.5, 0.5);
        this.shockwave(level, 2.0);
        this.shockwave(level, 4.0);
        int affected = 0;
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, this.getBoundingBox().inflate(StormheartPedestalBlockEntity.ARENA_RANGE),
                p -> !p.isSpectator() && !p.isCreative())) {
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DARKNESS, ROAR_DARKNESS_TICKS, 0), this);
            if (player.distanceTo(this) < ROAR_PUSH_RANGE) {
                Vec3 push = player.position().subtract(this.position()).multiply(1, 0, 1).normalize().scale(1.6);
                player.push(push.x, 0.5, push.z);
                player.hurtMarked = true;
            }
            affected++;
        }
        return affected;
    }

    /** One expanding ring of the roar's shockwave. */
    private void shockwave(ServerLevel level, double radius) {
        for (int i = 0; i < 28; i++) {
            double angle = i * Math.PI * 2 / 28;
            double x = this.getX() + Math.cos(angle) * radius, z = this.getZ() + Math.sin(angle) * radius;
            level.sendParticles(ParticleTypes.CLOUD, x, this.getY() + 1, z, 0, Math.cos(angle), 0.05, Math.sin(angle), 0.25);
            if (i % 4 == 0) level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, this.getY() + 1, z, 2, 0.2, 0.2, 0.2, 0.05);
        }
    }

    /** Sparks spiral in from around the head and pour into the maw as the breath builds. */
    private void chargeParticles(ServerLevel level, int elapsed) {
        Vec3 mouth = this.mouth();
        double radius = 4.0 - elapsed * 0.1;
        for (int i = 0; i < 10; i++) {
            double angle = elapsed * 0.45 + i * Math.PI / 5, rise = Math.sin(elapsed * 0.3 + i) * 1.5;
            Vec3 from = mouth.add(Math.cos(angle) * radius, rise, Math.sin(angle) * radius);
            Vec3 into = mouth.subtract(from);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, from.x, from.y, from.z, 0, into.x, into.y, into.z, 0.15);
        }
        level.sendParticles(ParticleTypes.END_ROD, mouth.x, mouth.y, mouth.z, 1 + elapsed / 10, 0.25, 0.25, 0.25, 0.01);
    }

    /**
     * Storm Breath: a lightning beam from the jaws toward {@code at}. Blocks stop it, so cover is the counterplay; anyone
     * along the open stretch takes armour-piercing damage and is thrown down the beam. Returns how many players it hit.
     */
    public int breathe(ServerLevel level, Vec3 at) {
        Vec3 mouth = this.mouth();
        Vec3 direction = at.subtract(mouth).normalize();
        Vec3 far = mouth.add(direction.scale(BREATH_RANGE));
        var block = level.clip(new net.minecraft.world.level.ClipContext(mouth, far, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, this));
        Vec3 end = block.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? far : block.getLocation();
        level.playSound(null, mouth.x, mouth.y, mouth.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 5, 0.6F);
        level.playSound(null, end.x, end.y, end.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.HOSTILE, 3, 0.7F);
        double length = end.distanceTo(mouth);
        Vec3 side = direction.cross(new Vec3(0, 1, 0)).lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : direction.cross(new Vec3(0, 1, 0)).normalize();
        Vec3 up = side.cross(direction).normalize();
        for (double d = 0; d < length; d += 0.25) {
            Vec3 core = mouth.add(direction.scale(d));
            level.sendParticles(ParticleTypes.END_ROD, core.x, core.y, core.z, 2, 0.08, 0.08, 0.08, 0.0);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, core.x, core.y, core.z, 2, 0.12, 0.12, 0.12, 0.02);
            double twist = d * 1.6;
            Vec3 coil = core.add(side.scale(Math.cos(twist) * 0.45)).add(up.scale(Math.sin(twist) * 0.45));
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, coil.x, coil.y, coil.z, 1, 0, 0, 0, 0);
            if (((int) (d * 4)) % 12 == 0) level.sendParticles(ParticleTypes.SONIC_BOOM, core.x, core.y, core.z, 1, 0, 0, 0, 0);
        }
        level.sendParticles(ParticleTypes.FLASH, mouth.x, mouth.y, mouth.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.FLASH, end.x, end.y, end.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.EXPLOSION, end.x, end.y, end.z, 3, 0.5, 0.5, 0.5, 0);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, end.x, end.y, end.z, 40, 0.6, 0.6, 0.6, 0.4);
        int hit = 0;
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, new AABB(mouth, end).inflate(BREATH_WIDTH), p -> !p.isSpectator() && !p.isCreative())) {
            if (player.getBoundingBox().inflate(BREATH_WIDTH * 0.5).clip(mouth, end).isEmpty()) continue;
            if (player.hurt(this.damageSources().indirectMagic(this, this), BREATH_DAMAGE)) {
                player.push(direction.x * 2.2, 0.45, direction.z * 2.2);
                player.hurtMarked = true;
                hit++;
            }
        }
        return hit;
    }

    /** Tail Sweep: whips the tail through everyone behind and beside it, launching them. Returns how many it hit. */
    public int tailSweep(ServerLevel level) {
        level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 4, 0.5F);
        level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.5F, 0.6F);
        float yaw = this.getYRot() * net.minecraft.util.Mth.DEG_TO_RAD;
        for (int i = 0; i <= 10; i++) {
            double angle = yaw + Math.PI * 0.4 + i * Math.PI * 1.2 / 10;
            double x = this.getX() - Math.sin(angle) * 6, z = this.getZ() + Math.cos(angle) * 6;
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, x, this.getY() + 1, z, 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.CLOUD, x, this.getY() + 1, z, 2, 0.3, 0.2, 0.3, 0.02);
        }
        int hit = 0;
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, this.getBoundingBox().inflate(TAIL_REACH), p -> !p.isSpectator() && !p.isCreative())) {
            if (player.distanceTo(this) > TAIL_REACH || !this.isBehindOrBeside(player)) continue;
            if (player.hurt(this.damageSources().mobAttack(this), TAIL_DAMAGE)) {
                Vec3 push = player.position().subtract(this.position()).multiply(1, 0, 1).normalize().scale(2.5);
                player.push(push.x, 0.7, push.z);
                player.hurtMarked = true;
                hit++;
            }
        }
        return hit;
    }

    /** Telegraphs a strike under a random fighter, then lands it; grounders redirect it as they do any storm strike. */
    private void discharge(ServerLevel level, List<ServerPlayer> fighters, boolean exposed) {
        if (this.strikeAt != null) {
            if (--this.strikeTicks > 0) {
                for (int i = 0; i < 6; i++) {
                    double angle = (this.strikeTicks + i) * Math.PI / 3;
                    level.sendParticles(ParticleTypes.ELECTRIC_SPARK, this.strikeAt.x + Math.cos(angle) * 1.5, this.strikeAt.y + 0.2,
                            this.strikeAt.z + Math.sin(angle) * 1.5, 1, 0, 0.05, 0, 0);
                }
                return;
            }
            JupiterStorms.strike(level, this.strikeAt);
            this.strikeAt = null;
            return;
        }
        if (--this.dischargeTimer > 0 || fighters.isEmpty()) return;
        this.dischargeTimer = exposed ? 140 : 90;
        this.strikeAt = fighters.get(this.random.nextInt(fighters.size())).position();
        this.strikeTicks = TELEGRAPH_TICKS;
        level.playSound(null, this.strikeAt.x, this.strikeAt.y, this.strikeAt.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 2, 0.5F);
    }

    /** Server truth for the shield: up while this creature's encounter still has energized relays. */
    public boolean isShielded() {
        if (this.level().isClientSide) return !this.isExposed();
        return this.pedestal != null && this.level().getBlockEntity(this.pedestal) instanceof StormheartPedestalBlockEntity encounter
                && encounter.phase() == StormheartPedestalBlock.Phase.ACTIVE;
    }

    /** The telegraphed strike point, if a discharge is building. */
    public @Nullable Vec3 pendingStrike() {
        return this.strikeAt;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.isShielded() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            // Shielded by the energized relays: the hit sparks off harmlessly.
            if (this.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, this.getX(), this.getY() + 1.5, this.getZ(), 12, 1, 1, 1, 0.1);
                level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.HOSTILE, 1.5F, 0.6F);
            }
            return false;
        }
        return super.hurt(source, amount);
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (this.pedestal != null && this.level() instanceof ServerLevel level
                && level.getBlockEntity(this.pedestal) instanceof StormheartPedestalBlockEntity encounter) {
            encounter.leviathanSlain();
        }
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        this.bossEvent.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        this.bossEvent.removePlayer(player);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (this.pedestal != null) tag.put("Pedestal", NbtUtils.writeBlockPos(this.pedestal));
        tag.putDouble("Orbit", this.orbit);
        tag.putBoolean("RoarPending", this.roarPending);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        NbtUtils.readBlockPos(tag, "Pedestal").ifPresent(pos -> this.pedestal = pos);
        this.orbit = tag.getDouble("Orbit");
        this.roarPending = !tag.contains("RoarPending") || tag.getBoolean("RoarPending");
        if (this.hasCustomName()) this.bossEvent.setName(this.getDisplayName());
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return SoundEvents.ELDER_GUARDIAN_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ELDER_GUARDIAN_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ELDER_GUARDIAN_DEATH;
    }

    @Override
    protected float getSoundVolume() {
        return 3.0F;
    }
}
