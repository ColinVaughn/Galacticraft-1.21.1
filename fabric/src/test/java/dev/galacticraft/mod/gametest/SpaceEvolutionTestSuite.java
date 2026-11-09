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

import dev.galacticraft.impl.internal.accessor.SpaceEvolvedAccessor;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.Galacticraft;
import dev.galacticraft.mod.config.ConfigImpl;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.phys.Vec3;

/**
 * Checks evolved traits without replacing mobs tracked by raid or wave mods. The airless batch
 * substitutes for planetary dimensions by disabling the level's default breathability.
 */
public final class SpaceEvolutionTestSuite implements GalacticraftGameTest {
    private static final String AIRLESS = "galacticraft:space_evolution";
    private static final String BREATHABLE = "galacticraft:space_evolution_breathable";
    private static final String DISABLED = "galacticraft:space_evolution_disabled";

    private static final Vec3 SPOT = new Vec3(1.5, 2, 1.5);
    /** Comfortably past the first depressurisation hit, which lands 21 ticks into an unprotected life. */
    private static final int BREATH_WINDOW = 60;
    private static final float CREEPER_HEALTH = 20.0F;
    private static final float WITHER_HEALTH = 300.0F;
    /** At noon an exposed zombie ignites with a chance of about 1 in 25 a tick; 200 ticks makes missing it vanishingly rare. */
    private static final int SUN_WINDOW = 200;
    private static final long NOON = 6000L;

    private static boolean levelWasBreathable = true;
    private static boolean evolutionWasEnabled = true;
    private static long previousDayTime;

    @BeforeBatch(batch = AIRLESS)
    public void pumpTheAirOut(ServerLevel level) {
        levelWasBreathable = level.getDefaultBreathable();
        level.setDefaultBreathable(false);
        makeItNoon(level);
    }

    @AfterBatch(batch = AIRLESS)
    public void letTheAirBackIn(ServerLevel level) {
        level.setDefaultBreathable(levelWasBreathable);
        level.setDayTime(previousDayTime);
    }

    @BeforeBatch(batch = BREATHABLE)
    public void noonInBreathableAir(ServerLevel level) {
        makeItNoon(level);
    }

    @AfterBatch(batch = BREATHABLE)
    public void restoreTheTime(ServerLevel level) {
        level.setDayTime(previousDayTime);
    }

    private static void makeItNoon(ServerLevel level) {
        previousDayTime = level.getDayTime();
        level.setDayTime(NOON);
    }

    @BeforeBatch(batch = DISABLED)
    public void pumpTheAirOutWithEvolutionOff(ServerLevel level) {
        levelWasBreathable = level.getDefaultBreathable();
        level.setDefaultBreathable(false);
        evolutionWasEnabled = Galacticraft.CONFIG.evolveMobsInSpace();
        ((ConfigImpl) Galacticraft.CONFIG).setEvolveMobsInSpace(false);
    }

    @AfterBatch(batch = DISABLED)
    public void restoreAirAndEvolution(ServerLevel level) {
        level.setDefaultBreathable(levelWasBreathable);
        ((ConfigImpl) Galacticraft.CONFIG).setEvolveMobsInSpace(evolutionWasEnabled);
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = AIRLESS)
    public void aHostileMobEvolvesOnArrival(GameTestHelper context) {
        Creeper creeper = context.spawnWithNoFreeWill(EntityType.CREEPER, SPOT);

        if (!evolved(creeper)) {
            context.fail("a creeper added to an airless level was not evolved");
        } else if (!creeper.galacticraft$hasMask() || !creeper.galacticraft$hasGear()) {
            context.fail("an evolved creeper should wear a mask and gear [mask=" + creeper.galacticraft$hasMask()
                    + " gear=" + creeper.galacticraft$hasGear() + "]");
        } else if (!Constant.Item.MEDIUM_OXYGEN_TANK.equals(creeper.galacticraft$tankSize(0))
                || !Constant.Item.MEDIUM_OXYGEN_TANK.equals(creeper.galacticraft$tankSize(1))) {
            context.fail("an evolved creeper should carry two medium tanks, not '"
                    + creeper.galacticraft$tankSize(0) + "' and '" + creeper.galacticraft$tankSize(1) + "'");
        } else if (!near(creeper.getMaxHealth(), boosted(CREEPER_HEALTH))) {
            context.fail("an evolved creeper's max health should be " + boosted(CREEPER_HEALTH) + ", not " + creeper.getMaxHealth());
        } else if (!near(creeper.getHealth(), creeper.getMaxHealth())) {
            context.fail("a fresh evolved creeper should be at full health, not " + creeper.getHealth() + "/" + creeper.getMaxHealth());
        } else {
            context.succeed();
        }
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = AIRLESS, timeoutTicks = 200)
    public void anEvolvedMobBreathesInVacuum(GameTestHelper context) {
        Creeper creeper = context.spawnWithNoFreeWill(EntityType.CREEPER, SPOT);
        float start = creeper.getHealth();
        context.onEachTick(() -> {
            if (creeper.getHealth() < start) {
                context.fail("an evolved creeper lost health in vacuum (" + start + " -> " + creeper.getHealth()
                        + ", last hurt by " + creeper.getLastDamageSource() + ")");
            } else if (creeper.tickCount >= BREATH_WINDOW) {
                context.succeed();
            }
        });
    }

