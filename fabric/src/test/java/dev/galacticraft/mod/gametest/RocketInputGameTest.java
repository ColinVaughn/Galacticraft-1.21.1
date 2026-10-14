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

package dev.galacticraft.mod.gametest;

import dev.galacticraft.api.rocket.LaunchStage;
import dev.galacticraft.api.rocket.RocketPrefabs;
import dev.galacticraft.mod.content.GCEntityTypes;
import dev.galacticraft.mod.content.entity.vehicle.RocketEntity;
import dev.galacticraft.mod.util.FluidUtil;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

public class RocketInputGameTest implements GalacticraftGameTest {
    @GameTest(template = EMPTY_STRUCTURE)
    public void steeringPreservesConflictingKeysAndInvertedControls(GameTestHelper test) {
        var rocket = new RocketEntity(GCEntityTypes.ROCKET, test.getLevel());
        rocket.setData(RocketPrefabs.TIER_1);
        rocket.setLaunchStage(LaunchStage.LAUNCHED);
        for (int keys = 0; keys < 128; keys++) {
            boolean up = (keys & 1) != 0, down = (keys & 2) != 0;
            boolean left = (keys & 4) != 0, right = (keys & 8) != 0;
            boolean jump = (keys & 16) != 0, shift = (keys & 32) != 0, inverted = (keys & 64) != 0;
            rocket.setXRot(0);
            rocket.setYRot(0);
            rocket.setZRot(0);
            rocket.inputTick(0, 0, up, down, left, right, jump, shift, inverted);
            float pitch = inverted ? (down ? -1 : up ? 1 : 0) : (up ? -1 : down ? 1 : 0);
            float yaw = inverted ? (left ? -2 : right ? 2 : 0) : (right ? -2 : left ? 2 : 0);
            boolean rollLeft = inverted ? shift && !left : jump && !right;
            boolean rollRight = inverted ? jump && !right : shift && !left;
            test.assertTrue(rocket.getXRot() == pitch && rocket.getYRot() == yaw
                    && rocket.getZRot() == (rollLeft ? -2 : rollRight ? 2 : 0), "Steering changed for key mask " + keys);
        }
        rocket.setXRot(180);
        rocket.inputTick(0, 0, false, true, false, false, false, false, false);
        test.assertTrue(rocket.getXRot() == 180, "Pitch exceeded its upper limit");
        rocket.setXRot(-180);
        rocket.inputTick(0, 0, true, false, false, false, false, false, false);
        test.assertTrue(rocket.getXRot() == -180, "Pitch exceeded its lower limit");
        test.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void launchRequiresTwoSeparatePressesAndFuel(GameTestHelper test) {
        var rocket = new RocketEntity(GCEntityTypes.ROCKET, test.getLevel());
        rocket.setData(RocketPrefabs.TIER_1);
        var pilot = test.makeMockServerPlayerInLevel();
        pilot.startRiding(rocket, true);
        press(rocket, true);
        test.assertTrue(rocket.getLaunchStage() == LaunchStage.IDLE, "An empty tank accepted launch confirmation");
        rocket.setFuel(FluidUtil.bucketsToDroplets(5));
        // Cancelling for lack of fuel resets the press latch as well as launch confirmation.
        press(rocket, true);
        test.assertTrue(rocket.getLaunchStage() == LaunchStage.WARNING, "First press did not show the warning");
        press(rocket, true);
        test.assertTrue(rocket.getLaunchStage() == LaunchStage.WARNING, "Holding jump confirmed launch");
        rocket.setFuel(0);
        press(rocket, false);
        test.assertTrue(rocket.getLaunchStage() == LaunchStage.IDLE, "Fuel loss did not cancel the warning");
        rocket.setFuel(FluidUtil.bucketsToDroplets(5));
        press(rocket, true);
        test.assertTrue(rocket.getLaunchStage() == LaunchStage.WARNING, "Fuel loss did not reset confirmation");
        press(rocket, false);
        press(rocket, true);
        test.assertTrue(rocket.getLaunchStage() == LaunchStage.IGNITED, "Second separate press did not ignite");
        pilot.stopRiding();
        pilot.discard();
        test.succeed();
    }

    private static void press(RocketEntity rocket, boolean pressed) {
        rocket.inputTick(0, 0, false, false, false, false, pressed, false, false);
    }
}
