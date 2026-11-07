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
import dev.galacticraft.impl.internal.accessor.ChunkOxygenAccessor;
import dev.galacticraft.impl.internal.accessor.ChunkOxygenSyncer;
import dev.galacticraft.machinelib.api.block.MachineBlock;
import dev.galacticraft.machinelib.api.machine.configuration.RedstoneMode;
import dev.galacticraft.mod.content.GCBlocks;
import dev.galacticraft.mod.content.block.entity.machine.OxygenBubbleDistributorBlockEntity;
import dev.galacticraft.mod.content.block.entity.machine.OxygenSealerBlockEntity;
import dev.galacticraft.mod.content.block.machine.ResourceStorageBlock;
import dev.galacticraft.mod.machine.SealerManager;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

public final class OxygenSealerTestSuite implements GalacticraftGameTest {
    /**
     * Sealers only do anything in a level that is not breathable to begin with, and breathability is a
     * property of the whole level rather than of one test's plot. These tests therefore share a batch,
     * which holds the level in vacuum for as long as any of them is running.
     */
    private static final String BATCH = "galacticraft:oxygen_sealer";
    /**
     * Isolate room invalidation: a recompute processes all rooms, so cross-room regressions must
     * fail the test that triggered them.
     */
    private static final String INVALIDATION_BATCH = "galacticraft:oxygen_sealer_invalidation";
    /**
     * Separate batch for the same invalidation risk as {@link #INVALIDATION_BATCH}.
     */
    private static final String REDSTONE_BATCH = "galacticraft:oxygen_sealer_redstone";
    /**
     * Isolate shutdown so another test cannot remove this room's air and make it pass accidentally.
     */
    private static final String SWITCH_OFF_BATCH = "galacticraft:oxygen_sealer_switch_off";
    /** Opens a room, so this gets a batch to itself for the reason {@link #INVALIDATION_BATCH} gives. */
    private static final String UNSEAL_AROUND_BUBBLE_BATCH = "galacticraft:oxygen_sealer_unseal_around_bubble";
    /**
     * Isolate cosmetic changes so a regression cannot unseal neighboring tests' rooms.
     */
    private static final String COSMETIC_CHANGE_BATCH = "galacticraft:oxygen_sealer_cosmetic_change";
    /** Opens a room, so this gets a batch to itself for the reason {@link #INVALIDATION_BATCH} gives. */
    private static final String RESHAPE_BATCH = "galacticraft:oxygen_sealer_reshape";
    /**
     * Isolate torch tests because cross-room unsealing permanently extinguishes their fixtures.
     */
    private static final String LIGHT_A_TORCH_BATCH = "galacticraft:oxygen_sealer_light_a_torch";
    private static final String LAY_A_BLOCK_BATCH = "galacticraft:oxygen_sealer_lay_a_block";
    private static final String PUT_OUT_BATCH = "galacticraft:oxygen_sealer_put_out";
    private static boolean levelWasBreathable = true;

    /** A hollow stone shell spanning this cube, so the interior is the positions from 1 to 3. */
    private static final int ROOM_SIZE = 5;
    private static final BlockPos FIRST_SEALER = new BlockPos(1, 1, 1);
    private static final BlockPos SECOND_SEALER = new BlockPos(3, 1, 3);
    /** An interior position neither sealer occupies. */
    private static final BlockPos INTERIOR = new BlockPos(2, 2, 2);
    /** Interior, against {@link #FIRST_SEALER}, so a block here powers that sealer. */
    private static final BlockPos SIGNAL_SOURCE = new BlockPos(2, 1, 1);
    /**
     * Ceiling opening; side walls and the floor border the plot's solid ground and barriers.
     */
    private static final BlockPos CEILING = new BlockPos(2, ROOM_SIZE - 1, 2);
    /**
     * The middle of a bubble of radius 1, from a distributor standing where {@link #SECOND_SEALER} would.
     * {@link #INTERIOR} is outside it.
     */
    private static final BlockPos BUBBLE_CENTRE = SECOND_SEALER.above();
    /** A wall inside that bubble, which the room never holds. */
    private static final BlockPos WALL_IN_BUBBLE = BUBBLE_CENTRE.east();
    /** Interior, in the corner {@link #SECOND_SEALER} takes when a test has a second sealer. */
    private static final BlockPos STORAGE_MODULE = SECOND_SEALER;
    /** A floor corner no other fixture uses, for a torch standing in the room. */
    private static final BlockPos TORCH = new BlockPos(3, 1, 1);
    /** Long enough for the machines to tick their resource flags and the sealer manager to run. */
    private static final int SETTLE_TICKS = 6;
    /** How long {@link #await} keeps looking before giving up, comfortably inside the test timeout. */
    private static final int DEADLINE_TICKS = 100;

