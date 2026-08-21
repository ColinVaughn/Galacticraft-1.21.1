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
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

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
    /** Meteoroids are aimed to pass through a point this far from the player, in blocks. */
    private static final int TARGET_MIN_OFFSET = 24;
    private static final int TARGET_MAX_OFFSET = 96;
    /** Real meteoroid entry speeds, m/s. */
    private static final double MIN_ENTRY_SPEED = 11000.0;
    private static final double MAX_ENTRY_SPEED = 30000.0;
    /** Size at or above which a body is worth warning nearby players about. */
    private static final int WARNING_SIZE = 6;
    /** Bias toward small bodies; higher means large meteoroids are rarer. */
    private static final double SIZE_BIAS = 2.4;
    /** Earth's atmosphere sees far fewer gameplay meteors than exposed moons and planets. */
    private static final double OVERWORLD_SPAWN_INTERVAL_MULTIPLIER = 4.0;

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

        // Aim for a point near the player rather than at them, then back-project the entry path.
        double targetAngle = random.nextDouble() * Math.PI * 2.0;
        double targetRange = TARGET_MIN_OFFSET + random.nextDouble() * (TARGET_MAX_OFFSET - TARGET_MIN_OFFSET);
        double targetX = player.getX() + Math.cos(targetAngle) * targetRange;
        double targetZ = player.getZ() + Math.sin(targetAngle) * targetRange;
        double targetY = player.getY();

        double drop = spawnY - targetY;
        // Entries must be steep enough that the whole path stays inside ticking chunks; a shallow
        // grazing entry over a 400-block drop would start a kilometre away and never tick.
        int maxRun = Math.max(48, (level.getServer().getPlayerList().getViewDistance() - 2) * 16);
        float minPitch = (float) Math.toDegrees(Math.atan2(drop, maxRun));

        float yaw;
        float pitch;
        if (state.phase().isShowerActive()) {
            // Shower meteors all stream from the shower's radiant, as real ones do.
            yaw = state.radiantYaw() + (random.nextFloat() - 0.5f) * 12.0f;
            pitch = Math.max(minPitch, state.radiantPitch());
        } else {
            yaw = random.nextFloat() * 360.0f;
            pitch = Mth.lerp(random.nextFloat(), Math.max(minPitch, 50.0f), 88.0f);
        }
        pitch = Mth.clamp(pitch, minPitch, 89.0f);

        // Unit vector pointing at the radiant; the body travels the opposite way.
        double pitchRad = Math.toRadians(pitch);
        double yawRad = Math.toRadians(yaw);
        double radiantX = Math.cos(pitchRad) * Math.sin(yawRad);
        double radiantY = Math.sin(pitchRad);
        double radiantZ = Math.cos(pitchRad) * Math.cos(yawRad);

        double travel = drop / radiantY;
        double spawnX = targetX + radiantX * travel;
        double spawnZ = targetZ + radiantZ * travel;

        FallingMeteorEntity meteor = new FallingMeteorEntity(GCEntityTypes.FALLING_METEOR, level);
        meteor.setPos(spawnX, spawnY, spawnZ);
        meteor.initialise(type, size, random.nextInt(),
                new Vec3(-radiantX * speed, -radiantY * speed, -radiantZ * speed));
        level.addFreshEntity(meteor);

        if (size >= WARNING_SIZE) {
            warnNearby(level, spawnX, spawnZ);
        }
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
