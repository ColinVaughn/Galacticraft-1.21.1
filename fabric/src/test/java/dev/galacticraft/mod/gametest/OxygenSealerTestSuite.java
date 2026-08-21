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

import dev.galacticraft.api.gas.Gases;
import dev.galacticraft.machinelib.api.machine.configuration.RedstoneMode;
import dev.galacticraft.mod.content.GCBlocks;
import dev.galacticraft.mod.content.block.entity.machine.OxygenSealerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public final class OxygenSealerTestSuite implements GalacticraftGameTest {
    /**
     * Sealers only do anything in a level that is not breathable to begin with, and breathability is a
     * property of the whole level rather than of one test's plot. These tests therefore share a batch,
     * which holds the level in vacuum for as long as any of them is running.
     */
    private static final String BATCH = "galacticraft:oxygen_sealer";
    /**
     * Invalidating a seal drops every sealed block in the level, not only the ones behind the block that
     * changed, so the test that does it gets a batch of its own rather than pulling the air out from
     * under the tests it would otherwise run beside.
     */
    private static final String INVALIDATION_BATCH = "galacticraft:oxygen_sealer_invalidation";
    /**
     * Cutting a sealer's redstone means changing a block, which invalidates every seal in the level, so
     * this gets a batch to itself for the same reason {@link #INVALIDATION_BATCH} exists.
     */
    private static final String REDSTONE_BATCH = "galacticraft:oxygen_sealer_redstone";
    /**
     * Switching a sealer off has to leave the room unbreathable, and any test laying a block anywhere in
     * the level unseals every room for a tick - which would let that check pass for the wrong reason. So
     * this one runs alone too.
     */
    private static final String SWITCH_OFF_BATCH = "galacticraft:oxygen_sealer_switch_off";
    private static boolean levelWasBreathable = true;

    /** A hollow stone shell spanning this cube, so the interior is the positions from 1 to 3. */
    private static final int ROOM_SIZE = 5;
    private static final BlockPos FIRST_SEALER = new BlockPos(1, 1, 1);
    private static final BlockPos SECOND_SEALER = new BlockPos(3, 1, 3);
    /** An interior position neither sealer occupies. */
    private static final BlockPos INTERIOR = new BlockPos(2, 2, 2);
    /** A wall of the room, so breaking it opens the room to the vacuum outside. */
    private static final BlockPos WALL = new BlockPos(2, 0, 2);
    /** Interior, against {@link #FIRST_SEALER}, so a block here powers that sealer. */
    private static final BlockPos SIGNAL_SOURCE = new BlockPos(2, 1, 1);
    /** Long enough for the machines to tick their resource flags and the sealer manager to run. */
    private static final int SETTLE_TICKS = 6;
    /** How long {@link #await} keeps looking before giving up, comfortably inside the test timeout. */
    private static final int DEADLINE_TICKS = 100;

    @BeforeBatch(batch = BATCH)
    public void emptyTheAir(ServerLevel level) {
        vacuum(level);
    }

    @AfterBatch(batch = BATCH)
    public void putTheAirBack(ServerLevel level) {
        restoreAir(level);
    }

    @BeforeBatch(batch = INVALIDATION_BATCH)
    public void emptyTheAirToInvalidate(ServerLevel level) {
        vacuum(level);
    }

    @AfterBatch(batch = INVALIDATION_BATCH)
    public void putTheAirBackAfterInvalidating(ServerLevel level) {
        restoreAir(level);
    }

    @BeforeBatch(batch = REDSTONE_BATCH)
    public void emptyTheAirToCutTheSignal(ServerLevel level) {
        vacuum(level);
    }

    @AfterBatch(batch = REDSTONE_BATCH)
    public void putTheAirBackAfterCuttingTheSignal(ServerLevel level) {
        restoreAir(level);
    }

    @BeforeBatch(batch = SWITCH_OFF_BATCH)
    public void emptyTheAirToSwitchOff(ServerLevel level) {
        vacuum(level);
    }

    @AfterBatch(batch = SWITCH_OFF_BATCH)
    public void putTheAirBackAfterSwitchingOff(ServerLevel level) {
        restoreAir(level);
    }

    private static void vacuum(ServerLevel level) {
        levelWasBreathable = level.getDefaultBreathable();
        level.setDefaultBreathable(false);
    }

    private static void restoreAir(ServerLevel level) {
        level.setDefaultBreathable(levelWasBreathable);
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = INVALIDATION_BATCH, timeoutTicks = 200)
    public void blockChangeInvalidatesSealedState(GameTestHelper context) {
        buildRoom(context);
        OxygenSealerBlockEntity sealer = placeFuelledSealer(context, FIRST_SEALER);

        await(context, sealer::isSealed, "the sealer never sealed the room to begin with", () -> {
            context.setBlock(WALL, Blocks.AIR);
            check(context, () -> {
                if (sealer.isSealed()) return "opening the room did not invalidate the oxygen sealer";
                if (context.getLevel().isBreathable(context.absolutePos(INTERIOR))) return "the opened room stayed breathable";
                return null;
            });
        });
    }

    /**
     * A control for {@link #twoSealersSealARoomEitherCouldSealAlone}: one sealer on its own has to seal
     * this room, otherwise a failure over there says nothing about how sealers combine.
     */
    @GameTest(template = EMPTY_STRUCTURE, batch = BATCH, timeoutTicks = 200)
    public void oneSealerSealsASmallRoom(GameTestHelper context) {
        buildRoom(context);
        OxygenSealerBlockEntity sealer = placeFuelledSealer(context, FIRST_SEALER);

        await(context, () -> sealer.isSealed() && context.getLevel().isBreathable(context.absolutePos(INTERIOR)),
                "the sealer never sealed the room and made it breathable", () -> context.succeed());
    }

    /**
     * Two sealers sharing a room that is well within a single sealer's volume budget. Both are inside the
     * same enclosure, so both belong to one sealed space - the second must not invalidate the first.
     */
    @GameTest(template = EMPTY_STRUCTURE, batch = BATCH, timeoutTicks = 200)
    public void twoSealersSealARoomEitherCouldSealAlone(GameTestHelper context) {
        buildRoom(context);
        OxygenSealerBlockEntity first = placeFuelledSealer(context, FIRST_SEALER);
        OxygenSealerBlockEntity second = placeFuelledSealer(context, SECOND_SEALER);

        await(context,
                () -> first.isSealed() && second.isSealed() && context.getLevel().isBreathable(context.absolutePos(INTERIOR)),
                "two sealers sharing a room never both held it", () -> context.succeed());
    }

    /**
     * Consumption has to follow the seal: a sealer that is holding a room has to be paying for it in
     * oxygen, whether or not it is sharing the room with another sealer.
     */
    @GameTest(template = EMPTY_STRUCTURE, batch = BATCH, timeoutTicks = 200)
    public void bothSealersOfASharedRoomSpendOxygen(GameTestHelper context) {
        buildRoom(context);
        OxygenSealerBlockEntity first = placeFuelledSealer(context, FIRST_SEALER);
        OxygenSealerBlockEntity second = placeFuelledSealer(context, SECOND_SEALER);
        long stocked = first.fluidStorage().slot(OxygenSealerBlockEntity.OXYGEN_TANK).getAmount();

        await(context, () -> first.isSealed() && second.isSealed(), "two sealers sharing a room never both held it", () ->
                check(context, () -> {
                    long firstLeft = first.fluidStorage().slot(OxygenSealerBlockEntity.OXYGEN_TANK).getAmount();
                    long secondLeft = second.fluidStorage().slot(OxygenSealerBlockEntity.OXYGEN_TANK).getAmount();
                    if (firstLeft == stocked) return "the first sealer held the room without spending any oxygen";
                    if (secondLeft == stocked) return "the second sealer held the room without spending any oxygen";
                    return null;
                }));
    }

    /**
     * A sealer switched off by redstone spends nothing, so it must not go on holding a room either -
     * the room it was paying for has to go back to vacuum.
     */
    @GameTest(template = EMPTY_STRUCTURE, batch = SWITCH_OFF_BATCH, timeoutTicks = 200)
    public void aSwitchedOffSealerStopsHoldingTheRoom(GameTestHelper context) {
        buildRoom(context);
        OxygenSealerBlockEntity sealer = placeFuelledSealer(context, FIRST_SEALER);

        await(context, sealer::isSealed, "the sealer never sealed the room to begin with", () -> {
            // No redstone anywhere near it, so requiring a high signal switches it off.
            sealer.setRedstoneMode(RedstoneMode.HIGH);

            runAt(context, SETTLE_TICKS, () -> check(context, () -> {
                if (sealer.isSealed()) return "a switched-off sealer went on holding the room";
                if (context.getLevel().isBreathable(context.absolutePos(INTERIOR))) return "the room stayed breathable without a sealer paying for it";
                return null;
            }));
        });
    }

    /**
     * The reported case, driven the way a player drives it: the sealer is set to run only on a high
     * signal and given one, and then the signal is taken away. Losing the signal has to put the room back
     * to vacuum, not just stop the fan.
     */
    @GameTest(template = EMPTY_STRUCTURE, batch = REDSTONE_BATCH, timeoutTicks = 200)
    public void cuttingTheRedstoneSignalStopsHoldingTheRoom(GameTestHelper context) {
        buildRoom(context);
        OxygenSealerBlockEntity sealer = placeFuelledSealer(context, FIRST_SEALER);
        // Placing this against the sealer is what tells the machine it is powered, through the
        // block's own neighbour-change hook - the same path a lever or a torch goes through.
        context.setBlock(SIGNAL_SOURCE, Blocks.REDSTONE_BLOCK);
        sealer.setRedstoneMode(RedstoneMode.HIGH);

        await(context, () -> sealer.isSealed() && context.getLevel().isBreathable(context.absolutePos(INTERIOR)),
                "a powered sealer set to run on a high signal never sealed the room and made it breathable", () -> {
            context.setBlock(SIGNAL_SOURCE, Blocks.AIR);

            runAt(context, SETTLE_TICKS, () -> check(context, () -> {
                if (sealer.isSealed()) return "the sealer went on holding the room after its redstone signal was cut";
                if (context.getLevel().isBreathable(context.absolutePos(INTERIOR))) return "the room stayed breathable after the sealer lost its redstone signal";
                return null;
            }));
        });
    }

    /**
     * A sealer with no room to hold is venting into the open. As in Galacticraft Legacy that costs
     * oxygen - more of it than holding a seal does - so a leak is worth finding.
     */
    @GameTest(template = EMPTY_STRUCTURE, batch = BATCH, timeoutTicks = 200)
    public void aSealerWithNoRoomToHoldSpendsOxygen(GameTestHelper context) {
        // No walls, so the flood fill runs out of budget instead of finding an enclosure.
        context.setBlock(FIRST_SEALER.below(), Blocks.STONE);
        context.setBlock(FIRST_SEALER.above(), Blocks.AIR);
        OxygenSealerBlockEntity sealer = placeFuelledSealer(context, FIRST_SEALER);
        long stocked = sealer.fluidStorage().slot(OxygenSealerBlockEntity.OXYGEN_TANK).getAmount();

        runAt(context, SETTLE_TICKS, () -> check(context, () -> {
            if (sealer.isSealed()) return "a sealer in the open somehow sealed something";
            if (!sealer.canSeal()) return "the sealer was not running at all, so this proves nothing";
            long left = sealer.fluidStorage().slot(OxygenSealerBlockEntity.OXYGEN_TANK).getAmount();
            if (left == stocked) return "a sealer venting into the open spent no oxygen";
            return null;
        }));
    }

    /** Builds a hollow stone shell with a breathable-once-sealed interior of 1..3 on every axis. */
    private static void buildRoom(GameTestHelper context) {
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

    private static OxygenSealerBlockEntity placeFuelledSealer(GameTestHelper context, BlockPos pos) {
        context.setBlock(pos, GCBlocks.OXYGEN_SEALER);
        OxygenSealerBlockEntity sealer = context.getBlockEntity(pos);
        sealer.energyStorage().setEnergy(sealer.energyStorage().getCapacity());
        sealer.fluidStorage().slot(OxygenSealerBlockEntity.OXYGEN_TANK)
                .set(Gases.OXYGEN, OxygenSealerBlockEntity.MAX_OXYGEN);
        return sealer;
    }

    /**
     * Waits for {@code ready} to hold, looking once a tick, then runs {@code then}.
     *
     * Sealing settles over several ticks, and laying any block anywhere in the level drops every seal
     * in it for a tick before they are recomputed - so while the other tests in a batch are still
     * building their rooms, sampling at a fixed tick is a race. Only ever wait for a condition the test
     * needs to be true: waiting for one to be false would be satisfied by exactly that flicker.
     */
    private void await(GameTestHelper context, BooleanSupplier ready, String timedOut, Runnable then) {
        await(context, DEADLINE_TICKS, ready, timedOut, then);
    }

    private void await(GameTestHelper context, int ticksLeft, BooleanSupplier ready, String timedOut, Runnable then) {
        if (ready.getAsBoolean()) {
            then.run();
        } else if (ticksLeft <= 0) {
            context.fail(timedOut);
        } else {
            runNext(context, () -> await(context, ticksLeft - 1, ready, timedOut, then));
        }
    }

    /** Settles the check's outcome, where a null return means the check passed. */
    private static void check(GameTestHelper context, Supplier<String> check) {
        String failure = check.get();
        if (failure != null) {
            context.fail(failure);
        } else {
            context.succeed();
        }
    }
}
