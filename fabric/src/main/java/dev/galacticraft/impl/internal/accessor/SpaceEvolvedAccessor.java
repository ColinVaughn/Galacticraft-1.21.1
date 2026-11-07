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

package dev.galacticraft.impl.internal.accessor;

/**
 * A mob that was evolved where it stood when it arrived in an airless dimension, rather than being a
 * Galacticraft mob to begin with. Implemented on every {@code LivingEntity}.
 *
 * @see dev.galacticraft.mod.content.entity.SpaceEvolution
 */
public interface SpaceEvolvedAccessor {
    /** Returns whether this entity evolved for space and can breathe without air. */
    boolean galacticraft$isSpaceEvolved();

    void galacticraft$setSpaceEvolved(boolean evolved);

    /**
     * Dresses the entity in a mask, oxygen gear and two tanks. This is the synced state that the gear
     * render layers read, and it is saved with the entity.
     *
     * @param tankSize the tank item id path both tanks show, e.g. {@code medium_oxygen_tank}
     */
    void galacticraft$wearSpaceGear(String tankSize);
}
