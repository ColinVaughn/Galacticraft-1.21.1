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

package dev.galacticraft.test.addon;

import dev.galacticraft.api.component.GCDataComponents;
import dev.galacticraft.api.event.ResearchEvents;
import dev.galacticraft.api.event.RocketEvents;
import dev.galacticraft.api.registry.BuiltInRocketRegistries;
import dev.galacticraft.api.registry.RocketRegistries;
import dev.galacticraft.api.rocket.RocketData;
import dev.galacticraft.api.rocket.part.RocketBody;
import dev.galacticraft.api.rocket.part.RocketCone;
import dev.galacticraft.api.rocket.part.RocketEngine;
import dev.galacticraft.api.rocket.part.RocketFin;
import dev.galacticraft.api.rocket.part.RocketPartTypes;
import dev.galacticraft.api.rocket.part.RocketUpgrade;
import dev.galacticraft.mod.content.item.RocketModuleItem;
import dev.galacticraft.mod.content.item.SchematicItem;
import net.neoforged.fml.common.Mod;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.EitherHolder;
import net.minecraft.world.item.Item;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Public API example with datapack-defined tier 4 parts, a schematic, an engine module,
 * a fuel-pod upgrade, and launch/research listeners. Checked on the native dedicated server with {@code -PaddonTest}.
 */
@Mod("galacticraft_addon_test")
public final class ExampleAddon {
    public static final String MOD_ID = "galacticraft-test";

    public static final ResourceKey<RocketCone<?, ?>> TIER_4_CONE = ResourceKey.create(RocketRegistries.ROCKET_CONE, id("tier_4"));
    public static final ResourceKey<RocketBody<?, ?>> TIER_4_BODY = ResourceKey.create(RocketRegistries.ROCKET_BODY, id("tier_4"));
    public static final ResourceKey<RocketFin<?, ?>> TIER_4_FIN = ResourceKey.create(RocketRegistries.ROCKET_FIN, id("tier_4"));
    public static final ResourceKey<RocketEngine<?, ?>> TIER_4_ENGINE = ResourceKey.create(RocketRegistries.ROCKET_ENGINE, id("tier_4"));
    public static final ResourceKey<RocketUpgrade<?, ?>> FUEL_POD = ResourceKey.create(RocketRegistries.ROCKET_UPGRADE, id("fuel_pod"));

    /** Rockets that left the pad, and research gained, as seen by the listeners below. */
    public static final List<Object> LAUNCHED = new CopyOnWriteArrayList<>();
    public static final List<ResourceLocation> RESEARCHED = new CopyOnWriteArrayList<>();

    private boolean passed;

    public static Item tier4Schematic;
    public static RocketModuleItem tier4EngineModule;

