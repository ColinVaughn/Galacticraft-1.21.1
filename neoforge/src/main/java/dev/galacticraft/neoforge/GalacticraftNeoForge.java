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

package dev.galacticraft.neoforge;

import dev.galacticraft.common.GalacticraftCommon;
import dev.galacticraft.api.registry.AddonRegistries;
import dev.galacticraft.api.registry.BuiltInAddonRegistries;
import dev.galacticraft.api.registry.BuiltInRocketRegistries;
import dev.galacticraft.api.registry.RocketRegistries;
import dev.galacticraft.api.accessor.SatelliteAccessor;
import dev.galacticraft.api.entity.attribute.GcApiEntityAttributes;
import dev.galacticraft.api.gas.Gases;
import dev.galacticraft.api.rocket.part.RocketBody;
import dev.galacticraft.api.rocket.part.RocketBooster;
import dev.galacticraft.api.rocket.part.RocketCone;
import dev.galacticraft.api.rocket.part.RocketEngine;
import dev.galacticraft.api.rocket.part.RocketFin;
import dev.galacticraft.api.rocket.part.RocketUpgrade;
import dev.galacticraft.api.universe.celestialbody.CelestialBody;
import dev.galacticraft.api.universe.celestialbody.landable.teleporter.CelestialTeleporter;
import dev.galacticraft.api.universe.galaxy.Galaxy;
import dev.galacticraft.impl.universe.BuiltinObjects;
import dev.galacticraft.dynamicdimensions.api.event.DynamicDimensionLoadCallback;
import dev.galacticraft.impl.internal.command.GCApiCommands;
import dev.galacticraft.impl.internal.fabric.GalacticraftAPI;
import dev.galacticraft.impl.network.GCApiPackets;
import dev.galacticraft.impl.network.GCApiServerPacketReceivers;
import dev.galacticraft.mod.Galacticraft;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.content.GCEntityTypes;
import dev.galacticraft.mod.content.GCRegistry;
import dev.galacticraft.mod.content.GCBlocks;
import dev.galacticraft.mod.content.GCBlockPlatformHooks;
import dev.galacticraft.mod.content.GCBlockEntityTypes;
import dev.galacticraft.mod.content.item.GCItems;
import dev.galacticraft.mod.content.item.InfiniteBatteryItem;
import dev.galacticraft.mod.attachments.GCAttachments;
import dev.galacticraft.impl.internal.gear.OxygenTankExtractor;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import dev.galacticraft.machinelib.impl.storage.neoforge.ExposedEnergyStorageNeoForge;
import dev.galacticraft.machinelib.api.item.SingleVariantFixedItemBackedFluidStorage;
import dev.galacticraft.mod.content.item.OxygenTankItem;
import dev.galacticraft.mod.storage.ItemFluidTransfer;
import dev.galacticraft.mod.storage.ItemStoragePull;
import dev.galacticraft.mod.content.item.CreativeTabPlatformHooks;
import dev.galacticraft.neoforge.fluid.InfiniteOxygenFluidHandler;
import dev.galacticraft.neoforge.fluid.CanisterFluidHandler;
import dev.galacticraft.mod.content.entity.data.GCEntityDataSerializers;
import dev.galacticraft.mod.data.gen.SatelliteChunkGenerator;
import dev.galacticraft.neoforge.fluid.GCNeoForgeFluidTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.MappedRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FireBlock;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraft.world.item.CreativeModeTabs;
import dev.galacticraft.mod.village.VillagerPlatformHooks;
import dev.galacticraft.mod.village.GCVillagerProfessions;
import net.neoforged.neoforge.event.village.VillagerTradesEvent;
import dev.galacticraft.mod.events.SleepPlatformHooks;
import dev.galacticraft.mod.events.GCSleepEventHandlers;
import net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent;
import net.neoforged.neoforge.event.entity.player.CanContinueSleepingEvent;
import net.neoforged.neoforge.event.entity.player.PlayerWakeUpEvent;
import net.neoforged.neoforge.common.ItemAbilities;

import java.util.Set;
import net.minecraft.world.level.storage.LevelResource;

