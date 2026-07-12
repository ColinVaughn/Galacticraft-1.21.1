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

package dev.galacticraft.mod.content;

import dev.galacticraft.api.data.RocketPartRecipeBuilder;
import dev.galacticraft.api.registry.RocketRegistries;
import dev.galacticraft.api.rocket.part.*;
import dev.galacticraft.api.rocket.travelpredicate.TravelPredicateType;
import dev.galacticraft.impl.rocket.part.config.*;
import dev.galacticraft.impl.rocket.part.type.*;
import dev.galacticraft.impl.rocket.travelpredicate.config.AccessWeightTravelPredicateConfig;
import dev.galacticraft.impl.rocket.travelpredicate.type.AccessWeightTravelPredicateType;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.content.item.GCItems;
import dev.galacticraft.mod.content.rocket.part.config.ExplosiveUpgradeConfig;
import dev.galacticraft.mod.content.rocket.part.config.StorageUpgradeConfig;
import dev.galacticraft.mod.content.rocket.part.type.ExplosiveUpgradeType;
import dev.galacticraft.mod.content.rocket.part.type.StorageUpgradeType;
import dev.galacticraft.machinelib.api.transfer.FluidConstants;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public class GCRocketParts {
    public static final ResourceKey<RocketCone<?, ?>> TIER_1_CONE = cone("tier_1");
    public static final ResourceKey<RocketBody<?, ?>> TIER_1_BODY = body("tier_1");
    public static final ResourceKey<RocketFin<?, ?>> TIER_1_FIN = fin("tier_1");
    public static final ResourceKey<RocketBooster<?, ?>> TIER_1_BOOSTER = booster("tier_1");
    public static final ResourceKey<RocketEngine<?, ?>> TIER_1_ENGINE = engine("tier_1");

    public static final ResourceKey<RocketCone<?, ?>> TIER_2_CONE = cone("tier_2");
    public static final ResourceKey<RocketBody<?, ?>> TIER_2_BODY = body("tier_2");
    public static final ResourceKey<RocketFin<?, ?>> TIER_2_FIN = fin("tier_2");
    public static final ResourceKey<RocketEngine<?, ?>> TIER_2_ENGINE = engine("tier_2");

    public static final ResourceKey<RocketCone<?, ?>> TIER_3_CONE = cone("tier_3");
    public static final ResourceKey<RocketBody<?, ?>> TIER_3_BODY = body("tier_3");
    public static final ResourceKey<RocketFin<?, ?>> TIER_3_FIN = fin("tier_3");
    public static final ResourceKey<RocketEngine<?, ?>> TIER_3_ENGINE = engine("tier_3");

    public static final ResourceKey<RocketCone<?, ?>> ADVANCED_CONE = cone("advanced_cone");
    public static final ResourceKey<RocketCone<?, ?>> SLOPED_CONE = cone("sloped_cone");

    public static final ResourceKey<RocketBooster<?, ?>> TIER_2_BOOSTER = booster("tier_2");
    public static final ResourceKey<RocketUpgrade<?, ?>> STORAGE_UPGRADE = upgrade("storage");

    public static final ResourceKey<RocketUpgrade<?, ?>> EXPLOSIVE_UPGRADE = upgrade("explosive");

    public static void bootstrapCone(BootstrapContext<RocketCone<?, ?>> context) {
        context.register(TIER_1_CONE,
                BasicRocketConeType.INSTANCE.configure(
                        new BasicRocketConeConfig(
                                AccessWeightTravelPredicateType.INSTANCE.configure(new AccessWeightTravelPredicateConfig(1, TravelPredicateType.Result.PASS)),
                                RocketPartRecipeBuilder.create()
                                        .define('T', Ingredient.of(Items.REDSTONE_TORCH))
                                        .define('D', Ingredient.of(GCItems.TIER_1_HEAVY_DUTY_PLATE))
                                        .center("T")
                                        .center("D")
                                        .center("DD")
                                        .build()
                        )
                )
        );
        context.register(SLOPED_CONE,
                BasicRocketConeType.INSTANCE.configure(
                        new BasicRocketConeConfig(
                                AccessWeightTravelPredicateType.INSTANCE.configure(new AccessWeightTravelPredicateConfig(1, TravelPredicateType.Result.PASS)),
                                RocketPartRecipeBuilder.create()
                                        .define('T', Ingredient.of(Items.REDSTONE_TORCH))
                                        .define('D', Ingredient.of(GCItems.TIER_1_HEAVY_DUTY_PLATE))
                                        .center("T")
                                        .center("DD")
                                        .center("DD")
                                        .build()
                        )
                )
        );
          context.register(ADVANCED_CONE,
                  BasicRocketConeType.INSTANCE.configure(
                          new BasicRocketConeConfig(
                                  AccessWeightTravelPredicateType.INSTANCE.configure(new AccessWeightTravelPredicateConfig(1, TravelPredicateType.Result.PASS)),
                                  RocketPartRecipeBuilder.create()
                                          .define('T', Ingredient.of(Items.REDSTONE_TORCH))
                                          .define('D', Ingredient.of(GCItems.TIER_2_HEAVY_DUTY_PLATE))
                                          .center("T")
                                          .center("D")
                                          .center("DD")
                                          .center("DD")
                                          .build()
                        )
                )
        );
        context.register(TIER_2_CONE,
                BasicRocketConeType.INSTANCE.configure(
                        new BasicRocketConeConfig(
                                AccessWeightTravelPredicateType.INSTANCE.configure(new AccessWeightTravelPredicateConfig(2, TravelPredicateType.Result.PASS)),
                                RocketPartRecipeBuilder.create()
                                        .define('T', Ingredient.of(Items.REDSTONE_TORCH))
                                        .define('D', Ingredient.of(GCItems.TIER_2_HEAVY_DUTY_PLATE))
                                        .center("T")
                                        .center("D")
                                        .center("DD")
                                        .build()
                        )
                )
        );
        context.register(TIER_3_CONE,
                BasicRocketConeType.INSTANCE.configure(
                        new BasicRocketConeConfig(
                                AccessWeightTravelPredicateType.INSTANCE.configure(new AccessWeightTravelPredicateConfig(3, TravelPredicateType.Result.PASS)),
                                RocketPartRecipeBuilder.create()
                                        .define('T', Ingredient.of(Items.REDSTONE_TORCH))
                                        .define('D', Ingredient.of(GCItems.TIER_3_HEAVY_DUTY_PLATE))
                                        .center("T")
                                        .center("D")
                                        .center("DD")
                                        .build()
                        )
                )
        );
    }

    public static void bootstrapBody(BootstrapContext<RocketBody<?, ?>> context) {
        context.register(TIER_1_BODY,
                BasicRocketBodyType.INSTANCE.configure(new BasicRocketBodyConfig(
                        AccessWeightTravelPredicateType.INSTANCE.configure(new AccessWeightTravelPredicateConfig(1, TravelPredicateType.Result.PASS)),
                        1,
                        RocketPartRecipeBuilder.create()
                                .define('D', Ingredient.of(GCItems.TIER_1_HEAVY_DUTY_PLATE))
                                .left("D")
                                .left("D")
                                .left("D")
                                .left("D")
                                .right("D")
                                .right("D")
                                .right("D")
                                .right("D")
                                .build()
                ))
        );
        context.register(TIER_2_BODY,
                BasicRocketBodyType.INSTANCE.configure(new BasicRocketBodyConfig(
                        AccessWeightTravelPredicateType.INSTANCE.configure(new AccessWeightTravelPredicateConfig(2, TravelPredicateType.Result.PASS)),
                        1,
                        RocketPartRecipeBuilder.create()
                                .define('D', Ingredient.of(GCItems.TIER_2_HEAVY_DUTY_PLATE))
                                .left("D")
                                .left("D")
                                .left("D")
                                .left("D")
                                .right("D")
                                .right("D")
                                .right("D")
                                .right("D")
                                .build()
                ))
        );
        context.register(TIER_3_BODY,
                BasicRocketBodyType.INSTANCE.configure(new BasicRocketBodyConfig(
                        AccessWeightTravelPredicateType.INSTANCE.configure(new AccessWeightTravelPredicateConfig(3, TravelPredicateType.Result.PASS)),
                        1,
                        RocketPartRecipeBuilder.create()
                                .define('D', Ingredient.of(GCItems.TIER_3_HEAVY_DUTY_PLATE))
                                .left("D")
                                .left("D")
                                .left("D")
                                .left("D")
                                .right("D")
                                .right("D")
                                .right("D")
                                .right("D")
                                .build()
                ))
        );
    }

    public static void bootstrapFin(BootstrapContext<RocketFin<?, ?>> context) {
        context.register(TIER_1_FIN,
                BasicRocketFinType.INSTANCE.configure(
                        new BasicRocketFinConfig(
                                AccessWeightTravelPredicateType.INSTANCE.configure(
                                        new AccessWeightTravelPredicateConfig(1, TravelPredicateType.Result.PASS)
                                ),
                                false,
                                RocketPartRecipeBuilder.create()
                                        .define('F', Ingredient.of(GCItems.ROCKET_FIN))
                                        .left("F")
                                        .left("F")
                                        .right("F")
                                        .right("F")
                                        .build()
                        )
                )
        );
        context.register(TIER_2_FIN,
                BasicRocketFinType.INSTANCE.configure(
                        new BasicRocketFinConfig(
                                AccessWeightTravelPredicateType.INSTANCE.configure(
                                        new AccessWeightTravelPredicateConfig(2, TravelPredicateType.Result.PASS)
                                ),
                                false,
                                RocketPartRecipeBuilder.create()
                                        .define('F', Ingredient.of(GCItems.ROCKET_FIN))
                                        .left("F")
                                        .left("F")
                                        .right("F")
                                        .right("F")
                                        .build()
                        )
                )
        );
        context.register(TIER_3_FIN,
                BasicRocketFinType.INSTANCE.configure(
                        new BasicRocketFinConfig(
                                AccessWeightTravelPredicateType.INSTANCE.configure(
                                        new AccessWeightTravelPredicateConfig(3, TravelPredicateType.Result.PASS)
                                ),
                                false,
                                RocketPartRecipeBuilder.create()
                                        .define('F', Ingredient.of(GCItems.HEAVY_ROCKET_FIN))
                                        .left("F")
                                        .left("F")
                                        .right("F")
                                        .right("F")
                                        .build()
                        )
                )
        );
    }

    public static void bootstrapBooster(BootstrapContext<RocketBooster<?, ?>> context) {
        context.register(TIER_1_BOOSTER,
                BasicRocketBoosterType.INSTANCE.configure(
                          new BasicRocketBoosterConfig(
                                  AccessWeightTravelPredicateType.INSTANCE.configure(
                                          new AccessWeightTravelPredicateConfig(1, TravelPredicateType.Result.PASS)
                                  ),
                                  1.0,
                                  0.02,
                                  FluidConstants.NUGGET,
                                  RocketPartRecipeBuilder.create()
                                          .define('B', Ingredient.of(GCItems.ROCKET_BOOSTER))
                                          .left("B")
                                          .right("B")
                                          .build()
                          )
                  )
          );
        context.register(TIER_2_BOOSTER,
                BasicRocketBoosterType.INSTANCE.configure(
                        new BasicRocketBoosterConfig(
                                AccessWeightTravelPredicateType.INSTANCE.configure(
                                        new AccessWeightTravelPredicateConfig(2, TravelPredicateType.Result.PASS)
                                ),
                                1.25,
                                0.03,
                                FluidConstants.NUGGET * 2,
                                RocketPartRecipeBuilder.create()
                                        .define('B', Ingredient.of(GCItems.ROCKET_BOOSTER))
                                        .define('P', Ingredient.of(GCItems.TIER_2_HEAVY_DUTY_PLATE))
                                        .left("P")
                                        .left("B")
                                        .right("P")
                                        .right("B")
                                        .build()
                        )
                )
        );
    }

    public static void bootstrapEngine(BootstrapContext<RocketEngine<?, ?>> context) {
        context.register(TIER_1_ENGINE,
                BasicRocketEngineType.INSTANCE.configure(
                        new BasicRocketEngineConfig(
                                AccessWeightTravelPredicateType.INSTANCE.configure(
                                        new AccessWeightTravelPredicateConfig(1, TravelPredicateType.Result.PASS)
                                ),
                                FluidConstants.BUCKET * 16,
                                RocketPartRecipeBuilder.create()
                                        .define('E', Ingredient.of(GCItems.ROCKET_ENGINE))
                                        .center("E")
                                        .build()
                        )
                )
        );
        context.register(TIER_2_ENGINE,
                BasicRocketEngineType.INSTANCE.configure(
                        new BasicRocketEngineConfig(
                                AccessWeightTravelPredicateType.INSTANCE.configure(
                                        new AccessWeightTravelPredicateConfig(2, TravelPredicateType.Result.PASS)
                                ),
                                FluidConstants.BUCKET * 24,
                                RocketPartRecipeBuilder.create()
                                        .define('E', Ingredient.of(GCItems.ROCKET_ENGINE))
                                        .center("E")
                                        .build()
                        )
                )
        );
        context.register(TIER_3_ENGINE,
                BasicRocketEngineType.INSTANCE.configure(
                        new BasicRocketEngineConfig(
                                AccessWeightTravelPredicateType.INSTANCE.configure(
                                        new AccessWeightTravelPredicateConfig(3, TravelPredicateType.Result.PASS)
                                ),
                                FluidConstants.BUCKET * 32,
                                RocketPartRecipeBuilder.create()
                                        .define('E', Ingredient.of(GCItems.HEAVY_ROCKET_ENGINE))
                                        .center("E")
                                        .build()
                        )
                )
        );
    }

    public static void bootstrapUpgrade(BootstrapContext<RocketUpgrade<?, ?>> context) {
        context.register(STORAGE_UPGRADE, RocketUpgrade.create(
                new StorageUpgradeConfig(
                        1,
                        RocketPartRecipeBuilder.create()
                                .define('C', Ingredient.of(Items.CHEST))
                                .center("C")
                                .build()
                ),
                StorageUpgradeType.INSTANCE
        ));

        context.register(EXPLOSIVE_UPGRADE, RocketUpgrade.create(
                new ExplosiveUpgradeConfig(
                        1.0f,
                        80,
                        RocketPartRecipeBuilder.create()
                                .define('T', Ingredient.of(Items.TNT))
                                .center("T")
                                .build()
                ),
                ExplosiveUpgradeType.INSTANCE
          ));
    }

    public static ResourceLocation recipeId(ResourceKey<?> part) {
        return part.location().withPath(part.registry().getPath() + "/" + part.location().getPath());
    }

    @Contract(pure = true)
    private static @NotNull ResourceKey<RocketCone<?, ?>> cone(@NotNull String id) {
        return Constant.key(RocketRegistries.ROCKET_CONE, id);
    }

    @Contract(pure = true)
    private static @NotNull ResourceKey<RocketBody<?, ?>> body(@NotNull String id) {
        return Constant.key(RocketRegistries.ROCKET_BODY, id);
    }

    @Contract(pure = true)
    private static @NotNull ResourceKey<RocketFin<?, ?>> fin(@NotNull String id) {
        return Constant.key(RocketRegistries.ROCKET_FIN, id);
    }

    @Contract(pure = true)
    private static @NotNull ResourceKey<RocketBooster<?, ?>> booster(@NotNull String id) {
        return Constant.key(RocketRegistries.ROCKET_BOOSTER, id);
    }

    @Contract(pure = true)
    private static @NotNull ResourceKey<RocketEngine<?, ?>> engine(@NotNull String id) {
        return Constant.key(RocketRegistries.ROCKET_ENGINE, id);
    }

    @Contract(pure = true)
    private static @NotNull ResourceKey<RocketUpgrade<?, ?>> upgrade(@NotNull String id) {
        return Constant.key(RocketRegistries.ROCKET_UPGRADE, id);
    }
}
