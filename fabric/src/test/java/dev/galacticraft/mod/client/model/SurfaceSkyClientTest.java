package dev.galacticraft.mod.client.model;

import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.client.render.dimension.SpaceflightTransition;
import dev.galacticraft.mod.world.dimension.GCDimensions;
import dev.galacticraft.mod.util.DimensionTime;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/** Opt-in GPU/gallery check in a disposable world: -PsurfaceSkyClientTest -Pastrocraft=false. */
public final class SurfaceSkyClientTest implements ClientModInitializer {
    private record View(String name, ResourceKey<Level> level, long time, boolean earth, double altitude, int face) {
        View(String name, ResourceKey<Level> level, long time, boolean earth, double altitude) { this(name, level, time, earth, altitude, -1); }
        View(String name, ResourceKey<Level> level, long time, boolean earth) { this(name, level, time, earth, 0); }
    }
    private static final boolean ADDON = Boolean.getBoolean("galacticraft.addon.client-test");
    private static final ResourceKey<Level> PRIME = ResourceKey.create(Registries.DIMENSION,
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("galacticraft-test", "andromeda_prime"));
    private static final View[] VIEWS = ADDON ? new View[] {
            new View("addon-tier4", PRIME, 6000, false),
            new View("addon-sun", PRIME, 3000, false),
            new View("addon-high-descent", PRIME, 6000, false, 900),
            new View("addon-flight", GCDimensions.SOLAR_SYSTEM, 6000, false),
            new View("addon-planet", GCDimensions.SOLAR_SYSTEM, 6000, false),
            new View("addon-module-page", PRIME, 6000, false)
    } : new View[] {
            new View("moon-earth-day", GCDimensions.MOON, 6000, true),
            new View("moon-earth-dusk", GCDimensions.MOON, 12000, true),
            new View("moon-earth-night", GCDimensions.MOON, 18000, true),
            new View("moon-sun", GCDimensions.MOON, 3000, false),
            new View("mercury-sun", GCDimensions.MERCURY, 3000, false),
            new View("mars-sun", GCDimensions.MARS, 3000, false),
            new View("mars-sunset", GCDimensions.MARS, 12000, false),
            new View("venus-sun", GCDimensions.VENUS, 3000, false),
            new View("asteroid-sun", GCDimensions.ASTEROID, 3000, false),
            new View("jupiter-sun", GCDimensions.JUPITER, 3000, false),
            new View("flight-moon-occlusion", GCDimensions.SOLAR_SYSTEM, 6000, true),
            new View("flight-sun", GCDimensions.SOLAR_SYSTEM, 6000, false),
            new View("moon-high-descent", GCDimensions.MOON, 6000, false, 900),
            new View("moon-ascent", GCDimensions.MOON, 6000, false, 400),
            new View("moon-high-night", GCDimensions.MOON, 18000, false, 900),
            new View("mercury-high-descent", GCDimensions.MERCURY, 6000, false, 900),
            new View("mars-high-descent", GCDimensions.MARS, 6000, false, 900),
            new View("venus-high-descent", GCDimensions.VENUS, 6000, false, 900),
            new View("jupiter-high-descent", GCDimensions.JUPITER, 6000, false, 900),
            new View("asteroid-open-space", GCDimensions.ASTEROID, 6000, false, 900),
            new View("earth-high-descent", Level.OVERWORLD, 6000, false, 900),
            new View("moon-transfer-east", GCDimensions.MOON, 6000, false, 900, 0),
            new View("moon-transfer-west", GCDimensions.MOON, 6000, false, 900, 1),
            new View("moon-transfer-north-cap", GCDimensions.MOON, 6000, false, 900, 2),
            new View("moon-transfer-south-cap", GCDimensions.MOON, 6000, false, 900, 3),
            new View("moon-transfer-front", GCDimensions.MOON, 6000, false, 900, 4),
            new View("moon-transfer-back", GCDimensions.MOON, 6000, false, 900, 5),
            new View("earth-transfer", Level.OVERWORLD, 6000, false, 900, 4)
    };
    private int stage, view, ticks, timeout;
    private boolean patternRequested;
    private java.util.concurrent.CompletableFuture<Void> reload;
    private net.minecraft.client.renderer.ShaderInstance beforeReload;

