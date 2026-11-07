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
import dev.galacticraft.api.universe.celestialbody.CelestialBody;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * Travel between celestial bodies, fired on the logical server. They work the same on Fabric and
 * NeoForge.
 */
public final class CelestialEvents {
    /**
     * Fired before a player enters another celestial body, after the rocket's travel rules allow
     * it. Return {@link EventResult#interruptFalse()} to refuse. Map travel stays on the map;
     * physical flight stays outside the destination atmosphere. Listeners may explain the refusal.
     */
    public static final Event<BeforeTravel> BEFORE_TRAVEL = EventFactory.createEventResult();

    /** Fired after a player has been sent to a celestial body. */
    public static final Event<AfterTravel> AFTER_TRAVEL = EventFactory.createLoop();

    private CelestialEvents() {
    }

    @FunctionalInterface
    public interface BeforeTravel {
        EventResult beforeTravel(ServerPlayer player, CelestialBody<?, ?> to, @Nullable CelestialBody<?, ?> from);
    }

    @FunctionalInterface
    public interface AfterTravel {
        void afterTravel(ServerPlayer player, CelestialBody<?, ?> to, @Nullable CelestialBody<?, ?> from);
    }
}
