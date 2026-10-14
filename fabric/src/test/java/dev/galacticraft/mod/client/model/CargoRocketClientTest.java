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

import dev.galacticraft.mod.client.render.entity.CargoRocketRenderer;
import dev.galacticraft.mod.content.GCBlocks;
import dev.galacticraft.mod.content.GCEntityTypes;
import dev.galacticraft.mod.content.entity.vehicle.CargoRocketEntity;
import dev.galacticraft.mod.content.item.GCItems;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.slf4j.LoggerFactory;

/** Opt-in visual and network check of the cargo fairing. */
public final class CargoRocketClientTest implements ClientModInitializer {
    private boolean opened, placed, cameraSet, sawExtension;
    private int ticks;
    private volatile CargoRocketEntity changing;
    private volatile ArmorStand camera;

    @Override public void onInitializeClient() {
        if (!Boolean.getBoolean("galacticraft.cargo.client-test")) return;
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.getOverlay() != null) return;
            if (!opened && client.screen != null) {
                require(GCModelLoader.INSTANCE.getModel(CargoRocketRenderer.MODEL) != GCModelLoader.MISSING_MODEL, "Missing cargo model");
                require(client.getItemRenderer().getModel(GCItems.CARGO_ROCKET.getDefaultInstance(), null, null, 0).isCustomRenderer(), "Missing cargo inventory renderer");
                opened = true;
                client.options.renderDistance().set(5);
                client.options.pauseOnLostFocus = false;
                client.options.guiScale().set(2);
                client.createWorldOpenFlows().createFreshLevel("cargo-rocket-" + System.currentTimeMillis(),
                        new LevelSettings("Cargo Fairing Check", GameType.CREATIVE, false, Difficulty.PEACEFUL,
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
            if (changing == null || camera == null || !(client.level.getEntity(changing.getId()) instanceof CargoRocketEntity rocket)
                    || client.level.getEntity(camera.getId()) == null) return;
            if (!cameraSet) {
                client.setCameraEntity(client.level.getEntity(camera.getId()));
                client.options.setCameraType(CameraType.FIRST_PERSON);
                client.options.hideGui = true;
                client.mouseHandler.releaseMouse();
                cameraSet = true;
            }
            ticks++;
            if (ticks == 40) {
                require(rocket.getCargoLoad() == 0 && rocket.getBbHeight() == 4.5F, "Empty fairing did not sync");
                capture(client, "cargo-rocket-loads.png");
                client.getSingleplayerServer().execute(() -> fill(changing, 64));
            }
            if (ticks > 40 && ticks < 85 && rocket.getCargoLoad() == 1
                    && rocket.getFairingLoad(1) > 0 && rocket.getFairingLoad(1) < 0.999F) sawExtension = true;
            if (ticks == 85) {
                require(sawExtension, "Fairing jumped instead of animating");
                require(rocket.getCargoLoad() == 1 && rocket.getFairingLoad(1) > 0.999F && rocket.getBbHeight() == 6,
                        "Full hold did not sync to an observer with no cargo menu open");
                capture(client, "cargo-rocket-loaded.png");
                client.getSingleplayerServer().execute(() -> changing.getVehicleInventory().clearContent());
            }
            if (ticks == 130) {
                require(rocket.getCargoLoad() == 0 && rocket.getFairingLoad(1) < 0.001F && rocket.getBbHeight() == 4.5F,
                        "Unloading did not sync and retract the fairing");
                capture(client, "cargo-rocket-unloaded.png");
                LoggerFactory.getLogger("CargoRocketClientTest").info("CARGO ROCKET CLIENT CHECK PASSED: textured model, empty/half/full loads, smooth extension, unload and observer sync");
            }
            if (ticks == 135) {
                client.setCameraEntity(client.player);
                client.options.hideGui = false;
                client.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(client.player));
            }
            if (ticks == 150) capture(client, "cargo-rocket-inventory.png");
            if (ticks == 160) {
                client.stop();
            }
        });
    }

    private void place(Minecraft client) {
        var server = client.getSingleplayerServer();
        var level = server.overworld();
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        level.setDayTime(6000);
        for (int x = -10; x <= 10; x++) for (int z = -5; z <= 20; z++) {
            level.setBlock(new BlockPos(x, 64, z), GCBlocks.MOON_ROCK.defaultBlockState(), 2);
        }
        var player = server.getPlayerList().getPlayers().getFirst();
        player.teleportTo(level, 7, 65, 16, 155, 10);
        player.getInventory().setItem(0, GCItems.CARGO_ROCKET.getDefaultInstance());
        for (int i = 0; i < 3; i++) {
            var rocket = new CargoRocketEntity(GCEntityTypes.CARGO_ROCKET, level);
            rocket.moveTo(-4 + i * 4, 65, 2, 0, 0);
            fill(rocket, i * 32);
            level.addFreshEntity(rocket);
            if (i == 0) changing = rocket;
        }
        camera = new ArmorStand(level, 7, 68, 16);
        camera.moveTo(7, 68, 16, 155, 10);
        camera.setYHeadRot(155);
        camera.setNoGravity(true);
        camera.setInvisible(true);
        level.addFreshEntity(camera);
    }

    private static void fill(CargoRocketEntity rocket, int count) {
        for (int slot = 0; slot < 27; slot++) rocket.getVehicleInventory().setItem(slot,
                count == 0 ? ItemStack.EMPTY : new ItemStack(Items.STONE, count));
    }

    private static void require(boolean valid, String message) { if (!valid) throw new AssertionError(message); }
    private static void capture(Minecraft client, String name) {
        Screenshot.grab(client.gameDirectory, name, client.getMainRenderTarget(),
                message -> LoggerFactory.getLogger("CargoRocketClientTest").info("{}", message.getString()));
    }
}