    @GameTest(template = EMPTY_STRUCTURE, batch = "galacticraft:oxygen_sync")
    public void oxygenUpdatesIncludeOnlyChangedSectionsAboveAndAcrossTheOldMaskLimit(GameTestHelper context) {
        var chunk = context.getLevel().getChunkAt(context.absolutePos(BlockPos.ZERO));
        var oxygen = (ChunkOxygenAccessor) chunk;
        var syncer = (ChunkOxygenSyncer) chunk;
        syncer.galacticraft$syncOxygenPacketsToClient();
        for (int section : new int[]{16, 23, 15}) {
            int y = chunk.getMinBuildHeight() + (section << 4);
            boolean previous = oxygen.galacticraft$isInverted(0, y, 0);
            try {
                oxygen.galacticraft$setInverted(0, y, 0, !previous);
                var updates = syncer.galacticraft$syncOxygenPacketsToClient();
                context.assertTrue(updates != null && updates.length == 1,
                        "Expected exactly one oxygen update for section " + section);
                context.assertTrue(updates[0].section() == section, "Oxygen update has the wrong section index");
                context.assertTrue(updates[0].data().get(0) == !previous, "Oxygen update lost its changed bit");
                context.assertTrue(syncer.galacticraft$syncOxygenPacketsToClient() == null,
                        "Oxygen updates must clear dirty sections after sending");
            } finally {
                oxygen.galacticraft$setInverted(0, y, 0, previous);
                syncer.galacticraft$syncOxygenPacketsToClient();
            }
        }
        context.succeed();
    }

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

    @BeforeBatch(batch = UNSEAL_AROUND_BUBBLE_BATCH)
    public void emptyTheAirToUnsealAroundABubble(ServerLevel level) {
        vacuum(level);
    }

    @AfterBatch(batch = UNSEAL_AROUND_BUBBLE_BATCH)
    public void putTheAirBackAfterUnsealingAroundABubble(ServerLevel level) {
        restoreAir(level);
    }

    @BeforeBatch(batch = COSMETIC_CHANGE_BATCH)
    public void emptyTheAirToChangeHowAMachineLooks(ServerLevel level) {
        vacuum(level);
    }

    @AfterBatch(batch = COSMETIC_CHANGE_BATCH)
    public void putTheAirBackAfterChangingHowAMachineLooks(ServerLevel level) {
        restoreAir(level);
    }

    @BeforeBatch(batch = RESHAPE_BATCH)
    public void emptyTheAirToReshapeTheCeiling(ServerLevel level) {
        vacuum(level);
    }

    @AfterBatch(batch = RESHAPE_BATCH)
    public void putTheAirBackAfterReshapingTheCeiling(ServerLevel level) {
        restoreAir(level);
    }

    @BeforeBatch(batch = LIGHT_A_TORCH_BATCH)
    public void emptyTheAirToLightATorch(ServerLevel level) {
        vacuum(level);
    }

    @AfterBatch(batch = LIGHT_A_TORCH_BATCH)
    public void putTheAirBackAfterLightingATorch(ServerLevel level) {
        restoreAir(level);
    }

    @BeforeBatch(batch = LAY_A_BLOCK_BATCH)
    public void emptyTheAirToLayABlock(ServerLevel level) {
        vacuum(level);
    }

    @AfterBatch(batch = LAY_A_BLOCK_BATCH)
    public void putTheAirBackAfterLayingABlock(ServerLevel level) {
        restoreAir(level);
    }

