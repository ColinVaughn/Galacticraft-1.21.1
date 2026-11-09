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

import org.jetbrains.annotations.Nullable;
import net.minecraft.core.registries.BuiltInRegistries;
import dev.galacticraft.api.component.GCDataComponents;
import dev.galacticraft.api.rocket.RocketData;
import dev.galacticraft.api.rocket.part.RocketPartTypes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import java.util.List;

public class RocketModuleItem extends Item {
    private final RocketPartTypes type;
    private final int tier;
    private final RocketData data;

    public RocketModuleItem(RocketPartTypes type, int tier, RocketData data) {
        super(new Properties().stacksTo(1).component(GCDataComponents.ROCKET_DATA, data));
        this.type = type;
        this.tier = tier;
        this.data = data;
    }

    private static @Nullable List<RocketModuleItem> all;

    /**
     * Every registered rocket module item, Galacticraft's and addons' alike. The workbench accepts
     * all of them and the client renders all of them, so an addon only needs to register one.
     * Valid once item registration has finished.
     */
    public static List<RocketModuleItem> all() {
        if (all == null) {
            all = BuiltInRegistries.ITEM.stream()
                    .filter(RocketModuleItem.class::isInstance)
                    .map(RocketModuleItem.class::cast)
                    .toList();
        }
        return all;
    }

    /**
     * The slot this item's part fits, or {@code null} for the {@linkplain GenericRocketModuleItem
     * generic module}, whose slot depends on the stack. Prefer {@link #type(ItemStack)}.
     */
    public @Nullable RocketPartTypes type() { return this.type; }

    /** The slot the part on {@code stack} fits, or {@code null} if the stack carries no part. */
    public @Nullable RocketPartTypes type(ItemStack stack) { return this.type; }
    public int tier() { return this.tier; }
    public RocketData rocketData() { return this.data; }

    public RocketData rocketData(ItemStack stack) {
        RocketData saved = stack.getOrDefault(GCDataComponents.ROCKET_DATA, this.data);
        // A module's item determines its identity; only its finish is customizable.
        return new RocketData(data.cone(), data.body(), data.fin(), data.booster(), data.engine(), data.upgrade(),
                data.upgradeData(), saved.color(), saved.paint(), saved.tints());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flags) {
        tooltip.add(Component.translatable("tooltip.galacticraft.rocket_module").withStyle(ChatFormatting.GRAY));
        RocketPartTypes type = this.type(stack);
        if (type == null) return;
        rocketData(stack).paint().color(type).ifPresent(color -> tooltip.add(Component.translatable("gui.galacticraft.rocket_paint.color",
                String.format("#%06X", color & 0xFFFFFF)).withStyle(ChatFormatting.GRAY)));
    }
}
