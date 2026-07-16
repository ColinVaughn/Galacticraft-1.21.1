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

package dev.galacticraft.mod.content.item;

import dev.galacticraft.api.component.GCDataComponents;
import dev.galacticraft.api.fluid.FluidData;
import dev.galacticraft.mod.content.GCEntityTypes;
import dev.galacticraft.mod.content.GCBlocks;
import dev.galacticraft.mod.content.block.special.launchpad.AbstractLaunchPad;
import dev.galacticraft.mod.content.block.special.launchpad.LaunchPadBlockEntity;
import dev.galacticraft.mod.content.entity.vehicle.CargoRocketEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;

public class CargoRocketItem extends Item {
    public CargoRocketItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos clickedPos = context.getClickedPos();
        if (level.getBlockState(clickedPos).getBlock() != GCBlocks.ROCKET_LAUNCH_PAD
                || level.getBlockState(clickedPos).getValue(AbstractLaunchPad.PART) == AbstractLaunchPad.Part.NONE) {
            return InteractionResult.FAIL;
        }
        BlockPos pos = clickedPos.offset(AbstractLaunchPad.partToCenterPos(
                level.getBlockState(clickedPos).getValue(AbstractLaunchPad.PART)));
        if (!(level.getBlockEntity(pos) instanceof LaunchPadBlockEntity pad) || pad.hasDockedEntity()) {
            return InteractionResult.FAIL;
        }
        if (level instanceof ServerLevel) {
            CargoRocketEntity rocket = new CargoRocketEntity(GCEntityTypes.CARGO_ROCKET, level);
            FluidData fuel = context.getItemInHand().get(GCDataComponents.FLUID_DATA);
            if (fuel != null) {
                rocket.getFuelTank().insert(fuel.variant().fluid(), fuel.variant().components(), fuel.amount());
            }
            rocket.placeOnPad(pad);
            rocket.setYRot(context.getHorizontalDirection().toYRot());
            level.addFreshEntity(rocket);
            pad.setDockedEntity(rocket);
            if (context.getPlayer() == null || !context.getPlayer().isCreative()) {
                context.getItemInHand().shrink(1);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
