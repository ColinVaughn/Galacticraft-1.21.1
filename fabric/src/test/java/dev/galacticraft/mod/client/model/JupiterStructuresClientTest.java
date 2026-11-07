/*
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

import dev.galacticraft.mod.content.GCBlocks;
import dev.galacticraft.mod.content.block.jupiter.JupiterBlocks;
import dev.galacticraft.mod.content.entity.jupiter.StormheartLeviathanEntity;
import dev.galacticraft.mod.world.gen.feature.GCFeatures;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Captures generated Jupiter structures and block variants in a flat creative world.
 * Enable with {@code -PjupiterStructuresClientTest}; screenshots are written to
 * {@code fabric/build/jupiter-structures-client-test/screenshots/}.
 */
public class JupiterStructuresClientTest implements ClientModInitializer {
    private static final int Y = 226;
    /** A capture; {@code stage} runs on the server {@value #STAGE_LEAD} ticks before the screenshot, to catch an animation mid-play. */
    private record Shot(String name, Vec3 camera, Vec3 target, boolean night, java.util.function.Consumer<ServerLevel> stage, boolean jupiter) {
        Shot(String name, Vec3 camera, Vec3 target) {
            this(name, camera, target, false, null, false);
        }

        Shot(String name, Vec3 camera, Vec3 target, boolean night) {
            this(name, camera, target, night, null, false);
        }

        Shot(String name, Vec3 camera, Vec3 target, boolean night, java.util.function.Consumer<ServerLevel> stage) {
            this(name, camera, target, night, stage, false);
        }

        /** A look down into Jupiter's depths from {@code y}. */
        static Shot jupiterDepth(String name, double y) {
            return new Shot(name, new Vec3(8.5, y, 8.5), new Vec3(14.5, y - 6, 20.5), false, null, true);
        }
    }
    private static final int STAGE_LEAD = 8;

    private boolean opened, built;
    private int ticks, shot;
    private List<Shot> shots;

