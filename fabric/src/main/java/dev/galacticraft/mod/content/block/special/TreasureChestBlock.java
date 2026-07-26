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

package dev.galacticraft.mod.content.block.special;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.galacticraft.mod.content.GCBlockEntityTypes;
import dev.galacticraft.mod.content.block.entity.TreasureChestBlockEntity;
import dev.galacticraft.mod.content.item.DungeonKeyItem;
import dev.galacticraft.mod.util.Translations;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * The locked chest at the end of a dungeon. It cannot be opened or broken until it is
 * unlocked with a {@link DungeonKeyItem} of the same tier, which the dungeon's boss drops
 * when it dies.
 */
public class TreasureChestBlock extends ChestBlock {
    public static final MapCodec<TreasureChestBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.intRange(1, 3).fieldOf("tier").forGetter(TreasureChestBlock::getTier),
            propertiesCodec()
    ).apply(instance, TreasureChestBlock::new));

    private final int tier;

    public TreasureChestBlock(int tier, Properties properties) {
        super(properties, () -> GCBlockEntityTypes.TREASURE_CHEST);
        this.tier = tier;
    }

    public int getTier() {
        return this.tier;
    }

    @Override
    public MapCodec<TreasureChestBlock> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TreasureChestBlockEntity(pos, state);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof TreasureChestBlockEntity chest && chest.isLocked()) {
            if (DungeonKeyItem.getTier(stack) == this.tier) {
                if (!level.isClientSide) {
                    chest.unlock();
                    if (!player.getAbilities().instabuild) {
                        stack.shrink(1);
                    }
                    level.playSound(null, pos, SoundEvents.CHEST_LOCKED, SoundSource.BLOCKS, 0.7F, 1.4F);
                }
                return ItemInteractionResult.sidedSuccess(level.isClientSide);
            }

            this.rejectUnlock(level, pos, player);
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }

        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof TreasureChestBlockEntity chest && chest.isLocked()) {
            this.rejectUnlock(level, pos, player);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        return super.useWithoutItem(state, level, pos, player, hit);
    }

    private void rejectUnlock(Level level, BlockPos pos, Player player) {
        if (!level.isClientSide) {
            player.displayClientMessage(Component.translatable(Translations.TreasureChest.LOCKED, this.tier), true);
            level.playSound(null, pos, SoundEvents.CHEST_LOCKED, SoundSource.BLOCKS, 0.5F, 0.8F);
        }
    }

    /**
     * A locked chest is indestructible, so the only way in is the boss's key. Once unlocked it
     * breaks like a normal chest. Creative-mode breaking bypasses destroy progress entirely.
     */
    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof TreasureChestBlockEntity chest && chest.isLocked()) {
            return 0.0F;
        }

        return super.getDestroyProgress(state, player, level, pos);
    }
}
