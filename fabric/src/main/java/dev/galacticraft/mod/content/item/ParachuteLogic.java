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

package dev.galacticraft.mod.content.item;

/**
 * Descent rules for an equipped {@link ParachuteItem}, kept free of entity state so both sides of the
 * connection can reach the same answer from data they each already have.
 *
 * As in Galacticraft Legacy, a parachute in an accessory slot opens by itself once a fall gets going
 * and stays open until the wearer is back on solid ground.
 */
public final class ParachuteLogic {
    /**
     * How fast an open canopy lets the wearer sink, in blocks per tick. Comfortably under the vanilla
     * safe fall distance of three blocks, so touching down never accumulates damaging fall distance.
     */
    public static final double DESCENT_SPEED = 1.0D;

    private ParachuteLogic() {
    }

    /**
     * Decides whether the canopy is open this tick.
     *
     * The canopy opens once the fall has gone further than the wearer could land from unhurt, so it
     * stays out of the way of jumping and hopping down ledges and is out by the time a fall could do harm.
     * Reading that limit off the wearer rather than fixing it here keeps the trigger honest wherever they
     * are: Galacticraft stretches the safe fall distance in low gravity, which is also where the same jump
     * carries far enough to have tripped a fixed trigger.
     *
     * Once open it latches, because an open canopy clears the wearer's fall distance every tick and
     * the trigger condition would otherwise stop holding immediately.
     *
     * @param open              whether the canopy was open last tick
     * @param hasParachute      whether a parachute is still equipped in an accessory slot
     * @param suppressed        whether something rules out parachuting - standing on the ground, swimming,
     *                          riding a vehicle, spectating, or flying under some other power
     * @param fallDistance      the wearer's current fall distance, in blocks
     * @param safeFallDistance  how far the wearer can fall unhurt, in blocks - vanilla's
     *                          {@code Attributes.SAFE_FALL_DISTANCE}, as adjusted for local gravity
     * @return whether the canopy should be open
     */
    public static boolean shouldBeOpen(boolean open, boolean hasParachute, boolean suppressed, float fallDistance, double safeFallDistance) {
        if (!hasParachute || suppressed) return false;
        return open || fallDistance > safeFallDistance;
    }

    /**
     * Caps how fast an open canopy allows the wearer to sink. Only ever slows a descent - upward motion
     * and gentle drifts pass through untouched, so the canopy never behaves like a jetpack.
     *
     * @param verticalSpeed the wearer's vertical speed, in blocks per tick
     * @return the vertical speed to fall at
     */
    public static double limitDescent(double verticalSpeed) {
        return Math.max(verticalSpeed, -DESCENT_SPEED);
    }
}
