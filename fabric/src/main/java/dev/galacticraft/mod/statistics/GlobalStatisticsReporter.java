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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.TickEvent;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.network.s2c.ServerStatisticsPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;

public final class GlobalStatisticsReporter {
    private static final long RETRY_MILLIS = 60_000L;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private static final Map<MinecraftServer, GlobalStatisticsReporter> REPORTERS = new WeakHashMap<>();
    private static boolean initialized;

    private final Configuration configuration;
    private final Path statePath;
    private final UUID sessionId = UUID.randomUUID();
    private PersistentState state;
    private long nextAttemptAt;
    private int peakOnlinePlayers;
    private @Nullable CompletableFuture<HttpResponse<String>> inFlight;

    private GlobalStatisticsReporter(Configuration configuration, Path statePath, PersistentState state) {
        this.configuration = configuration;
        this.statePath = statePath;
        this.state = state;
        this.peakOnlinePlayers = state.pending() == null ? 0 : state.pending().onlinePlayers();
    }

    public static synchronized void init() {
        if (initialized) return;
        initialized = true;
        LifecycleEvent.SERVER_STARTED.register(GlobalStatisticsReporter::start);
        LifecycleEvent.SERVER_STOPPING.register(GlobalStatisticsReporter::stop);
        TickEvent.SERVER_POST.register(GlobalStatisticsReporter::tick);
    }

    private static synchronized void start(MinecraftServer server) {
        Configuration configuration = Configuration.load();
        if (configuration == null) return;

        Path statePath = server.getWorldPath(LevelResource.ROOT)
                .resolve("data")
                .resolve("galacticraft-global-statistics.json");
        PersistentState state;
        try {
            state = loadState(statePath, configuration.serverId());
        } catch (IOException | RuntimeException exception) {
            Constant.LOGGER.error(
                    "Global statistic reporting is disabled because {} could not be read.",
                    statePath,
                    exception
            );
            return;
        }

        REPORTERS.put(server, new GlobalStatisticsReporter(configuration, statePath, state));
        Constant.LOGGER.info(
                "Secure global statistic reporting is enabled for server {}.",
                configuration.serverId()
        );
    }

    private static synchronized void tick(MinecraftServer server) {
        GlobalStatisticsReporter reporter = REPORTERS.get(server);
        if (reporter != null) reporter.tickReporter(server);
    }

    private static synchronized void stop(MinecraftServer server) {
        GlobalStatisticsReporter reporter = REPORTERS.remove(server);
        if (reporter != null) reporter.submitAtShutdown(server);
    }

