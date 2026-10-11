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

package dev.galacticraft.mod.client;

import dev.architectury.networking.NetworkManager;
import dev.galacticraft.mod.client.gui.overlay.SensorGlassesOverlay;
import dev.galacticraft.mod.client.gui.screen.ingame.CelestialSelectionScreen;
import dev.galacticraft.mod.content.entity.vehicle.Buggy;
import dev.galacticraft.mod.content.entity.vehicle.RocketEntity;
import dev.galacticraft.mod.network.c2s.OpenRocketPayload;
import dev.galacticraft.mod.network.c2s.ToggleBurrowerTunnelPayload;
import dev.galacticraft.mod.network.c2s.ToggleRocketLandingPayload;
import dev.galacticraft.mod.content.entity.LunarBurrowerEntity;
import dev.galacticraft.mod.util.Translations;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.lwjgl.glfw.GLFW;

public final class GCKeyBinds {
    public static final KeyMapping OPEN_CELESTIAL_SCREEN = new KeyMapping(
            Translations.Keybindings.OPEN_CELESTIAL_SCREEN, GLFW.GLFW_KEY_M, KeyMapping.CATEGORY_MISC);
    public static final KeyMapping OPEN_ROCKET_INVENTORY = new KeyMapping(
            Translations.Keybindings.ROCKET_INVENTORY, GLFW.GLFW_KEY_F, KeyMapping.CATEGORY_INVENTORY);
    public static final KeyMapping TOGGLE_SENSOR_GLASSES_MODE = new KeyMapping(
            Translations.Keybindings.SENSOR_GLASSES_MODE, GLFW.GLFW_KEY_K, KeyMapping.CATEGORY_MISC);
    public static final KeyMapping TOGGLE_BURROWER_TUNNEL = new KeyMapping(
            Translations.Keybindings.BURROWER_TUNNEL, GLFW.GLFW_KEY_G, KeyMapping.CATEGORY_MISC);
    public static final KeyMapping TOGGLE_ROCKET_LANDING = new KeyMapping(
            Translations.Keybindings.ROCKET_LANDING, GLFW.GLFW_KEY_V, KeyMapping.CATEGORY_MISC);

    public static void handleKeybinds(Minecraft client) {
        if (client.level == null || client.player == null || client.screen != null) return;
        while (OPEN_CELESTIAL_SCREEN.consumeClick()) {
            client.setScreen(new CelestialSelectionScreen(true, null, false, null));
        }
        while (OPEN_ROCKET_INVENTORY.consumeClick()) {
            Entity vehicle = client.player.getVehicle();
            if (vehicle instanceof RocketEntity || vehicle instanceof Buggy) {
                NetworkManager.sendToServer(OpenRocketPayload.INSTANCE);
            }
        }
        while (TOGGLE_SENSOR_GLASSES_MODE.consumeClick()) {
            SensorGlassesOverlay.toggleAdvancedMode();
        }
        while (TOGGLE_BURROWER_TUNNEL.consumeClick()) {
            if (client.player.getVehicle() instanceof LunarBurrowerEntity worm && worm.getControllingPassenger() == client.player)
                NetworkManager.sendToServer(ToggleBurrowerTunnelPayload.INSTANCE);
        }
        while (TOGGLE_ROCKET_LANDING.consumeClick()) {
            if (client.player.getVehicle() instanceof RocketEntity)
                NetworkManager.sendToServer(ToggleRocketLandingPayload.INSTANCE);
        }
    }

    public static void renderBurrowerMode(net.minecraft.client.gui.GuiGraphics graphics, net.minecraft.client.DeltaTracker delta) {
        Minecraft client = Minecraft.getInstance();
        if (client.options.hideGui || client.player == null || !(client.player.getVehicle() instanceof LunarBurrowerEntity worm)
                || worm.getControllingPassenger() != client.player) return;
        var text = net.minecraft.network.chat.Component.translatable(worm.isTunnelMode()
                ? "entity.galacticraft.lunar_burrower.tunnel_on" : "entity.galacticraft.lunar_burrower.tunnel_off",
                TOGGLE_BURROWER_TUNNEL.getTranslatedKeyMessage());
        graphics.drawCenteredString(client.font, text, graphics.guiWidth() / 2, graphics.guiHeight() - 64,
                worm.isTunnelMode() ? 0x7AE7D9 : 0xFFFFFF);
    }

    private GCKeyBinds() {}
}
