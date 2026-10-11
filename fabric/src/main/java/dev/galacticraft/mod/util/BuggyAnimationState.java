package dev.galacticraft.mod.util;

import net.minecraft.util.Mth;

/** Client visual state, driven by observed movement so spectators animate the same vehicle. */
public final class BuggyAnimationState {
    public static final double WHEEL_CIRCUMFERENCE = 2 * Math.PI * 0.4;
    private static final int CLIMB_HOLD_TICKS = 6, CLIMB_DURATION_TICKS = 16;
    public static final float CLIMB_HOLD_TIME = CLIMB_HOLD_TICKS / 20F;
    public static final float CLIMB_DURATION = CLIMB_DURATION_TICKS / 20F;
    private double travel, previousTravel;
    private float steering, previousSteering, drive, previousDrive;
    private int climbTicks = CLIMB_DURATION_TICKS, previousClimbTicks = CLIMB_DURATION_TICKS;
    private int climbDirection = 1;
    private boolean wasGrounded;

    public void tick(double dx, double dy, double dz, float yaw, float steeringTarget, boolean grounded, boolean blocked) {
        previousTravel = travel;
        previousSteering = steering;
        previousDrive = drive;
        previousClimbTicks = climbTicks;
        double distance = Math.hypot(dx, dz);
        // Position corrections and teleports should not spin the wheels or kick the suspension.
        if (distance > 2 || Math.abs(dy) > 1.25) distance = dx = dy = dz = 0;
        double heading = Math.toRadians(yaw);
        double direction = -dx * Math.sin(heading) + dz * Math.cos(heading);
        travel += Math.copySign(distance, direction);
        steering = Mth.lerp(0.35F, steering, Mth.clamp(steeringTarget, -30, 30));
        drive = Mth.lerp(0.25F, drive, grounded ? (float) Math.min(distance / 0.5, 1) : 0);
        boolean ascending = dy > 0.01 && (distance > 0.005 || blocked)
                && (grounded || wasGrounded || blocked || climbTicks <= CLIMB_HOLD_TICKS);
        if (ascending) {
            if (climbTicks >= CLIMB_DURATION_TICKS) {
                climbTicks = previousClimbTicks = 0;
                climbDirection = direction < 0 ? -1 : 1;
            }
            // Stay braced while rising; consecutive steps ease back into the same pose.
            climbTicks = climbTicks < CLIMB_HOLD_TICKS ? climbTicks + 1 : Math.max(CLIMB_HOLD_TICKS, climbTicks - 1);
        } else {
            climbTicks = Math.min(CLIMB_DURATION_TICKS, climbTicks + 1);
        }
        wasGrounded = grounded;
    }

    public double wheelTurns(float partialTick) {
        return (previousTravel + (travel - previousTravel) * partialTick) / WHEEL_CIRCUMFERENCE;
    }

    public float steering(float partialTick) { return Mth.lerp(partialTick, previousSteering, steering); }
    public float drive(float partialTick) { return Mth.lerp(partialTick, previousDrive, drive); }
    public float climbTime(float partialTick) { return Mth.lerp(partialTick, previousClimbTicks, climbTicks) / 20F; }
    public int climbDirection() { return climbDirection; }
}
