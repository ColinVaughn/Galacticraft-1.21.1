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

package dev.galacticraft.mod.content.block.environment;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Locale;

/** Supported cave details. Shapes are conservative bounds of the authored cuboids, not full blocks. */
public class LunarFormationBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<LunarFormationBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            StringRepresentable.fromEnum(Kind::values).fieldOf("kind").forGetter(block -> block.kind),
            propertiesCodec()
    ).apply(instance, LunarFormationBlock::new));

    private final Kind kind;
    private final VoxelShape[] shapes = new VoxelShape[4];

    public LunarFormationBlock(Kind kind, Properties properties) {
        super(properties);
        this.kind = kind;
        VoxelShape shape = Shapes.empty();
        for (double[] b : kind.boxes) shape = Shapes.or(shape, Block.box(b[0], b[1], b[2], b[3], b[4], b[5]));
        shapes[0] = shape.optimize();
        for (int i = 1; i < 4; i++) shapes[i] = turn(shapes[i - 1]);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    private static VoxelShape turn(VoxelShape shape) {
        VoxelShape[] result = {Shapes.empty()};
        shape.forAllBoxes((x1,y1,z1,x2,y2,z2) -> result[0] = Shapes.or(result[0], Shapes.box(1-z2,y1,x1,1-z1,y2,x2)));
        return result[0].optimize();
    }

    @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING); }
    @Override protected BlockState rotate(BlockState state, Rotation rotation) { return state.setValue(FACING, rotation.rotate(state.getValue(FACING))); }
    @Override protected BlockState mirror(BlockState state, Mirror mirror) { return state.rotate(mirror.getRotation(state.getValue(FACING))); }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        if (kind.attachment == Attachment.WALL) {
            facing = context.getClickedFace().getOpposite();
            if (facing.getAxis().isVertical()) return null;
        }
        BlockState state = defaultBlockState().setValue(FACING, facing);
        return state.canSurvive(context.getLevel(), context.getClickedPos()) ? state : null;
    }

    @Override protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction support = switch (kind.attachment) {
            case FLOOR -> Direction.DOWN;
            case CEILING -> Direction.UP;
            case WALL -> state.getValue(FACING);
        };
        BlockPos anchor = pos.relative(support);
        return level.getBlockState(anchor).isFaceSturdy(level, anchor, support.getOpposite());
    }

    @Override protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor,
                                               LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return state.canSurvive(level, pos) ? super.updateShape(state, direction, neighbor, level, pos, neighborPos) : Blocks.AIR.defaultBlockState();
    }

    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapes[switch (state.getValue(FACING)) { case EAST -> 1; case SOUTH -> 2; case WEST -> 3; default -> 0; }];
    }

    private enum Attachment { FLOOR, WALL, CEILING }

    public enum Kind implements StringRepresentable {
        LUNAR_BREAKDOWN_RUBBLE(Attachment.FLOOR, new double[][] {
                {0.080, 0.000, 1.080, 7.920, 2.000, 8.920},
                {1.387, 2.000, 2.387, 6.613, 5.000, 7.613},
                {8.022, 0.000, 2.042, 15.478, 4.000, 8.958},
                {9.328, 4.000, 3.349, 13.248, 6.000, 7.269},
                {4.272, 0.000, 9.042, 11.728, 3.000, 15.958},
                {11.540, 0.000, 10.540, 15.460, 2.000, 14.460}
        }),
        LUNAR_SCATTERED_FRAGMENTS(Attachment.FLOOR, new double[][] {
                {0.540, 0.000, 1.540, 4.460, 1.000, 5.460},
                {6.540, 0.000, 0.540, 10.460, 2.000, 4.460},
                {11.540, 0.000, 5.540, 15.460, 1.000, 9.460},
                {2.387, 0.000, 9.387, 7.613, 2.000, 14.613},
                {8.540, 0.000, 10.540, 12.460, 1.000, 14.460},
                {6.693, 0.000, 5.693, 9.307, 1.000, 8.307}
        }),
        LUNAR_FALLEN_ROOF_SLAB(Attachment.FLOOR, new double[][] {
                {1.659, 0.000, 2.194, 11.728, 1.000, 11.723},
                {0.352, 1.000, 0.888, 13.035, 4.000, 13.030},
                {9.973, 1.000, 1.046, 13.735, 3.000, 6.431},
                {1.659, 4.000, 3.725, 7.650, 6.000, 10.799},
                {9.540, 0.000, 11.540, 13.460, 2.000, 15.460}
        }),
        LUNAR_FRACTURED_OUTCROP(Attachment.FLOOR, new double[][] {
                {0.582, 0.000, 1.812, 11.418, 3.000, 13.188},
                {3.037, 3.000, 3.118, 7.339, 11.000, 7.962},
                {6.350, 3.000, 4.649, 10.111, 8.000, 10.034},
                {11.099, 0.000, 2.578, 15.401, 5.000, 7.422},
                {10.540, 0.000, 10.540, 14.460, 2.000, 14.460}
        }),
        LUNAR_BASALT_WALL_SHELF(Attachment.WALL, new double[][] {
                {2.000, 3.000, 0.000, 14.000, 14.000, 3.000},
                {3.000, 4.000, 3.000, 13.000, 6.000, 9.000},
                {2.000, 10.000, 3.000, 14.000, 12.000, 7.000},
                {5.000, 4.000, 9.000, 10.000, 5.000, 12.000},
                {4.000, 6.000, 3.000, 10.000, 10.000, 5.000}
        }),
        LUNAR_CEILING_BREAKDOWN(Attachment.CEILING, new double[][] {
                {2.774, 12.000, 2.774, 13.226, 16.000, 13.226},
                {4.080, 9.000, 4.463, 10.613, 12.000, 10.996},
                {4.000, 14.000, 0.000, 9.000, 16.000, 2.000},
                {1.540, 14.000, 9.540, 5.460, 16.000, 13.460},
                {5.387, 8.000, 6.152, 8.000, 9.000, 8.765}
        }),
        LUNAR_BASALT_BREAKDOWN_RUBBLE(Attachment.FLOOR, new double[][] {
                {0.080, 0.000, 1.080, 7.920, 2.000, 8.920},
                {1.387, 2.000, 2.387, 6.613, 5.000, 7.613},
                {8.022, 0.000, 2.042, 15.478, 4.000, 8.958},
                {9.328, 4.000, 3.349, 13.248, 6.000, 7.269},
                {4.272, 0.000, 9.042, 11.728, 3.000, 15.958},
                {11.540, 0.000, 10.540, 15.460, 2.000, 14.460}
        }),
        LUNAR_FROSTED_BREAKDOWN_RUBBLE(Attachment.FLOOR, new double[][] {
                {0.080, 0.000, 1.080, 7.920, 2.000, 8.920},
                {1.387, 2.000, 2.387, 6.613, 5.000, 7.613},
                {8.022, 0.000, 2.042, 15.478, 4.000, 8.958},
                {9.328, 4.000, 3.349, 13.248, 6.000, 7.269},
                {4.272, 0.000, 9.042, 11.728, 3.000, 15.958},
                {11.540, 0.000, 10.540, 15.460, 2.000, 14.460}
        }),
        ABANDONED_SURVEY_BEACON(Attachment.FLOOR, new double[][] {
                {3.000, 0.000, 3.000, 7.000, 2.000, 7.000},
                {9.000, 0.000, 3.000, 13.000, 2.000, 7.000},
                {3.000, 0.000, 9.000, 7.000, 2.000, 13.000},
                {9.000, 0.000, 9.000, 13.000, 2.000, 13.000},
                {5.000, 2.000, 5.000, 6.000, 6.000, 6.000},
                {10.000, 2.000, 5.000, 11.000, 6.000, 6.000},
                {5.000, 2.000, 10.000, 6.000, 6.000, 11.000},
                {10.000, 2.000, 10.000, 11.000, 6.000, 11.000},
                {4.000, 7.000, 5.000, 12.000, 12.000, 11.000},
                {4.000, 6.000, 5.000, 12.000, 7.000, 11.000},
                {4.000, 12.000, 5.000, 8.000, 14.000, 9.000},
                {10.000, 12.000, 8.000, 11.000, 15.000, 9.000},
                {8.000, 15.000, 8.000, 13.000, 16.000, 9.000}
        }),
        HELION_NODULES(Attachment.FLOOR, new double[][] {
                {0.889, 0.000, 1.501, 7.804, 1.000, 8.958},
                {2.196, 1.000, 2.425, 7.804, 4.000, 8.575},
                {2.196, 4.000, 2.808, 6.881, 6.000, 8.575},
                {8.080, 0.000, 2.080, 15.920, 1.000, 9.920},
                {9.387, 1.000, 3.387, 14.613, 3.000, 8.613},
                {10.311, 3.000, 4.693, 14.230, 5.000, 8.613},
                {3.272, 0.000, 9.042, 10.728, 1.000, 15.958},
                {4.578, 1.000, 10.349, 9.422, 3.000, 14.651},
                {4.578, 3.000, 10.731, 8.498, 4.000, 14.651}
        }),
        HELION_WALL_CRUST(Attachment.WALL, new double[][] {
                {1.000, 2.000, 0.000, 7.000, 11.000, 2.000},
                {8.000, 6.000, 0.000, 14.000, 14.000, 2.000},
                {7.000, 0.000, 0.000, 12.000, 4.000, 2.000},
                {2.000, 4.000, 2.000, 4.000, 9.000, 3.000},
                {4.000, 6.000, 2.000, 6.000, 8.000, 3.000},
                {9.000, 8.000, 2.000, 11.000, 12.000, 3.000},
                {11.000, 11.000, 2.000, 13.000, 12.000, 3.000},
                {8.000, 1.000, 2.000, 11.000, 3.000, 3.000}
        }),
        HELIUM_3_VENT(Attachment.FLOOR, new double[][] {
                {1.000, 0.000, 3.000, 4.000, 4.000, 12.000},
                {12.000, 0.000, 4.000, 15.000, 5.000, 12.000},
                {4.000, 0.000, 1.000, 12.000, 3.000, 4.000},
                {4.000, 0.000, 12.000, 11.000, 4.000, 15.000},
                {11.000, 0.000, 12.000, 14.000, 2.000, 14.000},
                {4.000, 0.000, 4.000, 12.000, 2.000, 12.000},
                {5.000, 2.000, 4.000, 7.000, 4.000, 7.000},
                {8.000, 3.000, 1.000, 11.000, 5.000, 3.000}
        }),
        BURROWER_EGG_CLUSTER(Attachment.FLOOR, new double[][] {
                {3.000, 1.000, 3.000, 7.000, 2.000, 7.000},
                {2.000, 2.000, 2.000, 8.000, 6.320, 8.000},
                {3.000, 6.320, 3.000, 7.000, 10.000, 7.000},
                {4.000, 10.000, 4.000, 6.000, 11.000, 6.000},
                {8.387, 1.000, 7.387, 13.613, 2.000, 12.613},
                {7.080, 2.000, 6.080, 14.920, 5.360, 13.920},
                {8.387, 5.360, 7.387, 13.613, 8.000, 12.613},
                {9.693, 8.000, 8.693, 12.307, 9.000, 11.307},
                {2.040, 1.000, 10.040, 5.960, 2.000, 13.960},
                {0.734, 2.000, 8.734, 7.266, 4.400, 15.266},
                {2.040, 4.400, 10.040, 5.960, 6.000, 13.960},
                {3.347, 6.000, 11.347, 4.653, 7.000, 12.653},
                {1.000, 0.000, 1.000, 14.000, 1.000, 15.000},
                {0.000, 0.000, 6.000, 2.000, 2.000, 11.000},
                {13.000, 0.000, 2.000, 16.000, 1.500, 7.000},
                {8.000, 0.000, 0.000, 11.000, 1.000, 2.000}
        }),
        BURROWER_BROKEN_EGGSHELL(Attachment.FLOOR, new double[][] {
                {3.000, 0.000, 3.000, 10.000, 1.000, 10.000},
                {3.000, 1.000, 3.000, 10.000, 3.000, 4.000},
                {3.000, 1.000, 4.000, 4.000, 5.000, 10.000},
                {4.000, 1.000, 9.000, 10.000, 4.000, 10.000},
                {9.000, 1.000, 4.000, 10.000, 2.000, 9.000},
                {4.000, 3.000, 3.000, 6.000, 5.000, 4.000},
                {3.000, 5.000, 6.000, 4.000, 7.000, 8.000},
                {6.000, 4.000, 9.000, 8.000, 6.000, 10.000},
                {4.000, 1.000, 4.000, 9.000, 2.000, 5.000},
                {4.000, 1.000, 5.000, 5.000, 3.000, 9.000},
                {10.578, 0.000, 2.349, 15.422, 1.000, 6.651},
                {10.540, 0.000, 10.540, 14.460, 1.000, 14.460},
                {0.540, 0.000, 11.540, 4.460, 1.000, 15.460}
        }),
        BURROWER_EXCAVATED_TAILINGS(Attachment.FLOOR, new double[][] {
                {1.000, 0.000, 2.000, 15.000, 1.000, 14.000},
                {1.927, 1.000, 2.927, 11.073, 4.000, 12.073},
                {4.000, 4.000, 5.000, 8.000, 5.000, 9.000},
                {9.042, 1.000, 5.272, 15.958, 3.000, 12.728},
                {6.000, 1.000, 11.000, 11.000, 3.000, 15.000},
                {1.000, 1.000, 2.000, 5.000, 2.500, 6.000},
                {7.000, 0.000, 0.000, 10.000, 1.000, 2.000},
                {11.000, 0.000, 1.000, 14.000, 0.600, 3.000},
                {0.000, 0.000, 9.000, 2.000, 1.000, 11.000},
                {2.000, 0.000, 14.000, 4.000, 0.600, 16.000},
                {9.731, 3.000, 7.502, 13.269, 4.000, 10.498}
        }),
        BURROWER_DEN_SCRAPS(Attachment.FLOOR, new double[][] {
                {2.000, 0.000, 3.000, 14.000, 0.700, 14.000},
                {0.698, 1.200, 3.156, 9.302, 2.200, 12.844},
                {2.387, 2.200, 4.463, 7.996, 3.300, 10.613},
                {2.004, 0.700, 3.539, 8.378, 1.200, 11.537},
                {9.387, 0.700, 5.387, 14.613, 4.000, 10.613},
                {11.000, 4.000, 7.000, 13.000, 5.000, 9.000},
                {7.616, 0.000, 0.157, 13.384, 1.000, 4.843},
                {8.578, 0.000, 11.349, 13.422, 2.000, 15.651},
                {0.000, 0.000, 1.000, 3.000, 1.500, 4.000},
                {0.770, 0.000, 12.311, 5.230, 0.800, 15.689}
        });

        private final Attachment attachment;
        private final double[][] boxes;
        Kind(Attachment attachment, double[][] boxes) { this.attachment = attachment; this.boxes = boxes; }
        @Override public String getSerializedName() { return name().toLowerCase(Locale.ROOT); }
    }
}
