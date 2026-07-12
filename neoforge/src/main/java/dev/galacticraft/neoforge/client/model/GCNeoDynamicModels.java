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

package dev.galacticraft.neoforge.client.model;

import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.content.GCBlocks;
import net.minecraft.client.resources.model.Material;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.models.model.ModelLocationUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.client.event.ModelEvent;

/** Installs loader-native baked replacements for block-entity connected models. */
public final class GCNeoDynamicModels {
    public static void modifyBakingResult(ModelEvent.ModifyBakingResult event) {
        for (Block pipe : GCBlocks.GLASS_FLUID_PIPES.values()) replace(event, pipe, ModelLocationUtils.getModelLocation(pipe), 0.125F);
        replace(event, GCBlocks.GLASS_FLUID_PIPE, Constant.id("block/glass_fluid_pipe"), 0.125F);
        replace(event, GCBlocks.ALUMINUM_WIRE, Constant.id("block/aluminum_wire"), 0.125F);
        replace(event, GCBlocks.HEAVY_ALUMINUM_WIRE, Constant.id("block/heavy_aluminum_wire"), 0.1875F);
        var glass = event.getTextureGetter().apply(new Material(InventoryMenu.BLOCK_ATLAS, Constant.id("block/vacuum_glass_vanilla")));
        var frame = event.getTextureGetter().apply(new Material(InventoryMenu.BLOCK_ATLAS, Constant.id("block/aluminum_decoration")));
        var vacuumGlass = new VacuumGlassBakedModelNeoForge(glass, frame);
        event.getModels().replaceAll((location, model) ->
                location.id().equals(Constant.BakedModel.VACUUM_GLASS_MODEL) && !"inventory".equals(location.variant())
                        ? vacuumGlass
                        : model);
        ResourceLocation cannedFood = BuiltInRegistries.BLOCK.getKey(GCBlocks.CANNED_FOOD);
        event.getModels().replaceAll((location, model) -> location.id().equals(cannedFood) ? new CannedFoodBakedModelNeoForge(model) : model);
    }

    private static void replace(ModelEvent.ModifyBakingResult event, Block block, ResourceLocation texture, float radius) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        var sprite = event.getTextureGetter().apply(new Material(InventoryMenu.BLOCK_ATLAS, texture));
        var replacement = new ConnectedPipeBakedModel(sprite, radius);
        event.getModels().replaceAll((location, model) ->
                location.id().equals(id) && !"inventory".equals(location.variant()) ? replacement : model);
    }

    private GCNeoDynamicModels() {}
}
