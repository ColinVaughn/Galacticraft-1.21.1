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

import com.mojang.serialization.MapCodec;
import dev.galacticraft.mod.content.GCBlockEntityTypes;
import dev.galacticraft.mod.content.block.entity.machine.AstroMinerBaseBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * The Astro Miner Base: a hand-rolled 2x2x2 multiblock (no framework), modelled on
 * {@code AbstractLaunchPad}'s {@link EnumProperty} pattern extended to 3-D. One master
 * corner ({@link Part#BOTTOM_NW}) owns the {@link AstroMinerBaseBlockEntity}; the other
 * seven corners are inert parts that resolve back to it via {@link #partToMasterPos}.
 * Faithful port of legacy {@code TileEntityMinerBase}/{@code BlockMulti.MINER_BASE}.
 */
public class AstroMinerBaseBlock extends BaseEntityBlock {
    public static final MapCodec<AstroMinerBaseBlock> CODEC = simpleCodec(AstroMinerBaseBlock::new);
    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    /** The corner that owns the block entity. */
    public static final Part MASTER = Part.BOTTOM_NW;

    public AstroMinerBaseBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.getStateDefinition().any().setValue(PART, Part.NONE).setValue(FACING, Direction.NORTH));
    }

    /** Offset from a part back to the master corner. */
    public static BlockPos partToMasterPos(Part part) {
        return new BlockPos(-part.offsetX, -part.offsetY, -part.offsetZ);
    }

    public static @Nullable AstroMinerBaseBlockEntity getMasterBlockEntity(BlockGetter level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof AstroMinerBaseBlock) || state.getValue(PART) == Part.NONE) {
            return null;
        }

        BlockPos master = pos.offset(partToMasterPos(state.getValue(PART)));
        return level.getBlockEntity(master) instanceof AstroMinerBaseBlockEntity base ? base : null;
    }

    private static Part partForOffset(int x, int y, int z) {
        for (Part part : Part.values()) {
            if (part != Part.NONE && part.offsetX == x && part.offsetY == y && part.offsetZ == z) {
                return part;
            }
        }
        return Part.NONE;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PART, FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(PART, Part.NONE).setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        // Unassembled blocks show a normal cube so you can see what you're building; once
        // assembled, the 2x2x2 OBJ is drawn by the block-entity renderer on the master corner.
        return state.getValue(PART) == Part.NONE ? RenderShape.MODEL : RenderShape.INVISIBLE;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.block();
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        // only try to assemble from an inert, unassigned block
        if (state.getValue(PART) != Part.NONE) {
            return;
        }
        // the placed block may sit at any of the 8 corners of a candidate cube
        for (int ox = 0; ox <= 1; ox++) {
            for (int oy = 0; oy <= 1; oy++) {
                for (int oz = 0; oz <= 1; oz++) {
                    BlockPos master = pos.offset(-ox, -oy, -oz);
                    if (this.isCubeOfNone(level, master)) {
                        this.assemble(level, master, state.getValue(FACING));
                        return;
                    }
                }
            }
        }
    }

    private boolean isCubeOfNone(Level level, BlockPos master) {
        for (int x = 0; x <= 1; x++) {
            for (int y = 0; y <= 1; y++) {
                for (int z = 0; z <= 1; z++) {
                    BlockState bs = level.getBlockState(master.offset(x, y, z));
                    if (!bs.is(this) || bs.getValue(PART) != Part.NONE) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private void assemble(Level level, BlockPos master, Direction facing) {
        for (int x = 0; x <= 1; x++) {
            for (int y = 0; y <= 1; y++) {
                for (int z = 0; z <= 1; z++) {
                    BlockPos partPos = master.offset(x, y, z);
                    level.setBlockAndUpdate(partPos, level.getBlockState(partPos)
                            .setValue(PART, partForOffset(x, y, z))
                            .setValue(FACING, facing));
                }
            }
        }
        if (level.getBlockEntity(master) instanceof AstroMinerBaseBlockEntity be) {
            be.setFacing(facing);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        // inert single block: nothing to tear down
        if (state.getValue(PART) == Part.NONE) {
            super.onRemove(state, level, pos, newState, moved);
            return;
        }
        BlockPos master = pos.offset(partToMasterPos(state.getValue(PART)));
        // removes the block entity (only present on the master corner)
        super.onRemove(state, level, pos, newState, moved);
        // break the remaining corners as a unit (already-air corners are a no-op)
        for (int x = 0; x <= 1; x++) {
            for (int y = 0; y <= 1; y++) {
                for (int z = 0; z <= 1; z++) {
                    BlockPos partPos = master.offset(x, y, z);
                    if (!partPos.equals(pos) && level.getBlockState(partPos).is(this)) {
                        level.destroyBlock(partPos, false);
                    }
                }
            }
        }
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        // break the other corners without drops so exactly one base drops from the broken corner
        if (state.getValue(PART) != Part.NONE) {
            BlockPos master = pos.offset(partToMasterPos(state.getValue(PART)));
            for (int x = 0; x <= 1; x++) {
                for (int y = 0; y <= 1; y++) {
                    for (int z = 0; z <= 1; z++) {
                        BlockPos partPos = master.offset(x, y, z);
                        if (!partPos.equals(pos) && level.getBlockState(partPos).is(this)) {
                            level.destroyBlock(partPos, false);
                        }
                    }
                }
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (state.getValue(PART) == Part.NONE) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            BlockPos master = pos.offset(partToMasterPos(state.getValue(PART)));
            if (level.getBlockEntity(master) instanceof AstroMinerBaseBlockEntity be) {
                if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                    dev.architectury.registry.menu.MenuRegistry.openExtendedMenu(serverPlayer, be);
                }
            }
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        // only the master corner owns a block entity
        return state.getValue(PART) == MASTER ? new AstroMinerBaseBlockEntity(pos, state) : null;
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || state.getValue(PART) != MASTER) {
            return null;
        }
        return createTickerHelper(type, GCBlockEntityTypes.ASTRO_MINER_BASE, AstroMinerBaseBlockEntity::serverTick);
    }

    /**
     * The eight corners of the 2x2x2, plus {@link #NONE} for an unassembled block. Each
     * value records its offset from the master corner ({@link #BOTTOM_NW}).
     */
    public enum Part implements StringRepresentable {
        NONE(0, 0, 0),
        BOTTOM_NW(0, 0, 0),
        BOTTOM_NE(1, 0, 0),
        BOTTOM_SW(0, 0, 1),
        BOTTOM_SE(1, 0, 1),
        TOP_NW(0, 1, 0),
        TOP_NE(1, 1, 0),
        TOP_SW(0, 1, 1),
        TOP_SE(1, 1, 1);

        private final int offsetX;
        private final int offsetY;
        private final int offsetZ;

        Part(int offsetX, int offsetY, int offsetZ) {
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.offsetZ = offsetZ;
        }

        @Override
        public String getSerializedName() {
            return this.name().toLowerCase();
        }
    }
}
