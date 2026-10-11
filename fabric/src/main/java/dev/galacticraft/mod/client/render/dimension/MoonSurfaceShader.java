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

import com.mojang.blaze3d.systems.RenderSystem;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.Galacticraft;
import dev.galacticraft.mod.mixin.client.PostChainAccessor;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EffectInstance;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Uses the existing planetary post-effect lifecycle on both loaders. */
public final class MoonSurfaceShader {
    public static final ResourceLocation EFFECT = Constant.id("shaders/post/moon.json");

    private MoonSurfaceShader() {
    }

    public static void render(Minecraft minecraft, PostChain chain, Camera camera,
                              Matrix4f view, Matrix4f projection, DeltaTracker delta) {
        if (camera.getFluidInCamera() != FogType.NONE) return;

        // Use the exact terrain matrices, including FOV, hurt tilt and view bob.
        EffectInstance surface = ((PostChainAccessor) chain).galacticraft$getPasses().getFirst().getEffect();
        surface.safeGetUniform("InverseViewProjection").set(new Matrix4f(projection).mul(view).invert());
        Vec3 position = camera.getPosition();
        surface.safeGetUniform("CameraOffset").set(wrappedCoordinate(position.x),
                wrappedCoordinate(position.y), wrappedCoordinate(position.z));
        float angle = minecraft.level.getTimeOfDay(delta.getGameTimeDeltaPartialTick(true)) * Mth.TWO_PI;
        surface.safeGetUniform("SunDirection").set(-Mth.sin(angle), Mth.cos(angle), 0.0F);
        surface.safeGetUniform("Strength").set(Galacticraft.CONFIG.moonSurfaceShaderStrength());

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        chain.process(delta.getGameTimeDeltaTicks());
        minecraft.getMainRenderTarget().bindWrite(true);
        RenderSystem.defaultBlendFunc();
    }

    static float wrappedCoordinate(double coordinate) {
        // All noise octaves tile at 4096 blocks; wrap in double precision first.
        return (float) (coordinate - Math.floor(coordinate / 4096.0) * 4096.0);
    }
}
