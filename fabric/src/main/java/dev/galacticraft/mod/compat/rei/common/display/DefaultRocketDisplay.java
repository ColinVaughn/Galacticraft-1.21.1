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

package dev.galacticraft.mod.compat.rei.common.display;

import dev.galacticraft.mod.compat.rei.common.GalacticraftREIServerPlugin;
import dev.galacticraft.mod.machine.workbench.WorkbenchLayout;
import dev.galacticraft.mod.machine.workbench.WorkbenchSlot;
import dev.galacticraft.mod.recipe.RocketRecipe;
import dev.galacticraft.mod.recipe.SchematicCraftingRecipe;
import dev.galacticraft.mod.recipe.WorkbenchRecipe;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.display.basic.BasicDisplay;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.util.EntryIngredients;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class DefaultRocketDisplay extends BasicDisplay {
    public static final BasicDisplay.Serializer<DefaultRocketDisplay> SERIALIZER = BasicDisplay.Serializer.of(
            (inputs, outputs, id, tag) -> {
                return new DefaultRocketDisplay(
                        inputs,
                        outputs,
                        id,
                        tag.getInt("BodyHeight"),
                        tag.getBoolean("HasBoosters"),
                        findLayout(tag.getString("Layout"))
                );
            },
            (display, tag) -> {
                tag.putInt("BodyHeight", display.bodyHeight);
                tag.putBoolean("HasBoosters", display.hasBoosters);
                if (display.layout != null) {
                    tag.putString("Layout", display.layout.getSerializedName());
                }
            }
    );

    public final int bodyHeight;
    public final boolean hasBoosters;
    private final @Nullable WorkbenchLayout layout;

    protected DefaultRocketDisplay(List<EntryIngredient> inputs, List<EntryIngredient> outputs, Optional<ResourceLocation> location,
                                   int bodyHeight, boolean hasBoosters, @Nullable WorkbenchLayout layout) {
        super(inputs, outputs, location);
        this.bodyHeight = bodyHeight;
        this.hasBoosters = hasBoosters;
        this.layout = layout;
    }

    public DefaultRocketDisplay(@Nullable RecipeHolder<? extends WorkbenchRecipe> recipe) {
        super(
                getInputs(recipe),
                recipe == null
                        ? Collections.emptyList()
                        : Collections.singletonList(EntryIngredients.of(recipe.value().getResultItem(registryAccess()))),
                recipe == null ? Optional.empty() : Optional.of(recipe.id())
        );
        if (recipe == null) {
            this.bodyHeight = 0;
            this.hasBoosters = false;
            this.layout = null;
        } else if (recipe.value() instanceof RocketRecipe rocketRecipe) {
            this.bodyHeight = rocketRecipe.bodyHeight();
            this.hasBoosters = !rocketRecipe.boosters().isEmpty();
            this.layout = null;
        } else if (recipe.value() instanceof SchematicCraftingRecipe schematicRecipe) {
            this.bodyHeight = 0;
            this.hasBoosters = false;
            this.layout = schematicRecipe.layout();
        } else {
            throw new IllegalArgumentException("Unsupported workbench recipe " + recipe.value().getClass().getName());
        }
    }

    @Override
    public CategoryIdentifier<? extends DefaultRocketDisplay> getCategoryIdentifier() {
        return GalacticraftREIServerPlugin.ROCKET;
    }

    public @Nullable WorkbenchLayout layout() {
        return this.layout;
    }

    private static List<EntryIngredient> getInputs(@Nullable RecipeHolder<? extends WorkbenchRecipe> recipe) {
        if (recipe == null) return Collections.emptyList();
        List<EntryIngredient> list = new ArrayList<>();
        for (WorkbenchSlot slot : recipe.value().ingredientSlots()) {
            list.add(EntryIngredients.ofIngredient(slot.ingredient()));
        }
        return list;
    }

    private static @Nullable WorkbenchLayout findLayout(String name) {
        for (WorkbenchLayout layout : WorkbenchLayout.values()) {
            if (layout.getSerializedName().equals(name)) {
                return layout;
            }
        }
        return null;
    }
}
