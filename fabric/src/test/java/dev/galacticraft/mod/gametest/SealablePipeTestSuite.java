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
import dev.galacticraft.machinelib.api.transfer.ResourceFlow;
import dev.galacticraft.machinelib.api.transfer.ResourceType;
import dev.galacticraft.machinelib.api.util.BlockFace;
import dev.galacticraft.mod.api.block.entity.PipeColor;
import dev.galacticraft.mod.content.GCBlocks;
import dev.galacticraft.mod.content.block.entity.machine.FluidTankBlockEntity;
import dev.galacticraft.mod.content.block.entity.machine.OxygenSealerBlockEntity;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;

import java.util.function.BooleanSupplier;

/**
 * A sealable fluid pipe is a pipe you can build into the wall of a sealed room, the fluid counterpart of
 * the sealable aluminum wire. Without one there is no way to get fluid through a wall without opening it,
 * which is what was reported.
 *
 * @see dev.galacticraft.mod.content.block.special.fluidpipe.SealableGlassFluidPipeBlock
 */
public final class SealablePipeTestSuite implements GalacticraftGameTest {
    /** Sealing needs the whole level held in vacuum, and each sealing test disturbs the others. */
    private static final String SEALED_BATCH = "galacticraft:sealable_pipe_sealed";
    private static final String LEAK_BATCH = "galacticraft:sealable_pipe_leak";
    private static boolean levelWasBreathable = true;

    private static final int ROOM_SIZE = 5;
    private static final BlockPos SEALER = new BlockPos(1, 1, 1);
    /** An interior position the sealer does not occupy. */
    private static final BlockPos INTERIOR = new BlockPos(2, 2, 2);
    /**
     * The block of the room's shell replaced by the pipe under test - in the ceiling, deliberately.
     *
     * A gametest plot is not open on every side: the framework walls it in with {@code minecraft:barrier},
     * and it stands on solid ground. Both of those are sturdy, so a gap in a side wall or in the floor
     * opens onto something the sealer reads as another wall, and the room stays sealed whatever is in the
     * gap - the test would pass for a bare pipe too. Only upwards is genuinely open, which is also why a
     * sealer placed in the open never seals.
     */
    private static final BlockPos WALL = new BlockPos(2, ROOM_SIZE - 1, 2);
    private static final int SETTLE_TICKS = 10;
    private static final int DEADLINE_TICKS = 100;

    @BeforeBatch(batch = SEALED_BATCH)
    public void emptyTheAirToSeal(ServerLevel level) {
        vacuum(level);
    }

    @AfterBatch(batch = SEALED_BATCH)
    public void putTheAirBackAfterSealing(ServerLevel level) {
        restoreAir(level);
    }

    @BeforeBatch(batch = LEAK_BATCH)
    public void emptyTheAirToLeak(ServerLevel level) {
        vacuum(level);
    }

    @AfterBatch(batch = LEAK_BATCH)
    public void putTheAirBackAfterLeaking(ServerLevel level) {
        restoreAir(level);
    }

    private static void vacuum(ServerLevel level) {
        levelWasBreathable = level.getDefaultBreathable();
        level.setDefaultBreathable(false);
    }

    private static void restoreAir(ServerLevel level) {
        level.setDefaultBreathable(levelWasBreathable);
    }

    /** The reported need: run a fluid line through a wall without breaking the seal. */
    @GameTest(template = EMPTY_STRUCTURE, batch = SEALED_BATCH, timeoutTicks = 200)
    public void aSealablePipeInTheWallKeepsTheRoomSealed(GameTestHelper context) {
        buildRoom(context);
        context.setBlock(WALL, GCBlocks.SEALABLE_GLASS_FLUID_PIPE);
        OxygenSealerBlockEntity sealer = placeFuelledSealer(context);

        await(context, () -> sealer.isSealed() && context.getLevel().isBreathable(context.absolutePos(INTERIOR)),
                "a sealable pipe in the wall did not hold the seal", () -> context.succeed());
    }

    /**
     * The control. A bare pipe is a thin tube, so the room leaks through it - which is the whole reason a
     * sealable one has to exist. Without this, the test above would pass just as well if sealers ignored
     * walls entirely.
     */
    @GameTest(template = EMPTY_STRUCTURE, batch = LEAK_BATCH, timeoutTicks = 200)
    public void aBarePipeInTheWallLetsTheAirOut(GameTestHelper context) {
        buildRoom(context);
        context.setBlock(WALL, GCBlocks.GLASS_FLUID_PIPE);
        OxygenSealerBlockEntity sealer = placeFuelledSealer(context);

        runAt(context, SETTLE_TICKS, () -> {
            if (sealer.isSealed()) {
                context.fail("a bare glass fluid pipe in the wall sealed the room, so the sealable one proves nothing");
            } else if (context.getLevel().isBreathable(context.absolutePos(INTERIOR))) {
                context.fail("the room was breathable despite leaking through a bare pipe");
            } else {
                context.succeed();
            }
        });
    }

