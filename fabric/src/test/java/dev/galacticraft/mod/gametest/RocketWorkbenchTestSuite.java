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

package dev.galacticraft.mod.gametest;

import dev.galacticraft.api.accessor.ServerResearchAccessor;
import dev.galacticraft.mod.content.GCBlocks;
import dev.galacticraft.mod.content.GCRocketParts;
import dev.galacticraft.mod.content.block.entity.RocketWorkbenchBlockEntity;
import dev.galacticraft.mod.content.item.GCItems;
import dev.galacticraft.mod.screen.RocketWorkbenchMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

public class RocketWorkbenchTestSuite implements GalacticraftGameTest {
    @GameTest(template = EMPTY_STRUCTURE)
    public void mixedTierRocketPartsAreRejected(GameTestHelper context) {
        BlockPos workbenchPos = new BlockPos(1, 1, 1);
        context.setBlock(workbenchPos, GCBlocks.ROCKET_WORKBENCH);

        RocketWorkbenchBlockEntity workbench = context.getBlockEntity(workbenchPos);
        ServerPlayer player = context.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.CREATIVE);
        RocketWorkbenchMenu menu = new RocketWorkbenchMenu(1, workbench, player.getInventory());

        for (int slot = 1; slot <= 8; slot++) {
            workbench.ingredients.setItem(slot, new ItemStack(GCItems.TIER_1_HEAVY_DUTY_PLATE));
        }
        for (int slot = 9; slot <= 12; slot++) {
            workbench.ingredients.setItem(slot, new ItemStack(GCItems.ROCKET_FIN));
        }
        workbench.ingredients.setItem(13, new ItemStack(GCItems.ROCKET_ENGINE));
        workbench.ingredients.setChanged();

        Slot coneSlot = menu.slots.getFirst();
        if (coneSlot.mayPlace(new ItemStack(GCItems.HEAVY_NOSE_CONE))) {
            menu.removed(player);
            context.fail("Expected the tier-1 assembly to reject a tier-3 heavy nose cone", workbenchPos);
            return;
        }

        // Reproduce a legacy/persisted mixed-tier inventory that bypassed slot validation.
        workbench.ingredients.setItem(0, new ItemStack(GCItems.HEAVY_NOSE_CONE));
        workbench.ingredients.setChanged();
        if (!workbench.output.getItem(0).isEmpty()) {
            menu.removed(player);
            context.fail("Expected no output for mixed-tier rocket parts", workbenchPos);
            return;
        }
        if (menu.previewRocket().cone().isPresent()) {
            menu.removed(player);
            context.fail("Expected the preview to mark the incompatible heavy nose cone as incomplete", workbenchPos);
            return;
        }

        workbench.ingredients.setItem(0, new ItemStack(GCItems.NOSE_CONE));
        workbench.ingredients.setChanged();
        menu.removed(player);
        if (workbench.output.getItem(0).is(GCItems.ROCKET)) {
            context.succeed();
        } else {
            context.fail("Expected replacing the heavy nose cone with a tier-1 nose cone to craft the rocket", workbenchPos);
        }
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void tierOneRocketCanBeCrafted(GameTestHelper context) {
        BlockPos workbenchPos = new BlockPos(1, 1, 1);
        context.setBlock(workbenchPos, GCBlocks.ROCKET_WORKBENCH);

        RocketWorkbenchBlockEntity workbench = context.getBlockEntity(workbenchPos);
        ServerPlayer player = context.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        ((ServerResearchAccessor) player).galacticraft$unlockRocketPartRecipes(
                GCRocketParts.recipeId(GCRocketParts.TIER_1_CONE),
                GCRocketParts.recipeId(GCRocketParts.TIER_1_BODY),
                GCRocketParts.recipeId(GCRocketParts.TIER_1_FIN),
                GCRocketParts.recipeId(GCRocketParts.TIER_1_ENGINE)
        );

        RocketWorkbenchMenu menu = new RocketWorkbenchMenu(1, workbench, player.getInventory());
        workbench.ingredients.setItem(0, new ItemStack(GCItems.NOSE_CONE));
        for (int slot = 1; slot <= 8; slot++) {
            workbench.ingredients.setItem(slot, new ItemStack(GCItems.TIER_1_HEAVY_DUTY_PLATE));
        }
        for (int slot = 9; slot <= 12; slot++) {
            workbench.ingredients.setItem(slot, new ItemStack(GCItems.ROCKET_FIN));
        }
        workbench.ingredients.setItem(13, new ItemStack(GCItems.ROCKET_ENGINE));
        workbench.ingredients.setChanged();

        if (!workbench.output.getItem(0).is(GCItems.ROCKET)) {
            context.fail("Expected a tier-1 rocket in the workbench result slot", workbenchPos);
            return;
        }

        Slot resultSlot = menu.slots.stream()
                .filter(slot -> slot.container == workbench.output)
                .findFirst()
                .orElseThrow();
        ItemStack crafted = resultSlot.remove(1);
        resultSlot.onTake(player, crafted);
        menu.removed(player);

        if (!crafted.is(GCItems.ROCKET)) {
            context.fail("Expected to take the crafted tier-1 rocket", workbenchPos);
        } else if (!workbench.ingredients.isEmpty()) {
            context.fail("Expected crafting to consume all tier-1 rocket parts", workbenchPos);
        } else {
            context.succeed();
        }
    }
}
