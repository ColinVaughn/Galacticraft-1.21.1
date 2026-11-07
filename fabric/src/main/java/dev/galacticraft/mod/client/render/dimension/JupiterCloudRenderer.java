package dev.galacticraft.mod.client.render.dimension;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.Galacticraft;
import dev.galacticraft.mod.content.GCBlocks;
import dev.galacticraft.mod.mixin.client.PostChainAccessor;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Mth;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Arrays;

import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL30C.GL_R8;
import static org.lwjgl.opengl.GL30C.GL_RG;
import static org.lwjgl.opengl.GL30C.GL_RG8;
import static org.lwjgl.opengl.GL12C.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL13C.GL_TEXTURE0;

/** Streams actual cloud voxels into a bounded R8 atlas; no per-frame world-volume rebuild. */
public final class JupiterCloudRenderer {
    public static final ResourceLocation EFFECT = Constant.id("shaders/post/jupiter.json");
    static final int WIDTH = 256, MIN_Y = -64, HEIGHT = 384;
    static final int ATLAS_WIDTH = WIDTH * 16, ATLAS_HEIGHT = HEIGHT * 16;
    private static final int CHUNKS = WIDTH / 16;
    private static final LevelChunk[] observed = new LevelChunk[CHUNKS * CHUNKS];
    private static final int[] worldX = new int[observed.length], worldZ = new int[observed.length];
    private static final boolean[] queued = new boolean[observed.length];
    private static final ArrayDeque<Integer> dirty = new ArrayDeque<>();
    private static ClientLevel level;
    private static DensityTexture texture;
    private static NoiseTexture noiseTexture;
    private static ByteBuffer upload;
    private static int originX, originZ, minCloudY, maxCloudY;
    private static final int[] chunkMin = new int[observed.length], chunkMax = new int[observed.length];
    private static volatile boolean active;
    private static boolean ready, failed;

    private JupiterCloudRenderer() { }

    public static boolean isActive() { return active; }
    public static boolean isCloud(net.minecraft.world.level.block.state.BlockState state) {
        return state.is(GCBlocks.DENSE_CLOUD) || state.is(GCBlocks.AMMONIA_FROST);
    }
    public static boolean hasFailed() { return failed; }
    public static void retry() { failed = false; }

    public static void tick(Minecraft minecraft, boolean selected) {
        setActive(minecraft, selected && ready && !failed);
        if (!selected && texture != null) release();
    }

    private static void setActive(Minecraft minecraft, boolean value) {
        if (active == value) return;
        active = value;
        // The fallback and volume must switch together, including already compiled chunk meshes.
        minecraft.levelRenderer.allChanged();
    }

    public static void invalidate(BlockPos pos) {
        if (level == null || pos.getY() < MIN_Y || pos.getY() >= MIN_Y + HEIGHT) return;
        int x = pos.getX() >> 4, z = pos.getZ() >> 4, slot = slot(x, z);
        if (worldX[slot] == x && worldZ[slot] == z) enqueue(slot);
    }

    static int slot(int chunkX, int chunkZ) { return (chunkZ & 15) * 16 + (chunkX & 15); }
    static int atlasX(int x, int z) { return (z & 15) * WIDTH + (x & 255); }
    static int atlasY(int y, int z) { return ((z & 255) >> 4) * HEIGHT + y - MIN_Y; }
    static float wrapped(double coordinate) { return (float) (coordinate - Math.floor(coordinate / WIDTH) * WIDTH); }

    private static void enqueue(int slot) {
        if (!queued[slot]) { queued[slot] = true; dirty.addLast(slot); }
    }

