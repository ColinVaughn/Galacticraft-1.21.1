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

import dev.galacticraft.mod.content.GCBlockEntityTypes;
import dev.galacticraft.mod.content.block.jupiter.JupiterBlocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.phys.AABB;

/** The Jupiter machines place facing their placer, carry the renderer's block entity, and drop themselves. */
public final class JupiterMachineBlockTestSuite implements GalacticraftGameTest {
    @GameTest(template = EMPTY_STRUCTURE)
    public void machinesPlaceRenderAndDropThemselves(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        for (var machine : JupiterBlocks.MACHINES) {
            level.setBlockAndUpdate(pos, machine.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
            var state = level.getBlockState(pos);
            helper.assertTrue(state.is(machine) && state.getValue(BlockStateProperties.HORIZONTAL_FACING) == Direction.EAST, "Did not keep its facing: " + machine);
            helper.assertTrue(state.getRenderShape() == RenderShape.ENTITYBLOCK_ANIMATED, "Not drawn by its renderer: " + machine);
            var entity = level.getBlockEntity(pos);
            helper.assertTrue(entity != null && GCBlockEntityTypes.JUPITER_MACHINES.contains(entity.getType()), "Missing block entity: " + machine);
            helper.assertTrue(level.destroyBlock(pos, true), "Could not break: " + machine);
            var drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(1.5));
            helper.assertTrue(drops.stream().anyMatch(drop -> drop.getItem().is(machine.asItem())), "Did not drop itself: " + machine);
            drops.forEach(ItemEntity::discard);
        }
        helper.succeed();
    }
}
