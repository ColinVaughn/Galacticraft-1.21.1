/* Copyright (c) 2026 Team Galacticraft. Licensed under the MIT license. */
package dev.galacticraft.api.universe.celestialbody;

import java.util.concurrent.atomic.AtomicLong;

/** Invalidates lazy level bindings after a runtime celestial registry mutation, on each logical side. */
public final class CelestialBodyCache {
    private static final AtomicLong REVISION = new AtomicLong();

    private CelestialBodyCache() { }

    public static void invalidate() { REVISION.incrementAndGet(); }

    public static long revision() { return REVISION.get(); }
}