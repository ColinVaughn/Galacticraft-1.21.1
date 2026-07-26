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

import dev.galacticraft.mod.content.item.GCItems;
import dev.galacticraft.mod.content.item.ParachuteLogic;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * The scenario from the bug report: someone coming down from orbit with a parachute equipped used to hit
 * the ground at terminal velocity and die, because nothing ever slowed the descent.
 *
 * <p>These tests fall a wolf rather than a player. Not by preference - the game test harness builds mock
 * players on a connection the server never ticks, so a mock player does not fall at all (it holds its
 * height with a fall distance of zero however long you wait) and cannot exercise fall code. A wolf is
 * ticked normally and runs the identical parachute path in {@code LivingEntityMixin}, so it is the only
 * subject here that actually falls. The parachute has to be pushed straight into its gear inventory,
 * since pets have no accessory slots to equip one into.
 */
public final class ParachuteTestSuite implements GalacticraftGameTest {
    /** Far enough to be lethal without a parachute, short enough to land inside the test timeout. */
    private static final int DROP_HEIGHT = 25;

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
    public void anOpenParachuteSetsItsWearerDownUnhurt(GameTestHelper context) {
        Wolf wolf = drop(context, true);

        context.succeedWhen(() -> {
            if (wolf.getHealth() < wolf.getMaxHealth()) {
                context.fail("landed on " + wolf.getHealth() + " of " + wolf.getMaxHealth() + " health");
            }
            if (!wolf.onGround()) {
                context.fail("still in the air at y=" + wolf.getY() + ", canopy open: " + wolf.galacticraft$isParachuteOpen());
            }
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
    public void anOpenParachuteCapsHowFastItsWearerSinks(GameTestHelper context) {
        Wolf wolf = drop(context, true);

        // Well past the deploy threshold by now, and still a long way above the ground.
        runFinalTaskAt(context, 12, () -> {
            if (!wolf.galacticraft$isParachuteOpen()) {
                context.fail("the canopy never opened, y=" + wolf.getY() + ", fall distance " + wolf.fallDistance);
            }
            double descent = -wolf.getDeltaMovement().y;
            // A tick of gravity lands on top of the cap before anything reads it back, so allow for that.
            if (descent > ParachuteLogic.DESCENT_SPEED + 0.1D) {
                context.fail("sinking at " + descent + " blocks per tick, expected at most " + ParachuteLogic.DESCENT_SPEED);
            }
        });
    }

    /**
     * Guards the tests above. If an unprotected drop from the same height were survivable, they would
     * pass whether or not the parachute did anything.
     */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
    public void withoutAParachuteTheSameDropHurts(GameTestHelper context) {
        Wolf wolf = drop(context, false);

        context.succeedWhen(() -> {
            if (wolf.isAlive() && wolf.getHealth() >= wolf.getMaxHealth()) {
                context.fail("a " + DROP_HEIGHT + " block drop did no harm, y=" + wolf.getY());
            }
        });
    }

    /** Puts a wolf {@link #DROP_HEIGHT} blocks above a fresh stone pad and lets go. */
    private static Wolf drop(GameTestHelper context, boolean withParachute) {
        BlockPos pad = new BlockPos(0, 1, 0);
        context.setBlock(pad, Blocks.STONE);

        // No free will, so the fall is gravity and nothing else.
        Wolf wolf = context.spawnWithNoFreeWill(EntityType.WOLF, pad.above(DROP_HEIGHT + 1));
        if (withParachute) {
            wolf.galacticraft$getAccessories().setItem(0, new ItemStack(GCItems.PARACHUTE));
        }
        wolf.setDeltaMovement(Vec3.ZERO);
        wolf.resetFallDistance();
        return wolf;
    }
}
