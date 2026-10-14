/* Copyright (c) 2026 Team Galacticraft. Licensed under the MIT license. */
package dev.galacticraft.mod.content.block.machine;

import com.mojang.serialization.MapCodec;
import dev.galacticraft.machinelib.api.block.MachineBlock;
import dev.galacticraft.mod.content.block.entity.machine.GasLiquefierBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.state.BlockState;

public class GasLiquefierBlock extends MachineBlock {
    private static final MapCodec<GasLiquefierBlock> CODEC = simpleCodec(GasLiquefierBlock::new);
    public GasLiquefierBlock(Properties properties) { super(properties); }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override public GasLiquefierBlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new GasLiquefierBlockEntity(pos, state); }
}
