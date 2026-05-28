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

import dev.galacticraft.mod.client.render.dimension.duststorm.ClientDustStorms;
import dev.galacticraft.mod.content.entity.vehicle.LanderEntity;
import dev.galacticraft.mod.content.item.GCItems;
import dev.galacticraft.mod.util.Translations;
import dev.galacticraft.mod.world.dimension.GCDimensions;
import dev.galacticraft.mod.world.dimension.duststorm.DustStormPhase;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FastColor;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * Draws the dust-storm HUD: a rust screen tint that reduces visibility while a player is exposed
 * (halved by sensor glasses), plus a forecast readout for players wearing detector-class gear.
 */
public class DustStormOverlay {
    public static void onHudRender(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.level == null || mc.player == null) return;
        if (!mc.level.dimension().equals(GCDimensions.MARS)) return;

        LocalPlayer player = mc.player;
        float intensity = ClientDustStorms.intensity();
        DustStormPhase phase = ClientDustStorms.phase();

        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();

        // --- Screen tint (only when exposed to the open storm) ---
        boolean sheltered = mc.level.isBreathable(player.blockPosition().above())
                || player.getVehicle() instanceof LanderEntity;
        if (intensity > 0.05F && !sheltered && !player.isSpectator()) {
            boolean glasses = player.getItemBySlot(EquipmentSlot.HEAD).is(GCItems.SENSOR_GLASSES);
            float alphaF = Math.min(0.38F, intensity * 0.42F);
            if (glasses) alphaF *= 0.45F;
            int topAlpha = (int) (alphaF * 255.0F);
            if (topAlpha > 0) {
                // Warm dust haze, denser toward the top/horizon and thinning toward the ground.
                int top = FastColor.ARGB32.color(topAlpha, 173, 125, 84);
                int bottom = FastColor.ARGB32.color((int) (topAlpha * 0.35F), 173, 125, 84);
                graphics.fillGradient(0, 0, width, height, top, bottom);
            }
        }

        // --- Forecast readout (only with detector-class gear) ---
        if (hasDetectorGear(player)) {
            Component text = null;
            if (phase == DustStormPhase.INCOMING) {
                int seconds = ClientDustStorms.secondsUntilStorm();
                if (seconds >= 0) text = Component.translatable(Translations.Ui.DUST_STORM_INCOMING, seconds);
            } else if (phase.isStormActive()) {
                int seconds = ClientDustStorms.secondsUntilClear();
                if (seconds >= 0) text = Component.translatable(Translations.Ui.DUST_STORM_CLEARING, seconds);
            }
            if (text != null) {
                int x = width / 2 - mc.font.width(text) / 2;
                graphics.drawString(mc.font, text, x, 12, 0xFFE0B080, true);
            }
        }
    }

    /**
     * The forecast readout is unlocked by gear the player already builds toward: the sensor
     * glasses (a heads-up display) or the frequency module (a comms/telemetry link).
     */
    private static boolean hasDetectorGear(LocalPlayer player) {
        if (player.getItemBySlot(EquipmentSlot.HEAD).is(GCItems.SENSOR_GLASSES)) return true;
        Container accessories = player.galacticraft$getAccessories();
        for (int i = 0; i < accessories.getContainerSize(); i++) {
            if (accessories.getItem(i).is(GCItems.FREQUENCY_MODULE)) return true;
        }
        return false;
    }
}
