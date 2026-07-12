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

package dev.galacticraft.mod.client.network;

import dev.galacticraft.impl.network.s2c.S2CPayload;
import dev.galacticraft.mod.network.s2c.*;
import dev.architectury.networking.NetworkManager;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public class GCClientPacketReceiver {
    public static void register() {
        register(BubbleSizePayload.TYPE, BubbleSizePayload.STREAM_CODEC);
        register(BubbleUpdatePayload.TYPE, BubbleUpdatePayload.STREAM_CODEC);
        register(OpenCelestialScreenPayload.TYPE, OpenCelestialScreenPayload.STREAM_CODEC);
        register(FootprintPacket.TYPE, FootprintPacket.STREAM_CODEC);
        register(FootprintRemovedPacket.TYPE, FootprintRemovedPacket.STREAM_CODEC);
        register(ResetPerspectivePacket.TYPE, ResetPerspectivePacket.STREAM_CODEC);
        register(CapeAssignmentsPacket.TYPE, CapeAssignmentsPacket.STREAM_CODEC);
        register(DustStormSyncPayload.TYPE, DustStormSyncPayload.STREAM_CODEC);
        register(SolarFlareSyncPayload.TYPE, SolarFlareSyncPayload.STREAM_CODEC);
    }

    public static <P extends S2CPayload> void register(CustomPacketPayload.Type<P> type,
            net.minecraft.network.codec.StreamCodec<? super net.minecraft.network.RegistryFriendlyByteBuf, P> codec) {
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, type, codec,
                (payload, context) -> context.queue(payload.handle(context)));
    }
}
