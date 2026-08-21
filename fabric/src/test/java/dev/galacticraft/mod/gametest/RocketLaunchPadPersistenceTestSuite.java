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

import com.mojang.datafixers.util.Pair;
import dev.galacticraft.api.rocket.RocketData;
import dev.galacticraft.api.rocket.RocketPrefabs;
import dev.galacticraft.mod.content.GCBlocks;
import dev.galacticraft.mod.content.GCEntityTypes;
import dev.galacticraft.mod.content.GCRocketParts;
import dev.galacticraft.mod.content.block.special.launchpad.AbstractLaunchPad;
import dev.galacticraft.mod.content.block.special.launchpad.LaunchPadBlockEntity;
import dev.galacticraft.mod.content.entity.vehicle.RocketEntity;
import dev.galacticraft.mod.content.rocket.part.data.StorageRocketData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.EitherHolder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import java.util.Optional;

/**
 * A rocket parked on a launch pad has to still be there after the world is saved and loaded again.
 * The reported bug is that quitting and rejoining deletes it.
 */
public final class RocketLaunchPadPersistenceTestSuite implements GalacticraftGameTest {
    private static final BlockPos PAD_CENTER = new BlockPos(2, 1, 2);

    @GameTest(template = EMPTY_STRUCTURE)
    public void rocketOnLaunchPadSurvivesSaveAndReload(GameTestHelper context) {
        ServerLevel level = context.getLevel();
        LaunchPadBlockEntity pad = buildPad(context);
        BlockPos padPos = context.absolutePos(PAD_CENTER);

        RocketEntity rocket = dockRocket(context, level, pad, padPos);

        // --- quit: the chunk is written out, then dropped ---
        CompoundTag rocketTag = new CompoundTag();
        if (!rocket.save(rocketTag)) {
            context.fail("the rocket refused to save at all", PAD_CENTER);
            return;
        }
        CompoundTag padTag = pad.saveWithFullMetadata(level.registryAccess());
        rocket.setRemoved(Entity.RemovalReason.UNLOADED_TO_CHUNK);

        // --- rejoin: block entity and entity are both read back ---
        pad.loadWithComponents(padTag, level.registryAccess());
        Entity loaded = EntityType.loadEntityRecursive(rocketTag, level, entity -> entity);
        if (!(loaded instanceof RocketEntity reloaded)) {
            context.fail("the rocket did not come back from NBT (got " + loaded + ")", PAD_CENTER);
            return;
        }
        level.addFreshEntity(reloaded);

        if (reloaded.getLandingPad() == null) {
            context.fail("the reloaded rocket lost its link to the pad", PAD_CENTER);
        } else if (pad.getDockedEntity() != reloaded) {
            context.fail("the pad no longer reports the reloaded rocket as docked", PAD_CENTER);
        } else {
            context.succeed();
        }
    }

    /**
     * The pad keeps the rocket's UUID, so after a reload it must resolve back to the same rocket
     * rather than treating the slot as free.
     */
    @GameTest(template = EMPTY_STRUCTURE)
    public void launchPadStillReportsItsRocketAfterReload(GameTestHelper context) {
        ServerLevel level = context.getLevel();
        LaunchPadBlockEntity pad = buildPad(context);
        BlockPos padPos = context.absolutePos(PAD_CENTER);

        RocketEntity rocket = dockRocket(context, level, pad, padPos);

        CompoundTag padTag = pad.saveWithFullMetadata(level.registryAccess());
        pad.loadWithComponents(padTag, level.registryAccess());

        if (pad.getDockedUUID() == null) {
            context.fail("the pad forgot which rocket was docked", PAD_CENTER);
        } else if (!pad.getDockedUUID().equals(rocket.getUUID())) {
            context.fail("the pad came back pointing at a different rocket", PAD_CENTER);
        } else if (!pad.hasDockedEntity()) {
            context.fail("the pad reports no docked rocket after reload", PAD_CENTER);
        } else {
            context.succeed();
        }
    }

