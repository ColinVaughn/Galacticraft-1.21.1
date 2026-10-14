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

package dev.galacticraft.mod.client.model;

import dev.architectury.registry.menu.MenuRegistry;
import dev.galacticraft.api.component.GCDataComponents;
import dev.galacticraft.api.entity.rocket.render.RocketPartRendererRegistry;
import dev.galacticraft.api.rocket.RocketData;
import dev.galacticraft.api.rocket.RocketPrefabs;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.client.render.rocket.Tier1RocketPartRenderer;
import dev.galacticraft.mod.content.GCBlocks;
import dev.galacticraft.mod.content.GCEntityTypes;
import dev.galacticraft.mod.content.block.entity.RocketWorkbenchBlockEntity;
import dev.galacticraft.mod.content.entity.vehicle.RocketEntity;
import dev.galacticraft.mod.content.item.GCItems;
import dev.galacticraft.mod.content.item.RocketModuleItem;
import dev.galacticraft.mod.screen.RocketWorkbenchMenu;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.slf4j.LoggerFactory;

import java.util.List;

/** Opt-in check of baked modules, synced assembly and actual client previews. */
public final class ModularRocketClientTest implements ClientModInitializer {
    private boolean opened, placed, assemblyOpened, checked, cameraSet;
    private int ticks, transitionTicks;
    private int refitPhase, refitWait;
    private boolean refitChecked;
    private ResourceLocation expectedPage;
    private double cursorX, cursorY;
    private volatile RocketEntity mixed;
    private volatile ArmorStand camera;

