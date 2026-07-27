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

package dev.galacticraft.mod.accessor;

/**
 * A clock of its own for a dimension that would otherwise be told the time by the Overworld.
 *
 * <p>Only the Overworld's level data actually stores a day time; every other dimension is handed a
 * {@code DerivedLevelData}, which reads the Overworld's clock and throws away writes to its own. That
 * is why sleeping or {@code /time set} in another dimension does nothing there, and why a body with a
 * day of its own cannot keep one. Implemented by the {@code DerivedLevelData} mixin, so every reader
 * of the level's day time - the sky, solar panels, mob spawning, the time packet sent to clients -
 * sees the dimension's own clock without knowing there is one.
 */
public interface DimensionDayTimeAccessor {
    /** {@return whether this dimension has taken over its own clock} */
    default boolean galacticraft$hasOwnDayTime() {
        throw new RuntimeException("This should be overridden by mixin!");
    }

    /**
     * Takes over this dimension's clock, starting at {@code dayTime}.
     *
     * <p>Until this is called the dimension reads and ignores writes exactly as vanilla does, so a
     * dimension that never starts a clock is left entirely alone.
     */
    default void galacticraft$startOwnDayTime(long dayTime) {
        throw new RuntimeException("This should be overridden by mixin!");
    }

    /** Runs this dimension's clock on by one tick. Does nothing until a clock has been started. */
    default void galacticraft$advanceOwnDayTime() {
        throw new RuntimeException("This should be overridden by mixin!");
    }
}
