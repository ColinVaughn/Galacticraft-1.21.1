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

package dev.galacticraft.mod.client.render.entity.feature.spacegear;

import dev.galacticraft.mod.client.render.entity.feature.EvolvedIllagerMaskRenderLayer;
import dev.galacticraft.mod.client.render.entity.feature.EvolvedSpiderMaskRenderLayer;
import dev.galacticraft.mod.client.render.entity.feature.EvolvedWitchMaskRenderLayer;
import dev.galacticraft.mod.client.render.entity.feature.OxygenMaskRenderLayer;
import dev.galacticraft.mod.client.render.entity.feature.OxygenTanksRenderLayer;
import dev.galacticraft.mod.client.render.entity.feature.ParrotOxygenGearRenderLayer;
import dev.galacticraft.mod.client.render.entity.feature.PetOxygenMaskRenderLayer;
import dev.galacticraft.mod.client.render.entity.feature.PetOxygenTanksRenderLayer;
import dev.galacticraft.mod.mixin.client.LivingEntityRendererAccessor;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Prevents dedicated, fitted, and hitbox gear renderers from drawing the same entity twice.
 * Fitted paths mark the entity; the fallback checks that mark after the main renderer.
 * A single slot suffices because entity rendering is single-threaded.
 */
public final class SpaceGearRenderState {
    private static final Map<EntityRenderer<?>, Boolean> HAND_TUNED = Collections.synchronizedMap(new WeakHashMap<>());
    private static @Nullable Entity drawnFor;

    private SpaceGearRenderState() {}

    public static void markDrawn(Entity entity) {
        drawnFor = entity;
    }

    /** Returns and clears the fitted-gear mark for {@code entity}. */
    public static boolean takeDrawn(Entity entity) {
        boolean drawn = drawnFor == entity;
        drawnFor = null;
        return drawn;
    }

    /** Returns whether this entity has synced space gear to render. */
    public static boolean wearsGear(Entity entity) {
        return entity instanceof LivingEntity living && (living.galacticraft$hasMask() || living.galacticraft$hasGear());
    }

    /** Returns whether this renderer has dedicated gear layers, which fitted and fallback paths skip. */
    public static boolean hasHandTunedGear(EntityRenderer<?> renderer) {
        if (!(renderer instanceof LivingEntityRenderer<?, ?> living)) return false;
        return HAND_TUNED.computeIfAbsent(renderer, ignored -> {
            for (RenderLayer<?, ?> layer : ((LivingEntityRendererAccessor) living).galacticraft$getLayers()) {
                if (layer instanceof OxygenMaskRenderLayer<?, ?> || layer instanceof OxygenTanksRenderLayer<?, ?>
                        || layer instanceof PetOxygenMaskRenderLayer<?, ?> || layer instanceof PetOxygenTanksRenderLayer<?, ?>
                        || layer instanceof ParrotOxygenGearRenderLayer<?, ?> || layer instanceof EvolvedIllagerMaskRenderLayer<?, ?>
                        || layer instanceof EvolvedSpiderMaskRenderLayer<?, ?> || layer instanceof EvolvedWitchMaskRenderLayer<?, ?>) {
                    return true;
                }
            }
            return false;
        });
    }
}
