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

package dev.galacticraft.fabric;

import dev.galacticraft.common.GalacticraftCommon;
import dev.galacticraft.mod.Galacticraft;
import dev.galacticraft.mod.content.GCBlockPlatformHooks;
import dev.galacticraft.mod.content.GCBlocks;
import dev.galacticraft.mod.content.EntityAttributePlatformHooks;
import dev.galacticraft.mod.data.OxygenBlockDataManager;
import dev.galacticraft.mod.storage.ItemFluidTransfer;
import dev.galacticraft.mod.storage.ItemStoragePull;
import dev.galacticraft.mod.util.FluidTooltipPlatformHooks;
import dev.galacticraft.mod.content.item.CreativeTabPlatformHooks;
import dev.galacticraft.mod.content.item.GCItems;
import dev.galacticraft.mod.world.gen.WorldgenPlatformHooks;
import dev.galacticraft.mod.world.gen.feature.GCOrePlacedFeatures;
import dev.galacticraft.mod.world.gen.feature.GCPlacedFeatures;
import net.minecraft.data.worldgen.features.MiscOverworldFeatures;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.biome.v1.ModificationPhase;
import net.minecraft.world.level.levelgen.GenerationStep;
import dev.galacticraft.mod.village.VillagerPlatformHooks;
import dev.galacticraft.mod.village.GCVillagerProfessions;
import net.fabricmc.fabric.api.object.builder.v1.trade.TradeOfferHelper;
import dev.galacticraft.mod.events.SleepPlatformHooks;
import dev.galacticraft.mod.events.GCSleepEventHandlers;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.item.InventoryStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.fabricmc.fabric.api.registry.FlammableBlockRegistry;
import net.fabricmc.fabric.api.registry.FlattenableBlockRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.world.poi.PointOfInterestHelper;
import dev.galacticraft.mod.world.poi.PointOfInterestPlatformHooks;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;

