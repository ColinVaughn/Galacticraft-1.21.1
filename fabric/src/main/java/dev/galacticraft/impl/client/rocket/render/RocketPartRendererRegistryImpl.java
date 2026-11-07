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

package dev.galacticraft.impl.client.rocket.render;

import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.Registry;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.Minecraft;
import dev.galacticraft.api.entity.rocket.render.RocketPartRenderer;
import dev.galacticraft.api.entity.rocket.render.RocketPartRendererRegistry;
import dev.galacticraft.api.rocket.part.RocketPart;
import dev.galacticraft.mod.Constant;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resources.ResourceKey;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Environment(EnvType.CLIENT)
public class RocketPartRendererRegistryImpl implements RocketPartRendererRegistry {
    private final Map<ResourceKey<? extends RocketPart<?, ?>>, RocketPartRenderer> renderers = new HashMap<>();
    private final Set<ResourceKey<?>> warned = new HashSet<>();
    private final Map<ResourceLocation, RocketPartRenderer> modelRenderers = new HashMap<>();

    public RocketPartRendererRegistryImpl() {
    }

    @Override
    public <T extends RocketPart<?, ?>> void register(@NotNull ResourceKey<T> id, @NotNull RocketPartRenderer renderer) {
        this.renderers.put(id, renderer);
    }

    @Override
    public <T extends RocketPart<?, ?>> @NotNull RocketPartRenderer getRenderer(ResourceKey<T> part) {
        RocketPartRenderer renderer = this.renderers.get(part);
        if (renderer != null) return renderer;
        // Datapack parts have no code to register a renderer; their config may name a model instead.
        Optional<ResourceLocation> model = this.configuredModel(part);
        if (model.isPresent()) {
            return this.modelRenderers.computeIfAbsent(model.get(), BakedModelRocketPartRenderer::new);
        }
        if (this.warned.add(part)) {
            Constant.LOGGER.warn("No rocket part renderer registered for {}; it will be invisible. Register one with RocketPartRendererRegistry.INSTANCE.register.", part.location());
        }
        return EmptyRocketPartRenderer.INSTANCE;
    }

    /** The {@code model} named by the part's config, read from the client's synced part registry. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private Optional<ResourceLocation> configuredModel(ResourceKey<? extends RocketPart<?, ?>> part) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return Optional.empty();
        return level.registryAccess().registry((ResourceKey) part.registryKey())
                .map(registry -> ((Registry<RocketPart<?, ?>>) registry).get((ResourceKey) part))
                .flatMap(value -> ((RocketPart<?, ?>) value).model());
    }
}
