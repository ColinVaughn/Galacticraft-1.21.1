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

import dev.galacticraft.mod.world.gen.JupiterWorldgen;
import dev.galacticraft.mod.content.block.jupiter.machine.LightningGrounderBlockEntity;
import dev.galacticraft.api.universe.celestialbody.CelestialBodyTags;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Storm Belt lightning. Below the top of the belt, strikes land near players, more often deeper and in the stormy
 * longitudes. A loaded Lightning Grounder within {@link #GROUNDER_RADIUS} blocks takes the strike instead.
 */
public final class JupiterStorms {
    public static final int GROUNDER_RADIUS = 16;
    private static final Map<ResourceKey<Level>, Set<BlockPos>> GROUNDERS = new HashMap<>();

    private JupiterStorms() { }

    /** Storm strength at a position: 0 above the Storm Belt, up to 1 deep in a storm band. */
    public static float activity(Level level, BlockPos pos) {
        if (!CelestialBodyTags.is(level, CelestialBodyTags.GAS_GIANT)) return 0;
        float depth = (float) Mth.clamp((144 - pos.getY()) / 96.0, 0, 1);
        float band = (float) (0.5 + 0.5 * Math.cos(pos.getZ() * Math.PI / 1024));
        float activity = depth * (0.4F + 0.6F * band);
        if (depth <= 0) return 0;
        // Red Vortices storm hardest; a Calm Eye is a refuge.
        var biome = level.getBiome(pos);
        if (biome.is(JupiterWorldgen.RED_VORTEX)) return Math.min(1, activity * 1.6F + 0.2F);
        if (biome.is(JupiterWorldgen.CALM_EYE)) return activity * 0.15F;
        return activity;
    }

    public static void addGrounder(ServerLevel level, BlockPos pos) {
        GROUNDERS.computeIfAbsent(level.dimension(), key -> new HashSet<>()).add(pos.immutable());
    }

    public static void removeGrounder(Level level, BlockPos pos) {
        Set<BlockPos> grounders = GROUNDERS.get(level.dimension());
        if (grounders != null) grounders.remove(pos);
    }

    public static void tick(ServerLevel level) {
        if (level.getGameTime() % 20 != 0 || !CelestialBodyTags.is(level, CelestialBodyTags.GAS_GIANT)) return;
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) continue;
            float activity = activity(level, player.blockPosition());
            if (activity <= 0 || level.random.nextFloat() >= 0.02F + 0.13F * activity) continue;
            strike(level, player.position().add(level.random.nextInt(49) - 24, level.random.nextInt(17) - 8, level.random.nextInt(49) - 24));
        }
    }

    /** Lands a strike at {@code target}, or on the nearest grounder covering it; returns that grounder, if any. */
    public static LightningGrounderBlockEntity strike(ServerLevel level, Vec3 target) {
        LightningGrounderBlockEntity grounder = nearestGrounder(level, BlockPos.containing(target));
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (grounder != null) grounder.ground();
        if (bolt == null) return grounder;
        if (grounder != null) {
            BlockPos pos = grounder.getBlockPos();
            bolt.moveTo(pos.getX() + 0.5, pos.getY() + 1.8, pos.getZ() + 0.5);
            bolt.setVisualOnly(true);
        } else {
            bolt.moveTo(target);
        }
        level.addFreshEntity(bolt);
        return grounder;
    }

    private static LightningGrounderBlockEntity nearestGrounder(ServerLevel level, BlockPos target) {
        Set<BlockPos> grounders = GROUNDERS.get(level.dimension());
        if (grounders == null) return null;
        LightningGrounderBlockEntity best = null;
        double bestDistance = GROUNDER_RADIUS * GROUNDER_RADIUS;
        for (var it = grounders.iterator(); it.hasNext(); ) {
            BlockPos pos = it.next();
            if (!level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof LightningGrounderBlockEntity grounder)) {
                it.remove();
                continue;
            }
            double distance = pos.distSqr(target);
            if (distance <= bestDistance) {
                best = grounder;
                bestDistance = distance;
            }
        }
        return best;
    }
}
