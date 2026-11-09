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

import com.mojang.authlib.GameProfile;
import dev.architectury.networking.NetworkManager;
import dev.galacticraft.mod.network.s2c.TeamFlagPayload;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.util.StringUtil;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The space race teams this world knows: each team's name and the flag it designed in the space race menu, kept
 * with the world so planted team flags show it to every player. Until teams can take on members, each player is
 * a team of their own, under their own id.
 */
public final class SpaceRaceTeams extends SavedData {
    private static final String ID = "galacticraft_space_race_teams";
    public static final int MAX_NAME_LENGTH = 32;
    /** Ticks a player waits between changes, since every change is shared with everyone online. */
    private static final int CHANGE_COOLDOWN = 20;
    /** How many teams' flags one player may ask for each second. */
    private static final int REQUESTS_PER_SECOND = 64;

    private final Map<UUID, Team> teams = new HashMap<>();
    private final Map<UUID, Integer> lastFlagChange = new HashMap<>();
    private final Map<UUID, Integer> lastNameChange = new HashMap<>();
    private final Map<UUID, int[]> requestWindows = new HashMap<>();

    public static SpaceRaceTeams get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(SpaceRaceTeams::new, SpaceRaceTeams::load, null), ID);
    }

    /** Returns a copy of the team's flag, or {@code null} if none is saved. */
    public @Nullable byte[] flag(UUID team) {
        Team known = this.teams.get(team);
        return known == null || known.flag == null ? null : known.flag.clone();
    }

    /** Returns the chosen team name, or an empty string if unset. */
    public String name(UUID team) {
        Team known = this.teams.get(team);
        return known == null ? "" : known.name;
    }

    /** Returns the chosen team name, falling back to its player's name. */
    public String displayName(MinecraftServer server, UUID team) {
        String chosen = this.name(team);
        if (!chosen.isBlank()) return chosen;
        ServerPlayer online = server.getPlayerList().getPlayer(team);
        if (online != null) return online.getGameProfile().getName();
        GameProfileCache profiles = server.getProfileCache();
        return profiles == null ? "" : profiles.get(team).map(GameProfile::getName).orElse("");
    }

    /**
     * Takes the flag a player designed in the space race menu. A new design is shared with everyone online, so their
     * planted flags change with it.
     *
     * @return whether the design is now the team's; false if it came too soon after the last one
     */
    public boolean acceptFlag(ServerPlayer player, byte[] rgb) {
        if (rgb.length != TeamFlagImage.BYTES) return false;
        Team team = this.teams.computeIfAbsent(player.getUUID(), id -> new Team());
        if (Arrays.equals(team.flag, rgb)) return true;
        if (!this.mayChange(this.lastFlagChange, player)) return false;
        team.flag = rgb.clone();
        this.setDirty();
        this.share(player.server, player.getUUID());
        return true;
    }

    /** Takes the team name a player typed in the space race menu, cleaned of formatting and cut to length. */
    public void acceptName(ServerPlayer player, String name) {
        String clean = StringUtil.filterText(name).strip();
        if (clean.length() > MAX_NAME_LENGTH) clean = clean.substring(0, MAX_NAME_LENGTH);
        Team team = this.teams.computeIfAbsent(player.getUUID(), id -> new Team());
        if (team.name.equals(clean) || !this.mayChange(this.lastNameChange, player)) return;
        team.name = clean;
        this.setDirty();
        this.share(player.server, player.getUUID());
    }

    /** Answers a player asking for a team's flag, within a per-second allowance. */
    public void answer(ServerPlayer player, UUID team) {
        int now = player.server.getTickCount();
        int[] window = this.requestWindows.computeIfAbsent(player.getUUID(), id -> new int[]{now, 0});
        if (now - window[0] >= 20) {
            window[0] = now;
            window[1] = 0;
        }
        if (++window[1] > REQUESTS_PER_SECOND) return;
        NetworkManager.sendToPlayer(player, this.payload(player.server, team));
    }

    public TeamFlagPayload payload(MinecraftServer server, UUID team) {
        return new TeamFlagPayload(team, this.displayName(server, team), Optional.ofNullable(this.flag(team)));
    }

    private boolean mayChange(Map<UUID, Integer> lastChange, ServerPlayer player) {
        int now = player.server.getTickCount();
        Integer last = lastChange.get(player.getUUID());
        if (last != null && now - last < CHANGE_COOLDOWN) return false;
        lastChange.put(player.getUUID(), now);
        return true;
    }

    private void share(MinecraftServer server, UUID team) {
        NetworkManager.sendToPlayers(server.getPlayerList().getPlayers(), this.payload(server, team));
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag all = new CompoundTag();
        this.teams.forEach((id, team) -> {
            CompoundTag entry = new CompoundTag();
            if (!team.name.isEmpty()) entry.putString("name", team.name);
            if (team.flag != null) entry.putByteArray("flag", team.flag);
            all.put(id.toString(), entry);
        });
        tag.put("teams", all);
        return tag;
    }

    public static SpaceRaceTeams load(CompoundTag tag, HolderLookup.Provider registries) {
        SpaceRaceTeams loaded = new SpaceRaceTeams();
        CompoundTag all = tag.getCompound("teams");
        for (String key : all.getAllKeys()) {
            UUID id;
            try {
                id = UUID.fromString(key);
            } catch (IllegalArgumentException e) {
                continue;
            }
            CompoundTag entry = all.getCompound(key);
            Team team = new Team();
            team.name = entry.getString("name");
            byte[] flag = entry.getByteArray("flag");
            team.flag = flag.length == TeamFlagImage.BYTES ? flag : null;
            loaded.teams.put(id, team);
        }
        return loaded;
    }

    private static final class Team {
        private String name = "";
        private @Nullable byte[] flag;
    }
}
