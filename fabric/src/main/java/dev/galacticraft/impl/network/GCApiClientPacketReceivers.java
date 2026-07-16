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

package dev.galacticraft.impl.network;

import dev.galacticraft.api.accessor.GearInventoryProvider;
import dev.galacticraft.api.accessor.SatelliteAccessor;
import dev.galacticraft.api.client.accessor.ClientSatelliteAccessor;
import dev.galacticraft.api.universe.celestialbody.CelestialBody;
import dev.galacticraft.impl.client.accessor.ClientResearchAccessor;
import dev.galacticraft.impl.internal.accessor.ChunkSectionOxygenAccessor;
import dev.galacticraft.impl.network.s2c.*;
import dev.galacticraft.impl.universe.celestialbody.type.SatelliteType;
import dev.architectury.networking.NetworkManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Handles client-bound (S2C) packets.
 */
@Environment(EnvType.CLIENT)
public class GCApiClientPacketReceivers {
    public static void register() {
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, AddSatellitePayload.TYPE, AddSatellitePayload.CODEC, (payload, context) -> context.queue(() -> handle(payload)));
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, GearInvPayload.TYPE, GearInvPayload.CODEC, (payload, context) -> context.queue(() -> handle(payload)));
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, OxygenUpdatePayload.TYPE, OxygenUpdatePayload.CODEC, (payload, context) -> context.queue(() -> handle(payload)));
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, RemoveSatellitePayload.TYPE, RemoveSatellitePayload.CODEC, (payload, context) -> context.queue(() -> handle(payload)));
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, ResearchUpdatePayload.TYPE, ResearchUpdatePayload.CODEC, (payload, context) -> context.queue(() -> handle(payload, context)));
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, UpdateSatellitePayload.TYPE, UpdateSatellitePayload.CODEC, (payload, context) -> context.queue(() -> handle(payload)));
    }

    private static void handle(AddSatellitePayload payload) {
        ((ClientSatelliteAccessor) Minecraft.getInstance().player.connection).galacticraft$addSatellite(
                new CelestialBody<>(SatelliteType.INSTANCE, payload.config()), payload.newlyCreated());
    }

    private static void handle(GearInvPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        Entity entity = minecraft.player != null && minecraft.player.getId() == payload.entityId()
                ? minecraft.player
                : minecraft.level == null ? null : minecraft.level.getEntity(payload.entityId());
        if (entity instanceof GearInventoryProvider provider) {
            Container container = provider.galacticraft$getGearInv();
            for (int i = 0; i < Math.min(payload.items().length, container.getContainerSize()); i++) {
                container.setItem(i, payload.items()[i]);
            }
        }
    }

    private static void handle(OxygenUpdatePayload payload) {
        LevelChunk chunk = Minecraft.getInstance().level.getChunk(ChunkPos.getX(payload.chunk()), ChunkPos.getZ(payload.chunk()));
        for (OxygenUpdatePayload.OxygenData datum : payload.data()) {
            ChunkSectionOxygenAccessor accessor = (ChunkSectionOxygenAccessor) chunk.getSection(datum.section());
            accessor.galacticraft$setBits(datum.data());
        }
    }

    private static void handle(RemoveSatellitePayload payload) {
        ((SatelliteAccessor) Minecraft.getInstance().getConnection()).galacticraft$removeSatellite(payload.id());
    }

    private static void handle(ResearchUpdatePayload payload, NetworkManager.PacketContext context) {
        ((ClientResearchAccessor) context.getPlayer()).galacticraft$updateResearch(payload.add(), payload.ids());
    }

    private static void handle(UpdateSatellitePayload payload) {
        ((ClientSatelliteAccessor) Minecraft.getInstance().player.connection).galacticraft$updateSatellite(
                new CelestialBody<>(SatelliteType.INSTANCE, payload.config()));
    }

    public static <P extends S2CPayload> void registerPacket(CustomPacketPayload.Type<P> type) {
        throw new UnsupportedOperationException("Register payloads with their codecs");
    }
}
