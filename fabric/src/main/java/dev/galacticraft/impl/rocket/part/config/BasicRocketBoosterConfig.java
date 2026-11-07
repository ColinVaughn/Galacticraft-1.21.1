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
import dev.galacticraft.api.rocket.part.config.RocketBoosterConfig;
import dev.galacticraft.api.rocket.recipe.RocketPartRecipe;
import dev.galacticraft.api.rocket.travelpredicate.ConfiguredTravelPredicate;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public record BasicRocketBoosterConfig(ConfiguredTravelPredicate<?, ?> predicate, double maxVelocity, double acceleration, long fuelUsage, RocketPartLayout layout, boolean paintable, @Nullable RocketPartRecipe<?, ?> recipe, Optional<ResourceLocation> model) implements RocketBoosterConfig {
    public static final Codec<BasicRocketBoosterConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ConfiguredTravelPredicate.DIRECT_CODEC.fieldOf("predicate").forGetter(BasicRocketBoosterConfig::predicate),
            Codec.DOUBLE.fieldOf("max_velocity").forGetter(BasicRocketBoosterConfig::maxVelocity),
            Codec.DOUBLE.fieldOf("acceleration").forGetter(BasicRocketBoosterConfig::acceleration),
            Codec.LONG.fieldOf("fuel_usage").forGetter(BasicRocketBoosterConfig::fuelUsage),
            RocketPartLayout.CODEC.optionalFieldOf("layout", RocketPartLayout.DEFAULT).forGetter(BasicRocketBoosterConfig::layout),
            Codec.BOOL.optionalFieldOf("paintable", false).forGetter(BasicRocketBoosterConfig::paintable),
            RocketPartRecipe.DIRECT_CODEC.optionalFieldOf("recipe").forGetter(config -> Optional.ofNullable(config.recipe)),
            ResourceLocation.CODEC.optionalFieldOf("model").forGetter(BasicRocketBoosterConfig::model)
    ).apply(instance, (ConfiguredTravelPredicate<?, ?> predicate, Double maxVelocity, Double acceleration, Long fuelUsage, RocketPartLayout layout, Boolean paintable, Optional<RocketPartRecipe<?, ?>> recipe, Optional<ResourceLocation> model) ->
            new BasicRocketBoosterConfig(predicate, maxVelocity, acceleration, fuelUsage, layout, paintable, recipe.orElse(null), model)));

    public BasicRocketBoosterConfig(ConfiguredTravelPredicate<?, ?> predicate, double maxVelocity, double acceleration, long fuelUsage, @Nullable RocketPartRecipe<?, ?> recipe) {
        this(predicate, maxVelocity, acceleration, fuelUsage, RocketPartLayout.DEFAULT, false, recipe);
    }

    public BasicRocketBoosterConfig(ConfiguredTravelPredicate<?, ?> predicate, double maxVelocity, double acceleration, long fuelUsage, RocketPartLayout layout, boolean paintable, @Nullable RocketPartRecipe<?, ?> recipe) {
        this(predicate, maxVelocity, acceleration, fuelUsage, layout, paintable, recipe, Optional.empty());
    }
}
