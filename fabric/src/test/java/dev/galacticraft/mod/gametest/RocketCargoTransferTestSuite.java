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

import dev.galacticraft.mod.content.GCBlocks;
import dev.galacticraft.mod.content.GCEntityTypes;
import dev.galacticraft.mod.content.block.entity.ParachestBlockEntity;
import dev.galacticraft.mod.content.entity.ParachestEntity;
import dev.galacticraft.mod.content.entity.vehicle.RocketCargoLogic;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * A rocket's storage upgrade grants up to three chests of cargo, and all of it has to survive the
 * trip down - through the lander on a landable body, or through the parachest on one that is not.
 */
public final class RocketCargoTransferTestSuite implements GalacticraftGameTest {
    /** Cargo for the largest rocket, plus the two slots holding the rocket and its launch pad. */
    private static final int THREE_CHEST_CARGO = 3 * RocketCargoLogic.SLOTS_PER_CHEST;
    private static final int TRANSFERRED_STACKS = THREE_CHEST_CARGO + 2;

    private static final BlockPos CHEST_POS = new BlockPos(1, 1, 1);

    /**
     * The falling parachest is only a courier - what matters is that the chest it leaves behind is
     * big enough for everything it was carrying.
     */
    @GameTest(template = EMPTY_STRUCTURE)
    public void parachestPlacesAChestBigEnoughForAFullRocketHold(GameTestHelper context) {
        ServerLevel level = context.getLevel();
        ParachestBlockEntity chest = dropParachest(context, level, transferredCargo());

        int expected = TRANSFERRED_STACKS + 1; // the parachest adds its own fuel container slot
        if (chest.getContainerSize() != expected) {
            context.fail("parachest sized " + chest.getContainerSize() + ", expected " + expected, CHEST_POS);
        } else if (!chest.getItem(0).is(Items.DIAMOND)) {
            context.fail("the first cargo slot did not arrive", CHEST_POS);
        } else if (!chest.getItem(THREE_CHEST_CARGO - 1).is(Items.EMERALD)) {
            context.fail("the last cargo slot did not arrive", CHEST_POS);
        } else {
            context.succeed();
        }
    }

    /**
     * The reason this is worth a test: the block entity used to save its items and never read them
     * back, so a parachest full of cargo emptied itself the next time the world loaded.
     */
    @GameTest(template = EMPTY_STRUCTURE)
    public void parachestKeepsItsCargoAcrossAReload(GameTestHelper context) {
        ServerLevel level = context.getLevel();
        ParachestBlockEntity chest = dropParachest(context, level, transferredCargo());

        int size = chest.getContainerSize();
        CompoundTag saved = chest.saveWithFullMetadata(level.registryAccess());
        chest.loadWithComponents(saved, level.registryAccess());

        if (chest.getContainerSize() != size) {
            context.fail("parachest shrank from " + size + " to " + chest.getContainerSize() + " on reload", CHEST_POS);
        } else if (!chest.getItem(0).is(Items.DIAMOND)) {
            context.fail("the first cargo slot was wiped on reload", CHEST_POS);
        } else if (!chest.getItem(THREE_CHEST_CARGO - 1).is(Items.EMERALD)) {
            context.fail("the last cargo slot was wiped on reload", CHEST_POS);
        } else if (!chest.getItem(size - ParachestBlockEntity.NON_CARGO_SLOTS).is(GCBlocks.ROCKET_LAUNCH_PAD.asItem())) {
            // ParachestMenu draws the last three slots as launch pad, rocket, fuel - so the pad the
            // rocket sent down has to be the first of them.
            context.fail("the launch pad slot was wiped on reload", CHEST_POS);
        } else {
            context.succeed();
        }
    }

    /**
     * ParachestMenu lays the container out as whole rows of nine plus three fixed slots, so a hold
     * carried down from any rocket has to land on that shape or slots would go unreachable.
     */
    @GameTest(template = EMPTY_STRUCTURE)
    public void everyRocketHoldSizeFitsTheParachestLayout(GameTestHelper context) {
        for (int chests = 0; chests <= 3; ++chests) {
            int transferred = RocketCargoLogic.storageSlots(chests, 3) + 2;
            int containerSize = transferred + 1;
            int cargo = containerSize - ParachestBlockEntity.NON_CARGO_SLOTS;
            if (cargo % RocketCargoLogic.SLOTS_PER_ROW != 0) {
                context.fail(chests + " chests gives a " + containerSize
                        + "-slot parachest, leaving " + cargo + " cargo slots which is not whole rows");
                return;
            }
        }
        context.succeed();
    }

    private ParachestBlockEntity dropParachest(GameTestHelper context, ServerLevel level, NonNullList<ItemStack> cargo) {
        context.setBlock(CHEST_POS.below(), net.minecraft.world.level.block.Blocks.STONE);
        BlockPos absolute = context.absolutePos(CHEST_POS);

        ParachestEntity parachest = new ParachestEntity(
                GCEntityTypes.PARACHEST, level, cargo, DyeColor.WHITE, 0L);
        parachest.setPos(absolute.getX() + 0.5, absolute.getY(), absolute.getZ() + 0.5);
        level.addFreshEntity(parachest);

        // Landing is what places the chest, and it is the entity's own code that decides the size,
        // so drive the real thing rather than reproducing it here.
        parachest.setOnGround(true);
        parachest.tick();

        if (!(level.getBlockEntity(absolute) instanceof ParachestBlockEntity chest)) {
            throw new AssertionError("the parachest did not leave a chest behind at " + absolute);
        }
        return chest;
    }

    /** What a three-chest rocket hands over: cargo, then the launch pad and the rocket itself. */
    private static NonNullList<ItemStack> transferredCargo() {
        NonNullList<ItemStack> stacks = NonNullList.withSize(TRANSFERRED_STACKS, ItemStack.EMPTY);
        stacks.set(0, new ItemStack(Items.DIAMOND, 64));
        stacks.set(THREE_CHEST_CARGO - 1, new ItemStack(Items.EMERALD, 12));
        stacks.set(TRANSFERRED_STACKS - 2, new ItemStack(GCBlocks.ROCKET_LAUNCH_PAD, 9));
        return stacks;
    }
}
