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

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.galacticraft.mod.content.GCBlocks;
import dev.galacticraft.mod.machine.workbench.WorkbenchLayout;
import dev.galacticraft.mod.machine.workbench.WorkbenchPageDisplay;
import dev.galacticraft.mod.machine.workbench.WorkbenchSlot;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A positional workbench recipe for a vehicle that is not a rocket - the moon buggy, cargo rocket
 * and astro miner.
 *
 * <p>Like legacy Galacticraft's {@code NasaWorkbenchRecipe}, the ingredients are indexed by well
 * rather than shapeless, so parts have to go where the page draws them. The wells themselves come
 * from a {@link WorkbenchLayout}, since their positions are fixed art rather than data.
 */
public class SchematicCraftingRecipe implements WorkbenchRecipe {
    private final String group;
    private final ItemStack result;
    private final WorkbenchLayout layout;
    private final Optional<Item> schematic;
    private final int sortOrder;
    private final List<Ingredient> ingredients;

    public SchematicCraftingRecipe(String group, ItemStack result, WorkbenchLayout layout, Optional<Item> schematic, int sortOrder, List<Ingredient> ingredients) {
        this.group = group;
        this.result = result;
        this.layout = layout;
        this.schematic = schematic;
        this.sortOrder = sortOrder;
        this.ingredients = List.copyOf(ingredients);
    }

    /**
     * Builds a recipe, rejecting one whose ingredients do not fill its layout. A page with too few
     * ingredients would draw wells that craft nothing; one with too many hides ingredients in wells
     * that are never drawn. Either way the page can never be completed, so datapacks fail at load.
     */
    public static DataResult<SchematicCraftingRecipe> create(String group, ItemStack result, WorkbenchLayout layout, Optional<Item> schematic, int sortOrder, List<Ingredient> ingredients) {
        return validate(new SchematicCraftingRecipe(group, result, layout, schematic, sortOrder, ingredients));
    }

    private static DataResult<SchematicCraftingRecipe> validate(SchematicCraftingRecipe recipe) {
        int wells = recipe.layout.ingredientSlots().size();
        if (recipe.ingredients.size() != wells) {
            return DataResult.error(() -> "the " + recipe.layout.getSerializedName() + " layout has " + wells
                    + " ingredient slots but the recipe lists " + recipe.ingredients.size());
        }
        return DataResult.success(recipe);
    }

    public WorkbenchLayout layout() {
        return this.layout;
    }

    @Override
    public Optional<Item> schematic() {
        return this.schematic;
    }

    @Override
    public int sortOrder() {
        return this.sortOrder;
    }

    @Override
    public List<WorkbenchSlot> ingredientSlots() {
        List<WorkbenchLayout.Position> positions = this.layout.ingredientSlots();
        List<WorkbenchSlot> slots = new ArrayList<>(this.ingredients.size());
        for (int i = 0; i < this.ingredients.size() && i < positions.size(); i++) {
            slots.add(new WorkbenchSlot(positions.get(i).x(), positions.get(i).y(), this.ingredients.get(i), null));
        }
        return slots;
    }

    @Override
    public WorkbenchPageDisplay display() {
        return WorkbenchPageDisplay.of(this.layout);
    }

    @Override
    public @NotNull NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> list = NonNullList.create();
        list.addAll(this.ingredients);
        return list;
    }

    @Override
    public boolean matches(RecipeInput input, Level level) {
        if (input.size() != this.ingredients.size()) return false;

        for (int slot = 0; slot < this.ingredients.size(); slot++) {
            if (!this.ingredients.get(slot).test(input.getItem(slot))) return false;
        }
        return true;
    }

    @Override
    public @NotNull ItemStack assemble(RecipeInput input, HolderLookup.Provider lookup) {
        return this.result.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height > 0;
    }

    @Override
    public @NotNull ItemStack getResultItem(HolderLookup.Provider lookup) {
        return this.result;
    }

    @Override
    public @NotNull ItemStack getToastSymbol() {
        return new ItemStack(GCBlocks.ROCKET_WORKBENCH);
    }

    @Override
    public @NotNull RecipeSerializer<?> getSerializer() {
        return GCRecipes.SCHEMATIC_CRAFTING_SERIALIZER;
    }

    @Override
    public @NotNull RecipeType<?> getType() {
        return GCRecipes.SCHEMATIC_CRAFTING_TYPE;
    }

    @Override
    public @NotNull String getGroup() {
        return this.group;
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    public ItemStack result() {
        return this.result;
    }

    public static class Serializer implements RecipeSerializer<SchematicCraftingRecipe> {
        public static final Serializer INSTANCE = new Serializer();

        public static final MapCodec<SchematicCraftingRecipe> CODEC = RecordCodecBuilder.<SchematicCraftingRecipe>mapCodec(instance -> instance.group(
                Codec.STRING.optionalFieldOf("group", "").forGetter(recipe -> recipe.group),
                ItemStack.CODEC.fieldOf("result").forGetter(recipe -> recipe.result),
                WorkbenchLayout.CODEC.fieldOf("layout").forGetter(recipe -> recipe.layout),
                BuiltInRegistries.ITEM.byNameCodec().optionalFieldOf("schematic").forGetter(recipe -> recipe.schematic),
                Codec.INT.optionalFieldOf("sort_order", 0).forGetter(recipe -> recipe.sortOrder),
                Ingredient.CODEC.listOf().fieldOf("ingredients").forGetter(recipe -> recipe.ingredients)
        ).apply(instance, SchematicCraftingRecipe::new)).flatXmap(SchematicCraftingRecipe::validate, DataResult::success);

        private static final StreamCodec<RegistryFriendlyByteBuf, Optional<Item>> SCHEMATIC_STREAM_CODEC =
                ByteBufCodecs.registry(Registries.ITEM).apply(ByteBufCodecs::optional);
        private static final StreamCodec<RegistryFriendlyByteBuf, List<Ingredient>> INGREDIENTS_STREAM_CODEC =
                Ingredient.CONTENTS_STREAM_CODEC.apply(ByteBufCodecs.list());

        public static final StreamCodec<RegistryFriendlyByteBuf, SchematicCraftingRecipe> STREAM_CODEC = new StreamCodec<>() {
            @Override
            public @NotNull SchematicCraftingRecipe decode(RegistryFriendlyByteBuf buf) {
                return new SchematicCraftingRecipe(
                        ByteBufCodecs.STRING_UTF8.decode(buf),
                        ItemStack.STREAM_CODEC.decode(buf),
                        buf.readEnum(WorkbenchLayout.class),
                        SCHEMATIC_STREAM_CODEC.decode(buf),
                        ByteBufCodecs.INT.decode(buf),
                        INGREDIENTS_STREAM_CODEC.decode(buf)
                );
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buf, SchematicCraftingRecipe recipe) {
                ByteBufCodecs.STRING_UTF8.encode(buf, recipe.group);
                ItemStack.STREAM_CODEC.encode(buf, recipe.result);
                buf.writeEnum(recipe.layout);
                SCHEMATIC_STREAM_CODEC.encode(buf, recipe.schematic);
                ByteBufCodecs.INT.encode(buf, recipe.sortOrder);
                INGREDIENTS_STREAM_CODEC.encode(buf, recipe.ingredients);
            }
        };

        @Override
        public @NotNull MapCodec<SchematicCraftingRecipe> codec() {
            return CODEC;
        }

        @Override
        public @NotNull StreamCodec<RegistryFriendlyByteBuf, SchematicCraftingRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
