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

package dev.galacticraft.neoforge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.galacticraft.mod.api.dimension.GalacticDimensionEffects;
import dev.galacticraft.mod.client.render.dimension.GCWorldRenderContext;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.util.CubicSampler;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.function.Consumer;

/** Bridges shared sky/weather renderers to NeoForge's dimension-effects hooks. */
public final class GCNeoDimensionEffects extends GalacticDimensionEffects {
    private final DimensionSpecialEffects delegate;
    private final Consumer<GCWorldRenderContext> sky;
    private final Consumer<GCWorldRenderContext> weather;

    public GCNeoDimensionEffects(DimensionSpecialEffects delegate, Consumer<GCWorldRenderContext> sky,
                                 Consumer<GCWorldRenderContext> weather) {
        super(delegate.getCloudHeight(), delegate.hasGround(), delegate.skyType(), delegate.forceBrightLightmap(), delegate.constantAmbientLight());
        this.delegate = delegate;
        this.sky = sky;
        this.weather = weather;
    }

    private static GCNeoWorldRenderContext context(ClientLevel level, Camera camera, Matrix4f modelView, Matrix4f projection, LightTexture light) {
        Minecraft minecraft = Minecraft.getInstance();
        return new GCNeoWorldRenderContext(level, camera, new PoseStack(), modelView, projection,
                minecraft.getTimer(), minecraft.levelRenderer, light);
    }

    @Override public Vec3 getBrightnessDependentFogColor(Vec3 color, float brightness) { return delegate.getBrightnessDependentFogColor(color, brightness); }
    @Override public boolean isFoggyAt(int x, int y) { return delegate.isFoggyAt(x, y); }
    @Override public float[] getSunriseColor(float angle, float partialTick) { return delegate.getSunriseColor(angle, partialTick); }
    @Override public Vec3 getFogColor(ClientLevel level, float partialTick, Vec3 cameraPos, CubicSampler.Vec3Fetcher fetcher) {
        return delegate instanceof GalacticDimensionEffects effects
                ? effects.getFogColor(level, partialTick, cameraPos, fetcher)
                : delegate.getBrightnessDependentFogColor(Vec3.ZERO, 1.0F);
    }
    @Override public Vec3 getSkyColor(ClientLevel level, float partialTick) {
        // Calling ClientLevel#getSkyColor here re-enters the shared ClientLevel mixin, which
        // delegates straight back to this wrapper. Airless delegates (Moon, Mercury, Asteroids,
        // and satellites) intentionally use a black sky, so return it without re-entering the
        // vanilla method.
        return delegate instanceof GalacticDimensionEffects effects ? effects.getSkyColor(level, partialTick) : Vec3.ZERO;
    }
    @Override public boolean tickRain(ClientLevel level, Camera camera, int ticks) {
        return delegate instanceof GalacticDimensionEffects effects && effects.tickRain(level, camera, ticks);
    }

    @Override
    public boolean renderSky(ClientLevel level, int ticks, float partialTick, Matrix4f modelView, Camera camera,
                             Matrix4f projection, boolean foggy, Runnable setupFog) {
        sky.accept(context(level, camera, modelView, projection, Minecraft.getInstance().gameRenderer.lightTexture()));
        return true;
    }

    @Override public boolean renderClouds(ClientLevel level, int ticks, float partialTick, PoseStack poses,
                                           double x, double y, double z, Matrix4f modelView, Matrix4f projection) { return true; }

    @Override
    public boolean renderSnowAndRain(ClientLevel level, int ticks, float partialTick, LightTexture light,
                                     double x, double y, double z) {
        if (weather != null) weather.accept(context(level, Minecraft.getInstance().gameRenderer.getMainCamera(), new Matrix4f(), new Matrix4f(), light));
        return true;
    }

    @Override
    public boolean tickRain(ClientLevel level, int ticks, Camera camera) {
        return tickRain(level, camera, ticks);
    }
}
