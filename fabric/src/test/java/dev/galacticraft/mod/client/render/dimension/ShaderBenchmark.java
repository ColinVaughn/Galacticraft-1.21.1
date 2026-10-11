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

package dev.galacticraft.mod.client.render.dimension;

import org.lwjgl.opengl.GL33C;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Locale;

import static org.lwjgl.opengl.GL32C.*;

/** Opt-in GPU timers, excluding CPU submission/readback and VSync. */
final class ShaderBenchmark {
    private ShaderBenchmark() { }

    static void measure(String name, Runnable baseline, Runnable enabled) throws Exception {
        int query = glGenQueries();
        for (int i = 0; i < 100; i++) { baseline.run(); enabled.run(); }
        glFinish();
        double[][] times = new double[2][200];
        for (int i = 0; i < 200; i++) {
            for (int j = 0; j < 2; j++) {
                int mode = (i + j) & 1;
                glBeginQuery(GL33C.GL_TIME_ELAPSED, query);
                (mode == 0 ? baseline : enabled).run();
                glEndQuery(GL33C.GL_TIME_ELAPSED);
                times[mode][i] = GL33C.glGetQueryObjectui64(query, GL_QUERY_RESULT) / 1_000_000.0;
            }
        }
        glDeleteQueries(query);
        for (var samples : times) Arrays.sort(samples);
        String row = String.format(Locale.ROOT, "%s,%.4f,%.4f,%.4f,%.4f,%.4f%n", name,
                times[0][100], times[0][190], times[1][100], times[1][190], times[1][100] - times[0][100]);
        Path path = Path.of("../fabric/build/shader-review/benchmark.csv");
        Files.createDirectories(path.getParent());
        if (!Files.exists(path)) Files.writeString(path, "# GPU: " + glGetString(GL_RENDERER) + "; OpenGL: " + glGetString(GL_VERSION)
                + "\ncase,baseline_median_ms,baseline_p95_ms,enabled_median_ms,enabled_p95_ms,median_delta_ms\n");
        Files.writeString(path, row, StandardOpenOption.APPEND);
        System.out.print(row);
    }
}
