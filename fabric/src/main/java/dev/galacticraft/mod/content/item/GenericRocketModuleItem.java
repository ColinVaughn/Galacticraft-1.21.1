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

package dev.galacticraft.mod.content.item;

import dev.galacticraft.api.component.GCDataComponents;
import dev.galacticraft.api.rocket.RocketData;
import dev.galacticraft.api.rocket.part.RocketPart;
import dev.galacticraft.api.rocket.part.RocketPartTypes;
import dev.galacticraft.mod.content.rocket.RocketPrefabs;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.EitherHolder;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Rocket module carrying the part specified by its {@code galacticraft:rocket_data} stack component.
 * Datapack recipe results, loot tables, or commands can supply that component.
 */
public class GenericRocketModuleItem extends RocketModuleItem {
    public GenericRocketModuleItem() {
        super(null, 0, RocketPrefabs.MISSING);
    }

    @Override
    public RocketData rocketData(ItemStack stack) {
        return stack.getOrDefault(GCDataComponents.ROCKET_DATA, RocketPrefabs.MISSING);
    }

    @Override
    public @Nullable RocketPartTypes type(ItemStack stack) {
        RocketData data = this.rocketData(stack);
        if (data.cone().isPresent()) return RocketPartTypes.CONE;
        if (data.body().isPresent()) return RocketPartTypes.BODY;
        if (data.fin().isPresent()) return RocketPartTypes.FIN;
        if (data.booster().isPresent()) return RocketPartTypes.BOOSTER;
        if (data.engine().isPresent()) return RocketPartTypes.ENGINE;
        if (data.upgrade().isPresent()) return RocketPartTypes.UPGRADE;
        return null;
    }

    /** Named after the part it carries, using the part's translation key. */
    @Override
    public Component getName(ItemStack stack) {
        return part(this.rocketData(stack)).<Component>map(RocketPart::getName).orElseGet(() -> super.getName(stack));
    }

    /** The components that make a generic module carry {@code part}; for recipe datagen. */
    public static DataComponentPatch componentsFor(ResourceKey<? extends RocketPart<?, ?>> part) {
        return DataComponentPatch.builder().set(GCDataComponents.ROCKET_DATA, dataFor(part)).build();
    }

    /** A generic module carrying {@code part}. */
    public static ItemStack stackFor(ResourceKey<? extends RocketPart<?, ?>> part) {
        ItemStack stack = new ItemStack(GCItems.ROCKET_MODULE);
        stack.applyComponents(componentsFor(part));
        return stack;
    }

    /** Rocket data holding only {@code part}, in its own slot. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static RocketData dataFor(ResourceKey<? extends RocketPart<?, ?>> part) {
        Optional holder = Optional.of(new EitherHolder<>((ResourceKey) part));
        RocketPartTypes type = RocketPartTypes.fromPart(part);
        return new RocketData(
                type == RocketPartTypes.CONE ? holder : Optional.empty(),
                type == RocketPartTypes.BODY ? holder : Optional.empty(),
                type == RocketPartTypes.FIN ? holder : Optional.empty(),
                type == RocketPartTypes.BOOSTER ? holder : Optional.empty(),
                type == RocketPartTypes.ENGINE ? holder : Optional.empty(),
                type == RocketPartTypes.UPGRADE ? holder : Optional.empty(),
                Optional.empty(), 0xFFFFFFFF);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Optional<ResourceKey<? extends RocketPart<?, ?>>> part(RocketData data) {
        Optional<? extends EitherHolder<?>> part = data.cone().isPresent() ? data.cone()
                : data.body().isPresent() ? data.body()
                : data.fin().isPresent() ? data.fin()
                : data.booster().isPresent() ? data.booster()
                : data.engine().isPresent() ? data.engine() : data.upgrade();
        return (Optional) part.map(EitherHolder::key);
    }
}
