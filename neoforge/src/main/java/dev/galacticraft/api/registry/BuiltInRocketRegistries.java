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

import dev.galacticraft.api.rocket.part.type.*;
import dev.galacticraft.api.rocket.recipe.type.RocketPartRecipeType;
import dev.galacticraft.api.rocket.travelpredicate.TravelPredicateType;
import dev.galacticraft.impl.rocket.part.type.*;
import dev.galacticraft.impl.rocket.recipe.type.CenteredPatternedRocketPartRecipeType;
import dev.galacticraft.impl.rocket.recipe.type.PatternedRocketPartRecipeType;
import dev.galacticraft.impl.rocket.travelpredicate.type.*;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.content.rocket.part.type.ExplosiveUpgradeType;
import dev.galacticraft.mod.content.rocket.part.type.StorageUpgradeType;
import net.minecraft.core.Registry;
import net.minecraft.core.WritableRegistry;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class BuiltInRocketRegistries {
    private static final DeferredRegister<TravelPredicateType<?>> TRAVEL = create(RocketRegistries.TRAVEL_PREDICATE_TYPE);
    private static final DeferredRegister<RocketConeType<?>> CONES = create(RocketRegistries.ROCKET_CONE_TYPE);
    private static final DeferredRegister<RocketBodyType<?>> BODIES = create(RocketRegistries.ROCKET_BODY_TYPE);
    private static final DeferredRegister<RocketFinType<?>> FINS = create(RocketRegistries.ROCKET_FIN_TYPE);
    private static final DeferredRegister<RocketBoosterType<?>> BOOSTERS = create(RocketRegistries.ROCKET_BOOSTER_TYPE);
    private static final DeferredRegister<RocketEngineType<?>> ENGINES = create(RocketRegistries.ROCKET_ENGINE_TYPE);
    private static final DeferredRegister<RocketUpgradeType<?>> UPGRADES = create(RocketRegistries.ROCKET_UPGRADE_TYPE);
    private static final DeferredRegister<RocketPartRecipeType<?>> RECIPES = create(RocketRegistries.ROCKET_PART_RECIPE_TYPE);

    public static final WritableRegistry<TravelPredicateType<?>> TRAVEL_PREDICATE_TYPE = registry(TRAVEL, true);
    public static final WritableRegistry<RocketConeType<?>> ROCKET_CONE_TYPE = registry(CONES, false);
    public static final WritableRegistry<RocketBodyType<?>> ROCKET_BODY_TYPE = registry(BODIES, false);
    public static final WritableRegistry<RocketFinType<?>> ROCKET_FIN_TYPE = registry(FINS, false);
    public static final WritableRegistry<RocketBoosterType<?>> ROCKET_BOOSTER_TYPE = registry(BOOSTERS, false);
    public static final WritableRegistry<RocketEngineType<?>> ROCKET_ENGINE_TYPE = registry(ENGINES, false);
    public static final WritableRegistry<RocketUpgradeType<?>> ROCKET_UPGRADE_TYPE = registry(UPGRADES, false);
    public static final WritableRegistry<RocketPartRecipeType<?>> ROCKET_PART_RECIPE_TYPE = registry(RECIPES, false);

    private BuiltInRocketRegistries() {
    }

    private static <T> DeferredRegister<T> create(net.minecraft.resources.ResourceKey<? extends Registry<T>> key) {
        return DeferredRegister.create(key, Constant.MOD_ID);
    }

    @SuppressWarnings("unchecked")
    private static <T> WritableRegistry<T> registry(DeferredRegister<T> deferred, boolean intrusive) {
        return (WritableRegistry<T>) deferred.makeRegistry(builder -> {
            if (intrusive) builder.withIntrusiveHolders();
        });
    }

    public static void register(IEventBus bus) {
        TRAVEL.register(bus); CONES.register(bus); BODIES.register(bus); FINS.register(bus);
        BOOSTERS.register(bus); ENGINES.register(bus); UPGRADES.register(bus); RECIPES.register(bus);
    }

    public static void initialize() {
    }

    static {
        Registry.register(TRAVEL_PREDICATE_TYPE, Constant.id("default"), DefaultTravelPredicateType.INSTANCE);
        Registry.register(TRAVEL_PREDICATE_TYPE, Constant.id("access_weight"), AccessWeightTravelPredicateType.INSTANCE);
        Registry.register(TRAVEL_PREDICATE_TYPE, Constant.id("constant"), ConstantTravelPredicateType.INSTANCE);
        Registry.register(TRAVEL_PREDICATE_TYPE, Constant.id("and"), AndTravelPredicateType.INSTANCE);
        Registry.register(TRAVEL_PREDICATE_TYPE, Constant.id("or"), OrTravelPredicateType.INSTANCE);
        Registry.register(ROCKET_CONE_TYPE, Constant.id("basic"), BasicRocketConeType.INSTANCE);
        Registry.register(ROCKET_BODY_TYPE, Constant.id("basic"), BasicRocketBodyType.INSTANCE);
        Registry.register(ROCKET_FIN_TYPE, Constant.id("basic"), BasicRocketFinType.INSTANCE);
        Registry.register(ROCKET_BOOSTER_TYPE, Constant.id("basic"), BasicRocketBoosterType.INSTANCE);
        Registry.register(ROCKET_ENGINE_TYPE, Constant.id("basic"), BasicRocketEngineType.INSTANCE);
        Registry.register(ROCKET_UPGRADE_TYPE, Constant.id("storage"), StorageUpgradeType.INSTANCE);
        Registry.register(ROCKET_UPGRADE_TYPE, Constant.id("explosive"), ExplosiveUpgradeType.INSTANCE);
        Registry.register(ROCKET_PART_RECIPE_TYPE, Constant.id("wrap_patterned"), PatternedRocketPartRecipeType.INSTANCE);
        Registry.register(ROCKET_PART_RECIPE_TYPE, Constant.id("centered_patterned"), CenteredPatternedRocketPartRecipeType.INSTANCE);
    }
}
