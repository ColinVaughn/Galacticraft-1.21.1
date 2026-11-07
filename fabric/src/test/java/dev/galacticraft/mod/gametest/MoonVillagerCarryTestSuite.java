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

import dev.galacticraft.mod.content.GCEntityTypes;
import dev.galacticraft.mod.content.entity.MoonVillagerEntity;
import dev.galacticraft.mod.village.MoonVillagerTypes;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Reproduces Easy Villagers' save/load cycle through a vanilla villager and checks that Moon
 * villager identity survives. The airless batch substitutes for the Moon's default atmosphere.
 */
public final class MoonVillagerCarryTestSuite implements GalacticraftGameTest {
    private static final String AIRLESS = "galacticraft:moon_villager_carry";

    private static final Vec3 PICK_UP_SPOT = new Vec3(1.5, 2, 1.5);
    private static final Vec3 PUT_DOWN_SPOT = new Vec3(3.5, 2, 3.5);
    /** Comfortably past the first depressurisation hit, which lands 21 ticks into an unprotected life. */
    private static final int BREATH_WINDOW = 60;

    private static boolean levelWasBreathable = true;

    @BeforeBatch(batch = AIRLESS)
    public void pumpTheAirOut(ServerLevel level) {
        levelWasBreathable = level.getDefaultBreathable();
        level.setDefaultBreathable(false);
    }

    @AfterBatch(batch = AIRLESS)
    public void letTheAirBackIn(ServerLevel level) {
        level.setDefaultBreathable(levelWasBreathable);
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = AIRLESS)
    public void aCarriedMoonVillagerIsPutBackAsAMoonVillager(GameTestHelper context) {
        MoonVillagerEntity original = context.spawnWithNoFreeWill(GCEntityTypes.MOON_VILLAGER, PICK_UP_SPOT);
        original.setVillagerData(original.getVillagerData().setProfession(VillagerProfession.LIBRARIAN).setLevel(2));
        original.setCustomName(Component.literal("Luna"));

        Villager standIn = rebuildAsVillager(context, pickUp(original));
        // Easy Villagers stores the name on its item and reapplies it separately from villager data.
        standIn.setCustomName(original.getCustomName());
        boolean added = putDown(context, standIn);

        Villager placed = onlyVillagerAt(context, PUT_DOWN_SPOT);
        if (!added) {
            context.fail("placing the carried villager was refused, so a mod would keep its item and lose nothing - or dupe it");
        } else if (placed == null) {
            context.fail("no villager arrived where the carried one was put down");
        } else if (placed.getType() != GCEntityTypes.MOON_VILLAGER) {
            context.fail("a carried Moon villager came back as " + EntityType.getKey(placed.getType()));
        } else if (placed.getVillagerData().getProfession() != VillagerProfession.LIBRARIAN || placed.getVillagerData().getLevel() != 2) {
            context.fail("the Moon villager lost its job on the way: " + placed.getVillagerData());
        } else if (placed.getCustomName() == null || !"Luna".equals(placed.getCustomName().getString())) {
            context.fail("the Moon villager lost its name on the way: " + placed.getCustomName());
        } else if (!standIn.isRemoved()) {
            context.fail("the stand-in villager is still live, so adding it again would duplicate the Moon villager");
        } else {
            context.succeed();
        }
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = AIRLESS, timeoutTicks = BREATH_WINDOW + 20)
    public void aCarriedMoonVillagerStillBreathesOnTheMoon(GameTestHelper context) {
        MoonVillagerEntity original = context.spawnWithNoFreeWill(GCEntityTypes.MOON_VILLAGER, PICK_UP_SPOT);
        putDown(context, rebuildAsVillager(context, pickUp(original)));

        Villager placed = onlyVillagerAt(context, PUT_DOWN_SPOT);
        if (placed == null) {
            context.fail("no villager arrived where the carried one was put down");
            return;
        }
        float start = placed.getHealth();
        context.onEachTick(() -> {
            if (placed.getHealth() < start) {
                context.fail("a carried Moon villager (" + EntityType.getKey(placed.getType()) + ") is suffocating: "
                        + placed.getHealth() + "/" + start + " health, air " + placed.getAirSupply());
            }
        });
        context.runAtTickTime(BREATH_WINDOW, context::succeed);
    }

