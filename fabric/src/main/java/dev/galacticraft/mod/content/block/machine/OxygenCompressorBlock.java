package dev.galacticraft.mod.content.block.machine;

import dev.galacticraft.machinelib.api.block.SimpleMachineBlock;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.api.oxygencompressor.OxygenTankInside;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

public class OxygenCompressorBlock extends SimpleMachineBlock {
    public static final EnumProperty<OxygenTankInside> OXYGEN_TANK_INSIDE = EnumProperty.create("oxygen_tank_size", OxygenTankInside.class);

    public OxygenCompressorBlock(Properties settings) {
        super(settings, Constant.id(Constant.Block.OXYGEN_COMPRESSOR));
        this.registerDefaultState((super.defaultBlockState().setValue(OXYGEN_TANK_INSIDE, OxygenTankInside.NONE)));
    }

    @Override
    public void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> stateBuilder) {
        super.createBlockStateDefinition(stateBuilder);
        stateBuilder.add(OXYGEN_TANK_INSIDE);
    }
}
