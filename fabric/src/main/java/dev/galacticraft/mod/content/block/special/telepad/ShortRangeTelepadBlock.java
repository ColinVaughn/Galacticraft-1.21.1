/* Copyright (c) 2026 Team Galacticraft. Licensed under the MIT license. */
package dev.galacticraft.mod.content.block.special.telepad;

import com.mojang.serialization.MapCodec;
import dev.galacticraft.machinelib.api.block.MachineBlock;
import dev.galacticraft.mod.content.GCBlocks;
import dev.galacticraft.mod.content.block.entity.machine.ShortRangeTelepadBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3f;

public class ShortRangeTelepadBlock extends MachineBlock {
    private static final MapCodec<ShortRangeTelepadBlock> CODEC = simpleCodec(ShortRangeTelepadBlock::new);
    private static final VoxelShape SHAPE = box(0, 0, 0, 16, 7.2, 16);
    public ShortRangeTelepadBlock(Properties properties) { super(properties); }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override public ShortRangeTelepadBlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new ShortRangeTelepadBlockEntity(pos, state); }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.ENTITYBLOCK_ANIMATED; }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return SHAPE; }

    public static boolean hasRoom(Level level, BlockPos pos) {
        if (pos.getY() + 2 >= level.getMaxBuildHeight()) return false;
        for (BlockPos part : BlockPos.betweenClosed(pos.offset(-1, 0, -1), pos.offset(1, 2, 1))) {
            if (!level.getWorldBorder().isWithinBounds(part) || !level.getBlockState(part).canBeReplaced()) return false;
        }
        return true;
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return hasRoom(context.getLevel(), context.getClickedPos()) ? super.getStateForPlacement(context) : null;
    }

    public static void createFrame(Level level, BlockPos pos) {
        for (int y = 0; y <= 2; y += 2) {
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    if (y == 0 && x == 0 && z == 0) continue;
                    level.setBlockAndUpdate(pos.offset(x, y, z), GCBlocks.TELEPAD_FRAME.defaultBlockState()
                            .setValue(TelepadFrameBlock.X, x + 1).setValue(TelepadFrameBlock.Z, z + 1).setValue(TelepadFrameBlock.TOP, y == 2));
                }
            }
        }
    }

    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide) createFrame(level, pos);
    }

    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moved) {
        if (!state.is(replacement.getBlock())) {
            if (level.getBlockEntity(pos) instanceof ShortRangeTelepadBlockEntity pad) pad.unregister();
            for (BlockPos part : BlockPos.betweenClosed(pos.offset(-1, 0, -1), pos.offset(1, 2, 1))) {
                BlockState frame = level.getBlockState(part);
                if (frame.is(GCBlocks.TELEPAD_FRAME) && TelepadFrameBlock.mainPos(frame, part).equals(pos)) level.removeBlock(part, false);
            }
        }
        super.onRemove(state, level, pos, replacement, moved);
    }

    @Override public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!(level.getBlockEntity(pos) instanceof ShortRangeTelepadBlockEntity pad)) return;
        for (boolean sending : new boolean[]{false, true}) {
            boolean valid = sending ? pad.getTargetResult() == ShortRangeTelepadBlockEntity.TargetResult.VALID : pad.hasValidAddress();
            float progress = Math.min(1, pad.getTeleportTime() / (float) ShortRangeTelepadBlockEntity.TELEPORT_TIME);
            Vector3f color = !valid ? new Vector3f(1, 0.3F, 0.3F)
                    : pad.energyStorage().getAmount() < ShortRangeTelepadBlockEntity.ENERGY_PER_TELEPORT ? new Vector3f(1, 0.6F, 0.3F)
                    : new Vector3f(0.3F, 0.3F + 0.7F * progress, 1 - 0.7F * progress);
            for (int i = 0; i < 6; i++) {
                level.addParticle(new DustParticleOptions(color, 0.65F), pos.getX() + 0.2 + random.nextDouble() * 0.6,
                        pos.getY() + (sending ? 2.9 : 0.45), pos.getZ() + 0.2 + random.nextDouble() * 0.6,
                        0, sending ? -0.1 : 0.05, 0);
            }
        }
    }
}
