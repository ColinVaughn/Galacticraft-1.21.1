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
import dev.galacticraft.mod.content.entity.mars.CuriosityRoverEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.block.Blocks;

/**
 * The rover is passive until hit: left alone it never harms a player standing beside it (the control for the other
 * tests), hit it and it chases its attacker down and drills them, and once that attacker is gone it calms down.
 */
public final class CuriosityRoverTestSuite implements GalacticraftGameTest {
    /** Fresh players are spawn-invulnerable for 60 ticks; anything that should hurt them waits this long. */
    private static final int AFTER_SPAWN_PROTECTION = 65;

    /** The empty structure is all air: lay a stone floor for the rover to drive on. */
    private static void floor(GameTestHelper helper) {
        for (BlockPos pos : BlockPos.betweenClosed(0, 1, 0, 7, 1, 7)) helper.setBlock(pos, Blocks.STONE);
    }

    private static CuriosityRoverEntity rover(GameTestHelper helper, int x, int z) {
        return helper.spawn(GCEntityTypes.CURIOSITY_ROVER, new BlockPos(x, 2, z));
    }

    private ServerPlayer survivor(GameTestHelper helper, int x, int z) {
        var player = this.makeSurvivalServerPlayer(helper);
        var at = helper.absoluteVec(new BlockPos(x, 2, z).getBottomCenter());
        player.moveTo(at.x, at.y, at.z);
        // A player only ticks (and so only loses its spawn protection) in an entity-ticking chunk. Never unforced.
        helper.getLevel().setChunkForced(player.getBlockX() >> 4, player.getBlockZ() >> 4, true);
        return player;
    }

    private static void leave(GameTestHelper helper, CuriosityRoverEntity rover, ServerPlayer player) {
        rover.discard();
        helper.getLevel().getServer().getPlayerList().remove(player);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 260)
    public void leftAloneItNeverAttacks(GameTestHelper helper) {
        floor(helper);
        var rover = rover(helper, 2, 2);
        var player = this.survivor(helper, 4, 2);
        helper.runAfterDelay(AFTER_SPAWN_PROTECTION, () -> {
            float health = player.getHealth();
            helper.runAfterDelay(140, () -> {
                try {
                    helper.assertTrue(!rover.isAngry() && rover.getTarget() == null, "An unprovoked rover turned on a player");
                    helper.assertTrue(player.getHealth() == health, "An unprovoked rover hurt a player");
                } finally {
                    leave(helper, rover, player);
                }
                helper.succeed();
            });
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400)
    public void hitItChasesDownAndDrillsTheAttacker(GameTestHelper helper) {
        floor(helper);
        var rover = rover(helper, 1, 1);
        // well out of reach, so it has to drive over
        var player = this.survivor(helper, 6, 6);
        boolean[] drilled = {false};
        float[] health = {0};
        helper.onEachTick(() -> { if (rover.action() == CuriosityRoverEntity.Action.ATTACK) drilled[0] = true; });
        helper.runAfterDelay(AFTER_SPAWN_PROTECTION, () -> {
            health[0] = player.getHealth();
            rover.hurt(helper.getLevel().damageSources().playerAttack(player), 1.0F);
            helper.succeedWhen(() -> {
                helper.assertTrue(rover.isAngry(), "The rover did not turn on the player who hit it");
                helper.assertTrue(drilled[0], "The rover never struck with its drill");
                helper.assertTrue(player.getHealth() < health[0], "The drill did not hurt its attacker");
                leave(helper, rover, player);
            });
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300)
    public void itCalmsDownOnceItsAttackerIsGone(GameTestHelper helper) {
        floor(helper);
        var rover = rover(helper, 1, 1);
        var player = this.survivor(helper, 6, 6);
        boolean[] provoked = {false};
        helper.runAfterDelay(5, () -> rover.hurt(helper.getLevel().damageSources().playerAttack(player), 1.0F));
        helper.onEachTick(() -> {
            if (!provoked[0] && rover.isAngry()) {
                provoked[0] = true;
                helper.getLevel().getServer().getPlayerList().remove(player);
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(provoked[0], "The rover was never provoked");
            helper.assertTrue(!rover.isAngry() && rover.getTarget() == null, "The rover is still angry at a player who left");
            rover.discard();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void theNaturalSpawnerNeverAddsRovers(GameTestHelper helper) {
        floor(helper);
        BlockPos at = helper.absolutePos(new BlockPos(3, 2, 3));
        var level = helper.getLevel();
        helper.assertTrue(!CuriosityRoverEntity.checkSpawnRules(GCEntityTypes.CURIOSITY_ROVER, level, MobSpawnType.NATURAL, at, level.random),
                "The natural spawner may top up rovers");
        // control: the same spot is fine while the chunk generates
        helper.assertTrue(CuriosityRoverEntity.checkSpawnRules(GCEntityTypes.CURIOSITY_ROVER, level, MobSpawnType.CHUNK_GENERATION, at, level.random),
                "Rovers cannot be placed on open ground at all");
        helper.succeed();
    }
}
