package dev.galacticraft.mod.world.gen.carver;

import dev.galacticraft.mod.content.GCBlocks;
import dev.galacticraft.mod.content.block.environment.LunarFormationBlock;
import dev.galacticraft.mod.world.biome.GCBiomes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

/** A complete room description, shared by every chunk that intersects it. */
record LunarChamber(double x, double y, double z, double radius, double height,
                    double heading, Kind kind, long seed) {
    enum Kind { COLLAPSE, COLD_TRAP, IMPACT_CORE, OLIVINE, CHEESE, HELION }

    static Kind choose(Holder<Biome> biome, RandomSource random) {
        if (random.nextFloat() < 0.55F) {
            if (biome.is(GCBiomes.Moon.COMET_TUNDRA)) return Kind.COLD_TRAP;
            if (biome.is(GCBiomes.Moon.RAY_CRATER_FIELD)) return Kind.IMPACT_CORE;
            if (biome.is(GCBiomes.Moon.OLIVINE_SPIKES)) return Kind.OLIVINE;
            if (biome.is(GCBiomes.Moon.CHEESE_GROVE)) return Kind.CHEESE;
        }
        int choice = random.nextInt(10);
        return choice < 2 ? Kind.IMPACT_CORE : choice == 2 ? Kind.COLD_TRAP
                : choice < 5 ? Kind.HELION : Kind.COLLAPSE;
    }

    private double phase() { return (seed & 255) * (Math.PI * 2 / 256); }

    private double bend(double along) {
        return Math.sin(along * 4.5 + phase()) * (kind == Kind.OLIVINE || kind == Kind.CHEESE ? 0.22 : 0.10);
    }

    private double width(double along) {
        double irregularity = Math.sin(along * 7 + phase()) * 0.045;
        double width = switch (kind) {
            case COLLAPSE -> 0.48 + irregularity;
            case COLD_TRAP -> 0.25 + irregularity * 0.5;
            case IMPACT_CORE -> 0.62 + irregularity;
            case OLIVINE -> 0.23 + irregularity;
            case CHEESE -> 0.42 + Math.sin(along * 5 + phase()) * 0.12;
            case HELION -> 0.52 + irregularity;
        };
        return width * (1 - Mth.clamp((Math.abs(along) - 0.60) / 0.28, 0, 1) * 0.50);
    }

    double floorLimit(double nx, double nz) {
        double along = Math.cos(heading) * nx + Math.sin(heading) * nz;
        double across = -Math.sin(heading) * nx + Math.cos(heading) * nz - bend(along);
        return floorAt(along, across);
    }

    private double floorAt(double along, double across) {
        double endRamp = Math.min(1, Math.abs(along) / 0.84);
        return switch (kind) {
            case COLLAPSE -> Math.max(-0.58 + 0.12 * Math.sin(along * 4 + phase())
                    + 0.22 * Mth.square(across / width(along)), -0.60 + endRamp * 0.36);
            case COLD_TRAP -> Math.max(-0.86 + endRamp * 0.64, across > 0.13 ? -0.38 : -1);
            case IMPACT_CORE -> -0.78 + endRamp * 0.51 + across * 0.14;
            case OLIVINE -> -0.60 + endRamp * 0.34 + along * 0.10
                    + Mth.clamp((across - 0.15) / 0.32, 0, 1) * 0.22;
            case CHEESE -> -0.55 + endRamp * 0.29 + Math.sin(along * 5 + phase()) * 0.055;
            case HELION -> across < 0.16 ? -0.20 + along * 0.09
                    : -0.86 + Mth.clamp((Math.abs(along) - 0.45) / 0.40, 0, 1) * 0.64;
        };
    }

    boolean skip(double nx, double ny, double nz) {
        double along = Math.cos(heading) * nx + Math.sin(heading) * nz;
        double across = -Math.sin(heading) * nx + Math.cos(heading) * nz - bend(along);
        double width = width(along);
        if (nx * nx + nz * nz >= 0.98 || Math.abs(along) > 0.88) return true;
        boolean footprint = Math.abs(across) < width;
        if (kind == Kind.OLIVINE) {
            // Two offset galleries reconnect through windows, retaining the stone between them.
            footprint |= along > -0.30 && along < 0.65 && Math.abs(across - 0.43) < 0.14;
            footprint |= (Math.abs(along + 0.22) < 0.11 || Math.abs(along - 0.55) < 0.11)
                    && across > 0 && across < 0.55;
        }
        if (!footprint) return true;
        if (kind == Kind.COLLAPSE && (Math.abs(along + 0.30) < 0.13 && Math.abs(across + 0.25) < 0.14
                || Math.abs(along - 0.28) < 0.13 && Math.abs(across - 0.25) < 0.14)) return true;
        if (kind == Kind.IMPACT_CORE && Mth.square(along - 0.06) / 0.075
                + Mth.square(across) / 0.055 < 1 && ny < 0.72) return true;
        if (kind == Kind.CHEESE && Math.abs(along - 0.24) < 0.12
                && across < -0.12 && ny > -0.75) return true;

        double floor = floorAt(along, across);
        double ceiling = switch (kind) {
            case COLLAPSE -> 0.60 + Math.sin(along * 5 + phase()) * 0.12 - Mth.square(across / width) * 0.18;
            case COLD_TRAP -> 0.94 - Math.abs(across / width) * 0.15;
            case IMPACT_CORE -> 0.60 + Math.sin(along * 3 + phase()) * 0.13;
            case OLIVINE -> (across > 0.26 ? 0.32 : 0.55) + Math.cos(along * 5 + phase()) * 0.07;
            case CHEESE -> 0.09 + Math.sin(along * 7 + phase()) * 0.055;
            case HELION -> across < 0.16 || Math.abs(along) > 0.55 || along > 0.25 && along < 0.45 ? 0.60 : -0.10;
        };
        if (kind == Kind.HELION && Math.abs(along) < 0.11 && across > 0.16) {
            // A retained stone bridge separates the upper crossing from the lower passage.
            return !(ny > -0.20 && ny < 0.55 || ny > floor && ny < -0.40);
        }
        return ny <= floor || ny >= ceiling;
    }

    static boolean protectedStone(java.util.List<LunarChamber> rooms, double wx, double wy, double wz) {
        for (LunarChamber room : rooms) {
            double nx = (wx - room.x) / room.radius, nz = (wz - room.z) / room.radius;
            double along = Math.cos(room.heading) * nx + Math.sin(room.heading) * nz;
            double across = -Math.sin(room.heading) * nx + Math.cos(room.heading) * nz;
            double ny = (wy - room.y) / room.height;
            // Preserve the room's pillars, ledges and ceilings when an approach cuts through it.
            if (Math.abs(along) < 0.78 && Math.abs(across) < 0.85 && ny > -0.95 && ny < 0.75
                    && room.skip(nx, ny, nz)) return true;
        }
        return false;
    }

    static long positionSeed(long seed, int x, int z) {
        long value = seed ^ (long) x * 341873128712L ^ (long) z * 132897987541L;
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }

    static boolean inside(ChunkAccess chunk, int x, int z) {
        return chunk.getPos().equals(new ChunkPos(x >> 4, z >> 4));
    }

    static boolean stone(BlockState state) {
        return state.is(GCBlocks.MOON_ROCK) || state.is(GCBlocks.LUNASLATE)
                || state.is(GCBlocks.MOON_BASALT) || state.is(GCBlocks.MOON_SURFACE_ROCK)
                || state.is(GCBlocks.VESICULAR_LUNAR_BASALT) || state.is(GCBlocks.ICE_BEARING_BRECCIA);
    }

    static BlockPos floor(ChunkAccess chunk, int x, int z, double y, double height) {
        if (!inside(chunk, x, z)) return null;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, 0, z);
        int top = Math.min(chunk.getMaxBuildHeight() - 3, Mth.floor(y + height));
        int bottom = Math.max(chunk.getMinBuildHeight() + 2, Mth.floor(y - height - 2));
        for (int scan = top; scan >= bottom; scan--) {
            pos.setY(scan);
            if (chunk.getBlockState(pos).isAir() && chunk.getBlockState(pos.above()).isAir()
                    && stone(chunk.getBlockState(pos.below()))) return pos.immutable();
        }
        return null;
    }

    static void formation(ChunkAccess chunk, BlockPos pos, Block block, Direction facing) {
        if (!inside(chunk, pos.getX(), pos.getZ()) || !chunk.getBlockState(pos).isAir()) return;
        var state = block.defaultBlockState();
        if (state.hasProperty(LunarFormationBlock.FACING)) state = state.setValue(LunarFormationBlock.FACING, facing);
        Direction support = block == GCBlocks.LUNAR_CEILING_BREAKDOWN ? Direction.UP : Direction.DOWN;
        BlockPos anchor = pos.relative(support);
        if (chunk.getBlockState(anchor).isFaceSturdy(chunk, anchor, support.getOpposite())) {
            chunk.setBlockState(pos, state, false);
        }
    }

    void decorate(ChunkAccess chunk) {
        int minX = Math.max(chunk.getPos().getMinBlockX(), Mth.floor(x - radius));
        int maxX = Math.min(chunk.getPos().getMaxBlockX(), Mth.ceil(x + radius));
        int minZ = Math.max(chunk.getPos().getMinBlockZ(), Mth.floor(z - radius));
        int maxZ = Math.min(chunk.getPos().getMaxBlockZ(), Mth.ceil(z + radius));
        for (int bx = minX; bx <= maxX; bx++) for (int bz = minZ; bz <= maxZ; bz++) {
            decorateColumn(chunk, bx, bz);
        }
    }

    private void decorateColumn(ChunkAccess chunk, int bx, int bz) {
        double nx = (bx + 0.5 - x) / radius, nz = (bz + 0.5 - z) / radius;
        double distance = Math.sqrt(nx * nx + nz * nz);
        if (distance > 0.92) return;
        RandomSource random = RandomSource.create(positionSeed(seed, bx, bz));
        BlockPos floor = floor(chunk, bx, bz, y, height);
        if (floor == null || skip(nx, (floor.getY() + 0.5 - y) / height, nz)) return;
        double across = -Math.sin(heading) * (bx + 0.5 - x) + Math.cos(heading) * (bz + 0.5 - z);
        double along = Math.cos(heading) * nx + Math.sin(heading) * nz;
        double localAcross = across / radius - bend(along);
        boolean lane = Math.abs(localAcross * radius) < 1.8;
        boolean margin = Math.abs(localAcross) > width(along) - 0.13;
        Direction facing = Direction.from2DDataValue(random.nextInt(4));
        decorateDeposit(chunk, floor, along, localAcross, lane, margin, random, facing);
        boolean breakdown = kind == Kind.COLLAPSE && Math.abs(along) < 0.48 && Math.abs(localAcross) > 0.14;
        if (!lane && (margin || breakdown) && random.nextInt(breakdown ? 8 : 24) == 0) {
            formation(chunk, floor, kind == Kind.COLD_TRAP ? GCBlocks.LUNAR_FROSTED_BREAKDOWN_RUBBLE
                    : kind == Kind.COLLAPSE && random.nextBoolean() ? GCBlocks.LUNAR_FALLEN_ROOF_SLAB
                    : GCBlocks.LUNAR_SCATTERED_FRAGMENTS, facing);
        }
        if (kind == Kind.COLLAPSE && !lane && (margin || breakdown) && random.nextInt(13) == 0) {
            decorateCeiling(chunk, bx, bz, facing);
        }
    }

    private void decorateDeposit(ChunkAccess chunk, BlockPos floor, double along, double localAcross,
                                 boolean lane, boolean margin, RandomSource random, Direction facing) {
        switch (kind) {
            case COLD_TRAP -> decorateColdTrap(chunk, floor, along, localAcross, lane, random);
            case IMPACT_CORE -> decorateImpactCore(chunk, floor, along, localAcross, lane);
            case COLLAPSE -> {
                if (margin && along > 0.35 && random.nextInt(4) == 0) {
                    chunk.setBlockState(floor.below(), GCBlocks.LUNAR_SAPPHIRE_ORE.defaultBlockState(), false);
                }
            }
            case OLIVINE -> {
                if (Math.abs(along - 0.36) < 0.24 && localAcross > 0.25 && random.nextInt(5) == 0) {
                    chunk.setBlockState(floor.below(), GCBlocks.BUDDING_OLIVINE.defaultBlockState(), false);
                    formation(chunk, floor, GCBlocks.OLIVINE_CLUSTER, facing);
                }
            }
            case CHEESE -> {
                if (Math.abs(along + 0.38) < 0.24 && localAcross < -0.08 && random.nextInt(3) == 0) {
                    chunk.setBlockState(floor.below(), random.nextInt(5) == 0
                            ? GCBlocks.MOON_CHEESE_BLOCK.defaultBlockState() : GCBlocks.MOON_MOSS.defaultBlockState(), false);
                    if (!lane) chunk.setBlockState(floor, GCBlocks.MOON_SHRUBS.defaultBlockState(), false);
                }
            }
            case HELION -> {
                if (Math.abs(along - 0.38) < 0.23 && localAcross > 0.24 && random.nextInt(7) == 0) {
                    formation(chunk, floor, random.nextInt(4) == 0 ? GCBlocks.HELIUM_3_VENT : GCBlocks.HELION_NODULES, facing);
                }
            }
        }
    }

    private void decorateColdTrap(ChunkAccess chunk, BlockPos floor, double along, double localAcross,
                                  boolean lane, RandomSource random) {
        if (random.nextDouble() < 0.85 - Math.abs(along) * 0.65) {
            chunk.setBlockState(floor.below(), Math.abs(along) < 0.30 && localAcross < 0.13 ? GCBlocks.DENSE_ICE.defaultBlockState()
                    : GCBlocks.ICE_BEARING_BRECCIA.defaultBlockState(), false);
            if (!lane && Math.abs(along) < 0.25 && random.nextInt(14) == 0) {
                chunk.setBlockState(floor, GCBlocks.DENSE_ICE.defaultBlockState(), false);
            }
        }
    }

    private void decorateImpactCore(ChunkAccess chunk, BlockPos floor, double along, double localAcross, boolean lane) {
        if (Math.abs(along) < 0.42) chunk.setBlockState(floor.below(), GCBlocks.MOON_BASALT.defaultBlockState(), false);
        if (Mth.square(along - 0.06) + Mth.square(localAcross) < 0.17 && localAcross > 0.15) {
            chunk.setBlockState(floor.below(), GCBlocks.FALLEN_METEOR.defaultBlockState(), false);
            if (!lane) chunk.setBlockState(floor, GCBlocks.FALLEN_METEOR.defaultBlockState(), false);
        }
    }

    private void decorateCeiling(ChunkAccess chunk, int bx, int bz, Direction facing) {
        for (int roofY = Mth.floor(y); roofY <= Math.min(chunk.getMaxBuildHeight() - 2, Mth.ceil(y + height)); roofY++) {
            BlockPos roof = new BlockPos(bx, roofY, bz);
            if (chunk.getBlockState(roof).isAir() && stone(chunk.getBlockState(roof.above()))) {
                formation(chunk, roof, GCBlocks.LUNAR_CEILING_BREAKDOWN, facing);
                break;
            }
        }
    }

    void clue(ChunkAccess chunk, double cx, double cy, double cz, double progress) {
        int bx = Mth.floor(cx), bz = Mth.floor(cz);
        RandomSource random = RandomSource.create(positionSeed(seed, bx, bz));
        if (random.nextDouble() > progress * 0.85) return;
        BlockPos floor = floor(chunk, bx, bz, cy, 5);
        if (floor == null) return;
        if (kind == Kind.COLD_TRAP) {
            chunk.setBlockState(floor.below(), GCBlocks.ICE_BEARING_BRECCIA.defaultBlockState(), false);
        } else if (kind == Kind.HELION) {
            BlockPos margin = floor(chunk, bx + Mth.floor(-Math.sin(heading) * 2.5),
                    bz + Mth.floor(Math.cos(heading) * 2.5), cy, 5);
            if (margin != null) formation(chunk, margin, GCBlocks.HELION_NODULES, Direction.NORTH);
        } else {
            formation(chunk, floor, GCBlocks.LUNAR_SCATTERED_FRAGMENTS, Direction.NORTH);
        }
    }
}
