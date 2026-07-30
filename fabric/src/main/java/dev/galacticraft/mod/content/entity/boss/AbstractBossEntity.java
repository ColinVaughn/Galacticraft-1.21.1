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

package dev.galacticraft.mod.content.entity.boss;

import dev.galacticraft.mod.content.block.entity.DungeonSpawnerBlockEntity;
import dev.galacticraft.mod.content.item.GCItems;
import dev.galacticraft.mod.util.Translations;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

import java.util.List;

public abstract class AbstractBossEntity extends Monster {
    protected DungeonSpawnerBlockEntity spawner;

    public int entitiesWithin;
    public int entitiesWithinLast;
    private boolean defeatRecorded;

    private final ServerBossEvent bossEvent = new ServerBossEvent(this.getDisplayName(), getHealthBarColor(), BossEvent.BossBarOverlay.PROGRESS);

    public AbstractBossEntity(EntityType<? extends AbstractBossEntity> entityType, Level level) {
        super(entityType, level);
    }

    public abstract int getChestTier();

    public abstract List<ItemStack> getGuaranteedLoot(RandomSource rand);

    /**
     * Drops the key that unlocks this dungeon's treasure chest. The tier is taken from
     * {@link #getChestTier()} so the key always matches the chest the dungeon generated.
     */
    public void dropKey() {
        this.spawnAtLocation(new ItemStack(GCItems.dungeonKey(this.getChestTier())), 0.5F);
    }

    public abstract BossEvent.BossBarColor getHealthBarColor();

