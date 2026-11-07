/* Copyright (c) 2026 Team Galacticraft. Licensed under the MIT license. */
package dev.galacticraft.mod.recipe;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.galacticraft.api.component.GCDataComponents;
import dev.galacticraft.api.rocket.part.RocketPart;
import dev.galacticraft.api.rocket.recipe.RocketPartRecipe;
import dev.galacticraft.mod.content.item.GenericRocketModuleItem;
import dev.galacticraft.mod.machine.workbench.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import java.util.*;

/** A configured part's own pattern becomes a research-gated module crafting page. */
public record RocketModuleRecipe(ResourceKey<? extends RocketPart<?, ?>> part, RocketPartRecipe<?, ?> pattern) implements WorkbenchRecipe {
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static final MapCodec<RocketModuleRecipe> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            GCDataComponents.ROCKET_PART_KEY_CODEC.fieldOf("part").forGetter(recipe -> (ResourceKey<?>) recipe.part()),
            RocketPartRecipe.DIRECT_CODEC.fieldOf("recipe").forGetter(RocketModuleRecipe::pattern)
    ).apply(i, (part, pattern) -> new RocketModuleRecipe((ResourceKey) part, pattern)));

    @Override public Optional<Item> schematic() { return Optional.empty(); }
    @Override public int sortOrder() { return 100; }
    @Override public NonNullList<Ingredient> getIngredients() { return pattern.getIngredients(); }
    @Override public List<WorkbenchSlot> ingredientSlots() {
        List<WorkbenchSlot> slots = new ArrayList<>();
        pattern.place((index, x, y, filter) -> slots.add(new WorkbenchSlot(x, y, getIngredients().get(index), null)), 66, 102, Math.max(130, pattern.height() + 30));
        return slots;
    }
    @Override public WorkbenchPageDisplay display() {
        var base = WorkbenchPageDisplay.rocket(RocketPart.getName(part));
        int bottom = Math.max(130, pattern.height() + 30);
        return new WorkbenchPageDisplay(base.texture(), 0, bottom + 108, List.of(),
                new WorkbenchLayout.Position(142, bottom - 18), bottom + 28, bottom + 86,
                base.title(), false, true);
    }
    @Override public boolean matches(RecipeInput input, Level level) { return input.size() == pattern.slots() && pattern.matches(input, level); }
    @Override public ItemStack assemble(RecipeInput input, HolderLookup.Provider lookup) { return getResultItem(lookup); }
    @Override public ItemStack getResultItem(HolderLookup.Provider lookup) {
        return new net.minecraft.world.item.EitherHolder<>(part).unwrap(lookup).isPresent() ? GenericRocketModuleItem.stackFor(part) : ItemStack.EMPTY;
    }
    @Override public boolean canCraftInDimensions(int width, int height) { return width * height >= pattern.slots(); }
    @Override public RecipeSerializer<?> getSerializer() { return GCRecipes.ROCKET_MODULE_SERIALIZER; }
    @Override public RecipeType<?> getType() { return GCRecipes.ROCKET_MODULE_TYPE; }
    @Override public boolean isSpecial() { return true; }

    public static final class Serializer implements RecipeSerializer<RocketModuleRecipe> {
        @Override public MapCodec<RocketModuleRecipe> codec() { return CODEC; }
        @Override public StreamCodec<RegistryFriendlyByteBuf, RocketModuleRecipe> streamCodec() {
            return ByteBufCodecs.fromCodecWithRegistries(CODEC.codec());
        }
    }
}