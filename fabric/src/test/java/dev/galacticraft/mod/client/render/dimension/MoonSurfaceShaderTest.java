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

import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.lwjgl.opengl.GL;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL32C.*;

class MoonSurfaceShaderTest {
    @Test
    void worldCoordinatesKeepSubBlockDetailAcrossWrapsAndWorldBorder() {
        for (double coordinate : new double[]{-30_000_000.125, -4096.125, -0.125, 0.125, 4096.125, 30_000_000.125}) {
            float wrapped = MoonSurfaceShader.wrappedCoordinate(coordinate);
            assertTrue(wrapped >= 0.0F && wrapped < 4096.0F);
            assertEquals(wrapped, MoonSurfaceShader.wrappedCoordinate(coordinate + 4096.0));
            assertEquals(coordinate - Math.floor(coordinate), wrapped - Math.floor(wrapped));
        }
    }

    /** Uses Minecraft's existing LWJGL dependencies; opt in on a machine with an OpenGL context. */
    @Test
    @EnabledIfEnvironmentVariable(named = "GC_SHADER_GL_TEST", matches = "1")
    void shaderCompilesAndPreservesSkyColorDepthAndDisabledOutput() throws Exception {
        assertTrue(glfwInit());
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 2);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        long window = glfwCreateWindow(64, 64, "Lunar shader check", 0, 0);
        assertNotEquals(0, window);
        try {
            glfwMakeContextCurrent(window);
            GL.createCapabilities();
            int program = glCreateProgram();
            glAttachShader(program, compile(GL_VERTEX_SHADER, """
                    #version 150
                    out vec2 texCoord;
                    void main() {
                        vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
                        texCoord = p;
                        gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
                    }
                    """));
            try (var resource = getClass().getResourceAsStream("/assets/galacticraft/shaders/program/moon_surface.fsh")) {
                assertNotNull(resource);
                glAttachShader(program, compile(GL_FRAGMENT_SHADER, new String(resource.readAllBytes(), StandardCharsets.UTF_8)));
            }
            glLinkProgram(program);
            assertEquals(GL_TRUE, glGetProgrami(program, GL_LINK_STATUS), glGetProgramInfoLog(program));
            glUseProgram(program);
            glBindVertexArray(glGenVertexArrays());

            float[] colors = new float[64 * 64 * 4];
            Arrays.fill(colors, 0.45F);
            for (int i = 3; i < colors.length; i += 4) colors[i] = 1.0F;
            float[] depths = new float[64 * 64];
            Arrays.fill(depths, 0.99F);
            for (int pixel = 0; pixel < 64; pixel++) {
                depths[pixel] = 1.0F; // sky with visible stars
                colors[(64 + pixel) * 4] = 0.9F; // strongly colored ore/machine
                colors[(64 + pixel) * 4 + 1] = 0.1F;
                colors[(64 + pixel) * 4 + 2] = 0.1F;
                for (int channel = 0; channel < 3; channel++) colors[(128 + pixel) * 4 + channel] = 0.015F; // cave
            }
            glActiveTexture(GL_TEXTURE0);
            texture(GL_RGBA32F, GL_RGBA, colors);
            glUniform1i(glGetUniformLocation(program, "DiffuseSampler"), 0);
            glActiveTexture(GL_TEXTURE1);
            texture(GL_DEPTH_COMPONENT32F, GL_DEPTH_COMPONENT, depths);
            glUniform1i(glGetUniformLocation(program, "DepthSampler"), 1);
            glActiveTexture(GL_TEXTURE2);
            int output = texture(GL_RGBA32F, GL_RGBA, null);
            int outputDepth = texture(GL_DEPTH_COMPONENT32F, GL_DEPTH_COMPONENT, null);
            glBindFramebuffer(GL_FRAMEBUFFER, glGenFramebuffers());
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, output, 0);
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, outputDepth, 0);
            assertEquals(GL_FRAMEBUFFER_COMPLETE, glCheckFramebufferStatus(GL_FRAMEBUFFER));
            glViewport(0, 0, 64, 64);
            glEnable(GL_DEPTH_TEST);
            glDepthFunc(GL_ALWAYS);
            glUniformMatrix4fv(glGetUniformLocation(program, "InverseViewProjection"), false,
                    new Matrix4f().perspective((float) Math.toRadians(60), 1, 0.05F, 2048).invert().get(new float[16]));
            glUniform3f(glGetUniformLocation(program, "SunDirection"), 0, 0.8F, 0.6F);

            glUniform1f(glGetUniformLocation(program, "Strength"), 0);
            float[] disabled = draw();
            assertArrayEquals(colors, disabled, 0.00001F, "Disabled/copy pass must be lossless");
            glUniform1f(glGetUniformLocation(program, "Strength"), 1);
            float[] enabled = draw();
            assertArrayEquals(Arrays.copyOf(colors, 64 * 3 * 4), Arrays.copyOf(enabled, 64 * 3 * 4), 0.00001F,
                    "Sky, colored materials and dark caves must retain their authored output");
            double minimum = 1, maximum = 0;
            for (int i = 64 * 3 * 4; i < enabled.length; i += 4) {
                assertTrue(Float.isFinite(enabled[i]));
                assertTrue(enabled[i] >= 0 && enabled[i] <= 1);
                minimum = Math.min(minimum, enabled[i]);
                maximum = Math.max(maximum, enabled[i]);
            }
            assertTrue(maximum - minimum > 0.01, "Flat gray terrain must gain spatial variation");
            assertFalse(Arrays.equals(disabled, enabled));
            glUniform3f(glGetUniformLocation(program, "CameraOffset"), 4096, 0, 0);
            assertArrayEquals(enabled, draw(), 0.0003F, "World-coordinate wrapping must not cause a visible seam");
            glUniform3f(glGetUniformLocation(program, "SunDirection"), 0, 0.8F, -0.6F);
            float[] unlitSlope = draw();
            assertTrue(enabled[2048 * 4] > unlitSlope[2048 * 4] + 0.02F,
                    "A sun-facing slope must be brighter than a slope facing away");
            float[] actualDepth = new float[depths.length];
            glReadPixels(0, 0, 64, 64, GL_DEPTH_COMPONENT, GL_FLOAT, actualDepth);
            assertArrayEquals(depths, actualDepth, 0.000001F, "Both passes must retain world depth");
            if ("1".equals(System.getenv("GC_SHADER_BENCHMARK"))) {
                for (int[] resolution : new int[][]{{1920, 1080}, {2560, 1440}}) {
                    glActiveTexture(GL_TEXTURE2);
                    glBindTexture(GL_TEXTURE_2D, output);
                    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, resolution[0], resolution[1], 0, GL_RGBA, GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);
                    glBindTexture(GL_TEXTURE_2D, outputDepth);
                    glTexImage2D(GL_TEXTURE_2D, 0, GL_DEPTH_COMPONENT32F, resolution[0], resolution[1], 0, GL_DEPTH_COMPONENT, GL_FLOAT, (java.nio.FloatBuffer) null);
                    glViewport(0, 0, resolution[0], resolution[1]);
                    ShaderBenchmark.measure("moon-post-shade-plus-copy-" + resolution[0] + "x" + resolution[1], () -> { }, () -> {
                        glUniform1f(glGetUniformLocation(program, "Strength"), 1);
                        glDrawArrays(GL_TRIANGLES, 0, 3);
                        glUniform1f(glGetUniformLocation(program, "Strength"), 0);
                        glDrawArrays(GL_TRIANGLES, 0, 3);
                    });
                }
            }
            assertEquals(GL_NO_ERROR, glGetError());
        } finally {
            glfwDestroyWindow(window);
            glfwTerminate();
            GL.setCapabilities(null);
        }
    }

    static int compile(int type, String source) {
        int shader = glCreateShader(type);
        glShaderSource(shader, source);
        glCompileShader(shader);
        assertEquals(GL_TRUE, glGetShaderi(shader, GL_COMPILE_STATUS), glGetShaderInfoLog(shader));
        return shader;
    }

    static int texture(int internalFormat, int format, float[] data) {
        int texture = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, texture);
        glTexImage2D(GL_TEXTURE_2D, 0, internalFormat, 64, 64, 0, format, GL_FLOAT, data);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        return texture;
    }

    static float[] draw() {
        glDrawArrays(GL_TRIANGLES, 0, 3);
        float[] result = new float[64 * 64 * 4];
        glReadPixels(0, 0, 64, 64, GL_RGBA, GL_FLOAT, result);
        return result;
    }
}
