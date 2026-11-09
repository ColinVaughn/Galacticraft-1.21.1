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
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;

public record SpaceflightHandoffPayload(UUID rocket, UUID token, ResourceKey<Level> destination,
                                        Vec3 flightPosition, Vec3 arrivalPosition, float yaw, float pitch, boolean cancelled) implements S2CPayload {
    public static final Type<SpaceflightHandoffPayload> TYPE = new Type<>(Constant.id("spaceflight_handoff"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SpaceflightHandoffPayload> STREAM_CODEC = StreamCodec.of(
            (buf, value) -> {
                buf.writeUUID(value.rocket); buf.writeUUID(value.token); buf.writeResourceKey(value.destination);
                buf.writeVec3(value.flightPosition); buf.writeVec3(value.arrivalPosition);
                buf.writeFloat(value.yaw); buf.writeFloat(value.pitch); buf.writeBoolean(value.cancelled);
            }, buf -> new SpaceflightHandoffPayload(buf.readUUID(), buf.readUUID(), buf.readResourceKey(Registries.DIMENSION),
                    buf.readVec3(), buf.readVec3(), buf.readFloat(), buf.readFloat(), buf.readBoolean()));

    @Override public Runnable handle(NetworkManager.PacketContext context) {
        return () -> {
            if (cancelled) SpaceflightTransition.cancel(token);
            else SpaceflightTransition.begin(this);
        };
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}