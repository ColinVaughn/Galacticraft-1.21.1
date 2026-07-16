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

package dev.galacticraft.mod.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.galacticraft.common.accessor.EntityAccessor;
import dev.galacticraft.mod.content.entity.damage.GCDamageTypes;
import dev.galacticraft.mod.events.GCEventHandlers;
import dev.galacticraft.mod.misc.footprint.Footprint;
import dev.galacticraft.mod.tag.*;
import dev.galacticraft.mod.util.CustomFluidPush;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(Entity.class)
public abstract class EntityMixin implements EntityAccessor {
    private @Unique double distanceSinceLastStep;
    private @Unique int lastStep = -1;
    private @Unique int timeInAcid = 0;

    private static final @Unique int GALACTICRAFT_IN_OIL = 1;
    private static final @Unique int GALACTICRAFT_IN_FUEL = 1 << 1;
    private static final @Unique int GALACTICRAFT_IN_SULFURIC_ACID = 1 << 2;
    private static final @Unique double GALACTICRAFT_FLUID_PUSH = 0.0028D;

    @Shadow
    public abstract Vec3 getDeltaMovement();

    @Shadow
    private float yRot;

    @Shadow
    private float xRot;

    @Shadow
    private Level level;

    @Shadow
    @Final
    private Object2DoubleMap<TagKey<Fluid>> fluidHeight;

    @Shadow
    public abstract boolean isOnFire();

    @Shadow
    private Vec3 position;

    @Shadow
    public abstract boolean isAlwaysTicking();

    @Shadow
    public abstract DamageSources damageSources();

    @Shadow
    public abstract boolean hurt(DamageSource source, float amount);

    @Shadow
    public abstract void playSound(SoundEvent sound, float volume, float pitch);

    @Shadow
    public abstract double getX();

    @Shadow
    public abstract double getY();

    @Shadow
    public abstract double getZ();

    @Shadow
    public abstract Level level();

    @Shadow
    public abstract @Nullable Entity getVehicle();

    @Shadow
    public abstract float getYRot();

    @Shadow
    public abstract UUID getUUID();

    @Shadow
    @Final
    protected RandomSource random;

    @Shadow
    public abstract Vec3 position();

    @Shadow
    public abstract EntityType<?> getType();

    @Shadow
    @Final
    protected abstract void discard();

    @Inject(method = "updateInWaterStateAndDoWaterCurrentPushing", at = @At("TAIL"))
    private void checkWaterStateGC(CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;
        boolean invulnerable = entity instanceof Player player && player.getAbilities().invulnerable;
        int customFluids = this.galacticraft$updateCustomFluidHeightAndDoFluidPushing(entity);
        if ((customFluids & (GALACTICRAFT_IN_OIL | GALACTICRAFT_IN_FUEL)) != 0) {
            if (this.isOnFire()) {
                level.explode(entity, position.x, position.y, position.z, 0f, Level.ExplosionInteraction.NONE);
                if (!invulnerable) {
                    this.hurt(this.damageSources().source(GCDamageTypes.OIL_BOOM), 20.0F);
                }
            }
        }

        if ((customFluids & GALACTICRAFT_IN_SULFURIC_ACID) != 0) {
            // The entity enters an acid fluid, this entity needs to take damage
            if (!invulnerable && !this.getType().is(GCEntityTypeTags.IMMUNE_TO_ACID)) {
                boolean damage = this.timeInAcid >= 30;
                boolean playSound = true;
                if (damage && ((Object) this instanceof Projectile || this.getType().is(GCEntityTypeTags.SENSITIVE_TO_ACID))) {
                    this.discard();
                    return;
                }

                if ((Object) this instanceof ItemEntity itemEntity) {
                    ItemStack itemStack = itemEntity.getItem();
                    if (itemStack.is(GCItemTags.ACID_RESISTANT)) {
                        damage = false;
                        playSound = false;
                    } else if (damage) {
                        damage = !GCEventHandlers.sulfuricAcidTransformItem(itemEntity, itemStack);
                    }
                } else {
                    damage = true;
                }

                if (damage) {
                    this.hurt(this.damageSources().source(GCDamageTypes.SULFURIC_ACID), 2.0F);
                }
                if (playSound && this.timeInAcid % 5 == 0) {
                    this.sulfuricAcidEffects();
                }
            }
            ++this.timeInAcid;
        } else {
            this.timeInAcid = 0;
        }
    }

