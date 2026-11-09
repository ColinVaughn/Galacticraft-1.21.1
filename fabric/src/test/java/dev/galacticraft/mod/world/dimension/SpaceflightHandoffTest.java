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

package dev.galacticraft.mod.world.dimension;

import org.junit.jupiter.api.Test;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class SpaceflightHandoffTest {
    @Test void allMountedClientsMustAcknowledgeTheCurrentTransfer() {
        UUID token = UUID.randomUUID(), pilot = UUID.randomUUID(), crew = UUID.randomUUID();
        var handoff = new SpaceflightHandoff(token, Set.of(pilot, crew));
        assertFalse(handoff.ready());
        assertFalse(handoff.acknowledge(pilot, UUID.randomUUID()));
        assertFalse(handoff.acknowledge(UUID.randomUUID(), token));
        assertTrue(handoff.acknowledge(pilot, token));
        assertFalse(handoff.ready());
        assertFalse(handoff.acknowledge(pilot, token));
        assertTrue(handoff.acknowledge(crew, token));
        assertTrue(handoff.ready());
    }

    @Test void leavingPassengersCannotStrandTheRemainingCrew() {
        UUID token = UUID.randomUUID(), pilot = UUID.randomUUID(), crew = UUID.randomUUID();
        var handoff = new SpaceflightHandoff(token, Set.of(pilot, crew));
        handoff.acknowledge(pilot, token);
        handoff.retainPassengers(Set.of(pilot));
        assertTrue(handoff.ready());
    }

    @Test void emptyCraftNeedsNoClientHandshake() {
        assertTrue(new SpaceflightHandoff(UUID.randomUUID(), Set.of()).ready());
    }
}