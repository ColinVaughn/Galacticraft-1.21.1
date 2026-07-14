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

package dev.galacticraft.mod.network;

import dev.architectury.networking.NetworkManager;
import dev.galacticraft.mod.network.c2s.*;
import dev.galacticraft.mod.network.s2c.*;
import dev.galacticraft.mod.client.network.GCClientPacketReceiver;
import net.minecraft.server.level.ServerPlayer;

public class GCPackets {
    public static void register() {
        GCClientPacketReceiver.register();

        registerC2S(AirlockPlayerNamePayload.TYPE, AirlockPlayerNamePayload.STREAM_CODEC);
        registerC2S(BubbleMaxPayload.TYPE, BubbleMaxPayload.STREAM_CODEC);
        registerC2S(BubbleVisibilityPayload.TYPE, BubbleVisibilityPayload.STREAM_CODEC);
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, ControlEntityPayload.TYPE, ControlEntityPayload.STREAM_CODEC,
                (payload, context) -> context.queue(() -> payload.apply((ServerPlayer) context.getPlayer())));
        registerC2S(EjectCanPayload.TYPE, EjectCanPayload.STREAM_CODEC);
        registerC2S(OpenGcInventoryPayload.TYPE, OpenGcInventoryPayload.STREAM_CODEC);
        registerC2S(OpenPetInventoryPayload.TYPE, OpenPetInventoryPayload.STREAM_CODEC);
        registerC2S(OpenRocketPayload.TYPE, OpenRocketPayload.STREAM_CODEC);
        registerC2S(PlanetTeleportPayload.TYPE, PlanetTeleportPayload.STREAM_CODEC);
        registerC2S(SatelliteCreationPayload.TYPE, SatelliteCreationPayload.STREAM_CODEC);
        registerC2S(SatelliteUpdatePayload.TYPE, SatelliteUpdatePayload.STREAM_CODEC);
        registerC2S(CapeSelectionPayload.TYPE, CapeSelectionPayload.STREAM_CODEC);
        registerC2S(CreativeGcTransferItemPayload.TYPE, CreativeGcTransferItemPayload.STREAM_CODEC);
    }

    private static <P extends dev.galacticraft.impl.network.c2s.C2SPayload> void registerC2S(
            net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<P> type,
            net.minecraft.network.codec.StreamCodec<? super net.minecraft.network.RegistryFriendlyByteBuf, P> codec) {
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, type, codec,
                (payload, context) -> context.queue(() -> payload.handle(context)));
    }
}
