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

package dev.galacticraft.mod.gametest;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import dev.galacticraft.impl.universe.BuiltinObjects;
import dev.galacticraft.api.universe.galaxy.GalaxySystems;
import dev.galacticraft.api.universe.galaxy.Galaxy;
import com.mojang.serialization.JsonOps;
import dev.galacticraft.api.accessor.ServerResearchAccessor;
import dev.galacticraft.api.registry.AddonRegistries;
import dev.galacticraft.api.rocket.LaunchStage;
import dev.galacticraft.api.rocket.RocketData;
import dev.galacticraft.api.rocket.RocketUpgradeData;
import dev.galacticraft.api.rocket.part.RocketPartTypes;
import dev.galacticraft.api.universe.celestialbody.CelestialBody;
import dev.galacticraft.api.universe.celestialbody.CelestialBodyTags;
import dev.galacticraft.api.universe.celestialbody.SkyConfig;
import dev.galacticraft.dynamicdimensions.api.DynamicDimensionRegistry;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.Galacticraft;
import dev.galacticraft.mod.config.ConfigImpl;
import dev.galacticraft.mod.content.GCCelestialBodies;
import dev.galacticraft.mod.content.GCEntityTypes;
import dev.galacticraft.mod.content.entity.vehicle.RocketEntity;
import dev.galacticraft.mod.content.item.RocketModuleItem;
import dev.galacticraft.mod.content.rocket.RocketPrefabs;
import dev.galacticraft.mod.machine.workbench.RocketRefit;
import dev.galacticraft.mod.machine.workbench.WorkbenchPages;
import dev.galacticraft.mod.machine.workbench.WorkbenchUpgrades;
import dev.galacticraft.mod.screen.RocketPaintMenu;
import dev.galacticraft.test.addon.ExampleAddon;
import dev.galacticraft.test.addon.FuelPodUpgradeType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.EitherHolder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Optional;

/**
 * Drives the example addon in {@code dev.galacticraft.test.addon}: a tier 4 rocket defined only
 * through the public API. Each test proves one addon hook reaches gameplay, so a regression back
 * to hardcoded Galacticraft parts fails here.
 */
