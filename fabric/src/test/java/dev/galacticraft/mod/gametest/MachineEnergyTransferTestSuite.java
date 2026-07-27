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

import dev.galacticraft.mod.content.GCBlocks;
import dev.galacticraft.mod.content.block.entity.machine.CoalGeneratorBlockEntity;
import dev.galacticraft.mod.content.block.entity.machine.EnergyStorageClusterBlockEntity;
import dev.galacticraft.mod.content.block.entity.machine.OxygenSealerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import team.reborn.energy.api.EnergyStorage;

/**
 * A machine is only reachable through the faces its configuration exposes, and every face starts blank.
 * Left that way nothing carries power to or from a machine anybody has just placed - no wire will even
 * connect to it - so these pin the defaults that make a freshly placed machine work.
 *
 * @see dev.galacticraft.mod.machine.MachineFaceDefaults
 */
public final class MachineEnergyTransferTestSuite implements GalacticraftGameTest {
    private static final BlockPos SOURCE = new BlockPos(0, 1, 0);
    private static final BlockPos MIDDLE = new BlockPos(1, 1, 0);
    private static final BlockPos SINK = new BlockPos(2, 1, 0);
    /** Enough ticks for both machines to tick and push a transfer through. */
    private static final int SETTLE_TICKS = 6;

    /** The lookup a wire uses to decide what it can connect to and feed. */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
    public void aFreshlyPlacedMachineIsReachable(GameTestHelper context) {
        context.setBlock(SOURCE, GCBlocks.OXYGEN_SEALER);

        runAt(context, SETTLE_TICKS, () -> {
            for (Direction direction : Direction.values()) {
                EnergyStorage exposed = EnergyStorage.SIDED.find(context.getLevel(), context.absolutePos(SOURCE), direction);
                if (exposed == null) {
                    context.fail("nothing can reach the sealer's " + direction + " face, so no wire will feed it");
                    return;
                }
                if (!exposed.supportsInsertion()) {
                    context.fail("the sealer's " + direction + " face will not take power");
                    return;
                }
            }
            context.succeed();
        });
    }

    /** A generator has to give power away, not take it. */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
    public void aGeneratorOffersPowerRatherThanTakingIt(GameTestHelper context) {
        context.setBlock(SOURCE, GCBlocks.COAL_GENERATOR);

        runAt(context, SETTLE_TICKS, () -> {
            EnergyStorage exposed = EnergyStorage.SIDED.find(context.getLevel(), context.absolutePos(SOURCE), Direction.NORTH);
            if (exposed == null) {
                context.fail("nothing can reach the coal generator");
            } else if (!exposed.supportsExtraction()) {
                context.fail("the coal generator will not give its power away");
            } else if (exposed.supportsInsertion()) {
                context.fail("the coal generator should not accept power back");
            } else {
                context.succeed();
            }
        });
    }

    /**
     * The reported case: a storage cluster standing between a power source and a machine has to pass
     * power along, rather than the source needing a direct line to the machine.
     */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
    public void aStorageClusterPassesPowerOnToTheMachineBesideIt(GameTestHelper context) {
        context.setBlock(MIDDLE, GCBlocks.ENERGY_STORAGE_CLUSTER);
        context.setBlock(SINK, GCBlocks.OXYGEN_SEALER);
        EnergyStorageClusterBlockEntity cluster = context.getBlockEntity(MIDDLE);
        OxygenSealerBlockEntity sealer = context.getBlockEntity(SINK);
        cluster.energyStorage().setEnergy(cluster.energyStorage().getCapacity());

        runAt(context, SETTLE_TICKS, () -> {
            if (sealer.energyStorage().isEmpty()) {
                context.fail("the sealer beside a full storage cluster received no power");
            } else {
                context.succeed();
            }
        });
    }

    /** And the whole chain: generator into the cluster, cluster into the machine. */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
    public void powerFlowsAlongAChainOfMachines(GameTestHelper context) {
        context.setBlock(SOURCE, GCBlocks.COAL_GENERATOR);
        context.setBlock(MIDDLE, GCBlocks.ENERGY_STORAGE_CLUSTER);
        context.setBlock(SINK, GCBlocks.OXYGEN_SEALER);
        CoalGeneratorBlockEntity generator = context.getBlockEntity(SOURCE);
        EnergyStorageClusterBlockEntity cluster = context.getBlockEntity(MIDDLE);
        OxygenSealerBlockEntity sealer = context.getBlockEntity(SINK);
        generator.energyStorage().setEnergy(generator.energyStorage().getCapacity());

        runAt(context, SETTLE_TICKS * 3, () -> {
            if (cluster.energyStorage().isEmpty()) {
                context.fail("the storage cluster took no power from the generator beside it");
            } else if (sealer.energyStorage().isEmpty()) {
                context.fail("power reached the cluster but stopped there");
            } else {
                context.succeed();
            }
        });
    }

    /** The arrangement the report says does work, kept as a control on the ones above. */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
    public void powerFlowsAlongAWire(GameTestHelper context) {
        context.setBlock(SOURCE, GCBlocks.COAL_GENERATOR);
        context.setBlock(MIDDLE, GCBlocks.ALUMINUM_WIRE);
        context.setBlock(SINK, GCBlocks.OXYGEN_SEALER);
        CoalGeneratorBlockEntity generator = context.getBlockEntity(SOURCE);
        OxygenSealerBlockEntity sealer = context.getBlockEntity(SINK);
        generator.energyStorage().setEnergy(generator.energyStorage().getCapacity());

        runAt(context, SETTLE_TICKS * 3, () -> {
            if (sealer.energyStorage().isEmpty()) {
                context.fail("no power crossed the wire between the generator and the sealer");
            } else {
                context.succeed();
            }
        });
    }
}
