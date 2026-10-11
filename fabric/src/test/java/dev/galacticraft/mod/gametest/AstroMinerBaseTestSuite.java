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
import dev.galacticraft.mod.content.GCEntityTypes;
import dev.galacticraft.mod.content.block.entity.machine.AstroMinerBaseBlockEntity;
import dev.galacticraft.mod.content.block.special.AstroMinerBaseBlock;
import dev.galacticraft.mod.content.entity.vehicle.AstroMinerEntity;
import dev.galacticraft.mod.content.item.GCItems;
import dev.galacticraft.mod.screen.AstroMinerBaseMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Regression coverage for legacy Astro Miner Base slave-to-master interaction delegation. */
public final class AstroMinerBaseTestSuite implements GalacticraftGameTest {
    private static final BlockPos MASTER = new BlockPos(1, 1, 1);

    @GameTest(template = EMPTY_STRUCTURE)
    public void everyAssembledPartOpensTheMasterMenu(GameTestHelper context) {
        AstroMinerBaseBlockEntity base = buildBase(context);
        ServerPlayer player = makeSurvivalServerPlayer(context);

        forEachPart(context, partPos -> {
            BlockHitResult hit = hit(context, partPos);
            InteractionResult result = context.getBlockState(partPos).useWithoutItem(context.getLevel(), player, hit);

            if (!result.consumesAction()) {
                context.fail("Astro Miner Base part " + partPos + " did not consume its GUI interaction");
            }
            if (!(player.containerMenu instanceof AstroMinerBaseMenu)) {
                context.fail("Astro Miner Base part " + partPos + " did not open the base menu");
            }
            AstroMinerBaseMenu menu = (AstroMinerBaseMenu) player.containerMenu;
            if (menu.getContainer() != base.getHold()) {
                context.fail("Astro Miner Base part " + partPos + " opened a container other than the master's");
            }
            player.closeContainer();
        });

        context.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void astroMinerItemDeploysThroughEveryAssembledPart(GameTestHelper context) {
        AstroMinerBaseBlockEntity base = buildBase(context);
        ServerPlayer player = makeSurvivalServerPlayer(context);

        forEachPart(context, partPos -> {
            BlockState state = context.getBlockState(partPos);
            BlockHitResult hit = hit(context, partPos);
            ItemStack stack = new ItemStack(GCItems.ASTRO_MINER);

            ItemInteractionResult blockResult = state.useItemOn(stack, context.getLevel(), player,
                    InteractionHand.MAIN_HAND, hit);
            if (blockResult != ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION) {
                context.fail("Astro Miner item did not take priority over the base GUI at " + partPos);
            }

            InteractionResult itemResult = stack.useOn(new UseOnContext(context.getLevel(), player,
                    InteractionHand.MAIN_HAND, stack, hit));
            List<AstroMinerEntity> miners = context.getEntities(GCEntityTypes.ASTRO_MINER);
            if (!itemResult.consumesAction() || miners.size() != 1 || !base.hasLinkedMiner()) {
                context.fail("Astro Miner item did not deploy through base part " + partPos);
            }

            miners.forEach(AstroMinerEntity::discard);
            base.setLinkedMiner(null);
        });

        context.succeed();
    }

    private static AstroMinerBaseBlockEntity buildBase(GameTestHelper context) {
        forEachPart(context, partPos -> context.setBlock(partPos, GCBlocks.ASTRO_MINER_BASE));
        BlockPos master = context.absolutePos(MASTER);
        BlockState masterState = context.getLevel().getBlockState(master);
        AstroMinerBaseBlockEntity base = AstroMinerBaseBlock.getMasterBlockEntity(
                context.getLevel(), master, masterState);
        if (base == null) {
            context.fail("eight Astro Miner Base blocks did not assemble around " + MASTER);
        }
        return base;
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void deployedSavedAndReturningMinersUseTheSameDockInEveryFacing(GameTestHelper context) {
        AstroMinerBaseBlockEntity base = buildBase(context);
        BlockPos master = base.getBlockPos();
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            base.setFacing(facing);
            Vec3 expected = new Vec3(master.getX() + 1 + facing.getStepX(), master.getY(),
                    master.getZ() + 1 + facing.getStepZ());
            AstroMinerEntity miner = new AstroMinerEntity(GCEntityTypes.ASTRO_MINER, context.getLevel());
            miner.prepareDeploy(master, facing, java.util.UUID.randomUUID());
            assertDock(context, miner, expected, facing, "deployment");

            // Load the old off-center, sunken parked position to exercise existing saves.
            miner.setPos(expected.add(-0.5, -1, -0.5));
            CompoundTag saved = miner.saveWithoutId(new CompoundTag());
            miner.load(saved);
            miner.tick();
            assertDock(context, miner, expected, facing, "saved miner");

            miner.setPos(expected.add(facing.getStepX() * 2, 1, facing.getStepZ() * 2));
            saved = miner.saveWithoutId(new CompoundTag());
            saved.putInt("AIState", AstroMinerEntity.AISTATE_RETURNING);
            saved.putInt("Energy", 4000);
            ListTag route = new ListTag();
            route.add(LongTag.valueOf(master.relative(facing).asLong()));
            saved.put("WayPoints", route);
            miner.load(saved);
            for (int tick = 0; tick < 600 && miner.getAiState() != AstroMinerEntity.AISTATE_ATBASE; tick++) {
                miner.tick();
            }
            if (miner.getAiState() != AstroMinerEntity.AISTATE_ATBASE) context.fail("Miner did not dock facing " + facing);
            miner.tick();
            assertDock(context, miner, expected, facing, "return");
            forEachPart(context, pos -> {
                if (!context.getBlockState(pos).is(GCBlocks.ASTRO_MINER_BASE)) context.fail("Docking damaged the base");
            });
            miner.discard();
        }
        context.succeed();
    }

    private static void assertDock(GameTestHelper context, AstroMinerEntity miner, Vec3 expected,
                                   Direction facing, String stage) {
        if (miner.position().distanceToSqr(expected) > 0.000001
                || Math.abs(net.minecraft.util.Mth.wrapDegrees(miner.getYRot() - facing.toYRot())) > 0.001) {
            context.fail(stage + " is misaligned facing " + facing + ": " + miner.position() + " yaw=" + miner.getYRot());
        }
    }

    private static void forEachPart(GameTestHelper context, java.util.function.Consumer<BlockPos> action) {
        for (int y = 0; y < 2; y++) {
            for (int x = 0; x < 2; x++) {
                for (int z = 0; z < 2; z++) {
                    action.accept(MASTER.offset(x, y, z));
                }
            }
        }
    }

    private static BlockHitResult hit(GameTestHelper context, BlockPos relativePos) {
        BlockPos absolutePos = context.absolutePos(relativePos);
        return new BlockHitResult(Vec3.atCenterOf(absolutePos), Direction.UP, absolutePos, false);
    }
}
