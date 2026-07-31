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

package dev.galacticraft.mod.compat.rei.client.handler;

import dev.galacticraft.mod.compat.rei.common.GalacticraftREIServerPlugin;
import dev.galacticraft.mod.compat.rei.common.display.DefaultRocketDisplay;
import dev.galacticraft.mod.screen.RocketWorkbenchMenu;
import dev.galacticraft.mod.util.Translations;
import me.shedaniel.rei.api.client.registry.transfer.TransferHandler;
import me.shedaniel.rei.api.client.registry.transfer.simple.SimpleTransferHandler;
import me.shedaniel.rei.api.common.transfer.info.stack.SlotAccessor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.util.stream.IntStream;

/**
 * REI's stock simple handler requires a fixed input range. Workbench pages are intentionally
 * variable-sized, so resolve both the ingredient wells and player inventory from the open menu.
 */
public final class WorkbenchRecipeTransferHandler implements SimpleTransferHandler {
    @Override
    public TransferHandler.ApplicabilityResult checkApplicable(TransferHandler.Context context) {
        if (!(context.getMenu() instanceof RocketWorkbenchMenu menu)
                || !(context.getDisplay() instanceof DefaultRocketDisplay display)
                || !GalacticraftREIServerPlugin.ROCKET.equals(context.getDisplay().getCategoryIdentifier())
                || context.getContainerScreen() == null) {
            return TransferHandler.ApplicabilityResult.createNotApplicable();
        }

        if (display.getDisplayLocation().filter(id -> !id.equals(menu.page().id())).isPresent()
                || display.getInputEntries().size() != menu.ingredientSlots().size()) {
            return TransferHandler.ApplicabilityResult.createApplicableWithError(
                    Component.translatable(Translations.Tooltip.INCORRECT_NUMBER_OF_SLOTS)
            );
        }
        return TransferHandler.ApplicabilityResult.createApplicable();
    }

    @Override
    public Iterable<SlotAccessor> getInputSlots(TransferHandler.Context context) {
        RocketWorkbenchMenu menu = (RocketWorkbenchMenu) context.getMenu();
        return menu.ingredientSlots().stream().map(SlotAccessor::fromSlot).toList();
    }

    @Override
    public Iterable<SlotAccessor> getInventorySlots(TransferHandler.Context context) {
        Player player = context.getMinecraft().player;
        if (player == null) {
            return java.util.List.of();
        }
        return IntStream.range(0, player.getInventory().items.size())
                .mapToObj(index -> SlotAccessor.fromPlayerInventory(player, index))
                .toList();
    }
}
