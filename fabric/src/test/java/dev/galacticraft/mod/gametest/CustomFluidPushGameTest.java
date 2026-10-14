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
import dev.galacticraft.mod.tag.GCFluidTags;
import dev.galacticraft.mod.util.CustomFluidPush;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class CustomFluidPushGameTest implements GalacticraftGameTest {
    @GameTest(template = EMPTY_STRUCTURE)
    public void mixedFluidsKeepOilPriorityFuelHeightAndAcidPush(GameTestHelper test) throws ReflectiveOperationException {
        var level = test.getLevel();
        BlockPos origin = test.absolutePos(new BlockPos(1, 1, 1));
        var entity = EntityType.PIG.create(level);
        entity.setPos(origin.getX() + 1, origin.getY() + 0.1, origin.getZ() + 1);
        entity.setBoundingBox(new AABB(origin.getX(), origin.getY() + 0.1, origin.getZ(), origin.getX() + 2, origin.getY() + 1.5, origin.getZ() + 2));
        for (int x = 0; x < 2; x++) for (int z = 0; z < 2; z++) {
            level.setBlock(origin.offset(x, 0, z), GCBlocks.FUEL.defaultBlockState(), 2);
        }
        test.assertValueEqual(scan(entity), 2, "Fuel contact flag");
        double previousFuelHeight = entity.getFluidHeight(GCFluidTags.FUEL);
        test.assertTrue(previousFuelHeight > 0.7, "Fuel depth not recorded");
        level.setBlock(origin, GCBlocks.CRUDE_OIL.defaultBlockState(), 2);
        level.setBlock(origin.north(), GCBlocks.CRUDE_OIL.defaultBlockState().setValue(LiquidBlock.LEVEL, 7), 2);
        BlockPos acidPos = origin.offset(1, 0, 1);
        level.setBlock(acidPos, GCBlocks.SULFURIC_ACID.defaultBlockState(), 2);
        level.setBlock(acidPos.south(), GCBlocks.SULFURIC_ACID.defaultBlockState().setValue(LiquidBlock.LEVEL, 7), 2);
        entity.setBoundingBox(new AABB(origin.getX(), origin.getY() + 0.6, origin.getZ(), origin.getX() + 2, origin.getY() + 1.5, origin.getZ() + 2));
        double minY = entity.getBoundingBox().deflate(0.001).minY;
        var oilState = level.getFluidState(origin);
        var acidState = level.getFluidState(acidPos);
        double oilDepth = origin.getY() + oilState.getHeight(level, origin) - minY;
        double acidDepth = acidPos.getY() + acidState.getHeight(level, acidPos) - minY;
        Vec3 oilFlow = oilState.getFlow(level, origin).scale(oilDepth);
        Vec3 acidFlow = acidState.getFlow(level, acidPos).scale(acidDepth);
        test.assertTrue(oilFlow.lengthSqr() > 0 && acidFlow.lengthSqr() > 0, "Fixture has no flowing oil or acid");
        Vec3 movement = new Vec3(0.01, 0, 0);
        Vec3 afterOil = movement.add(CustomFluidPush.calculate(oilFlow, 1, false, movement, 0.0028));
        Vec3 expected = afterOil.add(CustomFluidPush.calculate(acidFlow, 1, false, afterOil, 0.0028));
        entity.setDeltaMovement(movement);
        test.assertValueEqual(scan(entity), 7, "All mixed fluid contact flags");
        test.assertTrue(Math.abs(entity.getFluidHeight(GCFluidTags.OIL) - oilDepth) < 1.0E-12, "Oil depth changed");
        test.assertTrue(entity.getFluidHeight(GCFluidTags.FUEL) == previousFuelHeight, "Oil contact overwrote the previous fuel depth");
        test.assertTrue(Math.abs(entity.getFluidHeight(GCFluidTags.SULFURIC_ACID) - acidDepth) < 1.0E-12, "Acid depth changed");
        test.assertTrue(entity.getDeltaMovement().distanceTo(expected) < 1.0E-12, "Oil and acid push order changed");
        entity.setBoundingBox(entity.getBoundingBox().move(0, 2, 0));
        test.assertValueEqual(scan(entity), 0, "Dry entity still in fluid");
        test.assertTrue(entity.getFluidHeight(GCFluidTags.OIL) == 0 && entity.getFluidHeight(GCFluidTags.FUEL) == 0
                && entity.getFluidHeight(GCFluidTags.SULFURIC_ACID) == 0, "Dry scan did not clear fluid heights");
        test.succeed();
    }

    private int scan(Entity entity) throws ReflectiveOperationException {
        var scan = Entity.class.getDeclaredMethod("galacticraft$updateCustomFluidHeightAndDoFluidPushing", Entity.class);
        scan.setAccessible(true);
        return (int) scan.invoke(entity, entity);
    }
}
