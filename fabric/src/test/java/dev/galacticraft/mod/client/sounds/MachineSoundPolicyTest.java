/*
 * Copyright (c) 2019-2026 Team Galacticraft
 * Copyright (c) 2026 Colin Vaughn
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

package dev.galacticraft.mod.client.sounds;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Covers what a machine's looping sound should do when its status changes.
 *
 * <p>Many machine statuses share one sound event, so a machine flipping between two of them used to
 * start a fresh looping instance every time while only ever winding down the first one it found.
 * The instances accumulated until the sound engine ran out of channels and the whole game went
 * silent until relog. The rule that prevents it: a status change that does not change the sound
 * leaves the running sound alone, and every change that does starts at most one replacement.
 */
class MachineSoundPolicyTest {
    private static final SoundEvent BUZZ = SoundEvent.createVariableRangeEvent(ResourceLocation.parse("galacticraft:test_buzz"));
    private static final SoundEvent FAN = SoundEvent.createVariableRangeEvent(ResourceLocation.parse("galacticraft:test_fan"));

    @Test
    void aStatusChangeThatKeepsTheSameSoundDoesNotRestartIt() {
        assertEquals(MachineSoundPolicy.Action.KEEP, MachineSoundPolicy.decide(BUZZ, false, BUZZ),
                "flipping between two statuses that share a sound must not stack a second loop");
    }

    @Test
    void aFadingSoundIsReplacedRatherThanLeftToDie() {
        assertEquals(MachineSoundPolicy.Action.RESTART, MachineSoundPolicy.decide(BUZZ, true, BUZZ),
                "the machine is audible again, so the fade-out must be cut short and replaced");
    }

    @Test
    void aDifferentSoundReplacesTheRunningOne() {
        assertEquals(MachineSoundPolicy.Action.RESTART, MachineSoundPolicy.decide(BUZZ, false, FAN));
    }

    @Test
    void aMachineThatFallsSilentStopsItsSound() {
        assertEquals(MachineSoundPolicy.Action.STOP, MachineSoundPolicy.decide(BUZZ, false, null));
    }

    @Test
    void aSilentMachineThatStartsUpBeginsASound() {
        assertEquals(MachineSoundPolicy.Action.START, MachineSoundPolicy.decide(null, false, BUZZ));
    }

    @Test
    void aMachineWithNothingToPlayDoesNothing() {
        assertEquals(MachineSoundPolicy.Action.NOTHING, MachineSoundPolicy.decide(null, false, null));
    }

    @Test
    void aFadingSoundIsNotStoppedTwiceWhenTheMachineStaysSilent() {
        assertEquals(MachineSoundPolicy.Action.NOTHING, MachineSoundPolicy.decide(BUZZ, true, null),
                "it is already winding down; ending it again would just churn");
    }
}
