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

package dev.galacticraft.mod.gametest;

import dev.galacticraft.mod.content.GCEntityTypes;
import dev.galacticraft.mod.content.entity.jupiter.StormheartLeviathanEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Each Leviathan attack against a control: the breath against cover, the sweep against facing, the roar against creative. */
public final class LeviathanCombatTestSuite implements GalacticraftGameTest {
    /** A free (pedestal-less, so unshielded) Leviathan at {@code pos}, facing +X. */
    private static StormheartLeviathanEntity leviathan(GameTestHelper helper, BlockPos pos) {
        var creature = helper.spawn(GCEntityTypes.STORMHEART_LEVIATHAN, pos);
        creature.setNoAi(true);
        creature.moveTo(creature.getX(), creature.getY(), creature.getZ(), -90, 0);
        creature.setYBodyRot(-90);
        return creature;
    }

    private ServerPlayer survivor(GameTestHelper helper, Vec3 at) {
        var player = this.makeSurvivalServerPlayer(helper);
        player.moveTo(at.x, at.y, at.z);
        // Connected test players tick; keep them hovering where they were put rather than falling while the test waits.
        player.setNoGravity(true);
        // Force the chunk so players tick and their 60-tick spawn protection expires.
        // Keep chunks forced until the disposable GameTest world closes; unforcing can pause neighboring tests.
        helper.getLevel().setChunkForced(player.getBlockX() >> 4, player.getBlockZ() >> 4, true);
        return player;
    }

    private static void leave(GameTestHelper helper, StormheartLeviathanEntity creature, ServerPlayer... players) {
        creature.discard();
        for (ServerPlayer player : players) helper.getLevel().getServer().getPlayerList().remove(player);
    }

    /** Fresh players are spawn-invulnerable for 60 ticks; attacks are timed after it wears off. */
    private static final int AFTER_SPAWN_PROTECTION = 65;

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
    public void stormBreathHitsInTheOpenButCoverStopsIt(GameTestHelper helper) {
        var level = helper.getLevel();
        // GameTest walls each test in barrier blocks, which would stop the beam; stage it well above the box.
        var creature = leviathan(helper, new BlockPos(1, 60, 1));
        Vec3 mouth = creature.mouth();
        helper.assertTrue(mouth.x > creature.getX() + 4, "The mouth is not ahead of a Leviathan facing +X");
        var exposed = this.survivor(helper, mouth.add(9, -1.6, 0));
        var covered = this.survivor(helper, mouth.add(9, -1.6, 7));
        // a stone screen across the line to the covered player
        BlockPos screen = BlockPos.containing(mouth.add(4.5, 0, 3.5));
        for (BlockPos pos : BlockPos.betweenClosed(screen.offset(0, -2, -2), screen.offset(0, 2, 2))) level.setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
        helper.runAfterDelay(AFTER_SPAWN_PROTECTION, () -> {
            exposed.moveTo(mouth.x + 9, mouth.y - 1.6, mouth.z);
            covered.moveTo(mouth.x + 9, mouth.y - 1.6, mouth.z + 7);
            helper.assertTrue(exposed.isAlive() && covered.isAlive(), "A staged player died before the breath");
            float exposedHealth = exposed.getHealth(), coveredHealth = covered.getHealth();
            try {
                helper.assertTrue(creature.breathe(level, exposed.getEyePosition()) == 1, "The breath missed a player in the open");
                helper.assertTrue(exposed.getHealth() < exposedHealth, "The open player took no damage");
                helper.assertTrue(creature.breathe(level, covered.getEyePosition()) == 0, "The breath went through cover");
                helper.assertTrue(covered.getHealth() == coveredHealth, "The covered player was hurt");
            } finally {
                for (BlockPos pos : BlockPos.betweenClosed(screen.offset(0, -2, -2), screen.offset(0, 2, 2))) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                leave(helper, creature, exposed, covered);
            }
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
    public void tailSweepStrikesBehindNotInFront(GameTestHelper helper) {
        var level = helper.getLevel();
        var creature = leviathan(helper, new BlockPos(1, 4, 1));
        var behind = this.survivor(helper, creature.position().add(-5, 0, 0));
        var ahead = this.survivor(helper, creature.position().add(5, 0, 0));
        helper.runAfterDelay(AFTER_SPAWN_PROTECTION, () -> {
            behind.moveTo(creature.getX() - 5, creature.getY(), creature.getZ());
            ahead.moveTo(creature.getX() + 5, creature.getY(), creature.getZ());
            float aheadHealth = ahead.getHealth(), behindHealth = behind.getHealth();
            try {
                helper.assertTrue(creature.tailSweep(level) == 1, "The tail sweep should hit exactly the player behind");
                helper.assertTrue(behind.getHealth() < behindHealth, "The player behind was not hit");
                helper.assertTrue(ahead.getHealth() == aheadHealth, "The tail reached a player in front of the head");
            } finally {
                leave(helper, creature, behind, ahead);
            }
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void roarDarkensFightersButNotCreativeObservers(GameTestHelper helper) {
        var level = helper.getLevel();
        var creature = leviathan(helper, new BlockPos(1, 4, 1));
        var fighter = this.survivor(helper, creature.position().add(6, 0, 0));
        var observer = helper.makeMockServerPlayerInLevel();
        observer.moveTo(creature.getX() - 6, creature.getY(), creature.getZ());
        try {
            helper.assertTrue(creature.roar(level) >= 1, "The roar affected nobody");
            helper.assertTrue(fighter.hasEffect(MobEffects.DARKNESS), "The roar did not darken a fighter");
            helper.assertTrue(!observer.hasEffect(MobEffects.DARKNESS), "The roar darkened a creative observer");
        } finally {
            leave(helper, creature, fighter, observer);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
    public void aLiveLeviathanOpensWithItsRoar(GameTestHelper helper) {
        var creature = helper.spawn(GCEntityTypes.STORMHEART_LEVIATHAN, new BlockPos(1, 4, 1));
        var fighter = this.survivor(helper, creature.position().add(8, 0, 0));
        boolean[] roared = {false};
        helper.onEachTick(() -> { if (creature.action() == StormheartLeviathanEntity.Action.ROAR) roared[0] = true; });
        helper.succeedWhen(() -> {
            helper.assertTrue(roared[0], "The Leviathan never roared");
            helper.assertTrue(fighter.hasEffect(MobEffects.DARKNESS), "Its opening roar did not land");
            leave(helper, creature, fighter);
        });
    }
}
