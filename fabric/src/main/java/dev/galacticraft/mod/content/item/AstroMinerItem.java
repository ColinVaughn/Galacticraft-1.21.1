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

import dev.galacticraft.mod.Galacticraft;
import dev.galacticraft.mod.content.GCBlocks;
import dev.galacticraft.mod.content.GCEntityTypes;
import dev.galacticraft.mod.content.block.entity.machine.AstroMinerBaseBlockEntity;
import dev.galacticraft.mod.content.entity.vehicle.AstroMinerEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * Deploys an autonomous {@link AstroMinerEntity} onto an Astro Miner Base (legacy
 * ItemAstroMiner). Right-clicking the base's master block links a new miner to it.
 */
public class AstroMinerItem extends Item {
    public AstroMinerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();

        if (!level.getBlockState(pos).is(GCBlocks.ASTRO_MINER_BASE)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        // The block entity lives only on the base's master block.
        if (!(level.getBlockEntity(pos) instanceof AstroMinerBaseBlockEntity base)) {
            if (player != null) {
                player.displayClientMessage(Component.literal("Use the Astro Miner Base's main block to deploy a miner."), true);
            }
            return InteractionResult.CONSUME;
        }
        if (base.hasLinkedMiner()) {
            if (player != null) {
                player.displayClientMessage(Component.literal("This Astro Miner Base already has a linked miner."), true);
            }
            return InteractionResult.FAIL;
        }

        if (level instanceof ServerLevel serverLevel) {
            // Per-player cap (config). Counts currently-loaded miners owned by the player.
            if (player != null && !player.isCreative()) {
                int max = Galacticraft.CONFIG.astroMinerMax();
                int count = 0;
                for (ServerLevel lvl : serverLevel.getServer().getAllLevels()) {
                    count += lvl.getEntities(GCEntityTypes.ASTRO_MINER,
                            m -> player.getUUID().equals(m.getOwnerUUID())).size();
                }
                if (count >= max) {
                    player.displayClientMessage(Component.literal("You have reached your Astro Miner limit (" + max + ")."), true);
                    return InteractionResult.FAIL;
                }
            }

            AstroMinerEntity miner = new AstroMinerEntity(GCEntityTypes.ASTRO_MINER, level);
            miner.prepareDeploy(base.getMasterPos(), base.getFacing(), player != null ? player.getUUID() : null);
            serverLevel.addFreshEntity(miner);
            base.setLinkedMiner(miner.getUUID());
            base.planTargets();
            if (player == null || !player.isCreative()) {
                context.getItemInHand().shrink(1);
            }
        }
        return InteractionResult.CONSUME;
    }
}