    @Override public void onInitializeClient() {
        if (Boolean.getBoolean("galacticraft.surface-sky.client-test")) ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private void tick(Minecraft client) {
        if (++timeout > 6000) throw new AssertionError("Surface sky gallery timed out at " + view);
        if (stage >= 2 && client.screen instanceof PauseScreen) client.setScreen(null);
        if (client.getOverlay() != null || SpaceflightTransition.isActive()) return;
        if (stage == 0) {
            if (client.screen == null) return;
            stage = 1;
            org.lwjgl.glfw.GLFW.glfwHideWindow(client.getWindow().getWindow());
            org.lwjgl.glfw.GLFW.glfwSetWindowSize(client.getWindow().getWindow(), 1280, 720);
            client.options.renderDistance().set(4);
            client.options.pauseOnLostFocus = false;
            client.options.hideGui = true;
            client.options.fov().set(45);
            client.options.tutorialStep = TutorialSteps.NONE;
            client.createWorldOpenFlows().createFreshLevel("surface-sky-" + System.currentTimeMillis(),
                    new LevelSettings("Surface Sky Check", GameType.SPECTATOR, false, Difficulty.PEACEFUL,
                            true, new GameRules(), WorldDataConfiguration.DEFAULT), new WorldOptions(73, false, false),
                    registries -> registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT)
                            .value().createWorldDimensions(), client.screen);
            return;
        }
        if (client.player == null || client.level == null || client.getSingleplayerServer() == null) return;
        if (stage == 1) {
            if (client.screen != null) return;
            client.options.setCameraType(CameraType.FIRST_PERSON);
            stage = 2;
        }
        if (stage == 2) {
            View shot = VIEWS[view];
            dev.galacticraft.mod.client.render.dimension.PlanetSurfaceRenderer.clearApproach();
            client.getSingleplayerServer().execute(() -> {
                var server = client.getSingleplayerServer();
                var player = server.getPlayerList().getPlayers().getFirst();
                var level = server.getLevel(shot.level());
                if (level == null) throw new AssertionError("Missing gallery dimension " + shot.level());
                level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
                level.setDayTime(DimensionTime.sameTimeOfDay(shot.time(), DimensionTime.dayLength(level)));
                level.setWeatherParameters(0, 6000, false, false);
                boolean flight = shot.level().equals(GCDimensions.SOLAR_SYSTEM);
                player.teleportTo(level, ADDON && flight ? 68000 : 0, shot.name().equals("addon-tier4") || shot.name().equals("addon-module-page") ? 12 : shot.altitude() > 0 ? shot.altitude() : flight ? 1024 : shot.level().equals(GCDimensions.JUPITER) ? 320 : 180,
                        flight ? (ADDON ? 2400 : 2600) : 0, 58, -44);
                if (shot.name().equals("addon-tier4")) {
                    var rocket = new dev.galacticraft.mod.content.entity.vehicle.RocketEntity(dev.galacticraft.mod.content.GCEntityTypes.ROCKET, level);
                    var addon = dev.galacticraft.test.addon.ExampleAddon.TIER_4_CONE;
                    rocket.setData(new dev.galacticraft.api.rocket.RocketData(
                            java.util.Optional.of(new net.minecraft.world.item.EitherHolder<>(addon)),
                            java.util.Optional.of(new net.minecraft.world.item.EitherHolder<>(dev.galacticraft.test.addon.ExampleAddon.TIER_4_BODY)),
                            java.util.Optional.of(new net.minecraft.world.item.EitherHolder<>(dev.galacticraft.test.addon.ExampleAddon.TIER_4_FIN)),
                            java.util.Optional.empty(), java.util.Optional.of(new net.minecraft.world.item.EitherHolder<>(dev.galacticraft.test.addon.ExampleAddon.TIER_4_ENGINE)),
                            java.util.Optional.empty(), java.util.Optional.empty(), -1));
                    for (int x = -3; x <= 3; x++) for (int z = 9; z <= 15; z++)
                        level.setBlockAndUpdate(new net.minecraft.core.BlockPos(x, 8, z), net.minecraft.world.level.block.Blocks.IRON_BLOCK.defaultBlockState());
                    rocket.setPos(0, 9, 12);
                    level.addFreshEntity(rocket);
                }
                if (shot.name().equals("addon-module-page")) {
                    player.setGameMode(GameType.CREATIVE);
                    player.getAbilities().flying = true;
                    player.onUpdateAbilities();
                    var pos = new net.minecraft.core.BlockPos(1, 11, 2);
                    level.setBlockAndUpdate(pos, dev.galacticraft.mod.content.GCBlocks.ROCKET_WORKBENCH.defaultBlockState());
                    var bench = (dev.galacticraft.mod.content.block.entity.RocketWorkbenchBlockEntity) level.getBlockEntity(pos);
                    bench.ingredients.setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND));
                    bench.ingredients.setItem(1, new net.minecraft.world.item.ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                            net.minecraft.resources.ResourceLocation.parse("galacticraft:heavy_plating_t3"))));
                    bench.ingredients.setChanged();
                }
            });
            stage = 3;
            ticks = 0;
            return;
        }
        View shot = VIEWS[view];
        boolean menuShot = shot.name().equals("addon-module-page");
        if (!client.level.dimension().equals(shot.level()) || (!menuShot && client.screen != null)) return;
        if (menuShot && !(client.screen instanceof dev.galacticraft.mod.client.gui.screen.ingame.RocketWorkbenchScreen)) {
            var pos = new net.minecraft.core.BlockPos(1, 11, 2);
            if (!patternRequested && client.level.getBlockEntity(pos) instanceof dev.galacticraft.mod.content.block.entity.RocketWorkbenchBlockEntity) {
                patternRequested = true;
                client.getSingleplayerServer().execute(() -> {
                    var server = client.getSingleplayerServer();
                    var player = server.getPlayerList().getPlayers().getFirst();
                    var bench = (dev.galacticraft.mod.content.block.entity.RocketWorkbenchBlockEntity) player.serverLevel().getBlockEntity(pos);
                    ((dev.galacticraft.api.accessor.ServerResearchAccessor) player).galacticraft$unlockRocketPartRecipes(
                            dev.galacticraft.api.rocket.part.RocketPart.researchId(dev.galacticraft.test.addon.ExampleAddon.TIER_4_ENGINE));
                    dev.architectury.registry.menu.MenuRegistry.openExtendedMenu(player, bench.menuOn(
                            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("galacticraft-test", "rocket_module/rocket_engine/tier_4")));
                });
            }
            return;
        }
        if ((shot.level().equals(GCDimensions.MOON) || shot.level().equals(GCDimensions.MERCURY)
                || shot.level().equals(GCDimensions.ASTEROID) || shot.level().equals(GCDimensions.SOLAR_SYSTEM))
                && !client.level.getSkyColor(client.player.position(), 1).equals(net.minecraft.world.phys.Vec3.ZERO))
            throw new AssertionError("Airless sky inherited atmospheric color: " + shot.name());
        if (shot.name().equals("addon-tier4")) {
            client.player.setYRot(0);
            client.player.setXRot(0);
        } else if (shot.altitude() > 0) {
            client.player.setYRot(0);
            client.player.setXRot(70);
        } else if (shot.level().equals(GCDimensions.SOLAR_SYSTEM)) {
            client.player.setYRot(ADDON ? shot.name().equals("addon-planet") ? 180 : 90 : shot.earth() ? 180 : (float) Math.toDegrees(Math.atan2(8000, -2600)));
            client.player.setXRot(0);
        } else if (shot.earth()) {
            client.player.setYRot(58);
            client.player.setXRot(-44);
        } else {
            double angle = client.level.getTimeOfDay(1) * Math.PI * 2;
            double x = Math.sin(angle) * (shot.level().equals(GCDimensions.MOON) || shot.level().equals(GCDimensions.ASTEROID)
                    || shot.level().equals(GCDimensions.JUPITER) ? -1 : 1);
            client.player.setYRot(x > 0 ? -90 : 90);
            client.player.setXRot((float) -Math.toDegrees(Math.asin(Math.cos(angle))));
        }
        if (ticks == 40 && shot.face() >= 0) {
            var body = dev.galacticraft.mod.world.dimension.SpaceTravel.fromLevel(client.level);
            double days = dev.galacticraft.mod.world.dimension.SpaceTravel.days(client.level);
            var offset = switch (shot.face()) {
                case 0 -> new org.joml.Vector3d(3, .2, -.3);
                case 1 -> new org.joml.Vector3d(-3, .2, .3);
                case 2 -> new org.joml.Vector3d(.2, 3, -.3);
                case 3 -> new org.joml.Vector3d(.2, -3, .3);
                case 4 -> new org.joml.Vector3d(.2, -.3, 3);
                default -> new org.joml.Vector3d(-.2, .3, -3);
            };
            body.spin().orientation(days).transform(offset.mul(body.radius()));
            dev.galacticraft.mod.client.render.dimension.PlanetSurfaceRenderer.beginApproach(client.level, body,
                    body.center().add(offset.x, offset.y, offset.z), client.player.position(), days, body.radius() * .02, (float) days);
        }
        if (++ticks < 80) return;
        if (shot.name().equals("addon-tier4") && client.level.getEntitiesOfClass(
                dev.galacticraft.mod.content.entity.vehicle.RocketEntity.class, new net.minecraft.world.phys.AABB(-4, 7, 8, 4, 20, 16)).isEmpty())
            throw new AssertionError("Tier-4 gallery rocket was not synchronized to the client");
        if (view == 2 && reload == null) {
            beforeReload = GCRenderTypes.getSpacePlanetShader();
            reload = client.reloadResourcePacks();
            return;
        }
        if (reload != null && !reload.isDone()) return;
        if (view == 2) {
            reload.join();
            if (GCRenderTypes.getSpacePlanetShader() == beforeReload) throw new AssertionError("Planet shader did not reload");
        }
        if (menuShot && (!(client.player.containerMenu instanceof dev.galacticraft.mod.screen.RocketWorkbenchMenu menu)
                || menu.resultSlot().getItem().isEmpty())) throw new AssertionError("Addon module result did not sync");
        if (GCRenderTypes.getSpacePlanetShader() == null) throw new AssertionError("Planet shader missing");
        int error = org.lwjgl.opengl.GL11.glGetError();
        if (error != org.lwjgl.opengl.GL11.GL_NO_ERROR) throw new AssertionError("Sky GL error: " + error);
        if (shot.altitude() > 0 && shot.face() < 0 && shot.level().equals(GCDimensions.MOON)) {
            try (var image = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
                int visible = 0, samples = 0;
                for (int x = image.getWidth() / 4; x < image.getWidth() * 3 / 4; x += 8)
                    for (int y = image.getHeight() / 3; y < image.getHeight() * 2 / 3; y += 8) {
                        int pixel = image.getPixelRGBA(x, y);
                        if ((pixel & 255) + (pixel >>> 8 & 255) + (pixel >>> 16 & 255) > 12) visible++;
                        samples++;
                    }
                if (visible < samples * .9) throw new AssertionError("Lunar altitude surface is empty: " + shot.name());
            }
        }
        Screenshot.grab(client.gameDirectory, "surface-" + shot.name() + ".png", client.getMainRenderTarget(), ignored -> {});
        Constant.LOGGER.info("SURFACE SKY CAPTURE: {}", shot.name());
        if (++view < VIEWS.length) { stage = 2; return; }
        Constant.LOGGER.info("SURFACE SKY CLIENT CHECK PASSED: {} views, shader reload and GL checks", VIEWS.length);
        client.stop();
        stage = 4;
    }
}