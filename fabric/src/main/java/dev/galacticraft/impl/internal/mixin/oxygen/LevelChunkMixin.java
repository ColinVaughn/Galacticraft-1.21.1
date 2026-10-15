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

package dev.galacticraft.impl.internal.mixin.oxygen;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.galacticraft.impl.internal.accessor.ChunkOxygenAccessor;
import dev.galacticraft.impl.internal.accessor.ChunkOxygenSyncer;
import dev.galacticraft.impl.internal.accessor.ChunkSectionOxygenAccessor;
import dev.galacticraft.impl.network.s2c.OxygenUpdatePayload;
import dev.galacticraft.mod.accessor.GCLevelAccessor;
import dev.galacticraft.mod.events.GCEventHandlers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.blending.BlendingData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.BitSet;

@Mixin(LevelChunk.class)
public abstract class LevelChunkMixin extends ChunkAccess implements ChunkOxygenAccessor, ChunkOxygenSyncer {
    @Shadow
    @Final
    Level level;
    private @Unique final BitSet dirtySections = new BitSet();

    private LevelChunkMixin(ChunkPos pos, UpgradeData upgradeData, LevelHeightAccessor heightLimitView, Registry<Biome> biome, long inhabitedTime, @Nullable LevelChunkSection[] sectionArrayInitializer, @Nullable BlendingData blendingData) {
        super(pos, upgradeData, heightLimitView, biome, inhabitedTime, sectionArrayInitializer, blendingData);
    }

    @Override
    public boolean galacticraft$isInverted(int x, int y, int z) {
        return ((ChunkSectionOxygenAccessor) this.sections[this.getSectionIndex(y)]).galacticraft$isInverted(x, y & 15, z);
    }

    @Override
    public void galacticraft$setInverted(int x, int y, int z, boolean inverted) {
        var accessor = ((ChunkSectionOxygenAccessor) this.sections[this.getSectionIndex(y)]);
        if (inverted != accessor.galacticraft$isInverted(x, y & 15, z)) {
            if (!this.level.isClientSide) {
                this.unsaved = true;
                this.dirtySections.set(this.getSectionIndex(y));
            }
            accessor.galacticraft$setInverted(x, y & 15, z, inverted);
        }
    }

    @Override
    public @Nullable OxygenUpdatePayload.OxygenData[] galacticraft$syncOxygenPacketsToClient() {
        assert !this.level.isClientSide;
        if (!this.dirtySections.isEmpty()) {
            OxygenUpdatePayload.OxygenData[] data = new OxygenUpdatePayload.OxygenData[this.dirtySections.cardinality()];
            int idx = 0;
            for (int i = this.dirtySections.nextSetBit(0); i >= 0; i = this.dirtySections.nextSetBit(i + 1)) {
                BitSet data1 = ((ChunkSectionOxygenAccessor) this.sections[i]).galacticraft$getBits();
                data[idx++] = new OxygenUpdatePayload.OxygenData(i, data1 == null ? new BitSet(0) : data1);
            }
            this.dirtySections.clear();
            return data;
        }
        return null;
    }

    @Override
    public void galacticraft$readOxygenUpdate(@NotNull OxygenUpdatePayload.OxygenData[] buf) {
        for (OxygenUpdatePayload.@NotNull OxygenData oxygenData : buf) {
            ((ChunkSectionOxygenAccessor) this.sections[oxygenData.section()]).galacticraft$setBits(oxygenData.data());
        }
    }

    @Inject(method = "setBlockState", at = @At("HEAD"))
    private void updateSealerOnBlockChange(BlockPos pos, BlockState blockState, boolean bl, CallbackInfoReturnable<BlockState> cir) {
        ((GCLevelAccessor) this.level).galacticraft$getSealerManager().onBlockChanged(pos, this.getBlockState(pos), blockState);
    }

    @Inject(method = "setBlockState", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;getBlock()Lnet/minecraft/world/level/block/Block;", ordinal = 0))
    private void resetAirOnBlockChange(BlockPos pos, BlockState blockState, boolean bl, CallbackInfoReturnable<BlockState> cir) {
        if (blockState.isSolidRender(this.level, pos)) {
            this.galacticraft$setInverted(pos.getX() & 15, pos.getY(), pos.getZ() & 15, false);
        }
    }

    @WrapOperation(method = "setBlockState", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;onPlace(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Z)V", ordinal = 0))
    private void extinguishFire(BlockState newState, Level level, BlockPos pos, BlockState oldState, boolean bl, Operation<Void> original) {
        if (level.isBreathable(pos) || !GCEventHandlers.extinguishBlock(level, pos, newState)) {
            original.call(newState, level, pos, oldState, bl);
        }
    }
}
