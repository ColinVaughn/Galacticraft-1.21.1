/*
 * Copyright (c) 2019-2026 Team Galacticraft
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

package dev.galacticraft.mod.mixin.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.galacticraft.mod.client.accessor.SpaceflightTerrainAccess;
import dev.galacticraft.mod.client.render.dimension.SpaceflightTransition;
import dev.galacticraft.mod.client.render.dimension.SpaceflightViewArea;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.SectionOcclusionGraph;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.chunk.RenderRegionCache;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.levelgen.Heightmap;
import dev.galacticraft.mod.world.dimension.GCDimensions;
import dev.galacticraft.mod.world.dimension.SpaceTravel;
import org.spongepowered.asm.mixin.Shadow;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.client.render.dimension.OverworldRenderer;
import dev.galacticraft.mod.client.render.ShaderPackCompat;
import dev.galacticraft.mod.content.entity.vehicle.RocketEntity;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class LevelRendererMixin implements SpaceflightTerrainAccess {
    @Shadow private ClientLevel level;
    @Shadow private ViewArea viewArea;
    @Shadow private SectionRenderDispatcher sectionRenderDispatcher;
    @Shadow private SectionOcclusionGraph sectionOcclusionGraph;
    @Shadow private PostChain transparencyChain;
    @Unique private boolean galacticraft$pendingFlightGraph;
    @Unique private ViewArea galacticraft$previousFlightArea;

    @WrapOperation(method = "allChanged", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ViewArea;releaseAllBuffers()V"))
    private void galacticraft$retireFlightTerrain(ViewArea area, Operation<Void> original) {
        if (SpaceflightTransition.isReplacingLevel()) galacticraft$previousFlightArea = area;
        else original.call(area);
    }

    @WrapOperation(method = "allChanged", at = @At(value = "NEW", target = "net/minecraft/client/renderer/ViewArea"))
    private ViewArea galacticraft$stageFlightTerrain(SectionRenderDispatcher dispatcher, Level level, int distance,
                                                    LevelRenderer renderer, Operation<ViewArea> original) {
        if (!SpaceflightTransition.isReplacingLevel()) return original.call(dispatcher, level, distance, renderer);
        var area = new SpaceflightViewArea(dispatcher, level, distance, renderer, galacticraft$previousFlightArea);
        galacticraft$previousFlightArea = null;
        return area;
    }

    @WrapOperation(method = "allChanged", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SectionOcclusionGraph;waitAndReset(Lnet/minecraft/client/renderer/ViewArea;)V"))
    private void galacticraft$deferFlightGraph(SectionOcclusionGraph graph, ViewArea area, Operation<Void> original) {
        galacticraft$pendingFlightGraph = SpaceflightTransition.isReplacingLevel();
        if (!galacticraft$pendingFlightGraph) original.call(graph, area);
    }

    @WrapOperation(method = "allChanged", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;graphicsChanged()V"))
    private void galacticraft$keepFlightTransparency(LevelRenderer renderer, Operation<Void> original) {
        // A world swap does not change the Fabulous shader chain or its framebuffer size.
        if (!SpaceflightTransition.isReplacingLevel() || ShaderPackCompat.isActive()
                || Minecraft.useShaderTransparency() != (transparencyChain != null))
            original.call(renderer);
    }

    @Inject(method = "renderLevel", at = @At("HEAD"), cancellable = true)
    private void galacticraft$budgetFlightTerrain(CallbackInfo ci) {
        // Finish the old graph before resetting any sections it may still be reading.
        if (galacticraft$pendingFlightGraph) {
            var task = ((SectionOcclusionGraphAccessor) sectionOcclusionGraph).galacticraft$fullUpdateTask();
            if (task != null && !task.isDone()) { ci.cancel(); return; }
        }
        if (viewArea instanceof SpaceflightViewArea area && !area.isReady() && !area.prepare()) {
            ci.cancel();
            return;
        }
        if (galacticraft$pendingFlightGraph) {
            sectionOcclusionGraph.waitAndReset(viewArea);
            galacticraft$pendingFlightGraph = false;
        }
        if (SpaceflightTransition.coversWorld() && !SpaceflightTransition.isPreparingTerrain()) {
            // The source is covered; the destination still needs vanilla's lighting and visibility preparation.
            sectionRenderDispatcher.uploadAllPendingUploads();
            ci.cancel();
        }
    }

    @WrapOperation(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;renderSky(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;FLnet/minecraft/client/Camera;ZLjava/lang/Runnable;)V"))
    private void galacticraft$skipCoveredSky(LevelRenderer renderer, Matrix4f view, Matrix4f projection,
            float delta, Camera camera, boolean fog, Runnable fogCallback, Operation<Void> original) {
        if (!SpaceflightTransition.coversWorld()) {
            original.call(renderer, view, projection, delta, camera, fog, fogCallback);
            if (!fog) dev.galacticraft.mod.client.render.dimension.PlanetSurfaceRenderer.render(level, view, camera, delta);
        }
    }

    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;renderSectionLayer(Lnet/minecraft/client/renderer/RenderType;DDDLorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V", ordinal = 0), cancellable = true)
    private void galacticraft$skipCoveredDrawing(CallbackInfo ci) {
        // Lighting, visibility and mesh uploads above must finish even when the world is hidden.
        if (SpaceflightTransition.coversWorld()) ci.cancel();
    }

    @Inject(method = "compileSections", at = @At("HEAD"), cancellable = true)
    private void galacticraft$prioritizeLandingCorridor(CallbackInfo ci) {
        if (!SpaceflightTransition.isPreparingTerrain()) return;
        // Let the landing meshes finish before filling vanilla's initial-build FIFO with distant terrain.
        sectionRenderDispatcher.uploadAllPendingUploads();
        ci.cancel();
    }

    @WrapOperation(method = "compileSections", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher;rebuildSectionSync(Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher$RenderSection;Lnet/minecraft/client/renderer/chunk/RenderRegionCache;)V"))
    private void galacticraft$compileFlightTerrainAsync(SectionRenderDispatcher dispatcher,
            SectionRenderDispatcher.RenderSection section, RenderRegionCache cache, Operation<Void> original) {
        if (SpaceflightTransition.isActive()) section.rebuildSectionAsync(dispatcher, cache);
        else original.call(dispatcher, section, cache);
    }

    @Override
    public boolean galacticraft$prepareFlightTerrain(Vec3 arrival) {
        if (level == null || viewArea == null || galacticraft$pendingFlightGraph
                || viewArea instanceof SpaceflightViewArea area && !area.isReady()) return false;
        var center = new ChunkPos(BlockPos.containing(arrival));
        var cache = new RenderRegionCache();
        boolean ready = true;
        int radius = level.dimension().equals(GCDimensions.SOLAR_SYSTEM) ? 1
                : Math.min(SpaceTravel.LANDING_TERRAIN_RADIUS, Math.max(1, viewArea.getViewDistance() - 1));
        long deadline = System.nanoTime() + 2_000_000L;
        for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) {
            int cx = center.x + x, cz = center.z + z;
            if (!level.getChunkSource().hasChunk(cx, cz)) { ready = false; continue; }
            if (level.dimension().equals(GCDimensions.SOLAR_SYSTEM)) continue;
            int bx = cx * 16 + 8, bz = cz * 16 + 8;
            int height = level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz);
            if (height <= level.getMinBuildHeight()) continue;
            var position = new BlockPos(bx, height - 1, bz);
            var section = ((ViewAreaInvoker) viewArea).galacticraft$flightSection(position);
            if (section == null || !section.hasAllNeighbors()
                    || !level.getLightEngine().lightOnInSection(SectionPos.of(position))) { ready = false; continue; }
            if (section.isDirty()) {
                if (System.nanoTime() >= deadline) { ready = false; continue; }
                section.rebuildSectionAsync(sectionRenderDispatcher, cache);
                section.setNotDirty();
                ready = false;
            }
            if (section.compiled.get() == SectionRenderDispatcher.CompiledSection.UNCOMPILED) ready = false;
        }
        return ready;
    }

    @Unique
    private OverworldRenderer worldRenderer;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void gc$setupRenderer(Minecraft minecraft, EntityRenderDispatcher entityRenderDispatcher, BlockEntityRenderDispatcher blockEntityRenderDispatcher, RenderBuffers renderBuffers, CallbackInfo ci) {
        this.worldRenderer = new OverworldRenderer();
    }

    @Inject(method = "renderSky", at = @At("HEAD"), cancellable = true)
    public void gc$renderSky(Matrix4f matrix4f, Matrix4f projectionMatrix, float tickDelta, Camera camera, boolean thickFog, Runnable fogCallback, CallbackInfo ci) {
        Player player = Minecraft.getInstance().player;
        if (player != null && !ShaderPackCompat.isActive() && player.getVehicle() instanceof RocketEntity && player.getVehicle().getY() > Constant.OVERWORLD_SKYPROVIDER_STARTHEIGHT && player.level().dimension() == Level.OVERWORLD) {
            this.worldRenderer.renderOverworldSky(player, matrix4f, projectionMatrix, tickDelta, camera, thickFog, fogCallback);
            ci.cancel();
        }
    }

    @Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true)
    public void gc$preventCloudRendering(PoseStack matrices, Matrix4f matrix4f, Matrix4f matrix4f2, float tickDelta, double cameraX, double cameraY, double cameraZ, CallbackInfo ci) {
        Player player = Minecraft.getInstance().player;
        if (player != null && player.getVehicle() instanceof RocketEntity && player.getVehicle().getY() > Constant.CLOUD_HEIGHT) {
            // Have clouds slowly fade out
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, normalize((float) player.getY(), Constant.CLOUD_HEIGHT, Constant.CLOUD_LIMIT, 1, 0F));
            if (player.getVehicle().getY() > Constant.CLOUD_LIMIT)
                ci.cancel();
        }
    }

    @Inject(method = "renderSnowAndRain", at = @At("HEAD"), cancellable = true)
    public void gc$cancelRainAndSnow(LightTexture lightTexture, float f, double d, double e, double g, CallbackInfo ci) {
        Player player = Minecraft.getInstance().player;
        if (player != null && player.getVehicle() instanceof RocketEntity && player.getVehicle().getY() > Constant.CLOUD_HEIGHT)
            ci.cancel();
    }


    @Unique
    private float normalize(float x, float inMin, float inMax, float outMin, float outMax) {
        float outRange = outMax - outMin;
        float inRange = inMax - inMin;
        return (x - inMin) * outRange / inRange + outMin;
    }
}
