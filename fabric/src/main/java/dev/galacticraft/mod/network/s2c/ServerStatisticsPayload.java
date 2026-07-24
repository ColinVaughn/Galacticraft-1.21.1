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
import dev.galacticraft.mod.client.gui.screen.ingame.SpaceRaceScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public record ServerStatisticsPayload(int trackedPlayers, int onlinePlayers, List<Entry> entries) implements S2CPayload {
    public static final ResourceLocation ID = Constant.id("server_statistics");
    public static final Type<ServerStatisticsPayload> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, ServerStatisticsPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeVarInt(payload.trackedPlayers());
                buffer.writeVarInt(payload.onlinePlayers());
                buffer.writeVarInt(payload.entries().size());
                for (Entry entry : payload.entries()) {
                    buffer.writeResourceLocation(entry.stat());
                    buffer.writeVarLong(entry.total());
                }
            },
            buffer -> {
                int trackedPlayers = buffer.readVarInt();
                int onlinePlayers = buffer.readVarInt();
                int size = Math.min(buffer.readVarInt(), 64);
                List<Entry> entries = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    entries.add(new Entry(buffer.readResourceLocation(), buffer.readVarLong()));
                }
                return new ServerStatisticsPayload(trackedPlayers, onlinePlayers, List.copyOf(entries));
            }
    );

    @Override
    public Runnable handle(NetworkManager.@NotNull PacketContext context) {
        return () -> {
            if (Minecraft.getInstance().screen instanceof SpaceRaceScreen screen) {
                screen.acceptServerStatistics(this);
            }
        };
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record Entry(ResourceLocation stat, long total) {
    }
}
