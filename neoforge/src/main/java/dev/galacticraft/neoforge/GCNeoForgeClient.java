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

import dev.galacticraft.mod.client.gui.screen.ingame.*;
import dev.galacticraft.api.client.tabs.InventoryTabRegistry;
import dev.galacticraft.api.component.GCDataComponents;
import dev.galacticraft.api.fluid.FluidData;
import dev.galacticraft.api.gas.Gases;
import dev.galacticraft.mod.client.GCKeyBinds;
import dev.galacticraft.mod.client.network.CapeClientNet;
import dev.galacticraft.mod.client.gui.overlay.*;
import dev.galacticraft.mod.client.render.dimension.duststorm.ClientDustStorms;
import dev.galacticraft.mod.client.render.dimension.solarflare.ClientSolarFlares;
import dev.galacticraft.mod.client.render.dimension.AsteroidDimensionEffects;
import dev.galacticraft.mod.client.render.dimension.MarsDimensionEffects;
import dev.galacticraft.mod.client.render.dimension.MercuryDimensionEffects;
import dev.galacticraft.mod.client.render.dimension.MoonDimensionEffects;
import dev.galacticraft.mod.client.render.dimension.VenusDimensionEffects;
import dev.galacticraft.mod.client.render.dimension.AsteroidSkyRenderer;
import dev.galacticraft.mod.client.render.dimension.MarsSkyRenderer;
import dev.galacticraft.mod.client.render.dimension.MercurySkyRenderer;
import dev.galacticraft.mod.client.render.dimension.MoonSkyRenderer;
import dev.galacticraft.mod.client.render.dimension.SatelliteSkyRenderer;
import dev.galacticraft.mod.client.render.dimension.VenusSkyRenderer;
import dev.galacticraft.mod.client.render.dimension.VenusWeatherRenderer;
import dev.galacticraft.mod.client.sounds.GCSoundManager;
import dev.galacticraft.mod.client.sounds.RocketSound;
import dev.galacticraft.mod.content.entity.vehicle.RocketEntity;
import dev.galacticraft.mod.events.RocketEvents;
import dev.galacticraft.api.rocket.LaunchStage;
import dev.galacticraft.machinelib.client.api.event.MachineStatusEvents;
import dev.galacticraft.mod.client.model.GCModelLoader;
import dev.galacticraft.mod.client.model.GCRenderTypes;
import dev.galacticraft.mod.client.model.types.UnbakedObjModel;
import dev.galacticraft.mod.client.particle.*;
import dev.galacticraft.mod.client.render.block.entity.*;
import dev.galacticraft.mod.client.render.entity.*;
import dev.galacticraft.mod.client.render.FootprintRenderer;
import dev.galacticraft.mod.client.render.entity.feature.OxygenMaskRenderLayer;
import dev.galacticraft.mod.client.render.entity.feature.OxygenTanksRenderLayer;
import dev.galacticraft.mod.client.render.entity.feature.ParrotOxygenGearRenderLayer;
import dev.galacticraft.mod.client.render.entity.feature.PetOxygenMaskRenderLayer;
import dev.galacticraft.mod.client.render.entity.feature.PetOxygenTanksRenderLayer;
import dev.galacticraft.mod.client.render.entity.model.GCEntityModelLayer;
import dev.galacticraft.mod.client.render.entity.rocket.RocketEntityRenderer;
import dev.galacticraft.mod.client.render.rocket.GalacticraftRocketPartRenderers;
import dev.galacticraft.mod.content.GCBlockEntityTypes;
import dev.galacticraft.mod.content.GCBlocks;
import dev.galacticraft.mod.content.GCEntityTypes;
import dev.galacticraft.mod.content.GCFluids;
import dev.galacticraft.mod.content.ClientCannedFoodTooltip;
import dev.galacticraft.mod.content.CannedFoodTooltip;
import dev.galacticraft.mod.content.block.environment.FallenMeteorBlock;
import dev.galacticraft.mod.content.item.FluidCanisterItem;
import dev.galacticraft.mod.content.item.GCItems;
import dev.galacticraft.mod.misc.cape.CapeMode;
import dev.galacticraft.mod.misc.cape.CapeRegistry;
import dev.galacticraft.mod.misc.cape.CapesClientRole;
import dev.galacticraft.mod.misc.cape.CapesLoader;
import dev.galacticraft.mod.misc.cape.ClientCapePrefs;
import dev.galacticraft.mod.network.c2s.OpenGcInventoryPayload;
import dev.galacticraft.mod.network.c2s.OpenRocketPayload;
import dev.galacticraft.mod.particle.GCParticleTypes;
import dev.galacticraft.mod.screen.GCMenuTypes;
import dev.galacticraft.mod.screen.GCPlayerInventoryMenu;
import dev.galacticraft.mod.screen.RocketMenu;
import dev.galacticraft.mod.client.util.ColorUtil;
import dev.architectury.networking.NetworkManager;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.util.FastColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.entity.EntityType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.minecraft.client.particle.SplashParticle;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.minecraft.client.Minecraft;
import dev.galacticraft.neoforge.fluid.GCNeoForgeFluidTypes;
import dev.galacticraft.neoforge.client.GCNeoModelReloadListener;
import dev.galacticraft.neoforge.client.GCNeoItemRenderer;
import dev.galacticraft.neoforge.client.GCNeoBlockOutlineRenderer;
import dev.galacticraft.neoforge.client.GCNeoDimensionEffects;
import dev.galacticraft.neoforge.client.model.GCNeoDynamicModels;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

