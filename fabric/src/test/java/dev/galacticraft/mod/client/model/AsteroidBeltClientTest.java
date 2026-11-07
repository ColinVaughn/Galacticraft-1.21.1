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

package dev.galacticraft.mod.client.model;

import dev.galacticraft.api.rocket.LaunchStage;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.Galacticraft;
import dev.galacticraft.mod.client.render.dimension.SpaceflightTransition;
import dev.galacticraft.mod.config.ConfigImpl;
import dev.galacticraft.mod.content.GCEntityTypes;
import dev.galacticraft.mod.content.entity.vehicle.RocketEntity;
import dev.galacticraft.mod.content.rocket.RocketPrefabs;
import dev.galacticraft.mod.world.dimension.SpaceTravel;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;

/**
 * Opt-in screenshot gallery of the cosmetic asteroid belt, from a real mounted rocket in the solar-system dimension.
 * Run with {@code gradlew :fabric:runClient -PbeltClientTest -Prei=false -Pastrocraft=false}.
 */
public final class AsteroidBeltClientTest implements ClientModInitializer {
    private int stage, view, ticks, timeout;

    @Override
    public void onInitializeClient() {
        if (!Boolean.getBoolean("galacticraft.belt.client-test")) return;
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private static Vec3 onBelt(double radius, double angle, double height) {
        double a = Math.toRadians(angle);
        return SpaceTravel.SUN_POSITION.add(radius * Math.cos(a), height, radius * Math.sin(a));
    }

    private void tick(Minecraft client) {
        if (++timeout > 4000) throw new AssertionError("Belt gallery timed out in stage " + stage);
        if (stage >= 2 && client.screen instanceof PauseScreen) client.setScreen(null);
        if (client.getOverlay() != null || SpaceflightTransition.isActive()) return;
        if (stage == 0) {
            if (client.screen == null) return;
            stage = 1;
            org.lwjgl.glfw.GLFW.glfwHideWindow(client.getWindow().getWindow());
            client.options.renderDistance().set(4);
            client.options.pauseOnLostFocus = false;
            client.options.guiScale().set(2);
            client.options.tutorialStep = TutorialSteps.NONE;
            client.createWorldOpenFlows().createFreshLevel("belt-" + System.currentTimeMillis(),
                    new LevelSettings("Belt Gallery", GameType.CREATIVE, false, Difficulty.PEACEFUL,
                            true, new GameRules(), WorldDataConfiguration.DEFAULT), new WorldOptions(73, false, false),
                    registries -> registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT)
                            .value().createWorldDimensions(), client.screen);
            return;
        }
        if (client.player == null || client.level == null || client.getSingleplayerServer() == null) return;
        if (stage == 1) {
            if (client.screen != null) return;
            stage = 2;
            client.options.setCameraType(CameraType.FIRST_PERSON);
            client.getSingleplayerServer().execute(() -> {
                ((ConfigImpl) Galacticraft.CONFIG).setSeamlessSpaceTravel(true);
                var player = client.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                var rocket = new RocketEntity(GCEntityTypes.ROCKET, player.serverLevel());
                rocket.setData(RocketPrefabs.TIER_1_STORAGE_UPGRADE);
                rocket.setFuel(rocket.getFuelTankCapacity());
                rocket.setLaunchStage(LaunchStage.LAUNCHED);
                rocket.setPos(0, Constant.ESCAPE_HEIGHT + 1, 0);
                player.serverLevel().addFreshEntity(rocket);
                player.teleportTo(0, Constant.ESCAPE_HEIGHT + 2, 0);
                player.startRiding(rocket, true);
            });
            return;
        }
        if (!(client.player.getVehicle() instanceof RocketEntity rocket) || !rocket.isInSpaceflight() || client.screen != null) return;
        client.options.keyShift.setDown(true);
        var earth = SpaceTravel.DESTINATIONS.getFirst();
        var asteroid = SpaceTravel.DESTINATIONS.get(5);
        var jupiter = SpaceTravel.DESTINATIONS.getLast();
        // name, camera position, look target
        Object[][] gallery = {
                {"from-earth", earth.departurePosition(), jupiter.center()},
                {"belt-core", onBelt(18_500, 5, 40), onBelt(18_500, 20, 0)},
                {"asteroid-approach", asteroid.center().add(-500, 120, -300), asteroid.center()},
                {"above-belt", onBelt(13_000, -60, 4_000), onBelt(18_000, -40, 0)}
        };
        if (ticks == 0) pose(client, (Vec3) gallery[view][1], (Vec3) gallery[view][2]);
        if (++ticks < 50) return;
        Screenshot.grab(client.gameDirectory, "belt-" + view + "-" + gallery[view][0] + ".png", client.getMainRenderTarget(), ignored -> {});
        ticks = 0;
        if (++view < gallery.length) return;
        Constant.LOGGER.info("BELT GALLERY CAPTURED: {} views", gallery.length);
        client.options.keyShift.setDown(false);
        client.stop();
        stage = 3;
    }

    private static void pose(Minecraft client, Vec3 position, Vec3 target) {
        client.getSingleplayerServer().execute(() -> {
            var player = client.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
            if (!(player.getVehicle() instanceof RocketEntity rocket)) throw new AssertionError("Gallery lost the rocket");
            var direction = target.subtract(position).normalize();
            rocket.setPos(position);
            rocket.setDeltaMovement(Vec3.ZERO);
            rocket.setXRot((float) Math.toDegrees(Math.acos(direction.y)));
            rocket.setYRot((float) Math.toDegrees(Math.atan2(-direction.x, direction.z)));
            rocket.xRotO = rocket.getXRot();
            rocket.yRotO = rocket.getYRot();
            rocket.getPassengers().forEach(rocket::positionRider);
        });
    }
}
