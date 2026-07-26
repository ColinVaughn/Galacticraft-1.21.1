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

public interface ParachuteAccessor {

    /**
     * Whether this entity's equipped parachute is currently holding it up.
     *
     * <p>Both sides work this out for themselves each tick, so this is the answer the physics and the
     * fall damage check use locally. Use {@link #galacticraft$isParachuteVisible()} for rendering, since
     * a client does not simulate the fall of anyone but its own player.
     */
    default boolean galacticraft$isParachuteOpen() {
        throw new RuntimeException("This should be overridden by mixin!");
    }

    default void galacticraft$setParachuteOpen(boolean open) {
        throw new RuntimeException("This should be overridden by mixin!");
    }

    /** Whether an open canopy should be drawn above this entity, including entities other clients control. */
    default boolean galacticraft$isParachuteVisible() {
        throw new RuntimeException("This should be overridden by mixin!");
    }
}