    @BeforeBatch(batch = PUT_OUT_BATCH)
    public void emptyTheAirToPutATorchOut(ServerLevel level) {
        vacuum(level);
    }

    @AfterBatch(batch = PUT_OUT_BATCH)
    public void putTheAirBackAfterPuttingATorchOut(ServerLevel level) {
        restoreAir(level);
    }

    private static void vacuum(ServerLevel level) {
        levelWasBreathable = level.getDefaultBreathable();
        level.setDefaultBreathable(false);
    }

    private static void restoreAir(ServerLevel level) {
        level.setDefaultBreathable(levelWasBreathable);
    }

    /**
     * Opening a room has to invalidate its seal. A room keeps its air until the recompute that follows the
     * change, so this checks once that has run. The hole goes in the ceiling: the floor and the side walls
     * back onto the plot's ground and barrier, which the sealer rightly reads as more wall.
     */
    @GameTest(template = EMPTY_STRUCTURE, batch = INVALIDATION_BATCH, timeoutTicks = 200)
    public void blockChangeInvalidatesSealedState(GameTestHelper context) {
        buildRoom(context);
        OxygenSealerBlockEntity sealer = placeFuelledSealer(context, FIRST_SEALER);

        await(context, sealer::isSealed, "the sealer never sealed the room to begin with", () -> {
            context.setBlock(CEILING, Blocks.AIR);

            runAt(context, SETTLE_TICKS, () -> check(context, () -> {
                if (sealer.isSealed()) return "opening the room did not invalidate the oxygen sealer";
                if (breathable(context, INTERIOR)) return "the opened room stayed breathable";
                return null;
            }));
        });
    }

    /**
     * Checks that cosmetic machine state changes do not queue seal recomputes (issue #20). Query
     * the pending state before the next tick, since recomputing an unchanged room leaves no trace.
     */
    @GameTest(template = EMPTY_STRUCTURE, batch = COSMETIC_CHANGE_BATCH, timeoutTicks = 200)
    public void aMachineChangingOnlyHowItLooksLeavesTheSealAlone(GameTestHelper context) {
        buildRoom(context);
        OxygenSealerBlockEntity sealer = placeFuelledSealer(context, FIRST_SEALER);
        context.setBlock(STORAGE_MODULE, GCBlocks.OXYGEN_STORAGE_MODULE);
        SealerManager sealers = context.getLevel().galacticraft$getSealerManager();

        await(context, () -> sealer.isSealed() && breathable(context, INTERIOR) && !sealers.isRecomputeQueued(),
                "the sealer never settled on holding the room", () -> check(context, () -> {
            restyle(context, FIRST_SEALER, state -> state.cycle(MachineBlock.ACTIVE));
            if (sealers.isRecomputeQueued()) return "a sealer switching its light queued a flood fill of every room in the level";
            restyle(context, STORAGE_MODULE, state -> state.setValue(ResourceStorageBlock.AMOUNT, 6));
            if (sealers.isRecomputeQueued()) return "a storage module's gauge moving queued a flood fill of every room in the level";
            if (!sealer.isSealed() || !breathable(context, INTERIOR)) return "a machine changing only how it looks unsealed the room around it";
            // The control: a block that does change the room has to queue one, or the checks above prove nothing.
            context.setBlock(INTERIOR, Blocks.STONE);
            if (!sealers.isRecomputeQueued()) return "laying a block in the room queued no recompute either, so this proves nothing";
            return null;
        }));
    }

    /**
     * Control for {@link #aMachineChangingOnlyHowItLooksLeavesTheSealAlone}: opening a trapdoor
     * changes sealing faces even though the block type stays the same.
     */
    @GameTest(template = EMPTY_STRUCTURE, batch = RESHAPE_BATCH, timeoutTicks = 200)
    public void aTrapdoorOpeningInTheCeilingUnsealsTheRoom(GameTestHelper context) {
        buildRoom(context);
        // Lying in the bottom of its block, so its solid face is the one the room looks up at.
        context.setBlock(CEILING, Blocks.IRON_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.HALF, Half.BOTTOM));
        OxygenSealerBlockEntity sealer = placeFuelledSealer(context, FIRST_SEALER);