/** Native NeoForge client registrations replacing Fabric client entrypoints. */
public final class GCNeoForgeClient {
    public static void init(IEventBus modBus) {
        GCModelLoader.registerModelType(UnbakedObjModel.TYPE);
        GalacticraftRocketPartRenderers.register();
        modBus.addListener(GCNeoForgeClient::registerScreens);
        modBus.addListener(GCNeoForgeClient::registerRenderers);
        modBus.addListener(GCNeoForgeClient::addRenderLayers);
        modBus.addListener(GCNeoForgeClient::registerLayers);
        modBus.addListener(GCNeoForgeClient::registerParticles);
        modBus.addListener(GCNeoForgeFluidTypes::registerClientExtensions);
        modBus.addListener(GCNeoForgeClient::registerReloadListeners);
        modBus.addListener(GCNeoForgeClient::registerKeyMappings);
        modBus.addListener(GCNeoForgeClient::clientSetup);
        modBus.addListener(GCNeoForgeClient::registerBlockColors);
        modBus.addListener(GCNeoForgeClient::registerItemColors);
        modBus.addListener(GCNeoForgeClient::registerTooltipComponents);
        modBus.addListener(GCNeoForgeClient::registerShaders);
        modBus.addListener(GCNeoForgeClient::registerDimensionEffects);
        modBus.addListener(GCNeoDynamicModels::modifyBakingResult);
        modBus.addListener(GCNeoForgeClient::registerClientExtensions);
        NeoForge.EVENT_BUS.addListener(GCNeoForgeClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(GCNeoForgeClient::onRenderGui);
        NeoForge.EVENT_BUS.addListener(GCNeoForgeClient::onLogin);
        NeoForge.EVENT_BUS.addListener(FootprintRenderer::renderFootprints);
        NeoForge.EVENT_BUS.addListener(GCNeoBlockOutlineRenderer::render);
        CapesClientRole.ensureLoadedAsync();
        CapeRegistry.bootstrap();
        CapesLoader.loadAsync();
        InventoryTabRegistry.INSTANCE.register(GCItems.OXYGEN_MASK.getDefaultInstance(), () -> NetworkManager.sendToServer(new OpenGcInventoryPayload()), GCPlayerInventoryMenu.class);
        InventoryTabRegistry.INSTANCE.register(GCItems.ROCKET.getDefaultInstance(), () -> NetworkManager.sendToServer(new OpenRocketPayload()), player -> player.getVehicle() instanceof RocketEntity, RocketMenu.class);
        RocketEvents.STAGE_CHANGED.register(GCNeoForgeClient::onRocketStageChanged);
        MachineStatusEvents.MACHINE_STATUS_CHANGED.register(GCSoundManager::onStatusChanged);
    }

    private static void registerClientExtensions(RegisterClientExtensionsEvent event) {
        IClientItemExtensions extension = new IClientItemExtensions() {
            @Override public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return GCNeoItemRenderer.INSTANCE;
            }
        };
        event.registerItem(extension, GCItems.ROCKET, GCItems.ASTRO_MINER, GCItems.CARGO_ROCKET, GCBlocks.PARACHEST.asItem());
        event.registerItem(extension, GCItems.FLAGS.colorMap().values().toArray(net.minecraft.world.item.Item[]::new));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void addRenderLayers(EntityRenderersEvent.AddLayers event) {
        for (var skin : event.getSkins()) {
            LivingEntityRenderer renderer = event.getSkin(skin);
            renderer.addLayer(new OxygenMaskRenderLayer(renderer));
            renderer.addLayer(new OxygenTanksRenderLayer(renderer));
            renderer.addLayer(new ParrotOxygenGearRenderLayer(renderer));
        }
        LivingEntityRenderer wolf = (LivingEntityRenderer) event.getRenderer(EntityType.WOLF);
        wolf.addLayer(new PetOxygenMaskRenderLayer(wolf));
        wolf.addLayer(new PetOxygenTanksRenderLayer(wolf));
        LivingEntityRenderer cat = (LivingEntityRenderer) event.getRenderer(EntityType.CAT);
        cat.addLayer(new PetOxygenMaskRenderLayer(cat));
        cat.addLayer(new PetOxygenTanksRenderLayer(cat));
        LivingEntityRenderer parrot = (LivingEntityRenderer) event.getRenderer(EntityType.PARROT);
        parrot.addLayer(new ParrotOxygenGearRenderLayer(parrot));
    }

    private static void registerDimensionEffects(RegisterDimensionSpecialEffectsEvent event) {
        event.register(dev.galacticraft.mod.Constant.id("moon"), new GCNeoDimensionEffects(MoonDimensionEffects.INSTANCE, MoonSkyRenderer.INSTANCE::render, null));
        event.register(dev.galacticraft.mod.Constant.id("venus"), new GCNeoDimensionEffects(VenusDimensionEffects.INSTANCE, VenusSkyRenderer.INSTANCE::render, VenusWeatherRenderer.INSTANCE::render));
        event.register(dev.galacticraft.mod.Constant.id("mars"), new GCNeoDimensionEffects(MarsDimensionEffects.INSTANCE, MarsSkyRenderer.INSTANCE::render, null));
        event.register(dev.galacticraft.mod.Constant.id("mercury"), new GCNeoDimensionEffects(MercuryDimensionEffects.INSTANCE, MercurySkyRenderer.INSTANCE::render, null));
        event.register(dev.galacticraft.mod.Constant.id("asteroid"), new GCNeoDimensionEffects(AsteroidDimensionEffects.INSTANCE, AsteroidSkyRenderer.INSTANCE::render, null));
        event.register(dev.galacticraft.mod.Constant.id("satellite"), new GCNeoDimensionEffects(AsteroidDimensionEffects.INSTANCE, SatelliteSkyRenderer.INSTANCE::render, null));
    }

    private static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemBlockRenderTypes.setRenderLayer(GCBlocks.TIN_LADDER, RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(GCBlocks.WALKWAY, RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(GCBlocks.WIRE_WALKWAY, RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(GCBlocks.HEAVY_WIRE_WALKWAY, RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(GCBlocks.FLUID_PIPE_WALKWAY, RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(GCBlocks.IRON_GRATING, RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(GCBlocks.GLOWSTONE_TORCH, RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(GCBlocks.GLOWSTONE_WALL_TORCH, RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(GCBlocks.UNLIT_TORCH, RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(GCBlocks.UNLIT_WALL_TORCH, RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(GCBlocks.UNLIT_SOUL_TORCH, RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(GCBlocks.UNLIT_SOUL_WALL_TORCH, RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(GCBlocks.GLOWSTONE_LANTERN, RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(GCBlocks.UNLIT_LANTERN, RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(GCBlocks.UNLIT_SOUL_LANTERN, RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(GCBlocks.CAVERNOUS_VINES, RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(GCBlocks.CAVERNOUS_VINES_PLANT, RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(GCBlocks.OLIVINE_CLUSTER, RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(GCBlocks.MERCURY_CRYSTAL_CLUSTER, RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(GCBlocks.MOON_CHEESE_LEAVES, RenderType.cutoutMipped());
            ItemBlockRenderTypes.setRenderLayer(GCBlocks.VACUUM_GLASS, RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(GCBlocks.CLEAR_VACUUM_GLASS, RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(GCBlocks.STRONG_VACUUM_GLASS, RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(GCBlocks.CRYOGENIC_CHAMBER, RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(GCBlocks.CRYOGENIC_CHAMBER_PART, RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(GCBlocks.PLAYER_TRANSPORT_TUBE, RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(GCBlocks.OLIVINE_GLASS, RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(GCBlocks.OLIVINE_GLASS_PANE, RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(GCBlocks.MOON_GLASS, RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(GCBlocks.MOON_GLASS_PANE, RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(GCBlocks.MOON_WEED, RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(GCBlocks.MOON_SHRUBS, RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(GCBlocks.MOON_TANGLE, RenderType.cutout());
            for (Block pipe : GCBlocks.GLASS_FLUID_PIPES.values()) ItemBlockRenderTypes.setRenderLayer(pipe, RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(GCBlocks.GLASS_FLUID_PIPE, RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(GCFluids.FUEL, RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(GCFluids.FLOWING_FUEL, RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(GCFluids.SULFURIC_ACID, RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(GCFluids.FLOWING_SULFURIC_ACID, RenderType.translucent());
            ItemProperties.register(GCItems.FLUID_CANISTER, FluidCanisterItem.FILL_LEVEL, (stack, world, entity, seed) -> {
                FluidData data = stack.get(GCDataComponents.FLUID_DATA);
                if (data == null || data.amount() <= 0) return 0.0F;
                double percentage = (double) data.amount() / ((FluidCanisterItem) stack.getItem()).capacity;
                return (float) (Math.ceil(percentage * 6.0) / 6.0);
            });
        });
    }

    private static void registerBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register((state, world, pos, tintIndex) -> FallenMeteorBlock.colorMultiplier(state, world, pos), GCBlocks.FALLEN_METEOR);
    }

    private static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, layer) -> layer != 1 ? -1 : ColorUtil.getRainbowOpaque(), GCItems.INFINITE_BATTERY, GCItems.INFINITE_OXYGEN_TANK);
        event.register((stack, layer) -> layer != 1 ? -1 : FastColor.ARGB32.opaque(stack.getOrDefault(GCDataComponents.COLOR, 0xFFFFFF)), GCItems.CANNED_FOOD);
        event.register((stack, layer) -> {
            if (layer != 1) return -1;
            FluidData data = stack.get(GCDataComponents.FLUID_DATA);
            if (data == null || data.variant().fluid() == Fluids.EMPTY) return -1;
            int tint = IClientFluidTypeExtensions.of(data.variant().fluid()).getTintColor();
            if (tint != -1 && tint != 0xFFFFFFFF) return tint;
            if (data.variant().fluid().isSame(Gases.METHANE)) return 0xFF80FFFF;
            if (data.variant().fluid().isSame(GCFluids.LIQUID_OXYGEN)) return 0xFFD76453;
            return tint;
        }, GCItems.FLUID_CANISTER);
    }

    private static void registerTooltipComponents(RegisterClientTooltipComponentFactoriesEvent event) {
        event.register(CannedFoodTooltip.class, ClientCannedFoodTooltip::new);
    }

    private static void registerShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), dev.galacticraft.mod.Constant.id("rendertype_bubble"), com.mojang.blaze3d.vertex.DefaultVertexFormat.NEW_ENTITY), GCRenderTypes::setBubbleShader);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Failed to load Galacticraft bubble shader", exception);
        }
    }

    private static void onLogin(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingIn event) {
        ClientCapePrefs prefs = ClientCapePrefs.load();
        CapeClientNet.sendSelectionIfOnline(prefs.mode, prefs.mode == CapeMode.GC ? prefs.gcCapeId : null);
    }

    private static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(GCKeyBinds.OPEN_CELESTIAL_SCREEN);
        event.register(GCKeyBinds.OPEN_ROCKET_INVENTORY);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        GCKeyBinds.handleKeybinds(Minecraft.getInstance());
        OxygenOverlay.clientTick();
        LanderOverlay.clientTick();
        ClientDustStorms.clientTick();
        ClientSolarFlares.clientTick();
        var level = Minecraft.getInstance().level;
        if (level != null) {
            var manager = level.galacticraft$getFootprintManager();
            manager.getFootprints().forEach((packedPos, footprints) -> manager.tick(level, packedPos));
        }
    }

    private static void onRenderGui(RenderGuiEvent.Post event) {
        OxygenOverlay.onHudRender(event.getGuiGraphics(), event.getPartialTick());
        DustStormOverlay.onHudRender(event.getGuiGraphics(), event.getPartialTick());
        SolarFlareOverlay.onHudRender(event.getGuiGraphics(), event.getPartialTick());
        RocketOverlay.onHudRender(event.getGuiGraphics(), event.getPartialTick());
        LanderOverlay.onRenderHud(event.getGuiGraphics(), event.getPartialTick());
        CountdownOverlay.renderCountdown(event.getGuiGraphics(), event.getPartialTick());
    }

    private static void onRocketStageChanged(dev.galacticraft.api.rocket.entity.Rocket rocket, LaunchStage oldStage) {
        if (!(rocket instanceof RocketEntity entity)) return;
        LaunchStage stage = entity.getLaunchStage();
        if (stage == LaunchStage.IGNITED || (stage == LaunchStage.LAUNCHED && oldStage == LaunchStage.IDLE)) {
            Minecraft.getInstance().getSoundManager().play(new RocketSound(entity));
        }
    }

    private static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(GCNeoModelReloadListener.INSTANCE);
    }

    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(GCMenuTypes.BASIC_SOLAR_PANEL, BasicSolarPanelScreen::new);
        event.register(GCMenuTypes.ADVANCED_SOLAR_PANEL, AdvancedSolarPanelScreen::new);
        event.register(GCMenuTypes.COAL_GENERATOR, CoalGeneratorScreen::new);
        event.register(GCMenuTypes.CIRCUIT_FABRICATOR, CircuitFabricatorScreen::new);
        event.register(GCMenuTypes.REFINERY, RefineryScreen::new);
        event.register(GCMenuTypes.ELECTRIC_FURNACE, ElectricFurnaceScreen::new);
        event.register(GCMenuTypes.ELECTRIC_ARC_FURNACE, ElectricArcFurnaceScreen::new);
        event.register(GCMenuTypes.COMPRESSOR, CompressorScreen::new);
        event.register(GCMenuTypes.ELECTRIC_COMPRESSOR, ElectricCompressorScreen::new);
        event.register(GCMenuTypes.ENERGY_STORAGE_MODULE, EnergyStorageModuleScreen::new);
        event.register(GCMenuTypes.ENERGY_STORAGE_CLUSTER, EnergyStorageClusterScreen::new);
        event.register(GCMenuTypes.OXYGEN_COLLECTOR, OxygenCollectorScreen::new);
        event.register(GCMenuTypes.OXYGEN_COMPRESSOR, OxygenCompressorScreen::new);
        event.register(GCMenuTypes.FOOD_CANNER, FoodCannerScreen::new);
        event.register(GCMenuTypes.OXYGEN_DECOMPRESSOR, OxygenDecompressorScreen::new);
        event.register(GCMenuTypes.PLAYER_INV_GC, GCPlayerInventoryScreen::new);
        event.register(GCMenuTypes.PET_INV_GC, GCPetInventoryScreen::new);
        event.register(GCMenuTypes.OXYGEN_BUBBLE_DISTRIBUTOR, OxygenBubbleDistributorScreen::new);
        event.register(GCMenuTypes.OXYGEN_STORAGE_MODULE, OxygenStorageModuleScreen::new);
        event.register(GCMenuTypes.OXYGEN_SEALER, OxygenSealerScreen::new);
        event.register(GCMenuTypes.FUEL_LOADER, FuelLoaderScreen::new);
        event.register(GCMenuTypes.CARGO_LOADER, CargoLoaderScreen::new);
        event.register(GCMenuTypes.CARGO_UNLOADER, CargoUnloaderScreen::new);
        event.register(GCMenuTypes.FLUID_TANK, FluidTankScreen::new);
        event.register(GCMenuTypes.PAINTER, PainterScreen::new);
        event.register(GCMenuTypes.DECONSTRUCTOR, DeconstructorScreen::new);
        event.register(GCMenuTypes.AIRLOCK_CONTROLLER_MENU, AirlockControllerScreen::new);
        event.register(GCMenuTypes.ROCKET_WORKBENCH, RocketWorkbenchScreen::new);
        event.register(GCMenuTypes.ROCKET, RocketInventoryScreen::new);
        event.register(GCMenuTypes.PARACHEST, ParachestScreen::new);
        event.register(GCMenuTypes.VEHICLE_INVENTORY, VehicleInventoryScreen::new);
        event.register(GCMenuTypes.ASTRO_MINER_BASE, AstroMinerBaseScreen::new);
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(GCEntityTypes.MOON_VILLAGER, MoonVillagerRenderer::new);
        event.registerEntityRenderer(GCEntityTypes.EVOLVED_ZOMBIE, EvolvedZombieEntityRenderer::new);
        event.registerEntityRenderer(GCEntityTypes.EVOLVED_CREEPER, EvolvedCreeperEntityRenderer::new);
        event.registerEntityRenderer(GCEntityTypes.EVOLVED_SKELETON, EvolvedSkeletonEntityRenderer::new);
        event.registerEntityRenderer(GCEntityTypes.EVOLVED_SPIDER, EvolvedSpiderEntityRenderer::new);
        event.registerEntityRenderer(GCEntityTypes.EVOLVED_ENDERMAN, EvolvedEndermanEntityRenderer::new);
        event.registerEntityRenderer(GCEntityTypes.EVOLVED_WITCH, EvolvedWitchEntityRenderer::new);
        event.registerEntityRenderer(GCEntityTypes.EVOLVED_EVOKER, EvolvedEvokerEntityRenderer::new);
        event.registerEntityRenderer(GCEntityTypes.EVOLVED_PILLAGER, EvolvedPillagerEntityRenderer::new);
        event.registerEntityRenderer(GCEntityTypes.EVOLVED_VINDICATOR, EvolvedVindicatorEntityRenderer::new);
        event.registerEntityRenderer(GCEntityTypes.GAZER, GazerEntityRenderer::new);
        event.registerEntityRenderer(GCEntityTypes.RUMBLER, RumblerEntityRenderer::new);
        event.registerEntityRenderer(GCEntityTypes.COMET_CUBE, CometCubeEntityRenderer::new);
        event.registerEntityRenderer(GCEntityTypes.OLI_GRUB, OliGrubEntityRenderer::new);
        event.registerEntityRenderer(GCEntityTypes.GREY, GreyEntityRenderer::new);
        event.registerEntityRenderer(GCEntityTypes.ARCH_GREY, GreyEntityRenderer::arch);
        event.registerEntityRenderer(GCEntityTypes.FALLING_METEOR, FallingMeteorRenderer::new);
        event.registerEntityRenderer(GCEntityTypes.BUBBLE, BubbleEntityRenderer::new);
        event.registerEntityRenderer(GCEntityTypes.ROCKET, RocketEntityRenderer::new);
        event.registerEntityRenderer(GCEntityTypes.LANDER, LanderEntityRenderer::new);
        event.registerEntityRenderer(GCEntityTypes.BUGGY, BuggyRenderer::new);
        event.registerEntityRenderer(GCEntityTypes.ASTRO_MINER, AstroMinerRenderer::new);
        event.registerEntityRenderer(GCEntityTypes.CARGO_ROCKET, CargoRocketRenderer::new);
        event.registerEntityRenderer(GCEntityTypes.PARACHEST, ParachestRenderer::new);
        event.registerEntityRenderer(GCEntityTypes.THROWABLE_METEOR_CHUNK, ThrownItemRenderer::new);
        event.registerEntityRenderer(GCEntityTypes.SKELETON_BOSS, EvolvedSkeletonBossRenderer::new);
        event.registerEntityRenderer(GCEntityTypes.CREEPER_BOSS, CreeperBossRenderer::new);
        event.registerEntityRenderer(GCEntityTypes.SPIDER_BOSS, SpiderBossRenderer::new);
        event.registerBlockEntityRenderer(GCBlockEntityTypes.BASIC_SOLAR_PANEL, SolarPanelBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(GCBlockEntityTypes.ADVANCED_SOLAR_PANEL, SolarPanelBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(GCBlockEntityTypes.GLASS_FLUID_PIPE, FluidPipeBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(GCBlockEntityTypes.OXYGEN_BUBBLE_DISTRIBUTOR, BubbleDistributorRenderer::new);
        event.registerBlockEntityRenderer(GCBlockEntityTypes.ROCKET_WORKBENCH, RocketWorkbenchBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(GCBlockEntityTypes.ASTRO_MINER_BASE, AstroMinerBaseBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(GCBlockEntityTypes.FLAG, FlagBlockEntityRenderer::new);
    }

    private static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(GCEntityModelLayer.GAZER, dev.galacticraft.mod.client.model.entity.GazerEntityModel::createBodyLayer);
        event.registerLayerDefinition(GCEntityModelLayer.RUMBLER, dev.galacticraft.mod.client.model.entity.RumblerEntityModel::createBodyLayer);
        event.registerLayerDefinition(GCEntityModelLayer.COMET_CUBE, dev.galacticraft.mod.client.model.entity.CometCubeEntityModel::createBodyLayer);
        event.registerLayerDefinition(GCEntityModelLayer.OLI_GRUB, dev.galacticraft.mod.client.model.entity.OliGrubEntityModel::createBodyLayer);
        event.registerLayerDefinition(GCEntityModelLayer.GREY, dev.galacticraft.mod.client.model.entity.GreyEntityModel::createBodyLayer);
        event.registerLayerDefinition(GCEntityModelLayer.ARCH_GREY, dev.galacticraft.mod.client.model.entity.ArchGreyEntityModel::createBodyLayer);
        event.registerLayerDefinition(GCEntityModelLayer.LANDER, dev.galacticraft.mod.client.model.entity.LanderModel::createBodyLayer);
        event.registerLayerDefinition(GCEntityModelLayer.PARACHEST, dev.galacticraft.mod.client.render.entity.model.ParachestModel::createParachuteLayer);
        event.registerLayerDefinition(GCEntityModelLayer.MOON_VILLAGER, dev.galacticraft.mod.client.render.entity.model.MoonVillagerModel::createBodyLayer);
        event.registerLayerDefinition(GCEntityModelLayer.SKELETON_BOSS, dev.galacticraft.mod.client.model.entity.EvolvedSkeletonBossModel::createBodyLayer);
        event.registerLayerDefinition(GCEntityModelLayer.FLAG, FlagBlockEntityRenderer::createBodyLayer);
        event.registerLayerDefinition(GCEntityModelLayer.SOLAR_PANEL, SolarPanelBlockEntityRenderer::getTexturedModelData);
        event.registerLayerDefinition(GCEntityModelLayer.ROCKET_WORKBENCH, RocketWorkbenchBlockEntityRenderer::getTexturedModelData);
    }

    private static void registerParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(GCParticleTypes.DRIPPING_CRUDE_OIL, DrippingCrudeOilProvider::new);
        event.registerSpriteSet(GCParticleTypes.FALLING_CRUDE_OIL, FallingCrudeOilProvider::new);
        event.registerSpriteSet(GCParticleTypes.DRIPPING_FUEL, DrippingFuelProvider::new);
        event.registerSpriteSet(GCParticleTypes.FALLING_FUEL, FallingFuelProvider::new);
        event.registerSpriteSet(GCParticleTypes.DRIPPING_SULFURIC_ACID, DrippingSulfuricAcidProvider::new);
        event.registerSpriteSet(GCParticleTypes.FALLING_SULFURIC_ACID, FallingSulfuricAcidProvider::new);
        event.registerSpriteSet(GCParticleTypes.CRYOGENIC_PARTICLE, CryoFreezeParticle.Provider::new);
        event.registerSpriteSet(GCParticleTypes.LANDER_FLAME_PARTICLE, LanderParticle.Provider::new);
        event.registerSpriteSet(GCParticleTypes.SPARK_PARTICLE, SparksParticle.Provider::new);
        event.registerSpriteSet(GCParticleTypes.LAUNCH_SMOKE_PARTICLE, LaunchSmokeParticle.Provider::new);
        event.registerSpriteSet(GCParticleTypes.LAUNCH_FLAME, LaunchFlameParticle.Provider::new);
        event.registerSpriteSet(GCParticleTypes.LAUNCH_FLAME_LAUNCHED, LaunchFlameParticle.LaunchedProvider::new);
        event.registerSpriteSet(GCParticleTypes.ACID_VAPOR_PARTICLE, AcidVaporParticle.Provider::new);
        event.registerSpriteSet(GCParticleTypes.SPLASH_VENUS, SplashParticle.Provider::new);
    }

    private GCNeoForgeClient() {}
}