    @Override public void onInitializeClient() {
        if (!Boolean.getBoolean("galacticraft.rocket.client-test")) return;
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.getOverlay() != null) return;
            if (!opened && client.screen != null) {
                checkModels(client);
                opened = true;
                client.options.renderDistance().set(5);
                client.options.pauseOnLostFocus = false;
                client.options.guiScale().set(2);
                client.createWorldOpenFlows().createFreshLevel("modular-rocket-" + System.currentTimeMillis(),
                        new LevelSettings("Modular Rocket Check", GameType.CREATIVE, false, Difficulty.PEACEFUL,
                                true, new GameRules(), WorldDataConfiguration.DEFAULT), new WorldOptions(17, false, false),
                        registry -> registry.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT)
                                .value().createWorldDimensions(), client.screen);
                return;
            }
            if (client.level == null || client.player == null || client.getSingleplayerServer() == null) return;
            if (!placed) {
                if (client.screen != null) return;
                placed = true;
                client.getSingleplayerServer().execute(() -> place(client));
                return;
            }
            if (mixed == null || client.level.getEntity(mixed.getId()) == null) return;
            if (!assemblyOpened) {
                if (!(client.level.getBlockEntity(new BlockPos(2, 65, -3)) instanceof RocketWorkbenchBlockEntity)) return;
                assemblyOpened = true;
                client.getSingleplayerServer().execute(() -> openAssembly(client));
                return;
            }
            if (!checked) {
                if (!(client.player.containerMenu instanceof RocketWorkbenchMenu menu) || !menu.page().id().equals(Constant.id("rocket/modular"))) return;
                RocketData data = menu.resultSlot().getItem().get(GCDataComponents.ROCKET_DATA);
                if (data == null) return;
                require(data.body().orElseThrow().key().equals(RocketPrefabs.TIER_3.body().orElseThrow().key()), "Client result lost tier-3 body");
                require(data.engine().orElseThrow().key().equals(RocketPrefabs.TIER_1.engine().orElseThrow().key()), "Client result lost tier-1 engine");
                RocketData preview = menu.previewRocket();
                require(preview.isValid(), "Client preview is incomplete");
                require(preview.cone().orElseThrow().key().equals(data.cone().orElseThrow().key())
                        && preview.body().orElseThrow().key().equals(data.body().orElseThrow().key())
                        && preview.fin().orElseThrow().key().equals(data.fin().orElseThrow().key())
                        && preview.engine().orElseThrow().key().equals(data.engine().orElseThrow().key()),
                        "Client preview does not match the assembled rocket");
                checked = true;
            }
            if (ticks == 55 && !refitChecked) {
                if (!checkRefit(client)) return;
                refitChecked = true;
            }
            boolean waitingForPage = (ticks == 9 || ticks == 14 || ticks == 19 || ticks == 24 || ticks == 54)
                    && (!(client.player.containerMenu instanceof RocketWorkbenchMenu menu) || !menu.page().id().equals(expectedPage));
            boolean waitingForPaint = ticks == 42 && (!(client.player.containerMenu instanceof dev.galacticraft.mod.screen.RocketPaintMenu paintMenu)
                    || !dev.galacticraft.mod.screen.RocketPaintMenu.canPaint(paintMenu.input()));
            int expectedBlue = 0xFF000000 | net.minecraft.world.item.DyeColor.BLUE.getTextureDiffuseColor();
            var expectedPaint = dev.galacticraft.api.rocket.RocketPaint.NONE.withColor(dev.galacticraft.api.rocket.part.RocketPartTypes.CONE, expectedBlue)
                    .withColor(dev.galacticraft.api.rocket.part.RocketPartTypes.BODY, expectedBlue)
                    .withColor(dev.galacticraft.api.rocket.part.RocketPartTypes.FIN, expectedBlue)
                    .withColor(dev.galacticraft.api.rocket.part.RocketPartTypes.ENGINE, expectedBlue);
            boolean waitingForPaintAck = ticks == 47 && (!(client.player.containerMenu instanceof dev.galacticraft.mod.screen.RocketPaintMenu paintMenu)
                    || !dev.galacticraft.mod.screen.RocketPaintMenu.data(paintMenu.input()).paint().equals(expectedPaint));
            if (waitingForPage || waitingForPaint || waitingForPaintAck || ticks == 58 && client.screen != null) {
                require(++transitionTicks < 100, "Menu transition timed out: expected " + expectedPage + ", actual " + client.player.containerMenu);
                return;
            }
            transitionTicks = 0;
            ticks++;
            if (ticks == 5) clickPage(client, 1, true);
            if (ticks == 10) {
                checkPage(client);
                clickPage(client, 1, false);
            }
            if (ticks == 15) {
                checkPage(client);
                clickPage(client, -1, true);
            }
            if (ticks == 20) {
                checkPage(client);
                clickPage(client, -1, false);
            }
            if (ticks == 25) checkPage(client);
            if (ticks == 30) capture(client, "modular-rocket-workbench.png");
            if (ticks == 35) client.getSingleplayerServer().execute(() -> {
                var player = client.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                var workbench = (RocketWorkbenchBlockEntity) client.getSingleplayerServer().overworld().getBlockEntity(new BlockPos(2, 65, -3));
                var input = GCItems.ROCKET.getDefaultInstance();
                input.set(GCDataComponents.ROCKET_DATA, mixed.getRocketData());
                workbench.paintInput.setItem(0, input);
                player.containerMenu.broadcastChanges();
            });
            if (ticks == 36) clickButton(client, "gui.galacticraft.rocket_paint.open", true);
            if (ticks == 43) {
                checkCursor(client);
                require(client.screen instanceof dev.galacticraft.mod.client.gui.screen.ingame.RocketPaintScreen, "Paint screen did not open");
                int left = (client.screen.width - dev.galacticraft.mod.screen.RocketPaintMenu.WIDTH) / 2,
                        top = (client.screen.height - Constant.RocketWorkbench.ROCKET_PAGE_HEIGHT) / 2;
                client.screen.mouseClicked(left + 195, top + 75, 0);
                client.screen.mouseReleased(left + 195, top + 75, 0);
                client.screen.mouseClicked(left + 255, top + 132, 0);
                client.screen.mouseReleased(left + 255, top + 132, 0);
            }
            if (ticks == 48) {
                require(client.player.containerMenu instanceof dev.galacticraft.mod.screen.RocketPaintMenu, "Paint menu closed unexpectedly");
                var paintMenu = (dev.galacticraft.mod.screen.RocketPaintMenu) client.player.containerMenu;
                var paint = dev.galacticraft.mod.screen.RocketPaintMenu.data(paintMenu.input()).paint();
                int blue = 0xFF000000 | net.minecraft.world.item.DyeColor.BLUE.getTextureDiffuseColor();
                require(paint.body().orElseThrow() == blue && paint.cone().orElseThrow() == blue
                        && paint.fin().orElseThrow() == blue && paint.engine().orElseThrow() == blue,
                        "Paint controls or server synchronization lost the whole-rocket color");
                capture(client, "modular-rocket-paint.png");
            }
            if (ticks == 49) clickButton(client, "gui.galacticraft.rocket_paint.back", true);
            if (ticks == 55) {
                require(client.player.containerMenu instanceof RocketWorkbenchMenu menu
                        && menu.page().id().equals(Constant.id("rocket/modular")), "Paint Back did not restore assembly");
                checkCursor(client);
            }
            if (ticks == 56) client.getSingleplayerServer().execute(() -> {
                var player = client.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                player.closeContainer();
                player.startRiding(mixed, true);
            });
            if (ticks == 59) {
                require(client.screen == null, "Closing the workbench left its screen open");
                if (client.isWindowActive()) require(client.mouseHandler.isMouseGrabbed(), "Closing did not restore mouse capture");
            }
            if (ticks >= 60 && !cameraSet && client.player.getVehicle() instanceof RocketEntity) {
                if (camera == null || client.level.getEntity(camera.getId()) == null) return;
                client.setCameraEntity(client.level.getEntity(camera.getId()));
                client.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
                client.options.hideGui = true;
                client.mouseHandler.releaseMouse();
                cameraSet = true;
            }
            if (ticks == 100) {
                require(cameraSet, "Player did not mount the mixed rocket");
                var view = client.gameRenderer.getMainCamera();
                LoggerFactory.getLogger("ModularRocketClientTest").info("Rocket scene camera: {} yaw {} pitch {}", view.getPosition(), view.getYRot(), view.getXRot());
                capture(client, "modular-rockets.png");
            }
            if (ticks >= 110) {
                client.setCameraEntity(client.player);
                client.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
                client.player.setYRot(0);
                client.player.setXRot(0);
            }
            if (ticks == 150) capture(client, "modular-rocket-pilot.png");
            if (ticks == 180) {
                LoggerFactory.getLogger("ModularRocketClientTest").info("MODULAR ROCKET CLIENT CHECK PASSED: 12 baked modules, mixed assembly sync, paint controls and network sync, cursor-preserving navigation, refit extraction and upgrade sync, inventory models and mounted-player preview");
                client.stop();
            }
        });
    }

    private boolean checkRefit(Minecraft client) {
        require(++refitWait < 200, "Refit client check timed out at phase " + refitPhase);
        var menu = (RocketWorkbenchMenu) client.player.containerMenu;
        if (refitPhase == 0) {
            client.getSingleplayerServer().execute(() -> {
                var player = client.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                var bench = (RocketWorkbenchBlockEntity) client.getSingleplayerServer().overworld().getBlockEntity(new BlockPos(2, 65, -3));
                bench.ingredients.clearContent();
                bench.chests.clearContent();
                var rocket = GCItems.ROCKET.getDefaultInstance();
                rocket.set(GCDataComponents.ROCKET_DATA, mixed.getRocketData());
                rocket.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("Refit client check"));
                bench.refitInput.setItem(0, rocket);
                player.containerMenu.broadcastChanges();
            });
        } else if (refitPhase == 1) {
            if (!menu.isRefitting()) return false;
            var cone = menu.slots.stream().filter(slot -> slot.container == menu.workbench.ingredients && slot.getContainerSlot() == 0).findFirst().orElseThrow();
            require(cone.getItem().getItem() instanceof RocketModuleItem module && module.tier() == 2, "Installed module did not synchronize");
            client.gameMode.handleInventoryMouseClick(menu.containerId, cone.index, 0, net.minecraft.world.inventory.ClickType.PICKUP, client.player);
        } else if (refitPhase == 2) {
            if (menu.getCarried().isEmpty() || menu.previewRocket().cone().isPresent()) return false;
            require(menu.getCarried().getItem() instanceof RocketModuleItem && menu.resultSlot().getItem().isEmpty(), "Extraction left a complete result");
            capture(client, "modular-rocket-refit-incomplete.png");
            var cone = menu.slots.stream().filter(slot -> slot.container == menu.workbench.ingredients && slot.getContainerSlot() == 0).findFirst().orElseThrow();
            client.gameMode.handleInventoryMouseClick(menu.containerId, cone.index, 0, net.minecraft.world.inventory.ClickType.PICKUP, client.player);
        } else if (refitPhase == 3) {
            if (!menu.previewRocket().isValid() || !menu.getCarried().isEmpty()) return false;
            var source = menu.slots.stream().filter(slot -> slot.container == client.player.getInventory()
                    && slot.getItem().getItem() instanceof RocketModuleItem module
                    && module.type() == dev.galacticraft.api.rocket.part.RocketPartTypes.ENGINE && module.tier() == 3).findFirst().orElseThrow();
            client.gameMode.handleInventoryMouseClick(menu.containerId, source.index, 0, net.minecraft.world.inventory.ClickType.QUICK_MOVE, client.player);
        } else if (refitPhase == 4) {
            if (!menu.previewRocket().engine().orElseThrow().key().equals(RocketPrefabs.TIER_3.engine().orElseThrow().key())) return false;
            require(menu.previewRocket().paint().body().equals(mixed.getRocketData().paint().body()), "Refit changed body paint");
            capture(client, "modular-rocket-refit.png");
            client.gameMode.handleInventoryMouseClick(menu.containerId, menu.resultSlot().index, 5, net.minecraft.world.inventory.ClickType.SWAP, client.player);
        } else if (refitPhase == 5) {
            if (menu.isRefitting() || !client.player.getInventory().getItem(5).is(GCItems.ROCKET)) return false;
            var returned = client.player.getInventory().getItem(5);
            require(returned.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME).getString().equals("Refit client check"), "Refit lost item components");
            require(returned.get(GCDataComponents.ROCKET_DATA).engine().orElseThrow().key().equals(RocketPrefabs.TIER_3.engine().orElseThrow().key()), "Returned rocket lost its new engine");
            client.gameMode.handleInventoryMouseClick(menu.containerId, menu.resultSlot().index, 6, net.minecraft.world.inventory.ClickType.SWAP, client.player);
        } else {
            require(client.player.getInventory().countItem(GCItems.ROCKET) == 1, "Repeated result click duplicated the refitted rocket");
            require(!menu.isRefitting() && menu.resultSlot().getItem().isEmpty(), "Refit input or result survived pickup");
            return true;
        }
        refitPhase++;
        refitWait = 0;
        return false;
    }

    private void clickPage(Minecraft client, int direction, boolean move) {
        require(client.player.containerMenu instanceof RocketWorkbenchMenu, "Expected a workbench page");
        var menu = (RocketWorkbenchMenu) client.player.containerMenu;
        var target = menu.adjacentPage(direction);
        require(target != null, "Missing adjacent page");
        expectedPage = target.id();
        clickButton(client, direction > 0 ? "ui.galacticraft.rocket_workbench.button.next"
                : "ui.galacticraft.rocket_workbench.button.back", move);
    }

    private void clickButton(Minecraft client, String key, boolean move) {
        Button button = client.screen.children().stream().filter(child -> child instanceof Button)
                .map(child -> (Button) child).filter(child -> child.getMessage().getString().equals(Component.translatable(key).getString()))
                .findFirst().orElseThrow();
        if (move) {
            cursorX = (button.getX() + button.getWidth() / 2.0) * client.getWindow().getScreenWidth() / client.screen.width;
            cursorY = (button.getY() + button.getHeight() / 2.0) * client.getWindow().getScreenHeight() / client.screen.height;
            long window = client.getWindow().getWindow();
            GLFW.glfwSetCursorPos(window, cursorX, cursorY);
            // Windows may omit the move callback when the test window is unfocused.
            var callback = GLFW.glfwSetCursorPosCallback(window, null);
            GLFW.glfwSetCursorPosCallback(window, callback);
            require(callback != null, "Minecraft did not install its mouse callback");
            callback.invoke(window, cursorX, cursorY);
        }
        double x = cursorX * client.screen.width / client.getWindow().getScreenWidth();
        double y = cursorY * client.screen.height / client.getWindow().getScreenHeight();
        require(button.active && button.isMouseOver(x, y), "Navigation button moved away from the cursor");
        require(client.screen.mouseClicked(x, y, 0), "Navigation click was ignored");
        client.screen.mouseReleased(x, y, 0);
    }

    private void checkPage(Minecraft client) {
        require(client.player.containerMenu instanceof RocketWorkbenchMenu menu && menu.page().id().equals(expectedPage),
                "Page navigation did not synchronize the new menu: expected " + expectedPage + ", actual " + client.player.containerMenu);
        checkCursor(client);
    }

    private void checkCursor(Minecraft client) {
        double[] x = new double[1], y = new double[1];
        GLFW.glfwGetCursorPos(client.getWindow().getWindow(), x, y);
        require(Math.abs(x[0] - cursorX) < 1 && Math.abs(y[0] - cursorY) < 1,
                "Page switching recentered the native cursor: " + x[0] + ", " + y[0]);
        require(Math.abs(client.mouseHandler.xpos() - cursorX) < 1 && Math.abs(client.mouseHandler.ypos() - cursorY) < 1,
                "Page switching recentered Minecraft's cursor: " + client.mouseHandler.xpos() + ", " + client.mouseHandler.ypos());
        require(!client.mouseHandler.isMouseGrabbed(), "Page switching captured the mouse");
    }

    private static void checkModels(Minecraft client) {
        for (RocketModuleItem module : GCItems.ROCKET_MODULES) {
            String name = module.type() == dev.galacticraft.api.rocket.part.RocketPartTypes.FIN ? "fins" : module.type().name().toLowerCase(java.util.Locale.ROOT);
            require(GCModelLoader.INSTANCE.getModel(Constant.id("models/misc/rocket_" + name + "_tier_" + module.tier() + ".json")) != GCModelLoader.MISSING_MODEL, "Missing module mesh");
            require(client.getItemRenderer().getModel(module.getDefaultInstance(), null, null, 0).isCustomRenderer(), "Module inventory model lacks its custom renderer");
            var data = module.rocketData();
            var key = switch (module.type()) {
                case CONE -> data.cone().orElseThrow().key();
                case BODY -> data.body().orElseThrow().key();
                case FIN -> data.fin().orElseThrow().key();
                case ENGINE -> data.engine().orElseThrow().key();
                default -> throw new AssertionError("Unsupported module");
            };
            require(RocketPartRendererRegistry.INSTANCE.getRenderer(key) instanceof Tier1RocketPartRenderer, "Module renderer is missing");
        }
    }

    private void place(Minecraft client) {
        var server = client.getSingleplayerServer();
        var level = server.overworld();
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        level.setDayTime(6000);
        for (int x = -10; x <= 10; x++) for (int z = -15; z <= 8; z++) {
            level.setBlock(new BlockPos(x, 64, z), GCBlocks.MOON_ROCK.defaultBlockState(), 2);
            for (int y = 65; y <= 76; y++) level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2);
        }
        var player = server.getPlayerList().getPlayers().getFirst();
        player.teleportTo(level, 2, 65, -4, 150, 0);
        for (int i = 0; i < GCItems.ROCKET_MODULES.size(); i++) player.getInventory().setItem(9 + i, GCItems.ROCKET_MODULES.get(i).getDefaultInstance());
        BlockPos pos = new BlockPos(2, 65, -3);
        level.setBlock(pos, GCBlocks.ROCKET_WORKBENCH.defaultBlockState(), 3);
        RocketData data = new RocketData(RocketPrefabs.TIER_2.cone(), RocketPrefabs.TIER_3.body(), RocketPrefabs.TIER_3.fin(),
                java.util.Optional.empty(), RocketPrefabs.TIER_1.engine(), java.util.Optional.empty(), java.util.Optional.empty(), 0xFFFFFFFF);
        data = data.withPaint(dev.galacticraft.api.rocket.RocketPaint.NONE
                .withColor(dev.galacticraft.api.rocket.part.RocketPartTypes.BODY, 0xFF426BA8)
                .withColor(dev.galacticraft.api.rocket.part.RocketPartTypes.FIN, 0xFFDF762B));
        var variants = List.of(RocketPrefabs.TIER_1, data, RocketPrefabs.TIER_2, RocketPrefabs.TIER_3);
        for (int i = 0; i < variants.size(); i++) {
            RocketEntity rocket = new RocketEntity(GCEntityTypes.ROCKET, level);
            rocket.setData(variants.get(i));
            rocket.moveTo(-4.5 + i * 3, 65, 2, 0, 0);
            level.addFreshEntity(rocket);
            if (i == 1) mixed = rocket;
        }
        camera = new ArmorStand(level, 5, 68, -12);
        camera.moveTo(5, 68, -12, 25, 10);
        camera.setYHeadRot(25);
        camera.setNoGravity(true);
        camera.setInvisible(true);
        level.addFreshEntity(camera);
    }

    private static void openAssembly(Minecraft client) {
        var server = client.getSingleplayerServer();
        var player = server.getPlayerList().getPlayers().getFirst();
        var workbench = (RocketWorkbenchBlockEntity) server.overworld().getBlockEntity(new BlockPos(2, 65, -3));
        MenuRegistry.openExtendedMenu(player, workbench.menuOn(Constant.id("rocket/modular")));
        int[] tiers = {2, 3, 3, 1};
        var types = List.of(dev.galacticraft.api.rocket.part.RocketPartTypes.CONE, dev.galacticraft.api.rocket.part.RocketPartTypes.BODY,
                dev.galacticraft.api.rocket.part.RocketPartTypes.FIN, dev.galacticraft.api.rocket.part.RocketPartTypes.ENGINE);
        for (int i = 0; i < 4; i++) {
            final int slot = i;
            workbench.ingredients.setItem(i, GCItems.ROCKET_MODULES.stream().filter(module -> module.type() == types.get(slot) && module.tier() == tiers[slot]).findFirst().orElseThrow().getDefaultInstance());
        }
        workbench.ingredients.setChanged();
        RocketData data = workbench.output.getItem(0).get(GCDataComponents.ROCKET_DATA);
        require(data != null && data.isValid(), "Server did not assemble the mixed rocket");
        player.containerMenu.broadcastChanges();
    }

    private static void require(boolean valid, String message) { if (!valid) throw new AssertionError(message); }
    private static void capture(Minecraft client, String name) {
        Screenshot.grab(client.gameDirectory, name, client.getMainRenderTarget(),
                message -> LoggerFactory.getLogger("ModularRocketClientTest").info("{}", message.getString()));
    }
}

