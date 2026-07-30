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

import dev.galacticraft.api.accessor.GearInventoryProvider;
import dev.galacticraft.impl.internal.accessor.LivingEntityOxygenAccessor;
import dev.galacticraft.mod.content.GCAccessorySlots;
import dev.galacticraft.mod.content.item.GCItems;
import dev.galacticraft.mod.content.item.OxygenTankItem;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

/**
 * Creative mode is exempt from the survival hazards, and oxygen is no different: a creative player
 * takes no suffocation damage in vacuum, so spending their tank buys them nothing. The tank is an
 * item they keep, so draining it is a real loss.
 *
 * <p>These drive {@code galacticraft$tryUseOxygen} directly. It is the one place a player's tank is
 * ever extracted from, so guarding it covers every route into it — including the shoulder-parrot
 * check and anything added later — and the tests do not depend on which mixin happens to call it.
 */
public final class CreativeOxygenTestSuite implements GalacticraftGameTest {
    @GameTest(template = EMPTY_STRUCTURE)
    public void aCreativePlayerSpendsNoOxygen(GameTestHelper context) {
        ServerPlayer player = suitedPlayer(context);
        player.setGameMode(GameType.CREATIVE);
        Container gear = ((GearInventoryProvider) player).galacticraft$getGearInv();

        long before = tankAmount(gear);
        boolean breathed = ((LivingEntityOxygenAccessor) player).galacticraft$tryUseOxygen(rate());
        long after = tankAmount(gear);

        if (!breathed) {
            context.fail("a creative player should always count as breathing");
        } else if (after < before) {
            context.fail("a creative player's oxygen tank fell from " + before + " to " + after);
        } else {
            context.succeed();
        }
    }

    /**
     * The control. Without it, a setup that never reaches the extraction at all would make the
     * creative test pass for the wrong reason and prove nothing.
     */
    @GameTest(template = EMPTY_STRUCTURE)
    public void aSurvivalPlayerSpendsOxygen(GameTestHelper context) {
        ServerPlayer player = suitedPlayer(context);
        Container gear = ((GearInventoryProvider) player).galacticraft$getGearInv();

        long before = tankAmount(gear);
        boolean breathed = ((LivingEntityOxygenAccessor) player).galacticraft$tryUseOxygen(rate());
        long after = tankAmount(gear);

        if (!breathed) {
            context.fail("the survival control never drew from the tank; the test setup is wrong");
        } else if (after >= before) {
            context.fail("a survival player drew breath without spending oxygen (" + before + " -> " + after + ")");
        } else {
            context.succeed();
        }
    }

    /** A player wearing a mask, gear and a full medium tank. */
    private static ServerPlayer suitedPlayer(GameTestHelper context) {
        ServerPlayer player = new CreativeOxygenTestSuite().makeSurvivalServerPlayer(context);
        Container gear = ((GearInventoryProvider) player).galacticraft$getGearInv();
        gear.setItem(GCAccessorySlots.OXYGEN_MASK_SLOT, new ItemStack(GCItems.OXYGEN_MASK));
        gear.setItem(GCAccessorySlots.OXYGEN_GEAR_SLOT, new ItemStack(GCItems.OXYGEN_GEAR));
        gear.setItem(GCAccessorySlots.OXYGEN_TANK_1_SLOT, OxygenTankItem.getFullTank(GCItems.MEDIUM_OXYGEN_TANK));
        return player;
    }

    private static long rate() {
        return dev.galacticraft.mod.Galacticraft.CONFIG.playerOxygenConsumptionRate();
    }

    private static long tankAmount(Container gear) {
        ItemStack tank = gear.getItem(GCAccessorySlots.OXYGEN_TANK_1_SLOT);
        return tank.getItem() instanceof OxygenTankItem ? OxygenTankItem.getStorage(tank).getAmount() : 0L;
    }
}
