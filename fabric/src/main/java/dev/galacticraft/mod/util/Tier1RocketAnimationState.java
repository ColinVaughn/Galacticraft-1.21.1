package dev.galacticraft.mod.util;

import dev.galacticraft.api.rocket.LaunchStage;
import net.minecraft.util.Mth;

/** Interpolated client-only hatch and landing-leg positions. */
public final class Tier1RocketAnimationState {
    private float hatch, previousHatch, gear, previousGear;

    public void tick(LaunchStage stage, boolean occupied) {
        tick(stage, occupied, false);
    }

    public void tick(LaunchStage stage, boolean occupied, boolean landing) {
        previousHatch = hatch;
        previousGear = gear;
        hatch = Mth.approach(hatch, !occupied && (stage == LaunchStage.IDLE || stage == LaunchStage.WARNING) ? 1 : 0, 0.1F);
        gear = Mth.approach(gear, stage == LaunchStage.LAUNCHED && !landing ? 1 : 0, 0.05F);
    }

    public float hatch(float partialTick) { return Mth.lerp(partialTick, previousHatch, hatch); }
    public float gear(float partialTick) { return Mth.lerp(partialTick, previousGear, gear); }
}
