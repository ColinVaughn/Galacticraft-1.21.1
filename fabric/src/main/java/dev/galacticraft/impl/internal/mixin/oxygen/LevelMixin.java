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

import dev.galacticraft.api.accessor.LevelBodyAccessor;
import dev.galacticraft.api.accessor.LevelOxygenAccessor;
import dev.galacticraft.api.universe.celestialbody.CelestialBody;
import dev.galacticraft.impl.internal.accessor.ChunkOxygenAccessor;
import dev.galacticraft.impl.internal.accessor.InternalLevelBodyAccessor;
import dev.galacticraft.impl.internal.accessor.InternalLevelOxygenAccessor;
import dev.galacticraft.mod.events.GCEventHandlers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.WritableLevelData;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Supplier;

@Mixin(Level.class)
public abstract class LevelMixin implements LevelOxygenAccessor, InternalLevelOxygenAccessor, LevelAccessor {
    private @Unique boolean breathable = true;

    @Shadow
    public abstract @NotNull LevelChunk getChunk(int i, int j);

    @Shadow
    @Final
    private ResourceKey<Level> dimension;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void initializeOxygenValues(WritableLevelData writableLevelData, ResourceKey<Level> resourceKey, RegistryAccess registryAccess, Holder holder, Supplier supplier, boolean bl, boolean bl2, long l, int i, CallbackInfo ci) {
        // Deliberately the unchecked resolve: the level is not registered with the server or client
        // until after construction, so the virtual level check has no answer yet and would leave a
        // genuine airless dimension marked breathable. Wrapper levels are handled on read instead,
        // in galacticraft$defaultBreathable().
        Holder<CelestialBody<?, ?>> holder1 = ((InternalLevelBodyAccessor) this).galacticraft$getResolvedCelestialBody();
        this.setDefaultBreathable(holder1 != null ? holder1.value().atmosphere().breathable() : this.breathable);
    }

    /**
     * The stored value is captured at construction from the level's celestial body. A virtual level
     * inherits the dimension key — and so the atmosphere — of the level it wraps, which should not
     * apply to it; report breathable air there instead. Ordered so that the check costs nothing on
     * levels that already have air.
     */
    @Unique
    private boolean galacticraft$defaultBreathable() {
        return this.breathable || ((LevelBodyAccessor) this).galacticraft$isVirtualLevel();
    }

    @Override
    public boolean isBreathable(int x, int y, int z) {
        if (this.validPosition(x, y, z)) {
            return this.isBreathableChunk(this.getChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z)), x & 15, y, z & 15);
        }
        return this.galacticraft$defaultBreathable()/* && y < this.getMaxBuildHeight() * 2*/;
    }

    @Override
    public boolean isBreathableChunk(LevelChunk chunk, int x, int y, int z) {
        assert x >= 0 && x < 16 && z >= 0 && z < 16;
        if (this.withinBuildHeight(y)) {
            return this.galacticraft$defaultBreathable() ^ ((ChunkOxygenAccessor) chunk).galacticraft$isInverted(x, y, z);
        }
        return this.galacticraft$defaultBreathable()/* && y < this.getMaxBuildHeight() * 2*/;
    }

    @Override
    public void setBreathable(int x, int y, int z, boolean value) {
        if (withinWorldSize(x, z) && y >= this.getMinBuildHeight() && y < this.getMaxBuildHeight()) {
            this.setBreathableChunk(this.getChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z)), x & 15, y, z & 15, value);
        }
    }

    @Override
    public void setBreathableChunk(LevelChunk chunk, int x, int y, int z, boolean value) {
        assert x >= 0 && x < 16 && z >= 0 && z < 16;
        if (y < this.getMinBuildHeight() || y >= this.getMaxBuildHeight()) return;
        boolean wasBreathable = this.isBreathableChunk(chunk, x, y, z);
        ((ChunkOxygenAccessor) chunk).galacticraft$setInverted(x, y, z, this.galacticraft$defaultBreathable() ^ value);
        if (wasBreathable && !value) {
            BlockPos blockPos = chunk.getPos().getBlockAt(x, y, z);
            GCEventHandlers.extinguishBlock((Level) (Object) this, blockPos, this.getBlockState(blockPos));
        }
    }

    @Override
    public boolean getDefaultBreathable() {
        return this.galacticraft$defaultBreathable();
    }

    @Override
    public void setDefaultBreathable(boolean breathable) {
        this.breathable = breathable;
    }

    @Unique
    private boolean withinBuildHeight(int y) {
        return y >= this.getMinBuildHeight() && y < this.getMaxBuildHeight();
    }

    @Unique
    private static boolean withinWorldSize(int x, int z) {
        return x >= -Level.MAX_LEVEL_SIZE && z >= -Level.MAX_LEVEL_SIZE && x < Level.MAX_LEVEL_SIZE && z < Level.MAX_LEVEL_SIZE;
    }

    @Unique
    private boolean validPosition(int x, int y, int z) {
        return this.withinBuildHeight(y) && withinWorldSize(x, z);
    }
}
