package dev.galacticraft.mod.client.gui.overlay;

import dev.galacticraft.api.universe.celestialbody.CelestialBodyTags;
import dev.galacticraft.mod.network.s2c.JupiterPressurePayload;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import java.util.Locale;

public final class JupiterPressureOverlay {
    private static JupiterPressurePayload status;
    private static long received;
    public static void accept(JupiterPressurePayload payload) { status = payload; received = System.nanoTime(); }
    public static void onHudRender(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.options.hideGui || mc.player.isCreative() || mc.player.isSpectator()
                || !CelestialBodyTags.is(mc.level, CelestialBodyTags.GAS_GIANT) || status == null || System.nanoTime() - received > 3_000_000_000L) return;
        Component line = Component.translatable("gui.galacticraft.jupiter.pressure", String.format(Locale.ROOT, "%.1f", status.ambient()),
                (int) status.rating(), Math.round(status.charge() * 100));
        int x = mc.getWindow().getGuiScaledWidth() - mc.font.width(line) - 6;
        graphics.drawString(mc.font, line, x, 48, status.ambient() > status.rating() + 0.001F ? 0xFFFF7755 : 0xFFE4CE9A);
    }
}