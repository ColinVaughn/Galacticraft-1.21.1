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

package dev.galacticraft.mod.machine.workbench;

import dev.architectury.networking.NetworkManager;
import dev.galacticraft.api.rocket.part.RocketPart;
import dev.galacticraft.impl.internal.accessor.AdvancementRewardsAccessor;
import dev.galacticraft.impl.network.s2c.ResearchSourcesPayload;
import dev.galacticraft.mod.util.Translations;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Says how each piece of rocket-part research is unlocked, so a locked part can tell the player
 * where to get it instead of leaving them to guess between schematics and advancements.
 */
public final class ResearchSources {
    /** The client's copy, last sent by the server. Empty until a workbench has been opened. */
    private static Map<ResourceLocation, Component> synced = Map.of();

    private ResearchSources() {
    }

    /** Maps every research id that something unlocks to a line telling the player how to get it. */
    public static Map<ResourceLocation, Component> describe(MinecraftServer server) {
        Map<ResourceLocation, Component> sources = new HashMap<>();

        // Prefer the schematic source over the advancement fired when it is spent.
        for (Item schematic : WorkbenchPages.tierSchematics()) {
            Component how = Component.translatable(Translations.RocketWorkbench.UNLOCK_BY_SCHEMATIC, schematic.getDescription());
            for (ResourceKey<?> part : WorkbenchPages.rocketPartsUnlockedBy(schematic)) {
                sources.putIfAbsent(RocketPart.researchId(part), how);
            }
        }

        for (AdvancementHolder holder : server.getAdvancements().getAllAdvancements()) {
            ResourceLocation[] rewards = ((AdvancementRewardsAccessor) (Object) holder.value().rewards()).getRocketPartRecipeRewards();
            Optional<DisplayInfo> display = holder.value().display();
            // An advancement with no display has no name to point the player at.
            if (rewards == null || display.isEmpty()) continue;

            Component how = Component.translatable(Translations.RocketWorkbench.UNLOCK_BY_ADVANCEMENT,
                    display.get().getTitle(), display.get().getDescription());
            for (ResourceLocation research : rewards) {
                sources.putIfAbsent(research, how);
            }
        }
        sources.putAll(dev.galacticraft.mod.data.ResearchSourceData.INSTANCE.sources());
        return sources;
    }

    public static void sync(ServerPlayer player) {
        NetworkManager.sendToPlayer(player, new ResearchSourcesPayload(describe(player.server)));
    }

    public static void accept(Map<ResourceLocation, Component> sources) {
        synced = Map.copyOf(sources);
    }

    /** How the client was told {@code research} is unlocked, or null if nothing it knows of unlocks it. */
    public static @Nullable Component source(ResourceLocation research) {
        return synced.get(research);
    }
}
