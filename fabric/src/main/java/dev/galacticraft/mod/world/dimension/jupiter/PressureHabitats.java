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

package dev.galacticraft.mod.world.dimension.jupiter;

import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;

/**
 * Air volumes sealed by a powered Pressure Stabilizer; anyone inside is protected to {@link #RATING}. A stabilizer
 * refreshes its habitat every tick it runs, so one that stops ticking (unpowered, unloaded, or left behind in a world
 * that was closed) stops protecting within {@link #STALE_TICKS}.
 */
public final class PressureHabitats {
    public static final float RATING = 24;
    private static final long STALE_TICKS = 5;
    private record Habitat(LongSet volume, long refreshed) { }
    private static final Map<ResourceKey<Level>, Map<BlockPos, Habitat>> HABITATS = new HashMap<>();

    private PressureHabitats() { }

    public static void set(ServerLevel level, BlockPos stabilizer, LongSet volume) {
        HABITATS.computeIfAbsent(level.dimension(), key -> new HashMap<>()).put(stabilizer.immutable(), new Habitat(volume, level.getGameTime()));
    }

    public static void remove(ServerLevel level, BlockPos stabilizer) {
        Map<BlockPos, Habitat> habitats = HABITATS.get(level.dimension());
        if (habitats != null) habitats.remove(stabilizer);
    }

    /** The pressure rating a habitat gives at {@code pos}, or 1 outside every habitat. */
    public static float ratingAt(Level level, BlockPos pos) {
        Map<BlockPos, Habitat> habitats = HABITATS.get(level.dimension());
        if (habitats == null) return 1;
        long now = level.getGameTime();
        habitats.values().removeIf(habitat -> Math.abs(now - habitat.refreshed()) > STALE_TICKS);
        for (Habitat habitat : habitats.values()) if (habitat.volume().contains(pos.asLong())) return RATING;
        return 1;
    }
}
