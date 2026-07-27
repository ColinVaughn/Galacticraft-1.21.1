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

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.galacticraft.api.rocket.part.RocketPartTypes;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.content.GCBlocks;
import dev.galacticraft.mod.machine.workbench.WorkbenchPageDisplay;
import dev.galacticraft.mod.machine.workbench.WorkbenchSlot;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static dev.galacticraft.mod.Constant.RocketWorkbench.CENTER_X;

public class RocketRecipe implements WorkbenchRecipe {
    private final String group;
    private final ItemStack result;

    private final Ingredient cone;
    private final Ingredient engine;
    private final Ingredient body;
    private final Ingredient fins;
    private final Ingredient boosters;
    private final Ingredient storage;

    private final int bodyHeight;

    private final Optional<Item> schematic;
    private final int sortOrder;

    public RocketRecipe(String group, ItemStack result, int bodyHeight, Ingredient body, Ingredient cone, Ingredient engine, Ingredient fins, Ingredient boosters, Ingredient storage) {
        this(group, result, bodyHeight, body, cone, engine, fins, boosters, storage, Optional.empty(), 0);
    }

    public RocketRecipe(String group, ItemStack result, int bodyHeight, Ingredient body, Ingredient cone, Ingredient engine, Ingredient fins, Ingredient boosters, Ingredient storage, Optional<Item> schematic, int sortOrder) {
        this.group = group;
        this.result = result;

        this.bodyHeight = bodyHeight;
        this.body = body;
        this.cone = cone;
        this.engine = engine;
        this.fins = fins;
        this.boosters = boosters;
        this.storage = storage;

        this.schematic = schematic;
        this.sortOrder = sortOrder;
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
        List<Ingredient> ingredients = this.getIngredients();
        List<RocketSlotData> positions = slotData(this.bodyHeight, !this.boosters.isEmpty());
        List<WorkbenchSlot> slots = new ArrayList<>(positions.size());
        for (int i = 0; i < positions.size() && i < ingredients.size(); i++) {
            RocketSlotData data = positions.get(i);
            slots.add(new WorkbenchSlot(data.x(), data.y(), ingredients.get(i), background(data), data.mirror()));
        }
        return slots;
    }

    @Override
    public WorkbenchPageDisplay display() {
        return WorkbenchPageDisplay.rocket(this.result.getHoverName());
    }

    private static ResourceLocation background(RocketSlotData data) {
        return switch (data.partType()) {
            case CONE -> Constant.SlotSprite.ROCKET_CONE;
            case BODY -> Constant.SlotSprite.ROCKET_PLATING;
            case BOOSTER -> Constant.SlotSprite.ROCKET_BOOSTER;
            case FIN -> data.mirror() ? Constant.SlotSprite.ROCKET_FIN_RIGHT : Constant.SlotSprite.ROCKET_FIN_LEFT;
            case ENGINE -> Constant.SlotSprite.ROCKET_ENGINE;
            default -> null;
        };
    }

    @Override
    public @NotNull NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> ingredients = NonNullList.create();
        ingredients.add(this.cone);

        for (int i = 0; i < this.bodyHeight * 2; i++)
            ingredients.add(this.body);

        if (!this.boosters.isEmpty()) {
            ingredients.add(this.boosters);
            ingredients.add(this.boosters);
        }

        for (int i = 0; i < 4; i++)
            ingredients.add(this.fins);

        ingredients.add(this.engine);

        if (!this.storage.isEmpty()) {
            ingredients.add(this.storage);
        }