public class ExampleAddonGameTest implements GalacticraftGameTest {
    private static RocketData tier4() {
        return new RocketData(
                Optional.of(new EitherHolder<>(ExampleAddon.TIER_4_CONE)),
                Optional.of(new EitherHolder<>(ExampleAddon.TIER_4_BODY)),
                Optional.of(new EitherHolder<>(ExampleAddon.TIER_4_FIN)),
                Optional.empty(),
                Optional.of(new EitherHolder<>(ExampleAddon.TIER_4_ENGINE)),
                Optional.empty(), Optional.empty(), 0xFFFFFFFF);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void addonEngineSetsTankAndBurnRate(GameTestHelper test) {
        ConfigImpl config = (ConfigImpl) Galacticraft.CONFIG;
        boolean oldMode = config.gc4RocketFuelMode();
        config.setGc4RocketFuelMode(true);
        RocketEntity rocket = new RocketEntity(GCEntityTypes.ROCKET, test.getLevel());
        try {
            rocket.setData(tier4());
            test.assertTrue(rocket.getFuelTankCapacity() == 243000, "tank should come from the addon engine, got " + rocket.getFuelTankCapacity());

            rocket.setFuel(243000);
            BlockPos pos = test.absolutePos(new BlockPos(2, 10, 2));
            rocket.absMoveTo(pos.getX() + .5, 64, pos.getZ() + .5);
            rocket.setLaunchStage(LaunchStage.IGNITED);
            ExampleAddon.LAUNCHED.clear();
            for (int tick = 0; tick < rocket.getPreLaunchWait(); tick++) rocket.tick();
            test.assertTrue(ExampleAddon.LAUNCHED.contains(rocket), "RocketEvents.LAUNCH did not fire on lift-off");
            long beforeFlight = rocket.getFuel();
            for (int tick = 0; tick < 10; tick++) rocket.tick();
            long burned = beforeFlight - rocket.getFuel();
            test.assertTrue(burned == 10 * 9, "addon engine should burn 9 per tick, burned " + burned + " in 10 ticks");
            test.succeed();
        } finally {
            rocket.discard();
            config.setGc4RocketFuelMode(oldMode);
        }
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void addonPartsSetSeatsSteeringAndHitbox(GameTestHelper test) {
        RocketEntity rocket = new RocketEntity(GCEntityTypes.ROCKET, test.getLevel());
        try {
            rocket.setData(tier4());
            rocket.refreshDimensions();
            test.assertTrue(rocket.maxPassengers() == 3, "body should give 3 seats, got " + rocket.maxPassengers());
            test.assertTrue(rocket.canManeuver(), "fins with can_maneuver should allow steering");
            float height = (59 + 40) / 16F;
            test.assertTrue(Math.abs(rocket.getBbHeight() - height) < 1.0E-4F, "cone layout should set height " + height + ", got " + rocket.getBbHeight());
            test.assertTrue(Math.abs(rocket.getBbWidth() - 3.0F) < 1.0E-4F, "fin layout should set width 3, got " + rocket.getBbWidth());

            rocket.setData(RocketPrefabs.TIER_1);
            rocket.refreshDimensions();
            test.assertTrue(Math.abs(rocket.getBbHeight() - 5.25F) < 1.0E-4F && Math.abs(rocket.getBbWidth() - 2.5F) < 1.0E-4F,
                    "stock tier 1 hitbox changed: " + rocket.getBbWidth() + "x" + rocket.getBbHeight());
            test.succeed();
        } finally {
            rocket.discard();
        }
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void addonSchematicUnlocksItsParts(GameTestHelper test) {
        var parts = WorkbenchPages.rocketPartsUnlockedBy(ExampleAddon.tier4Schematic);
        test.assertTrue(parts.equals(List.of(ExampleAddon.TIER_4_CONE, ExampleAddon.TIER_4_BODY, ExampleAddon.TIER_4_FIN, ExampleAddon.TIER_4_ENGINE)),
                "schematic component lost its parts: " + parts);
        test.assertTrue(WorkbenchPages.tierSchematics().contains(ExampleAddon.tier4Schematic), "addon schematic not seen as a tier schematic");
        test.assertTrue(WorkbenchPages.pageUnlockedBy(test.getLevel(), ExampleAddon.tier4Schematic) != null, "addon schematic leads to no workbench page");
        test.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void addonModuleIsFittedAndPaintable(GameTestHelper test) {
        test.assertTrue(RocketModuleItem.all().contains(ExampleAddon.tier4EngineModule), "workbench does not know the addon module item");
        ItemStack fitted = RocketRefit.module(tier4(), RocketPartTypes.ENGINE);
        test.assertTrue(fitted.is(ExampleAddon.tier4EngineModule), "refit did not hand back the addon module, got " + fitted);
        List<RocketPartTypes> paintable = RocketPaintMenu.parts(new ItemStack(ExampleAddon.tier4EngineModule), test.getLevel().registryAccess());
        test.assertTrue(paintable.equals(List.of(RocketPartTypes.ENGINE)), "paintable engine not offered for paint: " + paintable);
        test.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void addonUpgradeInstallsWithItsOwnData(GameTestHelper test) {
        var access = test.getLevel().registryAccess();
        SimpleContainer slots = new SimpleContainer(3);
        slots.setItem(0, new ItemStack(Items.BUCKET));
        slots.setItem(1, new ItemStack(Items.BUCKET));
        test.assertTrue(WorkbenchUpgrades.canPlace(access, 2, new ItemStack(Items.BUCKET), slots), "bucket refused by the fuel pod upgrade");
        test.assertFalse(WorkbenchUpgrades.canPlace(access, 2, new ItemStack(Items.CHEST), slots), "a chest mixed into a fuel pod build");

        var installed = WorkbenchUpgrades.installed(access, slots).orElse(null);
        test.assertTrue(installed != null && installed.is(ExampleAddon.FUEL_POD), "buckets did not install the fuel pod upgrade");
        Optional<RocketUpgradeData> data = installed.value().createData(WorkbenchUpgrades.contents(slots));
        test.assertTrue(data.equals(Optional.of(new FuelPodUpgradeType.Pods(2))), "wrong upgrade data: " + data);

        RocketData rocket = new RocketData(RocketPrefabs.TIER_1.cone(), RocketPrefabs.TIER_1.body(), RocketPrefabs.TIER_1.fin(),
                Optional.empty(), RocketPrefabs.TIER_1.engine(), Optional.of(new EitherHolder<>(ExampleAddon.FUEL_POD)), data, 0xFFFFFFFF);
        RegistryOps<com.google.gson.JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, access);
        var encoded = RocketData.CODEC.encodeStart(ops, rocket).getOrThrow();
        RocketData decoded = RocketData.CODEC.parse(ops, encoded).getOrThrow();
        test.assertTrue(decoded.getUpgradeData(FuelPodUpgradeType.Pods.class).equals(Optional.of(new FuelPodUpgradeType.Pods(2))),
                "addon upgrade data did not survive saving: " + encoded);

        SimpleContainer chests = new SimpleContainer(3);
        chests.setItem(0, new ItemStack(Items.CHEST));
        var stock = WorkbenchUpgrades.installed(access, chests).orElse(null);
        test.assertTrue(stock != null && stock.is(dev.galacticraft.mod.content.GCRocketParts.STORAGE_UPGRADE), "stock storage upgrade no longer installs");
        test.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void researchEventSeesNewResearch(GameTestHelper test) {
        var player = test.makeMockServerPlayerInLevel();
        ResourceLocation research = ExampleAddon.id("rocket_engine/tier_4");
        ExampleAddon.RESEARCHED.clear();
        ((ServerResearchAccessor) player).galacticraft$unlockRocketPartRecipes(research);
        test.assertTrue(ExampleAddon.RESEARCHED.contains(research), "ResearchEvents.UNLOCKED did not fire");
        ExampleAddon.RESEARCHED.clear();
        ((ServerResearchAccessor) player).galacticraft$unlockRocketPartRecipes(research);
        test.assertTrue(ExampleAddon.RESEARCHED.isEmpty(), "ResearchEvents.UNLOCKED fired for research already held");
        test.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void hazardTagsAndSkyComeFromData(GameTestHelper test) {
        var bodies = test.getLevel().registryAccess().registryOrThrow(AddonRegistries.CELESTIAL_BODY);
        Holder<CelestialBody<?, ?>> mars = bodies.getHolderOrThrow(GCCelestialBodies.MARS);
        Holder<CelestialBody<?, ?>> moon = bodies.getHolderOrThrow(GCCelestialBodies.MOON);
        test.assertTrue(mars.is(CelestialBodyTags.DUST_STORMS) && !moon.is(CelestialBodyTags.DUST_STORMS), "dust storm tag wrong");
        test.assertTrue(bodies.getHolderOrThrow(GCCelestialBodies.MERCURY).is(CelestialBodyTags.SOLAR_FLARES), "solar flare tag missing");
        test.assertTrue(bodies.getHolderOrThrow(GCCelestialBodies.JUPITER).is(CelestialBodyTags.GAS_GIANT), "gas giant tag missing");
        Optional<ResourceLocation> effect = mars.value().sky().flatMap(SkyConfig::postEffect);
        test.assertTrue(effect.equals(Optional.of(Constant.id("shaders/post/mars.json"))), "Mars sky config lost its post effect: " + effect);
        test.assertTrue(moon.value().sky().isEmpty(), "Moon unexpectedly has a sky config");
        test.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void andromedaIsATier4DestinationInAnotherGalaxy(GameTestHelper test) {
        var access = test.getLevel().registryAccess();
        var bodies = access.registryOrThrow(AddonRegistries.CELESTIAL_BODY);
        var galaxies = access.registryOrThrow(AddonRegistries.GALAXY);
        CelestialBody<?, ?> earth = bodies.getOrThrow(GCCelestialBodies.EARTH);
        CelestialBody<?, ?> alpheratz = bodies.get(ExampleAddon.id("alpheratz"));
        CelestialBody<?, ?> prime = bodies.get(ExampleAddon.id("andromeda_prime"));
        Galaxy andromeda = galaxies.get(ExampleAddon.id("andromeda"));
        test.assertTrue(alpheratz != null && prime != null && andromeda != null, "Andromeda datapack entries did not load");

        var systems = GalaxySystems.byGalaxy(galaxies, bodies);
        test.assertTrue(systems.getOrDefault(andromeda, List.of()).equals(List.of(alpheratz)), "Andromeda should hold the Alpheratz system: " + systems.get(andromeda));
        test.assertTrue(systems.getOrDefault(galaxies.getOrThrow(BuiltinObjects.MILKY_WAY_KEY), List.of()).contains(bodies.getOrThrow(GCCelestialBodies.SOL)), "Sol left the Milky Way");
        test.assertFalse(GalaxySystems.sameGalaxy(bodies, earth, prime), "Earth and Andromeda Prime reported as the same galaxy");

        test.assertTrue(tier4().canTravel(access, earth, prime), "tier 4 parts should reach Andromeda Prime");
        test.assertFalse(RocketPrefabs.TIER_3.canTravel(access, earth, prime), "a tier 3 rocket reached a tier 4 planet");

        var type = access.registryOrThrow(Registries.DIMENSION_TYPE).get(ExampleAddon.id("andromeda_prime"));
        test.assertTrue(type != null && type.effectsLocation().equals(Constant.id("space")), "Andromeda Prime should use the galacticraft:space sky");
        test.assertTrue(bodies.wrapAsHolder(prime).is(CelestialBodyTags.DUST_STORMS), "addon tag entry did not opt Andromeda Prime into dust storms");

        // GameTest omits datapack dimensions; decode the fixture and check binding when real levels exist.
        var server = test.getLevel().getServer();
        var stems = access.registryOrThrow(Registries.LEVEL_STEM);
        test.assertTrue(stems.containsKey(ExampleAddon.id("andromeda_prime")) == stems.containsKey(dev.galacticraft.mod.world.dimension.GCDimensions.MARS.location()),
                "the addon dimension and Mars should be registered together");
        var stemFile = server.getResourceManager().getResource(ExampleAddon.id("dimension/andromeda_prime.json"));
        test.assertTrue(stemFile.isPresent(), "Andromeda Prime dimension file missing from the datapack");
        try (var reader = stemFile.get().openAsReader()) {
            var json = com.google.gson.JsonParser.parseReader(reader);
            var stem = net.minecraft.world.level.dimension.LevelStem.CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, access), json).getOrThrow();
            test.assertTrue(stem.type().is(ExampleAddon.id("andromeda_prime")), "Andromeda Prime dimension uses the wrong dimension type");
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, ExampleAddon.id("andromeda_prime")));
        ServerLevel mars = server.getLevel(dev.galacticraft.mod.world.dimension.GCDimensions.MARS);
        // Ignore the dynamic Mars fixture from SpaceflightGameTestSuite; it is not a datapack-loaded level.
        boolean worldBuiltMars = mars != null
                && !DynamicDimensionRegistry.from(server).dynamicDimensionExists(dev.galacticraft.mod.world.dimension.GCDimensions.MARS);
        test.assertTrue((level == null) == !worldBuiltMars, "the addon dimension and Mars should load together");
        if (level != null) {
            Holder<CelestialBody<?, ?>> body = level.galacticraft$getCelestialBody();
            test.assertTrue(body != null && body.value() == prime, "the Andromeda Prime dimension is not bound to its celestial body");
        }
        test.assertTrue(prime.sky().map(SkyConfig::sunSize).equals(Optional.of(3.0F)), "Andromeda Prime lost its sky config");
        test.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void addonFlightGeometryAndSkySystemComeFromTheSyncedRegistry(GameTestHelper test) {
        var access = test.getLevel().registryAccess();
        var primeKey = ResourceKey.create(AddonRegistries.CELESTIAL_BODY, ExampleAddon.id("andromeda_prime"));
        var prime = dev.galacticraft.mod.world.dimension.SpaceTravel.destinations(access).stream()
                .filter(body -> body.body().equals(primeKey)).findFirst().orElseThrow();
        test.assertTrue(prime.radius() == 60 && prime.center().x == 68000, "addon physical geometry did not load");
        var nearest = dev.galacticraft.mod.world.dimension.SpaceTravel.nearest(access, prime.center(), 0);
        test.assertTrue(nearest != null && nearest.body().equals(primeKey), "arrival targeting omitted the addon body");
        var sun = dev.galacticraft.mod.world.dimension.SpaceTravel.sun(access, prime, true);
        test.assertTrue(sun.body().location().equals(ExampleAddon.id("alpheratz")) && sun.radius() == 50,
                "surface sky did not use the addon's star");
        var system = dev.galacticraft.mod.world.dimension.SpaceTravel.systemBodies(access, prime);
        test.assertTrue(system.size() == 2 && system.stream().noneMatch(body -> body.body().equals(GCCelestialBodies.EARTH)),
                "addon surface sky leaked stock system bodies");
        test.assertTrue(dev.galacticraft.mod.world.dimension.SpaceTravel.preloadTarget(access, prime.departurePosition(),
                prime.center().subtract(prime.departurePosition()).normalize()).body().equals(primeKey), "addon arrival was not preloaded");
        test.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void upgradeModulesPreservePayloadAndActuallyExpandTheTank(GameTestHelper test) {
        var access = test.getLevel().registryAccess();
        var base = dev.galacticraft.mod.content.item.GenericRocketModuleItem.dataFor(ExampleAddon.FUEL_POD);
        RocketData carried = new RocketData(base.cone(), base.body(), base.fin(), base.booster(), base.engine(), base.upgrade(),
                Optional.of(new FuelPodUpgradeType.Pods(2)), base.color());
        ItemStack module = dev.galacticraft.mod.content.item.GenericRocketModuleItem.stackFor(ExampleAddon.FUEL_POD);
        module.set(dev.galacticraft.api.component.GCDataComponents.ROCKET_DATA, carried);
        test.assertTrue(dev.galacticraft.mod.content.item.GCItems.ROCKET_MODULE.type(module) == RocketPartTypes.UPGRADE,
                "generic module did not expose its upgrade slot");
        SimpleContainer slots = new SimpleContainer(3);
        test.assertTrue(WorkbenchUpgrades.canPlace(access, 0, module, slots), "upgrade module rejected");
        slots.setItem(0, module);
        test.assertTrue(WorkbenchUpgrades.installed(access, slots).orElseThrow().is(ExampleAddon.FUEL_POD), "wrong upgrade installed");
        test.assertFalse(WorkbenchUpgrades.canPlace(access, 1, new ItemStack(Items.CHEST), slots), "mixed upgrade items accepted");
        var data = new RocketData(RocketPrefabs.TIER_1.cone(), RocketPrefabs.TIER_1.body(), RocketPrefabs.TIER_1.fin(),
                Optional.empty(), RocketPrefabs.TIER_1.engine(), base.upgrade(), WorkbenchUpgrades.data(access, slots), -1);
        var returned = RocketRefit.module(data, RocketPartTypes.UPGRADE);
        test.assertTrue(dev.galacticraft.mod.content.item.GCItems.ROCKET_MODULE.rocketData(returned)
                .getUpgradeData(FuelPodUpgradeType.Pods.class).equals(Optional.of(new FuelPodUpgradeType.Pods(2))), "refit stripped upgrade payload");
        var config = (ConfigImpl) Galacticraft.CONFIG;
        boolean previous = config.gc4RocketFuelMode();
        config.setGc4RocketFuelMode(true);
        var rocket = new RocketEntity(GCEntityTypes.ROCKET, test.getLevel());
        var restored = new RocketEntity(GCEntityTypes.ROCKET, test.getLevel());
        try {
            rocket.setData(data);
            test.assertTrue(rocket.getFuelTankCapacity() == 243000, "two pods should add two buckets to the stock tank");
            rocket.setFuel(200000);
            restored.load(rocket.saveWithoutId(new net.minecraft.nbt.CompoundTag()));
            test.assertTrue(restored.getFuelTankCapacity() == 243000 && restored.getFuel() == 200000,
                    "fuel/pod capacity did not survive entity persistence");
        } finally { rocket.discard(); restored.discard(); config.setGc4RocketFuelMode(previous); }
        test.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void embeddedModulePatternsAndStackOnlyResearchSourcesReachGameplay(GameTestHelper test) {
        var recipe = WorkbenchPages.recipe(test.getLevel(), ExampleAddon.id("rocket_module/rocket_engine/tier_4"));
        test.assertTrue(recipe != null && recipe.value() instanceof dev.galacticraft.mod.recipe.RocketModuleRecipe,
                "configured part recipe did not become a workbench page");
        var plate = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                ResourceLocation.fromNamespaceAndPath("galacticraft", "heavy_plating_t3"));
        var items = List.of(new ItemStack(Items.DIAMOND), new ItemStack(plate));
        var input = new net.minecraft.world.item.crafting.RecipeInput() {
            @Override public ItemStack getItem(int slot) { return items.get(slot); }
            @Override public int size() { return items.size(); }
        };
        test.assertTrue(recipe.value().matches(input, test.getLevel()), "embedded recipe cannot be crafted");
        ItemStack output = recipe.value().assemble(input, test.getLevel().registryAccess());
        test.assertTrue(RocketRefit.key(dev.galacticraft.mod.content.item.GCItems.ROCKET_MODULE.rocketData(output), RocketPartTypes.ENGINE)
                .equals(Optional.of(ExampleAddon.TIER_4_ENGINE)), "embedded pattern produced the wrong module");
        var how = dev.galacticraft.mod.machine.workbench.ResearchSources.describe(test.getLevel().getServer())
                .get(ResourceLocation.fromNamespaceAndPath("examplepack", "rocket_engine/ion"));
        test.assertTrue(how != null && how.getString().contains("redstone block"), "stack-only schematic acquisition hint missing");
        for (String name : List.of("tier_4_cone_module", "tier_4_body_module", "tier_4_fin_module", "tier_4_engine_module", "tier_4_schematic"))
            test.assertTrue(test.getLevel().getRecipeManager().byKey(ExampleAddon.id(name)).isPresent(), "missing survival acquisition recipe: " + name);
        var pos = new BlockPos(1, 1, 1);
        test.setBlock(pos, dev.galacticraft.mod.content.GCBlocks.ROCKET_WORKBENCH);
        dev.galacticraft.mod.content.block.entity.RocketWorkbenchBlockEntity bench = test.getBlockEntity(pos);
        var player = makeSurvivalServerPlayer(test);
        player.setPos(test.absolutePos(pos).getCenter());
        ((ServerResearchAccessor) player).galacticraft$unlockRocketPartRecipes(
                dev.galacticraft.api.rocket.part.RocketPart.researchId(ExampleAddon.TIER_4_ENGINE));
        // Opening a smaller pattern must match prefilled wells before the backing list is trimmed.
        for (int i = 0; i < items.size(); i++) bench.ingredients.setItem(i, items.get(i).copy());
        bench.ingredients.setChanged();
        var menu = new dev.galacticraft.mod.screen.RocketWorkbenchMenu(1, bench, player.getInventory(), recipe.id());
        player.containerMenu = menu;
        var slot = menu.resultSlot();
        var crafted = slot.remove(1);
        test.assertTrue(!crafted.isEmpty() && RocketRefit.key(RocketRefit.data(crafted), RocketPartTypes.ENGINE)
                .equals(Optional.of(ExampleAddon.TIER_4_ENGINE)), "module page did not produce a fittable engine");
        slot.onTake(player, crafted);
        test.assertTrue(bench.ingredients.isEmpty() && bench.output.isEmpty(), "module page did not consume its pattern once");
        menu.removed(player);
        test.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void wrappedPatternsMatchTheirOwnRightHandIngredients(GameTestHelper test) {
        var json = com.google.gson.JsonParser.parseString("{\"type\":\"galacticraft:wrap_patterned\",\"config\":{\"height\":18,\"left\":[{\"x\":0,\"y\":0,\"ingredient\":{\"item\":\"minecraft:iron_ingot\"}}],\"right\":[{\"x\":0,\"y\":0,\"ingredient\":{\"item\":\"minecraft:gold_ingot\"}},{\"x\":18,\"y\":0,\"ingredient\":{\"item\":\"minecraft:diamond\"}}]}}");
        var pattern = dev.galacticraft.api.rocket.recipe.RocketPartRecipe.DIRECT_CODEC.parse(
                RegistryOps.create(JsonOps.INSTANCE, test.getLevel().registryAccess()), json).getOrThrow();
        var stacks = List.of(new ItemStack(Items.IRON_INGOT), new ItemStack(Items.GOLD_INGOT), new ItemStack(Items.DIAMOND));
        var input = new net.minecraft.world.item.crafting.RecipeInput() {
            @Override public ItemStack getItem(int slot) { return stacks.get(slot); }
            @Override public int size() { return stacks.size(); }
        };
        test.assertTrue(pattern.matches(input, test.getLevel()), "asymmetric right-hand recipe failed");
        test.assertTrue(pattern.getIngredients().get(0).test(stacks.get(0)) && pattern.getIngredients().get(2).test(stacks.get(2)),
                "recipe ingredient order disagrees with slot placement");
        test.succeed();
    }

    @net.minecraft.gametest.framework.BeforeBatch(batch = "galacticraft:addon-flight")
    public void prepareAddonFlight(ServerLevel level) {
        new SpaceflightGameTestSuite().enableSpaceflight(level);
        var server = level.getServer();
        var key = ResourceKey.create(Registries.DIMENSION, ExampleAddon.id("andromeda_prime"));
        if (server.getLevel(key) != null) return;
        var access = (dev.galacticraft.dynamicdimensions.impl.mixin.MinecraftServerAccessor) server;
        var settings = new net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings(Optional.empty(),
                level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(dev.galacticraft.mod.world.biome.GCBiomes.SPACE), List.of());
        var fixture = new ServerLevel(server, access.getExecutor(), access.getStorageSource(),
                new net.minecraft.world.level.storage.DerivedLevelData(server.getWorldData(), server.getWorldData().overworldData()), key,
                new net.minecraft.world.level.dimension.LevelStem(level.registryAccess().registryOrThrow(Registries.DIMENSION_TYPE)
                        .getHolderOrThrow(ResourceKey.create(Registries.DIMENSION_TYPE, key.location())),
                        new net.minecraft.world.level.levelgen.FlatLevelSource(settings)),
                access.getProgressListenerFactory().create(0), false, 0, List.of(), false, null);
        ((dev.galacticraft.dynamicdimensions.impl.accessor.DynamicDimensionProvider) server).dynamicdimensions$registerLevel(fixture);
    }

    @net.minecraft.gametest.framework.AfterBatch(batch = "galacticraft:addon-flight")
    public void restoreAddonFlight(ServerLevel level) { new SpaceflightGameTestSuite().restoreSpaceflight(level); }

    @GameTest(batch = "galacticraft:addon-flight", template = EMPTY_STRUCTURE, timeoutTicks = 20000)
    public void addonPlanetSupportsPhysicalDepartureAndArrival(GameTestHelper test) {
        var primeKey = ResourceKey.create(Registries.DIMENSION, ExampleAddon.id("andromeda_prime"));
        var prime = test.getLevel().getServer().getLevel(primeKey);
        test.assertTrue(prime != null && prime.galacticraft$getCelestialBody() != null, "live addon level is not bound");
        var body = dev.galacticraft.mod.world.dimension.SpaceTravel.fromLevel(prime);
        var rocket = new RocketEntity(GCEntityTypes.ROCKET, prime);
        rocket.setData(tier4());
        rocket.setFuel(rocket.getFuelTankCapacity());
        rocket.setLaunchStage(LaunchStage.LAUNCHED);
        rocket.setPos(0, Constant.ESCAPE_HEIGHT + 1, 0);
        prime.addFreshEntity(rocket);
        var pilot = test.makeMockServerPlayerInLevel();
        pilot.setInvulnerable(true);
        pilot.teleportTo(prime, 0, rocket.getY() + 2, 0, 0, 0);
        test.assertTrue(pilot.startRiding(rocket, true), "pilot cannot board addon rocket");
        boolean[] departed = {false};
        test.onEachTick(() -> {
            if (pilot.getVehicle() instanceof RocketEntity ship) {
                var token = ship.pendingSpaceflightHandoff();
                if (token != null) ship.acknowledgeSpaceflight(pilot, token);
                if (ship.isInSpaceflight()) dev.galacticraft.mod.world.dimension.SpaceTravel.prepareArrival(
                        (ServerLevel) ship.level(), ship.position(), ship.getId());
            }
        });
        test.succeedWhen(() -> {
            if (!departed[0]) {
                test.assertTrue(pilot.getVehicle() instanceof RocketEntity ship && ship.isInSpaceflight(), "waiting for addon departure");
                var ship = (RocketEntity) pilot.getVehicle();
                test.assertTrue(!pilot.galacticraft$isCelestialScreenActive(), "addon departure fell back to the map");
                ship.setPos(body.center().add(0, 0, body.radius() + 8));
                ship.move(net.minecraft.world.entity.MoverType.SELF, net.minecraft.world.phys.Vec3.ZERO);
                ship.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                departed[0] = true;
            }
            test.assertTrue(pilot.level().dimension().equals(primeKey)
                    && pilot.getVehicle() instanceof dev.galacticraft.mod.content.entity.vehicle.LanderEntity, "waiting for addon lander arrival");
            var lander = (dev.galacticraft.mod.content.entity.vehicle.LanderEntity) pilot.getVehicle();
            test.assertTrue(lander.getFuel() > 0, "addon arrival lost fuel");
        });
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void survivalAcquisitionSchematicAndAssemblyFormACompleteRoute(GameTestHelper test) {
        var level = test.getLevel();
        var lookup = level.registryAccess();
        var plate = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.parse("galacticraft:heavy_plating_t3"));
        var extras = List.of(Items.IRON_INGOT, Items.GOLD_INGOT, Items.EMERALD, Items.REDSTONE_BLOCK);
        var names = List.of("cone", "body", "fin", "engine");
        var modules = new java.util.ArrayList<ItemStack>();
        for (int i = 0; i < names.size(); i++) {
            var recipe = level.getRecipeManager().byKey(ExampleAddon.id("tier_4_" + names.get(i) + "_module")).orElseThrow().value();
            var input = net.minecraft.world.item.crafting.CraftingInput.of(1, 3,
                    List.of(new ItemStack(Items.DIAMOND), new ItemStack(plate), new ItemStack(extras.get(i))));
            test.assertTrue(recipe instanceof net.minecraft.world.item.crafting.ShapedRecipe shaped && shaped.matches(input, level), "survival module recipe does not match " + names.get(i));
            modules.add(((net.minecraft.world.item.crafting.ShapedRecipe) recipe).assemble(input, lookup));
        }
        var schematicRecipe = (net.minecraft.world.item.crafting.ShapelessRecipe) level.getRecipeManager()
                .byKey(ExampleAddon.id("tier_4_schematic")).orElseThrow().value();
        var schematicInput = net.minecraft.world.item.crafting.CraftingInput.of(2, 1, List.of(new ItemStack(Items.PAPER), new ItemStack(Items.DIAMOND)));
        test.assertTrue(schematicRecipe.matches(schematicInput, level), "survival schematic recipe does not match");
        var schematic = schematicRecipe.assemble(schematicInput, lookup);
        var pos = new BlockPos(1, 1, 1);
        test.setBlock(pos, dev.galacticraft.mod.content.GCBlocks.ROCKET_WORKBENCH);
        dev.galacticraft.mod.content.block.entity.RocketWorkbenchBlockEntity bench = test.getBlockEntity(pos);
        var player = makeSurvivalServerPlayer(test);
        player.setPos(test.absolutePos(pos).getCenter());
        var menu = new dev.galacticraft.mod.screen.RocketWorkbenchMenu(1, bench, player.getInventory(), WorkbenchPages.ROCKET_ID);
        player.containerMenu = menu;
        for (int i = 0; i < modules.size(); i++) bench.ingredients.setItem(i, modules.get(i));
        bench.ingredients.setChanged();
        test.assertTrue(bench.output.isEmpty() && menu.getMissingResearchIds().size() == 4, "unresearched survival player built a tier-4 rocket");
        bench.ingredients.clearContent();
        menu.removed(player);
        var unlock = new dev.galacticraft.mod.screen.RocketWorkbenchMenu(2, bench, player.getInventory(), WorkbenchPages.ADD_SCHEMATIC_ID);
        player.containerMenu = unlock;
        bench.schematic.setItem(0, schematic);
        test.assertTrue(unlock.clickMenuButton(player, dev.galacticraft.mod.screen.RocketWorkbenchMenu.BUTTON_UNLOCK) && bench.schematic.isEmpty(), "crafted schematic did not unlock and consume");
        var assembly = (dev.galacticraft.mod.screen.RocketWorkbenchMenu) player.containerMenu;
        for (int i = 0; i < modules.size(); i++) bench.ingredients.setItem(i, modules.get(i));
        bench.ingredients.setChanged();
        var slot = assembly.resultSlot();
        ItemStack result = slot.remove(1);
        test.assertTrue(result.is(dev.galacticraft.mod.content.item.GCItems.ROCKET)
                && RocketRefit.data(result).engine().orElseThrow().key().equals(ExampleAddon.TIER_4_ENGINE), "unlocked assembly produced no addon rocket");
        slot.onTake(player, result);
        test.assertTrue(bench.ingredients.isEmpty() && bench.output.isEmpty(), "assembly did not consume exactly its four modules");
        assembly.removed(player);
        test.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void compositeOrAllowsALaterChildWithoutChangingPartVetoes(GameTestHelper test) {
        var type = dev.galacticraft.impl.rocket.travelpredicate.type.ConstantTravelPredicateType.INSTANCE;
        var block = type.configure(new dev.galacticraft.impl.rocket.travelpredicate.config.ConstantTravelPredicateConfig(
                dev.galacticraft.api.rocket.travelpredicate.TravelPredicateType.Result.BLOCK));
        var allow = type.configure(new dev.galacticraft.impl.rocket.travelpredicate.config.ConstantTravelPredicateConfig(
                dev.galacticraft.api.rocket.travelpredicate.TravelPredicateType.Result.ALLOW));
        var rule = dev.galacticraft.impl.rocket.travelpredicate.type.OrTravelPredicateType.INSTANCE.configure(
                new dev.galacticraft.impl.rocket.travelpredicate.config.OrTravelPredicateConfig(List.of(block, allow)));
        var earth = test.getLevel().registryAccess().registryOrThrow(AddonRegistries.CELESTIAL_BODY).get(GCCelestialBodies.EARTH);
        test.assertTrue(rule.canTravel(earth, earth, null, null, null, null, null, null)
                == dev.galacticraft.api.rocket.travelpredicate.TravelPredicateType.Result.ALLOW, "OR stopped at its first blocking child");
        test.succeed();
    }

    @GameTest(batch = "galacticraft:addon-flight", template = EMPTY_STRUCTURE)
    public void aSameSizeRegistryReplacementRefreshesAPositiveLevelBinding(GameTestHelper test) throws ReflectiveOperationException {
        var prime = test.getLevel().getServer().getLevel(ResourceKey.create(Registries.DIMENSION, ExampleAddon.id("andromeda_prime")));
        var originalAccess = prime.registryAccess();
        var original = prime.galacticraft$getCelestialBody();
        test.assertTrue(original != null, "fixture has no initial body binding");
        var registry = originalAccess.registryOrThrow(AddonRegistries.CELESTIAL_BODY);
        var ops = RegistryOps.create(JsonOps.INSTANCE, originalAccess);
        var json = CelestialBody.DIRECT_CODEC.encodeStart(ops, original.value()).getOrThrow().getAsJsonObject();
        json.getAsJsonObject("config").addProperty("world", "galacticraft-test:unbound_prime");
        var relocated = CelestialBody.DIRECT_CODEC.parse(ops, json).getOrThrow();
        var replacement = new net.minecraft.core.MappedRegistry<CelestialBody<?, ?>>(AddonRegistries.CELESTIAL_BODY, com.mojang.serialization.Lifecycle.stable());
        registry.holders().forEach(holder -> net.minecraft.core.Registry.register(replacement, holder.key(), holder == original ? relocated : holder.value()));
        replacement.freeze();
        test.assertTrue(registry.size() == replacement.size(), "fixture must replace without changing registry size");
        var access = new net.minecraft.core.RegistryAccess.ImmutableRegistryAccess(originalAccess.registries()
                .map(entry -> entry.key().equals(AddonRegistries.CELESTIAL_BODY)
                        ? new net.minecraft.core.RegistryAccess.RegistryEntry<>(AddonRegistries.CELESTIAL_BODY, replacement) : entry));
        var field = net.minecraft.world.level.Level.class.getDeclaredField("registryAccess");
        field.setAccessible(true);
        try {
            field.set(prime, access);
            test.assertTrue(prime.galacticraft$getCelestialBody() == null, "positive body cache survived a same-size registry replacement");
        } finally {
            field.set(prime, originalAccess);
            dev.galacticraft.api.universe.celestialbody.CelestialBodyCache.invalidate();
        }
        test.assertTrue(prime.galacticraft$getCelestialBody() == original, "invalidation did not restore the real binding");
        test.succeed();
    }

}
