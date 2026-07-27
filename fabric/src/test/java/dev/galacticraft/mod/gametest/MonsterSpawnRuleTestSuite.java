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

import dev.galacticraft.mod.content.GCEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;

import java.util.function.BooleanSupplier;

/**
 * Galacticraft's evolved mobs have to obey the light, like the vanilla mobs they are dressed-up copies of.
 *
 * <p>A mob type vanilla has never been told about is not merely spawned loosely, it is spawned with no
 * checks at all - {@link SpawnPlacements#checkSpawnRules} returns true for an unknown type - so leaving
 * the table empty put evolved mobs inside lit, sealed rooms.
 *
 * @see dev.galacticraft.mod.content.GCSpawnPlacements
 */
public final class MonsterSpawnRuleTestSuite implements GalacticraftGameTest {
    /** Monsters never pass their spawn check on peaceful, which would make the dark control meaningless. */
    private static final String BATCH = "galacticraft:monster_spawn_rules";
    private static Difficulty previousDifficulty = Difficulty.NORMAL;

    private static final int ROOM_SIZE = 5;
    /** Interior floor level, with stone directly beneath it for a mob to stand on. */
    private static final BlockPos FLOOR = new BlockPos(2, 1, 2);
    /** An interior position one block above the floor, where a lamp lights the whole room. */
    private static final BlockPos LAMP = new BlockPos(2, 2, 2);
    /** How long {@link #awaitLight} waits for the light engine, comfortably inside the test timeout. */
    private static final int DEADLINE_TICKS = 100;

    @BeforeBatch(batch = BATCH)
    public void makeItDangerous(ServerLevel level) {
        previousDifficulty = level.getDifficulty();
        level.getServer().setDifficulty(Difficulty.NORMAL, true);
    }

    @AfterBatch(batch = BATCH)
    public void putTheDifficultyBack(ServerLevel level) {
        level.getServer().setDifficulty(previousDifficulty, true);
    }

    /** The reported case: a well-lit room on the Moon must not spawn anything. */
    @GameTest(template = EMPTY_STRUCTURE, batch = BATCH, timeoutTicks = 200)
    public void aLitRoomTurnsMonstersAway(GameTestHelper context) {
        buildSealedRoom(context);
        context.setBlock(LAMP, Blocks.GLOWSTONE);

        awaitLight(context, () -> brightness(context, LightLayer.BLOCK) > 0,
                "the lamp never lit the room, so this proves nothing", () -> {
            if (maySpawnAtFloor(context)) {
                context.fail("an evolved zombie was allowed to spawn in a room lit to block light "
                        + brightness(context, LightLayer.BLOCK));
            } else {
                context.succeed();
            }
        });
    }

    /**
     * The control. Without this, {@link #aLitRoomTurnsMonstersAway} would pass just as well if the mobs
     * were barred from spawning everywhere, which is a different bug.
     */
    @GameTest(template = EMPTY_STRUCTURE, batch = BATCH, timeoutTicks = 200)
    public void anUnlitRoomStillSpawnsMonsters(GameTestHelper context) {
        buildSealedRoom(context);

        // The roof only shuts the sky out once the light engine has caught up, and until it has, the
        // sky-light test inside the spawn rules is compared against a random number - so sampling early
        // fails perhaps half the time.
        awaitLight(context, () -> brightness(context, LightLayer.SKY) == 0 && brightness(context, LightLayer.BLOCK) == 0,
                "the room never went dark, so this proves nothing", () -> {
            if (maySpawnAtFloor(context)) {
                context.succeed();
            } else {
                ServerLevel level = context.getLevel();
                context.fail("evolved zombies were turned away from a dark room too, so nothing can spawn at all"
                        + " [difficulty=" + level.getDifficulty()
                        + " maxLocalRaw=" + level.getMaxLocalRawBrightness(context.absolutePos(FLOOR)) + "]");
            }
        });
    }

    /**
     * Position rules travel with the light rules: an unregistered type also falls back to
     * {@link SpawnPlacementTypes#NO_RESTRICTIONS}, which lets mobs appear in mid-air.
     */
    @GameTest(template = EMPTY_STRUCTURE, batch = BATCH, timeoutTicks = 200)
    public void evolvedMobsNeedGroundToStandOn(GameTestHelper context) {
        if (SpawnPlacements.getPlacementType(GCEntityTypes.EVOLVED_ZOMBIE) != SpawnPlacementTypes.ON_GROUND) {
            context.fail("evolved zombies have no placement rule, so they can spawn in mid-air");
        } else if (SpawnPlacements.getPlacementType(GCEntityTypes.EVOLVED_CREEPER) != SpawnPlacementTypes.ON_GROUND) {
            context.fail("evolved creepers have no placement rule, so they can spawn in mid-air");
        } else {
            context.succeed();
        }
    }

    /** Waits for the light engine to settle on {@code ready} before letting the test judge anything. */
    private void awaitLight(GameTestHelper context, BooleanSupplier ready, String timedOut, Runnable then) {
        awaitLight(context, DEADLINE_TICKS, ready, timedOut, then);
    }

    private void awaitLight(GameTestHelper context, int ticksLeft, BooleanSupplier ready, String timedOut, Runnable then) {
        if (ready.getAsBoolean()) {
            then.run();
        } else if (ticksLeft <= 0) {
            context.fail(timedOut);
        } else {
            runNext(context, () -> awaitLight(context, ticksLeft - 1, ready, timedOut, then));
        }
    }

    private static int brightness(GameTestHelper context, LightLayer layer) {
        return context.getLevel().getBrightness(layer, context.absolutePos(FLOOR));
    }

    private static boolean maySpawnAtFloor(GameTestHelper context) {
        ServerLevel level = context.getLevel();
        return SpawnPlacements.checkSpawnRules(GCEntityTypes.EVOLVED_ZOMBIE, level, MobSpawnType.NATURAL,
                context.absolutePos(FLOOR), level.random);
    }

    /** A hollow stone shell, so the interior is shut off from the sky and only lit by what is inside it. */
    private static void buildSealedRoom(GameTestHelper context) {
        for (int x = 0; x < ROOM_SIZE; x++) {
            for (int y = 0; y < ROOM_SIZE; y++) {
                for (int z = 0; z < ROOM_SIZE; z++) {
                    boolean shell = x == 0 || y == 0 || z == 0
                            || x == ROOM_SIZE - 1 || y == ROOM_SIZE - 1 || z == ROOM_SIZE - 1;
                    context.setBlock(new BlockPos(x, y, z), shell ? Blocks.STONE : Blocks.AIR);
                }
            }
        }
    }
}
