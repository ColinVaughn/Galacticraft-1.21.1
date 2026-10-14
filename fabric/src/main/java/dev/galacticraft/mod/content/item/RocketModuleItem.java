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

package dev.galacticraft.mod.content.item;

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

    public RocketPartTypes type() { return this.type; }
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
        rocketData(stack).paint().color(type).ifPresent(color -> tooltip.add(Component.translatable("gui.galacticraft.rocket_paint.color",
                String.format("#%06X", color & 0xFFFFFF)).withStyle(ChatFormatting.GRAY)));
    }
}
