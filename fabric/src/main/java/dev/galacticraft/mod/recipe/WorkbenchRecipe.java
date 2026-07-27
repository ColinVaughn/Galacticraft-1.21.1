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

import dev.galacticraft.mod.machine.workbench.WorkbenchPageDisplay;
import dev.galacticraft.mod.machine.workbench.WorkbenchSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;

import java.util.List;
import java.util.Optional;

/**
 * A recipe that occupies one page of the rocket workbench flip-book.
 *
 * <p>Legacy Galacticraft expressed this as {@code ISchematicPage}, pairing a schematic item with the
 * GUI and container it unlocked. Here the recipe carries that pairing, so a datapack can add a page
 * without any code.
 */
public interface WorkbenchRecipe extends Recipe<RecipeInput> {
    /**
     * The schematic item that must be consumed in the workbench before this page appears, or empty
     * for a page that is always available - legacy's tier-1 rocket being the only such page.
     */
    Optional<Item> schematic();

    /** Where this page sits in the book; lower comes first. */
    int sortOrder();

    /** The page's ingredient wells, in the order their container slots are laid out. */
    List<WorkbenchSlot> ingredientSlots();

    /** Background, chrome and player-inventory geometry for the page. */
    WorkbenchPageDisplay display();
}
