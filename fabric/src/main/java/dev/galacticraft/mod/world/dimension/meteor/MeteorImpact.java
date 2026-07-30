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

package dev.galacticraft.mod.world.dimension.meteor;

import dev.galacticraft.mod.Galacticraft;
import dev.galacticraft.mod.content.entity.damage.GCDamageTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * What happens when a meteoroid survives the sky and reaches the ground: a crater sized from the
 * body's actual kinetic energy, and the remains of the body left in the floor.
 *
 * <p>Crater size uses the standard {@code D proportional to E^(1/3.4)} scaling anchored on a real
 * impact (see {@link MeteorPhysics#craterDiameter}), then is clamped by config so a freak strike
 * cannot swallow a base. Excavation writes blocks without neighbour updates, which keeps even a
 * large crater to a single cheap tick.
 */
public final class MeteorImpact {
    /** Ratio of crater depth to crater radius; real simple craters are much wider than deep. */
    private static final double DEPTH_RATIO = 0.55;
    /** How far above the impact point the bowl is cleared, as a fraction of the radius. */
    private static final double CLEARANCE_RATIO = 0.3;
    /** Rim wobble, as a fraction of the radius, so craters are not perfect circles. */
    private static final double RIM_JITTER = 0.18;
    /** Entities within this multiple of the crater radius are caught in the blast. */
    private static final double BLAST_RATIO = 1.6;
    /** Strewn-field radius as a fraction of the crater the strike would otherwise have dug. */
    private static final double STREWN_RATIO = 0.75;
    /** Placement attempts per block of a strewn field, so a crowded surface still gets a fair try. */
    private static final int STREWN_ATTEMPTS_PER_BLOCK = 8;

    private MeteorImpact() {
    }

    /**
     * Resolves a ground impact.
     *
     * @param location         where the body's swept path met the ground
     * @param physics          the body's state at impact, used for kinetic energy
     * @param type             the material class, which decides what is left behind
     * @param seed             the body's shape seed, reused to jitter the crater rim
     * @param totalVoxels      the body's full voxel count
     * @param survivingVoxels  how many voxels were still attached, which sets the deposit size
     */
    public static void strike(ServerLevel level, Vec3 location, MeteoroidState physics, MeteoroidClass type,
                              int seed, int totalVoxels, int survivingVoxels) {
        double energy = physics.kineticEnergy();
        int maxRadius = Math.max(1, Galacticraft.CONFIG.meteorMaxCraterRadius());
        int radius = (int) Mth.clamp(Math.round(MeteorPhysics.craterDiameter(energy) / 2.0), 1L, maxRadius);

        BlockPos impact = BlockPos.containing(location);
        RandomSource random = RandomSource.create(seed * 31L + impact.asLong());

        announce(level, location, radius);
        damageEntities(level, location, radius, energy);

        if (MeteorImpactRules.blockDamageEnabled(level)) {
            List<BlockPos> floor = excavate(level, impact, radius, seed);
            deposit(level, floor, type, random, survivingVoxels, totalVoxels);
        } else {
            scatter(level, impact, radius, type, random, survivingVoxels, totalVoxels);
        }
    }

    /** The flash, shockwave and noise of the strike. No block damage: the crater is dug by hand. */
    private static void announce(ServerLevel level, Vec3 location, int radius) {
        level.explode(null, location.x, location.y, location.z, radius * 0.5f, false, Level.ExplosionInteraction.NONE);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, location.x, location.y, location.z,
                Math.max(1, radius / 3), radius * 0.4, 1.0, radius * 0.4, 0.0);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, location.x, location.y, location.z,
                40 + radius * 8, radius * 0.6, 1.5, radius * 0.6, 0.06);
        level.playSound(null, location.x, location.y, location.z, SoundEvents.GENERIC_EXPLODE.value(),
                SoundSource.WEATHER, Math.min(10.0f, 3.0f + radius * 0.4f), 0.4f);
    }

    /** Anything standing near the strike is hurt and thrown, falling off with distance. */
    private static void damageEntities(ServerLevel level, Vec3 location, int radius, double energy) {
        double blast = radius * BLAST_RATIO;
        AABB area = new AABB(location, location).inflate(blast);
        float peak = (float) Mth.clamp(Math.log10(Math.max(1.0, energy)) * 4.0, 6.0, 60.0);

        for (Entity entity : level.getEntities((Entity) null, area, EntitySelector.NO_SPECTATORS.and(Entity::isAlive))) {
            double distance = entity.position().distanceTo(location);
            if (distance > blast) continue;
            double falloff = 1.0 - distance / blast;
            entity.hurt(level.damageSources().source(GCDamageTypes.METEOR_STRIKE), (float) (peak * falloff));

            Vec3 push = entity.position().subtract(location).normalize().scale(falloff * 1.4).add(0.0, falloff * 0.5, 0.0);
            entity.setDeltaMovement(entity.getDeltaMovement().add(push));
            entity.hurtMarked = true;
        }
    }

    /**
     * Carves the bowl and returns the floor positions, where the remains will settle.
     *
     * <p>The profile is a parabola so the crater is wide and shallow like a real simple crater, and
     * per-column jitter keeps the rim from looking stamped out.
     */
    private static List<BlockPos> excavate(ServerLevel level, BlockPos impact, int radius, int seed) {
        double depth = radius * DEPTH_RATIO;
        int clearance = Mth.ceil(radius * CLEARANCE_RATIO);
        List<BlockPos> floor = new ArrayList<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                double horizontal = Math.sqrt(dx * dx + dz * dz);
                double columnRadius = radius * (1.0 + jitter(seed, dx, dz) * RIM_JITTER);
                if (horizontal > columnRadius) continue;

                double normalised = horizontal / columnRadius;
                int columnDepth = Mth.ceil(depth * (1.0 - normalised * normalised));
                if (columnDepth <= 0 && horizontal > columnRadius * 0.85) continue;

                boolean floorRecorded = false;
                for (int dy = -columnDepth; dy <= clearance; dy++) {
                    cursor.set(impact.getX() + dx, impact.getY() + dy, impact.getZ() + dz);
                    if (level.isOutsideBuildHeight(cursor)) continue;

                    BlockState state = level.getBlockState(cursor);
                    if (state.isAir()) continue;
                    if (state.getDestroySpeed(level, cursor) < 0.0f) continue; // bedrock and friends

                    if (!floorRecorded) {
                        floor.add(cursor.immutable());
                        floorRecorded = true;
                    }
                    level.setBlock(cursor, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
        return floor;
    }

    /**
     * How much of the body is left as recoverable blocks: what survived the fall, capped by what
     * the body was made of and by the space there is to put it.
     *
     * <p>Shared by the crater and the strewn field so a dimension that forbids block damage pays
     * out exactly what a cratering one would.
     */
    public static int depositCount(int survivingVoxels, int totalVoxels, int availableSlots) {
        if (availableSlots <= 0) return 0;
        return Mth.clamp(survivingVoxels, 1, Math.max(1, Math.min(totalVoxels, availableSlots)));
    }

    /** Radius the meteorite is strewn over when no crater is dug, in blocks. */
    public static int strewnRadius(int craterRadius) {
        return Math.max(1, Math.min(craterRadius, Mth.ceil(craterRadius * STREWN_RATIO)));
    }

    /** Scatters the surviving body through the crater floor as recoverable blocks. */
    private static void deposit(ServerLevel level, List<BlockPos> floor, MeteoroidClass type, RandomSource random,
                                int survivingVoxels, int totalVoxels) {
        if (floor.isEmpty()) return;

        int target = depositCount(survivingVoxels, totalVoxels, floor.size());
        for (int i = 0; i < target; i++) {
            BlockPos pos = floor.get(random.nextInt(floor.size()));
            if (!level.getBlockState(pos).isAir()) continue;
            level.setBlock(pos, MeteoroidPalette.depositState(type, random), Block.UPDATE_ALL);
        }
    }

    /**
     * The no-block-damage path: lay the meteorite out as a strewn field on top of the ground
     * instead of digging it in.
     *
     * <p>Blocks only ever go where the surface is already replaceable — air, grass, snow, a
     * flower — so a strike over someone's base leaves the base alone. The count matches what the
     * crater would have deposited, which is the point: the dimension gives up its terrain damage
     * without giving up its loot.
     */
    private static void scatter(ServerLevel level, BlockPos impact, int craterRadius, MeteoroidClass type,
                                RandomSource random, int survivingVoxels, int totalVoxels) {
        int radius = strewnRadius(craterRadius);
        List<BlockPos> surface = surfaceCandidates(level, impact, radius);
        if (surface.isEmpty()) return;

        int target = depositCount(survivingVoxels, totalVoxels, surface.size());
        int attempts = target * STREWN_ATTEMPTS_PER_BLOCK;
        int placed = 0;

        for (int attempt = 0; attempt < attempts && placed < target; attempt++) {
            BlockPos pos = surface.get(random.nextInt(surface.size()));
            if (!level.getBlockState(pos).canBeReplaced()) continue; // taken by an earlier fragment
            level.setBlock(pos, MeteoroidPalette.depositState(type, random), Block.UPDATE_ALL);
            placed++;
        }
    }

    /** Surface positions around the impact that a meteorite may be dropped onto without loss. */
    private static List<BlockPos> surfaceCandidates(ServerLevel level, BlockPos impact, int radius) {
        List<BlockPos> candidates = new ArrayList<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radius * radius) continue;

                cursor.set(impact.getX() + dx, impact.getY(), impact.getZ() + dz);
                // OCEAN_FLOOR ignores fluids, so a meteorite settles on the seabed rather than
                // floating on the waves, and lands inside grass or snow rather than on top of it.
                BlockPos pos = level.getHeightmapPos(Heightmap.Types.OCEAN_FLOOR, cursor);
                if (level.isOutsideBuildHeight(pos)) continue;
                if (!level.getBlockState(pos).canBeReplaced()) continue;
                candidates.add(pos);
            }
        }
        return candidates;
    }

    /** Stable per-column noise in {@code [-1, 1]} so a crater's rim is the same every time it loads. */
    private static double jitter(int seed, int dx, int dz) {
        int hash = seed * 0x9E3779B1;
        hash = (hash ^ (dx * 0x85EBCA6B)) * 0xC2B2AE35;
        hash = (hash ^ (dz * 0x27D4EB2F)) * 0x165667B1;
        hash ^= hash >>> 15;
        return ((hash & 0xFFFF) / 32768.0) - 1.0;
    }
}
