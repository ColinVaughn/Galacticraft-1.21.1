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

package dev.galacticraft.mod.recipe;

import com.mojang.serialization.MapCodec;
import dev.galacticraft.api.component.GCDataComponents;
import dev.galacticraft.api.rocket.RocketData;
import dev.galacticraft.api.rocket.RocketPaint;
import dev.galacticraft.api.rocket.RocketPrefabs;
import dev.galacticraft.api.rocket.part.RocketPartTypes;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.content.item.GCItems;
import dev.galacticraft.mod.content.item.RocketModuleItem;
import dev.galacticraft.mod.machine.workbench.WorkbenchPageDisplay;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;

import java.util.List;
import java.util.Optional;

public class ModularRocketRecipe extends RocketRecipe {
    public static final String TITLE = "gui.galacticraft.rocket_workbench.modular";
    public static final List<RocketSlotData> SLOTS = List.of(
            new RocketSlotData(RocketPartTypes.CONE, Constant.RocketWorkbench.CENTER_X - 9, 30),
            new RocketSlotData(RocketPartTypes.BODY, Constant.RocketWorkbench.CENTER_X - 9, 55),
            new RocketSlotData(RocketPartTypes.FIN, Constant.RocketWorkbench.CENTER_X - 9, 80),
            new RocketSlotData(RocketPartTypes.ENGINE, Constant.RocketWorkbench.CENTER_X - 9, 105));

    public ModularRocketRecipe() {
        super("", GCItems.ROCKET.getDefaultInstance(), 1, module(RocketPartTypes.BODY), module(RocketPartTypes.CONE),
                module(RocketPartTypes.ENGINE), module(RocketPartTypes.FIN), Ingredient.EMPTY, Ingredient.EMPTY, Optional.empty(), 0);
    }

    private static Ingredient module(RocketPartTypes type) {
        return Ingredient.of(GCItems.ROCKET_MODULES.stream().filter(item -> item.type() == type).map(ItemStack::new));
    }

    @Override public List<RocketSlotData> slots() { return SLOTS; }

    @Override public NonNullList<Ingredient> getIngredients() {
        return NonNullList.of(Ingredient.EMPTY, cone(), body(), fins(), engine());
    }

    @Override public WorkbenchPageDisplay display() {
        return WorkbenchPageDisplay.rocket(Component.translatable(TITLE));
    }

    public RocketData previewData(RecipeInput input) {
        RocketData cone = part(input, 0), body = part(input, 1), fin = part(input, 2), engine = part(input, 3);
        RocketPaint paint = new RocketPaint(moduleColor(cone, RocketPartTypes.CONE), moduleColor(body, RocketPartTypes.BODY),
                moduleColor(fin, RocketPartTypes.FIN), moduleColor(engine, RocketPartTypes.ENGINE), Optional.empty());
        return new RocketData(cone.cone(), body.body(), fin.fin(), Optional.empty(), engine.engine(),
                Optional.empty(), Optional.empty(), 0xFFFFFFFF, paint);
    }

    private RocketData part(RecipeInput input, int slot) {
        return slot < input.size() && input.getItem(slot).getItem() instanceof RocketModuleItem module
                && module.type() == SLOTS.get(slot).partType() ? module.rocketData(input.getItem(slot)) : RocketPrefabs.MISSING;
    }

    private static Optional<Integer> moduleColor(RocketData data, RocketPartTypes type) {
        return data.paint().color(type).or(() -> data.color() != 0xFFFFFFFF && data.color() != 0
                ? Optional.of(data.color() | 0xFF000000) : Optional.empty());
    }

    @Override public ItemStack assemble(RecipeInput input, HolderLookup.Provider lookup) {
        if (!matches(input, null)) return ItemStack.EMPTY;
        ItemStack result = getResultItem(lookup).copy();
        result.set(GCDataComponents.ROCKET_DATA, previewData(input));
        return result;
    }

    @Override public RecipeSerializer<?> getSerializer() { return GCRecipes.MODULAR_ROCKET_SERIALIZER; }

    public static class Serializer implements RecipeSerializer<ModularRocketRecipe> {
        private static final MapCodec<ModularRocketRecipe> CODEC = MapCodec.unit(ModularRocketRecipe::new);
        private static final StreamCodec<RegistryFriendlyByteBuf, ModularRocketRecipe> STREAM_CODEC = new StreamCodec<>() {
            @Override public ModularRocketRecipe decode(RegistryFriendlyByteBuf buf) { return new ModularRocketRecipe(); }
            @Override public void encode(RegistryFriendlyByteBuf buf, ModularRocketRecipe recipe) { }
        };
        @Override public MapCodec<ModularRocketRecipe> codec() { return CODEC; }
        @Override public StreamCodec<RegistryFriendlyByteBuf, ModularRocketRecipe> streamCodec() { return STREAM_CODEC; }
    }
}
