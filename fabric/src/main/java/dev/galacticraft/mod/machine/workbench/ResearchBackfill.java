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

package dev.galacticraft.mod.machine.workbench;

import dev.galacticraft.api.accessor.ResearchAccessor;
import dev.galacticraft.api.accessor.ServerResearchAccessor;
import dev.galacticraft.api.component.GCDataComponents;
import dev.galacticraft.api.rocket.RocketData;
import dev.galacticraft.api.rocket.part.RocketPart;
import dev.galacticraft.impl.internal.accessor.AdvancementRewardsAccessor;
import dev.galacticraft.mod.recipe.WorkbenchRecipe;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.EitherHolder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Grants rocket workbench research a player is owed but never received. */
public final class ResearchBackfill {
    private ResearchBackfill() {
    }

    /**
     * Backfills rocket-part research for advancements completed before their rewards were added.
     * Granting research is idempotent.
     *
     * @return the recipes that were granted
     */
    public static List<ResourceLocation> backfillFromAdvancements(ServerPlayer player) {
        PlayerAdvancements progress = player.getAdvancements();
        List<ResourceLocation> granted = new ArrayList<>();

        for (AdvancementHolder holder : player.server.getAdvancements().getAllAdvancements()) {
            if (!progress.getOrStartProgress(holder).isDone()) {
                continue;
            }

            ResourceLocation[] rewards = ((AdvancementRewardsAccessor) (Object) holder.value().rewards()).getRocketPartRecipeRewards();
            if (rewards != null) {
                Collections.addAll(granted, rewards);
            }
        }

        // Old schematic pickups granted parts without pages; backfill the pages needed to build them.
        for (ResourceLocation page : pagesImpliedBy(player, granted)) {
            if (!((ResearchAccessor) player).galacticraft$isUnlocked(page)) {
                granted.add(page);
            }
        }

        backfillStockSchematics(player, granted);

        if (!granted.isEmpty()) {
            ((ServerResearchAccessor) player).galacticraft$unlockRocketPartRecipes(granted.toArray(new ResourceLocation[0]));
        }

        return granted;
    }

    private static void backfillStockSchematics(ServerPlayer player, List<ResourceLocation> granted) {
        // Stock tier pages no longer have recipes; retain saved page unlocks and older part research.
        if (player.isCreative()) return;
        ResearchAccessor research = (ResearchAccessor) player;
        for (Item schematic : WorkbenchPages.tierSchematics()) {
            ResourceLocation unlock = WorkbenchPages.unlockId(BuiltInRegistries.ITEM.getKey(schematic));
            List<ResourceLocation> parts = WorkbenchPages.rocketPartsUnlockedBy(schematic).stream().map(RocketPart::researchId).toList();
            if (research.galacticraft$isUnlocked(unlock)) {
                for (ResourceLocation part : parts) {
                    if (!research.galacticraft$isUnlocked(part) && !granted.contains(part)) granted.add(part);
                }
            } else if (parts.stream().allMatch(part -> granted.contains(part) || research.galacticraft$isUnlocked(part))) {
                granted.add(unlock);
            }
        }
    }

    /**
     * The workbench pages whose parts {@code granted} already covers. A page counts as implied when
     * the player holds research for every structural part its recipe produces.
     */
    private static List<ResourceLocation> pagesImpliedBy(ServerPlayer player, List<ResourceLocation> granted) {
        Set<ResourceLocation> held = new HashSet<>(granted);
        List<ResourceLocation> pages = new ArrayList<>();

        for (RecipeHolder<? extends WorkbenchRecipe> holder : WorkbenchPages.recipes(player.level())) {
            Optional<Item> schematic = holder.value().schematic();
            if (schematic.isEmpty()) continue;

            RocketData data = holder.value().getResultItem(player.registryAccess()).get(GCDataComponents.ROCKET_DATA);
            if (data == null) continue;

            if (allPartsHeld(player, held, data)) {
                pages.add(WorkbenchPages.unlockId(BuiltInRegistries.ITEM.getKey(schematic.get())));
            }
        }
        return pages;
    }

    private static boolean allPartsHeld(ServerPlayer player, Set<ResourceLocation> held, RocketData data) {
        HolderLookup.Provider lookup = player.registryAccess();
        List<Optional<? extends EitherHolder<? extends RocketPart<?, ?>>>> parts =
                List.of(data.cone(), data.body(), data.fin(), data.engine());

        boolean any = false;
        for (Optional<? extends EitherHolder<? extends RocketPart<?, ?>>> part : parts) {
            if (part.isEmpty()) continue;

            ResourceLocation recipe = part.get().unwrap(lookup)
                    .flatMap(Holder::unwrapKey)
                    .map(RocketPart::researchId)
                    .orElse(null);
            if (recipe == null) continue;

            any = true;
            if (!held.contains(recipe) && !((ResearchAccessor) player).galacticraft$isUnlocked(recipe)) {
                return false;
            }
        }
        return any;
    }
}