        return ingredients;
    }

    @Override
    public @NotNull ItemStack getToastSymbol() {
        return new ItemStack(GCBlocks.ROCKET_WORKBENCH);
    }

    @Override
    public boolean matches(RecipeInput input, Level level) {
        NonNullList<Ingredient> ingredients = this.getIngredients();

        if (ingredients.size() != input.size())
            return false;

        for (int i = 0; i < ingredients.size(); i++) {
            if (!ingredients.get(i).test(input.getItem(i)))
                return false;
        }

        return true;
    }

    @Override
    public @NotNull ItemStack assemble(RecipeInput input, HolderLookup.Provider lookup) {
        return this.getResultItem(lookup).copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height > 0;
    }

    @Override
    public @NotNull ItemStack getResultItem(HolderLookup.Provider registriesLookup) {
        return this.result;
    }

    @Override
    public @NotNull RecipeSerializer<?> getSerializer() {
        return GCRecipes.ROCKET_SERIALIZER;
    }

    @Override
    public @NotNull RecipeType<?> getType() {
        return GCRecipes.ROCKET_TYPE;
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

    public Ingredient cone() {
        return this.cone;
    }

    public Ingredient engine() {
        return this.engine;
    }

    public Ingredient body() {
        return this.body;
    }

    public Ingredient fins() {
        return this.fins;
    }

    public Ingredient boosters() {
        return this.boosters;
    }

    public Ingredient storage() {
        return this.storage;
    }

    public int bodyHeight() {
        return this.bodyHeight;
    }

    public static List<RocketSlotData> slotData(int bodyHeight, boolean hasBoosters) {
        List<RocketSlotData> data = new ArrayList<>();
        int rocketHeight = 18 * (bodyHeight + 2);
        int y = 81 - rocketHeight / 2;

        // Cone
        data.add(new RocketSlotData(RocketPartTypes.CONE, CENTER_X - 9, y));

        // Body
        for (int i = 0; i < bodyHeight; i++) {
            data.add(new RocketSlotData(RocketPartTypes.BODY, CENTER_X - 18, 18 + 18 * i + y));
            data.add(new RocketSlotData(RocketPartTypes.BODY, CENTER_X, 18 + 18 * i + y));
        }

        // Boosters
        if (hasBoosters) {
            data.add(new RocketSlotData(RocketPartTypes.BOOSTER, CENTER_X - 36, 18 * bodyHeight - 18 + y));
            data.add(new RocketSlotData(RocketPartTypes.BOOSTER, CENTER_X + 18, 18 * bodyHeight - 18 + y));
        }

        // Left fins
        data.add(new RocketSlotData(RocketPartTypes.FIN, CENTER_X - 36, 18 * bodyHeight + y));
        data.add(new RocketSlotData(RocketPartTypes.FIN, CENTER_X - 36, 18 * bodyHeight + 18 + y));

        // Right fins
        data.add(new RocketSlotData(RocketPartTypes.FIN, CENTER_X + 18, 18 * bodyHeight + y, true));
        data.add(new RocketSlotData(RocketPartTypes.FIN, CENTER_X + 18, 18 * bodyHeight + 18 + y, true));

        // Engine
        data.add(new RocketSlotData(RocketPartTypes.ENGINE, CENTER_X - 9, 18 * bodyHeight + 18 + y));

        return data;
    }

    public record RocketSlotData(RocketPartTypes partType, int x, int y, boolean mirror) {
        public RocketSlotData(RocketPartTypes partType, int x, int y) {
            this(partType, x, y, false);
        }
    }

    public static class Serializer implements RecipeSerializer<RocketRecipe> {
        public static final Serializer INSTANCE = new Serializer();

        private static final StreamCodec<RegistryFriendlyByteBuf, Optional<Item>> SCHEMATIC_STREAM_CODEC =
                ByteBufCodecs.registry(Registries.ITEM).apply(ByteBufCodecs::optional);

        public static final MapCodec<RocketRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.STRING.optionalFieldOf("group", "").forGetter(r -> r.group),
                ItemStack.CODEC.fieldOf("result").forGetter(r -> r.result),
                Codec.INT.fieldOf("body_height").forGetter(r -> r.bodyHeight),
                Ingredient.CODEC.fieldOf("body").forGetter(r -> r.body),
                Ingredient.CODEC.fieldOf("cone").forGetter(r -> r.cone),
                Ingredient.CODEC.fieldOf("engine").forGetter(r -> r.engine),
                Ingredient.CODEC.fieldOf("fins").forGetter(r -> r.fins),
                Ingredient.CODEC.optionalFieldOf("boosters", Ingredient.EMPTY).forGetter(recipe -> recipe.boosters),
                Ingredient.CODEC.optionalFieldOf("storage", Ingredient.EMPTY).forGetter(recipe -> recipe.storage),
                BuiltInRegistries.ITEM.byNameCodec().optionalFieldOf("schematic").forGetter(recipe -> recipe.schematic),
                Codec.INT.optionalFieldOf("sort_order", 0).forGetter(recipe -> recipe.sortOrder)
        ).apply(instance, RocketRecipe::new));

        // RocketRecipe has too many fields for StreamCodec.composite oops
        public static final StreamCodec<RegistryFriendlyByteBuf, RocketRecipe> STREAM_CODEC = new StreamCodec<>() {
            @Override
            public @NotNull RocketRecipe decode(RegistryFriendlyByteBuf buf) {
                return new RocketRecipe(
                        ByteBufCodecs.STRING_UTF8.decode(buf),
                        ItemStack.STREAM_CODEC.decode(buf),
                        ByteBufCodecs.INT.decode(buf),
                        Ingredient.CONTENTS_STREAM_CODEC.decode(buf),
                        Ingredient.CONTENTS_STREAM_CODEC.decode(buf),
                        Ingredient.CONTENTS_STREAM_CODEC.decode(buf),
                        Ingredient.CONTENTS_STREAM_CODEC.decode(buf),
                        Ingredient.CONTENTS_STREAM_CODEC.decode(buf),
                        Ingredient.CONTENTS_STREAM_CODEC.decode(buf),
                        SCHEMATIC_STREAM_CODEC.decode(buf),
                        ByteBufCodecs.INT.decode(buf)
                );
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buf, RocketRecipe r) {
                ByteBufCodecs.STRING_UTF8.encode(buf, r.group);
                ItemStack.STREAM_CODEC.encode(buf, r.result);
                ByteBufCodecs.INT.encode(buf, r.bodyHeight);
                Ingredient.CONTENTS_STREAM_CODEC.encode(buf, r.body);
                Ingredient.CONTENTS_STREAM_CODEC.encode(buf, r.cone);
                Ingredient.CONTENTS_STREAM_CODEC.encode(buf, r.engine);
                Ingredient.CONTENTS_STREAM_CODEC.encode(buf, r.fins);
                Ingredient.CONTENTS_STREAM_CODEC.encode(buf, r.boosters);
                Ingredient.CONTENTS_STREAM_CODEC.encode(buf, r.storage);
                SCHEMATIC_STREAM_CODEC.encode(buf, r.schematic);
                ByteBufCodecs.INT.encode(buf, r.sortOrder);
            }
        };

        @Override
        public @NotNull MapCodec<RocketRecipe> codec() {
            return CODEC;
        }

        @Override
        public @NotNull StreamCodec<RegistryFriendlyByteBuf, RocketRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
