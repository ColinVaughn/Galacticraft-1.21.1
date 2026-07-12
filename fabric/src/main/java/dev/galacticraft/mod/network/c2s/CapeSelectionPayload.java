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

package dev.galacticraft.mod.network.c2s;

import dev.galacticraft.impl.network.c2s.C2SPayload;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.misc.cape.CapeMode;
import dev.galacticraft.mod.misc.cape.ServerCapeManager;
import dev.galacticraft.mod.network.s2c.CapeAssignmentsPacket;
import dev.architectury.networking.NetworkManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

public record CapeSelectionPayload(CapeMode mode, String gcCapeId) implements C2SPayload {
    public static final ResourceLocation ID = Constant.id("cape_select");
    public static final Type<CapeSelectionPayload> TYPE = new Type<>(ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, CapeSelectionPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, msg) -> {
                        buf.writeEnum(msg.mode);
                        buf.writeBoolean(msg.gcCapeId != null);
                        if (msg.gcCapeId != null) buf.writeUtf(msg.gcCapeId);
                    },
                    buf -> {
                        CapeMode mode = buf.readEnum(CapeMode.class);
                        String id = buf.readBoolean() ? buf.readUtf() : null;
                        return new CapeSelectionPayload(mode, id);
                    }
            );

    @Override
    public void handle(NetworkManager.@NotNull PacketContext context) {
        var player = ((net.minecraft.server.level.ServerPlayer) context.getPlayer());
        CapeMode m = this.mode;
        String id = this.gcCapeId;

        if (!ServerCapeManager.validateSelection(player, m, id)) {
            m = CapeMode.VANILLA;
            id = null;
        }
        ServerCapeManager.set(player, m, id);

        var snap = ServerCapeManager.snapshot();
        var list = new ArrayList<CapeAssignmentsPacket.Entry>(snap.size());
        for (var e : snap.entrySet()) {
            var a = e.getValue();
            list.add(new CapeAssignmentsPacket.Entry(
                    e.getKey(),
                    a.mode,
                    a.mode == CapeMode.GC ? a.gcCapeId : null
            ));
        }

        NetworkManager.sendToPlayers(((net.minecraft.server.level.ServerPlayer) context.getPlayer()).server.getPlayerList().getPlayers(), new CapeAssignmentsPacket(list));
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
