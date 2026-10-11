package dev.galacticraft.mod.content.block.environment;

import com.mojang.serialization.MapCodec;
import dev.galacticraft.mod.content.GCEntityTypes;
import dev.galacticraft.mod.content.entity.LunarBurrowerEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Scheduled stages persist across chunk unloads; blocked hatchlings wait for space. */
public final class LunarBurrowerEggBlock extends Block {
    public static final MapCodec<LunarBurrowerEggBlock> CODEC = simpleCodec(LunarBurrowerEggBlock::new);
    public static final IntegerProperty HATCH = IntegerProperty.create("hatch", 0, 2);
    private static final VoxelShape SHAPE = Block.box(3, 0, 3, 13, 13, 13);
    public LunarBurrowerEggBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(HATCH, 0));
    }
    @Override protected MapCodec<LunarBurrowerEggBlock> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(HATCH); }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return SHAPE; }
    @Override protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
    }
    @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        if (!level.isClientSide && !oldState.is(this)) level.scheduleTick(pos, this, 400);
    }
    @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!canSurvive(state, level, pos)) { level.destroyBlock(pos, true); return; }
        int stage = state.getValue(HATCH);
        if (stage < 2) {
            level.setBlock(pos, state.setValue(HATCH, stage + 1), 3);
            level.scheduleTick(pos, this, 400);
            return;
        }
        LunarBurrowerEntity child = GCEntityTypes.LUNAR_BURROWER.create(level);
        if (child == null) { level.scheduleTick(pos, this, 100); return; }
        child.hatch();
        child.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 180, 0);
        child.setDenPosition(pos);
        // Check the whole juvenile's footprint, including its trailing segments, before consuming the egg.
        var space = child.getBoundingBox().expandTowards(0, 0, 2.6);
        if (!level.hasChunksAt(BlockPos.containing(space.minX, space.minY, space.minZ), BlockPos.containing(space.maxX, space.maxY, space.maxZ))
                || !level.getWorldBorder().isWithinBounds(space)
                || !level.noCollision(child, space.move(0, 1, 0))) {
            level.scheduleTick(pos, this, 100);
            return;
        }
        level.removeBlock(pos, false);
        if (!level.noCollision(child, space) || !level.addFreshEntity(child)) {
            level.setBlock(pos, state, 3);
            level.scheduleTick(pos, this, 100);
        }
    }
}