/** NeoForge loader adapter for the shared Galacticraft initialization. */
@Mod(GalacticraftCommon.MOD_ID)
public final class GalacticraftNeoForge {
    public GalacticraftNeoForge(IEventBus modBus, ModContainer container) {
        GcApiEntityAttributes.register(modBus);
        BuiltInAddonRegistries.register(modBus);
        BuiltInRocketRegistries.register(modBus);
        GalacticraftCommon.init();
        OxygenTankExtractor.register((inventory, slot, amount) -> {
            var stack = inventory.getItem(slot);
            var handler = stack.getCapability(Capabilities.FluidHandler.ITEM);
            if (handler == null) return false;
            int requested = (int) Math.min(Integer.MAX_VALUE, Math.max(1L, (amount + 80L) / 81L));
            FluidStack drained = handler.drain(new FluidStack(Gases.OXYGEN, requested), IFluidHandler.FluidAction.EXECUTE);
            return drained.getAmount() > 0;
        });
        modBus.addListener(this::registerDataPackRegistries);
        modBus.addListener(this::registerGases);
        modBus.addListener(this::registerEntityDataSerializers);
        modBus.addListener(this::registerEntityAttributes);
        modBus.addListener(this::registerSpawnPlacements);
        modBus.addListener(this::registerCapabilities);
        modBus.addListener(GCAttachments::register);
        modBus.addListener((RegisterEvent event) -> GCRegistry.registerAll(event));
        prepareEagerContentConstruction();
        GCBlockPlatformHooks.setRegistration(() -> {
            FireBlock fire = (FireBlock) Blocks.FIRE;
            fire.setFlammable(GCBlocks.FUEL, 80, 130);
            fire.setFlammable(GCBlocks.CRUDE_OIL, 60, 100);
            fire.setFlammable(GCBlocks.CAVERNOUS_VINES, 15, 60);
            fire.setFlammable(GCBlocks.CAVERNOUS_VINES_PLANT, 15, 60);
        });
        ItemFluidTransfer.register((inventory, slotIndex, tank) -> {
            var stack = inventory.getItem(slotIndex);
            if (stack.isEmpty() || tank.isEmpty()) return;
            IFluidHandler handler = stack.getCapability(Capabilities.FluidHandler.ITEM);
            if (handler == null) return;
            FluidStack offered = new FluidStack(tank.getResource(), (int) Math.min(Integer.MAX_VALUE, tank.getAmount() / 81L));
            offered.applyComponents(tank.getComponents());
            int accepted = handler.fill(offered, IFluidHandler.FluidAction.EXECUTE);
            if (accepted > 0) tank.extract(accepted * 81L);
        });
        CreativeTabPlatformHooks.setSpawnEggRegistration(() -> {});
        ItemStoragePull.register((level, pos, direction, target, limit) -> {
            var source = Capabilities.ItemHandler.BLOCK.getCapability(
                    level, pos.relative(direction), null, null, direction.getOpposite());
            if (source == null) return 0;
            long moved = 0;
            for (int slot = 0; slot < source.getSlots() && moved < limit; slot++) {
                var available = source.extractItem(slot, (int) Math.min(Integer.MAX_VALUE, limit - moved), true);
                if (available.isEmpty()) continue;
                long accepted = target.tryInsert(available.getItem(), available.getComponentsPatch(), available.getCount());
                if (accepted <= 0) continue;
                var extracted = source.extractItem(slot, (int) accepted, false);
                moved += target.insert(extracted.getItem(), extracted.getComponentsPatch(), extracted.getCount());
            }
            return moved;
        });
        modBus.addListener(this::buildCreativeTabContents);
        VillagerPlatformHooks.setTrades(() -> {});
        NeoForge.EVENT_BUS.addListener(this::onVillagerTrades);
        SleepPlatformHooks.setRegistration(() -> {});
        NeoForge.EVENT_BUS.addListener(this::onCanPlayerSleep);
        NeoForge.EVENT_BUS.addListener(this::onCanContinueSleeping);
        NeoForge.EVENT_BUS.addListener(this::onPlayerWakeUp);
        NeoForge.EVENT_BUS.addListener(this::onBlockToolModification);
        initializeApi();
        BuiltinObjects.register();
        BuiltInRocketRegistries.initialize();
        new Galacticraft().onInitialize();
        if (FMLEnvironment.dist == Dist.CLIENT) GCNeoForgeClient.init(modBus);
    }

