/* Copyright (c) 2026 Team Galacticraft. Licensed under the MIT license. */
package dev.galacticraft.mod.recipe;

import dev.galacticraft.mod.api.data.recipe.GCShapedRecipeBuilder;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AddonRecipeBuilderTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test
    void stringIdsUseTheResultNamespaceOrAnExplicitNamespace() {
        check("addon_plate", "minecraft:compressing/addon_plate");
        check("examplepack:addon_plate", "examplepack:compressing/addon_plate");
    }

    private static void check(String name, String expected) {
        var builder = GCShapedRecipeBuilder.compressing(Items.IRON_NUGGET)
                .pattern("I").define('I', Items.IRON_INGOT)
                .unlockedBy("has_iron", InventoryChangeTrigger.TriggerInstance.hasItems(Items.IRON_INGOT));
        builder.save(new RecipeOutput() {
            @Override public Advancement.Builder advancement() { return Advancement.Builder.recipeAdvancement(); }
            @Override public void accept(ResourceLocation id, Recipe<?> recipe, AdvancementHolder advancement) {
                assertEquals(ResourceLocation.parse(expected), id);
                assertEquals(java.util.List.of(id), advancement.value().rewards().recipes());
            }
        }, name);
    }
}