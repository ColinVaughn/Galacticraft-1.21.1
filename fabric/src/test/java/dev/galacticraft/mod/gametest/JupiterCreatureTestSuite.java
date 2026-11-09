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

package dev.galacticraft.mod.gametest;

import dev.galacticraft.mod.content.GCEntityTypes;
import dev.galacticraft.mod.content.entity.jupiter.CloudRayEntity;
import dev.galacticraft.mod.content.entity.jupiter.SalvageDroneEntity;
import dev.galacticraft.mod.content.entity.jupiter.StaticJellyEntity;
import dev.galacticraft.mod.world.dimension.jupiter.JupiterFauna;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class JupiterCreatureTestSuite implements GalacticraftGameTest {
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120)
    public void staticJellyTelegraphsBeforeItDischarges(GameTestHelper helper) {
        var level = helper.getLevel();
        var jelly = helper.spawn(GCEntityTypes.STATIC_JELLY, new BlockPos(2, 3, 2));
        var near = this.makeSurvivalServerPlayer(helper);
        near.moveTo(jelly.getX() + 2.5, jelly.getY(), jelly.getZ());
        float full = near.getHealth();
        int[] chargeStart = {-1};
        boolean[] burst = {false};
        helper.onEachTick(() -> {
            if (jelly.burst() > 0) burst[0] = true;
            if (chargeStart[0] < 0 && jelly.charge() > 0) chargeStart[0] = jelly.tickCount;
            if (chargeStart[0] >= 0 && jelly.charge() > 0 && jelly.charge() < StaticJellyEntity.CHARGE_TICKS - 1) {
                // The telegraph is the counterplay: nothing may land while it is still building.
                helper.assertTrue(near.getHealth() == full, "The jelly struck before its charge finished");
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(chargeStart[0] >= 0, "The jelly never began charging at a player in range");
            helper.assertTrue(near.getHealth() < full, "The discharge missed a player in range");
            helper.assertTrue(burst[0], "The discharge never signalled its burst to the client");
            // Control: a discharge reaches no one standing outside its range.
            var far = this.makeSurvivalServerPlayer(helper);
            far.moveTo(jelly.getX() + StaticJellyEntity.RANGE + 4, jelly.getY(), jelly.getZ());
            near.moveTo(jelly.getX() + StaticJellyEntity.RANGE + 4, jelly.getY(), jelly.getZ() + 2);
            helper.assertTrue(jelly.discharge() == 0, "The discharge reached beyond its range");
            level.getServer().getPlayerList().remove(near);
            level.getServer().getPlayerList().remove(far);
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 160)
    public void salvageDronesDefendOnlyTheirPost(GameTestHelper helper) {
        var drone = helper.spawn(GCEntityTypes.SALVAGE_DRONE, new BlockPos(2, 3, 2));
        BlockPos post = helper.absolutePos(new BlockPos(2, 3, 2));
        drone.guard(post);
        var outsider = this.makeSurvivalServerPlayer(helper);
        outsider.moveTo(post.getX() + SalvageDroneEntity.GUARD_RADIUS + 6, post.getY(), post.getZ());
        helper.assertTrue(!drone.canEngage(outsider), "The drone would chase a player outside its post");
        var intruder = this.makeSurvivalServerPlayer(helper);
        intruder.moveTo(post.getX() + 3.5, post.getY(), post.getZ() + 0.5);
        helper.assertTrue(drone.canEngage(intruder), "The drone ignores an intruder at its post");
        float full = intruder.getHealth();
        boolean[] locked = {false};
        helper.onEachTick(() -> { if (drone.isTargeting()) locked[0] = true; });
        helper.succeedWhen(() -> {
            helper.assertTrue(locked[0], "The drone never signalled a target lock");
            helper.assertTrue(intruder.getHealth() < full, "The drone never reached and struck the intruder");
            helper.assertTrue(outsider.getHealth() == outsider.getMaxHealth(), "The drone struck a player outside its post");
            helper.getLevel().getServer().getPlayerList().remove(intruder);
            helper.getLevel().getServer().getPlayerList().remove(outsider);
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
    public void cloudRaysBoltUpwardWhenHurt(GameTestHelper helper) {
        var ray = helper.spawn(GCEntityTypes.CLOUD_RAY, new BlockPos(2, 3, 2));
        var calm = helper.spawn(GCEntityTypes.CLOUD_RAY, new BlockPos(6, 3, 6));
        var attacker = this.makeSurvivalServerPlayer(helper);
        attacker.moveTo(ray.getX() + 2, ray.getY(), ray.getZ());
        ray.hurt(helper.getLevel().damageSources().playerAttack(attacker), 1);
        helper.assertTrue(ray.isPanicking(), "A hurt ray did not panic");
        helper.assertTrue(!calm.isPanicking(), "An unhurt ray panicked");
        Vec3 flee = ray.waypoint();
        helper.assertTrue(flee != null && flee.y > ray.getY() + 5 && flee.distanceTo(attacker.position()) > ray.distanceTo(attacker),
                "A hurt ray does not flee upward and away");
        helper.getLevel().getServer().getPlayerList().remove(attacker);
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400)
    public void faunaFollowsStormActivity(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = this.makeSurvivalServerPlayer(helper);
        player.moveTo(Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 3, 2))));
        // Keep the sampled ring loaded until the test world closes; unforcing can pause neighboring tests.
        for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) level.setChunkForced((player.getBlockX() >> 4) + dx, (player.getBlockZ() >> 4) + dz, true);
        AABB around = new AABB(player.blockPosition()).inflate(JupiterFauna.RAY_RANGE);
        // Forced chunks out here have to generate first; wait until the whole ring is loaded, then spawn exactly once.
        int[][] holder = {null};
        helper.succeedWhen(() -> {
            if (holder[0] == null) {
                for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
                    helper.assertTrue(level.isLoaded(player.blockPosition().offset(dx * 16, 0, dz * 16)), "Waiting for the spawn ring to load");
                }
                int rays = level.getEntitiesOfClass(CloudRayEntity.class, around).size();
                holder[0] = new int[]{rays, JupiterFauna.populate(level, player)};
                try {
                    helper.assertTrue(level.getEntitiesOfClass(CloudRayEntity.class, around).size() == rays + 1, "No cloud ray joined the bank layer");
                    // Control: storm activity is zero away from a gas giant, so no jelly may appear.
                    helper.assertTrue(level.getEntitiesOfClass(StaticJellyEntity.class, around).isEmpty() && holder[0][1] == 1,
                            "Jellies spawned without storm activity");
                } finally {
                    level.getEntitiesOfClass(CloudRayEntity.class, around).forEach(ray -> ray.discard());
                    level.getServer().getPlayerList().remove(player);
                }
            }
        });
    }
}
