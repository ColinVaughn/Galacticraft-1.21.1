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

package dev.galacticraft.api.event;

import dev.architectury.event.Event;
import dev.architectury.event.EventFactory;
import dev.architectury.event.EventResult;
import dev.galacticraft.api.rocket.LaunchStage;
import dev.galacticraft.api.rocket.entity.Rocket;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

/**
 * Rocket flight events. They work the same on Fabric and NeoForge. All but
 * {@link #STAGE_CHANGED} fire on the logical server only.
 */
public final class RocketEvents {
    /** Fired on both sides whenever a rocket's launch stage changes; read the new stage from the rocket. */
    public static final Event<StageChanged> STAGE_CHANGED = EventFactory.createLoop();

    /**
     * Fired when the pilot confirms launch, before the countdown starts. Return
     * {@link EventResult#interruptFalse()} to keep the rocket on the pad; tell the pilot why yourself.
     */
    public static final Event<PreLaunch> PRE_LAUNCH = EventFactory.createEventResult();

    /** Fired the tick the rocket leaves the pad, after the countdown. */
    public static final Event<Launch> LAUNCH = EventFactory.createLoop();

    /**
     * Fired when the rocket reaches the edge of space, before its passengers are sent to the
     * celestial map (or it docks at a launch controller's destination).
     */
    public static final Event<ReachedSpace> REACHED_SPACE = EventFactory.createLoop();

    /** Fired when a rocket settles after a landing, by hand or at a launch controller's pad. */
    public static final Event<Landed> LANDED = EventFactory.createLoop();

    private RocketEvents() {
    }

    @FunctionalInterface
    public interface StageChanged {
        void onStageChanged(Rocket rocket, LaunchStage oldStage);
    }

    @FunctionalInterface
    public interface PreLaunch {
        EventResult preLaunch(Rocket rocket, @Nullable Entity pilot);
    }

    @FunctionalInterface
    public interface Launch {
        void launch(Rocket rocket);
    }

    @FunctionalInterface
    public interface ReachedSpace {
        void reachedSpace(Rocket rocket);
    }

    @FunctionalInterface
    public interface Landed {
        void landed(Rocket rocket);
    }
}
