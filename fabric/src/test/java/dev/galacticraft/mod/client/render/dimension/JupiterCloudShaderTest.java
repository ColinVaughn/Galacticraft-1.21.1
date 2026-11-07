package dev.galacticraft.mod.client.render.dimension;

import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL32C.*;

class JupiterCloudShaderTest {
    @Test
    void atlasAddressingWrapsNegativeCoordinatesWithoutAliasingSlices() {
        for (int x : new int[]{-30_000_000, -257, -256, -1, 0, 1, 15, 16, 255, 256, 30_000_000}) {
            for (int z : new int[]{-30_000_000, -257, -256, -1, 0, 15, 16, 255, 256, 30_000_000}) {
                for (int y : new int[]{-64, 0, 192, 222, 319}) {
                    int u = JupiterCloudRenderer.atlasX(x, z), v = JupiterCloudRenderer.atlasY(y, z);
                    assertTrue(u >= 0 && u < JupiterCloudRenderer.ATLAS_WIDTH);
                    assertTrue(v >= 0 && v < JupiterCloudRenderer.ATLAS_HEIGHT);
                    assertEquals(x & 255, u % 256);
                    assertEquals(y + 64, v % 384);
                    assertEquals(z & 255, (v / 384) * 16 + u / 256);
                    assertEquals(JupiterCloudRenderer.slot(x >> 4, z >> 4), JupiterCloudRenderer.slot((x >> 4) + 16, (z >> 4) + 16));
                }
            }
        }
        for (double x : new double[]{-30_000_000.125, -256.125, -0.125, 0.125, 256.125, 30_000_000.125}) {
            assertEquals(JupiterCloudRenderer.wrapped(x), JupiterCloudRenderer.wrapped(x + 256));
            assertEquals(x - Math.floor(x), JupiterCloudRenderer.wrapped(x) - Math.floor(JupiterCloudRenderer.wrapped(x)));
        }
        for (double z : new double[]{-30_000_000.125, -2048.125, -0.125, 0.125, 2048.125, 30_000_000.125}) {
            assertEquals(JupiterDimensionEffects.bandOffset(z), JupiterDimensionEffects.bandOffset(z + 2048));
            assertTrue(JupiterDimensionEffects.bandOffset(z) >= 0 && JupiterDimensionEffects.bandOffset(z) < 2048);
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "GC_SHADER_GL_TEST", matches = "1")
    void volumeShadersRespectEmptySpaceExcavationForegroundAndDepth() throws Exception {
        assertTrue(glfwInit());
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 2);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        long window = glfwCreateWindow(96, 64, "Jupiter cloud shader check", 0, 0);
        assertNotEquals(0, window);
        try {
            glfwMakeContextCurrent(window);
            GL.createCapabilities();
            glBindVertexArray(glGenVertexArrays());
            glEnable(GL_DEPTH_TEST);
            glDepthFunc(GL_ALWAYS);
            int copy = program("jupiter_copy"), march = program("jupiter_clouds"), composite = program("jupiter_composite");
            int noiseTexture = glGenTextures();
            glActiveTexture(GL_TEXTURE6); glBindTexture(GL_TEXTURE_2D, noiseTexture);
            ByteBuffer noisePixels = JupiterCloudRenderer.noisePixels();
            try { glTexImage2D(GL_TEXTURE_2D, 0, GL_RG8, 32, 32, 0, GL_RG, GL_UNSIGNED_BYTE, noisePixels); }
            finally { MemoryUtil.memFree(noisePixels); }
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            int volume = glGenTextures();
            glActiveTexture(GL_TEXTURE2);
            glBindTexture(GL_TEXTURE_2D, volume);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            ByteBuffer voxels = MemoryUtil.memCalloc(JupiterCloudRenderer.ATLAS_WIDTH * JupiterCloudRenderer.ATLAS_HEIGHT);
            try {
                upload(volume, voxels);
                Target source = new Target(96, 64), clouds = new Target(24, 16), result = new Target(96, 64);
                float[] color = new float[96 * 64 * 4], depths = new float[96 * 64];
                Arrays.fill(depths, 1);
                for (int p = 0; p < depths.length; p++) {
                    color[p * 4] = 0.10F; color[p * 4 + 1] = 0.16F; color[p * 4 + 2] = 0.24F; color[p * 4 + 3] = 1;
                }
                source.write(color, depths);
                Matrix4f projection = new Matrix4f().perspective((float) Math.toRadians(60), 1.5F, 0.05F, 2048);
                Matrix4f viewProjection = new Matrix4f(projection).rotateX((float) Math.PI / 2);
                march(march, source, clouds, viewProjection, 250);
                assertTrue(maxAlpha(clouds.read()) < 0.00001F, "Empty atmosphere became opaque cloud");
                copy(copy, source, result);
                combine(composite, source, clouds, result);
                assertArrayEquals(color, result.read(), 0.00001F, "Empty volume must preserve scene color");

                for (int z = 0; z < 256; z++) for (int y = 220; y < 228; y++) for (int x = 0; x < 256; x++) {
                    voxels.put(JupiterCloudRenderer.atlasY(y, z) * JupiterCloudRenderer.ATLAS_WIDTH + JupiterCloudRenderer.atlasX(x, z), (byte) 255);
                }
                upload(volume, voxels);
                march(march, source, clouds, viewProjection, 250);
                float[] bank = clouds.read();
                assertTrue(maxAlpha(bank) > 0.98F, "Solid bank did not produce dense volumetric cloud");
                glUniform1f(glGetUniformLocation(march, "BandOffset"), 1024);
                clouds.draw();
                float[] zone = clouds.read();
                int center = (8 * 24 + 12) * 4;
                assertEquals(bank[center + 3], zone[center + 3], 0.00001F, "Palette changed cloud occupancy");
                assertTrue(bank[center] > bank[center + 1] && bank[center + 1] > bank[center + 2], "Belts must have warm brown coloring");
                assertTrue(zone[center + 2] > bank[center + 2] + 0.10F, "Pale zones must remain distinct from brown belts");
                glUniform1f(glGetUniformLocation(march, "BandOffset"), 2048);
                clouds.draw();
                assertArrayEquals(bank, clouds.read(), 0.002F, "Cloud belt pattern jumps at its world-coordinate wrap");
                glUniform1f(glGetUniformLocation(march, "BandOffset"), 0);
                glUniform3f(glGetUniformLocation(march, "CameraOffset"), 384, 250, 128);
                clouds.draw();
                assertArrayEquals(bank, clouds.read(), 0.002F, "Camera wrapping changed cloud density or lighting");
                glUniform3f(glGetUniformLocation(march, "CameraOffset"), 128, 250, 128);
                glUniform1f(glGetUniformLocation(march, "CloudTime"), 48);
                clouds.draw();
                assertArrayEquals(bank, clouds.read(), 0.002F, "Detail wind jumps at its time wrap");
                glUniform1f(glGetUniformLocation(march, "CloudTime"), 0);
                copy(copy, source, result);
                combine(composite, source, clouds, result);
                assertFalse(Arrays.equals(color, result.read()), "Clouds did not composite into the scene");

                for (int z = 120; z <= 136; z++) for (int y = 220; y < 228; y++) for (int x = 120; x <= 136; x++) {
                    voxels.put(JupiterCloudRenderer.atlasY(y, z) * JupiterCloudRenderer.ATLAS_WIDTH + JupiterCloudRenderer.atlasX(x, z), (byte) 0);
                }
                upload(volume, voxels);
                march(march, source, clouds, viewProjection, 250);
                float[] excavated = clouds.read();
                assertTrue(excavated[(8 * 24 + 12) * 4 + 3] < 0.01F, "Mined passage remains a ghost cloud");

                Vector4f foreground = new Vector4f(0, -5, 0, 1).mul(viewProjection);
                float foregroundDepth = foreground.z / foreground.w * 0.5F + 0.5F;
                for (int y = 0; y < 64; y++) for (int x = 64; x < 96; x++) {
                    int p = y * 96 + x;
                    depths[p] = foregroundDepth;
                    color[p * 4] = 0.9F; color[p * 4 + 1] = 0.05F; color[p * 4 + 2] = 0.03F;
                }
                source.write(color, depths);
                march(march, source, clouds, viewProjection, 250);
                copy(copy, source, result);
                combine(composite, source, clouds, result);
                float[] actual = result.read();
                for (int y = 0; y < 64; y++) for (int x = 68; x < 96; x++) {
                    int p = (y * 96 + x) * 4;
                    assertEquals(color[p], actual[p], 0.00001F, "Cloud washed over a foreground building");
                    assertEquals(color[p + 1], actual[p + 1], 0.00001F);
                }
                float[] actualDepth = new float[depths.length];
                glReadPixels(0, 0, 96, 64, GL_DEPTH_COMPONENT, GL_FLOAT, actualDepth);
                assertArrayEquals(depths, actualDepth, 0.000001F, "Cloud composite damaged scene depth");
                for (float value : actual) assertTrue(Float.isFinite(value));

                if ("1".equals(System.getenv("GC_SHADER_BENCHMARK"))) {
                    for (int[] size : new int[][]{{1920, 1080}, {2560, 1440}}) {
                        int w = size[0], h = size[1];
                        Target full = new Target(w, h, GL_RGBA8), scene = new Target(w, h, GL_RGBA8), low = new Target(w / 4, h / 4, GL_RGBA8), output = new Target(w, h, GL_RGBA8);
                        glBindTexture(GL_TEXTURE_2D, low.color);
                        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
                        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
                        float[] sky = new float[w * h]; Arrays.fill(sky, 1);
                        glBindFramebuffer(GL_FRAMEBUFFER, full.framebuffer);
                        glClearColor(0.1F, 0.16F, 0.24F, 1);
                        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
                        full.write(null, sky);
                        for (boolean grazing : new boolean[]{false, true}) {
                            Matrix4f vp = new Matrix4f().perspective((float) Math.toRadians(70), (float) w / h, 0.05F, 2048);
                            if (!grazing) vp.rotateX((float) Math.PI / 2);
                            float cameraY = grazing ? 229 : 250;
                            if (w == 1920 && !grazing) {
                                ShaderBenchmark.measure("jupiter-phase-ray-1080", () -> { }, () -> march(march, full, low, vp, cameraY));
                                ShaderBenchmark.measure("jupiter-phase-composite-1080", () -> { }, () -> combine(composite, scene, low, output));
                            }
                            ShaderBenchmark.measure("jupiter-cached-noise-quarter-" + (grazing ? "horizon-" : "above-") + w + "x" + h,
                                    () -> copy(copy, full, output), () -> {
                                        copy(copy, full, output);
                                        glBindFramebuffer(GL_READ_FRAMEBUFFER, full.framebuffer);
                                        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, scene.framebuffer);
                                        glBlitFramebuffer(0, 0, w, h, 0, 0, w, h, GL_DEPTH_BUFFER_BIT, GL_NEAREST);
                                        march(march, full, low, vp, cameraY);
                                        combine(composite, scene, low, output);
                                    });
                        }
                        full.close(); scene.close(); low.close(); output.close();
                    }
                }
                assertEquals(GL_NO_ERROR, glGetError());
                source.close(); clouds.close(); result.close();
            } finally { MemoryUtil.memFree(voxels); }
            glDeleteTextures(volume); glDeleteTextures(noiseTexture);
            glDeleteProgram(copy); glDeleteProgram(march); glDeleteProgram(composite);
        } finally {
            glfwDestroyWindow(window); glfwTerminate(); GL.setCapabilities(null);
        }
    }

    private static float maxAlpha(float[] rgba) {
        float max = 0; for (int i = 3; i < rgba.length; i += 4) max = Math.max(max, rgba[i]); return max;
    }

    private static int program(String name) throws Exception {
        int program = glCreateProgram();
        glAttachShader(program, MoonSurfaceShaderTest.compile(GL_VERTEX_SHADER, """
                #version 150
                out vec2 texCoord;
                void main() {
                    vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
                    texCoord = p;
                    gl_Position = vec4(p * 2.0 - 1.0, 0, 1);
                }
                """));
        if (name.equals("jupiter_copy")) {
            glAttachShader(program, MoonSurfaceShaderTest.compile(GL_FRAGMENT_SHADER, """
                    #version 150
                    uniform sampler2D DiffuseSampler;
                    uniform sampler2D DepthSampler;
                    in vec2 texCoord;
                    out vec4 fragColor;
                    void main() {
                        fragColor = texture(DiffuseSampler, texCoord);
                        gl_FragDepth = texture(DepthSampler, texCoord).r;
                    }
                    """));
        } else try (var in = JupiterCloudShaderTest.class.getResourceAsStream("/assets/galacticraft/shaders/program/" + name + ".fsh")) {
            assertNotNull(in);
            glAttachShader(program, MoonSurfaceShaderTest.compile(GL_FRAGMENT_SHADER, new String(in.readAllBytes(), StandardCharsets.UTF_8)));
        }
        glLinkProgram(program);
        assertEquals(GL_TRUE, glGetProgrami(program, GL_LINK_STATUS), glGetProgramInfoLog(program));
        return program;
    }

    private static void sampler(int program, String name, int unit, int texture) {
        glActiveTexture(GL_TEXTURE0 + unit); glBindTexture(GL_TEXTURE_2D, texture);
        glUniform1i(glGetUniformLocation(program, name), unit);
    }

    private static void upload(int texture, ByteBuffer data) {
        glActiveTexture(GL_TEXTURE2); glBindTexture(GL_TEXTURE_2D, texture);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_R8, JupiterCloudRenderer.ATLAS_WIDTH, JupiterCloudRenderer.ATLAS_HEIGHT, 0, GL_RED, GL_UNSIGNED_BYTE, data);
    }

    private static void march(int program, Target scene, Target output, Matrix4f viewProjection, float y) {
        glUseProgram(program);
        sampler(program, "DepthSampler", 1, scene.depth);
        glUniform1i(glGetUniformLocation(program, "VolumeSampler"), 2);
        glUniform1i(glGetUniformLocation(program, "NoiseSampler"), 6);
        glUniformMatrix4fv(glGetUniformLocation(program, "InverseViewProjection"), false, new Matrix4f(viewProjection).invert().get(new float[16]));
        glUniform3f(glGetUniformLocation(program, "CameraOffset"), 128, y, 128);
        glUniform3f(glGetUniformLocation(program, "BoundsMin"), -112, 218 - y, -112);
        glUniform3f(glGetUniformLocation(program, "BoundsMax"), 112, 230 - y, 112);
        glUniform3f(glGetUniformLocation(program, "SunDirection"), 0, 0.8F, 0.6F);
        output.draw();
    }

    private static void combine(int program, Target scene, Target clouds, Target output) {
        glUseProgram(program);
        sampler(program, "DepthSampler", 1, scene.depth);
        sampler(program, "CloudSampler", 3, clouds.color);
        sampler(program, "CloudDepthSampler", 4, clouds.depth);
        glEnable(GL_BLEND);
        glBlendFuncSeparate(GL_ONE, GL_ONE_MINUS_SRC_ALPHA, GL_ZERO, GL_ONE);
        glDisable(GL_DEPTH_TEST); glDepthMask(false);
        output.draw();
        glDepthMask(true); glEnable(GL_DEPTH_TEST); glDisable(GL_BLEND);
    }

    private static void copy(int program, Target scene, Target output) {
        glUseProgram(program);
        sampler(program, "DiffuseSampler", 0, scene.color);
        sampler(program, "DepthSampler", 1, scene.depth);
        output.draw();
    }

    private static final class Target {
        final int width, height, color, depth, framebuffer;
        Target(int width, int height) { this(width, height, GL_RGBA32F); }
        Target(int width, int height, int colorFormat) {
            this.width = width; this.height = height;
            glActiveTexture(GL_TEXTURE7);
            color = texture(colorFormat, GL_RGBA);
            depth = texture(GL_DEPTH_COMPONENT32F, GL_DEPTH_COMPONENT);
            framebuffer = glGenFramebuffers(); glBindFramebuffer(GL_FRAMEBUFFER, framebuffer);
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, color, 0);
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, depth, 0);
            assertEquals(GL_FRAMEBUFFER_COMPLETE, glCheckFramebufferStatus(GL_FRAMEBUFFER));
        }
        int texture(int format, int channels) {
            int id = glGenTextures(); glBindTexture(GL_TEXTURE_2D, id);
            glTexImage2D(GL_TEXTURE_2D, 0, format, width, height, 0, channels, GL_FLOAT, (float[]) null);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST); glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE); glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            return id;
        }
        void write(float[] rgba, float[] z) {
            glActiveTexture(GL_TEXTURE7);
            if (rgba != null) { glBindTexture(GL_TEXTURE_2D, color); glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, width, height, GL_RGBA, GL_FLOAT, rgba); }
            glBindTexture(GL_TEXTURE_2D, depth); glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, width, height, GL_DEPTH_COMPONENT, GL_FLOAT, z);
        }
        void draw() { glBindFramebuffer(GL_FRAMEBUFFER, framebuffer); glViewport(0, 0, width, height); glDrawArrays(GL_TRIANGLES, 0, 3); }
        float[] read() { glBindFramebuffer(GL_FRAMEBUFFER, framebuffer); float[] pixels = new float[width * height * 4]; glReadPixels(0, 0, width, height, GL_RGBA, GL_FLOAT, pixels); return pixels; }
        void close() { glDeleteFramebuffers(framebuffer); glDeleteTextures(color); glDeleteTextures(depth); }
    }
}