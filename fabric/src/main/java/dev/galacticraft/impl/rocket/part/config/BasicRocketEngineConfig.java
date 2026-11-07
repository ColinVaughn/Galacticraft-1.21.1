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
import dev.galacticraft.api.rocket.part.config.RocketEngineConfig;
import dev.galacticraft.api.rocket.recipe.RocketPartRecipe;
import dev.galacticraft.api.rocket.travelpredicate.ConfiguredTravelPredicate;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public record BasicRocketEngineConfig(ConfiguredTravelPredicate<?, ?> predicate, long fuelCapacity, long fuelUsage, RocketPartLayout layout, boolean paintable, @Nullable RocketPartRecipe<?, ?> recipe, Optional<ResourceLocation> model) implements RocketEngineConfig {
    /** Fuel burned per tick when a config leaves {@code fuel_usage} out: the stock tier 1 rate. */
    public static final long DEFAULT_FUEL_USAGE = 20;

    public static final Codec<BasicRocketEngineConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ConfiguredTravelPredicate.DIRECT_CODEC.fieldOf("predicate").forGetter(BasicRocketEngineConfig::predicate),
            Codec.LONG.fieldOf("fuel_capacity").forGetter(BasicRocketEngineConfig::fuelCapacity),
            Codec.LONG.optionalFieldOf("fuel_usage", DEFAULT_FUEL_USAGE).forGetter(BasicRocketEngineConfig::fuelUsage),
            RocketPartLayout.CODEC.optionalFieldOf("layout", RocketPartLayout.DEFAULT).forGetter(BasicRocketEngineConfig::layout),
            Codec.BOOL.optionalFieldOf("paintable", false).forGetter(BasicRocketEngineConfig::paintable),
            RocketPartRecipe.DIRECT_CODEC.optionalFieldOf("recipe").forGetter(config -> Optional.ofNullable(config.recipe)),
            ResourceLocation.CODEC.optionalFieldOf("model").forGetter(BasicRocketEngineConfig::model)
    ).apply(instance, (ConfiguredTravelPredicate<?, ?> predicate, Long fuelCapacity, Long fuelUsage, RocketPartLayout layout, Boolean paintable, Optional<RocketPartRecipe<?, ?>> recipe, Optional<ResourceLocation> model) ->
            new BasicRocketEngineConfig(predicate, fuelCapacity, fuelUsage, layout, paintable, recipe.orElse(null), model)));

    public BasicRocketEngineConfig(ConfiguredTravelPredicate<?, ?> predicate, long fuelCapacity, @Nullable RocketPartRecipe<?, ?> recipe) {
        this(predicate, fuelCapacity, DEFAULT_FUEL_USAGE, RocketPartLayout.DEFAULT, false, recipe);
    }

    public BasicRocketEngineConfig(ConfiguredTravelPredicate<?, ?> predicate, long fuelCapacity, long fuelUsage, RocketPartLayout layout, boolean paintable, @Nullable RocketPartRecipe<?, ?> recipe) {
        this(predicate, fuelCapacity, fuelUsage, layout, paintable, recipe, Optional.empty());
    }
}
