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

package dev.galacticraft.mod.events;

import dev.galacticraft.api.registry.ExtinguishableBlockRegistry;
import dev.galacticraft.api.registry.AcidTransformItemRegistry;
import dev.galacticraft.api.registry.AddonRegistries;
import dev.architectury.event.events.common.TickEvent;
import dev.architectury.networking.NetworkManager;
import dev.galacticraft.api.universe.celestialbody.CelestialBody;
import dev.galacticraft.api.universe.celestialbody.landable.Landable;
import dev.galacticraft.api.universe.celestialbody.landable.teleporter.CelestialTeleporter;
import dev.galacticraft.mod.misc.footprint.FootprintManager;
import dev.galacticraft.mod.network.s2c.FootprintRemovedPacket;
import dev.galacticraft.mod.statistics.GlobalStatisticsReporter;
import dev.galacticraft.mod.tag.GCEntityTypeTags;
import dev.galacticraft.mod.world.dimension.duststorm.MarsDustStormManager;
import dev.galacticraft.mod.world.dimension.meteor.MeteorShowerManager;
import dev.galacticraft.mod.world.dimension.solarflare.MercurySolarFlareManager;
import dev.galacticraft.mod.util.Translations;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

public class GCEventHandlers {
    // Galacticraft Legacy treated Y=30 as an invisible plane that returned entities
    // falling from an orbit dimension to the body below.
    static final double SATELLITE_REENTRY_HEIGHT = 30.0D;

    public static void init() {
        GCSleepEventHandlers.init();
        GCInteractionEventHandlers.init();
        GlobalStatisticsReporter.init();
        TickEvent.SERVER_LEVEL_POST.register(GCEventHandlers::onWorldTick);
    }

    public static void onPlayerChangePlanets(MinecraftServer server, ServerPlayer player, CelestialBody<?, ?> body, CelestialBody<?, ?> fromBody) {
        if (body.type() instanceof Landable landable && player.galacticraft$isCelestialScreenActive() && (player.galacticraft$getCelestialScreenState() == null || player.galacticraft$getCelestialScreenState().canTravel(server.registryAccess(), fromBody, body))) {
            player.galacticraft$closeCelestialScreen();
            ((CelestialTeleporter) landable.teleporter(body.config()).value()).onEnterAtmosphere(server.getLevel(landable.world(body.config())), player, body, fromBody);
        } else {
            player.connection.disconnect(Component.translatable(Translations.DimensionTp.INVALID_PACKET));
        }
    }

    public static void onWorldTick(ServerLevel level) {
        tickSatelliteReentryPlane(level);

        FootprintManager footprintManager = level.galacticraft$getFootprintManager();
        footprintManager.tick(level);
        if (!footprintManager.footprintBlockChanges.isEmpty()) {
            for (GlobalPos targetPoint : footprintManager.footprintBlockChanges) {
                if (level.dimension().location().equals(targetPoint.dimension().location())) {
                    long packedPos = ChunkPos.asLong(targetPoint.pos());
                    level.players().stream().filter(player -> player.distanceToSqr(targetPoint.pos().getCenter()) <= 2500.0)
                            .forEach(player -> NetworkManager.sendToPlayer(player,
                                    new FootprintRemovedPacket(packedPos, targetPoint.pos())));
                }
            }

            footprintManager.footprintBlockChanges.clear();
        }
        level.galacticraft$getSealerManager().tick();
        MarsDustStormManager.tick(level);
        MercurySolarFlareManager.tick(level);
        MeteorShowerManager.tick(level);
    }

    private static void tickSatelliteReentryPlane(ServerLevel level) {
        Holder<CelestialBody<?, ?>> holder = level.galacticraft$getCelestialBody();
        if (holder == null || !holder.value().isSatellite()) return;

        // Teleporting changes the entity's level, so collect candidates before mutating them.
        List<Entity> reentering = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (isBelowSatelliteReentryPlane(entity.getY())
                    && entity.getType().is(GCEntityTypeTags.CAN_REENTER_ATMOSPHERE)) {
                reentering.add(entity);
            }
        }
        reentering.forEach(GCEventHandlers::tryReenterAtmosphere);
    }

    static boolean isBelowSatelliteReentryPlane(double y) {
        return y <= SATELLITE_REENTRY_HEIGHT;
    }

    /**
     * Attempts to return an entity falling from a satellite to its parent body.
     *
     * @return whether a destination teleporter accepted the entity
     */
    public static boolean tryReenterAtmosphere(Entity entity) {
        if (!entity.getType().is(GCEntityTypeTags.CAN_REENTER_ATMOSPHERE)
                || !(entity.level() instanceof ServerLevel level)) {
            return false;
        }

        Holder<CelestialBody<?, ?>> holder = level.galacticraft$getCelestialBody();
        CelestialBody<?, ?> fromBody = holder != null ? holder.value() : null;
        if (fromBody == null || !fromBody.isSatellite() || fromBody.parent().isEmpty()) return false;

        Registry<CelestialBody<?, ?>> celestialBodies = level.registryAccess().registryOrThrow(AddonRegistries.CELESTIAL_BODY);
        CelestialBody<?, ?> body = fromBody.parentValue(celestialBodies);
        if (!(body.type() instanceof Landable landable)) return false;

        ServerLevel destination = level.getServer().getLevel(landable.world(body.config()));
        if (destination == null) return false;

        ((CelestialTeleporter) landable.teleporter(body.config()).value()).onEnterAtmosphere(destination, entity, body, fromBody);
        return true;
    }


    public static boolean extinguishBlock(Level level, BlockPos pos, BlockState oldState) {
        ExtinguishableBlockRegistry.Entry entry = ExtinguishableBlockRegistry.INSTANCE.get(oldState.getBlock());
        if (entry == null) return false;
        BlockState newState = entry.transform(oldState);
        if (newState == null) return false;
        level.setBlockAndUpdate(pos, newState);
        entry.callback(new ExtinguishableBlockRegistry.Context(level, pos, oldState));
        return true;
    }

    public static boolean sulfuricAcidTransformItem(ItemEntity itemEntity, ItemStack original) {
        AcidTransformItemRegistry.Entry entry = AcidTransformItemRegistry.INSTANCE.get(original.getItem());
        if (entry == null) return false;
        ItemStack itemStack = entry.transform(original.copy());
        if (itemStack == null) return false;
        itemEntity.setItem(itemStack);
        entry.callback(new AcidTransformItemRegistry.Context(itemEntity, original));
        return true;
    }
}
