/*
 * Copyright (c) 2019-2026 Team Galacticraft
 * Copyright (c) 2026 Colin Vaughn
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

package dev.galacticraft.mod.statistics;

import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.content.GCStats;
import dev.galacticraft.mod.network.s2c.ServerStatisticsPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.stats.Stats;
import net.minecraft.stats.StatsCounter;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.stream.Stream;

public final class ServerStatistics {
    private static final long CACHE_MILLIS = 5_000L;
    private static final Map<MinecraftServer, CachedSnapshot> CACHE = new WeakHashMap<>();

    private ServerStatistics() {
    }

    public static synchronized ServerStatisticsPayload collect(MinecraftServer server) {
        long now = System.currentTimeMillis();
        CachedSnapshot cached = CACHE.get(server);
        if (cached != null && now - cached.createdAt() < CACHE_MILLIS) {
            return cached.payload();
        }

        Map<UUID, StatsCounter> counters = new HashMap<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            counters.put(player.getUUID(), player.getStats());
        }

        Path statsDirectory = server.getWorldPath(LevelResource.PLAYER_STATS_DIR);
        if (Files.isDirectory(statsDirectory)) {
            try (Stream<Path> files = Files.list(statsDirectory)) {
                files.filter(path -> path.getFileName().toString().endsWith(".json"))
                        .forEach(path -> loadSavedCounter(server, path, counters));
            } catch (IOException exception) {
                Constant.LOGGER.warn("Unable to read saved player statistics from {}.", statsDirectory, exception);
            }
        }

        List<ServerStatisticsPayload.Entry> totals = GCStats.ALL.stream()
                .map(stat -> new ServerStatisticsPayload.Entry(stat, sum(counters.values(), stat)))
                .toList();
        ServerStatisticsPayload payload = new ServerStatisticsPayload(
                counters.size(),
                server.getPlayerList().getPlayerCount(),
                totals
        );
        CACHE.put(server, new CachedSnapshot(now, payload));
        return payload;
    }

    private static void loadSavedCounter(MinecraftServer server, Path path, Map<UUID, StatsCounter> counters) {
        String fileName = path.getFileName().toString();
        try {
            UUID uuid = UUID.fromString(fileName.substring(0, fileName.length() - ".json".length()));
            counters.computeIfAbsent(uuid, ignored -> new ServerStatsCounter(server, path.toFile()));
        } catch (IllegalArgumentException exception) {
            Constant.LOGGER.debug("Ignoring player statistics file with an invalid UUID: {}", path);
        }
    }

    private static long sum(Iterable<StatsCounter> counters, net.minecraft.resources.ResourceLocation stat) {
        long total = 0L;
        for (StatsCounter counter : counters) {
            total += counter.getValue(Stats.CUSTOM, stat);
        }
        return total;
    }

    private record CachedSnapshot(long createdAt, ServerStatisticsPayload payload) {
    }
}
