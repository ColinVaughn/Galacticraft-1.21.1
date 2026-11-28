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

package dev.galacticraft.mod.content.block.entity.machine;

import dev.galacticraft.api.gas.Gases;
import dev.galacticraft.mod.content.GCBlocks;
import dev.galacticraft.mod.content.block.special.OxygenDetectorBlock;
import dev.galacticraft.mod.gametest.GalacticraftGameTest;
import dev.galacticraft.mod.machine.OxygenBubbleManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BooleanSupplier;

public final class OxygenBubbleDistributorGameTest implements GalacticraftGameTest {
    /**
     * Bubbles only write anything into a level without air of its own, and that is a property of the
     * whole level, so these share a batch that holds it in vacuum for as long as any of them runs.
     */
    private static final String VACUUM_BATCH = "galacticraft:oxygen_bubble";
    /** How long {@link #await} keeps looking before giving up, comfortably inside the test timeout. */
    private static final int DEADLINE_TICKS = 100;
    /** Generating a far-off chunk's neighbours far enough for it to tick takes a while on a server running flat out. */
    private static final int CHUNK_DEADLINE_TICKS = 1200;
    /** Keeps the far-off chunk the unload test runs in loaded for exactly as long as the test wants it. */
    private static final TicketType<ChunkPos> TEST_CHUNK = TicketType.create("galacticraft_test_bubble_chunk", Comparator.comparingLong(ChunkPos::toLong));
    /** Ticket distances: loaded but not ticking, and ticking its block entities. */
    private static final int LOADED = 0;
    private static final int TICKING = 2;
    private static boolean levelWasBreathable = true;

    @BeforeBatch(batch = VACUUM_BATCH)
    public void emptyTheAir(ServerLevel level) {
        levelWasBreathable = level.getDefaultBreathable();
        level.setDefaultBreathable(false);
    }

