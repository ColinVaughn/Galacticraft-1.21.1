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

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.level.ChunkPos;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Queue;

/**
 * Checks that unload waits for generation claims instead of immediately requeuing against a
 * completed save future, which can deadlock shutdown. Uses a manual claim on an otherwise idle chunk.
 */
public final class ChunkUnloadTestSuite implements GalacticraftGameTest {
    /** Far more runs than the queue could hold for one chunk, unless a task keeps putting itself back. */
    private static final int RUN_LIMIT = 10_000;
    /**
     * Allows neighboring world generation to release incidental claims before the test begins.
     */
    private static final int SETTLE_TICKS = 200;

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = SETTLE_TICKS + 20)
    @SuppressWarnings("unchecked")
    public void anUnloadWaitsForItsChunkToBeReleased(GameTestHelper context) {
        ChunkMap chunkMap = context.getLevel().getChunkSource().chunkMap;
        long chunkPos = new ChunkPos(context.absolutePos(BlockPos.ZERO)).toLong();
        Method visibleChunk;
        Method scheduleUnload;
        Queue<Runnable> unloadQueue;
        try {
            visibleChunk = declaredMethod("getVisibleChunkIfPresent", long.class);
            scheduleUnload = declaredMethod("scheduleUnload", long.class, ChunkHolder.class);
            Field queue = ChunkMap.class.getDeclaredField("unloadQueue");
            queue.setAccessible(true);
            unloadQueue = (Queue<Runnable>) queue.get(chunkMap);
        } catch (ReflectiveOperationException e) {
            context.fail("could not reach the chunk map's unload internals: " + e);
            return;
        }
        this.whenSettled(context, chunkMap, chunkPos, visibleChunk, holder -> this.claimAndUnload(context, chunkMap, chunkPos, holder, scheduleUnload, unloadQueue));
    }

    /** Runs {@code check} once the test's own chunk is loaded and nothing else is holding it. */
    private void whenSettled(GameTestHelper context, ChunkMap chunkMap, long chunkPos, Method visibleChunk, java.util.function.Consumer<ChunkHolder> check) {
        ChunkHolder holder;
        try {
            holder = (ChunkHolder) visibleChunk.invoke(chunkMap, chunkPos);
        } catch (ReflectiveOperationException e) {
            context.fail("could not look up the test's own chunk: " + e);
            return;
        }
        if (holder != null && holder.isReadyForSaving()) {
            check.accept(holder);
        } else if (context.getTick() >= SETTLE_TICKS) {
            context.fail("the test's own chunk was still " + (holder == null ? "not loaded" : "held or saving")
                    + " after " + SETTLE_TICKS + " ticks");
        } else {
            context.runAfterDelay(1, () -> this.whenSettled(context, chunkMap, chunkPos, visibleChunk, check));
        }
    }

    private void claimAndUnload(GameTestHelper context, ChunkMap chunkMap, long chunkPos, ChunkHolder holder, Method scheduleUnload, Queue<Runnable> unloadQueue) {
        holder.increaseGenerationRefCount();
        try {
            scheduleUnload.invoke(chunkMap, chunkPos, holder);
            int runs = drain(unloadQueue);
            if (runs >= RUN_LIMIT) {
                context.fail("the unload of a claimed chunk ran " + runs + " times without waiting for the claim");
                return;
            }
        } catch (ReflectiveOperationException e) {
            context.fail("could not schedule the unload: " + e);
            return;
        } finally {
            holder.decreaseGenerationRefCount();
        }

        // Waiting is only half of it: a task that gave up instead would leave the chunk loaded for good.
        if (unloadQueue.isEmpty()) {
            context.fail("releasing the claim never brought the unload back");
            return;
        }
        drain(unloadQueue);
        context.succeed();
    }

    /** Runs the queue the way a stopping server does, with no time limit, and counts the tasks run. */
    private static int drain(Queue<Runnable> queue) {
        int runs = 0;
        Runnable task;
        while (runs < RUN_LIMIT && (task = queue.poll()) != null) {
            task.run();
            runs++;
        }
        return runs;
    }

    private static Method declaredMethod(String name, Class<?>... parameters) throws NoSuchMethodException {
        Method method = ChunkMap.class.getDeclaredMethod(name, parameters);
        method.setAccessible(true);
        return method;
    }
}
