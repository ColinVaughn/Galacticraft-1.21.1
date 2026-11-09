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

package dev.galacticraft.neoforge.compat;

import dev.galacticraft.mod.compat.geckolib.GeckoSpaceGearLayer;
import net.neoforged.neoforge.common.NeoForge;
import software.bernie.geckolib.event.GeoRenderEvent;

/**
 * Adds the fitted space gear to every GeckoLib entity renderer on NeoForge. Only call this when
 * GeckoLib is loaded. GeckoLib posts the event on the game bus as each renderer is built.
 */
public final class GeckoLibNeoForgeCompat {
    private GeckoLibNeoForgeCompat() {}

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void register() {
        // Specify the event class because the bus cannot infer it from the lambda.
        NeoForge.EVENT_BUS.addListener(GeoRenderEvent.Entity.CompileRenderLayers.class,
                event -> event.addLayer(new GeckoSpaceGearLayer(event.getRenderer())));
    }
}
