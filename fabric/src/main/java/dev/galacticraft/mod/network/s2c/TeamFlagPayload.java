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
import dev.galacticraft.mod.client.spacerace.TeamFlagTextures;
import dev.galacticraft.mod.spacerace.TeamFlagImage;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.UUID;

/**
 * A space race team's name and flag, sent to a player who asked for it to draw a planted flag, and to everyone
 * when a team changes either.
 *
 * @param flag the team's flag in {@link TeamFlagImage} form, or empty if it has not designed one
 */
public record TeamFlagPayload(UUID team, String name, Optional<byte[]> flag) implements S2CPayload {
    public static final StreamCodec<ByteBuf, TeamFlagPayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, TeamFlagPayload::team,
            ByteBufCodecs.stringUtf8(64), TeamFlagPayload::name,
            ByteBufCodecs.optional(ByteBufCodecs.byteArray(TeamFlagImage.BYTES)), TeamFlagPayload::flag,
            TeamFlagPayload::new
    );
    public static final ResourceLocation ID = Constant.id("team_flag");
    public static final Type<TeamFlagPayload> TYPE = new Type<>(ID);

    public TeamFlagPayload {
        if (flag.isPresent() && flag.get().length != TeamFlagImage.BYTES) {
            throw new IllegalArgumentException("A team flag is " + TeamFlagImage.BYTES + " bytes, not " + flag.get().length);
        }
    }

    @Override
    public Runnable handle(NetworkManager.@NotNull PacketContext context) {
        return () -> TeamFlagTextures.accept(this);
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
