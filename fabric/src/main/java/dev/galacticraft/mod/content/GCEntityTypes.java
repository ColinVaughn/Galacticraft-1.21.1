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

package dev.galacticraft.mod.content;

import dev.galacticraft.api.entity.attribute.GcApiEntityAttributes;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.Constant.Entity;
import dev.galacticraft.mod.content.entity.*;
import dev.galacticraft.mod.content.entity.boss.CreeperBoss;
import dev.galacticraft.mod.content.entity.boss.SkeletonBoss;
import dev.galacticraft.mod.content.entity.boss.SpiderBoss;
import dev.galacticraft.mod.content.entity.vehicle.AstroMinerEntity;
import dev.galacticraft.mod.content.entity.vehicle.CargoRocketEntity;
import dev.galacticraft.mod.content.entity.vehicle.Buggy;
import dev.galacticraft.mod.content.entity.vehicle.RocketEntity;
import dev.galacticraft.mod.content.entity.vehicle.LanderEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.function.BiConsumer;

public class GCEntityTypes {
    public static final GCRegistry<EntityType<?>> ENTITIES = new GCRegistry<>(BuiltInRegistries.ENTITY_TYPE);
    public static final EntityType<MoonVillagerEntity> MOON_VILLAGER = ENTITIES.register(Entity.MOON_VILLAGER, EntityType.Builder.of(MoonVillagerEntity::new, MobCategory.MISC)
            .sized(0.6F, 2.5F)
            .eyeHeight(1.72F)
            .clientTrackingRange(10)
            .build(Constant.id(Entity.MOON_VILLAGER).toString()));
    public static final EntityType<EvolvedZombieEntity> EVOLVED_ZOMBIE = ENTITIES.register(Entity.EVOLVED_ZOMBIE, EntityType.Builder.of(EvolvedZombieEntity::new, MobCategory.MONSTER)
            .sized(0.6F, 1.95F)
            .eyeHeight(1.74F)
            .passengerAttachments(2.0125F)
            .ridingOffset(-0.7F)
            .clientTrackingRange(8)
            .build(Constant.id(Entity.EVOLVED_ZOMBIE).toString()));
    public static final EntityType<EvolvedCreeperEntity> EVOLVED_CREEPER = ENTITIES.register(Entity.EVOLVED_CREEPER, EntityType.Builder.of(EvolvedCreeperEntity::new, MobCategory.MONSTER)
            .sized(0.6F, 1.7F)
            .clientTrackingRange(8)
            .build(Constant.id(Entity.EVOLVED_CREEPER).toString()));
    public static final EntityType<EvolvedSkeletonEntity> EVOLVED_SKELETON = ENTITIES.register(Entity.EVOLVED_SKELETON, EntityType.Builder.of(EvolvedSkeletonEntity::new, MobCategory.MONSTER)
            .sized(0.6F, 1.99F)
            .eyeHeight(1.74F)
            .ridingOffset(-0.7F)
            .clientTrackingRange(8)
            .build(Constant.id(Entity.EVOLVED_SKELETON).toString()));
    public static final EntityType<EvolvedSpiderEntity> EVOLVED_SPIDER = ENTITIES.register(Entity.EVOLVED_SPIDER, EntityType.Builder.of(EvolvedSpiderEntity::new, MobCategory.MONSTER)
            .sized(1.4F, 0.9F)
            .eyeHeight(0.65F)
            .passengerAttachments(0.765F)
            .clientTrackingRange(8)
            .build(Constant.id(Entity.EVOLVED_SPIDER).toString()));
    public static final EntityType<EvolvedEndermanEntity> EVOLVED_ENDERMAN = ENTITIES.register(Entity.EVOLVED_ENDERMAN, EntityType.Builder.of(EvolvedEndermanEntity::new, MobCategory.MONSTER)
            .sized(0.6F, 2.9F)
            .eyeHeight(2.55F)
            .passengerAttachments(2.80625F)
            .clientTrackingRange(8)
            .build(Constant.id(Entity.EVOLVED_ENDERMAN).toString()));
    public static final EntityType<EvolvedWitchEntity> EVOLVED_WITCH = ENTITIES.register(Entity.EVOLVED_WITCH, EntityType.Builder.of(EvolvedWitchEntity::new, MobCategory.MONSTER)
            .sized(0.6F, 1.95F)
            .eyeHeight(1.62F)
            .passengerAttachments(2.2625F)
            .clientTrackingRange(8)
            .build(Constant.id(Entity.EVOLVED_WITCH).toString()));
    public static final EntityType<EvolvedPillagerEntity> EVOLVED_PILLAGER = ENTITIES.register(Entity.EVOLVED_PILLAGER, EntityType.Builder.of(EvolvedPillagerEntity::new, MobCategory.MONSTER)
            // .canSpawnFarFromPlayer()
            .sized(0.6F, 1.95F)
            .passengerAttachments(2.0F)
            .ridingOffset(-0.6F)
            .clientTrackingRange(8)
            .build(Constant.id(Entity.EVOLVED_PILLAGER).toString()));
    public static final EntityType<EvolvedEvokerEntity> EVOLVED_EVOKER = ENTITIES.register(Entity.EVOLVED_EVOKER, EntityType.Builder.of(EvolvedEvokerEntity::new, MobCategory.MONSTER)
            .sized(0.6F, 1.95F)
            .passengerAttachments(2.0F)
            .ridingOffset(-0.6F)
            .clientTrackingRange(8)
            .build(Constant.id(Entity.EVOLVED_EVOKER).toString()));
    public static final EntityType<EvolvedVindicatorEntity> EVOLVED_VINDICATOR = ENTITIES.register(Entity.EVOLVED_VINDICATOR, EntityType.Builder.of(EvolvedVindicatorEntity::new, MobCategory.MONSTER)
            .sized(0.6F, 1.95F)
            .passengerAttachments(2.0F)
            .ridingOffset(-0.6F)
            .clientTrackingRange(8)
            .build(Constant.id(Entity.EVOLVED_VINDICATOR).toString()));
    public static final EntityType<GazerEntity> GAZER = ENTITIES.register(Entity.GAZER, EntityType.Builder.of(GazerEntity::new, MobCategory.MONSTER)
            .sized(1.5F, 2.625F)
            .eyeHeight(1.6F)
            .clientTrackingRange(8)
            .build(Constant.id(Entity.GAZER).toString()));
    public static final EntityType<RumblerEntity> RUMBLER = ENTITIES.register(Entity.RUMBLER, EntityType.Builder.of(RumblerEntity::new, MobCategory.MONSTER)
            .sized(1.5F, 1.5F)
            .eyeHeight(0.8F)
            .clientTrackingRange(8)
            .build(Constant.id(Entity.RUMBLER).toString()));
    public static final EntityType<CometCubeEntity> COMET_CUBE = ENTITIES.register(Entity.COMET_CUBE, EntityType.Builder.of(CometCubeEntity::new, MobCategory.MONSTER)
            .sized(0.75F, 0.75F)
            .clientTrackingRange(8)
            .build(Constant.id(Entity.COMET_CUBE).toString()));
    public static final EntityType<OliGrubEntity> OLI_GRUB = ENTITIES.register(Entity.OLI_GRUB, EntityType.Builder.of(OliGrubEntity::new, MobCategory.CREATURE)
            .sized(0.75F, 0.375F)
            .clientTrackingRange(8)
            .build(Constant.id(Entity.OLI_GRUB).toString()));
    public static final EntityType<GreyEntity> GREY = ENTITIES.register(Entity.GREY, EntityType.Builder.of(GreyEntity::new, MobCategory.CREATURE)
            .sized(0.6F, 1.55F)
            .eyeHeight(1.25F)
            .clientTrackingRange(8)
            .build(Constant.id(Entity.GREY).toString()));
    public static final EntityType<ArchGreyEntity> ARCH_GREY = ENTITIES.register(Entity.ARCH_GREY, EntityType.Builder.of(ArchGreyEntity::new, MobCategory.CREATURE)
            .sized(0.6F, 1.55F)
            .eyeHeight(1.25F)
            .clientTrackingRange(8)
            .build(Constant.id(Entity.ARCH_GREY).toString()));