    @Unique
    private int galacticraft$updateCustomFluidHeightAndDoFluidPushing(Entity entity) {
        if (entity.touchingUnloadedChunk()) return 0;

        AABB bounds = entity.getBoundingBox().deflate(0.001D);
        int minX = Mth.floor(bounds.minX);
        int maxX = Mth.ceil(bounds.maxX);
        int minY = Mth.floor(bounds.minY);
        int maxY = Mth.ceil(bounds.maxY);
        int minZ = Mth.floor(bounds.minZ);
        int maxZ = Mth.ceil(bounds.maxZ);
        boolean pushedByFluid = entity.isPushedByFluid();

        double oilHeight = 0.0D;
        double fuelHeight = 0.0D;
        double acidHeight = 0.0D;
        boolean inOil = false;
        boolean inFuel = false;
        boolean inAcid = false;
        Vec3 oilFlow = Vec3.ZERO;
        Vec3 fuelFlow = Vec3.ZERO;
        Vec3 acidFlow = Vec3.ZERO;
        int oilFlowSamples = 0;
        int fuelFlowSamples = 0;
        int acidFlowSamples = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int x = minX; x < maxX; x++) {
            for (int y = minY; y < maxY; y++) {
                for (int z = minZ; z < maxZ; z++) {
                    pos.set(x, y, z);
                    FluidState state = this.level.getFluidState(pos);
                    boolean oil = state.is(GCFluidTags.OIL);
                    boolean fuel = state.is(GCFluidTags.FUEL);
                    boolean acid = state.is(GCFluidTags.SULFURIC_ACID);
                    if (!oil && !fuel && !acid) continue;

                    double surface = y + state.getHeight(this.level, pos);
                    if (surface < bounds.minY) continue;

                    double depth = surface - bounds.minY;
                    Vec3 flow = pushedByFluid ? state.getFlow(this.level, pos) : Vec3.ZERO;
                    if (oil) {
                        inOil = true;
                        oilHeight = Math.max(depth, oilHeight);
                        if (pushedByFluid) {
                            oilFlow = oilFlow.add(oilHeight < 0.4D ? flow.scale(oilHeight) : flow);
                            oilFlowSamples++;
                        }
                    }
                    if (fuel) {
                        inFuel = true;
                        fuelHeight = Math.max(depth, fuelHeight);
                        if (pushedByFluid) {
                            fuelFlow = fuelFlow.add(fuelHeight < 0.4D ? flow.scale(fuelHeight) : flow);
                            fuelFlowSamples++;
                        }
                    }
                    if (acid) {
                        inAcid = true;
                        acidHeight = Math.max(depth, acidHeight);
                        if (pushedByFluid) {
                            acidFlow = acidFlow.add(acidHeight < 0.4D ? flow.scale(acidHeight) : flow);
                            acidFlowSamples++;
                        }
                    }
                }
            }
        }

        // Match the previous oil-before-fuel short circuit.
        this.fluidHeight.put(GCFluidTags.OIL, oilHeight);
        this.galacticraft$applyFluidPush(entity, oilFlow, oilFlowSamples);
        if (!inOil) {
            this.fluidHeight.put(GCFluidTags.FUEL, fuelHeight);
            this.galacticraft$applyFluidPush(entity, fuelFlow, fuelFlowSamples);
        }
        this.fluidHeight.put(GCFluidTags.SULFURIC_ACID, acidHeight);
        this.galacticraft$applyFluidPush(entity, acidFlow, acidFlowSamples);

