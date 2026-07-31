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

package dev.galacticraft.mod.compat.emi;

import dev.emi.emi.api.recipe.BasicEmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import dev.galacticraft.mod.machine.workbench.WorkbenchLayout;
import dev.galacticraft.mod.machine.workbench.WorkbenchSlot;
import dev.galacticraft.mod.recipe.SchematicCraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.List;

import static dev.galacticraft.mod.Constant.RocketWorkbench.RECIPE_VIEWER_HEIGHT;
import static dev.galacticraft.mod.Constant.RocketWorkbench.SCHEMATIC_RECIPE_VIEWER_WIDTH;
import static dev.galacticraft.mod.Constant.RocketWorkbench.SCHEMATIC_RECIPE_VIEWER_X;

/**
 * EMI view of a legacy schematic page such as the Moon Buggy. The legacy textures already contain
 * their slot and output frames, so the EMI widgets only provide ingredients, hover handling and
 * recipe-tree context over the painted wells.
 */
public class SchematicEmiRecipe extends BasicEmiRecipe {
    private final WorkbenchLayout layout;

    public SchematicEmiRecipe(RecipeHolder<SchematicCraftingRecipe> holder) {
        super(GalacticraftEmiPlugin.ROCKET, holder.id(), SCHEMATIC_RECIPE_VIEWER_WIDTH, RECIPE_VIEWER_HEIGHT);
        SchematicCraftingRecipe recipe = holder.value();
        for (WorkbenchSlot slot : recipe.ingredientSlots()) {
            this.inputs.add(EmiIngredient.of(slot.ingredient()));
        }
        this.outputs.add(EmiStack.of(recipe.result()));
        this.layout = recipe.layout();
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        widgets.addTexture(
                this.layout.texture(),
                0,
                0,
                SCHEMATIC_RECIPE_VIEWER_WIDTH,
                Math.min(RECIPE_VIEWER_HEIGHT, this.layout.playerInventoryY()),
                SCHEMATIC_RECIPE_VIEWER_X,
                this.layout.textureV()
        );

        List<WorkbenchLayout.Position> positions = this.layout.ingredientSlots();
        for (int i = 0; i < positions.size() && i < this.inputs.size(); i++) {
            WorkbenchLayout.Position position = positions.get(i);
            widgets.addSlot(this.inputs.get(i), position.x() - SCHEMATIC_RECIPE_VIEWER_X - 1, position.y() - 1)
                    .drawBack(false);
        }

        WorkbenchLayout.Position result = this.layout.resultSlot();
        widgets.addSlot(this.outputs.getFirst(), result.x() - SCHEMATIC_RECIPE_VIEWER_X - 1, result.y() - 1)
                .drawBack(false)
                .recipeContext(this);
    }
}