    /** The control: without it, a harness that cannot suffocate anything would pass the test above. */
    @GameTest(template = EMPTY_STRUCTURE, batch = AIRLESS, timeoutTicks = 200)
    public void aPassiveMobStillSuffocates(GameTestHelper context) {
        Mob cow = context.spawnWithNoFreeWill(EntityType.COW, SPOT);
        if (evolved(cow)) {
            context.fail("a cow is not hostile and should not have been evolved");
            return;
        }
        expectSuffocation(context, cow, "a cow");
    }

    /** {@code minecraft:endermite} is in {@code never_evolves_in_space} in the test datapack only. */
    @GameTest(template = EMPTY_STRUCTURE, batch = AIRLESS, timeoutTicks = 200)
    public void anExcludedMobStillSuffocates(GameTestHelper context) {
        Mob endermite = context.spawnWithNoFreeWill(EntityType.ENDERMITE, SPOT);
        if (evolved(endermite)) {
            context.fail("an endermite is tagged never_evolves_in_space but was evolved anyway");
            return;
        }
        expectSuffocation(context, endermite, "an excluded endermite");
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = AIRLESS)
    public void aBossEvolvesWithoutTheHealthBoost(GameTestHelper context) {
        Mob wither = context.spawnWithNoFreeWill(EntityType.WITHER, SPOT);
        if (!evolved(wither)) {
            context.fail("a wither should still be evolved so it can breathe");
        } else if (!near(wither.getMaxHealth(), WITHER_HEALTH)) {
            context.fail("a boss should keep its own max health, but the wither's became " + wither.getMaxHealth());
        } else {
            context.succeed();
        }
    }

    /** A spawner that set its mob's health keeps the same fraction of the larger pool. */
    @GameTest(template = EMPTY_STRUCTURE, batch = AIRLESS)
    public void presetHealthKeepsItsProportion(GameTestHelper context) {
        ServerLevel level = context.getLevel();
        Creeper creeper = unspawned(context);
        creeper.setHealth(CREEPER_HEALTH / 2);
        level.addFreshEntity(creeper);

        float expected = boosted(CREEPER_HEALTH) / 2;
        if (!near(creeper.getHealth(), expected)) {
            context.fail("a half-health creeper should evolve to " + expected + ", not " + creeper.getHealth());
        } else {
            context.succeed();
        }
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = AIRLESS)
    public void aReloadedMobIsNotBoostedTwice(GameTestHelper context) {
        ServerLevel level = context.getLevel();
        Creeper original = context.spawnWithNoFreeWill(EntityType.CREEPER, SPOT);
        CompoundTag saved = original.saveWithoutId(new CompoundTag());
        original.discard();

        Creeper reloaded = new Creeper(EntityType.CREEPER, level);
        reloaded.load(saved);
        level.addFreshEntity(reloaded);

        if (!evolved(reloaded)) {
            context.fail("the evolved flag did not survive a save and reload");
        } else if (!near(reloaded.getMaxHealth(), boosted(CREEPER_HEALTH))) {
            context.fail("a reloaded creeper's max health should stay " + boosted(CREEPER_HEALTH) + ", not " + reloaded.getMaxHealth());
        } else if (!reloaded.galacticraft$hasMask()) {
            context.fail("a reloaded evolved creeper lost its mask");
        } else {
            context.succeed();
        }
    }

