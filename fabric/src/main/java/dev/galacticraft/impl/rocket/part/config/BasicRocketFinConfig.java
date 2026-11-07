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

package dev.galacticraft.impl.rocket.part.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.galacticraft.api.rocket.part.RocketPartLayout;
import dev.galacticraft.api.rocket.part.config.RocketFinConfig;
import dev.galacticraft.api.rocket.recipe.RocketPartRecipe;
import dev.galacticraft.api.rocket.travelpredicate.ConfiguredTravelPredicate;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public record BasicRocketFinConfig(ConfiguredTravelPredicate<?, ?> predicate, boolean canManeuver, RocketPartLayout layout, boolean paintable, @Nullable RocketPartRecipe<?, ?> recipe, Optional<ResourceLocation> model) implements RocketFinConfig {
    public static final Codec<BasicRocketFinConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ConfiguredTravelPredicate.DIRECT_CODEC.fieldOf("predicate").forGetter(BasicRocketFinConfig::predicate),
            Codec.BOOL.fieldOf("can_maneuver").forGetter(BasicRocketFinConfig::canManeuver),
            RocketPartLayout.CODEC.optionalFieldOf("layout", RocketPartLayout.DEFAULT).forGetter(BasicRocketFinConfig::layout),
            Codec.BOOL.optionalFieldOf("paintable", false).forGetter(BasicRocketFinConfig::paintable),
            RocketPartRecipe.DIRECT_CODEC.optionalFieldOf("recipe").forGetter(config -> Optional.ofNullable(config.recipe)),
            ResourceLocation.CODEC.optionalFieldOf("model").forGetter(BasicRocketFinConfig::model)
    ).apply(instance, (ConfiguredTravelPredicate<?, ?> predicate, Boolean canManeuver, RocketPartLayout layout, Boolean paintable, Optional<RocketPartRecipe<?, ?>> recipe, Optional<ResourceLocation> model) ->
            new BasicRocketFinConfig(predicate, canManeuver, layout, paintable, recipe.orElse(null), model)));

    public BasicRocketFinConfig(ConfiguredTravelPredicate<?, ?> predicate, boolean canManeuver, @Nullable RocketPartRecipe<?, ?> recipe) {
        this(predicate, canManeuver, RocketPartLayout.DEFAULT, false, recipe);
    }

    public BasicRocketFinConfig(ConfiguredTravelPredicate<?, ?> predicate, boolean canManeuver, RocketPartLayout layout, boolean paintable, @Nullable RocketPartRecipe<?, ?> recipe) {
        this(predicate, canManeuver, layout, paintable, recipe, Optional.empty());
    }
}