    public ExampleAddon(IEventBus modBus) {
        var types = DeferredRegister.create(BuiltInRocketRegistries.ROCKET_UPGRADE_TYPE.key(), MOD_ID);
        types.register("fuel_pod", () -> FuelPodUpgradeType.INSTANCE);
        types.register(modBus);
        var items = DeferredRegister.create(BuiltInRegistries.ITEM, MOD_ID);
        items.register("tier_4_rocket_schematic", () -> tier4Schematic = new SchematicItem(new Item.Properties()
                .stacksTo(1).component(GCDataComponents.ROCKET_PART_UNLOCKS,
                        List.of(TIER_4_CONE, TIER_4_BODY, TIER_4_FIN, TIER_4_ENGINE))));
        items.register("tier_4_rocket_engine", () -> tier4EngineModule = new RocketModuleItem(RocketPartTypes.ENGINE, 4,
                new RocketData(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                        Optional.of(new EitherHolder<>(TIER_4_ENGINE)), Optional.empty(), Optional.empty(), -1)));
        items.register(modBus);
        modBus.addListener((net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent event) -> event.enqueueWork(() -> {
            var actual = net.minecraft.core.registries.BuiltInRegistries.REGISTRY.get(RocketRegistries.ROCKET_UPGRADE_TYPE.location());
            if (actual != BuiltInRocketRegistries.ROCKET_UPGRADE_TYPE
                    || !BuiltInRocketRegistries.ROCKET_UPGRADE_TYPE.containsKey(id("fuel_pod"))
                    || tier4EngineModule == null || tier4Schematic == null)
                throw new AssertionError("Native addon registration did not reach the runtime registries");
        }));
        if (Boolean.getBoolean("galacticraft.addon-test")) {
            NeoForge.EVENT_BUS.addListener(this::check);
            // The dev transformer owns non-daemon pools; exit only after the server has saved/stopped.
            NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStoppedEvent event) ->
                    Thread.ofPlatform().name("addon-test-exit").start(() -> System.exit(passed ? 0 : 1)));
        }
        RocketEvents.LAUNCH.register(LAUNCHED::add);
        ResearchEvents.UNLOCKED.register((player, research) -> RESEARCHED.addAll(research));
    }

    private void check(ServerStartedEvent event) {
        var server = event.getServer();
        var level = server.overworld();
        try {
            var access = server.registryAccess();
            if (access.registryOrThrow(RocketRegistries.ROCKET_ENGINE).get(TIER_4_ENGINE) == null)
                throw new AssertionError("Native configured engine missing");
            if (dev.galacticraft.mod.machine.workbench.WorkbenchPages.recipe(level, id("rocket_module/rocket_engine/tier_4")) == null)
                throw new AssertionError("Native embedded recipe missing");
            if (level.getRecipeManager().byKey(id("tier_4_engine_module")).isEmpty())
                throw new AssertionError("Native acquisition recipe missing");
            if (dev.galacticraft.mod.machine.workbench.ResearchSources.describe(server)
                    .get(ResourceLocation.fromNamespaceAndPath("examplepack", "rocket_engine/ion")) == null)
                throw new AssertionError("Native research source missing");
            var prime = server.getLevel(ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, id("andromeda_prime")));
            if (prime == null || prime.galacticraft$getCelestialBody() == null
                    || dev.galacticraft.mod.world.dimension.SpaceTravel.fromLevel(prime) == null)
                throw new AssertionError("Native addon dimension/physical flight missing");
            var rocket = new dev.galacticraft.mod.content.entity.vehicle.RocketEntity(dev.galacticraft.mod.content.GCEntityTypes.ROCKET, level);
            var base = dev.galacticraft.mod.content.rocket.RocketPrefabs.TIER_1;
            rocket.setData(new RocketData(base.cone(), base.body(), base.fin(), base.booster(), base.engine(),
                    Optional.of(new EitherHolder<>(FUEL_POD)), Optional.of(new FuelPodUpgradeType.Pods(2)), -1));
            if (rocket.getFuelTankCapacity() < 162000) throw new AssertionError("Native upgrade did not expand capacity");
            dev.galacticraft.mod.api.data.recipe.GCShapedRecipeBuilder.compressing(tier4EngineModule)
                    .pattern("I").define('I', net.minecraft.world.item.Items.IRON_INGOT)
                    .unlockedBy("has_iron", net.minecraft.advancements.critereon.InventoryChangeTrigger.TriggerInstance.hasItems(net.minecraft.world.item.Items.IRON_INGOT))
                    .save(new net.minecraft.data.recipes.RecipeOutput() {
                        @Override public net.minecraft.advancements.Advancement.Builder advancement() {
                            return net.minecraft.advancements.Advancement.Builder.recipeAdvancement();
                        }
                        @Override public void accept(ResourceLocation recipeId, net.minecraft.world.item.crafting.Recipe<?> recipe,
                                net.minecraft.advancements.AdvancementHolder advancement, net.neoforged.neoforge.common.conditions.ICondition... conditions) {
                            accept(recipeId, recipe, advancement);
                        }
                        @Override public void accept(ResourceLocation recipeId, net.minecraft.world.item.crafting.Recipe<?> recipe,
                                net.minecraft.advancements.AdvancementHolder advancement) {
                            if (!recipeId.equals(id("compressing/native_probe")) || !advancement.value().rewards().recipes().equals(List.of(recipeId)))
                                throw new AssertionError("Native datagen helper lost its namespace/prefix");
                        }
                    }, "galacticraft-test:native_probe");
            passed = true;
            dev.galacticraft.mod.Constant.LOGGER.info("NATIVE ADDON CHECK PASSED: registries, dimension, recipes, sources, capacity");
        } finally {
            server.halt(false);
        }
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}