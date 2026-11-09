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

package dev.galacticraft.mod.network.s2c;

import dev.architectury.networking.NetworkManager;
import dev.galacticraft.impl.network.s2c.S2CPayload;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.client.render.dimension.SpaceflightTransition;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import java.util.UUID;

/** Commits the reveal only after all riders are ready and their destination vehicles have been deployed. */
public record SpaceflightArrivalPayload(UUID token, UUID vehicle) implements S2CPayload {
    public static final Type<SpaceflightArrivalPayload> TYPE = new Type<>(Constant.id("spaceflight_arrival"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SpaceflightArrivalPayload> STREAM_CODEC = StreamCodec.of(
            (buf, value) -> { buf.writeUUID(value.token); buf.writeUUID(value.vehicle); },
            buf -> new SpaceflightArrivalPayload(buf.readUUID(), buf.readUUID()));
    @Override public Runnable handle(NetworkManager.PacketContext context) {
        return () -> SpaceflightTransition.arrive(token, vehicle);
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}