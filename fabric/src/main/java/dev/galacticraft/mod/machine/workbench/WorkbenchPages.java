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
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.recipe.GCRecipes;
import dev.galacticraft.mod.recipe.WorkbenchRecipe;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Assembles the ordered list of workbench pages a player can flip through.
 *
 * <p>Legacy Galacticraft kept this list in {@code GCPlayerStats.unlockedSchematics}; here it is
 * derived on demand from the loaded recipes and the player's unlocks, so both sides can work it out
 * without a dedicated sync packet.
 */
public final class WorkbenchPages {
    /** The terminal "add new schematic" page, which is the only way to unlock any of the others. */
    public static final ResourceLocation ADD_SCHEMATIC_ID = Constant.id("add_schematic");
    public static final WorkbenchPage ADD_SCHEMATIC = new WorkbenchPage(ADD_SCHEMATIC_ID, Integer.MAX_VALUE, Optional.empty());

    private static final Comparator<WorkbenchPage> ORDER = Comparator
            .comparingInt(WorkbenchPage::sortOrder)
            .thenComparing(page -> page.id().toString());

    private WorkbenchPages() {
    }

    /**
     * The research id recording that a player has unlocked {@code schematicItem}.
     *
     * <p>This is persisted in player NBT, so the shape is a compatibility promise. The item's own
     * namespace is kept so that two addons shipping a {@code rocket_schematic} do not collide.
     */
    public static ResourceLocation unlockId(ResourceLocation schematicItem) {
        return schematicItem.withPrefix("schematic/");
    }

    /** Every workbench recipe the datapacks loaded, rocket pages and vehicle pages alike. */
    public static List<RecipeHolder<? extends WorkbenchRecipe>> recipes(Level level) {
        List<RecipeHolder<? extends WorkbenchRecipe>> recipes = new ArrayList<>();
        recipes.addAll(level.getRecipeManager().getAllRecipesFor(GCRecipes.ROCKET_TYPE));
        recipes.addAll(level.getRecipeManager().getAllRecipesFor(GCRecipes.SCHEMATIC_CRAFTING_TYPE));
        return recipes;
    }

    public static WorkbenchPage page(RecipeHolder<? extends WorkbenchRecipe> holder) {
        return new WorkbenchPage(
                holder.id(),
                holder.value().sortOrder(),
                holder.value().schematic().map(BuiltInRegistries.ITEM::getKey)
        );
    }

    /** The pages {@code player} may flip through, in order. */
    public static List<WorkbenchPage> visible(Level level, Player player) {
        List<WorkbenchPage> candidates = new ArrayList<>();
        for (RecipeHolder<? extends WorkbenchRecipe> holder : recipes(level)) {
            candidates.add(page(holder));
        }
        ResearchAccessor research = (ResearchAccessor) player;
        return order(candidates, research::galacticraft$isUnlocked);
    }

    public static @Nullable RecipeHolder<? extends WorkbenchRecipe> recipe(Level level, ResourceLocation pageId) {
        for (RecipeHolder<? extends WorkbenchRecipe> holder : recipes(level)) {
            if (holder.id().equals(pageId)) return holder;
        }
        return null;
    }

    /** The recipe whose page {@code schematicItem} unlocks, or null if no page claims it. */
    public static @Nullable RecipeHolder<? extends WorkbenchRecipe> pageUnlockedBy(Level level, Item schematicItem) {
        for (RecipeHolder<? extends WorkbenchRecipe> holder : recipes(level)) {
            if (holder.value().schematic().filter(item -> item == schematicItem).isPresent()) return holder;
        }
        return null;
    }

    /**
     * @param candidates every build page the loaded recipes offer, in any order
     * @param unlocked   whether the player holds a given {@linkplain #unlockId unlock id}
     * @return the pages to show, in flip order, always ending with {@link #ADD_SCHEMATIC}
     */
    public static List<WorkbenchPage> order(List<WorkbenchPage> candidates, Predicate<ResourceLocation> unlocked) {
        List<WorkbenchPage> pages = new ArrayList<>(candidates.size() + 1);
        for (WorkbenchPage page : candidates) {
            if (page.schematic().isEmpty() || unlocked.test(unlockId(page.schematic().get()))) {
                pages.add(page);
            }
        }
        pages.sort(ORDER);
        pages.add(ADD_SCHEMATIC);
        return List.copyOf(pages);
    }
}
