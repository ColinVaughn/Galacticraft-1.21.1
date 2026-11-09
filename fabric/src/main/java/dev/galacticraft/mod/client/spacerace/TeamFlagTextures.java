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

package dev.galacticraft.mod.client.spacerace;

import com.mojang.blaze3d.platform.NativeImage;
import dev.architectury.networking.NetworkManager;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.network.c2s.TeamFlagRequestPayload;
import dev.galacticraft.mod.network.s2c.TeamFlagPayload;
import dev.galacticraft.mod.spacerace.TeamFlagImage;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Caches team flag textures, requesting missing designs from the server and replacing them on
 * team updates. Missing designs render as plain white cloth.
 */
public final class TeamFlagTextures {
    /** How long to wait for an answer before asking again. */
    private static final long RETRY_MILLIS = 10_000L;

    private static final Map<UUID, Team> TEAMS = new HashMap<>();
    private static final Map<UUID, Long> ASKED = new HashMap<>();
    private static @Nullable ResourceLocation blank;
    private static int uploads;

    private record Team(String name, @Nullable ResourceLocation texture) {
    }

    private TeamFlagTextures() {
    }

    /** Returns a team's flag texture, requesting a missing design once. Uses plain cloth until it arrives. */
    public static ResourceLocation texture(@Nullable UUID team) {
        if (team == null) return blank();
        Team known = TEAMS.get(team);
        if (known == null) {
            ask(team);
            return blank();
        }
        return known.texture() != null ? known.texture() : blank();
    }

    /** Returns whether the client has this team's flag design. */
    public static boolean hasDesign(UUID team) {
        Team known = TEAMS.get(team);
        return known != null && known.texture() != null;
    }

    /** Takes a team's flag from the server, replacing any older design. */
    public static void accept(TeamFlagPayload payload) {
        Team old = TEAMS.remove(payload.team());
        if (old != null && old.texture() != null) {
            Minecraft.getInstance().getTextureManager().release(old.texture());
        }
        ResourceLocation texture = payload.flag()
                .map(rgb -> upload(Constant.id("team_flag/" + payload.team() + "/" + uploads++), TeamFlagImage.toArgb(rgb)))
                .orElse(null);
        TEAMS.put(payload.team(), new Team(payload.name(), texture));
        ASKED.remove(payload.team());
    }

    /** Forgets every team, on leaving a world: the next one may have its own designs. */
    public static void clear() {
        for (Team team : TEAMS.values()) {
            if (team.texture() != null) Minecraft.getInstance().getTextureManager().release(team.texture());
        }
        TEAMS.clear();
        ASKED.clear();
    }

    private static void ask(UUID team) {
        if (Minecraft.getInstance().getConnection() == null) return;
        long now = Util.getMillis();
        Long asked = ASKED.get(team);
        if (asked != null && now - asked < RETRY_MILLIS) return;
        ASKED.put(team, now);
        NetworkManager.sendToServer(new TeamFlagRequestPayload(team));
    }

    private static ResourceLocation blank() {
        if (blank == null) {
            int[] white = new int[TeamFlagImage.WIDTH * TeamFlagImage.HEIGHT];
            Arrays.fill(white, 0xFFFFFFFF);
            blank = upload(Constant.id("team_flag/blank"), white);
        }
        return blank;
    }

    private static ResourceLocation upload(ResourceLocation id, int[] design) {
        int[] layout = TeamFlagLayout.layout(design);
        NativeImage image = new NativeImage(TeamFlagLayout.SIZE, TeamFlagLayout.SIZE, false);
        for (int y = 0; y < TeamFlagLayout.SIZE; y++) {
            for (int x = 0; x < TeamFlagLayout.SIZE; x++) {
                image.setPixelRGBA(x, y, TeamFlagImage.swapRedBlue(layout[y * TeamFlagLayout.SIZE + x]));
            }
        }
        Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(image));
        return id;
    }
}