/** Fabric loader adapter. Existing Fabric initializers remain separate while their code moves to common. */
public final class GalacticraftFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        PointOfInterestPlatformHooks.setRegistrar(PointOfInterestHelper::register);
        FluidTooltipPlatformHooks.setNameResolver(data ->
                net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariantAttributes.getName(
                        FluidVariant.of(data.variant().fluid(), data.variant().components())).plainCopy());
        EntityAttributePlatformHooks.setRegistration(() ->
                dev.galacticraft.mod.content.GCEntityTypes.registerAttributes(FabricDefaultAttributeRegistry::register));
        GCBlockPlatformHooks.setRegistration(() -> {
            FlammableBlockRegistry.getDefaultInstance().add(GCBlocks.FUEL, 80, 130);
            FlammableBlockRegistry.getDefaultInstance().add(GCBlocks.CRUDE_OIL, 60, 100);
            FlammableBlockRegistry.getDefaultInstance().add(GCBlocks.CAVERNOUS_VINES, 15, 60);
            FlammableBlockRegistry.getDefaultInstance().add(GCBlocks.CAVERNOUS_VINES_PLANT, 15, 60);
            FlattenableBlockRegistry.register(GCBlocks.MOON_TURF, GCBlocks.MOON_DIRT_PATH.defaultBlockState());
            FlattenableBlockRegistry.register(GCBlocks.MOON_DIRT, GCBlocks.MOON_DIRT_PATH.defaultBlockState());
        });
        ItemFluidTransfer.register((container, slotIndex, tank) -> {
            var stack = container.getItem(slotIndex);
            if (stack.isEmpty() || tank.isEmpty()) return;
            var storage = ContainerItemContext.ofSingleSlot(InventoryStorage.of(container, null).getSlot(slotIndex)).find(FluidStorage.ITEM);
            if (storage == null) return;
            try (Transaction tx = Transaction.openOuter()) {
                long inserted = storage.insert(FluidVariant.of(tank.getResource(), tank.getComponents()), tank.getAmount(), tx);
                if (inserted > 0) { tank.extract(inserted); tx.commit(); }
            }
        });
        CreativeTabPlatformHooks.setSpawnEggRegistration(() ->
                ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.SPAWN_EGGS).register(content -> {
                    for (var egg : GCItems.spawnEggs()) content.addAfter(ItemStack.EMPTY, egg);
                }));
        ItemStoragePull.register((level, pos, direction, target, limit) -> {
            var source = net.fabricmc.fabric.api.transfer.v1.item.ItemStorage.SIDED.find(
                    level, pos.relative(direction), direction.getOpposite());
            if (source == null) return 0;
            long moved = 0;
            for (var view : source.nonEmptyViews()) {
                moved += dev.galacticraft.mod.util.TransferStorageUtil.move(
                        view.getResource(), source, target, limit - moved);
                if (moved >= limit) break;
            }
            return moved;
        });
        WorldgenPlatformHooks.setOverworldOres(() ->
                BiomeModifications.create(dev.galacticraft.mod.Constant.id("ores")).add(
                        ModificationPhase.ADDITIONS, BiomeSelectors.foundInOverworld(), context -> {
                            context.getGenerationSettings().addFeature(GenerationStep.Decoration.UNDERGROUND_ORES, GCOrePlacedFeatures.ORE_SILICON);
                            context.getGenerationSettings().addFeature(GenerationStep.Decoration.UNDERGROUND_ORES, GCOrePlacedFeatures.ORE_SILICON_LARGE);
                            context.getGenerationSettings().addFeature(GenerationStep.Decoration.UNDERGROUND_ORES, GCOrePlacedFeatures.ORE_TIN_UPPER);
                            context.getGenerationSettings().addFeature(GenerationStep.Decoration.UNDERGROUND_ORES, GCOrePlacedFeatures.ORE_TIN_MIDDLE);
                            context.getGenerationSettings().addFeature(GenerationStep.Decoration.UNDERGROUND_ORES, GCOrePlacedFeatures.ORE_TIN_SMALL);
                            context.getGenerationSettings().addFeature(GenerationStep.Decoration.UNDERGROUND_ORES, GCOrePlacedFeatures.ORE_ALUMINUM_MIDDLE);
                            context.getGenerationSettings().addFeature(GenerationStep.Decoration.UNDERGROUND_ORES, GCOrePlacedFeatures.ORE_ALUMINUM_SMALL);
                        }));
        WorldgenPlatformHooks.setOilLake(() -> BiomeModifications.addFeature(
                context -> context.hasFeature(MiscOverworldFeatures.LAKE_LAVA),
                GenerationStep.Decoration.LAKES, GCPlacedFeatures.OIL_LAKE));
        VillagerPlatformHooks.setTrades(() -> {
            for (int level = 1; level <= 5; level++) {
                int tradeLevel = level;
                TradeOfferHelper.registerVillagerOffers(GCVillagerProfessions.LUNAR_CARTOGRAPHER,
                        level, trades -> GCVillagerProfessions.populateTrades(tradeLevel, trades));
            }
        });
        SleepPlatformHooks.setRegistration(() -> {
            EntitySleepEvents.ALLOW_BED.register(GCSleepEventHandlers::allowCryogenicSleep);
            EntitySleepEvents.ALLOW_SETTING_SPAWN.register(GCSleepEventHandlers::allowSettingSpawn);
            EntitySleepEvents.MODIFY_SLEEPING_DIRECTION.register(GCSleepEventHandlers::changeSleepPosition);
            EntitySleepEvents.ALLOW_SLEEPING.register(GCSleepEventHandlers::sleepInSpace);
            EntitySleepEvents.ALLOW_SLEEP_TIME.register(GCSleepEventHandlers::canCryoSleep);
            EntitySleepEvents.STOP_SLEEPING.register(GCSleepEventHandlers::onWakeFromCryoSleep);
        });
        FabricOxygenTankExtractor.register();
        GalacticraftCommon.init();
        new Galacticraft().onInitialize();
        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override
            public ResourceLocation getFabricId() {
                return OxygenBlockDataManager.ID;
            }

            @Override
            public void onResourceManagerReload(ResourceManager manager) {
                OxygenBlockDataManager.INSTANCE.onResourceManagerReload(manager);
            }
        });
    }
}
