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

package dev.galacticraft.mod.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.GenerationChunkHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Makes chunk saving wait for outstanding generation claims. Otherwise an unload task can
 * requeue against an already-completed save future and block shutdown from finishing generation.
 * Claims begin on the server thread but can end on a worldgen thread. Read the release future
 * before decrementing the count so a new claim cannot replace it before completion.
 */
@Mixin(GenerationChunkHolder.class)
public abstract class GenerationChunkHolderMixin {
    @Unique
    private volatile CompletableFuture<Void> galacticraft$generationSaveSync;

    @WrapOperation(method = "increaseGenerationRefCount", at = @At(value = "INVOKE", target = "Ljava/util/concurrent/atomic/AtomicInteger;incrementAndGet()I", remap = false))
    private int galacticraft$holdSaveWhileClaimed(AtomicInteger claims, Operation<Integer> original) {
        int count = original.call(claims);
        if (count == 1 && (Object) this instanceof ChunkHolderInvoker holder) {
            CompletableFuture<Void> released = new CompletableFuture<>();
            this.galacticraft$generationSaveSync = released;
            holder.callAddSaveDependency(released);
        }
        return count;
    }

    @WrapOperation(method = "decreaseGenerationRefCount", at = @At(value = "INVOKE", target = "Ljava/util/concurrent/atomic/AtomicInteger;decrementAndGet()I", remap = false))
    private int galacticraft$releaseSaveWhenUnclaimed(AtomicInteger claims, Operation<Integer> original) {
        CompletableFuture<Void> released = this.galacticraft$generationSaveSync;
        int count = original.call(claims);
        if (count == 0 && released != null) {
            released.complete(null);
        }
        return count;
    }
}