    public static final EntityType<FallingMeteorEntity> FALLING_METEOR = ENTITIES.register(Entity.FALLING_METEOR, EntityType.Builder.of(FallingMeteorEntity::new, MobCategory.MISC)
            .clientTrackingRange(16)
            .updateInterval(5)
            .sized(1.0f, 1.0f)
            .eyeHeight(0.5f)
            .build(Constant.id(Entity.FALLING_METEOR).toString()));

    public static final EntityType<BubbleEntity> BUBBLE = ENTITIES.register(Entity.BUBBLE, EntityType.Builder.of(BubbleEntity::new, MobCategory.MISC)
            .fireImmune()
            .sized(0, 0)
            .noSave()
            .noSummon()
            .build(Constant.id(Entity.BUBBLE).toString()));
    public static final EntityType<RocketEntity> ROCKET = ENTITIES.register(Entity.ROCKET, velocityUpdates(EntityType.Builder.of(RocketEntity::new, MobCategory.MISC)
            .clientTrackingRange(32)
            .updateInterval(2), false)
            .sized(2.3F, 5.25F)
            .eyeHeight(2.525F)
            .build(Constant.id(Entity.ROCKET).toString())); //PLAYER VALUES
    public static final EntityType<LanderEntity> LANDER = ENTITIES.register(Entity.LANDER, EntityType.Builder.<LanderEntity>of(LanderEntity::new, MobCategory.MISC)
            .clientTrackingRange(32)
            .sized(2.5F, 4F)
            .eyeHeight(2.625F)
            .fireImmune()
            .noSummon()
            .build(Constant.id(Entity.LANDER).toString()));
    public static final EntityType<Buggy> BUGGY = ENTITIES.register(Entity.BUGGY, velocityUpdates(EntityType.Builder.of(Buggy::new, MobCategory.MISC)
            .clientTrackingRange(150)
            .updateInterval(5), true)
            .sized(1.4F, 0.6F)
            .fireImmune()
            .build(Constant.id(Entity.BUGGY).toString()));
    public static final EntityType<AstroMinerEntity> ASTRO_MINER = ENTITIES.register(Entity.ASTRO_MINER, velocityUpdates(EntityType.Builder.of(AstroMinerEntity::new, MobCategory.MISC)
            .clientTrackingRange(150)
            .updateInterval(5), true)
            .sized(3.0F, 3.0F)
            .fireImmune()
            .build(Constant.id(Entity.ASTRO_MINER).toString()));
    public static final EntityType<CargoRocketEntity> CARGO_ROCKET = ENTITIES.register(Entity.CARGO_ROCKET, velocityUpdates(EntityType.Builder.of(CargoRocketEntity::new, MobCategory.MISC)
            .clientTrackingRange(150)
            .updateInterval(5), true)
            .sized(0.98F, 2.0F)
            .fireImmune()
            .build(Constant.id(Entity.CARGO_ROCKET).toString()));
    public static final EntityType<ParachestEntity> PARACHEST = ENTITIES.register(Entity.PARACHEST, EntityType.Builder.<ParachestEntity>of(ParachestEntity::new, MobCategory.MISC)
            .clientTrackingRange(150)
            .updateInterval(5)
            .sized(1.0F, 1.0F)
            .build(Constant.id(Entity.PARACHEST).toString()));

