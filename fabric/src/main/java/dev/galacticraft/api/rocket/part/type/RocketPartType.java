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

package dev.galacticraft.api.rocket.part.type;

import com.mojang.serialization.MapCodec;
import dev.galacticraft.api.rocket.entity.Rocket;
import dev.galacticraft.api.rocket.part.RocketPart;
import dev.galacticraft.api.rocket.part.RocketPartLayout;
import dev.galacticraft.api.rocket.part.config.RocketPartConfig;
import dev.galacticraft.api.rocket.recipe.RocketPartRecipe;
import dev.galacticraft.api.rocket.travelpredicate.ConfiguredTravelPredicate;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Base rocket part interface. Extend {@link RocketBodyType}, {@link RocketBoosterType},
 * {@link RocketEngineType}, {@link RocketConeType}, {@link RocketFinType}, or {@link RocketUpgradeType}.
 */
public sealed interface RocketPartType<C extends RocketPartConfig> permits RocketBodyType, RocketBoosterType, RocketEngineType, RocketConeType, RocketFinType, RocketUpgradeType {

    @NotNull RocketPart<C, ? extends RocketPartType<C>> configure(@NotNull C config);

    /**
     * Called every tick when this part is applied to a placed rocket.
     * The rocket may not have launched yet.
     *
     * @param rocket the rocket that this part is a part of.
     */
    void tick(@NotNull Rocket rocket, @NotNull C config);

    @NotNull MapCodec<? extends RocketPart<C, ? extends RocketPartType<C>>> codec();

    @NotNull ConfiguredTravelPredicate<?, ?> travelPredicate(@NotNull C config);

    @Nullable RocketPartRecipe<?, ?> getRecipe(C config);

    /**
     * Returns how much room this part takes up on the assembled rocket.
     *
     * @see RocketPartLayout
     */
    default @NotNull RocketPartLayout layout(@NotNull C config) {
        return RocketPartLayout.DEFAULT;
    }

    /**
     * Returns whether the rocket paint menu offers this part. Only return {@code true} when this
     * part's renderer applies the rocket's paint colour.
     */
    default boolean isPaintable(@NotNull C config) {
        return false;
    }

    /**
     * Returns a model to draw this part with when no renderer is registered for it in code, for
     * example {@code mypack:models/misc/ion_engine.json}. This is how a datapack part, with its
     * model in a resource pack, becomes visible without any code.
     */
    default @NotNull Optional<ResourceLocation> model(@NotNull C config) {
        return Optional.empty();
    }
}
