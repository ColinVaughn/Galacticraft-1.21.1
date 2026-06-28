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

package dev.galacticraft.mod.world.dimension.solarflare;

import dev.galacticraft.api.universe.celestialbody.CelestialBody;
import dev.galacticraft.mod.Galacticraft;
import dev.galacticraft.mod.api.config.Config;
import dev.galacticraft.mod.content.GCCelestialBodies;
import dev.galacticraft.mod.content.block.special.CryogenicChamberBlock;
import dev.galacticraft.mod.content.block.special.CryogenicChamberPart;
import dev.galacticraft.mod.content.entity.damage.GCDamageTypes;
import dev.galacticraft.mod.content.entity.vehicle.LanderEntity;
import dev.galacticraft.mod.content.item.GCItems;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * Server-side driver of the Mercury solar-flare cycle. Ticked once per Mercury world tick from
 * {@code GCEventHandlers.onWorldTick}. Advances the persistent {@link MercurySolarFlareState},
 * syncs it to clients, and applies gear-gated hazard effects to exposed players.
 */
public final class MercurySolarFlareManager {
    /** Client sync cadence (ticks) while nothing changes, so late joiners and drift are covered. */
    private static final long KEEPALIVE_TICKS = 40L;

    private MercurySolarFlareManager() {
    }

    public static void tick(ServerLevel level) {
        if (!isMercury(level)) return;
        Config config = Galacticraft.CONFIG;
        if (!config.solarFlaresEnabled()) return;

        MercurySolarFlareState state = MercurySolarFlareState.get(level);
        SolarFlareTuning tuning = new SolarFlareTuning(true,
                config.solarFlareMeanInterval(),
                config.solarFlareMinDuration(),
                config.solarFlareMaxDuration(),
                config.solarFlareIntensity());

        boolean changed = state.tick(level.random, tuning);
        if (changed || level.getGameTime() % KEEPALIVE_TICKS == 0L) {
            sync(level, state);
        }

        if (state.phase().isFlareActive()) {
            float intensity = state.currentIntensity();
            for (ServerPlayer player : level.players()) {
                applyPlayerEffects(player, level, intensity, config);
            }
        }
    }

    private static boolean isMercury(ServerLevel level) {
        Holder<CelestialBody<?, ?>> body = level.galacticraft$getCelestialBody();
        return body != null && body.is(GCCelestialBodies.MERCURY);
    }

    /** Current flare intensity in {@code [0,1]} for this level, or 0 if not an active Mercury flare. */
    public static float currentIntensity(ServerLevel level) {
        if (!isMercury(level) || !Galacticraft.CONFIG.solarFlaresEnabled()) return 0.0f;
        return MercurySolarFlareState.get(level).currentIntensity();
    }

    /** Sends the current flare state to every player in the level. */
    public static void sync(ServerLevel level, MercurySolarFlareState state) {
        var payload = new dev.galacticraft.mod.network.s2c.SolarFlareSyncPayload(
                state.phase().id(),
                state.peakIntensity(),
                state.ticksIntoPhase(),
                state.phaseDuration(),
                state.remainingFlareTicks());
        for (ServerPlayer player : level.players()) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    /**
     * Applies solar-flare effects to an exposed player, scaled by intensity and mitigated by gear:
     * a full Isothermal Padding set blocks the burst heat damage, sensor glasses block the glare.
     */
    private static void applyPlayerEffects(ServerPlayer player, ServerLevel level, float intensity, Config config) {
        if (player.getAbilities().invulnerable || player.isSpectator()) return;
        if (player.getVehicle() instanceof LanderEntity) return;
        if (player.getInBlockState().getBlock() instanceof CryogenicChamberBlock
                || player.getInBlockState().getBlock() instanceof CryogenicChamberPart) return;

        BlockPos eyePos = player.blockPosition().relative(Direction.UP, (int) Math.floor(player.getEyeHeight(player.getPose())));
        // A sealed, breathable space (base or sealer bubble) keeps the flare out entirely.
        if (level.isBreathable(eyePos)) return;

        boolean glasses = player.getItemBySlot(EquipmentSlot.HEAD).is(GCItems.SENSOR_GLASSES);

        // Blinding glare at higher intensities, unless the sensor glasses cut through it.
        if (intensity > 0.5f && !glasses) {
            player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0, false, false, true));
        }

        // Searing radiation burst chips away at anyone caught without a full Isothermal Padding set at peak.
        if (config.solarFlareDamage() && intensity > 0.75f && !hasFullIsothermalSet(player) && level.getGameTime() % 20L == 0L) {
            float damage = 1.0f + (intensity - 0.75f) * 8.0f;
            player.hurt(level.damageSources().source(GCDamageTypes.SOLAR_FLARE), damage);
        }
    }

    /**
     * @return {@code true} if every thermal-armor slot holds an Isothermal Padding piece, i.e. the
     * player is wearing a full Isothermal Padding set and is protected from the flare's heat.
     */
    private static boolean hasFullIsothermalSet(ServerPlayer player) {
        Container thermalArmor = player.galacticraft$getThermalArmor();
        int size = thermalArmor.getContainerSize();
        if (size <= 0) return false;
        for (int slot = 0; slot < size; slot++) {
            if (!isIsothermalPadding(thermalArmor.getItem(slot))) return false;
        }
        return true;
    }

    private static boolean isIsothermalPadding(ItemStack stack) {
        return stack.is(GCItems.ISOTHERMAL_PADDING_HELMET)
                || stack.is(GCItems.ISOTHERMAL_PADDING_CHESTPIECE)
                || stack.is(GCItems.ISOTHERMAL_PADDING_LEGGINGS)
                || stack.is(GCItems.ISOTHERMAL_PADDING_BOOTS);
    }
}
