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

package dev.galacticraft.mod.machine;

import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.content.block.entity.machine.OxygenBubbleDistributorBlockEntity;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

/**
 * Tracks bubble coverage so air is cleared only when no bubble or sealed room still owns it.
 * Records persist with the level because bubble air can remain loaded while its distributor is not.
 */
public final class OxygenBubbleManager {
    private final Level level;
    private @Nullable Bubbles bubbles;

    public OxygenBubbleManager(Level level) {
        this.level = level;
    }

    /** Records how far the bubble from the distributor at {@code distributor} reaches, squared; -1 for none. */
    public void update(BlockPos distributor, int radiusSquared) {
        Bubbles bubbles = this.bubbles();
        long key = distributor.asLong();
        int previous = radiusSquared < 0 ? bubbles.radiiSquared.remove(key) : bubbles.radiiSquared.put(key, radiusSquared);
        if (previous != radiusSquared) bubbles.setDirty();
    }

    /** Whether any bubble other than {@code except}'s holds air at this position. */
    public boolean covers(BlockPos pos, @Nullable OxygenBubbleDistributorBlockEntity except) {
        Bubbles bubbles = this.bubbles();
        // This runs for every solid block placed in the level, almost all of them nowhere near a bubble.
        if (bubbles.radiiSquared.isEmpty()) return false;

        boolean skipping = except != null;
        long skipped = skipping ? except.getBlockPos().asLong() : 0;
        ObjectIterator<Long2IntMap.Entry> entries = bubbles.radiiSquared.long2IntEntrySet().fastIterator();
        while (entries.hasNext()) {
            Long2IntMap.Entry entry = entries.next();
            long distributor = entry.getLongKey();
            if (skipping && distributor == skipped) continue;

            long x = pos.getX() - BlockPos.getX(distributor);
            long y = pos.getY() - (BlockPos.getY(distributor) + 1);
            long z = pos.getZ() - BlockPos.getZ(distributor);
            if (x * x + y * y + z * z > entry.getIntValue()) continue;

            if (this.isLeftOver(distributor)) {
                entries.remove();
                bubbles.setDirty();
                continue;
            }
            return true;
        }
        return false;
    }

    /**
     * A loaded distributor keeps its own record right, so a record whose chunk is loaded with no distributor
     * in it is left over - from a crash between saves, or a world edited outside the game.
     */
    private boolean isLeftOver(long distributor) {
        BlockPos pos = BlockPos.of(distributor);
        LevelChunk chunk = this.level.getChunkSource().getChunkNow(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
        return chunk != null && !(chunk.getBlockEntity(pos, LevelChunk.EntityCreationType.CHECK) instanceof OxygenBubbleDistributorBlockEntity);
    }

    /** Resolved on first use: a level's saved data is not ready while the level is being built. */
    private Bubbles bubbles() {
        if (this.bubbles == null) {
            this.bubbles = this.level instanceof ServerLevel serverLevel ? Bubbles.get(serverLevel) : new Bubbles();
        }
        return this.bubbles;
    }

    /** Every bubble's distributor position and squared radius, kept in the level's own data folder. */
    public static final class Bubbles extends SavedData {
        private static final String ID = Constant.MOD_ID + "_oxygen_bubbles";
        private static final String DISTRIBUTORS = "distributors";
        private static final String RADII_SQUARED = "radii_squared";

        private final Long2IntOpenHashMap radiiSquared = new Long2IntOpenHashMap();

        private Bubbles() {
            this.radiiSquared.defaultReturnValue(-1);
        }

        /** Returns the distributor's applied bubble radius squared, or -1 when absent. */
        public int radiusSquared(BlockPos distributor) {
            return this.radiiSquared.get(distributor.asLong());
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            tag.putLongArray(DISTRIBUTORS, this.radiiSquared.keySet().toLongArray());
            tag.putIntArray(RADII_SQUARED, this.radiiSquared.values().toIntArray());
            return tag;
        }

        public static Bubbles load(CompoundTag tag, HolderLookup.Provider registries) {
            Bubbles bubbles = new Bubbles();
            long[] distributors = tag.getLongArray(DISTRIBUTORS);
            int[] radiiSquared = tag.getIntArray(RADII_SQUARED);
            for (int i = 0; i < Math.min(distributors.length, radiiSquared.length); i++) {
                bubbles.radiiSquared.put(distributors[i], radiiSquared[i]);
            }
            return bubbles;
        }

        public static Bubbles get(ServerLevel level) {
            return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(Bubbles::new, Bubbles::load, null), ID);
        }
    }
}