    private static void prepare(Minecraft minecraft, Camera camera) {
        if (level != minecraft.level) { release(); level = minecraft.level; }
        if (texture == null) {
            texture = new DensityTexture();
            noiseTexture = new NoiseTexture();
            upload = MemoryUtil.memAlloc(16 * HEIGHT);
            Arrays.fill(worldX, Integer.MIN_VALUE);
            Arrays.fill(chunkMin, MIN_Y + HEIGHT);
            Arrays.fill(chunkMax, MIN_Y);
        }
        int nextX = (Mth.floor(camera.getPosition().x) >> 4) - CHUNKS / 2;
        int nextZ = (Mth.floor(camera.getPosition().z) >> 4) - CHUNKS / 2;
        if (ready && (Math.abs(nextX - originX) >= CHUNKS / 2 || Math.abs(nextZ - originZ) >= CHUNKS / 2)) ready = false;
        originX = nextX; originZ = nextZ;
        for (int z = originZ; z < originZ + CHUNKS; z++) {
            for (int x = originX; x < originX + CHUNKS; x++) {
                int slot = slot(x, z);
                LevelChunk chunk = level.getChunkSource().getChunk(x, z, ChunkStatus.FULL, false);
                if (worldX[slot] != x || worldZ[slot] != z || observed[slot] != chunk) {
                    worldX[slot] = x; worldZ[slot] = z; observed[slot] = chunk;
                    enqueue(slot);
                }
            }
        }
        long deadline = System.nanoTime() + 1_500_000;
        RenderSystem.activeTexture(GL_TEXTURE0);
        texture.bind();
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
        glPixelStorei(GL_UNPACK_ROW_LENGTH, 0);
        glPixelStorei(GL_UNPACK_SKIP_PIXELS, 0);
        glPixelStorei(GL_UNPACK_SKIP_ROWS, 0);
        for (int count = 0; count < 4 && !dirty.isEmpty(); count++) {
            int slot = dirty.removeFirst();
            queued[slot] = false;
            updateChunk(slot);
            if (System.nanoTime() >= deadline) break;
        }
        glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
        minCloudY = MIN_Y + HEIGHT; maxCloudY = MIN_Y;
        for (int i = 0; i < observed.length; i++) {
            minCloudY = Math.min(minCloudY, chunkMin[i]);
            maxCloudY = Math.max(maxCloudY, chunkMax[i]);
        }
        if (dirty.isEmpty()) ready = true;
    }

    private static void updateChunk(int slot) {
        LevelChunk chunk = observed[slot];
        chunkMin[slot] = MIN_Y + HEIGHT; chunkMax[slot] = MIN_Y;
        boolean[] sections = new boolean[HEIGHT / 16];
        if (chunk != null) {
            for (int s = 0; s < sections.length; s++) {
                int index = chunk.getSectionIndex(MIN_Y + s * 16);
                sections[s] = !chunk.getSection(index).hasOnlyAir()
                        && chunk.getSection(index).getStates().maybeHas(JupiterCloudRenderer::isCloud);
            }
        }
        for (int z = 0; z < 16; z++) {
            MemoryUtil.memSet(upload, 0);
            for (int s = 0; s < sections.length; s++) {
                if (!sections[s]) continue;
                var section = chunk.getSection(chunk.getSectionIndex(MIN_Y + s * 16));
                for (int y = 0; y < 16; y++) {
                    for (int x = 0; x < 16; x++) {
                        var state = section.getBlockState(x, y, z);
                        if (isCloud(state)) {
                            int localY = s * 16 + y;
                            upload.put(localY * 16 + x, (byte) (state.is(GCBlocks.AMMONIA_FROST) ? 255 : 191));
                            chunkMin[slot] = Math.min(chunkMin[slot], MIN_Y + localY);
                            chunkMax[slot] = Math.max(chunkMax[slot], MIN_Y + localY + 1);
                        }
                    }
                }
            }
            upload.position(0).limit(upload.capacity());
            int globalZ = worldZ[slot] * 16 + z;
            glTexSubImage2D(GL_TEXTURE_2D, 0, atlasX(worldX[slot] * 16, globalZ), atlasY(MIN_Y, globalZ),
                    16, HEIGHT, GL_RED, GL_UNSIGNED_BYTE, upload);
        }
    }