    /** Mobs crossing dimensions are re-created and added with {@code addDuringTeleport}, not as fresh entities. */
    @GameTest(template = EMPTY_STRUCTURE, batch = AIRLESS)
    public void aMobArrivingFromAnotherDimensionEvolves(GameTestHelper context) {
        Creeper creeper = unspawned(context);
        context.getLevel().addDuringTeleport(creeper);
        if (!evolved(creeper)) {
            context.fail("a creeper arriving through a dimension change was not evolved");
        } else {
            context.succeed();
        }
    }

    /**
     * Undead burn in daylight unless something shades their heads; vanilla counts any helmet. The
     * space helmet has to count too, or every zombie a raid sends to the Moon burns up by day.
     */
    @GameTest(template = EMPTY_STRUCTURE, batch = AIRLESS, timeoutTicks = SUN_WINDOW + 100)
    public void anEvolvedZombieDoesNotBurnInSunlight(GameTestHelper context) {
        Zombie zombie = context.spawnWithNoFreeWill(EntityType.ZOMBIE, SPOT);
        if (!evolved(zombie)) {
            context.fail("the zombie was not evolved, so this proves nothing");
            return;
        }
        context.onEachTick(() -> {
            if (zombie.isOnFire()) {
                context.fail("an evolved zombie caught fire in sunlight; its helmet should shade it");
            } else if (zombie.tickCount >= SUN_WINDOW) {
                context.succeed();
            }
        });
    }

    /** The control: in the same noon sun an ordinary zombie burns, or the test above proves nothing. */
    @GameTest(template = EMPTY_STRUCTURE, batch = BREATHABLE, timeoutTicks = SUN_WINDOW + 100)
    public void anOrdinaryZombieBurnsInSunlight(GameTestHelper context) {
        Zombie zombie = context.spawnWithNoFreeWill(EntityType.ZOMBIE, SPOT);
        context.onEachTick(() -> {
            if (zombie.isOnFire()) {
                context.succeed();
            } else if (zombie.tickCount >= SUN_WINDOW) {
                ServerLevel level = context.getLevel();
                context.fail("an ordinary zombie never caught fire, so this harness cannot show sunburn [day=" + level.isDay()
                        + " sky=" + level.canSeeSky(BlockPos.containing(zombie.getX(), zombie.getEyeY(), zombie.getZ())) + "]");
            }
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = BREATHABLE)
    public void aMobInBreathableAirDoesNotEvolve(GameTestHelper context) {
        Creeper creeper = context.spawnWithNoFreeWill(EntityType.CREEPER, SPOT);
        if (evolved(creeper) || creeper.galacticraft$hasMask()) {
            context.fail("a creeper in breathable air should be left alone");
        } else if (!near(creeper.getMaxHealth(), CREEPER_HEALTH)) {
            context.fail("a creeper in breathable air should keep max health " + CREEPER_HEALTH + ", not " + creeper.getMaxHealth());
        } else {
            context.succeed();
        }
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = DISABLED)
    public void nothingEvolvesWhenTheConfigIsOff(GameTestHelper context) {
        Creeper creeper = context.spawnWithNoFreeWill(EntityType.CREEPER, SPOT);
        if (evolved(creeper)) {
            context.fail("evolveMobsInSpace is off, but a creeper was evolved");
        } else {
            context.succeed();
        }
    }

    private static void expectSuffocation(GameTestHelper context, LivingEntity entity, String what) {
        float start = entity.getHealth();
        context.onEachTick(() -> {
            if (entity.getHealth() < start) {
                context.succeed();
            } else if (entity.tickCount >= BREATH_WINDOW) {
                context.fail(what + " took no damage in vacuum, so this harness cannot detect suffocation");
            }
        });
    }

    private static Creeper unspawned(GameTestHelper context) {
        Creeper creeper = new Creeper(EntityType.CREEPER, context.getLevel());
        Vec3 at = context.absoluteVec(SPOT);
        creeper.moveTo(at.x, at.y, at.z, 0, 0);
        creeper.setNoAi(true);
        return creeper;
    }

    private static boolean evolved(LivingEntity entity) {
        return ((SpaceEvolvedAccessor) entity).galacticraft$isSpaceEvolved();
    }

    private static float boosted(float health) {
        return health * Galacticraft.CONFIG.evolvedHealthMultiplier();
    }

    private static boolean near(float actual, float expected) {
        return Math.abs(actual - expected) < 0.01F;
    }
}