    @Override
    public void onInitializeClient() {
        if (!Boolean.getBoolean("galacticraft.jupiter-structures.client-test")) return;
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.getOverlay() != null) return;
            if (!this.opened && client.screen != null) {
                this.opened = true;
                client.options.renderDistance().set(8);
                client.options.pauseOnLostFocus = false;
                client.options.hideGui = true;
                client.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
                client.createWorldOpenFlows().createFreshLevel("jupiter-structures-" + System.currentTimeMillis(),
                        new LevelSettings("Jupiter Structures", GameType.CREATIVE, false, Difficulty.NORMAL,
                                true, new GameRules(), WorldDataConfiguration.DEFAULT), new WorldOptions(23, false, false),
                        registry -> registry.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT)
                                .value().createWorldDimensions(), client.screen);
                return;
            }
            if (client.level == null || client.player == null || client.getSingleplayerServer() == null || client.screen != null) return;
            if (!this.built) {
                this.built = true;
                String only = System.getProperty("galacticraft.client-test.only");
                client.getSingleplayerServer().execute(() -> this.shots = build(client.getSingleplayerServer().overworld()).stream()
                        .filter(shot -> only == null || shot.name().contains(only)).toList());
                return;
            }
            if (this.shots == null) return;
            this.ticks++;
            if (this.shot >= this.shots.size()) {
                if (this.ticks > 20) {
                    LoggerFactory.getLogger("JupiterStructuresClientTest").info("JUPITER STRUCTURES CLIENT CHECK PASSED: {} captures", this.shots.size());
                    client.stop();
                }
                return;
            }
            Shot current = this.shots.get(this.shot);
            if (this.ticks == 1) {
                Vec3 d = current.target().subtract(current.camera());
                float yaw = (float) Math.toDegrees(Math.atan2(d.z, d.x)) - 90.0F;
                float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
                client.getSingleplayerServer().execute(() -> {
                    // Night shots show what the emissive layers really light up.
                    client.getSingleplayerServer().overworld().setDayTime(current.night() ? 18000 : 6000);
                    var player = client.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                    player.getAbilities().flying = true;
                    player.onUpdateAbilities();
                    ServerLevel world = current.jupiter()
                            ? client.getSingleplayerServer().getLevel(dev.galacticraft.mod.world.dimension.GCDimensions.JUPITER)
                            : client.getSingleplayerServer().overworld();
                    if (world == null) throw new AssertionError("Jupiter did not load");
                    player.teleportTo(world, current.camera().x, current.camera().y - 1.62, current.camera().z, yaw, pitch);
                });
            } else if (this.ticks == 70 - STAGE_LEAD && current.stage() != null) {
                client.getSingleplayerServer().execute(() -> current.stage().accept(client.getSingleplayerServer().overworld()));
            } else if (this.ticks == 70) {
                Screenshot.grab(client.gameDirectory, "jupiter-" + current.name() + ".png", client.getMainRenderTarget(),
                        message -> LoggerFactory.getLogger("JupiterStructuresClientTest").info("{}", message.getString()));
            } else if (this.ticks > 75) {
                this.shot++;
                this.ticks = 0;
            }
        });
    }

    private static List<Shot> build(ServerLevel level) {
        level.setDayTime(6000);
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, level.getServer());

        BlockPos station = new BlockPos(0, Y, 0);
        for (BlockPos pos : BlockPos.betweenClosed(station.offset(-10, -2, -10), station.offset(10, -1, 10))) {
            level.setBlock(pos, GCBlocks.DENSE_CLOUD.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        place(level, GCFeatures.JUPITER_WEATHER_STATION, station);
        BlockPos wreck = new BlockPos(64, Y, 0);
        place(level, GCFeatures.JUPITER_SURVEY_WRECK, wreck);
        BlockPos harvester = new BlockPos(128, Y, 0);
        place(level, GCFeatures.JUPITER_STORM_HARVESTER, harvester);
        BlockPos facility = new BlockPos(0, Y, 96);
        place(level, GCFeatures.JUPITER_RESEARCH_FACILITY, facility);
        BlockPos citadel = new BlockPos(96, Y, 96);
        place(level, GCFeatures.STORMHEART_CITADEL, citadel);

        // Lineup: buoyancy cells (stacked and alone), every stair and slab, and hanging/upright clusters.
        BlockPos lineup = new BlockPos(-64, Y, 0);
        for (BlockPos pos : BlockPos.betweenClosed(lineup.offset(-2, -1, -3), lineup.offset(16, -1, 3))) {
            level.setBlock(pos, JupiterBlocks.AEROSTAT_GRATING.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        for (int x = 0; x < 2; x++) for (int y = 0; y < 2; y++) for (int z = 0; z < 2; z++) {
            level.setBlock(lineup.offset(x, y, z - 2), JupiterBlocks.BUOYANCY_CELL.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        level.setBlock(lineup.offset(3, 0, -1), JupiterBlocks.BUOYANCY_CELL.defaultBlockState(), Block.UPDATE_CLIENTS);
        int x = 5;
        for (var variant : JupiterBlocks.VARIANT_BASES.entrySet()) {
            Block block = variant.getKey();
            if (block instanceof StairBlock) {
                level.setBlock(lineup.offset(x, 0, -1), block.defaultBlockState().setValue(StairBlock.FACING, net.minecraft.core.Direction.SOUTH), Block.UPDATE_ALL);
                level.setBlock(lineup.offset(x, 0, -2), variant.getValue().defaultBlockState(), Block.UPDATE_CLIENTS);
            } else {
                level.setBlock(lineup.offset(x, 0, -1), block.defaultBlockState(), Block.UPDATE_CLIENTS);
                level.setBlock(lineup.offset(x, 1, -2), block.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP), Block.UPDATE_CLIENTS);
                x++;
            }
            x++;
        }
        level.setBlock(lineup.offset(1, 3, 1), GCBlocks.DENSE_CLOUD.defaultBlockState(), Block.UPDATE_CLIENTS);
        level.setBlock(lineup.offset(1, 2, 1), JupiterBlocks.HYDROSULFIDE_CRYSTAL_CLUSTER.defaultBlockState()
                .setValue(JupiterBlocks.Cluster.HANGING, true), Block.UPDATE_CLIENTS);
        level.setBlock(lineup.offset(1, 0, 1), JupiterBlocks.CHARGED_CONDENSATE_CLUSTER.defaultBlockState(), Block.UPDATE_CLIENTS);

        // Creatures, frozen in place (their clips still play) and facing the camera's left.
        BlockPos zoo = new BlockPos(-64, Y, 48);
        for (BlockPos pos : BlockPos.betweenClosed(zoo.offset(-8, -1, -4), zoo.offset(8, -1, 4))) {
            level.setBlock(pos, GCBlocks.DENSE_CLOUD.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        creature(level, dev.galacticraft.mod.content.GCEntityTypes.CLOUD_RAY, zoo.offset(-5, 2, 0), 90);
        creature(level, dev.galacticraft.mod.content.GCEntityTypes.STATIC_JELLY, zoo.offset(0, 1, 0), 90);
        creature(level, dev.galacticraft.mod.content.GCEntityTypes.SALVAGE_DRONE, zoo.offset(4, 1, 0), 180);
        // A second trio, killed on cue so a capture lands halfway through their death clips.
        creature(level, dev.galacticraft.mod.content.GCEntityTypes.CLOUD_RAY, zoo.offset(-5, 2, -3), 90);
        creature(level, dev.galacticraft.mod.content.GCEntityTypes.STATIC_JELLY, zoo.offset(0, 1, -3), 90);
        creature(level, dev.galacticraft.mod.content.GCEntityTypes.SALVAGE_DRONE, zoo.offset(4, 1, -3), 180);
        // Curiosity rovers on a patch of Mars: calm, angry with its drill levelled, mid-survey and mid-strike. They have no
        // AI, so a staged pose holds still for the capture.
        BlockPos garage = new BlockPos(-160, Y, 48);
        for (BlockPos pos : BlockPos.betweenClosed(garage.offset(-10, -1, -6), garage.offset(10, -1, 6))) {
            level.setBlock(pos, GCBlocks.MARS_SURFACE_ROCK.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        creature(level, dev.galacticraft.mod.content.GCEntityTypes.CURIOSITY_ROVER, garage.offset(-6, 0, 0), 25);
        creature(level, dev.galacticraft.mod.content.GCEntityTypes.CURIOSITY_ROVER, garage.offset(-2, 0, 0), 25);
        creature(level, dev.galacticraft.mod.content.GCEntityTypes.CURIOSITY_ROVER, garage.offset(2, 0, 0), 25);
        creature(level, dev.galacticraft.mod.content.GCEntityTypes.CURIOSITY_ROVER, garage.offset(6, 0, 0), 25);
        BlockPos lair = new BlockPos(-64, Y, 112);
        creature(level, dev.galacticraft.mod.content.GCEntityTypes.STORMHEART_LEVIATHAN, lair, 90);

        return List.of(
                new Shot("blocks", Vec3.atCenterOf(lineup).add(7, 4, 9), Vec3.atCenterOf(lineup).add(7, 0, -1)),
                new Shot("buoyancy-cells", Vec3.atCenterOf(lineup).add(5, 3.5, 3), Vec3.atCenterOf(lineup).add(1.5, 0.5, -1)),
                new Shot("weather-station", Vec3.atCenterOf(station).add(14, 10, 14), Vec3.atCenterOf(station).add(0, 4, 0)),
                new Shot("survey-wreck", Vec3.atCenterOf(wreck).add(13, 7, 13), Vec3.atCenterOf(wreck).add(0, 3, 0)),
                new Shot("storm-harvester", Vec3.atCenterOf(harvester).add(14, 6, 14), Vec3.atCenterOf(harvester).add(0, 0, 0)),
                new Shot("research-facility", Vec3.atCenterOf(facility).add(24, 12, 24), Vec3.atCenterOf(facility).add(0, 4, 0)),
                new Shot("research-facility-hub", Vec3.atCenterOf(facility).add(-3, 2, -3), Vec3.atCenterOf(facility).add(3, 1.5, 3)),
                new Shot("creatures", Vec3.atCenterOf(zoo).add(0, 2.5, 6), Vec3.atCenterOf(zoo).add(0, 1, 0)),
                new Shot("cloud-ray", Vec3.atCenterOf(zoo).add(-3, 4, 3), Vec3.atCenterOf(zoo).add(-5, 1.8, 0)),
                new Shot("static-jelly", Vec3.atCenterOf(zoo).add(1.2, 1.6, 2.6), Vec3.atCenterOf(zoo).add(0, 1.2, 0)),
                new Shot("salvage-drone", Vec3.atCenterOf(zoo).add(4.6, 1.3, -1.6), Vec3.atCenterOf(zoo).add(4, 0.8, 0)),
                new Shot("stormheart-leviathan", Vec3.atCenterOf(lair).add(-4, 5, 12), Vec3.atCenterOf(lair).add(-1, 1.5, 0)),
                new Shot("creatures-hurt", Vec3.atCenterOf(zoo).add(0, 2.5, 6), Vec3.atCenterOf(zoo).add(0, 1, 0), false,
                        world -> world.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, new net.minecraft.world.phys.AABB(zoo).inflate(7, 3, 1))
                                .forEach(mob -> mob.hurt(world.damageSources().generic(), 1))),
                new Shot("creatures-dying", Vec3.atCenterOf(zoo).add(0, 2.5, 3), Vec3.atCenterOf(zoo).add(0, 1, -3), false,
                        world -> world.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, new net.minecraft.world.phys.AABB(zoo.offset(0, 0, -3)).inflate(7, 3, 0.5))
                                .forEach(mob -> mob.hurt(world.damageSources().genericKill(), Float.MAX_VALUE))),
                new Shot("leviathan-roar", Vec3.atCenterOf(lair).add(-6, 4, 14), Vec3.atCenterOf(lair).add(-2, 2, 0), false,
                        world -> leviathanAt(world, lair).ifPresent(l -> { l.showPose(StormheartLeviathanEntity.Action.ROAR, 16); l.roar(world); })),
                new Shot("leviathan-breath-charge", Vec3.atCenterOf(lair).add(-10, 3, 9), Vec3.atCenterOf(lair).add(-5, 2, 0), true,
                        world -> leviathanAt(world, lair).ifPresent(l -> {
                            l.showPose(StormheartLeviathanEntity.Action.BREATH_CHARGE, 26);
                            for (int i = 14; i <= 26; i += 2) l.showCharge(world, i);
                        })),
                new Shot("leviathan-breath", Vec3.atCenterOf(lair).add(-6, 6, 16), Vec3.atCenterOf(lair).add(-10, 1, 0), true,
                        world -> leviathanAt(world, lair).ifPresent(l -> {
                            l.showPose(StormheartLeviathanEntity.Action.BREATH, 3);
                            l.breathe(world, l.mouth().add(-30, -4, 2));
                        })),
                new Shot("leviathan-tail-slam", Vec3.atCenterOf(lair).add(2, 14, 10), Vec3.atCenterOf(lair).add(2, 0, 0), false,
                        world -> leviathanAt(world, lair).ifPresent(l -> { l.showPose(StormheartLeviathanEntity.Action.TAIL_SLAM, 13); l.tailSweep(world); })),
                new Shot("cloud-ray-night", Vec3.atCenterOf(zoo).add(-3, 4, 3), Vec3.atCenterOf(zoo).add(-5, 1.8, 0), true),
                new Shot("static-jelly-night", Vec3.atCenterOf(zoo).add(1.2, 1.2, 2.6), Vec3.atCenterOf(zoo).add(0, 1.2, 0), true),
                new Shot("salvage-drone-night", Vec3.atCenterOf(zoo).add(4.6, 1.3, -1.6), Vec3.atCenterOf(zoo).add(4, 0.8, 0), true),
                new Shot("stormheart-leviathan-night", Vec3.atCenterOf(lair).add(-4, 5, 12), Vec3.atCenterOf(lair).add(0, 1.5, 0), true),
                new Shot("leviathan-head", Vec3.atCenterOf(lair).add(-11, 2.5, 4), Vec3.atCenterOf(lair).add(-6, 1.6, 0)),
                new Shot("rover-lineup", Vec3.atCenterOf(garage).add(0, 3, 9), Vec3.atCenterOf(garage).add(0, 0, 0), false, world -> rovers(world, garage)),
                new Shot("rover-calm", Vec3.atCenterOf(garage).add(-7.6, 1.4, 3.0), Vec3.atCenterOf(garage).add(-6, 0.3, 0), false, world -> rovers(world, garage)),
                new Shot("rover-angry", Vec3.atCenterOf(garage).add(-3.6, 1.3, 3.0), Vec3.atCenterOf(garage).add(-2, 0.4, 0), false, world -> rovers(world, garage)),
                new Shot("rover-survey", Vec3.atCenterOf(garage).add(4.4, 1.2, 2.2), Vec3.atCenterOf(garage).add(2, 0.2, 0), false, world -> rovers(world, garage)),
                new Shot("rover-attack", Vec3.atCenterOf(garage).add(4.6, 1.3, 3.2), Vec3.atCenterOf(garage).add(6, 0.5, 0), false, world -> rovers(world, garage)),
                new Shot("rover-angry-night", Vec3.atCenterOf(garage).add(-3.6, 1.3, 3.0), Vec3.atCenterOf(garage).add(-2, 0.4, 0), true, world -> rovers(world, garage)),
                new Shot("rover-calm-night", Vec3.atCenterOf(garage).add(-7.6, 1.4, 3.0), Vec3.atCenterOf(garage).add(-6, 0.3, 0), true, world -> rovers(world, garage)),
                Shot.jupiterDepth("jupiter-core-ember", -30),
                Shot.jupiterDepth("jupiter-core-molten", -80),
                Shot.jupiterDepth("jupiter-core-incandescent", -120),
                new Shot("jupiter-core-looking-up", new Vec3(8.5, -80, 8.5), new Vec3(9.5, -60, 20.5), false, null, true),
                new Shot("stormheart-citadel", Vec3.atCenterOf(citadel).add(22, 14, 22), Vec3.atCenterOf(citadel).add(0, 0, 0)));
    }

    /** Poses the four showcase rovers, west to east: calm, angry, surveying, striking. */
    private static void rovers(ServerLevel level, BlockPos garage) {
        var rovers = level.getEntitiesOfClass(dev.galacticraft.mod.content.entity.mars.CuriosityRoverEntity.class,
                new net.minecraft.world.phys.AABB(garage).inflate(9, 2, 2));
        rovers.sort(java.util.Comparator.comparingDouble(net.minecraft.world.entity.Entity::getX));
        if (rovers.size() != 4) throw new AssertionError("Expected 4 showcase rovers, found " + rovers.size());
        var survey = dev.galacticraft.mod.content.entity.mars.CuriosityRoverEntity.Action.SURVEY;
        var attack = dev.galacticraft.mod.content.entity.mars.CuriosityRoverEntity.Action.ATTACK;
        var none = dev.galacticraft.mod.content.entity.mars.CuriosityRoverEntity.Action.NONE;
        rovers.get(0).showPose(none, 0, false);
        rovers.get(1).showPose(none, 0, true);
        rovers.get(2).showPose(survey, 40, false);
        rovers.get(3).showPose(attack, 3, true);
    }

    private static java.util.Optional<StormheartLeviathanEntity> leviathanAt(ServerLevel level, BlockPos lair) {
        return level.getEntitiesOfClass(StormheartLeviathanEntity.class, new net.minecraft.world.phys.AABB(lair).inflate(6)).stream().findFirst();
    }

    private static void creature(ServerLevel level, net.minecraft.world.entity.EntityType<? extends net.minecraft.world.entity.Mob> type, BlockPos at, float yaw) {
        var mob = type.create(level);
        if (mob == null) throw new AssertionError("Could not create " + type);
        mob.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, yaw, 0);
        mob.setYBodyRot(yaw);
        mob.setYHeadRot(yaw);
        mob.setNoAi(true);
        mob.setPersistenceRequired();
        level.addFreshEntity(mob);
    }

    private static void place(ServerLevel level, Feature<NoneFeatureConfiguration> feature, BlockPos origin) {
        if (!feature.place(NoneFeatureConfiguration.INSTANCE, level, level.getChunkSource().getGenerator(), RandomSource.create(7L), origin)) {
            throw new AssertionError("Structure refused its showcase site: " + origin);
        }
    }
}