        await(context, () -> sealer.isSealed() && breathable(context, INTERIOR),
                "a closed trapdoor in the ceiling never held the room, so this proves nothing", () -> {
            restyle(context, CEILING, state -> state.setValue(TrapDoorBlock.OPEN, true));

            runAt(context, SETTLE_TICKS, () -> check(context, () -> {
                if (sealer.isSealed()) return "the room stayed sealed after the trapdoor in its ceiling opened";
                if (breathable(context, INTERIOR)) return "the room stayed breathable after the trapdoor in its ceiling opened";
                return null;
            }));
        });
    }

    /**
     * Checks that placing a torch in a sealed room does not briefly unseal it and extinguish the torch.
     */
    @GameTest(template = EMPTY_STRUCTURE, batch = LIGHT_A_TORCH_BATCH, timeoutTicks = 200)
    public void aTorchLitInsideASealedRoomStaysLit(GameTestHelper context) {
        buildRoom(context);
        OxygenSealerBlockEntity sealer = placeFuelledSealer(context, FIRST_SEALER);

        await(context, () -> sealer.isSealed() && breathable(context, TORCH),
                "the sealer never sealed the room to begin with", () -> {
            context.setBlock(TORCH, Blocks.TORCH);

            runAt(context, SETTLE_TICKS, () -> check(context, () -> {
                if (!context.getBlockState(TORCH).is(Blocks.TORCH)) return "a torch lit inside a sealed room went out";
                if (!sealer.isSealed()) return "the room did not stay sealed around the torch, so this proves nothing";
                return null;
            }));
        });
    }

    /**
     * Checks that building inside a sealed room leaves existing torches lit.
     */
    @GameTest(template = EMPTY_STRUCTURE, batch = LAY_A_BLOCK_BATCH, timeoutTicks = 200)
    public void aBlockLaidInsideASealedRoomLeavesItsTorchLit(GameTestHelper context) {
        buildRoom(context);
        placeLitTorch(context, TORCH);
        OxygenSealerBlockEntity sealer = placeFuelledSealer(context, FIRST_SEALER);

        await(context, () -> sealer.isSealed() && breathable(context, TORCH),
                "the sealer never sealed the room around the torch", () -> {
            context.setBlock(INTERIOR, Blocks.STONE);

            runAt(context, SETTLE_TICKS, () -> check(context, () -> {
                if (!context.getBlockState(TORCH).is(Blocks.TORCH)) return "laying a block inside a sealed room put out the torch already burning there";
                if (!sealer.isSealed()) return "the room did not reseal around the new block, so this proves nothing";
                return null;
            }));
        });
    }

    /**
     * Control: opening the room to vacuum must extinguish its torch.
     */
    @GameTest(template = EMPTY_STRUCTURE, batch = PUT_OUT_BATCH, timeoutTicks = 200)
    public void openingASealedRoomPutsItsTorchOut(GameTestHelper context) {
        buildRoom(context);
        placeLitTorch(context, TORCH);
        OxygenSealerBlockEntity sealer = placeFuelledSealer(context, FIRST_SEALER);

        await(context, () -> sealer.isSealed() && breathable(context, TORCH),
                "the sealer never sealed the room around the torch", () -> {
            context.setBlock(CEILING, Blocks.AIR);

            runAt(context, SETTLE_TICKS, () -> check(context, () -> {
                if (sealer.isSealed()) return "the room stayed sealed with a hole in its ceiling, so this proves nothing";
                if (!context.getBlockState(TORCH).is(GCBlocks.UNLIT_TORCH)) return "a torch went on burning after its room was opened to vacuum";
                return null;
            }));
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

    /**
     * A bubble switched off inside a sealed room. Bubbles and rooms write the same per-block air, and the
     * bubble only clears what it is giving up - but the room is still holding most of that.
     */
    @GameTest(template = EMPTY_STRUCTURE, batch = BATCH, timeoutTicks = 200)
    public void aBubbleSwitchedOffInsideASealedRoomLeavesTheRoomItsAir(GameTestHelper context) {
        buildRoom(context);
        OxygenSealerBlockEntity sealer = placeFuelledSealer(context, FIRST_SEALER);
        OxygenBubbleDistributorBlockEntity distributor = placeDistributor(context, SECOND_SEALER);

        await(context, () -> sealer.isSealed() && breathable(context, BUBBLE_CENTRE),
                "the sealer never sealed the room around the distributor", () -> {
            distributor.distributeOxygenToArea(1, true);
            distributor.distributeOxygenToArea(1, false);
            check(context, () -> {
                if (!breathable(context, BUBBLE_CENTRE)) return "switching a bubble off inside a sealed room took the room's air";
                if (breathable(context, WALL_IN_BUBBLE)) return "the bubble kept the air only it covered, so this proves nothing";
                return null;
            });
        });
    }

    /**
     * The other way round: a room losing its seal clears every block it held, and the ones a bubble
     * inside it also covers have to keep their air.
     */
    @GameTest(template = EMPTY_STRUCTURE, batch = UNSEAL_AROUND_BUBBLE_BATCH, timeoutTicks = 200)
    public void aRoomLosingItsSealLeavesTheBubbleInsideItBreathable(GameTestHelper context) {
        buildRoom(context);
        OxygenSealerBlockEntity sealer = placeFuelledSealer(context, FIRST_SEALER);
        OxygenBubbleDistributorBlockEntity distributor = placeDistributor(context, SECOND_SEALER);

        await(context, () -> sealer.isSealed() && breathable(context, INTERIOR),
                "the sealer never sealed the room around the distributor", () -> {
            distributor.distributeOxygenToArea(1, true);
            context.setBlock(CEILING, Blocks.AIR);

            runAt(context, SETTLE_TICKS, () -> {
                try {
                    check(context, () -> {
                        if (sealer.isSealed()) return "the room stayed sealed with a hole in its ceiling, so this proves nothing";
                        if (breathable(context, INTERIOR)) return "the opened room kept its own air, so this proves nothing";
                        if (!breathable(context, BUBBLE_CENTRE)) return "a room losing its seal took the air of the bubble inside it";
                        return null;
                    });
                } finally {
                    distributor.distributeOxygenToArea(1, false);
                }
            });
        });
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

    /** Placed without power, so it never grows a bubble of its own and the test applies one by hand. */
    private static OxygenBubbleDistributorBlockEntity placeDistributor(GameTestHelper context, BlockPos pos) {
        context.setBlock(pos, GCBlocks.OXYGEN_BUBBLE_DISTRIBUTOR);
        return context.getBlockEntity(pos);
    }

    private static boolean breathable(GameTestHelper context, BlockPos pos) {
        return context.getLevel().isBreathable(context.absolutePos(pos));
    }

    /** Swaps a block for another state of itself in place, the way a machine does, without neighbour updates. */
    private static void restyle(GameTestHelper context, BlockPos pos, UnaryOperator<BlockState> change) {
        BlockPos absolute = context.absolutePos(pos);
        BlockState before = context.getLevel().getBlockState(absolute);
        BlockState after = change.apply(before);
        context.assertFalse(after == before, "restyling " + before + " changed nothing, so this proves nothing");
        context.getLevel().setBlock(absolute, after, Block.UPDATE_CLIENTS);
    }

    /**
     * Stands a burning torch in the room before it is sealed. The level is held in vacuum, where a torch
     * goes straight out, so its air is switched back on for the moment the torch is placed.
     */
    private static void placeLitTorch(GameTestHelper context, BlockPos pos) {
        ServerLevel level = context.getLevel();
        level.setDefaultBreathable(true);
        try {
            context.setBlock(pos, Blocks.TORCH);
        } finally {
            level.setDefaultBreathable(false);
        }
        context.assertTrue(context.getBlockState(pos).is(Blocks.TORCH), "the torch went out as it was placed, so this proves nothing");
    }

    /**
     * Polls once per tick until {@code ready}, then runs {@code then}. Wait for a positive readiness
     * condition: a negative check can pass before asynchronous sealing has begun.
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
