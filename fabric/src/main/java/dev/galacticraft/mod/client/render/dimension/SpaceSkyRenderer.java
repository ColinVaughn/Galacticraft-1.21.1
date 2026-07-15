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

package dev.galacticraft.mod.client.render.dimension;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.galacticraft.api.registry.AddonRegistries;
import dev.galacticraft.api.universe.celestialbody.CelestialBody;
import dev.galacticraft.mod.client.render.dimension.star.CelestialBodyRendererManager;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

public class SpaceSkyRenderer {
    protected final StarManager starManager = new StarManager();

    public void render(GCWorldRenderContext context) {
        PoseStack matrices = new PoseStack();
        matrices.mulPose(context.positionMatrix());
        // render whole skybox black for when first loading into the dimension
        RenderSystem.setShaderColor(0.0F, 0.0F, 0.0F, 1.0F);

        RenderSystem.disableBlend();
        RenderSystem.blendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO
        );

        context.profiler().push("celestial_render");
        matrices.pushPose();

        CelestialBodyRendererManager celestialBodyRendererManager = this.celestialBodyRendererManager(context);
        celestialBodyRendererManager.updateSolarPosition(
            context.camera().getPosition().x / 128.0,
            context.camera().getPosition().y / 128.0,
            context.camera().getPosition().z / 128.0
        );

        celestialBodyRendererManager.render(context);

        matrices.popPose();
        context.profiler().pop();
        RenderSystem.setShaderColor(1.0f, 1.0F, 1.0F, 1.0F);

    }

    /** Resolves an isolated render world space from the current body's root star. */
    protected CelestialBodyRendererManager celestialBodyRendererManager(GCWorldRenderContext context) {
        Registry<CelestialBody<?, ?>> registry = context.world().registryAccess().registryOrThrow(AddonRegistries.CELESTIAL_BODY);
        Holder<CelestialBody<?, ?>> holder = context.world().galacticraft$getCelestialBody();
        if (holder == null) {
            return CelestialBodyRendererManager.getInstance(context.world().dimension().location());
        }

        CelestialBody<?, ?> root = holder.value();
        Set<CelestialBody<?, ?>> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        while (root.parent().isPresent() && visited.add(root)) {
            root = root.parentValue(registry);
        }
        ResourceLocation rootId = registry.getKey(root);
        return CelestialBodyRendererManager.getInstance(rootId != null ? rootId : context.world().dimension().location());
    }
}
