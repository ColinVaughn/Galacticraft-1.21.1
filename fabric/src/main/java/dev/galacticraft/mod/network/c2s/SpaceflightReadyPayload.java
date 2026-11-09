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

package dev.galacticraft.mod.network.c2s;

import dev.architectury.networking.NetworkManager;
import dev.galacticraft.impl.network.c2s.C2SPayload;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.content.entity.vehicle.RocketEntity;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import io.netty.buffer.ByteBuf;
import java.util.UUID;

public record SpaceflightReadyPayload(UUID rocket, UUID token, boolean terrainReady, float yaw, float pitch) implements C2SPayload {
    public static final Type<SpaceflightReadyPayload> TYPE = new Type<>(Constant.id("spaceflight_ready"));
    public static final StreamCodec<ByteBuf, SpaceflightReadyPayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, SpaceflightReadyPayload::rocket,
            UUIDUtil.STREAM_CODEC, SpaceflightReadyPayload::token,
            net.minecraft.network.codec.ByteBufCodecs.BOOL, SpaceflightReadyPayload::terrainReady,
            net.minecraft.network.codec.ByteBufCodecs.FLOAT, SpaceflightReadyPayload::yaw,
            net.minecraft.network.codec.ByteBufCodecs.FLOAT, SpaceflightReadyPayload::pitch, SpaceflightReadyPayload::new);

    @Override public void handle(NetworkManager.PacketContext context) {
        apply((ServerPlayer) context.getPlayer());
    }

    public void apply(ServerPlayer player) {
        if (!Float.isFinite(yaw) || !Float.isFinite(pitch) || pitch < -180 || pitch > 180) return;
        if (player.getVehicle() instanceof RocketEntity ship && ship.getUUID().equals(rocket)
                && ship.acknowledgeSpaceflight(player, token, terrainReady)
                && terrainReady && ship.isInSpaceflight() && ship.getFirstPassenger() == player) {
            ship.setYRot(net.minecraft.util.Mth.wrapDegrees(yaw));
            ship.setXRot(pitch);
        }
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}