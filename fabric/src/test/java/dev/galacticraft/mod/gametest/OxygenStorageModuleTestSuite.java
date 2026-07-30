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

import dev.galacticraft.api.gas.Gases;
import dev.galacticraft.mod.content.GCBlocks;
import dev.galacticraft.mod.content.block.entity.machine.OxygenStorageModuleBlockEntity;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * An oxygen storage module exists to hand its oxygen back out. If nothing can extract from any of
 * its sides, it is a black hole: it fills up and never feeds anything.
 *
 * <p>The module has no energy storage at all, which is what made this specific machine fail while
 * others limped along - the default face configuration was derived purely from a machine's energy
 * rates, so a machine with no energy got no configuration and every side stayed blank.
 */
public final class OxygenStorageModuleTestSuite implements GalacticraftGameTest {
    @GameTest(template = EMPTY_STRUCTURE)
    public void aStockedModuleCanBeDrainedFromOutside(GameTestHelper context) {
        BlockPos pos = new BlockPos(0, 1, 0);
        context.setBlock(pos, GCBlocks.OXYGEN_STORAGE_MODULE);

        OxygenStorageModuleBlockEntity module =
                (OxygenStorageModuleBlockEntity) context.getBlockEntity(pos);
        module.fluidStorage().slot(OxygenStorageModuleBlockEntity.OXYGEN_TANK)
                .insert(Gases.OXYGEN, OxygenStorageModuleBlockEntity.MAX_OXYGEN / 2);

        long stored = module.fluidStorage().slot(OxygenStorageModuleBlockEntity.OXYGEN_TANK).getAmount();
        if (stored <= 0) {
            context.fail("test setup failed to stock the module");
            return;
        }

        // Face defaults are applied on the machine's first tick, so nothing can be concluded from
        // the sides until it has had one.
        runAt(context, 3, () -> {
            BlockPos absolute = context.absolutePos(pos);
            StringBuilder seen = new StringBuilder();
            for (Direction direction : Direction.values()) {
                Storage<FluidVariant> storage =
                        FluidStorage.SIDED.find(context.getLevel(), absolute, direction);
                if (storage == null) {
                    seen.append(direction).append("=none ");
                    continue;
                }
                try (Transaction transaction = Transaction.openOuter()) {
                    long drawn = storage.extract(FluidVariant.of(Gases.OXYGEN), 1000L, transaction);
                    if (drawn > 0) {
                        context.succeed();
                        return;
                    }
                    seen.append(direction).append("=0 ");
                }
            }

            context.fail("no side of a stocked oxygen storage module would give up any oxygen: " + seen);
        });
    }
}
