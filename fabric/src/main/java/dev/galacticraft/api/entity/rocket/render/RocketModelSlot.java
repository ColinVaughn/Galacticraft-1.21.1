package dev.galacticraft.api.entity.rocket.render;

import com.mojang.blaze3d.vertex.PoseStack;

/** Fixed-size rocket part origins, measured from the assembled model base (16 model units per block). */
public enum RocketModelSlot {
    ENGINE(7), BOOSTER(15), FIN(15), BODY(31), CONE(59);

    private final double y;

    RocketModelSlot(int modelY) { this.y = modelY / 16.0; }

    public double y() { return y; }

    /** Call inside a separate push/pop pair for each part, so absent or replaced parts cannot move their neighbours. */
    public void mount(PoseStack matrices) { matrices.translate(0, y, 0); }
}
