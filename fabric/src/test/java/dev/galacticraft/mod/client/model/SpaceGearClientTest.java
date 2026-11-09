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

import dev.galacticraft.impl.internal.accessor.SpaceEvolvedAccessor;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.client.render.dimension.SpaceflightTransition;
import dev.galacticraft.mod.content.GCEntityTypes;
import dev.galacticraft.mod.content.entity.SpaceEvolution;
import dev.galacticraft.mod.util.DimensionTime;
import dev.galacticraft.mod.world.dimension.GCDimensions;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Captures fitted space gear from both sides of each vanilla model family on the Moon.
 * Hostile mobs evolve naturally; other models are equipped directly to check their fit.
 * Run with {@code gradlew :fabric:runClient -PspaceGearClientTest -Prei=false -Pastrocraft=false}.
 * Screenshots: {@code fabric/build/space-gear-client-test/screenshots}.
 */
public final class SpaceGearClientTest implements ClientModInitializer {
    private static final int FLOOR_Y = 200;
    private static final int SPACING = 8;
    private static final int SETTLE_TICKS = 60;

    private record Subject(String name, EntityType<? extends Mob> type, boolean baby) {}

    private static final List<Subject> VANILLA = List.of(
            new Subject("zombie", EntityType.ZOMBIE, false),
            new Subject("baby_zombie", EntityType.ZOMBIE, true),
            new Subject("skeleton", EntityType.SKELETON, false),
            new Subject("creeper", EntityType.CREEPER, false),
            new Subject("spider", EntityType.SPIDER, false),
            new Subject("enderman", EntityType.ENDERMAN, false),
            new Subject("pillager", EntityType.PILLAGER, false),
            new Subject("witch", EntityType.WITCH, false),
            new Subject("piglin", EntityType.PIGLIN, false),
            new Subject("blaze", EntityType.BLAZE, false),
            new Subject("slime", EntityType.SLIME, false),
            new Subject("cow", EntityType.COW, false),
            new Subject("horse", EntityType.HORSE, false),
            new Subject("chicken", EntityType.CHICKEN, false),
            new Subject("iron_golem", EntityType.IRON_GOLEM, false),
            new Subject("evolved_zombie_control", GCEntityTypes.EVOLVED_ZOMBIE, false));

    /** The test mod's GeckoLib mob, registered only with {@code -PgeckolibRuntime}. */
    private static final ResourceLocation GECKO_DUMMY = ResourceLocation.fromNamespaceAndPath("galacticraft-test", "geo_dummy");

    private enum View {FRONT, BACK}

    private List<Subject> subjects = VANILLA;

    private int stage, subject, view, ticks, timeout;
    private final List<String> problems = new ArrayList<>();

    @Override
    @SuppressWarnings("unchecked")
    public void onInitializeClient() {
        if (!Boolean.getBoolean("galacticraft.space-gear.client-test")) return;
        BuiltInRegistries.ENTITY_TYPE.getOptional(GECKO_DUMMY).ifPresent(type -> {
            this.subjects = new ArrayList<>(VANILLA);
            this.subjects.add(new Subject("geckolib_dummy", (EntityType<? extends Mob>) type, false));
        });
        Constant.LOGGER.info("SPACE GEAR CLIENT CHECK: {} subjects, GeckoLib dummy {}", this.subjects.size(),
                this.subjects.size() > VANILLA.size() ? "included" : "absent");
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private void tick(Minecraft client) {
        if (++timeout > 12000) throw new AssertionError("Space gear check timed out in stage " + stage + " at " + subject);
        if (stage >= 2 && client.screen instanceof PauseScreen) client.setScreen(null);
        if (client.getOverlay() != null || SpaceflightTransition.isActive()) return;
        if (stage == 0) {
            if (client.screen == null) return;
            stage = 1;
            org.lwjgl.glfw.GLFW.glfwHideWindow(client.getWindow().getWindow());
            org.lwjgl.glfw.GLFW.glfwSetWindowSize(client.getWindow().getWindow(), 960, 720);
            client.options.renderDistance().set(4);
            client.options.pauseOnLostFocus = false;
            client.options.hideGui = true;
            client.options.tutorialStep = TutorialSteps.NONE;
            GameRules rules = new GameRules();
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            client.createWorldOpenFlows().createFreshLevel("space-gear-" + System.currentTimeMillis(),
                    new LevelSettings("Space Gear Check", GameType.CREATIVE, false, Difficulty.NORMAL,
                            true, rules, WorldDataConfiguration.DEFAULT), new WorldOptions(91, false, false),
                    registries -> registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT)
                            .value().createWorldDimensions(), client.screen);
            return;
        }
        if (client.player == null || client.level == null || client.getSingleplayerServer() == null) return;
        if (stage == 1) {
            if (client.screen != null) return;
            client.options.setCameraType(CameraType.FIRST_PERSON);
            client.getSingleplayerServer().execute(() -> {
                ServerLevel moon = client.getSingleplayerServer().getLevel(GCDimensions.MOON);
                if (moon == null) throw new AssertionError("The integrated server has no Moon");
                moon.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, moon.getServer());
                moon.setDayTime(DimensionTime.sameTimeOfDay(6000, DimensionTime.dayLength(moon)));
                stageAll(moon);
            });
            stage = 2;
            moveTo(client);
            return;
        }