    /**
     * The rocket resolves its pad by reading the block entity at the saved position while it is
     * being deserialized. When the entity comes back before that block entity is reachable - which
     * is what a chunk still being loaded looks like - the link is simply not made, and the pad is
     * the only thing left that can repair it. It holds the rocket's UUID for exactly that purpose.
     */
    @GameTest(template = EMPTY_STRUCTURE)
    public void padRelinksARocketThatLoadedWithoutIt(GameTestHelper context) {
        ServerLevel level = context.getLevel();
        LaunchPadBlockEntity pad = buildPad(context);
        BlockPos padPos = context.absolutePos(PAD_CENTER);

        RocketEntity rocket = dockRocket(context, level, pad, padPos);
        CompoundTag rocketTag = new CompoundTag();
        rocket.save(rocketTag);
        CompoundTag padTag = pad.saveWithFullMetadata(level.registryAccess());
        rocket.setRemoved(Entity.RemovalReason.UNLOADED_TO_CHUNK);

        // A failed block entity lookup leaves exactly the same state as no saved link at all.
        rocketTag.remove("Linked");

        Entity loaded = EntityType.loadEntityRecursive(rocketTag, level, entity -> entity);
        if (!(loaded instanceof RocketEntity reloaded)) {
            context.fail("the rocket did not come back from NBT (got " + loaded + ")", PAD_CENTER);
            return;
        }
        level.addFreshEntity(reloaded);
        pad.loadWithComponents(padTag, level.registryAccess());

        if (pad.getDockedEntity() != reloaded) {
            context.fail("the pad did not resolve its rocket by UUID", PAD_CENTER);
        } else if (reloaded.getLandingPad() == null) {
            context.fail("the pad resolved the rocket but never re-linked it to itself", PAD_CENTER);
        } else {
            context.succeed();
        }
    }

    /**
     * A rocket built with the storage upgrade has to come back from a reload still carrying that
     * upgrade, the cargo slots it grants, and whatever was in them.
     */
    @GameTest(template = EMPTY_STRUCTURE)
    public void storageUpgradeAndCargoSurviveReload(GameTestHelper context) {
        ServerLevel level = context.getLevel();

        TestRocket original = new TestRocket(level);
        original.setData(storageRocketData());

        int slots = original.getStorageSlotCount();
        if (slots <= 0) {
            context.fail("the storage upgrade granted no cargo slots to begin with (" + slots + ")");
            return;
        }
        original.getVehicleInventory().setItem(0, new ItemStack(Items.DIAMOND, 5));

        CompoundTag saved = new CompoundTag();
        original.writeData(saved);

        TestRocket restored = new TestRocket(level);
        restored.readData(saved);

        if (restored.upgrade() == null) {
            context.fail("the reloaded rocket lost its storage upgrade");
        } else if (restored.getStorageSlotCount() != slots) {
            context.fail("cargo slots changed across a reload: " + slots + " -> " + restored.getStorageSlotCount());
        } else if (!restored.getVehicleInventory().getItem(0).is(Items.DIAMOND)
                || restored.getVehicleInventory().getItem(0).getCount() != 5) {
            context.fail("the reloaded rocket's cargo was wiped (found "
                    + restored.getVehicleInventory().getItem(0) + ")");
        } else {
            context.succeed();
        }
    }

    /**
     * The chest count is per-rocket and only lives in the rocket's own upgrade data, so it is the
     * part most likely to be dropped by the codec.
     */
    @GameTest(template = EMPTY_STRUCTURE)
    public void rocketDataCodecRoundTripsTheStorageUpgrade(GameTestHelper context) {
        RocketData original = storageRocketData();
        // The part codecs are RegistryFileCodecs; they need registry-aware ops to read an id back.
        RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, context.getLevel().registryAccess());

        Tag encoded = RocketData.CODEC.encodeStart(ops, original)
                .resultOrPartial(error -> context.fail("encoding the rocket data failed: " + error))
                .orElse(null);
        if (encoded == null) return;

        RocketData decoded = RocketData.CODEC.decode(ops, encoded)
                .mapOrElse(Pair::getFirst, error -> {
                    context.fail("decoding the rocket data failed: " + error);
                    return null;
                });
        if (decoded == null) return;

