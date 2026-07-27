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

package dev.galacticraft.mod.recipe;

import com.mojang.serialization.DataResult;
import dev.galacticraft.mod.machine.workbench.WorkbenchLayout;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeInput;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Vehicle pages of the workbench are positional like legacy's NASA workbench recipes: the parts have
 * to be in the right wells, not merely present.
 */
class SchematicCraftingRecipeTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void matchesWhenEverySlotHoldsItsIngredient() {
        assertTrue(buggy(ironBuggy()).matches(input(ironBuggy()), null));
    }

    @Test
    void doesNotMatchWhenTwoPartsAreSwapped() {
        List<Item> parts = ironBuggy();
        parts.set(0, Items.GOLD_INGOT);
        parts.set(15, Items.IRON_INGOT);

        assertFalse(buggy(ironBuggy()).matches(input(parts), null));
    }

    @Test
    void doesNotMatchWhileASlotIsStillEmpty() {
        List<Item> parts = ironBuggy();
        parts.set(7, Items.AIR);

        assertFalse(buggy(ironBuggy()).matches(input(parts), null));
    }

    @Test
    void doesNotMatchAnInputSizedForADifferentPage() {
        assertFalse(buggy(ironBuggy()).matches(input(List.of(Items.IRON_INGOT, Items.IRON_INGOT)), null));
    }

    /**
     * An ingredient list that disagrees with its layout leaves either parts of the recipe
     * unreachable or wells that craft nothing. Datapacks can get this wrong, so loading rejects it
     * rather than shipping a page that can never be completed.
     */
    @Test
    void loadingRejectsAnIngredientListThatDoesNotFillItsLayout() {
        DataResult<SchematicCraftingRecipe> result = SchematicCraftingRecipe.create(
                "", new ItemStack(Items.MINECART), WorkbenchLayout.BUGGY, Optional.empty(), 0,
                List.of(Ingredient.of(Items.IRON_INGOT))
        );

        assertTrue(result.error().isPresent(), "a one-ingredient buggy recipe should not load");
        String message = result.error().orElseThrow().message();
        assertTrue(message.contains("buggy") && message.contains("16"), () -> "unhelpful message: " + message);
    }

    @Test
    void loadingAcceptsAnIngredientListThatFillsItsLayout() {
        DataResult<SchematicCraftingRecipe> result = SchematicCraftingRecipe.create(
                "", new ItemStack(Items.MINECART), WorkbenchLayout.BUGGY, Optional.empty(), 0, ingredients(ironBuggy())
        );

        assertTrue(result.result().isPresent(), () -> "should load: " + result.error().map(Object::toString).orElse(""));
    }

    @Test
    void reportsTheSchematicAndSortOrderThatPlaceItsPage() {
        SchematicCraftingRecipe recipe = new SchematicCraftingRecipe(
                "", new ItemStack(Items.MINECART), WorkbenchLayout.ASTRO_MINER, Optional.of(Items.PAPER), 7,
                ingredients(filled(WorkbenchLayout.ASTRO_MINER.ingredientSlots().size(), Items.IRON_INGOT))
        );

        assertEquals(Optional.of(Items.PAPER), recipe.schematic());
        assertEquals(7, recipe.sortOrder());
        assertEquals(WorkbenchLayout.ASTRO_MINER.ingredientSlots().size(), recipe.getIngredients().size());
    }

    private static SchematicCraftingRecipe buggy(List<Item> parts) {
        return new SchematicCraftingRecipe("", new ItemStack(Items.MINECART), WorkbenchLayout.BUGGY, Optional.empty(), 0, ingredients(parts));
    }

    /** Twelve plating slots then four wheels, so a swapped part is a distinguishable failure. */
    private static List<Item> ironBuggy() {
        List<Item> parts = new ArrayList<>(filled(12, Items.IRON_INGOT));
        parts.addAll(filled(4, Items.GOLD_INGOT));
        return parts;
    }

    private static List<Item> filled(int count, Item item) {
        List<Item> items = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            items.add(item);
        }
        return items;
    }

    private static List<Ingredient> ingredients(List<Item> items) {
        return items.stream().map(Ingredient::of).toList();
    }

    private static RecipeInput input(List<Item> items) {
        List<ItemStack> stacks = items.stream().map(item -> item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item)).toList();
        return new RecipeInput() {
            @Override
            public ItemStack getItem(int slot) {
                return stacks.get(slot);
            }

            @Override
            public int size() {
                return stacks.size();
            }
        };
    }
}
