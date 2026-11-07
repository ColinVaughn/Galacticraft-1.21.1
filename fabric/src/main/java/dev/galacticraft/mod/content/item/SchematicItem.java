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

import java.util.List;
import net.minecraft.core.component.DataComponentPatch;
import dev.galacticraft.api.component.GCDataComponents;
import dev.galacticraft.api.item.Schematic;
import dev.galacticraft.api.rocket.part.RocketPart;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SchematicItem extends Item implements Schematic {
    public SchematicItem(Properties settings) {
        super(settings);
    }

    /** The components that make a schematic unlock {@code parts}; for loot table or recipe datagen. */
    public static DataComponentPatch componentsFor(List<? extends ResourceKey<?>> parts) {
        return DataComponentPatch.builder().set(GCDataComponents.ROCKET_PART_UNLOCKS, List.copyOf(parts)).build();
    }

    /** A {@code galacticraft:rocket_schematic} that unlocks {@code parts} when spent in the rocket workbench. */
    public static ItemStack stackFor(List<? extends ResourceKey<?>> parts) {
        ItemStack stack = new ItemStack(GCItems.ROCKET_SCHEMATIC);
        stack.applyComponents(componentsFor(parts));
        return stack;
    }

    @Override
    public @Nullable ResourceKey<? extends RocketPart<?, ?>> getPart(@NotNull ItemStack stack) {
        ResourceKey<?> resourceKey = stack.get(GCDataComponents.KEY);
        if (resourceKey != null) {
            return (ResourceKey<? extends RocketPart<?, ?>>) resourceKey;
        }
        return null;
    }
}