        return (inOil ? GALACTICRAFT_IN_OIL : 0)
                | (inFuel ? GALACTICRAFT_IN_FUEL : 0)
                | (inAcid ? GALACTICRAFT_IN_SULFURIC_ACID : 0);
    }

    @Unique
    private void galacticraft$applyFluidPush(Entity entity, Vec3 flow, int samples) {
        Vec3 push = CustomFluidPush.calculate(
                flow,
                samples,
                entity instanceof Player,
                entity.getDeltaMovement(),
                GALACTICRAFT_FLUID_PUSH
        );
        if (push != Vec3.ZERO) entity.setDeltaMovement(entity.getDeltaMovement().add(push));
    }

    @Unique
    private void sulfuricAcidEffects() {
        this.playSound(SoundEvents.LAVA_EXTINGUISH, 0.7F, 1.6F + (level.getRandom().nextFloat() - level.getRandom().nextFloat()) * 0.4F);
        for (int i = 0; i < 4; i++) {
            level.addParticle(ParticleTypes.WHITE_SMOKE, true, this.getX() + level.random.nextDouble() - 0.5, Mth.ceil(this.getY()), this.getZ() + level.random.nextDouble() - 0.5, 0.0D, 0.0D, 0.0D);
        }
    }

    // GC 4 ticks footprints on the client and server, however we will just do it on the server
    @Inject(method = "move", at = @At("HEAD"))
    private void tickFootprints(MoverType type, Vec3 motion, CallbackInfo ci) {
        if (!this.getType().is(GCEntityTypeTags.HAS_FOOTPRINTS)) {
            return;
        } else if ((Object) this instanceof Player player && player.getAbilities().flying) {
            return;
        } else if (this.getVehicle() != null) {
            return;
        }
        // The entity has footprints, is not flying and is not riding anything

        double motionSqrd = motion.horizontalDistanceSqr();
        Holder<DimensionType> dimensionType = this.level.dimensionTypeRegistration();

        // Check that the entity is moving fast enough and is in a footprint dimension
        if (motionSqrd > 0.001D && dimensionType.is(GCDimensionTypeTags.FOOTPRINTS_DIMENSIONS)) {
            // If it has been long enough since the last step
            if (this.galacticraft$getDistanceSinceLastStep() > 0.35D) {
                float rotation = this.getYRot() * Mth.DEG_TO_RAD;

                // Set the footprint position to the block below
                Vector3d pos = new Vector3d(
                        this.getX() + this.galacticraft$getLastStep() * Mth.cos(rotation) * 0.25D,
                        Math.floor(this.getY()),
                        this.getZ() + this.galacticraft$getLastStep() * Mth.sin(rotation) * 0.25D
                );
                pos = Footprint.getFootprintPosition(this.level, rotation - Mth.PI, pos, this.position());

                BlockPos blockPos = new BlockPos(Mth.floor(pos.x), Mth.floor(pos.y - 0.05D), Mth.floor(pos.z));
                BlockState state = this.level.getBlockState(blockPos);

                // If the block below is the moon block
                if (state.is(GCBlockTags.FOOTPRINTS)) {
                    long chunkKey = ChunkPos.asLong(SectionPos.blockToSectionCoord(pos.x), SectionPos.blockToSectionCoord(pos.z));
                    short age = (short) (this.level.getGameTime() % 20);
                    this.level.galacticraft$getFootprintManager().addFootprint(chunkKey, new Footprint(dimensionType.unwrapKey().get().location(), pos, rotation, age, this.getUUID()));
                }

                // Change the sign of the lastStep variable
                this.galacticraft$swapLastStep();
                this.galacticraft$setDistanceSinceLastStep(0);
            } else {
                this.galacticraft$setDistanceSinceLastStep(this.galacticraft$getDistanceSinceLastStep() + motionSqrd);
            }
        }
    }

    @Override
    public double galacticraft$getDistanceSinceLastStep() {
        return this.distanceSinceLastStep;
    }

    @Override
    public void galacticraft$setDistanceSinceLastStep(double distanceSinceLastStep) {
        this.distanceSinceLastStep = distanceSinceLastStep;
    }

    @Override
    public int galacticraft$getLastStep() {
        return this.lastStep;
    }

    @Override
    public void galacticraft$swapLastStep() {
        this.lastStep = -this.lastStep;
    }

    @WrapOperation(method = "checkBelowWorld", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;onBelowWorld()V"))
    private void galacticraft$onBelowWorld(Entity entity, Operation<Void> original) {
        if (!GCEventHandlers.tryReenterAtmosphere(entity)) original.call(entity);
    }
}
