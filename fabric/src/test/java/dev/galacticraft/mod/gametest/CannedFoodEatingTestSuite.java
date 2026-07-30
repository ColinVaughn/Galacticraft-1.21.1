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

package dev.galacticraft.mod.gametest;

import dev.galacticraft.mod.content.item.CannedFoodItem;
import dev.galacticraft.mod.content.item.GCItems;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Eating a can has to actually feed the player.
 *
 * <p>A can carries no nutrition of its own: the item is registered with a zero-nutrition
 * placeholder and the real value is derived from its contents. That derivation lived only in a
 * client-side mixin, so the numbers on the tooltip were right while the server, which is what
 * actually feeds the player, saw zero. The can emptied, the eating animation played, and hunger
 * never moved.
 */
public final class CannedFoodEatingTestSuite implements GalacticraftGameTest {
    @GameTest(template = EMPTY_STRUCTURE)
    public void eatingACanRestoresHunger(GameTestHelper context) {
        ServerPlayer player = makeSurvivalServerPlayer(context);
        player.getFoodData().setFoodLevel(6);

        ItemStack can = GCItems.CANNED_FOOD.getDefaultInstance();
        CannedFoodItem.add(can, new ItemStack(Items.COOKED_BEEF, 4));

        can.getItem().finishUsingItem(can, context.getLevel(), player);

        int food = player.getFoodData().getFoodLevel();
        if (food <= 6) {
            context.fail("eating a can of cooked beef left hunger at " + food + " of 20");
        } else {
            context.succeed();
        }
    }
}
