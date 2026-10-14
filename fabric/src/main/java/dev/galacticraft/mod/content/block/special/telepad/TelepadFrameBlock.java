/* Copyright (c) 2026 Team Galacticraft. Licensed under the MIT license. */
package dev.galacticraft.mod.content.block.special.telepad;

import com.mojang.serialization.MapCodec;
import dev.galacticraft.mod.content.GCBlocks;
import dev.galacticraft.mod.content.block.entity.machine.ShortRangeTelepadBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Invisible multiblock parts carry their master offset in block state, without extra block entities. */
public class TelepadFrameBlock extends Block {
    private static final MapCodec<TelepadFrameBlock> CODEC = simpleCodec(TelepadFrameBlock::new);
    public static final IntegerProperty X = IntegerProperty.create("offset_x", 0, 2);
    public static final IntegerProperty Z = IntegerProperty.create("offset_z", 0, 2);
    public static final BooleanProperty TOP = BooleanProperty.create("top");
    private static final VoxelShape BOTTOM_SHAPE = box(0, 0, 0, 16, 3.2, 16), TOP_SHAPE = box(0, 8.8, 0, 16, 16, 16);
    public TelepadFrameBlock(Properties properties) { super(properties); }
    @Override public MapCodec<TelepadFrameBlock> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(X, Z, TOP); }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return state.getValue(TOP) ? TOP_SHAPE : BOTTOM_SHAPE; }
    public static BlockPos mainPos(BlockState state, BlockPos pos) { return pos.offset(1 - state.getValue(X), state.getValue(TOP) ? -2 : 0, 1 - state.getValue(Z)); }
    public static ShortRangeTelepadBlockEntity master(BlockGetter level, BlockPos pos, BlockState state) {
        return level.getBlockEntity(mainPos(state, pos)) instanceof ShortRangeTelepadBlockEntity pad ? pad : null;
    }
    public static ShortRangeTelepadBlockEntity energyInput(BlockGetter level, BlockPos pos, BlockState state, Direction side) {
        if (!state.is(GCBlocks.TELEPAD_FRAME) || side != Direction.UP || !state.getValue(TOP) || state.getValue(X) != 1 || state.getValue(Z) != 1) return null;
        return master(level, pos, state);
    }
    @Override public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        var pad = master(level, pos, state);
        return pad == null ? InteractionResult.PASS : ((ShortRangeTelepadBlock) pad.getBlockState().getBlock())
                .useWithoutItem(pad.getBlockState(), level, pad.getBlockPos(), player, hit);
    }
    @Override public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        var pad = master(level, pos, state);
        return pad == null ? ItemStack.EMPTY : pad.getBlockState().getBlock().asItem().getDefaultInstance();
    }
    @Override public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        var pad = master(level, pos, state);
        if (!level.isClientSide && pad != null) level.destroyBlock(pad.getBlockPos(), !player.isCreative(), player);
        return super.playerWillDestroy(level, pos, state, player);
    }
    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moved) {
        if (!level.isClientSide && !state.is(replacement.getBlock())) {
            var pad = master(level, pos, state);
            if (pad != null) level.destroyBlock(pad.getBlockPos(), true);
        }
        super.onRemove(state, level, pos, replacement, moved);
    }
}
