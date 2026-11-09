package dev.galacticraft.mod.data.recipes;

import dev.galacticraft.mod.api.data.recipe.GCShapedRecipeBuilder;
import dev.galacticraft.mod.api.data.recipe.GCShapelessRecipeBuilder;
import dev.galacticraft.mod.content.GCBlocks;
import dev.galacticraft.mod.content.block.jupiter.JupiterBlocks;
import dev.galacticraft.mod.content.item.GCItems;
import dev.galacticraft.mod.content.item.jupiter.JupiterItems;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.world.level.block.Blocks;
import static net.minecraft.data.recipes.RecipeProvider.*;

public final class JupiterRecipes {
    public static void build(RecipeOutput output) {
        GCShapedRecipeBuilder.crafting(RecipeCategory.MISC, JupiterItems.ATMOSPHERIC_ENTRY_UPGRADE)
                .define('P', GCItems.TIER_3_HEAVY_DUTY_PLATE).define('W', GCItems.ADVANCED_WAFER)
                .pattern("PPP").pattern("PWP").pattern("PPP")
                .unlockedBy("has_plate", has(GCItems.TIER_3_HEAVY_DUTY_PLATE)).save(output);
        GCShapedRecipeBuilder.crafting(RecipeCategory.MISC, JupiterItems.PRESSURE_CONTROLLER_MK1)
                .define('C', JupiterItems.HYDROSULFIDE_CRYSTAL).define('W', GCItems.ADVANCED_WAFER)
                .define('B', GCItems.BATTERY).define('P', GCItems.COMPRESSED_STEEL)
                .pattern(" C ").pattern("WBW").pattern("PPP")
                .unlockedBy("has_crystal", has(JupiterItems.HYDROSULFIDE_CRYSTAL)).save(output);
        GCShapedRecipeBuilder.crafting(RecipeCategory.MISC, JupiterItems.PRESSURE_CONTROLLER_MK2)
                .define('P', JupiterItems.JOVIAN_COMPOSITE_PLATE).define('C', JupiterItems.CHARGED_CONDENSATE)
                .define('W', GCItems.ADVANCED_WAFER).define('F', JupiterItems.PRESSURE_CONTROLLER_MK1)
                .pattern("PCP").pattern("WFW").pattern("PCP")
                .unlockedBy("has_composite", has(JupiterItems.JOVIAN_COMPOSITE_PLATE)).save(output);
        GCShapelessRecipeBuilder.crafting(RecipeCategory.MISC, JupiterItems.CLOUD_CONDENSATE)
                .requires(GCBlocks.DENSE_CLOUD, 4).unlockedBy("has_cloud", has(GCBlocks.DENSE_CLOUD)).save(output);
        GCShapedRecipeBuilder.crafting(RecipeCategory.MISC, JupiterItems.JOVIAN_COMPOSITE_PLATE, 2)
                .define('P', GCItems.TIER_3_HEAVY_DUTY_PLATE).define('C', JupiterItems.CHARGED_CONDENSATE)
                .define('S', JupiterItems.AEROSTAT_SALVAGE)
                .pattern("CPC").pattern("SSS").pattern("CPC")
                .unlockedBy("has_charged_condensate", has(JupiterItems.CHARGED_CONDENSATE)).save(output);

        GCShapedRecipeBuilder.crafting(RecipeCategory.BUILDING_BLOCKS, JupiterBlocks.BUOYANCY_CELL, 2)
                .define('S', JupiterItems.AEROSTAT_SALVAGE).define('C', JupiterItems.CLOUD_CONDENSATE)
                .pattern(" S ").pattern("SCS").pattern(" S ")
                .unlockedBy("has_salvage", has(JupiterItems.AEROSTAT_SALVAGE)).save(output);
        JupiterBlocks.VARIANT_BASES.forEach((variant, base) -> {
            if (variant instanceof net.minecraft.world.level.block.StairBlock) {
                GCShapedRecipeBuilder.crafting(RecipeCategory.BUILDING_BLOCKS, variant, 4).define('#', base)
                        .pattern("#  ").pattern("## ").pattern("###").unlockedBy("has_base", has(base)).save(output);
            } else {
                GCShapedRecipeBuilder.crafting(RecipeCategory.BUILDING_BLOCKS, variant, 6).define('#', base)
                        .pattern("###").unlockedBy("has_base", has(base)).save(output);
            }
        });

        // Aerostat construction. Only the reinforced hull, pressure glass and sealed hatch are pressure-rated.
        GCShapedRecipeBuilder.crafting(RecipeCategory.BUILDING_BLOCKS, JupiterBlocks.AEROSTAT_HULL, 8)
                .define('P', GCItems.COMPRESSED_STEEL).define('S', JupiterItems.AEROSTAT_SALVAGE)
                .pattern("PSP").pattern("S S").pattern("PSP")
                .unlockedBy("has_salvage", has(JupiterItems.AEROSTAT_SALVAGE)).save(output);
        GCShapedRecipeBuilder.crafting(RecipeCategory.BUILDING_BLOCKS, JupiterBlocks.REINFORCED_AEROSTAT_HULL, 8)
                .define('H', JupiterBlocks.AEROSTAT_HULL).define('J', JupiterItems.JOVIAN_COMPOSITE_PLATE)
                .pattern("HHH").pattern("HJH").pattern("HHH")
                .unlockedBy("has_composite", has(JupiterItems.JOVIAN_COMPOSITE_PLATE)).save(output);
        GCShapedRecipeBuilder.crafting(RecipeCategory.BUILDING_BLOCKS, JupiterBlocks.AEROSTAT_GRATING, 6)
                .define('I', Blocks.IRON_BARS).define('P', GCItems.COMPRESSED_STEEL)
                .pattern("III").pattern("PPP")
                .unlockedBy("has_salvage", has(JupiterItems.AEROSTAT_SALVAGE)).save(output);
        GCShapedRecipeBuilder.crafting(RecipeCategory.BUILDING_BLOCKS, JupiterBlocks.PRESSURE_GLASS, 8)
                .define('G', Blocks.GLASS).define('J', JupiterItems.JOVIAN_COMPOSITE_PLATE)
                .pattern("GGG").pattern("GJG").pattern("GGG")
                .unlockedBy("has_composite", has(JupiterItems.JOVIAN_COMPOSITE_PLATE)).save(output);
        GCShapedRecipeBuilder.crafting(RecipeCategory.BUILDING_BLOCKS, JupiterBlocks.SEALED_HATCH, 2)
                .define('T', Blocks.IRON_TRAPDOOR).define('J', JupiterItems.JOVIAN_COMPOSITE_PLATE)
                .pattern("JTJ").pattern("JTJ")
                .unlockedBy("has_composite", has(JupiterItems.JOVIAN_COMPOSITE_PLATE)).save(output);

        GCShapedRecipeBuilder.crafting(RecipeCategory.MISC, JupiterBlocks.ATMOSPHERIC_COLLECTOR)
                .define('F', GCItems.OXYGEN_FAN).define('P', GCItems.COMPRESSED_TITANIUM)
                .define('C', GCBlocks.OXYGEN_COLLECTOR).define('W', GCItems.ADVANCED_WAFER)
                .pattern("PFP").pattern("PCP").pattern("PWP")
                .unlockedBy("has_condensate", has(JupiterItems.CLOUD_CONDENSATE)).save(output);
        GCShapedRecipeBuilder.crafting(RecipeCategory.MISC, JupiterBlocks.CONDENSATE_SEPARATOR)
                .define('P', GCItems.COMPRESSED_STEEL).define('T', GCBlocks.FLUID_TANK)
                .define('H', JupiterItems.HYDROSULFIDE_CRYSTAL).define('W', GCItems.ADVANCED_WAFER).define('C', GCItems.COMPRESSED_COPPER)
                .pattern("PTP").pattern("WHW").pattern("PCP")
                .unlockedBy("has_condensate", has(JupiterItems.CLOUD_CONDENSATE)).save(output);
        GCShapedRecipeBuilder.crafting(RecipeCategory.MISC, JupiterBlocks.VEHICLE_DOCK)
                .define('S', JupiterItems.AEROSTAT_SALVAGE).define('V', GCItems.OXYGEN_VENT)
                .define('B', GCItems.BATTERY).define('W', GCItems.ADVANCED_WAFER).define('P', GCItems.COMPRESSED_TITANIUM)
                .pattern("SVS").pattern("BWB").pattern("PPP")
                .unlockedBy("has_cloud_diver", has(JupiterItems.CLOUD_DIVER)).save(output);
        GCShapedRecipeBuilder.crafting(RecipeCategory.MISC, JupiterBlocks.SURVEY_BEACON)
                .define('L', GCItems.SENSOR_LENS).define('F', GCItems.FREQUENCY_MODULE)
                .define('W', GCItems.BASIC_WAFER).define('S', GCItems.COMPRESSED_STEEL).define('R', GCItems.STEEL_POLE)
                .pattern(" L ").pattern("WFW").pattern("SRS")
                .unlockedBy("has_frequency_module", has(GCItems.FREQUENCY_MODULE)).save(output);
        GCShapedRecipeBuilder.crafting(RecipeCategory.MISC, JupiterBlocks.STORM_CAPACITOR)
                .define('P', JupiterItems.JOVIAN_COMPOSITE_PLATE).define('C', JupiterItems.CHARGED_CONDENSATE)
                .define('E', GCBlocks.ENERGY_STORAGE_MODULE).define('H', GCBlocks.HEAVY_ALUMINUM_WIRE)
                .pattern("PCP").pattern("EHE").pattern("PCP")
                .unlockedBy("has_charged_condensate", has(JupiterItems.CHARGED_CONDENSATE)).save(output);
        GCShapedRecipeBuilder.crafting(RecipeCategory.MISC, JupiterBlocks.LIGHTNING_GROUNDER)
                .define('C', JupiterItems.CHARGED_CONDENSATE).define('R', GCItems.STEEL_POLE)
                .define('P', JupiterItems.JOVIAN_COMPOSITE_PLATE).define('H', GCBlocks.HEAVY_ALUMINUM_WIRE)
                .pattern("CRC").pattern(" R ").pattern("PHP")
                .unlockedBy("has_charged_condensate", has(JupiterItems.CHARGED_CONDENSATE)).save(output);
        // Advanced habitats are the Stormheart encounter's reward.
        GCShapedRecipeBuilder.crafting(RecipeCategory.MISC, JupiterBlocks.PRESSURE_STABILIZER)
                .define('P', JupiterItems.JOVIAN_COMPOSITE_PLATE).define('V', GCItems.OXYGEN_VENT)
                .define('W', GCItems.ADVANCED_WAFER).define('S', GCBlocks.OXYGEN_SEALER).define('H', JupiterItems.STORMHEART_CORE)
                .pattern("PVP").pattern("WSW").pattern("PHP")
                .unlockedBy("has_stormheart_core", has(JupiterItems.STORMHEART_CORE)).save(output);
    }
}