        if (!client.level.dimension().equals(GCDimensions.MOON) || client.screen != null) return;
        if (++ticks < SETTLE_TICKS) return;
        ticks = 0;
        Subject here = this.subjects.get(subject);
        if (here.name().equals("geckolib_dummy") && view == 0) checkGeckoLayer(client);
        String name = "space-gear-" + here.name() + "-" + View.values()[view].name().toLowerCase(Locale.ROOT) + ".png";
        Screenshot.grab(client.gameDirectory, name, client.getMainRenderTarget(), ignored -> {});

        if (++view >= View.values().length) {
            view = 0;
            subject++;
        }
        if (subject < this.subjects.size()) {
            moveTo(client);
            return;
        }
        if (!problems.isEmpty()) throw new AssertionError("Space gear check: " + String.join("; ", problems));
        Constant.LOGGER.info("SPACE GEAR CLIENT CHECK PASSED");
        client.stop();
        stage = 3;
    }

    /** GeckoLib collects layers through its own event; prove ours was added, not just that some gear shows. */
    private void checkGeckoLayer(Minecraft client) {
        var dummies = client.level.getEntities((Entity) null, client.player.getBoundingBox().inflate(16),
                entity -> BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).equals(GECKO_DUMMY));
        if (dummies.isEmpty()) {
            problems.add("the GeckoLib dummy never reached the client");
        } else if (!dev.galacticraft.test.geckolib.GeoDummyClient.rendererHasSpaceGear(dummies.getFirst())) {
            problems.add("the GeckoLib renderer has no space gear layer");
        } else {
            Constant.LOGGER.info("SPACE GEAR CLIENT CHECK: GeckoLib renderer carries the space gear layer");
        }
    }

    private void stageAll(ServerLevel moon) {
        for (int i = 0; i < this.subjects.size(); i++) {
            Subject subject = this.subjects.get(i);
            int x = i * SPACING;
            for (BlockPos pos : BlockPos.betweenClosed(x - 3, FLOOR_Y, -4, x + 3, FLOOR_Y, 4)) {
                moon.setBlock(pos, Blocks.SMOOTH_STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
            Mob mob = subject.type().create(moon);
            if (mob == null) throw new AssertionError("Could not create " + subject.name());
            mob.setNoAi(true);
            mob.setPersistenceRequired();
            mob.setBaby(subject.baby());
            mob.moveTo(x + 0.5, FLOOR_Y + 1, 0.5, 0.0F, 0.0F);
            mob.setYHeadRot(0.0F);
            mob.setYBodyRot(0.0F);
            moon.addFreshEntity(mob);

            boolean evolved = ((SpaceEvolvedAccessor) mob).galacticraft$isSpaceEvolved();
            boolean hostile = SpaceEvolution.shouldEvolve(moon, mob) || evolved;
            if (subject.type() == GCEntityTypes.EVOLVED_ZOMBIE) {
                if (evolved) problems.add("Galacticraft's own evolved zombie was evolved again");
            } else if (hostile && !evolved) {
                problems.add(subject.name() + " arrived on the Moon but did not evolve");
            } else if (!hostile) {
                // Not hostile, so it would never evolve by itself; dress it anyway to check the fit.
                SpaceEvolution.evolve(mob);
            }
        }
    }

    /** Puts the camera a few blocks in front of, or behind, the current subject and aims it at its chest. */
    private void moveTo(Minecraft client) {
        int index = subject;
        View facing = View.values()[view];
        client.getSingleplayerServer().execute(() -> {
            ServerLevel moon = client.getSingleplayerServer().getLevel(GCDimensions.MOON);
            var player = client.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
            double targetX = index * SPACING + 0.5, targetY = FLOOR_Y + 1.9, targetZ = 0.5;
            // Mobs face +Z (yaw 0). A three-quarter view from the front-right, or from the back-left.
            double camX = targetX + (facing == View.FRONT ? 1.8 : -1.8);
            double camZ = targetZ + (facing == View.FRONT ? 3.0 : -3.0);
            double camY = FLOOR_Y + 2.2;
            double dx = targetX - camX, dy = targetY - (camY + player.getEyeHeight()), dz = targetZ - camZ;
            float yaw = (float) (Mth.atan2(-dx, dz) * Mth.RAD_TO_DEG);
            float pitch = (float) (-Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG);
            player.teleportTo(moon, camX, camY, camZ, yaw, pitch);
        });
    }
}