        if (decoded.upgrade().isEmpty()) {
            context.fail("the upgrade did not survive the codec round trip");
        } else if (decoded.upgradeData().isEmpty()) {
            context.fail("the per-rocket chest count did not survive the codec round trip");
        } else if (!(decoded.upgradeData().get() instanceof StorageRocketData storage) || storage.chests() != 3) {
            context.fail("the chest count came back wrong: " + decoded.upgradeData().get());
        } else {
            context.succeed();
        }
    }

    /**
     * The reported scenario end to end: an upgraded rocket carrying cargo, parked on a pad, taken
     * through the same save and reload path the game uses on quit and rejoin.
     */
    @GameTest(template = EMPTY_STRUCTURE)
    public void dockedUpgradedRocketKeepsItsCargoAcrossReload(GameTestHelper context) {
        ServerLevel level = context.getLevel();
        LaunchPadBlockEntity pad = buildPad(context);
        BlockPos padPos = context.absolutePos(PAD_CENTER);

        RocketEntity rocket = dockRocket(context, level, pad, padPos);
        rocket.setData(storageRocketData());
        int slots = rocket.getStorageSlotCount();
        if (slots <= 0) {
            context.fail("the storage upgrade granted no cargo slots to begin with (" + slots + ")");
            return;
        }
        rocket.getVehicleInventory().setItem(0, new ItemStack(Items.DIAMOND, 5));

        CompoundTag rocketTag = new CompoundTag();
        rocket.save(rocketTag);
        CompoundTag padTag = pad.saveWithFullMetadata(level.registryAccess());
        rocket.setRemoved(Entity.RemovalReason.UNLOADED_TO_CHUNK);

        pad.loadWithComponents(padTag, level.registryAccess());
        Entity loaded = EntityType.loadEntityRecursive(rocketTag, level, entity -> entity);
        if (!(loaded instanceof RocketEntity reloaded)) {
            context.fail("the rocket did not come back from NBT (got " + loaded + ")", PAD_CENTER);
            return;
        }
        level.addFreshEntity(reloaded);

        if (reloaded.upgrade() == null) {
            context.fail("the reloaded rocket lost its storage upgrade", PAD_CENTER);
        } else if (reloaded.getStorageSlotCount() != slots) {
            context.fail("cargo slots changed across the reload: " + slots + " -> " + reloaded.getStorageSlotCount(), PAD_CENTER);
        } else if (!reloaded.getVehicleInventory().getItem(0).is(Items.DIAMOND)) {
            context.fail("the reloaded rocket's cargo was wiped (found "
                    + reloaded.getVehicleInventory().getItem(0) + ")", PAD_CENTER);
        } else {
            context.succeed();
        }
    }

    /**
     * The cargo hold is sized from the storage upgrade on every access. If that upgrade ever fails
     * to resolve for a single call - data not synced yet, registry not reachable yet - the hold must
     * not be destroyed, because nothing puts it back once the upgrade resolves again.
     */
    @GameTest(template = EMPTY_STRUCTURE)
    public void losingTheUpgradeDoesNotDestroyCargoAlreadyLoaded(GameTestHelper context) {
        TestRocket rocket = new TestRocket(context.getLevel());
        rocket.setData(storageRocketData());

        int slots = rocket.getStorageSlotCount();
        int lastSlot = rocket.getVehicleInventory().getContainerSize() - 1;
        rocket.getVehicleInventory().setItem(lastSlot, new ItemStack(Items.DIAMOND, 5));

        // Whatever the cause, this is what it looks like from the hold's point of view.
        rocket.setData(RocketPrefabs.TIER_1);
        if (rocket.getStorageSlotCount() != 0) {
            context.fail("expected the bare tier 1 data to grant no cargo slots");
            return;
        }

        if (!rocket.getVehicleInventory().getItem(lastSlot).is(Items.DIAMOND)) {
            context.fail("cargo in slot " + lastSlot + " of " + slots
                    + " was destroyed when the upgrade stopped resolving");
            return;
        }

        // ...and it must come back intact once the upgrade resolves again.
        rocket.setData(storageRocketData());
        if (!rocket.getVehicleInventory().getItem(lastSlot).is(Items.DIAMOND)) {
            context.fail("cargo did not survive the upgrade resolving again");
        } else {
            context.succeed();
        }
    }

    /** Exactly what RocketWorkbenchMenu#createRocketData builds for a tier 1 rocket with 3 chests. */
    private static RocketData storageRocketData() {
        RocketData base = RocketPrefabs.TIER_1;
        return new RocketData(
                base.cone(), base.body(), base.fin(), base.booster(), base.engine(),
                Optional.of(new EitherHolder<>(GCRocketParts.STORAGE_UPGRADE)),
                Optional.of(new StorageRocketData(3)),
                base.color());
    }

    private static final class TestRocket extends RocketEntity {
        private TestRocket(Level level) {
            super(GCEntityTypes.ROCKET, level);
        }

        private void readData(CompoundTag tag) {
            super.readAdditionalSaveData(tag);
        }

        private void writeData(CompoundTag tag) {
            super.addAdditionalSaveData(tag);
        }
    }

    private LaunchPadBlockEntity buildPad(GameTestHelper context) {
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                context.setBlock(PAD_CENTER.offset(x, 0, z), GCBlocks.ROCKET_LAUNCH_PAD);
            }
        }
        AbstractLaunchPad.Part part = context.getBlockState(PAD_CENTER).getValue(AbstractLaunchPad.PART);
        if (part != AbstractLaunchPad.Part.CENTER) {
            context.fail("the 3x3 launch pad did not form (centre is " + part + ")", PAD_CENTER);
        }
        return context.getBlockEntity(PAD_CENTER);
    }

    private RocketEntity dockRocket(GameTestHelper context, ServerLevel level, LaunchPadBlockEntity pad, BlockPos padPos) {
        RocketEntity rocket = new RocketEntity(GCEntityTypes.ROCKET, level);
        rocket.setData(RocketPrefabs.TIER_1);
        rocket.setPad(pad);
        rocket.setOldPosAndRot();
        rocket.absMoveTo(padPos.getX() + 0.5D, padPos.getY() + 0.5D, padPos.getZ() + 0.5D);
        level.addFreshEntity(rocket);
        pad.setDockedEntity(rocket);
        return rocket;
    }
}
