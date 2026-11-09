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

import dev.galacticraft.api.universe.celestialbody.CelestialBody;
import dev.galacticraft.api.universe.celestialbody.SkyConfig;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.api.dimension.GalacticDimensionEffects;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.CubicSampler;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The {@code galacticraft:space} dimension effects: a sky, fog and starfield taken from the current
 * celestial body's {@link SkyConfig}. A dimension type names it with
 * {@code "effects": "galacticraft:space"}; the sky itself is drawn by {@link ConfigurableSkyRenderer}.
 */
@Environment(EnvType.CLIENT)
public class SpaceDimensionEffects extends GalacticDimensionEffects {
    public static final ResourceLocation ID = Constant.id("space");
    public static final SpaceDimensionEffects INSTANCE = new SpaceDimensionEffects();

    private SpaceDimensionEffects() {
        super(Float.NaN, false, SkyType.NORMAL, true, true);
    }

    public static SkyConfig sky(@Nullable Level level) {
        Holder<CelestialBody<?, ?>> body = level == null ? null : level.galacticraft$getCelestialBody();
        return body == null ? SkyConfig.DEFAULT : body.value().sky().orElse(SkyConfig.DEFAULT);
    }

    @Override
    public float getStarBrightness(Level level, float partialTicks) {
        SkyConfig sky = sky(level);
        // Without air to scatter sunlight the stars stay out all day.
        return sky.starBrightness() * (sky.airless() ? 1.0F : super.getStarBrightness(level, partialTicks));
    }

    @Override
    public Vec3 getSkyColor(ClientLevel level, float partialTicks) {
        return daylit(sky(level).skyColor(), level, partialTicks, 0.15F);
    }

    @Override
    public Vec3 getFogColor(ClientLevel level, float partialTicks, Vec3 cameraPos, CubicSampler.Vec3Fetcher fetcher) {
        return daylit(sky(level).fogColor(), level, partialTicks, 0.2F);
    }

    private Vec3 daylit(int rgb, ClientLevel level, float partialTicks, float floor) {
        if ((rgb & 0xFFFFFF) == 0) return Vec3.ZERO;
        float day = 1.0F - super.getStarBrightness(level, partialTicks);
        return Vec3.fromRGB24(rgb).scale(Math.max(floor, day));
    }

    @Override
    public boolean isFoggyAt(int camX, int camY) {
        return false;
    }

    @Override
    public float[] getSunriseColor(float skyAngle, float tickDelta) {
        return null;
    }

    @Override
    public boolean tickRain(ClientLevel level, Camera camera, int ticks) {
        return true;
    }
}