    @AfterBatch(batch = VACUUM_BATCH)
    public void putTheAirBack(ServerLevel level) {
        level.setDefaultBreathable(levelWasBreathable);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void defaultBubbleProtectsAPlayerBesideIt(GameTestHelper context) {
        BlockPos distributorPos = new BlockPos(4, 2, 4);
        boolean levelWasBreathable = context.getLevel().getDefaultBreathable();

        try {
            context.getLevel().setDefaultBreathable(false);
            context.setBlock(distributorPos, GCBlocks.OXYGEN_BUBBLE_DISTRIBUTOR);
            OxygenBubbleDistributorBlockEntity distributor = context.getBlockEntity(distributorPos);
            distributor.updateBreathableArea(1);
            BlockPos eyePos = context.absolutePos(distributorPos.offset(1, 1, 0));
            context.assertTrue(context.getLevel().isBreathable(eyePos), "the default bubble did not reach a nearby player's eye");
            context.succeed();
        } finally {
            OxygenBubbleDistributorBlockEntity distributor = context.getBlockEntity(distributorPos);
            if (distributor != null) distributor.updateBreathableArea(0);
            context.setBlock(distributorPos, Blocks.AIR);
            context.getLevel().setDefaultBreathable(levelWasBreathable);
        }
    }

    /**
     * Two bubbles sharing some air. A bubble only writes the blocks it gains or loses, so the one
     * switching off must not take the shared air with it - the other is still holding it.
     */
    @GameTest(template = EMPTY_STRUCTURE, batch = VACUUM_BATCH)
    public void aBubbleSwitchingOffLeavesTheAirItSharedWithANeighbour(GameTestHelper context) {
        OxygenBubbleDistributorBlockEntity first = placeDistributor(context, new BlockPos(2, 1, 3));
        OxygenBubbleDistributorBlockEntity second = placeDistributor(context, new BlockPos(5, 1, 3));
        try {
            first.updateBreathableArea(2);
            second.updateBreathableArea(2);
            first.updateBreathableArea(0);

            context.assertTrue(breathable(context, new BlockPos(3, 2, 3)) && breathable(context, new BlockPos(4, 2, 3)),
                    "switching one bubble off took the air it shared with the bubble beside it");
            context.assertFalse(breathable(context, new BlockPos(1, 2, 3)),
                    "the switched-off bubble kept the air only it covered, so this proves nothing");
            context.succeed();
        } finally {
            first.updateBreathableArea(0);
            second.updateBreathableArea(0);
        }
    }

    /**
     * Building inside a bubble and knocking the block back out. Placing a solid block drops the air at
     * its position, and the bubble only rewrites the blocks at its edge, so unless the bubble keeps
     * that block a player leaves a pocket of vacuum behind.
     */
    @GameTest(template = EMPTY_STRUCTURE, batch = VACUUM_BATCH)
    public void aBlockBuiltAndBrokenInsideABubbleLeavesNoVacuumBehind(GameTestHelper context) {
        BlockPos inside = new BlockPos(4, 2, 3);
        OxygenBubbleDistributorBlockEntity distributor = placeDistributor(context, new BlockPos(3, 1, 3));
        try {
            distributor.updateBreathableArea(2);
            context.assertTrue(breathable(context, inside), "the bubble never reached the block under test");

            context.setBlock(inside, Blocks.STONE);
            context.setBlock(inside, Blocks.AIR);
            context.assertTrue(breathable(context, inside), "building and breaking a block inside a bubble left a pocket of vacuum");
            context.succeed();
        } finally {
            distributor.updateBreathableArea(0);
        }
    }

    /** An oxygen detector reads the air at its own position, which placing it - a solid block - drops. */
    @GameTest(template = EMPTY_STRUCTURE, batch = VACUUM_BATCH, timeoutTicks = 200)
    public void anOxygenDetectorPlacedInsideABubbleDetectsIt(GameTestHelper context) {
        BlockPos detectorPos = new BlockPos(3, 2, 4);
        OxygenBubbleDistributorBlockEntity distributor = placeDistributor(context, new BlockPos(3, 1, 3));
        distributor.updateBreathableArea(2);
        context.setBlock(detectorPos, GCBlocks.OXYGEN_DETECTOR);

        await(context, () -> context.getBlockState(detectorPos).getValue(OxygenDetectorBlock.POWERED),
                "an oxygen detector inside a bubble never detected any oxygen", () -> {
            distributor.updateBreathableArea(0);
            context.succeed();
        });
    }

    /**
     * A distributor with no power lets its bubble collapse. Clients are told the size at most every few
     * ticks, so the tick that empties the bubble usually falls between updates - and they still have to
     * hear about it, or they go on drawing a bubble that is not there.
     */
    @GameTest(template = EMPTY_STRUCTURE, batch = VACUUM_BATCH, timeoutTicks = 200)
    public void anUnpoweredDistributorCollapsesItsBubbleCompletely(GameTestHelper context) {
        BlockPos centre = new BlockPos(3, 2, 3);
        OxygenBubbleDistributorBlockEntity distributor = placeDistributor(context, centre.below());
        // There is no energy, so every tick shrinks the bubble it has instead of growing it.
        distributor.setSize(2);

        runNext(context, () -> {
            if (!breathable(context, centre)) {
                context.fail("the collapsing bubble was never applied, so this proves nothing");
                return;
            }
            await(context, () -> distributor.getSize() == 0, "the unpowered bubble never collapsed", () ->
                    // Long enough for the size to go out to clients once the sync interval lets it.
                    runAt(context, 11, () -> {
                        List<String> failures = new ArrayList<>();
                        if (lastSyncedSize(distributor) != 0) {
                            failures.add("clients were last told the collapsed bubble was " + lastSyncedSize(distributor) + " across");
                        }
                        if (breathable(context, centre)) failures.add("the collapsed bubble left its centre breathable");
                        if (failures.isEmpty()) {
                            context.succeed();
                        } else {
                            context.fail(String.join("; ", failures));
                        }
                    }));
        });
    }

    /**
     * The bubble is put up by hand, so the distributor has not ticked since. Its current size matches
     * the applied radius, as on a running machine, and breaking it must still clear the air.
     */
    @GameTest(template = EMPTY_STRUCTURE, batch = VACUUM_BATCH)
    public void breakingADistributorTakesItsBubbleWithIt(GameTestHelper context) {
        BlockPos distributorPos = new BlockPos(3, 1, 3);
        BlockPos inside = distributorPos.above(2);
        OxygenBubbleDistributorBlockEntity distributor = placeDistributor(context, distributorPos);
        distributor.updateBreathableArea(2);
        distributor.setSize(2);
        if (!breathable(context, inside)) {
            distributor.updateBreathableArea(0);
            context.fail("the bubble never went up, so this proves nothing");
            return;
        }

        context.setBlock(distributorPos, Blocks.AIR);
        List<String> failures = new ArrayList<>();
        if (breathable(context, inside)) failures.add("breaking the distributor left its bubble behind");
        if (context.getLevel().galacticraft$getBubbleManager().covers(context.absolutePos(inside), null)) {
            failures.add("the broken distributor is still counted among the level's bubbles");
        }
        if (failures.isEmpty()) {
            context.succeed();
        } else {
            context.fail(String.join("; ", failures));
        }
    }

    /**
     * Checks that chunk unload preserves bubble air and ownership, and reload resumes its applied
     * radius. Clearing air during unload would reload affected chunks and can deadlock shutdown.
     * Runs in a distant chunk controlled only by this test.
     */
    @GameTest(template = EMPTY_STRUCTURE, batch = VACUUM_BATCH, timeoutTicks = 3 * CHUNK_DEADLINE_TICKS)
    public void aDistributorLeavesItsBubbleAloneWhileItsChunkIsUnloaded(GameTestHelper context) {
        ServerLevel level = context.getLevel();
        ServerChunkCache chunks = level.getChunkSource();
        BlockPos far = context.absolutePos(BlockPos.ZERO).offset(4096, 0, 0);
        ChunkPos chunk = new ChunkPos(far);
        chunks.addRegionTicket(TEST_CHUNK, chunk, TICKING, chunk);
        await(context, CHUNK_DEADLINE_TICKS, () -> ticking(level, chunk), "the test's chunk never started ticking",
                () -> this.putUpAndUnloadABubble(context, chunk, far));
    }

    private void putUpAndUnloadABubble(GameTestHelper context, ChunkPos chunk, BlockPos far) {
        ServerLevel level = context.getLevel();
        ServerChunkCache chunks = level.getChunkSource();
        BlockPos distributorPos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, far);
        // The bubble is centred on the block above the distributor: one block out, and right on its edge.
        BlockPos inside = distributorPos.above(2);
        BlockPos edge = distributorPos.above(3);
        Runnable cleanUp = () -> {
            chunks.addRegionTicket(TEST_CHUNK, chunk, LOADED, chunk);
            level.setBlockAndUpdate(distributorPos, Blocks.AIR.defaultBlockState());
            chunks.removeRegionTicket(TEST_CHUNK, chunk, LOADED, chunk);
            chunks.removeRegionTicket(TEST_CHUNK, chunk, TICKING, chunk);
        };

        level.setBlockAndUpdate(distributorPos, GCBlocks.OXYGEN_BUBBLE_DISTRIBUTOR.defaultBlockState());
        OxygenBubbleDistributorBlockEntity distributor = (OxygenBubbleDistributorBlockEntity) level.getBlockEntity(distributorPos);
        distributor.energyStorage().setEnergy(distributor.energyStorage().getCapacity());
        distributor.fluidStorage().slot(OxygenBubbleDistributorBlockEntity.OXYGEN_TANK)
                .set(Gases.OXYGEN, OxygenBubbleDistributorBlockEntity.MAX_OXYGEN);
        distributor.setTargetSize((byte) 2);
        distributor.setSize(2);

        await(context, () -> level.isBreathable(edge), "the distributor never put its bubble up", () -> {
            chunks.removeRegionTicket(TEST_CHUNK, chunk, TICKING, chunk);
            await(context, distributor::isRemoved, "the distributor's chunk never unloaded", () -> {
                List<String> failures = new ArrayList<>();
                if (level.hasChunk(chunk.x, chunk.z)) failures.add("unloading the distributor loaded its chunk straight back");
                if (!level.galacticraft$getBubbleManager().covers(inside, null)) {
                    failures.add("the unloaded distributor's bubble stopped counting, so anything nearby could clear its air");
                }
                if (!failures.isEmpty()) {
                    cleanUp.run();
                    context.fail(String.join("; ", failures));
                    return;
                }

                // Back without ticking, so what it reads is what the chunk was saved with.
                chunks.addRegionTicket(TEST_CHUNK, chunk, LOADED, chunk);
                level.getChunk(chunk.x, chunk.z);
                if (!level.isBreathable(edge)) failures.add("the bubble's air was not kept with its chunk");
                if (!level.galacticraft$getBubbleManager().covers(edge, null)) {
                    failures.add("the reloaded distributor did not count its bubble among the level's again");
                }
                if (!failures.isEmpty()) {
                    cleanUp.run();
                    context.fail(String.join("; ", failures));
                    return;
                }

                OxygenBubbleDistributorBlockEntity reloaded = (OxygenBubbleDistributorBlockEntity) level.getBlockEntity(distributorPos);
                reloaded.energyStorage().setEnergy(0);
                chunks.addRegionTicket(TEST_CHUNK, chunk, TICKING, chunk);
                chunks.removeRegionTicket(TEST_CHUNK, chunk, LOADED, chunk);
                await(context, CHUNK_DEADLINE_TICKS, () -> reloaded.getSize() == 0, "the reloaded distributor never collapsed its bubble", () -> {
                    List<String> collapsed = new ArrayList<>();
                    if (level.isBreathable(edge)) collapsed.add("collapsing the bubble after a reload left its old edge breathable");
                    if (level.galacticraft$getBubbleManager().covers(edge, null)) collapsed.add("the collapsed bubble is still counted");
                    cleanUp.run();
                    if (collapsed.isEmpty()) {
                        context.succeed();
                    } else {
                        context.fail(String.join("; ", collapsed));
                    }
                });
            });
        });
    }