    public static void render(Minecraft minecraft, PostChain chain, Camera camera,
                              Matrix4f view, Matrix4f projection, DeltaTracker delta) {
        try {
            prepare(minecraft, camera);
            setActive(minecraft, ready);
            if (!ready) return;
            var passes = ((PostChainAccessor) chain).galacticraft$getPasses();
            var clouds = chain.getTempTarget("jupiter_clouds");
            int divisor = Galacticraft.CONFIG.jupiterCloudResolutionDivisor();
            int width = Math.max(1, (minecraft.getMainRenderTarget().width + divisor - 1) / divisor);
            int height = Math.max(1, (minecraft.getMainRenderTarget().height + divisor - 1) / divisor);
            if (clouds.width != width || clouds.height != height) clouds.resize(width, height, Minecraft.ON_OSX);
            clouds.setFilterMode(GL_LINEAR);
            passes.getFirst().setOrthoMatrix(new Matrix4f().setOrtho(0, width, 0, height, 0.1F, 1000));
            var shader = passes.getFirst().getEffect();
            shader.setSampler("VolumeSampler", texture::getId);
            shader.setSampler("NoiseSampler", noiseTexture::getId);
            shader.safeGetUniform("InverseViewProjection").set(new Matrix4f(projection).mul(view).invert());
            var position = camera.getPosition();
            shader.safeGetUniform("CameraOffset").set(wrapped(position.x), (float) position.y, wrapped(position.z));
            shader.safeGetUniform("BandOffset").set(JupiterDimensionEffects.bandOffset(position.z));
            shader.safeGetUniform("BoundsMin").set((float) (originX * 16.0 - position.x), minCloudY - 2.0F - (float) position.y,
                    (float) (originZ * 16.0 - position.z));
            shader.safeGetUniform("BoundsMax").set((float) ((originX + CHUNKS) * 16.0 - position.x), maxCloudY + 2.0F - (float) position.y,
                    (float) ((originZ + CHUNKS) * 16.0 - position.z));
            float partial = delta.getGameTimeDeltaPartialTick(true);
            float angle = minecraft.level.getTimeOfDay(partial) * Mth.TWO_PI;
            shader.safeGetUniform("SunDirection").set(-Mth.sin(angle), Mth.cos(angle), 0);
            shader.safeGetUniform("CloudTime").set((minecraft.level.getGameTime() % 24000 + partial) * 0.002F);
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
            var depthCopy = chain.getTempTarget("jupiter_depth");
            depthCopy.copyDepthFrom(minecraft.getMainRenderTarget());
            passes.getFirst().process(delta.getGameTimeDeltaTicks());
            var composite = passes.get(1).getEffect();
            composite.setSampler("DepthSampler", depthCopy::getDepthTextureId);
            composite.setSampler("CloudSampler", clouds::getColorTextureId);
            composite.setSampler("CloudDepthSampler", clouds::getDepthTextureId);
            int screenWidth = minecraft.getMainRenderTarget().width, screenHeight = minecraft.getMainRenderTarget().height;
            composite.safeGetUniform("ProjMat").set(new Matrix4f().setOrtho(0, screenWidth, 0, screenHeight, 0.1F, 1000));
            composite.safeGetUniform("OutSize").set((float) screenWidth, (float) screenHeight);
            composite.apply();
            minecraft.getMainRenderTarget().bindWrite(true);
            RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(GL_ONE, GL_ONE_MINUS_SRC_ALPHA, GL_ZERO, GL_ONE);
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            var quad = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
            quad.addVertex(0, 0, 500); quad.addVertex(screenWidth, 0, 500);
            quad.addVertex(screenWidth, screenHeight, 500); quad.addVertex(0, screenHeight, 500);
            BufferUploader.draw(quad.buildOrThrow());
            composite.clear();
        } catch (RuntimeException exception) {
            failed = true;
            setActive(minecraft, false);
            release();
            Constant.LOGGER.error("Jupiter volumetric clouds failed; restoring block rendering.", exception);
        } finally {
            minecraft.getMainRenderTarget().bindWrite(true);
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
            RenderSystem.defaultBlendFunc();
        }
    }

    private static void release() {
        if (texture != null) { texture.close(); texture.releaseId(); texture = null; }
        if (noiseTexture != null) { noiseTexture.close(); noiseTexture.releaseId(); noiseTexture = null; }
        if (upload != null) { MemoryUtil.memFree(upload); upload = null; }
        level = null; ready = false;
        dirty.clear(); Arrays.fill(queued, false); Arrays.fill(observed, null);
    }

    // Adjacent Z lattice planes share one filtered RG lookup; the tiny tile stays cache-resident.
    static ByteBuffer noisePixels() {
        ByteBuffer pixels = MemoryUtil.memAlloc(32 * 32 * 2);
        for (int y = 0; y < 32; y++) for (int x = 0; x < 32; x++) {
            pixels.put(noiseValue(x, y)).put(noiseValue(x + 37, y + 17));
        }
        return pixels.flip();
    }

    private static byte noiseValue(int x, int y) {
        int value = (x & 31) * 374761393 + (y & 31) * 668265263;
        value = (value ^ value >>> 13) * 1274126177;
        return (byte) (value >>> 24);
    }

    private static final class NoiseTexture extends AbstractTexture {
        NoiseTexture() {
            bind();
            ByteBuffer pixels = noisePixels();
            try { glTexImage2D(GL_TEXTURE_2D, 0, GL_RG8, 32, 32, 0, GL_RG, GL_UNSIGNED_BYTE, pixels); }
            finally { MemoryUtil.memFree(pixels); }
            setFilter(true, false);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
        }
        @Override public void load(ResourceManager resources) { }
    }

    private static final class DensityTexture extends AbstractTexture {
        DensityTexture() {
            if (glGetInteger(GL_MAX_TEXTURE_SIZE) < ATLAS_HEIGHT) throw new IllegalStateException("Cloud atlas exceeds GPU texture limit");
            bind();
            glTexImage2D(GL_TEXTURE_2D, 0, GL_R8, ATLAS_WIDTH, ATLAS_HEIGHT, 0, GL_RED, GL_UNSIGNED_BYTE, (ByteBuffer) null);
            setFilter(true, false);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        }
        @Override public void load(ResourceManager resources) { }
    }
}