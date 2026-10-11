package dev.galacticraft.mod.client.model;

import dev.galacticraft.mod.content.GCBlocks;
import dev.galacticraft.mod.content.GCEntityTypes;
import dev.galacticraft.mod.content.entity.BurrowerState;
import dev.galacticraft.mod.content.entity.LunarBurrowerEntity;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import org.slf4j.LoggerFactory;

/** Opt-in real renderer fixtures; worlds and screenshots stay inside the isolated build run directory. */
public final class BurrowerVisualTest implements ClientModInitializer {
    private boolean opened, placed;
    private int ticks;
    private volatile LunarBurrowerEntity worm;
    private Vec3 rideStart;
    private Vec3 previousRiderPosition;
    private volatile LunarBurrowerEntity juvenile, adolescent, giant;

    @Override public void onInitializeClient() {
        boolean petTest = Boolean.getBoolean("galacticraft.burrower.pet-test");
        if (!petTest && !Boolean.getBoolean("galacticraft.burrower.visual-test")) return;
        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            previousRiderPosition = client.player == null ? null : client.player.position();
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.getOverlay() != null) return;
            if (!opened && client.screen != null) {
                opened = true;
                client.options.renderDistance().set(5);
                client.options.pauseOnLostFocus = false;
                client.options.hideGui = !petTest;
                client.createWorldOpenFlows().createFreshLevel((petTest ? "burrower-pet-" : "burrower-visual-") + System.currentTimeMillis(),
                        new LevelSettings(petTest ? "Burrower Pet Playground" : "Burrower Visual Check", GameType.CREATIVE, false, Difficulty.NORMAL,
                                true, new GameRules(), WorldDataConfiguration.DEFAULT), new WorldOptions(17, false, false),
                        registry -> registry.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT)
                                .value().createWorldDimensions(), client.screen);
                return;
            }
            if (client.level == null || client.player == null || client.getSingleplayerServer() == null) return;
            if (!placed) {
                placed = true;
                if (petTest) {
                    client.getSingleplayerServer().execute(() -> preparePetPlayground(client));
                    return;
                }
                client.getSingleplayerServer().execute(() -> {
                    var level = client.getSingleplayerServer().overworld();
                    level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, client.getSingleplayerServer());
                    level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, client.getSingleplayerServer());
                    level.setDayTime(6000);
                    for (int x = -12; x <= 12; x++) for (int z = -8; z <= 16; z++) {
                        for (int y = 65; y <= 76; y++) level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                        level.setBlock(new BlockPos(x, 64, z), GCBlocks.MOON_ROCK.defaultBlockState(), 2);
                        if (z >= 4) level.setBlock(new BlockPos(x, 65, z), GCBlocks.MOON_ROCK.defaultBlockState(), 2);
                    }
                    worm = new LunarBurrowerEntity(GCEntityTypes.LUNAR_BURROWER, level);
                    worm.moveTo(0.5, 68, 0.5, 180, 0);
                    worm.setDenPosition(new BlockPos(0, 65, 0));
                    level.addFreshEntity(worm);
                    var player = client.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                    player.getAbilities().flying = true;
                    player.onUpdateAbilities();
                    player.teleportTo(level, 7, 69, -3, 50, 27);
                });
                return;
            }
            if (petTest) return;
            if (worm == null || client.level.getEntity(worm.getId()) == null || client.screen != null) return;
            ticks++;
            if (ticks >= 615 && ticks <= 640 || ticks >= 735 && ticks <= 765 || ticks >= 820 && ticks <= 895) {
                if (previousRiderPosition == null || !(client.player.getVehicle() instanceof LunarBurrowerEntity mount))
                    throw new AssertionError("Missing mount during rider interpolation check");
                Vec3 oldPosition = new Vec3(client.player.xo, client.player.yo, client.player.zo);
                if (oldPosition.distanceTo(previousRiderPosition) > 0.001)
                    throw new AssertionError("Mount moved the rider before recording the previous camera position: "
                            + oldPosition + " instead of " + previousRiderPosition);
                Vec3 seated = client.player.position().add(client.player.getVehicleAttachmentPoint(mount));
                if (seated.distanceTo(mount.getSaddlePosition(1)) > 0.001)
                    throw new AssertionError("Smooth rider drifted off the saddle");
                if (ticks == 640 || ticks == 765 || ticks == 895)
                    LoggerFactory.getLogger("BurrowerVisualTest").info("BURROWER RIDER INTERPOLATION PASSED: tick {}", ticks);
            }
            if (ticks >= 800) {
                client.mouseHandler.releaseMouse();
                client.setCameraEntity(client.player);
                client.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
                client.player.setYRot(180);
                client.player.setXRot(ticks < 835 ? 45 : ticks < 865 ? 0 : -45);
                client.options.keyUp.setDown(ticks >= 810 && ticks < 900);
                if (ticks == 805 || ticks == 903) net.minecraft.client.KeyMapping.click(
                        com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM.getOrCreate(org.lwjgl.glfw.GLFW.GLFW_KEY_G));
                var rendered = (LunarBurrowerEntity)client.level.getEntity(worm.getId());
                if (ticks == 830) {
                    if (!rendered.isTunnelMode() || rendered.getY() > rideStart.y - 1.5)
                        throw new AssertionError("Rider key toggle and downward tunneling failed: mode=" + rendered.isTunnelMode() + ", position=" + rendered.position());
                    LoggerFactory.getLogger("BurrowerVisualTest").info("BURROWER DOWNWARD TUNNEL PASSED: {}", rendered.position());
                }
                if (ticks == 840 || ticks == 870) rideStart = rendered.position();
                if (ticks == 860 && (Math.abs(rendered.getY() - rideStart.y) > 0.8 || rendered.getZ() > rideStart.z - 2))
                    throw new AssertionError("Straight tunneling did not follow the rider's aim");
                if (ticks == 900) {
                    if (rendered.getY() < rideStart.y + 1.5) throw new AssertionError("Upward tunneling did not climb");
                    LoggerFactory.getLogger("BurrowerVisualTest").info("BURROWER STRAIGHT AND UPWARD TUNNELS PASSED: {}", rendered.position());
                    capture(client, "burrower-rider-tunneling.png");
                }
                if (ticks == 915) {
                    if (rendered.isTunnelMode()) throw new AssertionError("Second rider key press did not disable tunneling");
                    LoggerFactory.getLogger("BurrowerVisualTest").info("BURROWER TUNNEL CLIENT CHECK PASSED: toggle packet, aimed excavation and client/server movement");
                    client.stop();
                }
                return;
            }
            if (ticks >= 600) {
                if (ticks >= 710) {
                    if (ticks == 710) client.getSingleplayerServer().execute(() -> {
                        var player = client.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                        var level = client.getSingleplayerServer().overworld();
                        for (int x = -25; x <= -12; x++) for (int z = -90; z <= 30; z++)
                            level.setBlock(new BlockPos(x, 64, z), GCBlocks.MOON_ROCK.defaultBlockState(), 2);
                        giant.setNoAi(false);
                        player.getAbilities().flying = false; player.onUpdateAbilities();
                        for (var item : new net.minecraft.world.item.Item[]{dev.galacticraft.mod.content.item.GCItems.MOON_CHEESE_CURD,
                                net.minecraft.world.item.Items.SADDLE, net.minecraft.world.item.Items.CHEST}) {
                            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(item));
                            giant.mobInteract(player, net.minecraft.world.InteractionHand.MAIN_HAND);
                        }
                        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.ItemStack.EMPTY);
                        player.teleportTo(giant.getX(), giant.getY(), giant.getZ());
                        giant.mobInteract(player, net.minecraft.world.InteractionHand.MAIN_HAND);
                        rideStart = giant.position();
                    });
                    client.mouseHandler.releaseMouse();
                    client.player.setYRot(180); client.player.setXRot(0);
                    client.options.keyUp.setDown(ticks >= 720 && ticks < 770);
                    var rendered = (LunarBurrowerEntity)client.level.getEntity(giant.getId());
                    client.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
                    if (ticks >= 730 && ticks <= 770 && ticks % 10 == 0) capture(client, "burrower-giant-riding-wave-" + ticks + ".png");
                    if (ticks == 750) {
                        if (rendered.getBurrowerState() != BurrowerState.RIDE) throw new AssertionError("Giant did not use the riding cycle");
                        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
                        for (int section = 0; section < 12; section++) {
                            double x = rendered.bodyPosition(section).x;
                            minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                        }
                        if (maxX - minX < 0.8) throw new AssertionError("Giant's rear sections did not follow the riding wave");
                    }
                    if (ticks == 790) {
                        if (giant.position().subtract(rideStart).horizontalDistanceSqr() < 16 || client.player.getVehicle() == null)
                            throw new AssertionError("Giant riding failed");
                        LoggerFactory.getLogger("BurrowerVisualTest").info("BURROWER GIANT RIDE PASSED: twelve tapered sections, shared traveling wave and attached rider");
                        client.setCameraEntity(client.player);
                        client.getSingleplayerServer().execute(() -> {
                            var level = client.getSingleplayerServer().overworld();
                            var player = client.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                            player.stopRiding();
                            for (int x = -5; x <= 5; x++) for (int z = -65; z <= -8; z++) for (int y = 48; y <= (z <= -15 ? 82 : 64); y++)
                                level.setBlock(new BlockPos(x, y, z), GCBlocks.MOON_ROCK.defaultBlockState(), 2);
                            worm.discard();
                            worm = new LunarBurrowerEntity(GCEntityTypes.LUNAR_BURROWER, level);
                            worm.hatch(); worm.setGrowingTicks(0); worm.moveTo(0.5, 65, -10.5, 180, 0);
                            level.addFreshEntity(worm);
                            for (var item : new net.minecraft.world.item.Item[]{dev.galacticraft.mod.content.item.GCItems.MOON_CHEESE_CURD,
                                    net.minecraft.world.item.Items.SADDLE, net.minecraft.world.item.Items.CHEST}) {
                                player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(item));
                                worm.mobInteract(player, net.minecraft.world.InteractionHand.MAIN_HAND);
                            }
                            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.ItemStack.EMPTY);
                            player.teleportTo(worm.getX(), worm.getY(), worm.getZ());
                            worm.mobInteract(player, net.minecraft.world.InteractionHand.MAIN_HAND);
                        });
                        rideStart = new Vec3(0.5, 65, -10.5);
                    }
                    return;
                }
                client.mouseHandler.releaseMouse();
                client.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
                client.player.setYRot(180);
                client.player.setXRot(15);
                client.options.keyUp.setDown(ticks >= 610 && ticks < 645);
                if (ticks >= 615 && ticks <= 645) {
                    var rendered = (LunarBurrowerEntity)client.level.getEntity(worm.getId());
                    client.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
                    if (ticks >= 620 && ticks % 5 == 0) capture(client, "burrower-riding-wave-" + ticks + ".png");
                    if (ticks == 630 && rendered.getBurrowerState() != BurrowerState.RIDE)
                        throw new AssertionError("Mounted movement did not synchronize the riding animation");
                }
                if (ticks == 646) client.setCameraEntity(client.player);
                if (ticks == 625) capture(client, "burrower-companion-riding.png");
                if (ticks == 650) {
                    client.getSingleplayerServer().execute(() -> {
                        if (worm.position().subtract(rideStart).horizontalDistanceSqr() < 4 || worm.getControllingPassenger() == null)
                            throw new AssertionError("Saddled companion did not respond to client riding input");
                        LoggerFactory.getLogger("BurrowerVisualTest").info("BURROWER COMPANION RIDE PASSED: {} horizontal blocks", worm.position().subtract(rideStart).horizontalDistance());
                    });
                }
                if (ticks == 660) {
                    client.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
                    client.getSingleplayerServer().execute(() -> {
                        var player = client.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                        player.stopRiding();
                        player.getAbilities().flying = true; player.onUpdateAbilities();
                        player.teleportTo(24, 82, -19);
                        juvenile.setGrowthAge(0);
                        adolescent = new LunarBurrowerEntity(GCEntityTypes.LUNAR_BURROWER, worm.level());
                        adolescent.hatch(); adolescent.setGrowthAge(6000); adolescent.moveTo(-7, 65, 0, 180, 0); adolescent.setNoAi(true);
                        worm.level().addFreshEntity(adolescent);
                        giant = new LunarBurrowerEntity(GCEntityTypes.LUNAR_BURROWER, worm.level());
                        giant.hatch(); giant.setGrowthAge(dev.galacticraft.mod.util.BurrowerGrowth.MAX_AGE);
                        giant.moveTo(-18, 65, -15, 180, 0); giant.setNoAi(true); worm.level().addFreshEntity(giant);
                        for (int x = -25; x <= -12; x++) for (int z = -25; z <= 30; z++)
                            ((net.minecraft.server.level.ServerLevel)worm.level()).setBlock(new BlockPos(x,64,z), GCBlocks.MOON_ROCK.defaultBlockState(),2);
                    });
                }
                if (ticks > 660) {
                    client.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
                    client.player.setPos(24, 82, -19); client.player.setYRot(58); client.player.setXRot(24);
                    client.player.setDeltaMovement(Vec3.ZERO);
                    if (ticks == 690) capture(client, "burrower-growth-stages.png");
                    if (ticks == 705) {
                        var rendered = (LunarBurrowerEntity)client.level.getEntity(giant.getId());
                        if (rendered == null || rendered.getBodySectionCount() != 12 || rendered.getModelScale() != 3)
                            throw new AssertionError("Giant growth did not synchronize to the renderer");
                        LoggerFactory.getLogger("BurrowerVisualTest").info("BURROWER GROWTH VISUAL PASSED: juvenile, adolescent, adult, twelve-section giant");
                    }
                }
                return;
            }
            // Keep this isolated fixture's camera independent of gravity and foreground mouse input.
            client.mouseHandler.releaseMouse();
            client.player.getAbilities().flying = true;
            client.player.setDeltaMovement(Vec3.ZERO);
            boolean movingTurn = ticks >= 175 && ticks < 315;
            client.player.setPos(movingTurn ? 11 : 7, movingTurn ? 72 : 69, movingTurn ? -7 : -3);
            client.player.setYRot(movingTurn ? 68 : 50);
            client.player.setXRot(movingTurn ? 34 : 27);
            client.player.yRotO = client.player.getYRot();
            client.player.xRotO = client.player.getXRot();
            if (ticks == 50) client.getSingleplayerServer().execute(() -> worm.setNoAi(true));
            if (ticks == 100) capture(client, "burrower-ledge-idle.png");
            if (ticks == 115) client.getSingleplayerServer().execute(() -> {
                try {
                    var begin = LunarBurrowerEntity.class.getDeclaredMethod("begin", BurrowerState.class);
                    begin.setAccessible(true);
                    begin.invoke(worm, BurrowerState.WARNING);
                } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
            });
            if (ticks == 127) capture(client, "burrower-ledge-warning.png");
            if (ticks == 128) {
                var rendered = (LunarBurrowerEntity)client.level.getEntity(worm.getId());
                LoggerFactory.getLogger("BurrowerVisualTest").info("Visual fixture state={}, age={}, headY={}",
                        rendered.getBurrowerState(), rendered.getStateTicks(), rendered.getY());
                for (int part = 0; part < 7; part++) LoggerFactory.getLogger("BurrowerVisualTest").info("Body {} client={} server={} yaw={}",
                        part, rendered.bodyPosition(part), worm.bodyPosition(part), rendered.getYRot());
            }
            if (ticks == 140) client.getSingleplayerServer().execute(() -> worm.setYRot(90));
            if (ticks == 155) capture(client, "burrower-ledge-turn.png");
            if (ticks == 175) client.getSingleplayerServer().execute(() -> {
                worm.discard();
                worm = new LunarBurrowerEntity(GCEntityTypes.LUNAR_BURROWER, client.getSingleplayerServer().overworld());
                worm.moveTo(-3, 65, -2, 180, 0);
                worm.setNoAi(true);
                client.getSingleplayerServer().overworld().addFreshEntity(worm);
                try {
                    var begin = LunarBurrowerEntity.class.getDeclaredMethod("begin", BurrowerState.class);
                    begin.setAccessible(true);
                    begin.invoke(worm, BurrowerState.CHASE);
                } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
                client.getSingleplayerServer().getPlayerList().getPlayers().getFirst().teleportTo(
                        client.getSingleplayerServer().overworld(), 11, 72, -7, 68, 34);
            });
            if (ticks >= 176 && ticks <= 295) {
                int age = ticks - 175;
                client.getSingleplayerServer().execute(() -> {
                    worm.setPos(-3 + Math.max(0, age - 30) * 0.08, 65, -2 - Math.min(30, age) * 0.08);
                    worm.setYRot(age <= 30 ? 180 : -90);
                });
            }
            if (ticks >= 205 && ticks <= 295 && ticks % 5 == 0) capture(client, "burrower-moving-turn-" + ticks + ".png");
            if (ticks == 315) {
                client.getSingleplayerServer().execute(() -> {
                    worm.discard();
                    for (int x = -12; x <= 12; x++) for (int z = 4; z <= 16; z++)
                        client.getSingleplayerServer().overworld().setBlock(new BlockPos(x, 65, z), Blocks.AIR.defaultBlockState(), 2);
                    worm = new LunarBurrowerEntity(GCEntityTypes.LUNAR_BURROWER, client.getSingleplayerServer().overworld());
                    worm.moveTo(0.5, 65, 0.5, 180, 0);
                    worm.setNoAi(true);
                    worm.setDenPosition(new BlockPos(0, 65, 0));
                    client.getSingleplayerServer().overworld().addFreshEntity(worm);
                });
            }
            if (ticks == 335) client.getSingleplayerServer().execute(() -> worm.setYRot(0));
            if (ticks == 350) capture(client, "burrower-head-reversal.png");
            if (ticks == 365) client.getSingleplayerServer().execute(() -> {
                var target = new net.minecraft.world.entity.decoration.ArmorStand(client.getSingleplayerServer().overworld(), 0.5, 65, 5.5);
                target.setInvisible(true); target.setInvulnerable(true); target.setNoGravity(true);
                client.getSingleplayerServer().overworld().addFreshEntity(target);
                worm.setTarget(target); worm.setNoAi(false);
                try {
                    var anger = LunarBurrowerEntity.class.getDeclaredField("angerTicks");
                    anger.setAccessible(true); anger.setInt(worm, 240);
                    var begin = LunarBurrowerEntity.class.getDeclaredMethod("begin", BurrowerState.class);
                    begin.setAccessible(true); begin.invoke(worm, BurrowerState.CHASE);
                } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
            });
            if (ticks >= 395 && ticks <= 515 && ticks % 30 == 5) capture(client, "burrower-rear-pursuit-" + ticks + ".png");
            if (ticks == 535) {
                client.getSingleplayerServer().execute(() -> {
                    var level = client.getSingleplayerServer().overworld();
                    worm.discard();
                    for (int x = -12; x <= 12; x++) for (int z = -40; z <= -8; z++)
                        level.setBlock(new BlockPos(x, 64, z), GCBlocks.MOON_ROCK.defaultBlockState(), 2);
                    var player = client.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                    juvenile = new LunarBurrowerEntity(GCEntityTypes.LUNAR_BURROWER, level);
                    juvenile.hatch(); juvenile.moveTo(-3, 65, -1.5, 180, 0); juvenile.setNoAi(true); level.addFreshEntity(juvenile);
                    worm = new LunarBurrowerEntity(GCEntityTypes.LUNAR_BURROWER, level);
                    worm.hatch(); worm.setGrowingTicks(0); worm.moveTo(0.5, 65, 0.5, 180, 0); level.addFreshEntity(worm);
                    player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                            new net.minecraft.world.item.ItemStack(dev.galacticraft.mod.content.item.GCItems.MOON_CHEESE_CURD));
                    worm.mobInteract(player, net.minecraft.world.InteractionHand.MAIN_HAND);
                    player.setShiftKeyDown(true); player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.ItemStack.EMPTY);
                    worm.mobInteract(player, net.minecraft.world.InteractionHand.MAIN_HAND);
                    for (var item : new net.minecraft.world.item.Item[]{net.minecraft.world.item.Items.SADDLE, net.minecraft.world.item.Items.CHEST}) {
                        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(item));
                        worm.mobInteract(player, net.minecraft.world.InteractionHand.MAIN_HAND);
                    }
                    player.setShiftKeyDown(false); player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.ItemStack.EMPTY);
                    level.setBlock(new BlockPos(-2, 65, -3), GCBlocks.LUNAR_BURROWER_EGG.defaultBlockState(), 3);
                });
            }
            if (ticks == 570) capture(client, "burrower-companion-family.png");
            if (ticks == 599) client.getSingleplayerServer().execute(() -> {
                var player = client.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                player.getAbilities().flying = false; player.onUpdateAbilities();
                player.teleportTo(worm.getX(), worm.getY(), worm.getZ());
                rideStart = worm.position();
                worm.mobInteract(player, net.minecraft.world.InteractionHand.MAIN_HAND);
            });
        });
    }

    private static void preparePetPlayground(Minecraft client) {
        var server = client.getSingleplayerServer();
        var level = server.overworld();
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        level.setDayTime(6000);
        for (int x = -24; x <= 24; x++) for (int z = -45; z <= 45; z++)
            level.setBlock(new BlockPos(x, 64, z), GCBlocks.MOON_ROCK.defaultBlockState(), 2);
        for (int x = -24; x <= 24; x++) for (int z = -40; z <= -9; z++) for (int y = 50; y <= (z <= -15 ? 78 : 63); y++)
            level.setBlock(new BlockPos(x, y, z), GCBlocks.MOON_ROCK.defaultBlockState(), 2);
        var player = server.getPlayerList().getPlayers().getFirst();
        player.teleportTo(level, 7, 65, -6, 50, 15);
        var hand = net.minecraft.world.InteractionHand.MAIN_HAND;
        var cheese = dev.galacticraft.mod.content.item.GCItems.MOON_CHEESE_CURD;
        for (int stage = 0; stage < 3; stage++) {
            var pet = new LunarBurrowerEntity(GCEntityTypes.LUNAR_BURROWER, level);
            pet.hatch();
            pet.setGrowthAge(stage == 0 ? 12000 : stage == 1 ? 0 : dev.galacticraft.mod.util.BurrowerGrowth.MAX_AGE);
            pet.moveTo(stage == 0 ? 0 : stage == 1 ? -6 : -14, 65, stage == 2 ? -7 : 0, 180, 0);
            level.addFreshEntity(pet);
            if (stage != 0) continue;
            for (var item : new net.minecraft.world.item.Item[]{cheese, net.minecraft.world.item.Items.SADDLE, net.minecraft.world.item.Items.CHEST}) {
                player.setItemInHand(hand, new net.minecraft.world.item.ItemStack(item));
                pet.mobInteract(player, hand);
            }
            player.setShiftKeyDown(true);
            player.setItemInHand(hand, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK));
            pet.mobInteract(player, hand);
            player.setShiftKeyDown(false);
        }
        player.getInventory().clearContent();
        var supplies = new net.minecraft.world.item.Item[]{cheese, net.minecraft.world.item.Items.SADDLE,
                net.minecraft.world.item.Items.CHEST, net.minecraft.world.item.Items.STICK,
                net.minecraft.world.item.Items.SHEARS, GCBlocks.LUNAR_BURROWER_EGG.asItem()};
        for (int slot = 0; slot < supplies.length; slot++)
            player.getInventory().setItem(slot, new net.minecraft.world.item.ItemStack(supplies[slot], slot == 0 ? 64 : slot == 5 ? 16 : 1));
        player.getInventory().selected = 6;
        level.setBlock(new BlockPos(-3, 65, -4), GCBlocks.LUNAR_BURROWER_EGG.defaultBlockState(), 3);
        player.inventoryMenu.broadcastChanges();
        LoggerFactory.getLogger("BurrowerVisualTest").info("BURROWER PET PLAYGROUND READY: saddled pet, tameable hatchling and giant, eggs and equipment supplied");
    }

    private static void capture(Minecraft client, String name) {
        Screenshot.grab(client.gameDirectory, name, client.getMainRenderTarget(),
                message -> LoggerFactory.getLogger("BurrowerVisualTest").info("{}: {}", name, message.getString()));
    }
}
