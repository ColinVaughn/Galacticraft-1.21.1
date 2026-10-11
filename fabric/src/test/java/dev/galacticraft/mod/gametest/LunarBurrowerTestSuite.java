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

package dev.galacticraft.mod.gametest;

import dev.galacticraft.mod.content.*;
import dev.galacticraft.mod.content.entity.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import dev.galacticraft.mod.content.item.GCItems;
import dev.galacticraft.mod.content.block.environment.LunarBurrowerEggBlock;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.nbt.CompoundTag;

public final class LunarBurrowerTestSuite implements GalacticraftGameTest {
    public static final String ARENA = "galacticraft-test:burrower_arena";
    private void use(LunarBurrowerEntity worm, ServerPlayer player, ItemStack stack) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        worm.mobInteract(player, InteractionHand.MAIN_HAND);
    }
    private LunarBurrowerEntity companion(GameTestHelper test, ServerPlayer player) {
        LunarBurrowerEntity worm = worm(test);
        worm.hatch();
        use(worm, player, new ItemStack(GCItems.MOON_CHEESE_CURD));
        return worm;
    }
    private void driveTunnel(GameTestHelper test, float pitch) {
        var owner = makeSurvivalServerPlayer(test);
        var pet = companion(test, owner); pet.setGrowingTicks(0);
        BlockPos start = test.absolutePos(new BlockPos(10, 6, 14));
        for (int x = 5; x <= 15; x++) for (int z = 1; z <= 18; z++) for (int y = 0; y <= 17; y++)
            test.getLevel().setBlock(test.absolutePos(new BlockPos(x, y, z)), GCBlocks.MOON_ROCK.defaultBlockState(), 2);
        pet.moveTo(start.getX() + 0.5, start.getY(), start.getZ() + 0.5, 180, pitch);
        owner.setPos(pet.position()); owner.setYRot(180); owner.setXRot(pitch);
        use(pet, owner, new ItemStack(Items.SADDLE)); use(pet, owner, new ItemStack(Items.CHEST)); use(pet, owner, ItemStack.EMPTY);
        dev.galacticraft.mod.network.c2s.ToggleBurrowerTunnelPayload.INSTANCE.apply(owner);
        owner.zza = 1;
        for (int step = 0; step < 44; step++) {
            owner.setYRot(180); owner.setXRot(pitch);
            pet.aiStep();
            // A real player's client normally supplies this movement; simulate that input on the test server.
            pet.travel(Vec3.ZERO); pet.tick();
        }
        test.assertTrue(pet.getZ() < start.getZ() - 3, "Mount did not dig forward");
        if (pitch > 0) test.assertTrue(pet.getY() < start.getY() - 3, "Looking down did not make a descending tunnel: " + pet.position() + ", start=" + start + ", pitch=" + pet.getXRot());
        else if (pitch < 0) test.assertTrue(pet.getY() > start.getY() + 3, "Looking up did not make an ascending tunnel: " + pet.position() + ", start=" + start + ", pitch=" + pet.getXRot());
        else test.assertTrue(Math.abs(pet.getY() - start.getY()) < 0.05, "Straight digging changed height");
        test.assertTrue(test.getLevel().getBlockState(pet.blockPosition()).isAir(), "Head remained inside rock");
        test.assertTrue(test.getLevel().getBlockState(BlockPos.containing(pet.getSaddlePosition(1).add(0, 1.5, 0))).isAir(), "Tunnel ceiling clipped the rider");
        if (pitch != 0) test.assertTrue(Math.abs(pet.bodyPosition(1).y - pet.getY()) > 0.5, "Body stayed flat while tunneling");
        owner.stopRiding(); pet.aiStep();
        test.assertTrue(!pet.isTunnelMode(), "Dismount left excavation enabled");
        owner.discard(); pet.discard(); test.succeed();
    }
    @GameTest(template = ARENA, timeoutTicks = 30)
    public void riderCanDigStraight(GameTestHelper test) { driveTunnel(test, 0); }
    @GameTest(template = ARENA, timeoutTicks = 30)
    public void riderCanDigDown(GameTestHelper test) { driveTunnel(test, 45); }
    @GameTest(template = ARENA, timeoutTicks = 30)
    public void riderCanDigUp(GameTestHelper test) { driveTunnel(test, -45); }

    @GameTest(template = ARENA, timeoutTicks = 30)
    public void riderTunnelFitsGiantCargoAndLeavesItsFloor(GameTestHelper test) {
        var owner = makeSurvivalServerPlayer(test);
        var pet = companion(test, owner); pet.setGrowthAge(dev.galacticraft.mod.util.BurrowerGrowth.MAX_AGE);
        owner.setPos(pet.position()); owner.setYRot(180);
        use(pet, owner, new ItemStack(Items.SADDLE)); use(pet, owner, new ItemStack(Items.CHEST)); use(pet, owner, ItemStack.EMPTY);
        BlockPos start = pet.blockPosition();
        for (int x = -6; x <= 6; x++) for (int z = -3; z <= -1; z++) for (int y = 0; y <= 8; y++)
            test.getLevel().setBlock(start.offset(x, y, z), GCBlocks.MOON_ROCK.defaultBlockState(), 2);
        dev.galacticraft.mod.network.c2s.ToggleBurrowerTunnelPayload.INSTANCE.apply(owner);
        owner.zza = 1; pet.aiStep();
        test.assertTrue(test.getLevel().getBlockState(start.offset(5, 6, -2)).isAir()
                && test.getLevel().getBlockState(start.offset(-5, 6, -2)).isAir(), "Giant tunnel clipped its cargo or rider");
        test.assertTrue(test.getLevel().getBlockState(start.offset(5, -1, -2)).is(GCBlocks.MOON_ROCK), "Straight giant digging removed the floor");
        owner.stopRiding(); owner.discard(); pet.discard(); test.succeed();
    }

    @GameTest(template = ARENA, timeoutTicks = 30)
    public void riderTunnelingRequiresTheToggleAndProtectsContainersAndBedrock(GameTestHelper test) {
        ServerPlayer owner = makeSurvivalServerPlayer(test), stranger = makeSurvivalServerPlayer(test);
        var pet = companion(test, owner); pet.setGrowingTicks(0);
        owner.setPos(pet.position()); owner.setYRot(180);
        use(pet, owner, new ItemStack(Items.SADDLE)); use(pet, owner, ItemStack.EMPTY);
        BlockPos rock = pet.blockPosition().north(), protectedPos = rock.north();
        test.getLevel().setBlock(rock, GCBlocks.MOON_ROCK.defaultBlockState(), 2);
        owner.zza = 1; pet.aiStep();
        test.assertTrue(test.getLevel().getBlockState(rock).is(GCBlocks.MOON_ROCK), "Normal riding excavated terrain");
        pet.toggleTunnelMode(stranger);
        test.assertTrue(!pet.isTunnelMode(), "Non-rider enabled excavation");
        var toggle = dev.galacticraft.mod.network.c2s.ToggleBurrowerTunnelPayload.INSTANCE;
        toggle.apply(owner);
        test.assertTrue(pet.isTunnelMode(), "Rider toggle packet did not enable tunneling");
        for (var block : new net.minecraft.world.level.block.Block[]{Blocks.CHEST, Blocks.BEDROCK}) {
            test.getLevel().setBlock(protectedPos, block.defaultBlockState(), 2);
            pet.aiStep(); pet.travel(Vec3.ZERO);
            test.assertTrue(test.getLevel().getBlockState(protectedPos).is(block) && test.getLevel().getBlockState(rock).is(GCBlocks.MOON_ROCK), "Protected slice was partially excavated");
        }
        test.getLevel().setBlock(protectedPos, Blocks.AIR.defaultBlockState(), 2);
        var rule = test.getLevel().getGameRules().getRule(GameRules.RULE_MOBGRIEFING);
        boolean previous = rule.get();
        try {
            rule.set(false, test.getLevel().getServer()); pet.aiStep();
            test.assertTrue(test.getLevel().getBlockState(rock).is(GCBlocks.MOON_ROCK), "Rider bypassed mobGriefing");
        } finally { rule.set(previous, test.getLevel().getServer()); }
        toggle.apply(owner);
        test.assertTrue(!pet.isTunnelMode(), "Second key press did not disable tunneling");
        owner.stopRiding(); owner.discard(); stranger.discard(); pet.discard(); test.succeed();
    }
    @GameTest(template = ARENA, timeoutTicks = 35)
    public void ridingUsesAWaveAndKeepsTheSeatAndDamageTargetsAttached(GameTestHelper test) {
        var owner = makeSurvivalServerPlayer(test);
        var pet = companion(test, owner); pet.setGrowingTicks(0);
        owner.setPos(pet.position());
        use(pet, owner, new ItemStack(Items.SADDLE)); use(pet, owner, ItemStack.EMPTY);
        owner.zza = 1;
        runAt(test, 10, () -> {
            test.assertTrue(pet.getBurrowerState() == BurrowerState.RIDE, "Riding still used the walking clip");
            test.assertTrue(pet.bodyPosition(2).y > pet.getY(), "Riding body remained flat");
            Vec3 seated = owner.position().add(owner.getVehicleAttachmentPoint(pet));
            test.assertTrue(seated.distanceTo(pet.getSaddlePosition(1)) < 0.05, "Rider drifted off the animated saddle");
            var parts = test.getLevel().getEntitiesOfClass(BurrowerBodyEntity.class, pet.getBoundingBox().inflate(20), part -> part.owner() == pet);
            test.assertTrue(parts.size() == 7, "Riding lost damage targets");
            for (var part : parts) {
                double nearest = Double.MAX_VALUE;
                for (int section = 0; section <= pet.getBodySectionCount(); section++)
                    nearest = Math.min(nearest, part.position().distanceTo(pet.bodyPosition(section)));
                test.assertTrue(nearest < 0.05, "Damage target missed the animated centerline");
            }
            owner.zza = 0; pet.setDeltaMovement(Vec3.ZERO);
        });
        runFinalTaskAt(test, 26, () -> {
            test.assertTrue(pet.getBurrowerState() == BurrowerState.IDLE && owner.getVehicle() == pet, "Stopping did not settle the mount");
            owner.stopRiding(); owner.discard(); pet.discard();
        });
    }
    @GameTest(template = ARENA, timeoutTicks = 1240)
    public void placedEggHatchesIntoPersistentJuvenile(GameTestHelper test) {
        LunarBurrowerEntity original = worm(test);
        BlockPos pos = original.blockPosition(); original.discard();
        test.getLevel().setBlock(pos, GCBlocks.LUNAR_BURROWER_EGG.defaultBlockState(), 3);
        runAt(test, 410, () -> test.assertTrue(test.getLevel().getBlockState(pos).getValue(LunarBurrowerEggBlock.HATCH) == 1, "Egg never cracked"));
        runAt(test, 810, () -> test.assertTrue(test.getLevel().getBlockState(pos).getValue(LunarBurrowerEggBlock.HATCH) == 2, "Egg never entered hatching stage"));
        runFinalTaskAt(test, 1210, () -> {
            var children = test.getLevel().getEntitiesOfClass(LunarBurrowerEntity.class, new net.minecraft.world.phys.AABB(pos).inflate(3));
            test.assertTrue(test.getLevel().getBlockState(pos).isAir() && children.size() == 1, "Egg did not hatch exactly once");
            var child = children.getFirst();
            test.assertTrue(child.isHatched() && child.isBaby() && !child.isTame() && child.isPersistenceRequired(), "Wrong hatchling state");
            test.assertTrue(child.getBbWidth() < 0.8 && child.getBodySectionCount() == 3, "Juvenile retained the adult body");
            child.discard();
        });
    }
    @GameTest(template = ARENA, timeoutTicks = 25)
    public void blockedEggWaitsAndHatchesAfterSpaceIsCleared(GameTestHelper test) {
        LunarBurrowerEntity original = worm(test);
        BlockPos pos = original.blockPosition(); original.discard();
        var egg = GCBlocks.LUNAR_BURROWER_EGG.defaultBlockState().setValue(LunarBurrowerEggBlock.HATCH, 2);
        test.getLevel().setBlock(pos, egg, 3);
        test.getLevel().setBlock(pos.above(), Blocks.STONE.defaultBlockState(), 3);
        egg.tick(test.getLevel(), pos, test.getLevel().random);
        test.assertTrue(test.getLevel().getBlockState(pos).is(GCBlocks.LUNAR_BURROWER_EGG), "Blocked egg was consumed");
        test.getLevel().removeBlock(pos.above(), false);
        egg.tick(test.getLevel(), pos, test.getLevel().random);
        test.assertTrue(test.getLevel().getEntitiesOfClass(LunarBurrowerEntity.class, new net.minecraft.world.phys.AABB(pos).inflate(3)).size() == 1, "Cleared egg failed to hatch");
        test.succeed();
    }
    @GameTest(template = ARENA, timeoutTicks = 25)
    public void onlyWildWormsDropEggs(GameTestHelper test) {
        LunarBurrowerEntity wild = worm(test);
        var area = wild.getBoundingBox().inflate(4);
        wild.hurt(test.getLevel().damageSources().magic(), 1000);
        var eggs = test.getLevel().getEntitiesOfClass(ItemEntity.class, area, item -> item.getItem().is(GCBlocks.LUNAR_BURROWER_EGG.asItem()));
        test.assertTrue(eggs.size() == 1 && eggs.getFirst().getItem().getCount() == 1, "Wild worm did not drop one egg");
        eggs.forEach(ItemEntity::discard);
        LunarBurrowerEntity child = worm(test); child.hatch(); child.hurt(test.getLevel().damageSources().magic(), 1000);
        test.assertTrue(test.getLevel().getEntitiesOfClass(ItemEntity.class, area, item -> item.getItem().is(GCBlocks.LUNAR_BURROWER_EGG.asItem())).isEmpty(), "Hatchlings allow endless egg farming");
        test.succeed();
    }
    @GameTest(template = ARENA, timeoutTicks = 30)
    public void juvenileTamingGrowthAndOwnerPermissions(GameTestHelper test) {
        ServerPlayer owner = makeSurvivalServerPlayer(test), stranger = makeSurvivalServerPlayer(test);
        LunarBurrowerEntity pet = companion(test, owner);
        test.assertTrue(pet.isOwnedBy(owner) && owner.getMainHandItem().isEmpty(), "Cheese did not tame the juvenile");
        use(pet, owner, new ItemStack(Items.SADDLE));
        test.assertTrue(!pet.isSaddled() && owner.getMainHandItem().getCount() == 1, "Juvenile accepted a saddle");
        use(pet, owner, new ItemStack(GCItems.MOON_CHEESE_CURD));
        CompoundTag age = new CompoundTag(); pet.addAdditionalSaveData(age);
        test.assertTrue(age.getInt("GrowingTicks") == 10800, "Food did not accelerate growth");
        pet.setGrowingTicks(0);
        use(pet, stranger, new ItemStack(Items.SADDLE));
        test.assertTrue(!pet.isSaddled() && stranger.getMainHandItem().getCount() == 1, "Stranger equipped owner's worm");
        use(pet, owner, new ItemStack(Items.SADDLE)); use(pet, owner, new ItemStack(Items.CHEST));
        test.assertTrue(pet.isSaddled() && pet.hasChest(), "Owner could not equip adult");
        use(pet, stranger, ItemStack.EMPTY);
        test.assertTrue(!stranger.isPassenger(), "Stranger stole the mount");
        pet.disturb(owner);
        test.assertTrue(!pet.beginAttack(owner, true) && !pet.beginAttack(stranger, false), "Companion retained hostile attacks");
        use(pet, owner, ItemStack.EMPTY);
        test.assertTrue(owner.getVehicle() == pet && pet.getControllingPassenger() == owner && pet.getCapturedId() == -1, "Saddle riding used swallow capture");
        runFinalTaskAt(test, 5, () -> {
            test.assertTrue(owner.isAlive() && owner.getVehicle() == pet, "Pet attacked or ejected its rider");
            owner.stopRiding(); owner.discard(); stranger.discard(); pet.discard();
        });
    }
    @GameTest(template = ARENA, timeoutTicks = 25)
    public void cargoAndEquipmentSurviveReloadAndRecoverOnDeath(GameTestHelper test) {
        ServerPlayer owner = makeSurvivalServerPlayer(test), stranger = makeSurvivalServerPlayer(test);
        LunarBurrowerEntity pet = companion(test, owner); pet.setGrowingTicks(0);
        owner.setPos(pet.position()); stranger.setPos(pet.position());
        use(pet, owner, new ItemStack(Items.SADDLE)); use(pet, owner, new ItemStack(Items.CHEST));
        pet.getCargo().setItem(23, new ItemStack(Items.DIAMOND, 7));
        CompoundTag saved = new CompoundTag(); pet.addAdditionalSaveData(saved); pet.discard();
        LunarBurrowerEntity loaded = worm(test); loaded.readAdditionalSaveData(saved);
        test.assertTrue(loaded.isOwnedBy(owner) && loaded.isSaddled() && loaded.hasChest(), "Reload lost ownership or equipment");
        test.assertTrue(loaded.getCargo().getItem(23).is(Items.DIAMOND) && loaded.getCargo().getItem(23).getCount() == 7, "Reload lost cargo slot or count");
        test.assertTrue(loaded.getCargo().getItem(0).isEmpty(), "Reload compacted cargo slots");
        test.assertTrue(loaded.getCargo().stillValid(owner) && !loaded.getCargo().stillValid(stranger), "Cargo did not enforce ownership");
        loaded.hurt(test.getLevel().damageSources().magic(), 1000);
        var drops = test.getLevel().getEntitiesOfClass(ItemEntity.class, loaded.getBoundingBox().inflate(4));
        for (var item : new net.minecraft.world.item.Item[]{Items.SADDLE, Items.CHEST, Items.DIAMOND})
            test.assertTrue(drops.stream().filter(drop -> drop.getItem().is(item)).mapToInt(drop -> drop.getItem().getCount()).sum() == (item == Items.DIAMOND ? 7 : 1), "Wrong equipment/cargo death drop: " + item);
        test.assertTrue(!loaded.getCargo().stillValid(owner), "Dead worm left its cargo menu usable");
        owner.discard(); stranger.discard(); test.succeed();
    }
    @GameTest(template = ARENA, timeoutTicks = 25)
    public void cargoMenuAndShearsRecoverEquipmentWithoutLosingItems(GameTestHelper test) {
        ServerPlayer owner = makeSurvivalServerPlayer(test);
        LunarBurrowerEntity pet = companion(test, owner); pet.setGrowingTicks(0); owner.setPos(pet.position());
        use(pet, owner, new ItemStack(Items.SADDLE)); use(pet, owner, new ItemStack(Items.CHEST));
        pet.getCargo().setItem(5, new ItemStack(Items.EMERALD, 3));
        owner.setShiftKeyDown(true); use(pet, owner, ItemStack.EMPTY);
        test.assertTrue(owner.containerMenu instanceof net.minecraft.world.inventory.ChestMenu, "Sneaking did not open cargo");
        owner.closeContainer();
        use(pet, owner, new ItemStack(Items.STICK));
        test.assertTrue(pet.isSitting(), "Cargo companion cannot be told to stay");
        use(pet, owner, new ItemStack(Items.STICK));
        test.assertTrue(!pet.isSitting(), "Cargo companion cannot resume following");
        use(pet, owner, new ItemStack(Items.SHEARS));
        test.assertTrue(!pet.hasChest() && pet.isSaddled() && pet.getCargo().isEmpty(), "Shears did not recover cargo chest");
        use(pet, owner, new ItemStack(Items.SHEARS));
        test.assertTrue(!pet.isSaddled(), "Shears did not recover saddle");
        use(pet, owner, ItemStack.EMPTY);
        test.assertTrue(pet.isSitting(), "Empty-handed sneak did not make companion stay");
        owner.discard(); pet.discard(); test.succeed();
    }
    @GameTest(template = ARENA, timeoutTicks = 30)
    public void agingAddsBodyPartsAndRetainsGiantGrowthAcrossReload(GameTestHelper test) {
        LunarBurrowerEntity pet = worm(test); pet.hatch();
        float childWidth = pet.getBbWidth();
        runAt(test, 2, () -> {
            test.assertTrue(test.getLevel().getEntitiesOfClass(BurrowerBodyEntity.class, pet.getBoundingBox().inflate(50), part -> part.owner() == pet).size() == 4, "Hatchling has adult body targets");
            pet.setGrowthAge(6000);
            test.assertTrue(pet.getBbWidth() > childWidth && pet.getBodySectionCount() == 4, "Age did not grow the juvenile");
        });
        runAt(test, 4, () -> {
            test.assertTrue(test.getLevel().getEntitiesOfClass(BurrowerBodyEntity.class, pet.getBoundingBox().inflate(50), part -> part.owner() == pet).size() == 5, "Growing juvenile did not add a section");
            pet.setGrowthAge(dev.galacticraft.mod.util.BurrowerGrowth.MAX_AGE);
        });
        runFinalTaskAt(test, 7, () -> {
            test.assertTrue(pet.getBodySectionCount() == 12 && pet.getModelScale() == 3, "Giant growth was capped at adulthood");
            test.assertTrue(test.getLevel().getEntitiesOfClass(BurrowerBodyEntity.class, pet.getBoundingBox().inflate(60), part -> part.owner() == pet).size() == 13, "Giant did not have twelve sections and tail targets");
            test.assertTrue(pet.bodyPosition(12).subtract(pet.position()).horizontalDistance() > 30, "Giant tail collapsed into the head");
            CompoundTag saved = new CompoundTag(); pet.addAdditionalSaveData(saved);
            var loaded = new LunarBurrowerEntity(GCEntityTypes.LUNAR_BURROWER, test.getLevel()); loaded.readAdditionalSaveData(saved);
            test.assertTrue(loaded.getGrowthAge() == dev.galacticraft.mod.util.BurrowerGrowth.MAX_AGE && loaded.getBodySectionCount() == 12, "Reload reset giant's growth");
            pet.discard();
            test.assertTrue(test.getLevel().getEntitiesOfClass(BurrowerBodyEntity.class, pet.getBoundingBox().inflate(60), part -> part.owner() == pet).isEmpty(), "Giant removal left extra sections behind");
        });
    }
    private LunarBurrowerEntity worm(GameTestHelper test) {
        for (int x = 1; x <= 19; x++) for (int z = 1; z <= 19; z++)
            test.getLevel().setBlock(test.absolutePos(new BlockPos(x, 0, z)), GCBlocks.MOON_ROCK.defaultBlockState(), 2);
        BlockPos pos = test.absolutePos(new BlockPos(10, 1, 10));
        LunarBurrowerEntity worm = new LunarBurrowerEntity(GCEntityTypes.LUNAR_BURROWER, test.getLevel());
        worm.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 180, 0);
        worm.setDenPosition(pos);
        test.getLevel().addFreshEntity(worm);
        return worm;
    }
    private ServerPlayer victim(GameTestHelper test, LunarBurrowerEntity worm) {
        ServerPlayer player = makeSurvivalServerPlayer(test);
        player.setPos(worm.getX(), worm.getY(), worm.getZ() - 2);
        player.setNoGravity(true);
        return player;
    }
    @GameTest(template = ARENA, timeoutTicks = 190)
    public void aPlayerBehindTheTailMakesTheWormTurnWithoutFoldingItsHeadBack(GameTestHelper test) {
        LunarBurrowerEntity worm = worm(test);
        ServerPlayer player = victim(test, worm);
        Vec3 start = worm.position();
        player.setPos(start.add(0, 0, 5));
        worm.disturb(player);
        float[] previousYaw = {Float.NaN};
        double[] widestTurn = {0};
        for (int tick = 2; tick <= 160; tick++) runAt(test, tick, () -> {
            float neck = worm.getNeckYaw(1);
            test.assertTrue(Math.abs(net.minecraft.util.Mth.wrapDegrees(worm.getYRot() - neck)) <= 35.01,
                    "Head reversed through its neck");
            test.assertTrue(!Float.isFinite(previousYaw[0]) || Math.abs(net.minecraft.util.Mth.wrapDegrees(worm.getYRot() - previousYaw[0])) <= 6.1,
                    "Navigation snapped the head instead of steering it: " + previousYaw[0] + " -> " + worm.getYRot() + ", neck=" + neck);
            previousYaw[0] = worm.getYRot();
            widestTurn[0] = Math.max(widestTurn[0], Math.abs(worm.getX() - start.x));
            for (int segment = 1; segment < 7; segment++)
                test.assertTrue(worm.position().subtract(worm.bodyPosition(segment)).horizontalDistance() > 1.2,
                        "Head drove through body segment " + segment + " at tick " + test.getTick());
        });
        runFinalTaskAt(test, 170, () -> {
            test.assertTrue(widestTurn[0] > 1, "Rear target did not produce a turning arc: width=" + widestTurn[0] + ", state=" + worm.getBurrowerState());
            test.assertTrue(worm.getLookAngle().z > 0.2, "Worm could not turn toward a target behind its tail: " + worm.getLookAngle());
            player.discard();
        });
    }

    @GameTest(template = ARENA, timeoutTicks = 25)
    public void bitesAndSwallowsCannotStartOnAPlayerBehindTheNeck(GameTestHelper test) {
        LunarBurrowerEntity worm = worm(test);
        ServerPlayer player = victim(test, worm);
        player.setPos(worm.position().add(0, 0, 2));
        test.assertTrue(!worm.beginAttack(player, true), "Swallow started through the back of the head");
        test.assertTrue(!worm.beginAttack(player, false), "Bite started through the back of the head");
        player.discard(); test.succeed();
    }

    @GameTest(template = ARENA, timeoutTicks = 35)
    public void movingBehindTheNeckDuringTheWindupAvoidsTheGrab(GameTestHelper test) {
        LunarBurrowerEntity worm = worm(test);
        ServerPlayer player = victim(test, worm);
        test.assertTrue(worm.beginAttack(player, true), "Front swallow did not start");
        runAt(test, 10, () -> player.setPos(worm.position().add(0, 0, 2)));
        runFinalTaskAt(test, 25, () -> {
            test.assertTrue(player.getVehicle() == null && worm.getCapturedId() == -1,
                    "Grab reached backward through the neck");
            player.discard();
        });
    }
    @GameTest(template = ARENA, timeoutTicks = 145)
    public void aMovingTurnFlowsFromTheNeckToTheTailWithoutSegmentJumps(GameTestHelper test) {
        LunarBurrowerEntity worm = worm(test);
        worm.setNoAi(true);
        Vec3 start = worm.position();
        Vec3[] previous = new Vec3[7];
        for (int tick = 1; tick <= 125; tick++) {
            int age = tick;
            runAt(test, tick, () -> {
                for (int segment = 0; segment < 7; segment++) {
                    Vec3 point = worm.bodyPosition(segment);
                    if (previous[segment] != null) test.assertTrue(point.distanceTo(previous[segment]) < 0.14,
                            "Segment jumped during the turn: " + segment);
                    previous[segment] = point;
                }
                for (double distance = 0.25; distance < 7; distance += 0.25)
                    test.assertTrue(Math.abs(worm.bodyPositionAt(distance).distanceTo(worm.bodyPositionAt(distance + 0.25)) - 0.25) < 0.02,
                            "Body spacing collapsed around the corner at " + distance);
                // A deliberately sharp navigation corner; the shared body route must soften it.
                worm.setPos(start.add(Math.max(0, age - 30) * 0.08, 0, -Math.min(30, age) * 0.08));
                if (age == 30) worm.setYRot(-90);
            });
        }
        runAt(test, 65, () -> {
            Vec3 front = worm.bodyPositionAt(1).subtract(worm.bodyPositionAt(2)).normalize();
            Vec3 rear = worm.bodyPositionAt(6).subtract(worm.bodyPositionAt(7)).normalize();
            test.assertTrue(front.x > 0.9 && rear.z < -0.9, "Turn did not travel down the body progressively");
        });
        runFinalTaskAt(test, 130, () -> {
            Vec3 rear = worm.bodyPositionAt(6).subtract(worm.bodyPositionAt(7)).normalize();
            test.assertTrue(rear.x > 0.9, "Tail never followed the head around the bend: " + rear);
        });
    }
    @GameTest(template = ARENA, timeoutTicks = 65)
    public void fallingAndSteppingDownDoNotStackTheBodyInMidair(GameTestHelper test) {
        LunarBurrowerEntity worm = worm(test);
        test.assertTrue(Math.abs(worm.bodyPosition(0).distanceTo(worm.position()) - 1.375) < 0.01,
                "Newly spawned body collapsed into the tail before its first tick");
        worm.setPos(worm.getX(), worm.getY() + 3, worm.getZ());
        runFinalTaskAt(test, 45, () -> {
            double floor = test.absolutePos(new BlockPos(10, 1, 10)).getY();
            test.assertTrue(Math.abs(worm.getY() - floor) < 0.05, "Head did not finish falling");
            for (int segment = 0; segment < 7; segment++) {
                Vec3 point = worm.bodyPosition(segment);
                test.assertTrue(Math.abs(point.y - floor) < 0.05, "Body floated after falling: " + segment + " at " + point);
                test.assertTrue(Math.abs(point.z - worm.getZ() - (segment == 6 ? 7 : 1.375 + segment)) < 0.05,
                        "Vertical fall consumed the horizontal body length");
            }
        });
    }
    @GameTest(template = ARENA, timeoutTicks = 30)
    public void bodyUsesEachSegmentsFloorInsteadOfTheHeadsHeight(GameTestHelper test) {
        LunarBurrowerEntity worm = worm(test);
        worm.setNoAi(true);
        for (int z = 14; z <= 19; z++) for (int x = 9; x <= 11; x++)
            test.setBlock(new BlockPos(x, 1, z), GCBlocks.MOON_ROCK);
        runFinalTaskAt(test, 10, () -> {
            double floor = worm.getY();
            test.assertTrue(worm.bodyPositionAt(-0.25).z < worm.getZ() - 0.24, "Forward attack reach was clamped at the head");
            test.assertTrue(Math.abs(worm.bodyPosition(0).y - floor) < 0.05, "Front segment left the lower floor");
            test.assertTrue(Math.abs(worm.bodyPosition(4).y - floor - 1) < 0.05, "Rear segment sank into the ledge");
            test.assertTrue(worm.bodyPositionAt(3.375).y > floor && worm.bodyPositionAt(3.375).y < floor + 1,
                    "Body did not bridge the step continuously");
        });
    }
    @GameTest(template = ARENA, timeoutTicks = 30)
    public void idleIsCalmAndCreativePlayersCannotBeCaptured(GameTestHelper test) {
        LunarBurrowerEntity worm = worm(test);
        ServerPlayer player = victim(test, worm);
        player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        worm.disturb(player);
        test.assertTrue(!worm.beginAttack(player, true), "Creative player was accepted for swallow");
        runFinalTaskAt(test, 15, () -> {
            test.assertTrue(worm.getBurrowerState() == BurrowerState.IDLE, "Resting worm attacked a creative player");
            test.assertTrue(worm.getTarget() == null, "Creative player became a target");
            player.discard();
        });
    }
    @GameTest(template = ARENA, timeoutTicks = 65)
    public void warningPrecedesChaseAndLeavingTerritoryStopsPursuit(GameTestHelper test) {
        LunarBurrowerEntity worm = worm(test);
        ServerPlayer player = victim(test, worm);
        player.setPos(worm.getX(), worm.getY(), worm.getZ() - 7);
        worm.disturb(player);
        runAt(test, 20, () -> test.assertTrue(worm.getBurrowerState() == BurrowerState.WARNING, "Warning ended before its clip completed"));
        runAt(test, 38, () -> {
            test.assertTrue(worm.getBurrowerState() == BurrowerState.CHASE, "Warning did not lead to chase");
            player.setPos(worm.getX() + 30, worm.getY() + 20, worm.getZ());
        });
        runFinalTaskAt(test, 48, () -> {
            test.assertTrue(worm.getTarget() == null && worm.getBurrowerState() != BurrowerState.CHASE, "Worm chased outside its territory");
            player.discard();
        });
    }
    @GameTest(template = ARENA, timeoutTicks = 35)
    public void crawlReturnsTowardTheDen(GameTestHelper test) {
        LunarBurrowerEntity worm = worm(test);
        BlockPos home = test.absolutePos(new BlockPos(4, 1, 10));
        worm.setDenPosition(home);
        double initial = worm.getX();
        runFinalTaskAt(test, 20, () -> {
            test.assertTrue(worm.getBurrowerState() == BurrowerState.CRAWL, "Worm did not choose the crawl state");
            test.assertTrue(worm.getX() < initial - 0.1, "Crawl did not move toward the den");
        });
    }
    @GameTest(template = ARENA, timeoutTicks = 110)
    public void biteHitsOnceAtTheAnimationContactFrame(GameTestHelper test) {
        LunarBurrowerEntity worm = worm(test);
        ServerPlayer player = victim(test, worm);
        player.setPos(worm.getX(), worm.getY(), worm.getZ() - 7);
        // Real server players have a sixty-tick login damage grace period.
        runAt(test, 65, () -> {
            player.setPos(worm.getX(), worm.getY(), worm.getZ() - 2);
            test.assertTrue(worm.beginAttack(player, false), "Bite did not start");
        });
        runAt(test, 73, () -> test.assertTrue(player.getHealth() == 20, "Bite dealt damage before the contact frame"));
        runFinalTaskAt(test, 95, () -> {
            test.assertTrue(player.getHealth() == 13, "Bite did not deal exactly one seven-point hit: " + player.getHealth());
            test.assertTrue(worm.getBurrowerState() == BurrowerState.CHASE, "Bite did not recover into chase");
            player.discard();
        });
    }
    @GameTest(template = ARENA, timeoutTicks = 45)
    public void sneakDuringTheEscapeWindowReleasesThePlayer(GameTestHelper test) {
        LunarBurrowerEntity worm = worm(test);
        ServerPlayer player = victim(test, worm);
        test.assertTrue(worm.beginAttack(player, true), "Eat attack did not start");
        runAt(test, 24, () -> {
            test.assertTrue(player.getVehicle() == worm && worm.canEscape(), "Player was not captured in the escape window");
            player.setShiftKeyDown(true);
        });
        runFinalTaskAt(test, 40, () -> {
            test.assertTrue(player.isAlive() && player.getVehicle() == null, "Sneak escape did not release a living player");
            test.assertTrue(worm.getCapturedId() == -1, "Escaped player remained in the swallow renderer");
            player.discard();
        });
    }
    @GameTest(template = ARENA, timeoutTicks = 145)
    public void failedEscapeIsLethalAndEatLeadsToBurpThenIdle(GameTestHelper test) {
        LunarBurrowerEntity worm = worm(test);
        ServerPlayer player = victim(test, worm);
        test.assertTrue(worm.beginAttack(player, true), "Eat attack did not start");
        runAt(test, 30, () -> test.assertTrue(player.isAlive() && player.getVehicle() == worm, "Player died before the escape deadline"));
        runAt(test, 42, () -> test.assertTrue(!player.isAlive() && player.getVehicle() == null, "Lethal swallow did not resolve after the deadline"));
        runAt(test, 93, () -> test.assertTrue(worm.getBurrowerState() == BurrowerState.BURP, "Successful swallow did not lead to burp"));
        runFinalTaskAt(test, 130, () -> {
            test.assertTrue(worm.getBurrowerState() == BurrowerState.IDLE, "Burp did not end in idle");
            player.discard();
        });
    }
    @GameTest(template = ARENA, timeoutTicks = 40)
    public void allyDamageInterruptsCaptureAndBodyHitsReachTheWorm(GameTestHelper test) {
        LunarBurrowerEntity worm = worm(test);
        ServerPlayer player = victim(test, worm);
        test.assertTrue(worm.beginAttack(player, true), "Eat attack did not start");
        runAt(test, 24, () -> {
            var parts = test.getLevel().getEntitiesOfClass(BurrowerBodyEntity.class, worm.getBoundingBox().inflate(10), part -> part.owner() == worm);
            test.assertTrue(parts.size() == 7, "Expected seven body damage targets");
            test.assertTrue(parts.getFirst().hurt(test.getLevel().damageSources().magic(), 4), "Body hit did not register");
        });
        runFinalTaskAt(test, 35, () -> {
            test.assertTrue(player.isAlive() && player.getVehicle() == null, "Ally hit did not release the captured player");
            test.assertTrue(worm.getHealth() == 44, "Body damage did not reach the parent exactly once");
            player.discard();
        });
    }
    @GameTest(template = ARENA, timeoutTicks = 40)
    public void removalReleasesCapturedPlayersAndRemovesParts(GameTestHelper test) {
        LunarBurrowerEntity worm = worm(test);
        ServerPlayer player = victim(test, worm);
        test.assertTrue(worm.beginAttack(player, true), "Eat attack did not start");
        runAt(test, 24, () -> {
            test.assertTrue(player.getVehicle() == worm, "Player was not captured");
            worm.discard();
        });
        runFinalTaskAt(test, 30, () -> {
            test.assertTrue(player.isAlive() && player.getVehicle() == null, "Removal stranded or killed the player");
            test.assertTrue(test.getLevel().getEntitiesOfClass(BurrowerBodyEntity.class, worm.getBoundingBox().inflate(10), part -> part.owner() == worm).isEmpty(), "Removal left orphan body entities");
            player.discard();
        });
    }
    @GameTest(template = ARENA, timeoutTicks = 220)
    public void danceLoopsBrieflyAndReturnsToIdle(GameTestHelper test) {
        LunarBurrowerEntity worm = worm(test);
        test.assertTrue(worm.startDance(), "A calm worm could not begin its dance");
        runAt(test, 100, () -> test.assertTrue(worm.getBurrowerState() == BurrowerState.DANCE, "Dance did not loop"));
        runFinalTaskAt(test, 200, () -> test.assertTrue(worm.getBurrowerState() == BurrowerState.IDLE, "Rare dance did not finish"));
    }

    @GameTest(template = ARENA, timeoutTicks = 65)
    public void deathReleasesCaptureBeforeTheCorpseIsRemoved(GameTestHelper test) {
        LunarBurrowerEntity worm = worm(test);
        ServerPlayer player = victim(test, worm);
        test.assertTrue(worm.beginAttack(player, true), "Eat attack did not start");
        runAt(test, 24, () -> {
            test.assertTrue(player.getVehicle() == worm, "Player was not captured");
            worm.hurt(test.getLevel().damageSources().magic(), 1000);
            test.assertTrue(player.isAlive() && player.getVehicle() == null, "Death did not immediately release the player");
        });
        runFinalTaskAt(test, 52, () -> {
            test.assertTrue(worm.isRemoved(), "Corpse was not removed");
            test.assertTrue(test.getLevel().getEntitiesOfClass(BurrowerBodyEntity.class, worm.getBoundingBox().inflate(10), part -> part.owner() == worm).isEmpty(), "Death left orphan body targets");
            player.discard();
        });
    }
    @GameTest(template = ARENA, timeoutTicks = 110)
    public void steppingAwayBeforeBiteContactAvoidsDamage(GameTestHelper test) {
        LunarBurrowerEntity worm = worm(test);
        ServerPlayer player = victim(test, worm);
        player.setPos(worm.getX(), worm.getY(), worm.getZ() - 7);
        runAt(test, 65, () -> {
            player.setPos(worm.getX(), worm.getY(), worm.getZ() - 2);
            test.assertTrue(worm.beginAttack(player, false), "Bite did not start");
        });
        runAt(test, 73, () -> player.setPos(worm.getX(), worm.getY(), worm.getZ() - 7));
        runFinalTaskAt(test, 95, () -> {
            test.assertTrue(player.getHealth() == 20, "Bite damaged an out-of-range player");
            player.discard();
        });
    }
    @GameTest(template = ARENA, timeoutTicks = 35)
    public void missedGrabDoesNotCaptureADistantPlayer(GameTestHelper test) {
        LunarBurrowerEntity worm = worm(test);
        ServerPlayer player = victim(test, worm);
        test.assertTrue(worm.beginAttack(player, true), "Eat attack did not start");
        runAt(test, 10, () -> player.setPos(worm.getX(), worm.getY(), worm.getZ() - 7));
        runFinalTaskAt(test, 25, () -> {
            test.assertTrue(player.isAlive() && player.getVehicle() == null && worm.getCapturedId() == -1, "A missed grab captured the player");
            player.discard();
        });
    }
    @GameTest(template = ARENA, timeoutTicks = 35)
    public void damageInterruptsTheRareDance(GameTestHelper test) {
        LunarBurrowerEntity worm = worm(test);
        test.assertTrue(worm.startDance(), "Dance did not start");
        ServerPlayer player = victim(test, worm);
        test.assertTrue(worm.hurt(test.getLevel().damageSources().playerAttack(player), 1), "Disturbance did not hit");
        test.assertTrue(worm.getBurrowerState() == BurrowerState.WARNING, "Damage did not interrupt dance for warning");
        player.discard(); test.succeed();
    }
    @GameTest(template = ARENA, timeoutTicks = 30)
    public void naturalSpawningRequiresRoomForTheWholeBody(GameTestHelper test) {
        LunarBurrowerEntity worm = worm(test);
        BlockPos pos = worm.blockPosition(); worm.discard();
        for (int x = -2; x <= 2; x++) for (int z = -1; z <= 9; z++)
            test.getLevel().setBlock(pos.offset(x, 3, z), GCBlocks.MOON_ROCK.defaultBlockState(), 2);
        runFinalTaskAt(test, 8, () -> {
            test.assertTrue(LunarBurrowerEntity.checkSpawnRules(GCEntityTypes.LUNAR_BURROWER, test.getLevel(), MobSpawnType.NATURAL, pos, test.getLevel().random),
                    "Clear dark cave route was rejected; sky=" + test.getLevel().canSeeSky(pos) + ", light=" + test.getLevel().getBrightness(net.minecraft.world.level.LightLayer.BLOCK, pos));
            test.getLevel().setBlock(pos.offset(0, 1, 6), GCBlocks.MOON_ROCK.defaultBlockState(), 2);
            test.assertTrue(!LunarBurrowerEntity.checkSpawnRules(GCEntityTypes.LUNAR_BURROWER, test.getLevel(), MobSpawnType.NATURAL, pos, test.getLevel().random), "Spawn accepted an obstructed tail route");
        });
    }

    @GameTest(template = ARENA, timeoutTicks = 30)
    public void generatedDenContainsOnePersistentAdultAndClearLane(GameTestHelper test) {
        var level = test.getLevel();
        BlockPos origin = test.absolutePos(new BlockPos(10, 1, 10));
        BlockPos center = new BlockPos((origin.getX() & ~15) + 8, 1, (origin.getZ() & ~15) + 8);
        // A controlled chamber within the feature's underground height range, above the other fixtures.
        for (int x = -6; x <= 6; x++) for (int z = -6; z <= 6; z++) {
            level.setBlock(center.offset(x, -1, z), GCBlocks.MOON_ROCK.defaultBlockState(), 2);
            level.setBlock(center.offset(x, 12, z), GCBlocks.MOON_ROCK.defaultBlockState(), 2);
        }
        net.minecraft.world.level.levelgen.Heightmap.primeHeightmaps(level.getChunkAt(center),
                java.util.EnumSet.of(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG));
        var feature = new dev.galacticraft.mod.world.gen.feature.LunarBurrowerDenFeature(
                net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration.CODEC);
        test.assertTrue(feature.place(new net.minecraft.world.level.levelgen.feature.FeaturePlaceContext<>(
                java.util.Optional.empty(), level, level.getChunkSource().getGenerator(), net.minecraft.util.RandomSource.create(42),
                center, net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration.INSTANCE)), "Broad chamber did not generate a den");
        var residents = level.getEntitiesOfClass(LunarBurrowerEntity.class, new net.minecraft.world.phys.AABB(center).inflate(10));
        test.assertTrue(residents.size() == 1 && residents.getFirst().isPersistenceRequired(), "Den did not create one persistent resident");
        var resident = residents.getFirst();
        var forward = resident.getDirection().getOpposite();
        BlockPos den = resident.getDenPosition().relative(forward, 3);
        var right = forward.getClockWise();
        for (int side = -1; side <= 1; side++) for (int depth = -4; depth <= 5; depth++) {
            BlockPos lane = den.relative(right, side).relative(forward, depth);
            var state = level.getBlockState(lane);
            test.assertTrue((state.isAir() || state.is(dev.galacticraft.mod.tag.GCBlockTags.MOON_CAVE_FORMATIONS)
                    && state.getCollisionShape(level, lane).max(net.minecraft.core.Direction.Axis.Y) <= 0.5)
                    && level.isEmptyBlock(lane.above()) && level.isEmptyBlock(lane.above(2)), "Den blocked its central lane: " + state + " at " + lane);
        }
        resident.discard();
        for (int x = -6; x <= 6; x++) for (int z = -6; z <= 6; z++) for (int y : new int[]{-1, 0, 12})
            level.setBlock(center.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
        test.succeed();
    }

    @GameTest(template = ARENA, timeoutTicks = 40)
    public void aSecondPlayerCannotStealTheTelegraphedSwallow(GameTestHelper test) {
        LunarBurrowerEntity worm = worm(test);
        ServerPlayer first = victim(test, worm), second = victim(test, worm);
        second.setPos(worm.getX() + 2, worm.getY(), worm.getZ());
        test.assertTrue(worm.beginAttack(first, true), "Eat attack did not start");
        runAt(test, 10, () -> worm.disturb(second));
        runFinalTaskAt(test, 24, () -> {
            test.assertTrue(first.getVehicle() == worm && second.getVehicle() == null, "Telegraphed grab switched players");
            worm.discard(); first.discard(); second.discard();
        });
    }
    @GameTest(template = ARENA, timeoutTicks = 40)
    public void aStrongHitDuringTheWindupCancelsTheGrab(GameTestHelper test) {
        LunarBurrowerEntity worm = worm(test);
        ServerPlayer player = victim(test, worm);
        test.assertTrue(worm.beginAttack(player, true), "Eat attack did not start");
        runAt(test, 10, () -> worm.hurt(test.getLevel().damageSources().magic(), 4));
        runFinalTaskAt(test, 24, () -> {
            test.assertTrue(player.isAlive() && player.getVehicle() == null && worm.getCapturedId() == -1, "Interrupted windup still grabbed the player");
            test.assertTrue(worm.getBurrowerState() != BurrowerState.EAT, "Interrupted windup remained in eat");
            player.discard();
        });
    }

    private void wall(GameTestHelper test, LunarBurrowerEntity worm) {
        BlockPos start = worm.blockPosition();
        for (int step = 1; step <= 4; step++) for (int width = -1; width <= 1; width++) for (int y = 0; y < 2; y++)
            test.getLevel().setBlock(start.offset(step, y, width), GCBlocks.MOON_ROCK.defaultBlockState(), 2);
    }
    @GameTest(template = ARENA, timeoutTicks = 125)
    public void digCreatesASupportedTunnelAndFinishesItsState(GameTestHelper test) {
        LunarBurrowerEntity worm = worm(test);
        wall(test, worm);
        BlockPos rock = worm.blockPosition().offset(2, 0, 0);
        test.assertTrue(worm.tryStartDigging(new Vec3(1, 0, 0)), "Natural Moon rock route was rejected");
        runAt(test, 12, () -> test.assertTrue(worm.getBurrowerState() == BurrowerState.DIG, "Dig animation state ended early"));
        runFinalTaskAt(test, 110, () -> {
            test.assertTrue(test.getLevel().getBlockState(rock).isAir(), "Digging did not remove the tunnel rock");
            test.assertTrue(test.getLevel().getBlockState(rock.below()).is(GCBlocks.MOON_ROCK), "Digging removed the supporting floor");
            test.assertTrue(worm.getBurrowerState() != BurrowerState.DIG, "Digging never completed");
        });
    }
    @GameTest(template = ARENA, timeoutTicks = 25)
    public void diggingRejectsOresContainersAndDisabledMobGriefing(GameTestHelper test) {
        LunarBurrowerEntity worm = worm(test); wall(test, worm);
        BlockPos protectedPos = worm.blockPosition().offset(2, 0, 0);
        test.getLevel().setBlock(protectedPos, GCBlocks.MOON_COPPER_ORE.defaultBlockState(), 2);
        test.assertTrue(!worm.tryStartDigging(new Vec3(1, 0, 0)), "Dig plan accepted an ore block");
        test.getLevel().setBlock(protectedPos, Blocks.CHEST.defaultBlockState(), 2);
        test.assertTrue(!worm.tryStartDigging(new Vec3(1, 0, 0)), "Dig plan accepted a container");
        test.getLevel().setBlock(protectedPos, GCBlocks.MOON_ROCK.defaultBlockState(), 2);
        var rule = test.getLevel().getGameRules().getRule(GameRules.RULE_MOBGRIEFING);
        boolean previous = rule.get();
        try {
            rule.set(false, test.getLevel().getServer());
            test.assertTrue(!worm.tryStartDigging(new Vec3(1, 0, 0)), "Worm dug with mobGriefing disabled");
        } finally { rule.set(previous, test.getLevel().getServer()); }
        test.succeed();
    }
    @GameTest(template = ARENA, timeoutTicks = 25)
    public void miningProvokesAWarningAndReloadDoesNotResumeCapture(GameTestHelper test) {
        LunarBurrowerEntity worm = worm(test);
        ServerPlayer player = victim(test, worm);
        BlockPos rock = worm.blockPosition().offset(2, 0, 0);
        test.getLevel().setBlock(rock, GCBlocks.MOON_ROCK.defaultBlockState(), 2);
        test.assertTrue(player.gameMode.destroyBlock(rock), "Test player did not mine the Moon rock");
        test.assertTrue(worm.getBurrowerState() == BurrowerState.WARNING, "Mining did not invoke the disturbance hook");
        var tag = new net.minecraft.nbt.CompoundTag(); worm.saveWithoutId(tag);
        LunarBurrowerEntity loaded = new LunarBurrowerEntity(GCEntityTypes.LUNAR_BURROWER, test.getLevel()); loaded.load(tag);
        test.assertTrue(loaded.getCapturedId() == -1 && loaded.getBurrowerState() == BurrowerState.IDLE, "Reload restored a capture");
        test.assertTrue(loaded.getDenPosition().equals(worm.getDenPosition()), "Reload lost its territory");
        player.discard(); test.succeed();
    }
}
