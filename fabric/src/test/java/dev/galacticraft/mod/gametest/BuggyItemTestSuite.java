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
import dev.galacticraft.mod.content.GCEntityTypes;
import dev.galacticraft.mod.content.entity.vehicle.Buggy;
import dev.galacticraft.mod.content.item.GCCreativeModeTabs;
import dev.galacticraft.mod.content.item.GCItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * The buggy item is the only way to put a buggy in the world, so if it is not in a creative tab the
 * vehicle may as well not exist - which is exactly how it was reported.
 */
public final class BuggyItemTestSuite implements GalacticraftGameTest {
    private static final BlockPos GROUND = new BlockPos(1, 1, 1);

    /** The reported case: the buggy could be crafted for but never found in creative. */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
    public void theBuggyIsInACreativeTab(GameTestHelper context) {
        CreativeModeTab tab = GCCreativeModeTabs.ITEMS_GROUP;
        tab.buildContents(new CreativeModeTab.ItemDisplayParameters(
                context.getLevel().enabledFeatures(), true, context.getLevel().registryAccess()));

        List<ItemStack> buggies = tab.getDisplayItems().stream().filter(stack -> stack.is(GCItems.BUGGY)).toList();
        if (buggies.isEmpty()) {
            context.fail("the buggy is in no creative tab, so it cannot be obtained without commands");
        } else if (buggies.size() != Buggy.BuggyType.values().length) {
            context.fail("creative offers " + buggies.size() + " of the " + Buggy.BuggyType.values().length
                    + " buggy cargo fittings; the fitting is a component, so each one needs its own entry");
        } else {
            context.succeed();
        }
    }

    /** And having found it, it has to actually put a buggy down. */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
    public void placingTheBuggyItemSpawnsTheVehicle(GameTestHelper context) {
        context.setBlock(GROUND, Blocks.STONE);
        ItemStack stack = new ItemStack(GCItems.BUGGY);
        stack.set(GCDataComponents.BUGGY_TYPE, Buggy.BuggyType.STORAGE_18.getId());

        BlockPos clicked = context.absolutePos(GROUND);
        GCItems.BUGGY.useOn(new UseOnContext(context.getLevel(), null, InteractionHand.MAIN_HAND, stack,
                new BlockHitResult(Vec3.atCenterOf(clicked), Direction.UP, clicked, false)));

        runNext(context, () -> {
            List<Buggy> spawned = context.getEntities(GCEntityTypes.BUGGY);
            if (spawned.isEmpty()) {
                context.fail("using the buggy item placed no buggy");
            } else if (spawned.getFirst().getVariant() != Buggy.BuggyType.STORAGE_18) {
                context.fail("the placed buggy lost the cargo fitting the item carried");
            } else {
                spawned.forEach(buggy -> buggy.discard());
                context.succeed();
            }
        });
    }
}
