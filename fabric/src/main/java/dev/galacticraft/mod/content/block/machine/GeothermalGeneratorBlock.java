/* Copyright (c) 2026 Team Galacticraft. Licensed under the MIT license. */
package dev.galacticraft.mod.content.block.machine;

import com.mojang.serialization.MapCodec;
import dev.galacticraft.machinelib.api.block.MachineBlock;
import dev.galacticraft.mod.content.block.entity.machine.GeothermalGeneratorBlockEntity;
import dev.galacticraft.mod.particle.GCParticleTypes;
import dev.galacticraft.mod.particle.ScaleParticleType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.state.BlockState;

public class GeothermalGeneratorBlock extends MachineBlock {
    private static final MapCodec<GeothermalGeneratorBlock> CODEC = simpleCodec(GeothermalGeneratorBlock::new);

    public GeothermalGeneratorBlock(Properties properties) { super(properties); }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override public GeothermalGeneratorBlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GeothermalGeneratorBlockEntity(pos, state);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(ACTIVE) || random.nextInt(3) != 0) return;
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                level.addParticle(new ScaleParticleType(GCParticleTypes.ACID_EXHAUST, 0.5F),
                        pos.getX() + 0.5 + x * 0.25, pos.getY() + 1.05, pos.getZ() + 0.5 + z * 0.25,
                        0, 0.025, 0);
            }
        }
    }
}
