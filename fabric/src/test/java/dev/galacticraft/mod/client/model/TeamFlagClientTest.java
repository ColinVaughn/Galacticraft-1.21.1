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

import com.mojang.blaze3d.platform.NativeImage;
import dev.architectury.networking.NetworkManager;
import dev.galacticraft.api.component.GCDataComponents;
import dev.galacticraft.impl.network.c2s.TeamNamePayload;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.client.render.dimension.SpaceflightTransition;
import dev.galacticraft.mod.client.spacerace.TeamFlagClient;
import dev.galacticraft.mod.client.spacerace.TeamFlagTextures;
import dev.galacticraft.mod.content.block.entity.decoration.FlagBlockEntity;
import dev.galacticraft.mod.content.item.FlagItem;
import dev.galacticraft.mod.spacerace.SpaceRaceTeams;
import dev.galacticraft.mod.spacerace.TeamFlagImage;
import dev.galacticraft.mod.spacerace.TeamFlagOwner;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Checks that a saved flag design syncs on joining, renders correctly on both sides, and leaves
 * unknown teams' flags blank. Captures planted and held flags.
 * Run with {@code gradlew :fabric:runClient -PteamFlagClientTest -Prei=false -Pastrocraft=false}.
 */
public final class TeamFlagClientTest implements ClientModInitializer {
    private static final int RED = 0xFFE53935, BLUE = 0xFF1E88E5, GREEN = 0xFF43A047, YELLOW = 0xFFFDD835;
    private static final String TEAM_NAME = "Test Pilots";
    /** Ticks to let a moved camera, and the flags in view, settle before a photograph. */
    private static final int SHOT_TICKS = 30;

    private record Shot(String name, double x, double eyeY, double z, float yaw, boolean hud) {
    }

    private int stage, ticks, timeout, shot;
    private BlockPos ours, theirs;
    private UUID player;
    private final List<Shot> shots = new ArrayList<>();
    private final List<String> failures = new ArrayList<>();
    private volatile boolean serverHasDesign, serverHasName, planted;
    private volatile String plantedOwner = "";

