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

package dev.galacticraft.mod.client.render.dimension;

import dev.galacticraft.mod.api.dimension.GalacticDimensionEffects;
import dev.galacticraft.mod.client.render.dimension.duststorm.ClientDustStorms;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.CubicSampler;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

@Environment(EnvType.CLIENT)
public class MarsDimensionEffects extends GalacticDimensionEffects {
    // Mars has a thin, dusty reddish atmosphere (unlike the airless Moon, which is pure black).
    private static final Vec3 MARS_FOG = new Vec3(0.52D, 0.29D, 0.20D);
    private static final Vec3 MARS_SKY = new Vec3(0.776D, 0.478D, 0.329D);
    // During a storm the air fills with choking rust dust: darker, denser, browner.
    private static final Vec3 STORM_FOG = new Vec3(0.30D, 0.16D, 0.10D);
    private static final Vec3 STORM_SKY = new Vec3(0.22D, 0.12D, 0.08D);

    public static final MarsDimensionEffects INSTANCE = new MarsDimensionEffects();

    private final Minecraft minecraft = Minecraft.getInstance();

    private MarsDimensionEffects() {
        super(Float.NaN, false, SkyType.NORMAL, true, true);
    }

    @Override
    public float getStarBrightness(Level level, float partialTicks) {
        float base = super.getStarBrightness(level, partialTicks);
        return base * (1.0F - Math.min(1.0F, ClientDustStorms.intensity(partialTicks)));
    }

    @Override
    public Vec3 getFogColor(ClientLevel level, float partialTicks, Vec3 cameraPos, CubicSampler.Vec3Fetcher fetcher) {
        float day = 1.0F - super.getStarBrightness(level, partialTicks);
        float brightness = Math.max(0.2F, day);
        Vec3 base = MARS_FOG.scale(brightness);
        float intensity = ClientDustStorms.intensity(partialTicks);
        if (intensity <= 0.0F) return base;
        Vec3 storm = STORM_FOG.scale(Math.max(0.15F, brightness));
        return base.lerp(storm, Math.min(1.0F, intensity));
    }

    @Override
    public Vec3 getSkyColor(ClientLevel level, float partialTicks) {
        float day = 1.0F - super.getStarBrightness(level, partialTicks);
        Vec3 base = MARS_SKY.scale(Math.max(0.15F, day));
        float intensity = ClientDustStorms.intensity(partialTicks);
        if (intensity <= 0.0F) return base;
        Vec3 storm = STORM_SKY.scale(Math.max(0.1F, day));
        return base.lerp(storm, Math.min(1.0F, intensity));
    }

    @Override
    public boolean isFoggyAt(int camX, int camY) {
        // Thicken the render-distance fog once the storm is well underway so visibility drops.
        return ClientDustStorms.intensity() > 0.55F;
    }

    @Override
    public float[] getSunriseColor(float skyAngle, float tickDelta) {
        return null;
    }

    @Override
    public boolean tickRain(@NotNull ClientLevel level, Camera camera, int ticks) {
        float intensity = ClientDustStorms.intensity();
        if (intensity <= 0.05F) return true;

        RandomSource random = RandomSource.create((long) ticks * 0x9E3779B97F4A7C15L);
        Vec3 cam = camera.getPosition();
        int count = (int) (70.0F * intensity * intensity);
        ParticleStatus particles = this.minecraft.options.particles().get();
        if (particles == ParticleStatus.MINIMAL) return true;
        if (particles == ParticleStatus.DECREASED) count /= 2;

        double wind = 0.35D + intensity * 0.55D;
        for (int i = 0; i < count; i++) {
            double px = cam.x + (random.nextDouble() - 0.5D) * 30.0D;
            double py = cam.y + (random.nextDouble() - 0.2D) * 12.0D;
            double pz = cam.z + (random.nextDouble() - 0.5D) * 30.0D;
            // Dust blows roughly downwind (+x/+z) rather than falling straight down.
            level.addParticle(ParticleTypes.WHITE_ASH, px, py, pz, wind, -0.02D, wind * 0.4D);
        }
        return true;
    }
}