    private void onCanPlayerSleep(CanPlayerSleepEvent event) {
        var player = event.getEntity();
        var state = event.getState();
        if (player.isInCryoSleep() && state.getBlock() instanceof dev.galacticraft.mod.content.block.special.CryogenicChamberPart) {
            event.setProblem(null);
            return;
        }
        var problem = GCSleepEventHandlers.sleepInSpace(player, event.getPos());
        if (problem != null) event.setProblem(problem);
    }

    private void onCanContinueSleeping(CanContinueSleepingEvent event) {
        if (event.getEntity().isInCryoSleep()) event.setContinueSleeping(true);
    }

    private void onPlayerWakeUp(PlayerWakeUpEvent event) {
        event.getEntity().getSleepingPos().ifPresent(pos ->
                GCSleepEventHandlers.onWakeFromCryoSleep(event.getEntity(), pos));
    }

    private void onVillagerTrades(VillagerTradesEvent event) {
        if (event.getType() != GCVillagerProfessions.LUNAR_CARTOGRAPHER) return;
        event.getTrades().forEach(GCVillagerProfessions::populateTrades);
    }

    private void buildCreativeTabContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            GCItems.spawnEggs().forEach(event::accept);
        }
    }

    private void onBlockToolModification(BlockEvent.BlockToolModificationEvent event) {
        if (event.getItemAbility() != ItemAbilities.SHOVEL_FLATTEN) return;
        if (event.getState().is(GCBlocks.MOON_TURF) || event.getState().is(GCBlocks.MOON_DIRT)) {
            event.setFinalState(GCBlocks.MOON_DIRT_PATH.defaultBlockState());
        }
    }

    /**
     * The shared API exposes concrete content instances. These vanilla value
     * registries acquire intrusive holders or direct holder registrations during construction,
     * before our RegisterEvent queues can bind them. Limit the compatibility window
     * to exactly those registries; RegisterEvent freezes each one again after binding.
     */
    private static void prepareEagerContentConstruction() {
        unfreeze(BuiltInRegistries.BLOCK);
        unfreeze(BuiltInRegistries.ITEM);
        unfreeze(BuiltInRegistries.FLUID);
        unfreeze(BuiltInRegistries.ENTITY_TYPE);
        unfreeze(BuiltInRegistries.BLOCK_ENTITY_TYPE);
        unfreeze(BuiltInRegistries.ARMOR_MATERIAL);
        unfreeze(BuiltInRegistries.CHUNK_GENERATOR);
        unfreeze(BuiltInRegistries.ITEM_SUB_PREDICATE_TYPE);
        unfreeze(BuiltInRegistries.CREATIVE_MODE_TAB);
        unfreeze(BuiltInRegistries.RECIPE_TYPE);
        unfreeze(BuiltInRegistries.RECIPE_SERIALIZER);
        unfreeze(BuiltInRegistries.MENU);
        unfreeze(BuiltInRegistries.SOUND_EVENT);
        unfreeze(BuiltInRegistries.CUSTOM_STAT);
        unfreeze(BuiltInRegistries.FEATURE);
        unfreeze(BuiltInRegistries.CARVER);
        unfreeze(BuiltInRegistries.MATERIAL_RULE);
        unfreeze(BuiltInRegistries.VILLAGER_TYPE);
        unfreeze(BuiltInRegistries.VILLAGER_PROFESSION);
        unfreeze(BuiltInRegistries.DATA_COMPONENT_TYPE);
        unfreeze(BuiltInRegistries.ATTRIBUTE);
        unfreeze(BuiltInRegistries.COMMAND_ARGUMENT_TYPE);
        unfreeze(BuiltInRegistries.PARTICLE_TYPE);
        unfreeze(BuiltInRegistries.STRUCTURE_PIECE);
        unfreeze(BuiltInRegistries.STRUCTURE_TYPE);
        unfreeze(BuiltInRegistries.TRIGGER_TYPES);
    }

    private static void unfreeze(Registry<?> registry) {
        if (registry instanceof MappedRegistry<?> mapped) mapped.unfreeze();
    }

    /** Initializes the API entrypoint work normally invoked from Fabric metadata. */
    private void initializeApi() {
        GCApiCommands.registerCommands();
        GcApiEntityAttributes.init();
        DynamicDimensionLoadCallback.register((server, loader) ->
                ((SatelliteAccessor) server).galacticraft$loadSatellites(loader));
        // A physical client installs the real S2C handlers from GCNeoForgeClient. Registering
        // no-op payload handlers here would win instead and silently discard live sync packets.
        GCApiPackets.register(FMLEnvironment.dist == Dist.DEDICATED_SERVER);
        GCApiServerPacketReceivers.register();
        NeoForge.EVENT_BUS.addListener(this::onServerStarted);
        NeoForge.EVENT_BUS.addListener(this::onAddReloadListeners);
    }

    private void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(dev.galacticraft.mod.data.OxygenBlockDataManager.INSTANCE);
    }

    private void onServerStarted(ServerStartedEvent event) {
        ServerLevel overworld = event.getServer().overworld();
        GalacticraftAPI.currentWorldSaveDirectory = overworld.getServer().getWorldPath(LevelResource.ROOT);
        Constant.LOGGER.info("World Save Directory: {}", GalacticraftAPI.currentWorldSaveDirectory);
    }

    /**
     * NeoForge does not consume Fabric's {@code DynamicRegistries} registrations.
     * Register these explicitly so the server creates and synchronizes Galacticraft's
     * datapack registries before it builds a world registry access.
     */
    private void registerDataPackRegistries(DataPackRegistryEvent.NewRegistry event) {
        event.dataPackRegistry(AddonRegistries.CELESTIAL_BODY, CelestialBody.DIRECT_CODEC, CelestialBody.DIRECT_CODEC);
        event.dataPackRegistry(AddonRegistries.GALAXY, Galaxy.DIRECT_CODEC, Galaxy.DIRECT_CODEC);
        event.dataPackRegistry(AddonRegistries.CELESTIAL_TELEPORTER, CelestialTeleporter.DIRECT_CODEC, CelestialTeleporter.DIRECT_CODEC);

        event.dataPackRegistry(RocketRegistries.ROCKET_CONE, RocketCone.DIRECT_CODEC, RocketCone.DIRECT_CODEC);
        event.dataPackRegistry(RocketRegistries.ROCKET_BODY, RocketBody.DIRECT_CODEC, RocketBody.DIRECT_CODEC);
        event.dataPackRegistry(RocketRegistries.ROCKET_FIN, RocketFin.DIRECT_CODEC, RocketFin.DIRECT_CODEC);
        event.dataPackRegistry(RocketRegistries.ROCKET_BOOSTER, RocketBooster.DIRECT_CODEC, RocketBooster.DIRECT_CODEC);
        event.dataPackRegistry(RocketRegistries.ROCKET_ENGINE, RocketEngine.DIRECT_CODEC, RocketEngine.DIRECT_CODEC);
        event.dataPackRegistry(RocketRegistries.ROCKET_UPGRADE, RocketUpgrade.DIRECT_CODEC, RocketUpgrade.DIRECT_CODEC);
    }

    private void registerGases(RegisterEvent event) {
        GCNeoForgeFluidTypes.register(event);
        event.register(Registries.FLUID, helper -> Gases.init());
    }

    private void registerEntityDataSerializers(RegisterEvent event) {
        event.register(Registries.CHUNK_GENERATOR, Constant.id("satellite"), () -> SatelliteChunkGenerator.CODEC);
        event.register(Registries.COMMAND_ARGUMENT_TYPE, Constant.id("registry"), () -> {
            var serializer = GCApiCommands.argumentSerializer();
            GCApiCommands.registerArgumentClass(serializer);
            return serializer;
        });
        event.register(NeoForgeRegistries.Keys.ENTITY_DATA_SERIALIZERS,
                ResourceLocation.fromNamespaceAndPath(GalacticraftCommon.MOD_ID, "rocket_data"),
                () -> GCEntityDataSerializers.ROCKET_DATA);
        event.register(NeoForgeRegistries.Keys.ENTITY_DATA_SERIALIZERS,
                ResourceLocation.fromNamespaceAndPath(GalacticraftCommon.MOD_ID, "launch_stage"),
                () -> GCEntityDataSerializers.LAUNCH_STAGE);
        event.register(Registries.POINT_OF_INTEREST_TYPE,
                ResourceLocation.fromNamespaceAndPath(GalacticraftCommon.MOD_ID, "lunar_cartographer"),
                () -> new PoiType(Set.copyOf(GCBlocks.LUNAR_CARTOGRAPHY_TABLE.getStateDefinition().getPossibleStates()), 1, 1));
    }

    private void registerEntityAttributes(EntityAttributeCreationEvent event) {
        GCEntityTypes.registerAttributes((type, attributes) -> event.put(type, attributes.build()));
    }

    private void registerSpawnPlacements(RegisterSpawnPlacementsEvent event) {
        event.register(GCEntityTypes.EVOLVED_ZOMBIE, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                Monster::checkMonsterSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(GCEntityTypes.EVOLVED_CREEPER, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                Monster::checkMonsterSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(GCEntityTypes.EVOLVED_SKELETON, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                Monster::checkMonsterSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(GCEntityTypes.EVOLVED_SPIDER, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                Monster::checkMonsterSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(GCEntityTypes.EVOLVED_ENDERMAN, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                Monster::checkMonsterSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(GCEntityTypes.EVOLVED_WITCH, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                Monster::checkMonsterSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }

    /** Registers Galacticraft's non-MachineLib storages through native NeoForge capabilities. */
    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, GCBlockEntityTypes.WIRE_T1,
                (wire, direction) -> direction == null || wire.canConnect(direction) ? wire.getInsertable() : null);
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, GCBlockEntityTypes.WIRE_T2,
                (wire, direction) -> direction == null || wire.canConnect(direction) ? wire.getInsertable() : null);
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, GCBlockEntityTypes.ASTRO_MINER_BASE,
                (base, direction) -> new ExposedEnergyStorageNeoForge(base.getEnergyStorage(),
                        base.getEnergyStorage().externalInsertionRate(), 0));
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, GCBlockEntityTypes.PARACHEST,
                (chest, direction) -> chest.tank);
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, GCBlockEntityTypes.GLASS_FLUID_PIPE,
                (pipe, direction) -> direction == null || pipe.canConnect(direction) ? pipe.getInsertable() : null);

        event.registerItem(Capabilities.EnergyStorage.ITEM,
                (stack, context) -> GCItems.BATTERY.createEnergyStorage(stack), GCItems.BATTERY);
        event.registerItem(Capabilities.EnergyStorage.ITEM,
                (stack, context) -> ((InfiniteBatteryItem) GCItems.INFINITE_BATTERY).createEnergyStorage(),
                GCItems.INFINITE_BATTERY);
        event.registerItem(Capabilities.FluidHandler.ITEM, (stack, context) -> {
            long capacity = ((OxygenTankItem) stack.getItem()).capacity;
            return new SingleVariantFixedItemBackedFluidStorage(stack, capacity, capacity, capacity,
                    new FluidStack(Gases.OXYGEN, 1));
        }, GCItems.SMALL_OXYGEN_TANK, GCItems.MEDIUM_OXYGEN_TANK, GCItems.LARGE_OXYGEN_TANK);
        event.registerItem(Capabilities.FluidHandler.ITEM,
                (stack, context) -> new InfiniteOxygenFluidHandler(stack), GCItems.INFINITE_OXYGEN_TANK);
        event.registerItem(Capabilities.FluidHandler.ITEM,
                (stack, context) -> new CanisterFluidHandler(stack), GCItems.FLUID_CANISTER);
    }
}