    private void tickReporter(MinecraftServer server) {
        this.peakOnlinePlayers = Math.max(this.peakOnlinePlayers, server.getPlayerList().getPlayerCount());
        long now = System.currentTimeMillis();
        if (this.inFlight != null || now < this.nextAttemptAt) return;

        try {
            ensurePending(server);
            HttpRequest request = createRequest(this.state.pending());
            this.inFlight = HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString());
            this.inFlight.whenComplete((response, error) -> server.execute(() -> {
                this.inFlight = null;
                handleResponse(response, error);
            }));
        } catch (IOException | GeneralSecurityException | RuntimeException exception) {
            Constant.LOGGER.warn("Unable to prepare the global statistic submission.", exception);
            this.nextAttemptAt = now + RETRY_MILLIS;
        }
    }

    private void submitAtShutdown(MinecraftServer server) {
        this.peakOnlinePlayers = Math.max(this.peakOnlinePlayers, server.getPlayerList().getPlayerCount());
        if (this.inFlight != null) {
            Constant.LOGGER.debug("A global statistic submission is already in progress during shutdown.");
            return;
        }
        try {
            ensurePending(server);
            HttpResponse<String> response = HTTP_CLIENT.send(
                    createRequest(this.state.pending()),
                    HttpResponse.BodyHandlers.ofString()
            );
            handleResponse(response, null);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (IOException | GeneralSecurityException | RuntimeException exception) {
            Constant.LOGGER.warn(
                    "The final global statistic submission could not be sent; it will be retried on the next start.",
                    exception
            );
        }
    }

    private void ensurePending(MinecraftServer server) throws IOException, GeneralSecurityException {
        if (this.state.pending() != null) return;

        var collected = ServerStatistics.collect(server);
        var snapshot = new ServerStatisticsPayload(
                Math.max(collected.trackedPlayers(), this.peakOnlinePlayers),
                Math.max(collected.onlinePlayers(), this.peakOnlinePlayers),
                collected.entries()
        );
        GlobalStatisticsSubmission submission = GlobalStatisticsSubmission.create(
                this.configuration.serverId(),
                this.state.acknowledgedSequence() + 1,
                this.sessionId,
                this.state.acknowledgedPayloadHash(),
                modVersion(),
                snapshot,
                this.configuration.privateKey()
        );
        this.state = new PersistentState(
                this.configuration.serverId(),
                this.state.acknowledgedSequence(),
                this.state.acknowledgedPayloadHash(),
                submission
        );
        saveState(this.statePath, this.state);
        this.peakOnlinePlayers = server.getPlayerList().getPlayerCount();
    }

    private HttpRequest createRequest(GlobalStatisticsSubmission submission) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(this.configuration.endpoint())
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(submission), StandardCharsets.UTF_8));
        if (this.configuration.apiKey() != null) {
            builder.header("apikey", this.configuration.apiKey());
            if (this.configuration.apiKey().startsWith("eyJ")) {
                builder.header("Authorization", "Bearer " + this.configuration.apiKey());
            }
        }
        return builder.build();
    }

    private void handleResponse(@Nullable HttpResponse<String> response, @Nullable Throwable error) {
        if (error != null || response == null) {
            Constant.LOGGER.warn("Unable to submit global statistics.", error);
            this.nextAttemptAt = System.currentTimeMillis() + RETRY_MILLIS;
            return;
        }

        try {
            JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject();
            boolean accepted = result.has("accepted") && result.get("accepted").getAsBoolean();
            GlobalStatisticsSubmission pending = this.state.pending();
            if (accepted && pending != null
                    && result.has("sequence")
                    && result.get("sequence").getAsLong() == pending.sequence()
                    && result.has("payload_hash")
                    && result.get("payload_hash").getAsString().equals(pending.payloadHash())) {
                boolean baseline = result.has("baseline") && result.get("baseline").getAsBoolean();
                this.state = new PersistentState(
                        this.configuration.serverId(),
                        pending.sequence(),
                        pending.payloadHash(),
                        null
                );
                saveState(this.statePath, this.state);
                this.nextAttemptAt = System.currentTimeMillis() + this.configuration.intervalMillis();
                if (baseline) {
                    Constant.LOGGER.info(
                            "Global statistic baseline accepted for server {}; existing totals were not imported.",
                            this.configuration.serverId()
                    );
                }
                return;
            }

            String reason = result.has("reason") ? result.get("reason").getAsString() : "unknown";
            if ("timestamp_out_of_window".equals(reason) && pending != null) {
                this.state = new PersistentState(
                        this.configuration.serverId(),
                        this.state.acknowledgedSequence(),
                        this.state.acknowledgedPayloadHash(),
                        null
                );
                saveState(this.statePath, this.state);
            }
            long retry = response.statusCode() == 429
                    ? Math.max(RETRY_MILLIS, Duration.ofMinutes(5).toMillis())
                    : RETRY_MILLIS;
            this.nextAttemptAt = System.currentTimeMillis() + retry;
            Constant.LOGGER.warn(
                    "Global statistic submission was rejected with HTTP {} ({}).",
                    response.statusCode(),
                    reason
            );
        } catch (IOException | RuntimeException exception) {
            Constant.LOGGER.warn(
                    "Unable to process the global statistic service response (HTTP {}).",
                    response.statusCode(),
                    exception
            );
            this.nextAttemptAt = System.currentTimeMillis() + RETRY_MILLIS;
        }
    }

    private static PersistentState loadState(Path path, UUID serverId) throws IOException {
        if (!Files.exists(path)) return new PersistentState(serverId, 0, null, null);
        PersistentState state = GSON.fromJson(Files.readString(path), PersistentState.class);
        if (state == null || state.acknowledgedSequence() < 0) {
            throw new IOException("Invalid global statistic state");
        }
        if (state.serverId() == null && state.acknowledgedSequence() == 0 && state.pending() == null) {
            return new PersistentState(serverId, 0, null, null);
        }
        if (!serverId.equals(state.serverId())
                || state.pending() != null && !serverId.equals(state.pending().serverId())) {
            throw new IOException("Global statistic state belongs to a different server ID");
        }
        return state;
    }

    private static void saveState(Path path, PersistentState state) throws IOException {
        Files.createDirectories(path.getParent());
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(temporary, GSON.toJson(state), StandardCharsets.UTF_8);
        try {
            Files.move(
                    temporary,
                    path,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
            );
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String modVersion() {
        String implementationVersion = GlobalStatisticsReporter.class.getPackage().getImplementationVersion();
        return implementationVersion == null || implementationVersion.isBlank()
                ? "development"
                : implementationVersion;
    }

    private record PersistentState(
            @Nullable UUID serverId,
            long acknowledgedSequence,
            @Nullable String acknowledgedPayloadHash,
            @Nullable GlobalStatisticsSubmission pending
    ) {
    }

    record Configuration(
            URI endpoint,
            UUID serverId,
            PrivateKey privateKey,
            @Nullable String apiKey,
            long intervalMillis
    ) {
        private static final String ENABLED_PROPERTY = "galacticraft.globalStats.enabled";
        private static final String ENDPOINT_PROPERTY = "galacticraft.globalStats.endpoint";
        private static final String SERVER_ID_PROPERTY = "galacticraft.globalStats.serverId";
        private static final String PRIVATE_KEY_PROPERTY = "galacticraft.globalStats.privateKey";
        private static final String PRIVATE_KEY_FILE_PROPERTY = "galacticraft.globalStats.privateKeyFile";
        private static final String INTERVAL_PROPERTY = "galacticraft.globalStats.intervalSeconds";

        private static final String ENABLED_ENVIRONMENT = "GALACTICRAFT_GLOBAL_STATS_ENABLED";
        private static final String ENDPOINT_ENVIRONMENT = "GALACTICRAFT_GLOBAL_STATS_ENDPOINT";
        private static final String SERVER_ID_ENVIRONMENT = "GALACTICRAFT_GLOBAL_STATS_SERVER_ID";
        private static final String PRIVATE_KEY_ENVIRONMENT = "GALACTICRAFT_GLOBAL_STATS_PRIVATE_KEY";
        private static final String PRIVATE_KEY_FILE_ENVIRONMENT = "GALACTICRAFT_GLOBAL_STATS_PRIVATE_KEY_FILE";
        private static final String INTERVAL_ENVIRONMENT = "GALACTICRAFT_GLOBAL_STATS_INTERVAL_SECONDS";

        private static @Nullable Configuration load() {
            if (!Boolean.parseBoolean(value(ENABLED_PROPERTY, ENABLED_ENVIRONMENT))) return null;

            try {
                String endpoint = value(ENDPOINT_PROPERTY, ENDPOINT_ENVIRONMENT);
                if (endpoint == null) {
                    String baseUrl = value("galacticraft.supabase.url", "GALACTICRAFT_SUPABASE_URL");
                    if (baseUrl == null) baseUrl = SupabaseGlobalStatistics.DEFAULT_URL;
                    if (!baseUrl.isBlank()) {
                        endpoint = baseUrl.replaceFirst("/+$", "") + "/functions/v1/submit-stats";
                    }
                }
                String serverId = value(SERVER_ID_PROPERTY, SERVER_ID_ENVIRONMENT);
                String encodedPrivateKey = value(PRIVATE_KEY_PROPERTY, PRIVATE_KEY_ENVIRONMENT);
                if (encodedPrivateKey == null) {
                    String keyFile = value(PRIVATE_KEY_FILE_PROPERTY, PRIVATE_KEY_FILE_ENVIRONMENT);
                    if (keyFile != null) encodedPrivateKey = Files.readString(Path.of(keyFile)).trim();
                }
                if (endpoint == null || serverId == null || encodedPrivateKey == null) {
                    Constant.LOGGER.error(
                            "Global statistic reporting is enabled but its endpoint, server ID, or private key is missing."
                    );
                    return null;
                }

                encodedPrivateKey = encodedPrivateKey
                        .replace("-----BEGIN PRIVATE KEY-----", "")
                        .replace("-----END PRIVATE KEY-----", "")
                        .replaceAll("\\s", "");
                PrivateKey privateKey = KeyFactory.getInstance("Ed25519").generatePrivate(
                        new PKCS8EncodedKeySpec(Base64.getDecoder().decode(encodedPrivateKey))
                );

                long intervalSeconds = 900;
                String configuredInterval = value(INTERVAL_PROPERTY, INTERVAL_ENVIRONMENT);
                if (configuredInterval != null) intervalSeconds = Long.parseLong(configuredInterval);
                intervalSeconds = Math.max(300, intervalSeconds);

                String apiKey = value("galacticraft.supabase.key", "GALACTICRAFT_SUPABASE_KEY");
                if (apiKey == null) {
                    apiKey = value("galacticraft.supabase.anonKey", "GALACTICRAFT_SUPABASE_ANON_KEY");
                }
                return new Configuration(
                        URI.create(endpoint),
                        UUID.fromString(serverId),
                        privateKey,
                        apiKey,
                        Duration.ofSeconds(intervalSeconds).toMillis()
                );
            } catch (IOException | IllegalArgumentException | GeneralSecurityException exception) {
                Constant.LOGGER.error("Global statistic reporting configuration is invalid.", exception);
                return null;
            }
        }

        private static @Nullable String value(String property, String environment) {
            String value = System.getProperty(property);
            if (value == null || value.isBlank()) value = System.getenv(environment);
            return value == null || value.isBlank() ? null : value.trim();
        }
    }
}