    public static final EntityType<ThrowableMeteorChunkEntity> THROWABLE_METEOR_CHUNK = ENTITIES.register(Constant.Item.THROWABLE_METEOR_CHUNK, EntityType.Builder.<ThrowableMeteorChunkEntity>of(ThrowableMeteorChunkEntity::new, MobCategory.MISC)
            .sized(0.25F, 0.25F)
            .build(Constant.id(Constant.Item.THROWABLE_METEOR_CHUNK).toString()));
    // Bosses
    public static final EntityType<SkeletonBoss> SKELETON_BOSS = ENTITIES.register(Entity.EVOLVED_SKELETON_BOSS, velocityUpdates(EntityType.Builder.of(SkeletonBoss::new, MobCategory.MONSTER)
            .sized(1.5F, 4.0F)
            .fireImmune()
            .clientTrackingRange(80)
            .updateInterval(3), true)
            .build(Constant.id(Entity.EVOLVED_SKELETON_BOSS).toString()));
    public static final EntityType<CreeperBoss> CREEPER_BOSS = ENTITIES.register(Entity.CREEPER_BOSS, velocityUpdates(EntityType.Builder.of(CreeperBoss::new, MobCategory.MONSTER)
            .sized(2.0F, 5.1F)
            .fireImmune()
            .clientTrackingRange(80)
            .updateInterval(3), true)
            .build(Constant.id(Entity.CREEPER_BOSS).toString()));
    public static final EntityType<SpiderBoss> SPIDER_BOSS = ENTITIES.register(Entity.SPIDER_BOSS, velocityUpdates(EntityType.Builder.of(SpiderBoss::new, MobCategory.MONSTER)
            .sized(3.5F, 2.25F)
            .fireImmune()
            .clientTrackingRange(80)
            .updateInterval(3), true)
            .build(Constant.id(Entity.SPIDER_BOSS).toString()));

