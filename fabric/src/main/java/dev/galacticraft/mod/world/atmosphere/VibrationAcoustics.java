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

package dev.galacticraft.mod.world.atmosphere;

import dev.galacticraft.mod.mixin.EntityPositionSourceAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.EntityPositionSource;
import net.minecraft.world.level.gameevent.PositionSource;
import net.minecraft.world.phys.Vec3;

import java.util.function.Predicate;

/**
 * Vibrations reach a listener either through the air, with range scaled by air density, or
 * through solid blocks touching the listener, which is the only path that works in a vacuum.
 */
public final class VibrationAcoustics {
    // Physically range scales with the square root of density, but that leaves Mars (0.006 atm)
    // hearing under a block and puts Venus past 6x vanilla. The fourth root keeps both playable.
    private static final double RANGE_EXPONENT = 0.25;
    // Contact reaches carriers two steps from the anchor, and a source sits at most one block above
    // its carrier, so nothing further than this can conduct.
    private static final double MAX_CONTACT_DISTANCE_SQR = 9.0;

    private VibrationAcoustics() {
    }

    public static float rangeMultiplier(float pressure) {
        float clamped = AtmosphericDensity.clamp(pressure);
        return clamped == 0.0F ? 0.0F : (float) Math.pow(clamped, RANGE_EXPONENT);
    }

    // The thinner end of the path decides, so a habitat on the Moon can't hear outside it.
    public static float rangeMultiplier(Level level, BlockPos source, BlockPos listener) {
        return rangeMultiplier(Math.min(AtmosphericDensity.at(level, source), AtmosphericDensity.at(level, listener)));
    }

    // Never below 1, since habitats hold 1 atm even on airless worlds.
    public static float widestMultiplier(Level level) {
        return Math.max(1.0F, rangeMultiplier(AtmosphericDensity.of(level)));
    }

    public static boolean reaches(Level level, BlockPos source, BlockPos listener, BlockPos anchor, int radius) {
        double distanceSqr = source.distSqr(listener);
        double widest = radius * widestMultiplier(level);
        boolean contactPossible = source.distSqr(anchor) <= MAX_CONTACT_DISTANCE_SQR;
        if (distanceSqr > widest * widest && !contactPossible) return false;

        double airRange = radius * rangeMultiplier(level, source, listener);
        if (distanceSqr <= airRange * airRange) return true;
        return contactPossible && conducts(source, anchor, pos -> conductive(level.getBlockState(pos)));
    }

    /**
     * The distance a sculk sensor converts into signal strength. Air at k times normal range makes a
     * sound feel k times closer; a vibration that also came through contact keeps its real distance
     * when that is stronger.
     */
    public static float perceivedDistance(Level level, BlockPos source, BlockPos listener, BlockPos anchor, float distance, int radius) {
        float best = Float.MAX_VALUE;
        float multiplier = rangeMultiplier(level, source, listener);
        if (multiplier > 0.0F && distance <= radius * multiplier) best = distance / multiplier;
        if (distance < best && source.distSqr(anchor) <= MAX_CONTACT_DISTANCE_SQR
                && conducts(source, anchor, pos -> conductive(level.getBlockState(pos)))) {
            best = distance;
        }
        // Neither path qualifies if the world changed while the vibration was in flight.
        return best == Float.MAX_VALUE ? distance : best;
    }

    // Entities listen from eye height (a warden's is ~2.5 blocks up) but touch the world at their feet.
    public static BlockPos anchor(PositionSource source, Vec3 listenerPos) {
        if (source instanceof EntityPositionSource) {
            return BlockPos.containing(listenerPos.x, listenerPos.y - ((EntityPositionSourceAccessor) source).galacticraft$getYOffset(), listenerPos.z);
        }
        return BlockPos.containing(listenerPos);
    }

    public static boolean conductive(BlockState state) {
        return !state.isAir() && !state.is(BlockTags.DAMPENS_VIBRATIONS);
    }

    // Footsteps and landings are emitted in the air block above the block actually struck.
    public static boolean conducts(BlockPos source, BlockPos anchor, Predicate<BlockPos> solid) {
        return carries(source, anchor, solid) || carries(source.below(), anchor, solid);
    }

    // The carrier must be the anchor, touch it, or touch a solid block that touches it.
    private static boolean carries(BlockPos carrier, BlockPos anchor, Predicate<BlockPos> solid) {
        int dx = carrier.getX() - anchor.getX();
        int dy = carrier.getY() - anchor.getY();
        int dz = carrier.getZ() - anchor.getZ();
        int steps = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
        if (steps > 2 || !solid.test(carrier)) return false;
        if (steps <= 1) return true;
        return dx != 0 && solid.test(anchor.offset(Integer.signum(dx), 0, 0))
                || dy != 0 && solid.test(anchor.offset(0, Integer.signum(dy), 0))
                || dz != 0 && solid.test(anchor.offset(0, 0, Integer.signum(dz)));
    }
}
