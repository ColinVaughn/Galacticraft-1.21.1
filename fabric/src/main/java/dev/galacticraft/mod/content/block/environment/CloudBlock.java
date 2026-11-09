package dev.galacticraft.mod.content.block.environment;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Solid cloud terrain cushions a landing without moving or consuming the block. */
public final class CloudBlock extends Block {
    public CloudBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void fallOn(Level level, BlockState state, BlockPos pos, Entity entity, float distance) {
        entity.causeFallDamage(distance, 0.2F, level.damageSources().fall());
    }
}