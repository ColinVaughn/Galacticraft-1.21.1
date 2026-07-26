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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParachuteLogicTest {
    /** Vanilla {@code Attributes.SAFE_FALL_DISTANCE} default: falls shorter than this never hurt. */
    private static final float SAFE_FALL_DISTANCE = 3.0F;
    /** Vanilla living-entity gravity, in blocks per tick squared. */
    private static final double GRAVITY = 0.08D;
    /** Vanilla vertical drag applied to a falling living entity each tick. */
    private static final double DRAG = 0.98D;

    /** Where {@code OverworldCelestialTeleporterType} drops a returning player: max build height + 20. */
    private static final double REENTRY_HEIGHT = 340.0D;
    private static final double GROUND_HEIGHT = 64.0D;

    @Test
    void doesNotOpenWithoutAParachute() {
        assertFalse(ParachuteLogic.shouldBeOpen(false, false, false, 40.0F));
        assertFalse(ParachuteLogic.shouldBeOpen(true, false, false, 40.0F), "losing the parachute mid-fall must close it");
    }

    @Test
    void doesNotOpenWhileSuppressed() {
        assertFalse(ParachuteLogic.shouldBeOpen(false, true, true, 40.0F));
        assertFalse(ParachuteLogic.shouldBeOpen(true, true, true, 40.0F), "landing or entering water must close it");
    }

    @Test
    void opensOnceTheFallIsLongEnough() {
        assertFalse(ParachuteLogic.shouldBeOpen(false, true, false, 0.0F), "standing still must not open it");
        assertFalse(ParachuteLogic.shouldBeOpen(false, true, false, ParachuteLogic.DEPLOY_FALL_DISTANCE));
        assertTrue(ParachuteLogic.shouldBeOpen(false, true, false, ParachuteLogic.DEPLOY_FALL_DISTANCE + 0.01F));
    }

    @Test
    void staysOpenWhileFallDistanceIsHeldAtZero() {
        // The open canopy resets fall distance every tick, so the trigger condition stops holding.
        // Without a latch the canopy would flicker open and shut all the way down.
        assertTrue(ParachuteLogic.shouldBeOpen(true, true, false, 0.0F));
    }

    @Test
    void limitsDescentWithoutAddingLift() {
        assertEquals(-ParachuteLogic.DESCENT_SPEED, ParachuteLogic.limitDescent(-3.92D), 1.0E-9D, "terminal velocity must be capped");
        assertEquals(-0.1D, ParachuteLogic.limitDescent(-0.1D), 1.0E-9D, "a slow drift must be left alone");
        assertEquals(0.42D, ParachuteLogic.limitDescent(0.42D), 1.0E-9D, "upward motion must be left alone");
    }

    @Test
    void cappedDescentCannotAccumulateDamagingFallDistancePerTick() {
        // One tick of capped descent plus one tick of gravity must stay under the safe fall distance,
        // because the damage check runs inside the same tick the entity touches down.
        double perTick = ParachuteLogic.DESCENT_SPEED + GRAVITY;
        assertTrue(perTick < SAFE_FALL_DISTANCE, "descent speed " + ParachuteLogic.DESCENT_SPEED + " is too fast to land safely");
    }

    @Test
    void survivesTheReturnFromOrbit() {
        float impact = simulateDescent(true);
        assertTrue(impact < SAFE_FALL_DISTANCE,
                "returning with a parachute hit the ground with a fall distance of " + impact + " blocks");
    }

    @Test
    void withoutAParachuteTheReturnFromOrbitIsStillFatal() {
        // Guards the test above: it must be the parachute doing the work, not the simulation being lenient.
        float impact = simulateDescent(false);
        assertTrue(impact > SAFE_FALL_DISTANCE,
                "unprotected re-entry should be lethal but only accumulated " + impact + " blocks");
    }

    /**
     * Replays a vanilla fall from the re-entry drop height, in the order Minecraft runs it: gravity and
     * movement during {@code LivingEntity.travel}, then the Galacticraft accessory tick at the end of
     * {@code LivingEntity.tick}.
     *
     * @param hasParachute whether a parachute is equipped in an accessory slot
     * @return the fall distance handed to {@code causeFallDamage} on touchdown
     */
    private static float simulateDescent(boolean hasParachute) {
        double y = REENTRY_HEIGHT;
        // OverworldCelestialTeleporterType nudges the player downward on arrival.
        double verticalSpeed = -0.25D;
        float fallDistance = 0.0F;
        boolean open = false;

        for (int tick = 0; tick < 20 * 60 * 5; tick++) {
            verticalSpeed = (verticalSpeed - GRAVITY) * DRAG;
            y += verticalSpeed;
            fallDistance += (float) -verticalSpeed;
            if (y <= GROUND_HEIGHT) {
                return fallDistance;
            }

            open = ParachuteLogic.shouldBeOpen(open, hasParachute, false, fallDistance);
            if (open) {
                verticalSpeed = ParachuteLogic.limitDescent(verticalSpeed);
                fallDistance = 0.0F;
            }
        }
        throw new AssertionError("the descent never reached the ground");
    }
}
