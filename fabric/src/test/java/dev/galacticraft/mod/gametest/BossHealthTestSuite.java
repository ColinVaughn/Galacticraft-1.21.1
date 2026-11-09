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

package dev.galacticraft.mod.gametest;

import dev.galacticraft.mod.Galacticraft;
import dev.galacticraft.mod.config.ConfigImpl;
import dev.galacticraft.mod.content.GCEntityTypes;
import dev.galacticraft.mod.content.entity.boss.AbstractBossEntity;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/**
 * Checks boss health scaling against Galacticraft Legacy: the Moon boss starts at 150 health,
 * with speed 0.25 plus 0.075 per difficulty step above easy.
 */
public final class BossHealthTestSuite implements GalacticraftGameTest {
    private static final String DOUBLED = "galacticraft:boss_health_doubled";
    private static final Vec3 SPOT = new Vec3(1.5, 2, 1.5);
    private static final double MULTIPLIER = 2.0;

    private static double previousMultiplier = 1.0;

    @BeforeBatch(batch = DOUBLED)
    public void doubleBossHealth(ServerLevel level) {
        previousMultiplier = Galacticraft.CONFIG.bossHealthMultiplier();
        ((ConfigImpl) Galacticraft.CONFIG).setBossHealthMultiplier(MULTIPLIER);
    }

    @AfterBatch(batch = DOUBLED)
    public void restoreBossHealth(ServerLevel level) {
        ((ConfigImpl) Galacticraft.CONFIG).setBossHealthMultiplier(previousMultiplier);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void theMoonBossHasItsLegacyHealth(GameTestHelper context) {
        AbstractBossEntity boss = context.spawnWithNoFreeWill(GCEntityTypes.SKELETON_BOSS, SPOT);
        double expected = 150.0 * Galacticraft.CONFIG.bossHealthMultiplier();
        if (!near(boss.getMaxHealth(), expected)) {
            context.fail("the Moon boss should have " + expected + " max health, not " + boss.getMaxHealth());
        } else if (!near(boss.getHealth(), boss.getMaxHealth())) {
            context.fail("a fresh Moon boss should be at full health, not " + boss.getHealth() + "/" + boss.getMaxHealth());
        } else {
            context.succeed();
        }
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void theMoonBossWalksAtItsLegacySpeed(GameTestHelper context) {
        AbstractBossEntity boss = context.spawnWithNoFreeWill(GCEntityTypes.SKELETON_BOSS, SPOT);
        Difficulty difficulty = context.getLevel().getDifficulty();
        double expected = 0.25 + 0.075 * switch (difficulty) {
            case HARD -> 2;
            case NORMAL -> 1;
            default -> 0;
        };
        double speed = boss.getAttributeBaseValue(Attributes.MOVEMENT_SPEED);
        if (!near(speed, expected)) {
            context.fail("on " + difficulty + " the Moon boss should walk at " + expected + ", not " + speed);
        } else {
            context.succeed();
        }
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = DOUBLED)
    public void theHealthMultiplierScalesTheMoonBoss(GameTestHelper context) {
        checkScaled(context, GCEntityTypes.SKELETON_BOSS, 150.0);
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = DOUBLED)
    public void theHealthMultiplierScalesTheMarsBoss(GameTestHelper context) {
        checkScaled(context, GCEntityTypes.CREEPER_BOSS, 150.0);
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = DOUBLED)
    public void theHealthMultiplierScalesTheVenusBoss(GameTestHelper context) {
        checkScaled(context, GCEntityTypes.SPIDER_BOSS, 200.0);
    }

    private static void checkScaled(GameTestHelper context, EntityType<? extends AbstractBossEntity> type, double baseHealth) {
        AbstractBossEntity boss = context.spawnWithNoFreeWill(type, SPOT);
        double expected = baseHealth * MULTIPLIER;
        if (!near(boss.getMaxHealth(), expected)) {
            context.fail(EntityType.getKey(type) + " with a x" + MULTIPLIER + " health multiplier should have "
                    + expected + " max health, not " + boss.getMaxHealth());
        } else if (!near(boss.getHealth(), boss.getMaxHealth())) {
            context.fail("a fresh " + EntityType.getKey(type) + " should be at full health, not "
                    + boss.getHealth() + "/" + boss.getMaxHealth());
        } else {
            context.succeed();
        }
    }

    private static boolean near(double actual, double expected) {
        return Math.abs(actual - expected) < 1.0E-4;
    }
}
