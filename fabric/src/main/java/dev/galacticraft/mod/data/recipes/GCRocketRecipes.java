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

package dev.galacticraft.mod.data.recipes;

import dev.galacticraft.api.rocket.RocketPrefabs;
import dev.galacticraft.mod.api.data.recipe.GCShapedRecipeBuilder;
import dev.galacticraft.mod.api.data.recipe.RocketRecipeBuilder;
import dev.galacticraft.mod.api.data.recipe.SchematicCraftingRecipeBuilder;
import dev.galacticraft.mod.machine.workbench.WorkbenchLayout;
import dev.galacticraft.mod.content.GCBlocks;
import dev.galacticraft.mod.content.item.GCItems;
import dev.galacticraft.mod.tag.GCItemTags;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricRecipeProvider;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalItemTags;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

public class GCRocketRecipes extends FabricRecipeProvider {
    public GCRocketRecipes(FabricDataOutput output, CompletableFuture<HolderLookup.Provider> lookup) {
        super(output, lookup);
    }

    @Override
    public void buildRecipes(RecipeOutput output) {
        GCShapedRecipeBuilder.crafting(RecipeCategory.DECORATIONS, GCBlocks.ROCKET_WORKBENCH)
                .define('S', GCItems.COMPRESSED_STEEL)
                .define('C', ConventionalItemTags.PLAYER_WORKSTATIONS_CRAFTING_TABLES)
                .define('L', Items.LEVER)
                .define('W', GCItems.ADVANCED_WAFER)
                .define('R', Items.REDSTONE_TORCH)
                .pattern("SCS")
                .pattern("LWL")
                .pattern("SRS")
                .unlockedBy(getHasName(GCItems.ADVANCED_WAFER), has(GCItems.ADVANCED_WAFER))
                .emiDefaultRecipe(true)
                .save(output);

        // Rocket Part Items
        GCShapedRecipeBuilder.crafting(RecipeCategory.TRANSPORTATION, GCItems.NOSE_CONE)
                .define('R', Items.REDSTONE_TORCH)
                .define('P', GCItems.TIER_1_HEAVY_DUTY_PLATE)
                .pattern(" R ")
                .pattern(" P ")
                .pattern("P P")
                .unlockedBy(getHasName(GCItems.TIER_1_HEAVY_DUTY_PLATE), has(GCItems.TIER_1_HEAVY_DUTY_PLATE))
                .emiDefaultRecipe(true)
                .save(output);

        GCShapedRecipeBuilder.crafting(RecipeCategory.TRANSPORTATION, GCItems.HEAVY_NOSE_CONE)
                .define('R', Items.REDSTONE_TORCH)
                .define('P', GCItems.TIER_3_HEAVY_DUTY_PLATE)
                .pattern(" R ")
                .pattern(" P ")
                .pattern("P P")
                .unlockedBy(getHasName(GCItems.TIER_3_HEAVY_DUTY_PLATE), has(GCItems.TIER_3_HEAVY_DUTY_PLATE))
                .emiDefaultRecipe(true)
                .save(output);

        GCShapedRecipeBuilder.crafting(RecipeCategory.TRANSPORTATION, GCItems.ROCKET_FIN)
                .define('S', GCItems.COMPRESSED_STEEL)
                .define('P', GCItems.TIER_1_HEAVY_DUTY_PLATE)
                .pattern(" S ")
                .pattern("PSP")
                .pattern("P P")
                .unlockedBy(getHasName(GCItems.TIER_1_HEAVY_DUTY_PLATE), has(GCItems.TIER_1_HEAVY_DUTY_PLATE))
                .emiDefaultRecipe(true)
                .save(output);

        GCShapedRecipeBuilder.crafting(RecipeCategory.TRANSPORTATION, GCItems.HEAVY_ROCKET_FIN)
                .define('T', GCItems.TIER_2_HEAVY_DUTY_PLATE)
                .define('P', GCItems.TIER_3_HEAVY_DUTY_PLATE)
                .pattern(" T ")
                .pattern("PTP")
                .pattern("P P")
                .unlockedBy(getHasName(GCItems.TIER_3_HEAVY_DUTY_PLATE), has(GCItems.TIER_3_HEAVY_DUTY_PLATE))
                .save(output);

        GCShapedRecipeBuilder.crafting(RecipeCategory.TRANSPORTATION, GCItems.HEAVY_ROCKET_FIN)
                .define('T', GCItems.COMPRESSED_TITANIUM)
                .define('P', GCItems.TIER_3_HEAVY_DUTY_PLATE)
                .pattern(" T ")
                .pattern("PTP")
                .pattern("P P")
                .unlockedBy(getHasName(GCItems.TIER_3_HEAVY_DUTY_PLATE), has(GCItems.TIER_3_HEAVY_DUTY_PLATE))
                .save(output, getItemName(GCItems.HEAVY_ROCKET_FIN) + "_alt");

        GCShapedRecipeBuilder.crafting(RecipeCategory.TRANSPORTATION, GCItems.ROCKET_ENGINE)
                .define('F', Items.FLINT_AND_STEEL)
                .define('B', ItemTags.STONE_BUTTONS)
                .define('V', GCItems.OXYGEN_VENT)
                .define('P', GCItems.TIER_1_HEAVY_DUTY_PLATE)
                .define('T', GCItemTags.TIN_CANISTERS)
                .pattern(" FB")
                .pattern("PTP")
                .pattern("PVP")
                .unlockedBy(getHasName(GCItems.TIER_1_HEAVY_DUTY_PLATE), has(GCItems.TIER_1_HEAVY_DUTY_PLATE))
                .emiDefaultRecipe(true)
                .save(output);

        GCShapedRecipeBuilder.crafting(RecipeCategory.TRANSPORTATION, GCItems.HEAVY_ROCKET_ENGINE)
                .define('F', Items.FLINT_AND_STEEL)
                .define('B', ItemTags.STONE_BUTTONS)
                .define('V', GCItems.OXYGEN_VENT)
                .define('P', GCItems.TIER_3_HEAVY_DUTY_PLATE)
                .define('T', GCItemTags.TIN_CANISTERS)
                .pattern(" FB")
                .pattern("PTP")
                .pattern("PVP")
                .unlockedBy(getHasName(GCItems.TIER_3_HEAVY_DUTY_PLATE), has(GCItems.TIER_3_HEAVY_DUTY_PLATE))
                .emiDefaultRecipe(true)
                .save(output);

        GCShapedRecipeBuilder.crafting(RecipeCategory.TRANSPORTATION, GCItems.ROCKET_BOOSTER)
                .define('M', GCItems.COMPRESSED_METEORIC_IRON)
                .define('Y', Items.YELLOW_WOOL)
                .define('F', GCItemTags.FUEL_BUCKETS)
                .define('P', GCItems.TIER_1_HEAVY_DUTY_PLATE)
                .define('V', GCItems.OXYGEN_VENT)
                .pattern("MYM")
                .pattern("MFM")
                .pattern("PVP")
                .unlockedBy(getHasName(GCItems.COMPRESSED_METEORIC_IRON), has(GCItems.COMPRESSED_METEORIC_IRON))
                .emiDefaultRecipe(true)
                .save(output);

        RocketRecipeBuilder.create(GCItems.ROCKET)
                .rocketData(RocketPrefabs.TIER_1)
                .cone(GCItems.NOSE_CONE)
                .body(GCItems.TIER_1_HEAVY_DUTY_PLATE)
                .bodyHeight(4)
                .fins(GCItems.ROCKET_FIN)
                .engine(GCItems.ROCKET_ENGINE)
                .unlockedBy(getHasName(GCItems.TIER_1_HEAVY_DUTY_PLATE), has(GCItems.TIER_1_HEAVY_DUTY_PLATE))
                .save(output);

        // No dedicated "rocket + chest" recipe: the workbench's chest slots are an upgrade applied
        // on top of whichever tier's recipe matched (RocketWorkbenchMenu#withWorkbenchUpgrade), so
        // storage works for every tier and records how many chests were installed. A separate
        // recipe could never match anyway - its storage ingredient made it need a 15th ingredient
        // slot that the workbench does not have.

        RocketRecipeBuilder.create(GCItems.ROCKET)
                .rocketData(RocketPrefabs.TIER_2)
                .cone(GCItems.NOSE_CONE)
                .body(GCItems.TIER_2_HEAVY_DUTY_PLATE)
                .bodyHeight(4)
                .fins(GCItems.ROCKET_FIN)
                .engine(GCItems.ROCKET_ENGINE)
                .schematic(GCItems.TIER_2_ROCKET_SCHEMATIC)
                .sortOrder(20)
                .unlockedBy(getHasName(GCItems.TIER_2_HEAVY_DUTY_PLATE), has(GCItems.TIER_2_HEAVY_DUTY_PLATE))
                .save(output, getItemName(GCItems.ROCKET) + "_tier_2");

        RocketRecipeBuilder.create(GCItems.ROCKET)
                .rocketData(RocketPrefabs.TIER_3)
                .cone(GCItems.HEAVY_NOSE_CONE)
                .body(GCItems.TIER_3_HEAVY_DUTY_PLATE)
                .bodyHeight(4)
                .fins(GCItems.HEAVY_ROCKET_FIN)
                .engine(GCItems.HEAVY_ROCKET_ENGINE)
                .schematic(GCItems.TIER_3_ROCKET_SCHEMATIC)
                .sortOrder(30)
                .unlockedBy(getHasName(GCItems.TIER_3_HEAVY_DUTY_PLATE), has(GCItems.TIER_3_HEAVY_DUTY_PLATE))
                .save(output, getItemName(GCItems.ROCKET) + "_tier_3");

        this.buildVehiclePages(output);
    }

