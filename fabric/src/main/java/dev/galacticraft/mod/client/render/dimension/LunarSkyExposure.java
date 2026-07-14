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

import dev.galacticraft.mod.world.dimension.GCDimensions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import org.joml.Vector3f;

/**
 * Lightweight eye/camera adaptation shared by the lunar sky core shaders and
 * the Moon post chain. Zero is daylight-adapted and one is dark-adapted.
 */
public final class LunarSkyExposure {
    private static final float DARK_ADAPTATION_RATE = 0.018F;
    private static final float LIGHT_ADAPTATION_RATE = 0.14F;

    private static float adaptation = 0.08F;
    private static boolean wasOnMoon;

    private LunarSkyExposure() {
    }

    public static void tick(Minecraft minecraft) {
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.player == null || !level.dimension().equals(GCDimensions.MOON)) {
            wasOnMoon = false;
            adaptation = 0.08F;
            return;
        }

        if (!wasOnMoon) {
            adaptation = 0.08F;
            wasOnMoon = true;
        }

        BlockPos eye = BlockPos.containing(minecraft.player.getEyePosition());
        float localLight = level.getRawBrightness(eye, level.getSkyDarken()) / 15.0F;

        // Looking into the solar disc forces a rapid exposure-down response even
        // when the player is standing inside an otherwise dark crater or shelter.
        float skyAngle = level.getTimeOfDay(1.0F) * Mth.TWO_PI;
        Vector3f sunDirection = new Vector3f(-Mth.sin(skyAngle), Mth.cos(skyAngle), 0.0F);
        Vector3f viewDirection = minecraft.gameRenderer.getMainCamera().getLookVector();
        float lookingAtSun = (float) Mth.smoothstep(Mth.clamp((viewDirection.dot(sunDirection) - 0.72F) / 0.25F, 0.0F, 1.0F));

        float target = 1.0F - (float) Mth.smoothstep(Mth.clamp((localLight - 0.18F) / 0.62F, 0.0F, 1.0F));
        target *= 1.0F - lookingAtSun * 0.98F;

        float rate = target > adaptation ? DARK_ADAPTATION_RATE : LIGHT_ADAPTATION_RATE;
        adaptation = Mth.lerp(rate, adaptation, target);
    }

    public static float value() {
        return adaptation;
    }
}
