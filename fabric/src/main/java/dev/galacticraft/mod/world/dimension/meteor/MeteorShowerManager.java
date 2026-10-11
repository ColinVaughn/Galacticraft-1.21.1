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

import dev.architectury.networking.NetworkManager;
import dev.galacticraft.mod.Galacticraft;
import dev.galacticraft.mod.api.config.Config;
import dev.galacticraft.mod.content.GCEntityTypes;
import dev.galacticraft.mod.content.entity.FallingMeteorEntity;
import dev.galacticraft.mod.network.s2c.MeteorShowerSyncPayload;
import dev.galacticraft.mod.util.Translations;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Server-side driver of one dimension's meteor activity. Ticked once per world tick from
 * {@code GCEventHandlers.onWorldTick}: advances the persistent {@link MeteorShowerState}, syncs it
 * to clients for the sky-streak layer, and spawns the handful of real meteoroids that actually
 * fall near players.
 *
 * Two rates are in play, as in a real sky. A low sporadic rate runs constantly, and an active
 * shower multiplies it up to {@link Config#meteorShowerPeakMultiplier()} times. Everything else a
 * meteor does - whether it burns out, breaks up or lands - is left to the physics.
 */
public final class MeteorShowerManager {
    /** Client sync cadence (ticks) while nothing changes, so late joiners and drift are covered. */
    private static final long KEEPALIVE_TICKS = 40L;
    /** Blocks above the build limit that meteoroids enter at. */
    private static final int ENTRY_HEIGHT_ABOVE_BUILD_LIMIT = 160;
    /** Keep natural flight paths away from occupied bases, while allowing strikes farther out. */
    private static final int TARGET_MIN_OFFSET = 96;
    private static final int TARGET_MAX_OFFSET = 256;
    private static final int PLACEMENT_ATTEMPTS = 12;
    /** Real meteoroid entry speeds, m/s. */
    private static final double MIN_ENTRY_SPEED = 11000.0;
    private static final double MAX_ENTRY_SPEED = 30000.0;
    /** Size at or above which a body is worth warning nearby players about. */
    private static final int WARNING_SIZE = 6;
    /** Bias toward small bodies; higher means large meteoroids are rarer. */
    private static final double SIZE_BIAS = 2.4;
    /** Earth's atmosphere sees far fewer gameplay meteors than exposed moons and planets. */
    private static final double OVERWORLD_SPAWN_INTERVAL_MULTIPLIER = 2.0;

    private MeteorShowerManager() {
    }

    public static void tick(ServerLevel level) {
        Config config = Galacticraft.CONFIG;
        if (!config.meteorsEnabled()) return;
        if (level.galacticraft$getCelestialBody() == null) return;

        MeteorShowerState state = MeteorShowerState.get(level);
        MeteorShowerTuning tuning = new MeteorShowerTuning(true,
                config.meteorShowerMeanInterval(),
                config.meteorShowerMinDuration(),
                config.meteorShowerMaxDuration(),
                config.meteorShowerIntensity());

        MeteorShowerPhase before = state.phase();
        boolean changed = state.tick(level.random, tuning);
        if (changed) {
            announcePhase(level, before, state.phase());
        }
        if (changed || level.getGameTime() % KEEPALIVE_TICKS == 0L) {
            sync(level, state);
        }

        trySpawn(level, state, config);
    }

    /** Current shower intensity in {@code [0, 1]} for this level; 0 outside an active shower. */
    public static float currentIntensity(ServerLevel level) {
        if (!Galacticraft.CONFIG.meteorsEnabled() || level.galacticraft$getCelestialBody() == null) return 0.0f;
        return MeteorShowerState.get(level).currentIntensity();
    }

    /** Sends the current shower state to every player in the level. */
    public static void sync(ServerLevel level, MeteorShowerState state) {
        MeteorShowerSyncPayload payload = new MeteorShowerSyncPayload(
                state.phase().id(),
                state.peakIntensity(),
                state.ticksIntoPhase(),
                state.phaseDuration(),
                state.remainingShowerTicks(),
                state.radiantYaw(),
                state.radiantPitch());
        for (ServerPlayer player : level.players()) {
            NetworkManager.sendToPlayer(player, payload);
        }
    }

    private static void announcePhase(ServerLevel level, MeteorShowerPhase before, MeteorShowerPhase after) {
        String key = switch (after) {
            case INCOMING -> Translations.Ui.METEOR_SHOWER_INCOMING;
            case WAXING -> Translations.Ui.METEOR_SHOWER_BEGAN;
            case DORMANT -> before == MeteorShowerPhase.WANING ? Translations.Ui.METEOR_SHOWER_ENDED : null;
            default -> null;
        };
        if (key == null) return;

        Component message = Component.translatable(key);
        for (ServerPlayer player : level.players()) {
            player.displayClientMessage(message, true);
        }
    }

    /**
     * Rolls for and spawns one meteoroid near a player. The effective rate is the sporadic
     * background scaled up by the current shower intensity and the config multiplier.
     */
    private static void trySpawn(ServerLevel level, MeteorShowerState state, Config config) {
        if (level.players().isEmpty()) return;

        float intensity = state.currentIntensity();
        double multiplier = 1.0 + intensity * (config.meteorShowerPeakMultiplier() - 1.0);
        double spawnRate = Math.max(0.0001f, config.meteorSpawnMultiplier()) * multiplier;
        double worldInterval = config.meteorSporadicInterval()
                * (level.dimension().equals(Level.OVERWORLD) ? OVERWORLD_SPAWN_INTERVAL_MULTIPLIER : 1.0);
        int interval = Math.max(1, (int) (worldInterval / spawnRate));

        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) continue;
            if (level.random.nextInt(interval) != 0) continue;

            // Two players standing together should not each pull their own meteor.
            Player nearest = level.getNearestPlayer(player, 96.0);
            if (nearest != null && nearest.getId() < player.getId()) continue;

            if (countMeteors(level) >= config.meteorMaxConcurrent()) return;
            spawn(level, state, player);
            return;
        }
    }

    private static int countMeteors(ServerLevel level) {
        return level.getEntities(GCEntityTypes.FALLING_METEOR, entity -> true).size();
    }

    private static void spawn(ServerLevel level, MeteorShowerState state, ServerPlayer player) {
        RandomSource random = level.random;
        MeteoroidClass type = MeteoroidClass.random(random);
        int size = rollSize(random);
        double speed = MIN_ENTRY_SPEED + random.nextDouble() * (MAX_ENTRY_SPEED - MIN_ENTRY_SPEED);
        double spawnY = level.getMaxBuildHeight() + ENTRY_HEIGHT_ABOVE_BUILD_LIMIT;
        int tickingDistance = Math.min(level.getServer().getPlayerList().getViewDistance(),
                level.getServer().getPlayerList().getSimulationDistance());
        int maxOffset = targetMaxOffset(tickingDistance);
        if (maxOffset < TARGET_MIN_OFFSET) return;
        List<Vec3> occupied = level.players().stream().filter(p -> !p.isSpectator()).map(Player::position).toList();

        for (int attempt = 0; attempt < PLACEMENT_ATTEMPTS; attempt++) {
            Vec3 candidate = player.position().add(targetOffset(random, maxOffset));
            BlockPos column = BlockPos.containing(candidate);
            if (!level.isPositionEntityTicking(column) || !level.getWorldBorder().isWithinBounds(column)) continue;
            // Aim at the surface, even when the observer is underground or on a tall roof.
            Vec3 target = new Vec3(candidate.x, level.getHeight(Heightmap.Types.OCEAN_FLOOR,
                    column.getX(), column.getZ()), candidate.z);
            double drop = spawnY - target.y;
            int maxRun = Math.max(16, (tickingDistance - 2) * 16);
            float minPitch = (float) Math.toDegrees(Math.atan2(drop, maxRun));
            float yaw;
            float pitch;
            if (state.phase().isShowerActive()) {
                yaw = state.radiantYaw() + (random.nextFloat() - 0.5f) * 12.0f;
                pitch = Math.max(minPitch, state.radiantPitch());
            } else {
                yaw = random.nextFloat() * 360.0f;
                pitch = Mth.lerp(random.nextFloat(), Math.max(minPitch, 50.0f), Math.max(minPitch, 88.0f));
            }
            pitch = Mth.clamp(pitch, minPitch, 90.0f);

            double pitchRad = Math.toRadians(pitch), yawRad = Math.toRadians(yaw);
            Vec3 radiant = new Vec3(Math.cos(pitchRad) * Math.sin(yawRad), Math.sin(pitchRad),
                    Math.cos(pitchRad) * Math.cos(yawRad));
            Vec3 entry = target.add(radiant.scale(drop / radiant.y));
            if (!pathClearsPlayers(entry, target, occupied) || !pathIsTicking(level, entry, target)) continue;

            FallingMeteorEntity meteor = new FallingMeteorEntity(GCEntityTypes.FALLING_METEOR, level);
            meteor.setPos(entry.x, entry.y, entry.z);
            meteor.initialise(type, size, random.nextInt(), radiant.scale(-speed));
            level.addFreshEntity(meteor);
            if (size >= WARNING_SIZE) warnNearby(level, target.x, target.z);
            return;
        }
        // Never fall back to dropping a meteor closer to the player when placement fails.
    }

    static int targetMaxOffset(int tickingDistance) {
        return Math.min(TARGET_MAX_OFFSET, (tickingDistance + 1) * 16);
    }

    static Vec3 targetOffset(RandomSource random, int maxOffset) {
        if (maxOffset < TARGET_MIN_OFFSET) throw new IllegalArgumentException("No distant target area");
        double angle = random.nextDouble() * Math.PI * 2;
        // Uniform area: a uniform radius disproportionately bombards the inner edge near bases.
        double range = Math.sqrt(Mth.lerp(random.nextDouble(), TARGET_MIN_OFFSET * TARGET_MIN_OFFSET,
                (double) maxOffset * maxOffset));
        return new Vec3(Math.cos(angle) * range, 0, Math.sin(angle) * range);
    }

    static boolean pathClearsPlayers(Vec3 entry, Vec3 target, List<Vec3> players) {
        double dx = target.x - entry.x, dz = target.z - entry.z;
        double lengthSquared = dx * dx + dz * dz;
        for (Vec3 player : players) {
            double along = lengthSquared == 0 ? 0 : Mth.clamp(
                    ((player.x - entry.x) * dx + (player.z - entry.z) * dz) / lengthSquared, 0, 1);
            double x = entry.x + along * dx - player.x, z = entry.z + along * dz - player.z;
            if (x * x + z * z < TARGET_MIN_OFFSET * TARGET_MIN_OFFSET) return false;
        }
        return true;
    }

    private static boolean pathIsTicking(ServerLevel level, Vec3 entry, Vec3 target) {
        BlockPos start = BlockPos.containing(entry), end = BlockPos.containing(target);
        if (!level.getWorldBorder().isWithinBounds(start) || !level.getWorldBorder().isWithinBounds(end)) return false;
        // Check the enclosing chunk rectangle, covering every crossed chunk without loading any.
        for (int x = Math.min(start.getX(), end.getX()) >> 4; x <= (Math.max(start.getX(), end.getX()) >> 4); x++) {
            for (int z = Math.min(start.getZ(), end.getZ()) >> 4; z <= (Math.max(start.getZ(), end.getZ()) >> 4); z++) {
                if (!level.isPositionEntityTicking(new BlockPos(x << 4, 0, z << 4))) return false;
            }
        }
        return true;
    }

    /** A big one is coming in: everyone nearby hears it and gets told. */
    private static void warnNearby(ServerLevel level, double x, double z) {
        Component message = Component.translatable(Translations.Ui.METEOR_IMPACTOR_WARNING);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(x, player.getY(), z) > 300.0 * 300.0) continue;
            player.displayClientMessage(message, true);
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 1.0f, 0.5f);
        }
    }

    /**
     * Size class weighted toward small bodies, so a sky full of pebbles occasionally produces
     * something worth taking cover from.
     */
    private static int rollSize(RandomSource random) {
        double roll = Math.pow(random.nextDouble(), SIZE_BIAS);
        return Mth.clamp(MeteoroidShape.MIN_SIZE + (int) (roll * MeteoroidShape.MAX_SIZE),
                MeteoroidShape.MIN_SIZE, MeteoroidShape.MAX_SIZE);
    }
}