    /**
     * Easy Villagers' trader, breeder and farmer blocks hold a villager as an ordinary
     * {@code minecraft:villager} and save it back into the item from there, so the Moon villager has
     * to survive a second trip through a stand-in.
     */
    @GameTest(template = EMPTY_STRUCTURE, batch = AIRLESS)
    public void aMoonVillagerSurvivesBeingResavedByAStandIn(GameTestHelper context) {
        MoonVillagerEntity original = context.spawnWithNoFreeWill(GCEntityTypes.MOON_VILLAGER, PICK_UP_SPOT);
        Villager heldInABlock = rebuildAsVillager(context, pickUp(original));
        putDown(context, rebuildAsVillager(context, pickUp(heldInABlock)));

        Villager placed = onlyVillagerAt(context, PUT_DOWN_SPOT);
        if (placed == null) {
            context.fail("no villager arrived where the carried one was put down");
        } else if (placed.getType() != GCEntityTypes.MOON_VILLAGER) {
            context.fail("a Moon villager re-saved by a stand-in came back as " + EntityType.getKey(placed.getType()));
        } else {
            context.succeed();
        }
    }

    /**
     * Control: an ordinary villager wearing the Moon highlands outfit (its villager type, which a
     * villager born in the highlands gets) is still an ordinary villager. Only a villager that really
     * was a Moon villager comes back as one.
     */
    @GameTest(template = EMPTY_STRUCTURE, batch = AIRLESS)
    public void aVillagerInMoonClothesIsNotTurnedIntoAMoonVillager(GameTestHelper context) {
        Villager original = context.spawnWithNoFreeWill(EntityType.VILLAGER, PICK_UP_SPOT);
        original.setVillagerData(original.getVillagerData().setType(MoonVillagerTypes.MOON_HIGHLANDS));
        putDown(context, rebuildAsVillager(context, pickUp(original)));

        Villager placed = onlyVillagerAt(context, PUT_DOWN_SPOT);
        if (placed == null) {
            context.fail("no villager arrived where the carried one was put down");
        } else if (placed.getType() != EntityType.VILLAGER) {
            context.fail("an ordinary villager in Moon clothes came back as " + EntityType.getKey(placed.getType()));
        } else {
            context.succeed();
        }
    }

    /** Picking up: Easy Villagers keeps only what {@code addAdditionalSaveData} writes, then removes the villager. */
    private static CompoundTag pickUp(Villager villager) {
        CompoundTag carried = new CompoundTag();
        villager.addAdditionalSaveData(carried);
        villager.discard();
        return carried;
    }

    /** What Easy Villagers builds from its item, whether to place it or to keep it inside one of its blocks. */
    private static Villager rebuildAsVillager(GameTestHelper context, CompoundTag carried) {
        Villager villager = new Villager(EntityType.VILLAGER, context.getLevel());
        villager.readAdditionalSaveData(carried);
        return villager;
    }

    private static boolean putDown(GameTestHelper context, Villager villager) {
        Vec3 at = context.absoluteVec(PUT_DOWN_SPOT);
        villager.setPos(at.x, at.y, at.z);
        return context.getLevel().addFreshEntity(villager);
    }

    private static @Nullable Villager onlyVillagerAt(GameTestHelper context, Vec3 relative) {
        Vec3 at = context.absoluteVec(relative);
        List<Villager> villagers = context.getLevel().getEntitiesOfClass(Villager.class, new AABB(at, at).inflate(0.5), Villager::isAlive);
        if (villagers.size() > 1) {
            context.fail(villagers.size() + " villagers arrived where one carried villager was put down");
        }
        return villagers.isEmpty() ? null : villagers.get(0);
    }
}