    @Override
    protected void customServerAiStep() {
        this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());
        super.customServerAiStep();
    }

    @Override
    protected void tickDeath() {
        ++this.deathTime;

//        if (this.deathTime >= 180 && this.deathTime <= 200) {
//            final float x = (this.random.nextFloat() - 0.5F) * this.width;
//            final float y = (this.random.nextFloat() - 0.5F) * (this.height / 2.0F);
//            final float z = (this.random.nextFloat() - 0.5F) * this.width;
//            this.world.spawnParticle(EnumParticleTypes.EXPLOSION_HUGE, this.posX + x, this.posY + 2.0D + y, this.posZ + z, 0.0D, 0.0D, 0.0D);
//        }
//
//        int i;
//        int j;
//
//        if (!this.world.isRemote) {
//            if (this.deathTime >= 180 && this.deathTime % 5 == 0) {
//                GalacticraftCore.packetPipeline.sendToAllAround(new PacketSimple(PacketSimple.EnumSimplePacket.C_PLAY_SOUND_EXPLODE, GCCoreUtil.getDimensionID(this.world), new Object[]
//                        {}), new NetworkRegistry.TargetPoint(GCCoreUtil.getDimensionID(this.world), this.posX, this.posY, this.posZ, 40.0D));
//            }
//
//            if (this.deathTime > 150 && this.deathTime % 5 == 0) {
//                i = 30;
//
//                while (i > 0) {
//                    j = EntityXPOrb.getXPSplit(i);
//                    i -= j;
//                    this.world.spawnEntity(new EntityXPOrb(this.world, this.posX, this.posY, this.posZ, j));
//                }
//            }
//        }

       if (this.deathTime >= 200 && !this.level().isClientSide) {
//            i = 20;
//
//            while (i > 0) {
//                j = EntityXPOrb.getXPSplit(i);
//                i -= j;
//                this.world.spawnEntity(new EntityXPOrb(this.world, this.posX, this.posY, this.posZ, j));
//            }
//
            this.recordDefeat();

            this.level().broadcastEntityEvent(this, EntityEvent.POOF);
            this.remove(Entity.RemovalReason.KILLED);
       }
    }

    /**
     * Hands out this kill's rewards and marks the dungeon cleared, once. Normally this happens when
     * the death animation runs out, but the animation lasts ten seconds and anything that destroys
     * the corpse before then - despawning because the room emptied, {@code /kill}, a mod - would
     * otherwise throw the kill away and let the boss respawn.
     */
    private void recordDefeat() {
        if (this.defeatRecorded) {
            return;
        }
        this.defeatRecorded = true;

        this.fillRewardChest();
        this.dropKey();

        if (this.spawner != null) {
            this.spawner.isBossDefeated = true;
            this.spawner.boss = null;
            this.spawner.spawned = false;
            this.spawner.lastKillTime = Util.getMillis();
            this.spawner.setChanged();
        }
    }

    private void fillRewardChest() {
        List<ItemStack> rewards = this.getGuaranteedLoot(this.random);
        if (rewards.isEmpty()) {
            return;
        }

        ChestBlockEntity chest = this.getRewardChest();
        if (chest == null) {
            for (ItemStack reward : rewards) {
                if (!reward.isEmpty()) {
                    this.spawnAtLocation(reward, 0.5F);
                }
            }
            return;
        }

        chest.unpackLootTable(null);
        boolean changed = false;
        for (ItemStack reward : rewards) {
            if (reward.isEmpty()) {
                continue;
            }
            int slot = getRandomEmptySlot(chest, this.random);
            if (slot >= 0) {
                chest.setItem(slot, reward);
                changed = true;
            } else {
                this.spawnAtLocation(reward, 0.5F);
            }
        }
        if (changed) {
            chest.setChanged();
        }
    }

    private ChestBlockEntity getRewardChest() {
        BlockPos chestPos = this.spawner == null ? null : this.spawner.getChestPos();
        if (chestPos == null || !(this.level().getBlockEntity(chestPos) instanceof ChestBlockEntity chest)) {
            return null;
        }

        return this.distanceToSqr(chestPos.getX() + 0.5D, chestPos.getY() + 0.5D, chestPos.getZ() + 0.5D) < 1000.0D * 1000.0D ? chest : null;
    }

    private static int getRandomEmptySlot(ChestBlockEntity chest, RandomSource random) {
        int start = random.nextInt(chest.getContainerSize());
        for (int i = 0; i < chest.getContainerSize(); i++) {
            int slot = (start + i) % chest.getContainerSize();
            if (chest.getItem(slot).isEmpty()) {
                return slot;
            }
        }
        return -1;
    }

    @Override
    public void aiStep() {
        if (this.level().isClientSide) {
            this.setHealth(this.getHealth());
        }

        // aiStep keeps running throughout the death animation, so this check has to skip a boss that
        // is already dead - otherwise finishing the boss off from outside the room (a thrown potion,
        // a lingering cloud, lava) despawns the corpse as an escape and undoes the kill.
        if (this.spawner != null && !this.isDeadOrDying()) {
            List<Player> playersWithin = this.level().getEntitiesOfClass(Player.class, this.spawner.getRangeBounds());

            this.entitiesWithin = playersWithin.size();

            if (this.entitiesWithin == 0 && this.entitiesWithinLast != 0) {
                List<Player> entitiesWithin2 = this.level().getEntitiesOfClass(Player.class, this.spawner.getRangeBoundsPlus11());

                for (Player p : entitiesWithin2) {
                    p.sendSystemMessage(Component.translatable(Translations.Boss.SKELETON_BOSS_DESPAWN));
                }

                this.remove(RemovalReason.DISCARDED);
                // Note: spawner.isBossDefeated is false, so the boss will
                // respawn if any player comes back inside the room

                return;
            }

            this.entitiesWithinLast = this.entitiesWithin;
        }

        super.aiStep();
    }

    @Override
    public void remove(RemovalReason reason) {
        // Reasons that do not destroy the entity (chunk unload) are left alone: the boss is coming
        // back to finish its death animation, and rewarding here would hand out the loot twice.
        if (this.spawner != null && !this.level().isClientSide && reason.shouldDestroy()) {
            if (this.isDeadOrDying()) {
                this.recordDefeat();
            } else if (reason == RemovalReason.DISCARDED) {
                // Gave up on the room while still alive, so it gets to respawn.
                this.spawner.isBossDefeated = false;
                this.spawner.boss = null;
                this.spawner.spawned = false;
            }
        }

        super.remove(reason);
    }

    public void onBossSpawned(DungeonSpawnerBlockEntity spawner) {
        this.spawner = spawner;
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
}
