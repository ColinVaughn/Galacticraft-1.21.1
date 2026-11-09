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

package dev.galacticraft.test.geckolib;

import dev.galacticraft.mod.compat.geckolib.GeckoSpaceGearLayer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public final class GeoDummyClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        if (FabricLoader.getInstance().isModLoaded("geckolib")) {
            Renderers.register();
        }
    }

    /** Returns whether Galacticraft's gear layer is attached to the GeckoLib renderer for {@code entity}. */
    public static boolean rendererHasSpaceGear(Entity entity) {
        return FabricLoader.getInstance().isModLoaded("geckolib") && Renderers.hasSpaceGear(entity);
    }

    // Keep optional renderer classes out of entrypoint verification when GeckoLib is absent.
    private static final class Renderers {
        private static void register() {
            EntityRendererRegistry.register(GeoDummy.Types.type,
                    context -> new GeoEntityRenderer<>(context, new DefaultedEntityGeoModel<>(GeoDummy.ID)));
        }
        private static boolean hasSpaceGear(Entity entity) {
            return Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity) instanceof GeoEntityRenderer<?> renderer
                    && renderer.getRenderLayers().stream().anyMatch(layer -> layer instanceof GeckoSpaceGearLayer<?>);
        }
    }
}
