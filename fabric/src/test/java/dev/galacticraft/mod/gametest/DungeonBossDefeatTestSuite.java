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

package dev.galacticraft.mod.gametest;

import dev.galacticraft.mod.content.GCBlocks;
import dev.galacticraft.mod.content.GCEntityTypes;
import dev.galacticraft.mod.content.block.entity.DungeonSpawnerBlockEntity;
import dev.galacticraft.mod.content.entity.boss.SpiderBoss;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * A dungeon boss only counts as defeated once its 200-tick death animation finishes, and the boss
 * keeps ticking its "the room is empty, despawn me" check the whole way through that animation.
 * Kill the boss without standing in the room - a thrown potion, a lingering cloud, lava - and the
 * despawn check fires first, removing the corpse as if the boss had escaped and rolling back the
 * kill: no key, no reward chest, and the boss respawns.
 */
public final class DungeonBossDefeatTestSuite implements GalacticraftGameTest {
    private static final BlockPos SPAWNER_POS = new BlockPos(1, 1, 1);
    private static final BlockPos BOSS_POS = new BlockPos(3, 1, 3);

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300)
    public void bossKilledWithNoPlayerInTheRoomStillCountsAsDefeated(GameTestHelper context) {
        DungeonSpawnerBlockEntity spawner = placeSpawner(context);
        SpiderBoss boss = spawnLinkedBoss(context, spawner);

        boss.hurt(context.getLevel().damageSources().magic(), 1000.0F);
        if (!boss.isDeadOrDying()) {
            context.fail("Indirect magic damage did not kill the boss");
            return;
        }

        runAt(context, 215, () -> {
            if (!spawner.isBossDefeated) {
                context.fail("A boss killed by indirect damage was not counted as defeated");
            } else if (spawner.spawned) {
                context.fail("The spawner still considers its defeated boss to be spawned");
            } else {
                context.succeed();
            }
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
    public void dyingBossIsNotDespawnedAsIfItEscaped(GameTestHelper context) {
        DungeonSpawnerBlockEntity spawner = placeSpawner(context);
        SpiderBoss boss = spawnLinkedBoss(context, spawner);

        boss.hurt(context.getLevel().damageSources().magic(), 1000.0F);

        runAt(context, 20, () -> {
            if (boss.isRemoved()) {
                context.fail("The dying boss was removed before its death animation finished");
            } else {
                context.succeed();
            }
        });
    }

    /**
     * Control for {@link #dyingBossIsNotDespawnedAsIfItEscaped}: proves this setup really does
     * trigger the empty-room despawn, so passing that test means the fix works rather than that the
     * despawn never fired in the first place.
     */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
    public void livingBossStillDespawnsWhenTheRoomEmpties(GameTestHelper context) {
        DungeonSpawnerBlockEntity spawner = placeSpawner(context);
        SpiderBoss boss = spawnLinkedBoss(context, spawner);

        runAt(context, 20, () -> {
            if (!boss.isRemoved()) {
                context.fail("A living boss left alone in an empty room was not despawned");
            } else if (spawner.isBossDefeated) {
                context.fail("Despawning a living boss counted it as defeated");
            } else {
                context.succeed();
            }
        });
    }

    private DungeonSpawnerBlockEntity placeSpawner(GameTestHelper context) {
        context.setBlock(SPAWNER_POS, GCBlocks.BOSS_SPAWNER.defaultBlockState());
        BlockEntity blockEntity = context.getBlockEntity(SPAWNER_POS);
        if (!(blockEntity instanceof DungeonSpawnerBlockEntity spawner)) {
            throw new IllegalStateException("Boss spawner block did not create its block entity");
        }

        spawner.setEntityId(GCEntityTypes.SPIDER_BOSS, context.getLevel().getRandom());
        BlockPos origin = context.absolutePos(SPAWNER_POS);
        spawner.setRoom(new Vec3i(origin.getX(), origin.getY(), origin.getZ()), new Vec3i(6, 4, 6));
        return spawner;
    }

    /**
     * Spawns the Venus boss in a room a player has already visited and then left, which is the state
     * the room is in whenever the boss is finished off from outside it.
     */
    private SpiderBoss spawnLinkedBoss(GameTestHelper context, DungeonSpawnerBlockEntity spawner) {
        SpiderBoss boss = context.spawnWithNoFreeWill(GCEntityTypes.SPIDER_BOSS, BOSS_POS);
        boss.onBossSpawned(spawner);
        spawner.boss = boss;
        spawner.spawned = true;
        boss.entitiesWithinLast = 1;
        return boss;
    }
}
