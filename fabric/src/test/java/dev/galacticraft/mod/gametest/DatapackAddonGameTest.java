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

import dev.galacticraft.api.registry.RocketRegistries;
import dev.galacticraft.api.rocket.RocketData;
import dev.galacticraft.api.rocket.part.RocketEngine;
import dev.galacticraft.api.rocket.part.RocketPartTypes;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.Galacticraft;
import dev.galacticraft.mod.config.ConfigImpl;
import dev.galacticraft.mod.content.GCEntityTypes;
import dev.galacticraft.mod.content.entity.vehicle.RocketEntity;
import dev.galacticraft.mod.content.item.GCItems;
import dev.galacticraft.mod.content.item.GenericRocketModuleItem;
import dev.galacticraft.mod.content.rocket.RocketPrefabs;
import dev.galacticraft.mod.machine.workbench.RocketRefit;
import dev.galacticraft.mod.machine.workbench.WorkbenchPages;
import dev.galacticraft.mod.recipe.ModularRocketRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.EitherHolder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;

import java.util.List;
import java.util.Optional;

/**
 * Drives the {@code examplepack} namespace in the test resources: a rocket part added by a plain
 * datapack, with no code. The part, the recipe that hands it out as a generic module, and the loot
 * table that drops its schematic are all JSON.
 */
public class DatapackAddonGameTest implements GalacticraftGameTest {
    private static final ResourceKey<RocketEngine<?, ?>> ION = ResourceKey.create(RocketRegistries.ROCKET_ENGINE,
            ResourceLocation.fromNamespaceAndPath("examplepack", "ion"));

    @GameTest(template = EMPTY_STRUCTURE)
    public void datapackPartLoadsWithItsModelAndStats(GameTestHelper test) {
        RocketEngine<?, ?> ion = test.getLevel().registryAccess().registryOrThrow(RocketRegistries.ROCKET_ENGINE).get(ION);
        test.assertTrue(ion != null, "datapack engine did not load");
        test.assertTrue(ion.model().equals(Optional.of(Constant.id("models/misc/rocket_engine_tier_2.json"))), "part model not read: " + ion.model());
        test.assertTrue(ion.getFuelUsagePerTick() == 6 && ion.getFuelCapacity() == 162000, "datapack engine stats not read");

        ConfigImpl config = (ConfigImpl) Galacticraft.CONFIG;
        boolean oldMode = config.gc4RocketFuelMode();
        config.setGc4RocketFuelMode(true);
        RocketEntity rocket = new RocketEntity(GCEntityTypes.ROCKET, test.getLevel());
        try {
            RocketData data = RocketPrefabs.TIER_1;
            rocket.setData(new RocketData(data.cone(), data.body(), data.fin(), Optional.empty(),
                    Optional.of(new EitherHolder<>(ION)), Optional.empty(), Optional.empty(), 0xFFFFFFFF));
            test.assertTrue(rocket.getFuelTankCapacity() == 162000, "rocket tank ignored the datapack engine");
            test.succeed();
        } finally {
            rocket.discard();
            config.setGc4RocketFuelMode(oldMode);
        }
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void datapackRecipeHandsOutAFittableModule(GameTestHelper test) {
        var access = test.getLevel().registryAccess();
        var recipe = test.getLevel().getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath("examplepack", "ion_engine_module"));
        test.assertTrue(recipe.isPresent(), "datapack module recipe did not load");
        ItemStack module = recipe.get().value().getResultItem(access);
        test.assertTrue(module.is(GCItems.ROCKET_MODULE), "recipe should make a generic rocket module, made " + module);
        test.assertTrue(GCItems.ROCKET_MODULE.type(module) == RocketPartTypes.ENGINE, "generic module did not read its slot from the stack");
        test.assertTrue(RocketRefit.key(GCItems.ROCKET_MODULE.rocketData(module), RocketPartTypes.ENGINE).equals(Optional.of(ION)),
                "generic module does not carry the datapack engine");
        test.assertTrue(new ModularRocketRecipe().engine().test(module), "the modular rocket page refuses the generic module");

        ItemStack refitted = RocketRefit.module(GenericRocketModuleItem.dataFor(ION), RocketPartTypes.ENGINE);
        test.assertTrue(refitted.is(GCItems.ROCKET_MODULE) && GCItems.ROCKET_MODULE.type(refitted) == RocketPartTypes.ENGINE,
                "removing a datapack part from a rocket did not hand back a generic module");
        test.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void datapackLootDropsASchematicForItsPart(GameTestHelper test) {
        var table = test.getLevel().getServer().reloadableRegistries()
                .getLootTable(ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.fromNamespaceAndPath("examplepack", "ion_schematic")));
        List<ItemStack> drops = table.getRandomItems(new LootParams.Builder(test.getLevel()).create(LootContextParamSets.EMPTY));
        test.assertTrue(drops.size() == 1 && drops.getFirst().is(GCItems.ROCKET_SCHEMATIC), "loot table did not drop a rocket schematic: " + drops);
        ItemStack schematic = drops.getFirst();
        test.assertTrue(WorkbenchPages.rocketPartsUnlockedBy(schematic).equals(List.of(ION)),
                "schematic stack lost its parts: " + WorkbenchPages.rocketPartsUnlockedBy(schematic));
        test.assertTrue(WorkbenchPages.pageUnlockedBy(test.getLevel(), schematic) != null, "datapack schematic leads to no workbench page");
        test.assertTrue(WorkbenchPages.rocketPartsUnlockedBy(new ItemStack(GCItems.ROCKET_SCHEMATIC)).isEmpty(), "a blank rocket schematic unlocks parts");
        test.succeed();
    }
}
