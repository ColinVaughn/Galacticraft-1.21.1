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

package dev.galacticraft.mod.world.dimension;

import dev.galacticraft.mod.Constant;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * A dimension's own day time, across a restart.
 *
 * <p>Only the Overworld's clock is part of the save's level data; every other dimension reads that one
 * and has nowhere of its own to write. A body that keeps its own time therefore has to store it here,
 * in that dimension's own data folder.
 *
 * @see dev.galacticraft.mod.accessor.DimensionDayTimeAccessor
 */
public class DimensionDayTimeState extends SavedData {
    private static final String ID = Constant.MOD_ID + "_day_time";
    private static final String DAY_TIME = "day_time";

    private long dayTime;

    private DimensionDayTimeState(long dayTime) {
        this.dayTime = dayTime;
    }

    /** {@return the stored day time, or {@code fallback} if this dimension has never stored one} */
    public long dayTimeOr(long fallback) {
        return this.dayTime < 0L ? fallback : this.dayTime;
    }

    public void setDayTime(long dayTime) {
        if (this.dayTime != dayTime) {
            this.dayTime = dayTime;
            this.setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLong(DAY_TIME, this.dayTime);
        return tag;
    }

    public static DimensionDayTimeState load(CompoundTag tag, HolderLookup.Provider registries) {
        return new DimensionDayTimeState(tag.getLong(DAY_TIME));
    }

    /** Fetches (or creates) the persistent day time for the given level. */
    public static DimensionDayTimeState get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                // Negative marks "never stored", so a dimension seen for the first time can start from
                // whatever the shared clock reads rather than from the beginning of time.
                new SavedData.Factory<>(() -> new DimensionDayTimeState(-1L), DimensionDayTimeState::load, null),
                ID);
    }
}
