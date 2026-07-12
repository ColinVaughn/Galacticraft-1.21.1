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

package dev.galacticraft.mod.client.gui.overlay;

import dev.galacticraft.mod.client.render.dimension.solarflare.ClientSolarFlares;
import dev.galacticraft.mod.content.entity.vehicle.LanderEntity;
import dev.galacticraft.mod.content.item.GCItems;
import dev.galacticraft.mod.world.dimension.GCDimensions;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.FastColor;
import net.minecraft.world.entity.EquipmentSlot;

/**
 * Draws the solar-flare HUD: a bright white/orange screen haze that washes out the view while a
 * player is exposed to a Mercury flare. The opacity scales with the current flare intensity and is
 * dimmed by sensor glasses (which cut the glare).
 */
public class SolarFlareOverlay {
    public static void onHudRender(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.level == null || mc.player == null) return;
        if (!mc.level.dimension().equals(GCDimensions.MERCURY)) return;

        LocalPlayer player = mc.player;
        float intensity = ClientSolarFlares.intensity();

        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();

        // --- Bright glare wash (only when exposed to the open flare) ---
        boolean sheltered = mc.level.isBreathable(player.blockPosition().above())
                || player.getVehicle() instanceof LanderEntity;
        if (intensity > 0.05F && !sheltered && !player.isSpectator()) {
            boolean glasses = player.getItemBySlot(EquipmentSlot.HEAD).is(GCItems.SENSOR_GLASSES);
            float alphaF = Math.min(0.55F, intensity * 0.6F);
            if (glasses) alphaF *= 0.45F;
            int topAlpha = (int) (alphaF * 255.0F);
            if (topAlpha > 0) {
                // Searing white-orange light, brightest toward the top/sky and easing toward the ground.
                int top = FastColor.ARGB32.color(topAlpha, 255, 244, 214);
                int bottom = FastColor.ARGB32.color((int) (topAlpha * 0.6F), 255, 224, 170);
                graphics.fillGradient(0, 0, width, height, top, bottom);
            }
        }
    }
}
