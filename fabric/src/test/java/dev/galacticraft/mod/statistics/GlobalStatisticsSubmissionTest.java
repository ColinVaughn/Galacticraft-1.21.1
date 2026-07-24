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
import dev.galacticraft.mod.network.s2c.ServerStatisticsPayload;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.util.Base64;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlobalStatisticsSubmissionTest {
    @Test
    void signsTheCanonicalPayloadAndHashesIt() throws Exception {
        var keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        var snapshot = new ServerStatisticsPayload(
                2,
                1,
                List.of(
                        new ServerStatisticsPayload.Entry(Constant.id("safe_landing"), 4),
                        new ServerStatisticsPayload.Entry(Constant.id("launch_rocket"), 3)
                )
        );

        GlobalStatisticsSubmission submission = GlobalStatisticsSubmission.create(
                UUID.fromString("123e4567-e89b-42d3-a456-426614174000"),
                1,
                UUID.fromString("123e4567-e89b-42d3-a456-426614174001"),
                null,
                "5.4.6",
                snapshot,
                keyPair.getPrivate()
        );

        byte[] canonical = submission.canonicalPayload().getBytes(StandardCharsets.UTF_8);
        String expectedHash = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(MessageDigest.getInstance("SHA-256").digest(canonical));
        assertEquals(expectedHash, submission.payloadHash());

        Signature verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(keyPair.getPublic());
        verifier.update(canonical);
        assertTrue(verifier.verify(Base64.getUrlDecoder().decode(submission.signature())));
    }

    @Test
    void canonicalPayloadSortsStatistics() {
        SortedMap<String, String> stats = new TreeMap<>();
        stats.put("galacticraft:safe_landing", "4");
        stats.put("galacticraft:launch_rocket", "3");

        String canonical = GlobalStatisticsSubmission.canonicalPayload(
                UUID.fromString("123e4567-e89b-42d3-a456-426614174000"),
                7,
                1_700_000_000,
                UUID.fromString("123e4567-e89b-42d3-a456-426614174001"),
                null,
                "5.4.6",
                2,
                1,
                stats
        );

        assertEquals("""
                v1
                123e4567-e89b-42d3-a456-426614174000
                7
                1700000000
                123e4567-e89b-42d3-a456-426614174001

                5.4.6
                2
                1
                galacticraft:launch_rocket=3
                galacticraft:safe_landing=4
                """, canonical);
    }
}
