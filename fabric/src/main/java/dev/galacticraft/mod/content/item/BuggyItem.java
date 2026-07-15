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
import dev.galacticraft.mod.content.GCBlocks;
import dev.galacticraft.mod.content.GCEntityTypes;
import dev.galacticraft.mod.content.block.special.launchpad.AbstractLaunchPad;
import dev.galacticraft.mod.content.block.special.launchpad.LaunchPadBlockEntity;
import dev.galacticraft.mod.content.entity.vehicle.Buggy;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public class BuggyItem extends Item {
    public BuggyItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos clickedPos = context.getClickedPos();
        BlockState clickedState = level.getBlockState(clickedPos);

        // Right-clicking a Fuelling Pad docks the buggy so it can be refuelled by a Fuel
        // Loader, mirroring RocketItem.useOn / the legacy Buggy Fueling Pad.
        if (clickedState.getBlock() == GCBlocks.FUELING_PAD
                && clickedState.getValue(AbstractLaunchPad.PART) != AbstractLaunchPad.Part.NONE) {
            if (level instanceof ServerLevel) {
                BlockPos center = clickedPos.offset(AbstractLaunchPad.partToCenterPos(clickedState.getValue(AbstractLaunchPad.PART)));
                if (level.getBlockEntity(center) instanceof LaunchPadBlockEntity pad) {
                    if (pad.hasDockedEntity()) {
                        return InteractionResult.FAIL;
                    }
                    spawnBuggy(level, center.getX() + 0.5D, center.getY() + 1.0D, center.getZ() + 0.5D, context, pad);
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        // Otherwise place a free (unfuelled) buggy on top of the clicked face.
        if (level instanceof ServerLevel) {
            BlockPos pos = clickedPos.relative(context.getClickedFace());
            spawnBuggy(level, pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, context, null);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static void spawnBuggy(Level level, double x, double y, double z, UseOnContext context, LaunchPadBlockEntity pad) {
        Buggy buggy = new Buggy(GCEntityTypes.BUGGY, level);
        ItemStack stack = context.getItemInHand();
        buggy.setVariant(Buggy.BuggyType.byId(stack.getOrDefault(GCDataComponents.BUGGY_TYPE, 0)));
        FluidData fuel = stack.get(GCDataComponents.FLUID_DATA);
        if (fuel != null) {
            buggy.getFuelTank().insert(fuel.variant().fluid(), fuel.variant().components(), fuel.amount());
        }
        buggy.setPos(x, y, z);
        buggy.setYRot(context.getHorizontalDirection().toYRot());
        if (pad != null) {
            buggy.setPad(pad);
        }
        level.addFreshEntity(buggy);
        if (pad != null) {
            pad.setDockedEntity(buggy);
        }
        Player player = context.getPlayer();
        if (player == null || !player.isCreative()) {
            context.getItemInHand().shrink(1);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag type) {
        Buggy.BuggyType variant = Buggy.BuggyType.byId(stack.getOrDefault(GCDataComponents.BUGGY_TYPE, 0));
        tooltip.add(Component.translatable("tooltip.galacticraft.buggy_storage", variant.getStorage()));
        super.appendHoverText(stack, context, tooltip, type);
    }
}