    /**
     * The workbench pages legacy Galacticraft unlocked with a schematic. Slot order follows legacy's
     * containers exactly, so the parts sit in the wells its GUI textures paint.
     */
    private void buildVehiclePages(RecipeOutput output) {
        // Legacy ContainerBuggyBench: three columns of plating with the seat in the middle column,
        // then the four wheels. Storage goes in the upgrade wells, not the recipe.
        SchematicCraftingRecipeBuilder.create(WorkbenchLayout.BUGGY, GCItems.BUGGY)
                .slots(4, GCItems.TIER_1_HEAVY_DUTY_PLATE)
                .slot(GCItems.TIER_1_HEAVY_DUTY_PLATE)
                .slot(GCItems.BUGGY_SEAT)
                .slots(2, GCItems.TIER_1_HEAVY_DUTY_PLATE)
                .slots(4, GCItems.TIER_1_HEAVY_DUTY_PLATE)
                .slots(4, GCItems.BUGGY_WHEEL)
                .schematic(GCItems.MOON_BUGGY_SCHEMATIC)
                .sortOrder(10)
                .unlockedBy(getHasName(GCItems.MOON_BUGGY_SCHEMATIC), has(GCItems.MOON_BUGGY_SCHEMATIC))
                .save(output, getItemName(GCItems.BUGGY));

        // Legacy ContainerSchematicCargoRocket: an unmanned tier-2 hull - no cockpit, so an advanced
        // wafer flies it instead.
        SchematicCraftingRecipeBuilder.create(WorkbenchLayout.CARGO_ROCKET, GCItems.CARGO_ROCKET)
                .slot(GCItems.NOSE_CONE)
                .slot(GCItems.ADVANCED_WAFER)
                .slots(6, GCItems.TIER_2_HEAVY_DUTY_PLATE)
                .slots(2, GCItems.ROCKET_FIN)
                .slot(GCItems.ROCKET_ENGINE)
                .slots(2, GCItems.ROCKET_FIN)
                .schematic(GCItems.CARGO_ROCKET_SCHEMATIC)
                .sortOrder(40)
                .unlockedBy(getHasName(GCItems.CARGO_ROCKET_SCHEMATIC), has(GCItems.CARGO_ROCKET_SCHEMATIC))
                .save(output, getItemName(GCItems.CARGO_ROCKET));

        // Legacy RecipeManagerAsteroids: plating and orion drives in three hull layers, a wafer and
        // two chests amidships, then the beam core and flag pole that make up the mining lasers.
        SchematicCraftingRecipeBuilder.create(WorkbenchLayout.ASTRO_MINER, GCItems.ASTRO_MINER)
                .slots(GCItems.TIER_1_HEAVY_DUTY_PLATE, GCItems.ORION_DRIVE, GCItems.TIER_1_HEAVY_DUTY_PLATE, GCItems.ORION_DRIVE)
                .slot(GCItems.TIER_1_HEAVY_DUTY_PLATE)
                .slot(GCItems.ADVANCED_WAFER)
                .slot(Items.CHEST)
                .slot(Items.CHEST)
                .slot(GCItems.ORION_DRIVE)
                .slots(GCItems.ORION_DRIVE, GCItems.TIER_1_HEAVY_DUTY_PLATE, GCItems.ORION_DRIVE)
                .slot(GCItems.BEAM_CORE)
                .slot(GCItemTags.FLAGS)
                .schematic(GCItems.ASTRO_MINER_SCHEMATIC)
                .sortOrder(50)
                .unlockedBy(getHasName(GCItems.ASTRO_MINER_SCHEMATIC), has(GCItems.ASTRO_MINER_SCHEMATIC))
                .save(output, getItemName(GCItems.ASTRO_MINER));
    }

    @Override
    public @NotNull String getName() {
        return "Rocket Recipes";
    }
}
