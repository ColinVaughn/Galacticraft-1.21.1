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

import dev.galacticraft.api.component.GCDataComponents;
import dev.galacticraft.mod.attachments.GCServerPlayer;
import dev.galacticraft.mod.content.GCBlocks;
import dev.galacticraft.mod.content.GCEntityTypes;
import dev.galacticraft.mod.content.block.special.launchpad.LaunchPadBlockEntity;
import dev.galacticraft.mod.content.entity.vehicle.RocketEntity;
import dev.galacticraft.mod.content.item.GCItems;
import dev.galacticraft.mod.content.rocket.RocketPrefabs;
import dev.galacticraft.mod.content.teleporters.DestinationArrival;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class RocketArrivalTestSuite implements GalacticraftGameTest {
    private static final long FUEL = 45_740;

    @GameTest(template = EMPTY_STRUCTURE)
    public void packedRocketCarriesItsFuelBackOntoAPad(GameTestHelper context) {
        RocketEntity flown = new RocketEntity(GCEntityTypes.ROCKET, context.getLevel());
        flown.setData(RocketPrefabs.TIER_1);
        flown.setFuel(FUEL);
        ItemStack item = flown.getDropItem();
        context.assertTrue(item.getOrDefault(GCDataComponents.ROCKET_FUEL, 0L) == FUEL, "the rocket item did not keep the fuel");
        context.assertFalse(flown.getPickResult().has(GCDataComponents.ROCKET_FUEL), "pick-block copied the fuel");

        BlockPos center = context.absolutePos(new BlockPos(2, 1, 2));
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
            context.getLevel().setBlockAndUpdate(center.offset(x, 0, z), GCBlocks.ROCKET_LAUNCH_PAD.defaultBlockState());
        }
        ServerPlayer player = context.makeMockServerPlayerInLevel();
        player.setItemInHand(InteractionHand.MAIN_HAND, item);
        item.useOn(new UseOnContext(context.getLevel(), player, InteractionHand.MAIN_HAND, item,
                new BlockHitResult(Vec3.atCenterOf(center), Direction.UP, center, false)));

        var pad = (LaunchPadBlockEntity) context.getLevel().getBlockEntity(center);
        context.assertTrue(pad != null && pad.getDockedEntity() instanceof RocketEntity, "the rocket was not placed on the pad");
        RocketEntity placed = (RocketEntity) pad.getDockedEntity();
        context.assertTrue(placed.getFuel() == FUEL, "the placed rocket has " + placed.getFuel() + " fuel, expected " + FUEL);
        placed.discard();
        context.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void stationArrivalHandsBackTheParkedRocketPadAndHold(GameTestHelper context) {
        ServerPlayer player = context.makeMockServerPlayerInLevel();
        GCServerPlayer stored = GCServerPlayer.get(player);
        // Cargo, then the launch pad and rocket slots.
        stored.setRocketStacks(NonNullList.of(ItemStack.EMPTY, new ItemStack(Items.DIAMOND, 5), ItemStack.EMPTY, ItemStack.EMPTY));
        stored.setLaunchpadStack(new ItemStack(GCBlocks.ROCKET_LAUNCH_PAD, 9));
        stored.finishReturnInventory(GCItems.ROCKET.getDefaultInstance());
        stored.setFuel(FUEL);

        DestinationArrival.returnParkedCargo(player);

        var inventory = player.getInventory();
        context.assertTrue(inventory.countItem(Items.DIAMOND) == 5, "the rocket's hold was not handed back");
        context.assertTrue(inventory.countItem(GCBlocks.ROCKET_LAUNCH_PAD.asItem()) == 9, "the launch pad was not handed back");
        ItemStack rocket = ItemStack.EMPTY;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) if (inventory.getItem(slot).is(GCItems.ROCKET)) rocket = inventory.getItem(slot);
        context.assertFalse(rocket.isEmpty(), "the rocket was not handed back");
        context.assertTrue(rocket.getOrDefault(GCDataComponents.ROCKET_FUEL, 0L) == FUEL, "the parked fuel did not go into the rocket");
        context.assertTrue(stored.getRocketStacks().stream().allMatch(ItemStack::isEmpty) && stored.getFuel() == 0,
                "the rocket is still parked, so the next launch would hand it out twice");
        context.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void handingBackKeepsTheFuelTheRocketItemAlreadyCarries(GameTestHelper context) {
        ServerPlayer player = context.makeMockServerPlayerInLevel();
        GCServerPlayer stored = GCServerPlayer.get(player);
        ItemStack rocket = GCItems.ROCKET.getDefaultInstance();
        rocket.set(GCDataComponents.ROCKET_FUEL, 1_000L);
        stored.setRocketStacks(NonNullList.withSize(GCServerPlayer.RESERVED_RETURN_STACKS, ItemStack.EMPTY));
        stored.finishReturnInventory(rocket);
        stored.setFuel(FUEL);

        DestinationArrival.returnParkedCargo(player);

        int slot = player.getInventory().findSlotMatchingItem(rocket);
        context.assertTrue(slot >= 0, "the rocket was not handed back unchanged");
        context.assertTrue(stored.getFuel() == FUEL, "fuel with no rocket left to hold it was thrown away");
        context.succeed();
    }
}
