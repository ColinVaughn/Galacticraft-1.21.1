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

package dev.galacticraft.mod.util;

import dev.galacticraft.api.universe.celestialbody.CelestialBody;
import dev.galacticraft.mod.tag.GCDimensionTypeTags;
import net.minecraft.core.Holder;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Day lengths for dimensions whose day is not 24000 ticks long.
 *
 * <p>A celestial body spins at its own rate - the Moon takes eight vanilla days to turn once - and the
 * sky is drawn from that rate rather than from the vanilla one. Anything that wants to name a point in
 * the day ("morning", "noon") has to be expressed against the same rate, or it lands somewhere else in
 * the sky than its name says.
 *
 * @see dev.galacticraft.impl.internal.mixin.LevelTimeAccessMixin the sky angle these lengths feed
 */
public final class DimensionTime {
    /** The length of a vanilla day, and of any dimension that does not declare its own. */
    public static final long VANILLA_DAY_LENGTH = 24000L;

    private DimensionTime() {}

    /**
     * {@return how many ticks {@code level} takes to turn once}
     *
     * <p>The gate matches the one the sky renderer uses, so a "morning" derived from this length is the
     * moment the sun actually comes up in that dimension.
     */
    public static long dayLength(@Nullable Level level) {
        if (level == null) return VANILLA_DAY_LENGTH;
        Holder<CelestialBody<?, ?>> body = level.galacticraft$getCelestialBody();
        if (body == null || !level.galacticraft$hasDimensionTypeTag(GCDimensionTypeTags.SPACE)) {
            return VANILLA_DAY_LENGTH;
        }
        long dayLength = body.value().dayLength();
        return dayLength > 0L ? dayLength : VANILLA_DAY_LENGTH;
    }

    /**
     * {@return the first day time after {@code dayTime} at which a {@code dayLength}-tick day begins}
     *
     * <p>This is Galacticraft Legacy's {@code WorldUtil.setNextMorning}: sleeping runs the clock on to
     * the start of the dimension's next day, however many vanilla days that spans.
     */
    public static long nextMorning(long dayTime, long dayLength) {
        if (dayLength <= 0L) return dayTime;
        return dayTime - Math.floorMod(dayTime, dayLength) + dayLength;
    }

    /**
     * {@return the point of a {@code dayLength}-tick day that sits where {@code vanillaDayTime} sits in a
     * vanilla one}
     *
     * <p>Both days are drawn with the same curve, so scaling by the ratio of their lengths puts the sun
     * at the same height: on the Moon, noon is 48000 rather than 6000.
     */
    public static long sameTimeOfDay(long vanillaDayTime, long dayLength) {
        if (dayLength <= 0L) return vanillaDayTime;
        return Math.floorDiv(vanillaDayTime * dayLength, VANILLA_DAY_LENGTH);
    }

    /**
     * {@return where {@code dayTime} sits in a {@code dayLength}-tick day, counted in the ticks of a
     * vanilla one}
     *
     * <p>The inverse of {@link #sameTimeOfDay}, within a single day: lunar noon reads back as 6000.
     */
    public static long vanillaTimeOfDay(long dayTime, long dayLength) {
        if (dayLength <= 0L) return dayTime;
        return Math.floorDiv(Math.floorMod(dayTime, dayLength) * VANILLA_DAY_LENGTH, dayLength);
    }
}