    @Override
    public void onInitializeClient() {
        if (Boolean.getBoolean("galacticraft.team-flag.client-test")) ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    /** Hoist-top red, fly-top blue, hoist-bottom green, fly-bottom yellow: any flip or turn shows. */
    private static int[] quartered() {
        int[] design = new int[TeamFlagImage.WIDTH * TeamFlagImage.HEIGHT];
        for (int y = 0; y < TeamFlagImage.HEIGHT; y++) {
            for (int x = 0; x < TeamFlagImage.WIDTH; x++) {
                boolean top = y < TeamFlagImage.HEIGHT / 2, hoist = x < TeamFlagImage.WIDTH / 2;
                design[y * TeamFlagImage.WIDTH + x] = top ? (hoist ? RED : BLUE) : (hoist ? GREEN : YELLOW);
            }
        }
        return design;
    }

    private void tick(Minecraft client) {
        if (++timeout > 6000) throw new AssertionError("Team flag check timed out in stage " + stage + ": " + failures);
        if (stage >= 2 && client.screen instanceof PauseScreen) client.setScreen(null);
        if (client.getOverlay() != null || SpaceflightTransition.isActive()) return;
        if (stage == 0) {
            if (client.screen == null) return;
            stage = 1;
            saveDesign();
            org.lwjgl.glfw.GLFW.glfwHideWindow(client.getWindow().getWindow());
            org.lwjgl.glfw.GLFW.glfwSetWindowSize(client.getWindow().getWindow(), 1280, 720);
            client.options.renderDistance().set(4);
            client.options.pauseOnLostFocus = false;
            client.options.fov().set(50);
            client.options.tutorialStep = TutorialSteps.NONE;
            client.createWorldOpenFlows().createFreshLevel("team-flag-" + System.currentTimeMillis(),
                    new LevelSettings("Team Flag Check", GameType.CREATIVE, false, Difficulty.PEACEFUL,
                            true, new GameRules(), WorldDataConfiguration.DEFAULT), new WorldOptions(73, false, false),
                    registries -> registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT)
                            .value().createWorldDimensions(), client.screen);
            return;
        }
        if (client.player == null || client.level == null || client.getSingleplayerServer() == null || client.screen != null) return;
        var server = client.getSingleplayerServer();

        if (stage == 1) {
            // Joining should already have shared the saved design; the team name comes from the menu's name box.
            player = client.player.getUUID();
            client.options.setCameraType(CameraType.FIRST_PERSON);
            NetworkManager.sendToServer(new TeamNamePayload(TEAM_NAME));
            stage = 2;
            ticks = 0;
            return;
        }
        if (stage == 2) {
            if (++ticks % 10 == 0) {
                server.execute(() -> {
                    SpaceRaceTeams teams = SpaceRaceTeams.get(server);
                    serverHasDesign = Arrays.equals(TeamFlagImage.toRgb(quartered()), teams.flag(player));
                    serverHasName = TEAM_NAME.equals(teams.name(player));
                });
            }
            if (!serverHasDesign || !serverHasName) {
                if (ticks > 400) {
                    throw new AssertionError("The server never got the player's saved flag (" + serverHasDesign
                            + ") or team name (" + serverHasName + ") after joining");
                }
                return;
            }
            Constant.LOGGER.info("TEAM FLAG: joining shared the saved design and the team name with the server");
            plant(client);
            stage = 3;
            ticks = 0;
            return;
        }
        if (stage == 3) {
            // The client has to learn both flags' teams from the world and fetch our design from the server.
            boolean synced = client.level.getBlockEntity(ours) instanceof FlagBlockEntity flag && flag.getTeam() != null
                    && client.level.getBlockEntity(theirs) instanceof FlagBlockEntity other && other.getTeam() != null;
            if (!planted || !synced || !TeamFlagTextures.hasDesign(player)) {
                if (++ticks > 400) throw new AssertionError("The planted team flag never showed its design (planted "
                        + planted + ", synced " + synced + ", design " + TeamFlagTextures.hasDesign(player) + ")");
                return;
            }
            if (!plantedOwner.equals(player + "/" + TEAM_NAME)) {
                failures.add("planting an unclaimed team flag did not make it the planter's team's: " + plantedOwner);
            }
            checkTexture(client);
            stage = 4;
            ticks = 0;
            return;
        }
        if (stage == 4) {
            Shot view = shots.get(shot);
            if (ticks == 0) {
                client.options.hideGui = !view.hud();
                server.execute(() -> {
                    ServerPlayer serverPlayer = server.getPlayerList().getPlayer(player);
                    serverPlayer.getAbilities().flying = true;
                    serverPlayer.onUpdateAbilities();
                    serverPlayer.teleportTo(serverPlayer.serverLevel(), view.x(), view.eyeY() - serverPlayer.getEyeHeight(), view.z(), view.yaw(), 0.0F);
                });
            }
            client.player.setYRot(view.yaw());
            client.player.setXRot(0.0F);
            if (++ticks == SHOT_TICKS) {
                Screenshot.grab(client.gameDirectory, "team-flag-" + view.name() + ".png", client.getMainRenderTarget(), ignored -> {});
            }
            if (ticks < SHOT_TICKS + 5) return;
            ticks = 0;
            if (++shot < shots.size()) return;

            if (!failures.isEmpty()) throw new AssertionError("Team flag check failed: " + String.join("; ", failures));
            Constant.LOGGER.info("TEAM FLAG CLIENT CHECK PASSED");
            client.stop();
            stage = 5;
        }
    }

