/*
 * Copyright (c) 2019-2026 Team Galacticraft
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

package dev.galacticraft.mod.world.dimension.duststorm;

import dev.galacticraft.api.universe.celestialbody.CelestialBody;
import dev.galacticraft.mod.Galacticraft;
import dev.galacticraft.mod.api.config.Config;
import dev.galacticraft.mod.content.GCCelestialBodies;
import dev.galacticraft.mod.content.block.special.CryogenicChamberBlock;
import dev.galacticraft.mod.content.block.special.CryogenicChamberPart;
import dev.galacticraft.mod.content.entity.damage.GCDamageTypes;
import dev.galacticraft.mod.content.entity.vehicle.LanderEntity;
import dev.galacticraft.mod.content.item.GCItems;
import dev.architectury.networking.NetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.phys.Vec3;

/**
 * Server-side driver of the Mars dust-storm cycle. Ticked once per Mars world tick from
 * {@code GCEventHandlers.onWorldTick}. Advances the persistent {@link MarsDustStormState},
 * syncs it to clients, and applies gear-gated hazard effects to exposed players.
 */
public final class MarsDustStormManager {
    /** Client sync cadence (ticks) while nothing changes, so late joiners and drift are covered. */
    private static final long KEEPALIVE_TICKS = 40L;

    private MarsDustStormManager() {
    }

    public static void tick(ServerLevel level) {
        if (!isMars(level)) return;
        Config config = Galacticraft.CONFIG;
        if (!config.dustStormsEnabled()) return;

        MarsDustStormState state = MarsDustStormState.get(level);
        DustStormTuning tuning = new DustStormTuning(true,
                config.dustStormMeanInterval(),
                config.dustStormMinDuration(),
                config.dustStormMaxDuration(),
                config.dustStormIntensity());

        boolean changed = state.tick(level.random, tuning);
        if (changed || level.getGameTime() % KEEPALIVE_TICKS == 0L) {
            sync(level, state);
        }

        if (state.phase().isStormActive()) {
            float intensity = state.currentIntensity();
            for (ServerPlayer player : level.players()) {
                applyPlayerEffects(player, level, intensity, config);
            }
        }

        // At the height of the storm, dust settles over the terrain near players (cosmetic).
        if (state.phase() == DustStormPhase.PEAK && config.terrainDustEnabled() && level.getGameTime() % 20L == 0L) {
            placeTerrainDust(level);
        }
    }

    private static final int MAX_DUST_PLACEMENTS_PER_SWEEP = 12;

    private static void placeTerrainDust(ServerLevel level) {
        RandomSource random = level.random;
        int placed = 0;
        for (ServerPlayer player : level.players()) {
            if (placed >= MAX_DUST_PLACEMENTS_PER_SWEEP) break;
            BlockPos base = player.blockPosition();
            for (int i = 0; i < 3 && placed < MAX_DUST_PLACEMENTS_PER_SWEEP; i++) {
                BlockPos surface = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                        base.offset(random.nextInt(24) - 12, 0, random.nextInt(24) - 12));
                BlockPos below = surface.below();
                if (level.isEmptyBlock(surface)
                        && level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)
                        && !(level.getBlockState(below).getBlock() instanceof dev.galacticraft.mod.content.block.environment.MarsDustLayerBlock)) {
                    level.setBlock(surface, dev.galacticraft.mod.content.GCBlocks.MARS_DUST_LAYER.defaultBlockState(),
                            net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
                    placed++;
                }
            }
        }
    }

    private static boolean isMars(ServerLevel level) {
        Holder<CelestialBody<?, ?>> body = level.galacticraft$getCelestialBody();
        return body != null && body.is(GCCelestialBodies.MARS);
    }

    /** Current dust intensity in {@code [0,1]} for this level, or 0 if not a Mars storm. */
    public static float currentIntensity(ServerLevel level) {
        if (!isMars(level) || !Galacticraft.CONFIG.dustStormsEnabled()) return 0.0f;
        return MarsDustStormState.get(level).currentIntensity();
    }

    /**
     * Generation multiplier for a sky-exposed solar panel, in {@code [0,1]}: 1.0 in clear weather,
     * dropping toward 0 as a dust storm peaks (scaled by the configured solar penalty).
     */
    public static double solarMultiplier(ServerLevel level) {
        float intensity = currentIntensity(level);
        if (intensity <= 0.0f) return 1.0;
        return Math.max(0.0, 1.0 - intensity * Galacticraft.CONFIG.dustStormSolarPenalty());
    }

    /** Sends the current storm state to every player in the level. */
    public static void sync(ServerLevel level, MarsDustStormState state) {
        var payload = new dev.galacticraft.mod.network.s2c.DustStormSyncPayload(
                state.phase().id(),
                state.peakIntensity(),
                state.ticksIntoPhase(),
                state.phaseDuration(),
                state.remainingStormTicks());
        for (ServerPlayer player : level.players()) {
            NetworkManager.sendToPlayer(player, payload);
        }
    }

    /**
     * Applies dust-storm effects to an exposed player, scaled by intensity and mitigated by gear:
     * a sealed suit blocks slowdown and damage, sensor glasses block the vision loss.
     */
    private static void applyPlayerEffects(ServerPlayer player, ServerLevel level, float intensity, Config config) {
        if (player.getAbilities().invulnerable || player.isSpectator()) return;
        if (player.getVehicle() instanceof LanderEntity) return;
        if (player.getInBlockState().getBlock() instanceof CryogenicChamberBlock
                || player.getInBlockState().getBlock() instanceof CryogenicChamberPart) return;

        BlockPos eyePos = player.blockPosition().relative(Direction.UP, (int) Math.floor(player.getEyeHeight(player.getPose())));
        // A sealed, breathable space (base or sealer bubble) keeps the dust out entirely.
        if (level.isBreathable(eyePos)) return;

        boolean sealed = player.galacticraft$hasMaskAndGear();
        boolean glasses = player.getItemBySlot(EquipmentSlot.HEAD).is(GCItems.SENSOR_GLASSES);

        // Vision loss in thick dust unless the sensor glasses cut through it.
        if (intensity > 0.65f && !glasses) {
            MobEffectInstance current = player.getEffect(MobEffects.DARKNESS);
            if (current == null || current.endsWithin(20)) {
                player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 40, 0, false, false, true));
            }
        }

        // Trudging through blowing dust slows you unless the suit seals it out.
        if (intensity > 0.40f && !sealed) {
            int amplifier = intensity > 0.75f ? 1 : 0;
            MobEffectInstance current = player.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
            if (current == null || current.endsWithin(20) || current.getAmplifier() < amplifier) {
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, amplifier, false, false, true));
            }
        }

        // Gusts nudge exposed players around.
        if (intensity > 0.60f && level.getGameTime() % 5L == 0L) {
            double force = (intensity - 0.60f) * 0.12;
            player.setDeltaMovement(player.getDeltaMovement().add(new Vec3(force, 0.0, force * 0.6)));
            player.hurtMarked = true;
        }

        // Abrasive, choking dust chips away at anyone caught without a sealed suit at peak.
        if (config.dustStormDamage() && intensity > 0.85f && !sealed && level.getGameTime() % 20L == 0L) {
            float damage = 1.0f + (intensity - 0.85f) * 6.0f;
            player.hurt(level.damageSources().source(GCDamageTypes.DUST_STORM), damage);
        }
    }
}
