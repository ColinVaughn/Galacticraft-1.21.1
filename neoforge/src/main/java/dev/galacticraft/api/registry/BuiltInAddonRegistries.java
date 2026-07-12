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

package dev.galacticraft.api.registry;

import dev.galacticraft.api.universe.celestialbody.CelestialBodyType;
import dev.galacticraft.api.universe.celestialbody.CelestialHandler;
import dev.galacticraft.api.universe.celestialbody.landable.teleporter.type.CelestialTeleporterType;
import dev.galacticraft.api.universe.display.CelestialDisplayType;
import dev.galacticraft.api.universe.display.ring.CelestialRingDisplayType;
import dev.galacticraft.api.universe.position.CelestialPositionType;
import dev.galacticraft.mod.Constant;
import net.minecraft.core.Registry;
import net.minecraft.core.WritableRegistry;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class BuiltInAddonRegistries {
    private static final DeferredRegister<CelestialPositionType<?>> POSITIONS = create(AddonRegistries.CELESTIAL_POSITION_TYPE);
    private static final DeferredRegister<CelestialDisplayType<?>> DISPLAYS = create(AddonRegistries.CELESTIAL_DISPLAY_TYPE);
    private static final DeferredRegister<CelestialRingDisplayType<?>> RINGS = create(AddonRegistries.CELESTIAL_RING_DISPLAY_TYPE);
    private static final DeferredRegister<CelestialBodyType<?>> BODY_TYPES = create(AddonRegistries.CELESTIAL_BODY_TYPE);
    private static final DeferredRegister<CelestialTeleporterType<?>> TELEPORTERS = create(AddonRegistries.CELESTIAL_TELEPORTER_TYPE);
    private static final DeferredRegister<CelestialHandler> HANDLERS = create(AddonRegistries.CELESTIAL_HANDLER);

    public static final WritableRegistry<CelestialPositionType<?>> CELESTIAL_POSITION_TYPE = registry(POSITIONS, Constant.id("static"));
    public static final WritableRegistry<CelestialDisplayType<?>> CELESTIAL_DISPLAY_TYPE = registry(DISPLAYS, Constant.id("empty"));
    public static final WritableRegistry<CelestialRingDisplayType<?>> CELESTIAL_RING_DISPLAY_TYPE = registry(RINGS, Constant.id("empty"));
    public static final WritableRegistry<CelestialBodyType<?>> CELESTIAL_BODY_TYPE = registry(BODY_TYPES, Constant.id("star"));
    public static final WritableRegistry<CelestialTeleporterType<?>> CELESTIAL_TELEPORTER_TYPE = registry(TELEPORTERS, Constant.id("direct"));
    public static final WritableRegistry<CelestialHandler> CELESTIAL_HANDLER = registry(HANDLERS, null);

    private BuiltInAddonRegistries() {
    }

    private static <T> DeferredRegister<T> create(net.minecraft.resources.ResourceKey<? extends Registry<T>> key) {
        return DeferredRegister.create(key, Constant.MOD_ID);
    }

    @SuppressWarnings("unchecked")
    private static <T> WritableRegistry<T> registry(DeferredRegister<T> deferred, net.minecraft.resources.ResourceLocation defaultKey) {
        Registry<T> registry = deferred.makeRegistry(builder -> {
            if (defaultKey != null) builder.defaultKey(defaultKey);
        });
        return (WritableRegistry<T>) registry;
    }

    public static void register(IEventBus bus) {
        POSITIONS.register(bus);
        DISPLAYS.register(bus);
        RINGS.register(bus);
        BODY_TYPES.register(bus);
        TELEPORTERS.register(bus);
        HANDLERS.register(bus);
    }
}
