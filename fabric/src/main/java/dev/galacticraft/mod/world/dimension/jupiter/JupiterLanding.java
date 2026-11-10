package dev.galacticraft.mod.world.dimension.jupiter;

import dev.galacticraft.mod.content.GCBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import java.util.Optional;

/** Bounded landing search. Platforms and pads are placed only in verified empty space. */
public final class JupiterLanding {
    private JupiterLanding() { }

    public static Optional<BlockPos> prepare(ServerLevel level, double sourceX, double sourceZ) {
        int x = Mth.floor(Mth.clamp(sourceX, level.getWorldBorder().getMinX() + 280, level.getWorldBorder().getMaxX() - 280));
        int z = Mth.floor(Mth.clamp(sourceZ, level.getWorldBorder().getMinZ() + 280, level.getWorldBorder().getMaxZ() - 280));
        var generator = level.getChunkSource().getGenerator();
        var random = level.getChunkSource().randomState();
        for (int ring = 0; ring <= 8; ring++) {
            for (int dx = -ring; dx <= ring; dx++) for (int dz = -ring; dz <= ring; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
                int cx = x + dx * 32, cz = z + dz * 32;
                int predicted = generator.getBaseHeight(cx, cz, Heightmap.Types.WORLD_SURFACE_WG, level, random);
                if (predicted < 208 || predicted > 240) continue;
                level.getChunk(cx >> 4, cz >> 4);
                BlockPos site = new BlockPos(cx, level.getHeight(Heightmap.Types.WORLD_SURFACE, cx, cz), cz);
                if (level.getBlockState(site.below()).is(GCBlocks.ROCKET_LAUNCH_PAD)) site = site.below();
                if (hasSupport(level, site) && clear(level, site, 5, 9, true)) {
                    installPad(level, site);
                    return Optional.of(site);
                }
            }
        }
        for (int y = 240; y <= 288; y += 16) {
            for (int shift = 0; shift <= 4; shift++) {
                BlockPos site = new BlockPos(x + shift * 32, y, z);
                if (!clear(level, site.below(), 8, 11, false)) continue;
                for (int dx = -8; dx <= 8; dx++) for (int dz = -8; dz <= 8; dz++)
                    level.setBlockAndUpdate(site.offset(dx, -1, dz), GCBlocks.PACKED_CLOUD.defaultBlockState());
                installPad(level, site);
                return Optional.of(site);
            }
        }
        return Optional.empty();
    }

    private static boolean hasSupport(ServerLevel level, BlockPos site) {
        for (int x = -5; x <= 5; x++) for (int z = -5; z <= 5; z++) {
            BlockPos below = site.offset(x, -1, z);
            if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) return false;
        }
        return true;
    }

    private static boolean clear(ServerLevel level, BlockPos site, int radius, int height, boolean allowPad) {
        for (BlockPos pos : BlockPos.betweenClosed(site.offset(-radius, 0, -radius), site.offset(radius, height - 1, radius))) {
            var state = level.getBlockState(pos);
            if (!state.isAir() && !(allowPad && pos.getY() == site.getY() && state.is(GCBlocks.ROCKET_LAUNCH_PAD))) return false;
        }
        return level.getEntities(null, new AABB(site).inflate(radius, height, radius)).isEmpty();
    }

    private static void installPad(ServerLevel level, BlockPos site) {
        if (level.getBlockState(site).is(GCBlocks.ROCKET_LAUNCH_PAD)) return;
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++)
            level.setBlockAndUpdate(site.offset(x, 0, z), GCBlocks.ROCKET_LAUNCH_PAD.defaultBlockState());
    }
}