    /** Saves the quartered design where the space race menu saves the player's flag, before any world is joined. */
    private static void saveDesign() {
        int[] design = quartered();
        Path path = TeamFlagClient.savedFlagPath();
        try (NativeImage image = new NativeImage(TeamFlagImage.WIDTH, TeamFlagImage.HEIGHT, false)) {
            for (int y = 0; y < TeamFlagImage.HEIGHT; y++) {
                for (int x = 0; x < TeamFlagImage.WIDTH; x++) {
                    image.setPixelRGBA(x, y, TeamFlagImage.swapRedBlue(design[y * TeamFlagImage.WIDTH + x]));
                }
            }
            Files.createDirectories(path.getParent());
            image.writeToFile(path);
        } catch (IOException e) {
            throw new AssertionError("Could not save the test flag design", e);
        }
    }

    /**
     * Plants an unclaimed team flag the way a player does, facing south so it flies west, and beside it a flag of a
     * team that never designed one. Lines up a photograph of each side of ours, of theirs, and of one in hand.
     */
    private void plant(Minecraft client) {
        BlockPos feet = client.player.blockPosition();
        ours = feet.offset(0, 0, 5);
        theirs = feet.offset(6, 0, 5);
        double clothX = ours.getX() - 0.3, clothY = ours.getY() + 2.35, clothZ = ours.getZ() + 0.5;
        shots.add(new Shot("south-side", clothX, clothY, clothZ + 3.2, 180.0F, false));
        shots.add(new Shot("north-side", clothX, clothY, clothZ - 3.2, 0.0F, false));
        shots.add(new Shot("no-design", theirs.getX() - 0.3, clothY, clothZ + 3.2, 180.0F, false));
        shots.add(new Shot("in-hand", feet.getX() + 0.5, feet.getY() + 1.62, feet.getZ() + 0.5, 0.0F, true));

        var server = client.getSingleplayerServer();
        server.execute(() -> {
            ServerPlayer serverPlayer = server.getPlayerList().getPlayer(player);
            serverPlayer.setYRot(0.0F);
            serverPlayer.setYHeadRot(0.0F);
            plantOne(serverPlayer, ours, FlagItem.teamFlag());

            ItemStack ghost = FlagItem.teamFlag();
            ghost.set(GCDataComponents.TEAM_FLAG, new TeamFlagOwner(UUID.randomUUID(), "Ghost Team"));
            plantOne(serverPlayer, theirs, ghost);

            serverPlayer.setItemInHand(InteractionHand.MAIN_HAND, FlagItem.teamFlag());
            if (serverPlayer.serverLevel().getBlockEntity(ours) instanceof FlagBlockEntity flag && flag.getTeam() != null) {
                plantedOwner = flag.getTeam().id() + "/" + flag.getTeam().name();
            }
            planted = true;
        });
    }

    private static void plantOne(ServerPlayer player, BlockPos at, ItemStack stack) {
        BlockPos floor = at.below();
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(floor).add(0.0, 0.5, 0.0), Direction.UP, floor, false);
        stack.useOn(new UseOnContext(player.serverLevel(), player, InteractionHand.MAIN_HAND, stack, hit));
    }

    /** Reads the client's own texture of our flag back, at the corners the layout puts each quarter. */
    private void checkTexture(Minecraft client) {
        AbstractTexture texture = client.getTextureManager().getTexture(TeamFlagTextures.texture(player));
        if (!(texture instanceof DynamicTexture dynamic) || dynamic.getPixels() == null) {
            failures.add("our flag's texture was not a drawn design: " + texture);
            return;
        }
        NativeImage pixels = dynamic.getPixels();
        expect(pixels, 98, 5, RED, "front, top at the pole");
        expect(pixels, 98, 146, BLUE, "front, top at the fly");
        expect(pixels, 5, 5, GREEN, "front, bottom at the pole");
        expect(pixels, 5, 146, YELLOW, "front, bottom at the fly");
        expect(pixels, 105, 5, RED, "back, top at the pole");
        expect(pixels, 198, 146, YELLOW, "back, bottom at the fly");
    }

    private void expect(NativeImage pixels, int x, int y, int argb, String where) {
        int actual = TeamFlagImage.swapRedBlue(pixels.getPixelRGBA(x, y));
        if (actual != argb) failures.add(String.format("texel %s was %08X, not %08X", where, actual, argb));
    }
}
