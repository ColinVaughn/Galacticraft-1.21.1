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

import net.minecraft.sounds.SoundEvent;
import org.jetbrains.annotations.Nullable;

/**
 * What to do with a machine's looping sound when its status changes.
 *
 * Kept apart from {@link GCSoundManager} so the decision can be checked without a running
 * client. The rule that matters: most machine statuses map to the same sound event, so a machine
 * flipping between two of them must keep the one loop it already has rather than starting another.
 */
public final class MachineSoundPolicy {
    private MachineSoundPolicy() {
    }

    public enum Action {
        /** No sound now, none wanted. */
        NOTHING,
        /** Nothing is playing and a sound is wanted. */
        START,
        /** What is playing is wrong or already fading; wind it down and start the wanted one. */
        RESTART,
        /** Something is playing and nothing is wanted. */
        STOP,
        /** The right sound is already playing; leave it be. */
        KEEP
    }

    /**
     * @param current  the event of the sound already running for this machine, or null if silent
     * @param fading   whether that sound is already winding down
     * @param next     the event the machine's new status calls for, or null for silence
     */
    public static Action decide(@Nullable SoundEvent current, boolean fading, @Nullable SoundEvent next) {
        if (current == null) {
            return next == null ? Action.NOTHING : Action.START;
        }
        if (next == null) {
            // Already fading out on its own; ending it again would only churn.
            return fading ? Action.NOTHING : Action.STOP;
        }
        if (current.equals(next) && !fading) {
            return Action.KEEP;
        }
        return Action.RESTART;
    }
}
