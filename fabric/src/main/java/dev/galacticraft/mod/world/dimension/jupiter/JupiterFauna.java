/*
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

import dev.galacticraft.mod.content.GCEntityTypes;
import dev.galacticraft.mod.content.entity.jupiter.CloudRayEntity;
import dev.galacticraft.mod.content.entity.jupiter.StaticJellyEntity;
import dev.galacticraft.mod.world.dimension.GCDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Spawns creatures at atmospheric altitudes around players because vanilla ground heightmaps
 * do not suit Jupiter. Cloud Rays occupy upper banks; Static Jelly density follows storm activity.
 */
public final class JupiterFauna {
    public static final int INTERVAL = 100, RAY_CAP = 4, JELLY_CAP = 6, STORM_TOP = 143, STORM_BOTTOM = 64;
    public static final double RAY_RANGE = 96, JELLY_RANGE = 64, MIN_PLAYER_DISTANCE = 16;

    private JupiterFauna() {
    }

    public static void tick(ServerLevel level) {
        if (level.getGameTime() % INTERVAL != 7 || !level.dimension().equals(GCDimensions.JUPITER)) return;
        if (!level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)) return;
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) continue;
            populate(level, player);
        }
    }

    /** One spawning pass around {@code player}; returns the creatures added. */
    public static int populate(ServerLevel level, ServerPlayer player) {
        int added = 0;
        if (count(level, CloudRayEntity.class, player, RAY_RANGE) < RAY_CAP
                && spawnNear(level, GCEntityTypes.CLOUD_RAY, player, CloudRayEntity.MIN_Y, CloudRayEntity.MAX_Y) != null) added++;
        if (player.getY() < 160 && level.getDifficulty() != Difficulty.PEACEFUL) {
            float activity = JupiterStorms.activity(level, BlockPos.containing(player.getX(), 104, player.getZ()));
            int cap = Math.round(JELLY_CAP * activity);
            if (count(level, StaticJellyEntity.class, player, JELLY_RANGE) < cap) {
                int low = Mth.clamp((int) player.getY() - 20, STORM_BOTTOM, STORM_TOP), high = Mth.clamp((int) player.getY() + 20, STORM_BOTTOM, STORM_TOP);
                if (spawnNear(level, GCEntityTypes.STATIC_JELLY, player, low, high) != null) added++;
            }
        }
        return added;
    }

    private static int count(ServerLevel level, Class<? extends Mob> type, ServerPlayer player, double range) {
        return level.getEntitiesOfClass(type, new AABB(player.blockPosition()).inflate(range)).size();
    }

    private static <T extends Mob> @Nullable T spawnNear(ServerLevel level, EntityType<T> type, ServerPlayer player, int minY, int maxY) {
        if (maxY < minY) return null;
        var random = level.random;
        for (int attempt = 0; attempt < 4; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2, distance = 24 + random.nextDouble() * 24;
            Vec3 at = new Vec3(player.getX() + Math.cos(angle) * distance, minY + random.nextInt(maxY - minY + 1), player.getZ() + Math.sin(angle) * distance);
            if (!level.isLoaded(BlockPos.containing(at)) || level.getNearestPlayer(at.x, at.y, at.z, MIN_PLAYER_DISTANCE, false) != null) continue;
            if (!level.noCollision(type.getSpawnAABB(at.x, at.y, at.z))) continue;
            T creature = type.create(level);
            if (creature == null) return null;
            creature.moveTo(at.x, at.y, at.z, random.nextFloat() * 360, 0);
            creature.finalizeSpawn(level, level.getCurrentDifficultyAt(creature.blockPosition()), MobSpawnType.NATURAL, null);
            level.addFreshEntity(creature);
            return creature;
        }
        return null;
    }
}
