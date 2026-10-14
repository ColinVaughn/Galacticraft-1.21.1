/* Copyright (c) 2026 Team Galacticraft. Licensed under the MIT license. */
package dev.galacticraft.mod.content.block.machine;

import com.mojang.serialization.MapCodec;
import dev.galacticraft.machinelib.api.block.MachineBlock;
import dev.galacticraft.mod.content.block.entity.machine.MethaneSynthesizerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.state.BlockState;

public class MethaneSynthesizerBlock extends MachineBlock {
    private static final MapCodec<MethaneSynthesizerBlock> CODEC = simpleCodec(MethaneSynthesizerBlock::new);
    public MethaneSynthesizerBlock(Properties properties) { super(properties); }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override public MethaneSynthesizerBlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new MethaneSynthesizerBlockEntity(pos, state); }
}
