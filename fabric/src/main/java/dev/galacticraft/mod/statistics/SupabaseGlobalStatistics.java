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

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import dev.architectury.networking.NetworkManager;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.content.GCStats;
import dev.galacticraft.mod.network.s2c.GlobalStatisticsPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class SupabaseGlobalStatistics {
    private static final String URL_PROPERTY = "galacticraft.supabase.url";
    private static final String KEY_PROPERTY = "galacticraft.supabase.key";
    private static final String LEGACY_KEY_PROPERTY = "galacticraft.supabase.anonKey";
    private static final String URL_ENVIRONMENT = "GALACTICRAFT_SUPABASE_URL";
    private static final String KEY_ENVIRONMENT = "GALACTICRAFT_SUPABASE_KEY";
    private static final String LEGACY_KEY_ENVIRONMENT = "GALACTICRAFT_SUPABASE_ANON_KEY";
    /**
     * Project defaults used when no property or environment variable overrides them. The
     * publishable key only grants the read-only access that {@code global_stat_totals}
     * exposes to {@code anon} through row level security, so it is safe to distribute.
     * A service-role key must never appear here.
     */
    static final String DEFAULT_URL = "https://pmavhuluiomkidpjyfmd.supabase.co";
    static final String DEFAULT_KEY = "sb_publishable_uhJfsK4yS47W7eIW7YVrsw_Tm5mJp6t";
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private SupabaseGlobalStatistics() {
    }

    public static void request(ServerPlayer player) {
        Configuration configuration = Configuration.load();
        if (configuration == null) {
            NetworkManager.sendToPlayer(player, GlobalStatisticsPayload.notConfigured());
            return;
        }

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(configuration.endpoint())
                .timeout(Duration.ofSeconds(10))
                .header("apikey", configuration.apiKey())
                .header("Accept", "application/json")
                .GET();
        if (configuration.apiKey().startsWith("eyJ")) {
            requestBuilder.header("Authorization", "Bearer " + configuration.apiKey());
        }
        HttpRequest request = requestBuilder.build();

        HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .whenComplete((response, error) -> player.server.execute(() -> {
                    if (player.server.getPlayerList().getPlayer(player.getUUID()) != player) {
                        return;
                    }
                    if (error != null || response.statusCode() < 200 || response.statusCode() >= 300) {
                        if (error != null) {
                            Constant.LOGGER.warn("Unable to retrieve global statistics from Supabase.", error);
                        } else {
                            Constant.LOGGER.warn("Supabase global statistics request returned HTTP {}.", response.statusCode());
                        }
                        NetworkManager.sendToPlayer(player, GlobalStatisticsPayload.error());
                        return;
                    }

                    try {
                        NetworkManager.sendToPlayer(player, new GlobalStatisticsPayload(
                                GlobalStatisticsPayload.Status.AVAILABLE,
                                parseEntries(response.body())
                        ));
                    } catch (RuntimeException exception) {
                        Constant.LOGGER.warn("Unable to parse global statistics returned by Supabase.", exception);
                        NetworkManager.sendToPlayer(player, GlobalStatisticsPayload.error());
                    }
                }));
    }

    static List<GlobalStatisticsPayload.Entry> parseEntries(String json) {
        Map<ResourceLocation, Long> values = new HashMap<>();
        for (JsonElement element : JsonParser.parseString(json).getAsJsonArray()) {
            String id = element.getAsJsonObject().get("stat_id").getAsString();
            ResourceLocation stat = ResourceLocation.tryParse(id);
            if (stat != null && GCStats.ALL.contains(stat)) {
                values.put(stat, Math.max(0L, element.getAsJsonObject().get("total").getAsLong()));
            }
        }

        List<GlobalStatisticsPayload.Entry> entries = new ArrayList<>(GCStats.ALL.size());
        for (ResourceLocation stat : GCStats.ALL) {
            entries.add(new GlobalStatisticsPayload.Entry(stat, values.getOrDefault(stat, 0L)));
        }
        return List.copyOf(entries);
    }

    private record Configuration(URI endpoint, String apiKey) {
        private static Configuration load() {
            String url = value(URL_PROPERTY, URL_ENVIRONMENT);
            String key = value(KEY_PROPERTY, KEY_ENVIRONMENT);
            if (key == null) {
                key = value(LEGACY_KEY_PROPERTY, LEGACY_KEY_ENVIRONMENT);
            }
            if (url == null) {
                url = trimmed(DEFAULT_URL);
            }
            if (key == null) {
                key = trimmed(DEFAULT_KEY);
            }
            if (url == null || key == null) {
                return null;
            }
            String baseUrl = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
            return new Configuration(
                    URI.create(baseUrl + "/rest/v1/global_stat_totals?select=stat_id,total&order=total.desc"),
                    key
            );
        }

        private static String value(String property, String environment) {
            String value = System.getProperty(property);
            if (value == null || value.isBlank()) {
                value = System.getenv(environment);
            }
            return trimmed(value);
        }

        private static String trimmed(String value) {
            return value == null || value.isBlank() ? null : value.trim();
        }
    }
}
