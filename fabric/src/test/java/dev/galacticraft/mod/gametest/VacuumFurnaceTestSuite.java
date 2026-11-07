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

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;

/**
 * Checks that furnaces, blast furnaces, and smokers work in vacuum, matching Galacticraft Legacy.
 * The airless batch disables the level's default breathability.
 */
public final class VacuumFurnaceTestSuite implements GalacticraftGameTest {
    private static final String AIRLESS = "galacticraft:vacuum_furnace";
    private static final BlockPos FURNACE = new BlockPos(1, 1, 1);
    /** Long enough for a furnace that lit and then went out again to show it. */
    private static final int BURN_WINDOW = 20;

    private static boolean levelWasBreathable = true;

    @BeforeBatch(batch = AIRLESS)
    public void pumpTheAirOut(ServerLevel level) {
        levelWasBreathable = level.getDefaultBreathable();
        level.setDefaultBreathable(false);
    }

    @AfterBatch(batch = AIRLESS)
    public void letTheAirBackIn(ServerLevel level) {
        level.setDefaultBreathable(levelWasBreathable);
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = AIRLESS, timeoutTicks = BURN_WINDOW + 20)
    public void aFurnaceSmeltsWithoutOxygen(GameTestHelper context) {
        smeltsWithoutOxygen(context, Blocks.FURNACE, Items.RAW_IRON);
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = AIRLESS, timeoutTicks = BURN_WINDOW + 20)
    public void aBlastFurnaceSmeltsWithoutOxygen(GameTestHelper context) {
        smeltsWithoutOxygen(context, Blocks.BLAST_FURNACE, Items.RAW_IRON);
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = AIRLESS, timeoutTicks = BURN_WINDOW + 20)
    public void aSmokerCooksWithoutOxygen(GameTestHelper context) {
        smeltsWithoutOxygen(context, Blocks.SMOKER, Items.BEEF);
    }

    private static void smeltsWithoutOxygen(GameTestHelper context, Block block, Item input) {
        context.setBlock(FURNACE, block);
        AbstractFurnaceBlockEntity furnace = context.getBlockEntity(FURNACE);
        furnace.setItem(0, new ItemStack(input, 4));
        furnace.setItem(1, new ItemStack(Items.COAL));
        context.runAtTickTime(BURN_WINDOW, () -> {
            if (!furnace.getItem(1).isEmpty()) {
                context.fail(block.getName().getString() + " did not burn its fuel with no oxygen around it");
            } else if (!context.getBlockState(FURNACE).getValue(AbstractFurnaceBlock.LIT)) {
                context.fail(block.getName().getString() + " went out with no oxygen around it");
            } else {
                context.succeed();
            }
        });
    }
}
