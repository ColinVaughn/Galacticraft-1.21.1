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

package dev.galacticraft.mod.content.block.entity;

import dev.galacticraft.mod.content.GCBlockEntityTypes;
import dev.galacticraft.mod.content.block.special.OxygenDetectorBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class OxygenDetectorBlockEntity extends BlockEntity {
    private int ticks = 0;

    public OxygenDetectorBlockEntity(BlockPos pos, BlockState state) {
        super(GCBlockEntityTypes.OXYGEN_DETECTOR, pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, OxygenDetectorBlockEntity blockEntity) {
        // Poll the oxygen state once per second to keep the redstone output cheap and stable.
        if (blockEntity.ticks++ % 20 != 0) {
            return;
        }

        boolean breathable = level.isBreathable(pos);
        if (state.getValue(OxygenDetectorBlock.POWERED) != breathable) {
            level.setBlock(pos, state.setValue(OxygenDetectorBlock.POWERED, breathable), Block.UPDATE_ALL);
        }
    }
}
