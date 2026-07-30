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
    /** Vanilla jump velocity for a living entity, in blocks per tick. */
    private static final double JUMP_POWER = 0.42D;

    /** Where {@code OverworldCelestialTeleporterType} drops a returning player: max build height + 20. */
    private static final double REENTRY_HEIGHT = 340.0D;
    private static final double GROUND_HEIGHT = 64.0D;

    @Test
    void doesNotOpenWithoutAParachute() {
        assertFalse(ParachuteLogic.shouldBeOpen(false, false, false, 40.0F, SAFE_FALL_DISTANCE));
        assertFalse(ParachuteLogic.shouldBeOpen(true, false, false, 40.0F, SAFE_FALL_DISTANCE), "losing the parachute mid-fall must close it");
    }

    @Test
    void doesNotOpenWhileSuppressed() {
        assertFalse(ParachuteLogic.shouldBeOpen(false, true, true, 40.0F, SAFE_FALL_DISTANCE));
        assertFalse(ParachuteLogic.shouldBeOpen(true, true, true, 40.0F, SAFE_FALL_DISTANCE), "landing or entering water must close it");
    }

    @Test
    void opensOnceTheFallCouldStartToHurt() {
        assertFalse(ParachuteLogic.shouldBeOpen(false, true, false, 0.0F, SAFE_FALL_DISTANCE), "standing still must not open it");
        assertFalse(ParachuteLogic.shouldBeOpen(false, true, false, SAFE_FALL_DISTANCE, SAFE_FALL_DISTANCE), "a fall that lands unhurt must not open it");
        assertTrue(ParachuteLogic.shouldBeOpen(false, true, false, SAFE_FALL_DISTANCE + 0.01F, SAFE_FALL_DISTANCE));
    }

    @Test
    void followsTheWearersOwnSafeFallDistance() {
        // Low gravity stretches the attribute, so the same fall that deploys on Earth is still a harmless
        // hop on the Moon.
        float moonSafeFallDistance = (float) (SAFE_FALL_DISTANCE / 0.166D);
        assertTrue(ParachuteLogic.shouldBeOpen(false, true, false, 5.0F, SAFE_FALL_DISTANCE));
        assertFalse(ParachuteLogic.shouldBeOpen(false, true, false, 5.0F, moonSafeFallDistance));
        assertTrue(ParachuteLogic.shouldBeOpen(false, true, false, 20.0F, moonSafeFallDistance));
    }

    @Test
    void aPlainJumpDoesNotOpenTheCanopy() {
        // Jumping straight up and coming back down is not a fall to be saved from, on any world. Low
        // gravity makes the same jump carry much further, so this has to hold well past Earth.
        assertFalse(jumpOpensTheCanopy(1.0D), "a jump on Earth opened the canopy");
        assertFalse(jumpOpensTheCanopy(0.91D), "a jump on Venus opened the canopy");
        assertFalse(jumpOpensTheCanopy(0.38D), "a jump on Mars opened the canopy");
        assertFalse(jumpOpensTheCanopy(0.166D), "a jump on the Moon opened the canopy");
        assertFalse(jumpOpensTheCanopy(0.1D), "a jump on an asteroid opened the canopy");
    }

    @Test
    void aFallWorthSavingFromStillOpensTheCanopy() {
        // Guards the test above: raising the trigger must not raise it past the falls that hurt.
        assertTrue(fallOpensTheCanopyBeforeLanding(1.0D), "a lethal fall on Earth never opened the canopy");
        assertTrue(fallOpensTheCanopyBeforeLanding(0.91D), "a lethal fall on Venus never opened the canopy");
        assertTrue(fallOpensTheCanopyBeforeLanding(0.38D), "a lethal fall on Mars never opened the canopy");
        assertTrue(fallOpensTheCanopyBeforeLanding(0.166D), "a lethal fall on the Moon never opened the canopy");
        assertTrue(fallOpensTheCanopyBeforeLanding(0.1D), "a lethal fall on an asteroid never opened the canopy");
    }

    @Test
    void staysOpenWhileFallDistanceIsHeldAtZero() {
        // The open canopy resets fall distance every tick, so the trigger condition stops holding.
        // Without a latch the canopy would flicker open and shut all the way down.
        assertTrue(ParachuteLogic.shouldBeOpen(true, true, false, 0.0F, SAFE_FALL_DISTANCE));
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

    /** Replays a standing jump on a world with the given gravity, relative to Earth. */
    private static boolean jumpOpensTheCanopy(double gravityFactor) {
        return simulate(JUMP_POWER, 0.0D, gravityFactor);
    }

    /** Replays a drop from three times the height this world starts hurting at. */
    private static boolean fallOpensTheCanopyBeforeLanding(double gravityFactor) {
        return simulate(0.0D, 3.0D * SAFE_FALL_DISTANCE / gravityFactor, gravityFactor);
    }

    /**
     * Replays vertical movement in the order Minecraft runs it: move by the current velocity, then apply
     * gravity and drag, then the Galacticraft accessory tick at the end of {@code LivingEntity.tick}.
     * Galacticraft scales gravity by the celestial body, so the same jump carries much further on the Moon.
     *
     * @param initialSpeed  the starting vertical speed, in blocks per tick
     * @param height        the starting height above the ground, in blocks
     * @param gravityFactor the celestial body's gravity, relative to Earth
     * @return whether the canopy was open at any point before touchdown
     */
    private static boolean simulate(double initialSpeed, double height, double gravityFactor) {
        double gravity = GRAVITY * gravityFactor;
        // LivingEntityMixin stretches the attribute the other way, so falls stay harmless for longer.
        double safeFallDistance = SAFE_FALL_DISTANCE / gravityFactor;
        double y = height;
        double verticalSpeed = initialSpeed;
        float fallDistance = 0.0F;
        boolean open = false;

        for (int tick = 0; tick < 20 * 60 * 5; tick++) {
            y += verticalSpeed;
            if (verticalSpeed < 0.0D) {
                fallDistance += (float) -verticalSpeed;
            }
            if (y <= 0.0D) {
                // Touched down. From here onGround suppresses the canopy, so this is the last say.
                return open;
            }

            verticalSpeed = (verticalSpeed - gravity) * DRAG;
            open = ParachuteLogic.shouldBeOpen(open, true, false, fallDistance, safeFallDistance);
            if (open) {
                verticalSpeed = ParachuteLogic.limitDescent(verticalSpeed);
                fallDistance = 0.0F;
            }
        }
        throw new AssertionError("the movement never reached the ground");
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

            open = ParachuteLogic.shouldBeOpen(open, hasParachute, false, fallDistance, SAFE_FALL_DISTANCE);
            if (open) {
                verticalSpeed = ParachuteLogic.limitDescent(verticalSpeed);
                fallDistance = 0.0F;
            }
        }
        throw new AssertionError("the descent never reached the ground");
    }
}
