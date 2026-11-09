package dev.galacticraft.mod.util;

import net.minecraft.util.Mth;

/** Client visual state for the lander's Blockbench clips, driven by what spectators can observe. */
public final class LanderAnimationState {
    /** Longer than the touchdown clip, so an idle lander samples its rest pose. */
    private static final int TOUCHDOWN_SETTLED = 20;
    private float door, previousDoor, thrust, previousThrust;
    private int touchdownTicks = TOUCHDOWN_SETTLED;
    private Boolean wasGrounded;

    public void tick(boolean grounded, boolean occupied, boolean thrusting) {
        previousDoor = door;
        previousThrust = thrust;
        door = Mth.approach(door, grounded && !occupied ? 1 : 0, 0.1F);
        thrust = Mth.approach(thrust, thrusting ? 1 : 0, 0.2F);
        // A lander that loads in already standing has not just landed.
        if (wasGrounded != null && grounded && !wasGrounded) touchdownTicks = 0;
        else if (touchdownTicks < TOUCHDOWN_SETTLED) touchdownTicks++;
        wasGrounded = grounded;
    }

    public float door(float partialTick) { return Mth.lerp(partialTick, previousDoor, door); }
    public float thrust(float partialTick) { return Mth.lerp(partialTick, previousThrust, thrust); }
    public float touchdownTime(float partialTick) { return (touchdownTicks + partialTick) / 20F; }
}
