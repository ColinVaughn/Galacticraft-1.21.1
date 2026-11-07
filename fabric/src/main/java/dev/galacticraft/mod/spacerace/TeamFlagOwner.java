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

package dev.galacticraft.mod.spacerace;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.Util;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * Team identity retained when a flag is crafted or planted. The captured name lets clients name
 * the flag without a server lookup.
 *
 * @param id team ID; currently the player's UUID
 * @param name team name when claimed, or empty while unclaimed
 */
public record TeamFlagOwner(UUID id, String name) {
    public static final TeamFlagOwner UNCLAIMED = new TeamFlagOwner(Util.NIL_UUID, "");

    public static final Codec<TeamFlagOwner> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(TeamFlagOwner::id),
            Codec.STRING.optionalFieldOf("name", "").forGetter(TeamFlagOwner::name)
    ).apply(instance, TeamFlagOwner::new));
    public static final StreamCodec<ByteBuf, TeamFlagOwner> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, TeamFlagOwner::id,
            ByteBufCodecs.STRING_UTF8, TeamFlagOwner::name,
            TeamFlagOwner::new
    );

    /** Returns the player's team identity. */
    public static TeamFlagOwner of(ServerPlayer player) {
        return new TeamFlagOwner(player.getUUID(), SpaceRaceTeams.get(player.server).displayName(player.server, player.getUUID()));
    }

    public boolean isClaimed() {
        return !this.id.equals(Util.NIL_UUID);
    }
}
