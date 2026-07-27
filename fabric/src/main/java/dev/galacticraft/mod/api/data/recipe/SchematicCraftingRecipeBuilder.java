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

package dev.galacticraft.mod.api.data.recipe;

import dev.galacticraft.mod.machine.workbench.WorkbenchLayout;
import dev.galacticraft.mod.recipe.SchematicCraftingRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.ItemLike;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Builds a positional workbench page for a vehicle, filling the wells of a {@link WorkbenchLayout}
 * in order. Legacy Galacticraft assembled the same thing as a {@code HashMap<Integer, ItemStack>}.
 */
public class SchematicCraftingRecipeBuilder extends GCRecipeBuilder<SchematicCraftingRecipeBuilder> {
    private final WorkbenchLayout layout;
    private final List<Ingredient> ingredients = new ArrayList<>();
    private Optional<Item> schematic = Optional.empty();
    private int sortOrder;

    protected SchematicCraftingRecipeBuilder(WorkbenchLayout layout, ItemLike result, int count) {
        super("", null, result, count);
        this.layout = layout;
    }

    public static SchematicCraftingRecipeBuilder create(WorkbenchLayout layout, ItemLike result) {
        return new SchematicCraftingRecipeBuilder(layout, result, 1);
    }

    /** Fills the next well. */
    public SchematicCraftingRecipeBuilder slot(ItemLike item) {
        this.ingredients.add(Ingredient.of(item));
        return this;
    }

    public SchematicCraftingRecipeBuilder slot(TagKey<Item> tag) {
        this.ingredients.add(Ingredient.of(tag));
        return this;
    }

    /** Fills the next {@code count} wells with the same part. */
    public SchematicCraftingRecipeBuilder slots(int count, ItemLike item) {
        for (int i = 0; i < count; i++) {
            this.slot(item);
        }
        return this;
    }

    /** Fills the next wells in order, one per item. */
    public SchematicCraftingRecipeBuilder slots(ItemLike... items) {
        Arrays.stream(items).forEach(this::slot);
        return this;
    }

    public SchematicCraftingRecipeBuilder schematic(ItemLike schematic) {
        this.schematic = Optional.of(schematic.asItem());
        return this;
    }

    public SchematicCraftingRecipeBuilder sortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
        return this;
    }

    @Override
    public Recipe<?> createRecipe(ResourceLocation id) {
        if (this.criteria.isEmpty()) {
            throw new IllegalStateException("No way of obtaining recipe " + id);
        }

        return SchematicCraftingRecipe.create(this.group, new ItemStack(this.result, this.count), this.layout, this.schematic, this.sortOrder, this.ingredients)
                .getOrThrow(error -> new IllegalStateException("Recipe " + id + " does not fit its layout: " + error));
    }
}