    /** Being a wall is only half of it - it still has to carry fluid, or it is just a decorated block. */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
    public void aSealablePipeCarriesFluidBetweenTanks(GameTestHelper context) {
        BlockPos source = new BlockPos(1, 4, 1);
        BlockPos target = new BlockPos(1, 1, 1);
        context.setBlock(source, GCBlocks.FLUID_TANK);
        context.setBlock(new BlockPos(1, 3, 1), GCBlocks.SEALABLE_GLASS_FLUID_PIPE);
        context.setBlock(new BlockPos(1, 2, 1), GCBlocks.SEALABLE_GLASS_FLUID_PIPE);
        context.setBlock(target, GCBlocks.FLUID_TANK);

        FluidTankBlockEntity from = context.getBlockEntity(source);
        FluidTankBlockEntity to = context.getBlockEntity(target);
        from.fluidStorage().slot(FluidTankBlockEntity.FLUID_TANK).set(Fluids.WATER, FluidConstants.BUCKET);
        from.getIOConfig().get(BlockFace.BOTTOM).setOption(ResourceType.FLUID, ResourceFlow.OUTPUT);
        to.getIOConfig().get(BlockFace.TOP).setOption(ResourceType.FLUID, ResourceFlow.INPUT);

        await(context, () -> to.fluidStorage().slot(FluidTankBlockEntity.FLUID_TANK).getAmount() > 0,
                "no water crossed a pair of sealable pipes", () -> context.succeed());
    }

    /**
     * Every colour has to be a full block, since that is the only thing making it seal. A colour that
     * slipped back to the bare pipe's thickness would leak while looking identical in the inventory.
     */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
    public void everySealablePipeColourIsSolidOnEveryFace(GameTestHelper context) {
        BlockPos pos = new BlockPos(1, 1, 1);
        for (PipeColor color : PipeColor.values()) {
            Block pipe = GCBlocks.SEALABLE_GLASS_FLUID_PIPES.get(color);
            if (pipe == null) {
                context.fail("no sealable pipe exists for the " + color.getName() + " line");
                return;
            }
            context.setBlock(pos, pipe);
            for (Direction direction : Direction.values()) {
                if (!context.getBlockState(pos).isFaceSturdy(context.getLevel(), context.absolutePos(pos), direction)) {
                    context.fail("the " + color.getName() + " sealable pipe is not solid on its " + direction
                            + " face, so a wall built from it would leak");
                    return;
                }
            }
        }
        context.succeed();
    }

    private static void buildRoom(GameTestHelper context) {
        for (int x = 0; x < ROOM_SIZE; x++) {
            for (int y = 0; y < ROOM_SIZE; y++) {
                for (int z = 0; z < ROOM_SIZE; z++) {
                    boolean shell = x == 0 || y == 0 || z == 0
                            || x == ROOM_SIZE - 1 || y == ROOM_SIZE - 1 || z == ROOM_SIZE - 1;
                    context.setBlock(new BlockPos(x, y, z), shell ? Blocks.STONE : Blocks.AIR);
                }
            }
        }
    }

    private static OxygenSealerBlockEntity placeFuelledSealer(GameTestHelper context) {
        context.setBlock(SealablePipeTestSuite.SEALER, GCBlocks.OXYGEN_SEALER);
        OxygenSealerBlockEntity sealer = context.getBlockEntity(SealablePipeTestSuite.SEALER);
        sealer.energyStorage().setEnergy(sealer.energyStorage().getCapacity());
        sealer.fluidStorage().slot(OxygenSealerBlockEntity.OXYGEN_TANK)
                .set(Gases.OXYGEN, OxygenSealerBlockEntity.MAX_OXYGEN);
        return sealer;
    }

    /** Waits for a condition that must become true; see the note in {@link OxygenSealerTestSuite}. */
    private void await(GameTestHelper context, BooleanSupplier ready, String timedOut, Runnable then) {
        await(context, DEADLINE_TICKS, ready, timedOut, then);
    }

    private void await(GameTestHelper context, int ticksLeft, BooleanSupplier ready, String timedOut, Runnable then) {
        if (ready.getAsBoolean()) {
            then.run();
        } else if (ticksLeft <= 0) {
            context.fail(timedOut);
        } else {
            runNext(context, () -> await(context, ticksLeft - 1, ready, timedOut, then));
        }
    }
}
