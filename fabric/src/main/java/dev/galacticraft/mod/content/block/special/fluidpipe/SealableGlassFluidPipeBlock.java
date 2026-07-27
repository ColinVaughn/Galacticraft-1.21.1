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

package dev.galacticraft.mod.content.block.special.fluidpipe;

import com.mojang.serialization.MapCodec;
import dev.galacticraft.mod.api.block.FluidPipeBlock;
import dev.galacticraft.mod.api.block.entity.PipeColor;
import dev.galacticraft.mod.content.GCBlocks;
import dev.galacticraft.mod.content.block.entity.networked.GlassFluidPipeBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A glass fluid pipe cased in sealant, so it can be built into the wall of a sealed room without letting
 * the air out - the fluid counterpart of the sealable aluminum wire, and of Galacticraft Legacy's
 * Enclosed Oxygen Pipe.
 *
 * <p>The whole trick is the radius: an ordinary pipe is a thin tube, and the oxygen sealer treats any
 * block whose faces are not solid as a hole to escape through. Filling the block makes every face sturdy,
 * which is the same thing that makes the sealable wire work - neither block is in the {@code sealable}
 * tag, and neither needs to be.
 *
 * <p>Carries fluid exactly as the pipe it encloses does, colour included, so a coloured line keeps its
 * identity through a wall instead of being bridged into every other line by a colourless section.
 */
public class SealableGlassFluidPipeBlock extends FluidPipeBlock {
    /** Fills the block, rather than the 0.125 of a bare pipe. */
    private static final float FULL_BLOCK = 0.5f;

    public SealableGlassFluidPipeBlock(Properties settings, PipeColor color) {
        super(FULL_BLOCK, settings, color);
    }

    @Override
    @Nullable
    public PipeBlockEntity newBlockEntity(BlockPos blockPos, BlockState blockState) {
        return new GlassFluidPipeBlockEntity(blockPos, blockState);
    }

    @Override
    protected @NotNull MapCodec<? extends FluidPipeBlock> codec() {
        return this.simpleCodec(SealableGlassFluidPipeBlock::new);
    }

    /** Dyeing one keeps it sealable, rather than swapping in a bare pipe and opening the wall. */
    @Override
    protected Block getMatchingBlock(PipeColor color) {
        return GCBlocks.SEALABLE_GLASS_FLUID_PIPES.get(color);
    }
}
