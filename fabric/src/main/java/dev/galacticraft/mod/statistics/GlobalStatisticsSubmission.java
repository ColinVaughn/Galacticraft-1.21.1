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

import dev.galacticraft.mod.network.s2c.ServerStatisticsPayload;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;

record GlobalStatisticsSubmission(
        int version,
        UUID serverId,
        long sequence,
        long generatedAt,
        UUID sessionId,
        @Nullable String previousHash,
        String modVersion,
        int trackedPlayers,
        int onlinePlayers,
        SortedMap<String, String> stats,
        String payloadHash,
        String signature
) {
    static final int PROTOCOL_VERSION = 1;

    GlobalStatisticsSubmission {
        stats = Collections.unmodifiableSortedMap(new TreeMap<>(stats));
    }

    static GlobalStatisticsSubmission create(
            UUID serverId,
            long sequence,
            UUID sessionId,
            @Nullable String previousHash,
            String modVersion,
            ServerStatisticsPayload snapshot,
            PrivateKey privateKey
    ) throws GeneralSecurityException {
        SortedMap<String, String> stats = new TreeMap<>();
        for (ServerStatisticsPayload.Entry entry : snapshot.entries()) {
            stats.put(entry.stat().toString(), Long.toString(entry.total()));
        }

        long generatedAt = Instant.now().getEpochSecond();
        String canonical = canonicalPayload(
                serverId,
                sequence,
                generatedAt,
                sessionId,
                previousHash,
                modVersion,
                snapshot.trackedPlayers(),
                snapshot.onlinePlayers(),
                stats
        );
        byte[] canonicalBytes = canonical.getBytes(StandardCharsets.UTF_8);
        String payloadHash = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(MessageDigest.getInstance("SHA-256").digest(canonicalBytes));
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(privateKey);
        signer.update(canonicalBytes);
        String signature = Base64.getUrlEncoder().withoutPadding().encodeToString(signer.sign());

        return new GlobalStatisticsSubmission(
                PROTOCOL_VERSION,
                serverId,
                sequence,
                generatedAt,
                sessionId,
                previousHash,
                modVersion,
                snapshot.trackedPlayers(),
                snapshot.onlinePlayers(),
                stats,
                payloadHash,
                signature
        );
    }

    String canonicalPayload() {
        return canonicalPayload(
                this.serverId,
                this.sequence,
                this.generatedAt,
                this.sessionId,
                this.previousHash,
                this.modVersion,
                this.trackedPlayers,
                this.onlinePlayers,
                this.stats
        );
    }

    static String canonicalPayload(
            UUID serverId,
            long sequence,
            long generatedAt,
            UUID sessionId,
            @Nullable String previousHash,
            String modVersion,
            int trackedPlayers,
            int onlinePlayers,
            SortedMap<String, String> stats
    ) {
        StringBuilder builder = new StringBuilder(512)
                .append("v1\n")
                .append(serverId).append('\n')
                .append(sequence).append('\n')
                .append(generatedAt).append('\n')
                .append(sessionId).append('\n')
                .append(previousHash == null ? "" : previousHash).append('\n')
                .append(modVersion).append('\n')
                .append(trackedPlayers).append('\n')
                .append(onlinePlayers).append('\n');
        stats.forEach((stat, total) -> builder.append(stat).append('=').append(total).append('\n'));
        return builder.toString();
    }
}