    private static <T extends net.minecraft.world.entity.Entity> EntityType.Builder<T> velocityUpdates(
            EntityType.Builder<T> builder, boolean enabled) {
        try {
            builder.getClass().getMethod("alwaysUpdateVelocity", boolean.class).invoke(builder, enabled);
        } catch (ReflectiveOperationException fabricMissing) {
            try {
                builder.getClass().getMethod("setShouldReceiveVelocityUpdates", boolean.class).invoke(builder, enabled);
            } catch (ReflectiveOperationException neoForgeMissing) {
                throw new IllegalStateException("No entity velocity update configuration method is available", neoForgeMissing);
            }
        }
        return builder;
    }

    public static void register() {
        EntityAttributePlatformHooks.registerAttributes();
    }

    public static void registerAttributes(BiConsumer<EntityType<? extends LivingEntity>, AttributeSupplier.Builder> register) {
        register.accept(MOON_VILLAGER, MoonVillagerEntity.createMobAttributes().add(GcApiEntityAttributes.CAN_BREATHE_IN_SPACE, 1.0D));
        register.accept(EVOLVED_ZOMBIE, EvolvedZombieEntity.createAttributes().add(GcApiEntityAttributes.CAN_BREATHE_IN_SPACE, 1.0D).add(Attributes.MOVEMENT_SPEED, 0.35D).add(Attributes.MAX_HEALTH, 25.0D));
        register.accept(EVOLVED_CREEPER, EvolvedCreeperEntity.createAttributes().add(GcApiEntityAttributes.CAN_BREATHE_IN_SPACE, 1.0D).add(Attributes.MAX_HEALTH, 25.0D));
        register.accept(EVOLVED_SKELETON, EvolvedSkeletonEntity.createAttributes().add(GcApiEntityAttributes.CAN_BREATHE_IN_SPACE, 1.0D).add(Attributes.MAX_HEALTH, 25.0D));
        register.accept(EVOLVED_SPIDER, EvolvedSpiderEntity.createAttributes().add(GcApiEntityAttributes.CAN_BREATHE_IN_SPACE, 1.0D).add(Attributes.MAX_HEALTH, 20.0D));
        register.accept(EVOLVED_ENDERMAN, EvolvedEndermanEntity.createAttributes().add(GcApiEntityAttributes.CAN_BREATHE_IN_SPACE, 1.0D).add(Attributes.MAX_HEALTH, 50.0D));
        register.accept(EVOLVED_WITCH, EvolvedWitchEntity.createAttributes().add(GcApiEntityAttributes.CAN_BREATHE_IN_SPACE, 1.0D).add(Attributes.MAX_HEALTH, 32.0D));
        register.accept(EVOLVED_PILLAGER, EvolvedPillagerEntity.createAttributes().add(GcApiEntityAttributes.CAN_BREATHE_IN_SPACE, 1.0D).add(Attributes.MAX_HEALTH, 30.0D));
        register.accept(EVOLVED_EVOKER, EvolvedEvokerEntity.createAttributes().add(GcApiEntityAttributes.CAN_BREATHE_IN_SPACE, 1.0D).add(Attributes.MAX_HEALTH, 30.0D));
        register.accept(EVOLVED_VINDICATOR, EvolvedVindicatorEntity.createAttributes().add(GcApiEntityAttributes.CAN_BREATHE_IN_SPACE, 1.0D).add(Attributes.MAX_HEALTH, 30.0D));
        register.accept(GAZER, GazerEntity.createAttributes());
        register.accept(RUMBLER, RumblerEntity.createAttributes());
        register.accept(COMET_CUBE, CometCubeEntity.createAttributes());
        register.accept(OLI_GRUB, OliGrubEntity.createAttributes());
        register.accept(GREY, GreyEntity.createAttributes());
        register.accept(ARCH_GREY, ArchGreyEntity.createAttributes());
        register.accept(SKELETON_BOSS, SkeletonBoss.createAttributes().add(GcApiEntityAttributes.CAN_BREATHE_IN_SPACE, 1.0D));
        register.accept(CREEPER_BOSS, CreeperBoss.createAttributes().add(GcApiEntityAttributes.CAN_BREATHE_IN_SPACE, 1.0D));
        register.accept(SPIDER_BOSS, SpiderBoss.createAttributes().add(GcApiEntityAttributes.CAN_BREATHE_IN_SPACE, 1.0D));
    }
}
