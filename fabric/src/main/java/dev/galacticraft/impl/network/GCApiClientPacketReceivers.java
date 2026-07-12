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

import dev.galacticraft.impl.network.s2c.*;
import dev.architectury.networking.NetworkManager;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Handles client-bound (S2C) packets.
 */
public class GCApiClientPacketReceivers {
    public static void register() {
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, AddSatellitePayload.TYPE, AddSatellitePayload.CODEC, (payload, context) -> context.queue(payload.handle(context)));
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, GearInvPayload.TYPE, GearInvPayload.CODEC, (payload, context) -> context.queue(payload.handle(context)));
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, OxygenUpdatePayload.TYPE, OxygenUpdatePayload.CODEC, (payload, context) -> context.queue(payload.handle(context)));
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, RemoveSatellitePayload.TYPE, RemoveSatellitePayload.CODEC, (payload, context) -> context.queue(payload.handle(context)));
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, ResearchUpdatePayload.TYPE, ResearchUpdatePayload.CODEC, (payload, context) -> context.queue(payload.handle(context)));
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, UpdateSatellitePayload.TYPE, UpdateSatellitePayload.CODEC, (payload, context) -> context.queue(payload.handle(context)));
    }

    public static <P extends S2CPayload> void registerPacket(CustomPacketPayload.Type<P> type) {
        throw new UnsupportedOperationException("Register payloads with their codecs");
    }
}