    /**
     * After a restart a bubble's air can be loaded before its distributor is - someone standing at the far
     * edge of a big one - so the level's record of its bubbles has to come back with it.
     */
    @GameTest(template = EMPTY_STRUCTURE, batch = VACUUM_BATCH)
    public void theLevelsBubblesAreSavedWithIt(GameTestHelper context) {
        BlockPos distributorPos = new BlockPos(3, 1, 3);
        OxygenBubbleDistributorBlockEntity distributor = placeDistributor(context, distributorPos);
        try {
            distributor.updateBreathableArea(2);
            ServerLevel level = context.getLevel();
            OxygenBubbleManager.Bubbles saved = OxygenBubbleManager.Bubbles.get(level);
            OxygenBubbleManager.Bubbles loaded = OxygenBubbleManager.Bubbles.load(saved.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
            int radiusSquared = loaded.radiusSquared(context.absolutePos(distributorPos));

            if (!saved.isDirty()) {
                context.fail("putting a bubble up never marked the level's bubbles to be saved");
            } else if (radiusSquared != 4) {
                context.fail("a bubble of radius 2 came back from a save and load as " + radiusSquared + " squared");
            } else {
                context.succeed();
            }
        } finally {
            distributor.updateBreathableArea(0);
        }
    }

    /** A record with no distributor behind it - left by a crash between saves - would keep its air for good. */
    @GameTest(template = EMPTY_STRUCTURE, batch = VACUUM_BATCH)
    public void aBubbleWithNoDistributorBehindItIsForgotten(GameTestHelper context) {
        ServerLevel level = context.getLevel();
        BlockPos nowhere = context.absolutePos(new BlockPos(3, 1, 3));
        OxygenBubbleManager bubbles = level.galacticraft$getBubbleManager();
        bubbles.update(nowhere, 4);

        if (bubbles.covers(nowhere.above(), null)) {
            context.fail("a bubble with no distributor behind it still counted");
        } else if (OxygenBubbleManager.Bubbles.get(level).radiusSquared(nowhere) != -1) {
            context.fail("a bubble with no distributor behind it was not forgotten");
        } else {
            context.succeed();
        }
    }

    private static OxygenBubbleDistributorBlockEntity placeDistributor(GameTestHelper context, BlockPos pos) {
        context.setBlock(pos, GCBlocks.OXYGEN_BUBBLE_DISTRIBUTOR);
        return context.getBlockEntity(pos);
    }

    /**
     * Checks both loading dependencies required for block entity ticking.
     */
    private static boolean ticking(ServerLevel level, ChunkPos chunk) {
        return level.getChunkSource().isPositionTicking(chunk.toLong()) && level.areEntitiesLoaded(chunk.toLong());
    }

    private static boolean breathable(GameTestHelper context, BlockPos pos) {
        return context.getLevel().isBreathable(context.absolutePos(pos));
    }

    /** What the distributor last sent to clients; there is no client in a game test to ask instead. */
    private static double lastSyncedSize(OxygenBubbleDistributorBlockEntity distributor) {
        try {
            Field field = OxygenBubbleDistributorBlockEntity.class.getDeclaredField("lastSyncedSize");
            field.setAccessible(true);
            return field.getDouble(distributor);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Waits for a condition that must become true; see the note in {@code OxygenSealerTestSuite}. */
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
}
