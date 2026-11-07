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

package dev.galacticraft.mod.content.entity;

import dev.galacticraft.api.entity.attribute.GcApiEntityAttributes;
import dev.galacticraft.impl.internal.accessor.SpaceEvolvedAccessor;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.Galacticraft;
import dev.galacticraft.mod.tag.GCEntityTypeTags;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;

/**
 * Applies evolved traits to hostile mobs entering airless dimensions without replacing their
 * entity, preserving references held by raid and wave mods. Runs before client tracking so gear
 * is present in the initial synced state.
 */
public final class SpaceEvolution {
    /** The permanent max-health modifier an evolved mob carries; its presence also guards against applying it twice. */
    public static final ResourceLocation HEALTH_BOOST = Constant.id("space_evolved");
    /** Both tanks an evolved mob carries, matching the evolved zombie's. */
    public static final String TANK_SIZE = Constant.Item.MEDIUM_OXYGEN_TANK;

    private SpaceEvolution() {}

    public static void onEntityAdded(Level level, Entity entity) {
        if (shouldEvolve(level, entity)) {
            evolve((Mob) entity);
        }
    }

    /**
     * Checks the dimension's default atmosphere, even if the mob currently stands in a sealed base: it
     * may leave that shelter later.
     *
     * @return whether the entity should evolve on entering this level
     */
    public static boolean shouldEvolve(Level level, Entity entity) {
        if (!(entity instanceof Mob mob) || level.isClientSide() || !Galacticraft.CONFIG.evolveMobsInSpace()) return false;
        if (level.getDefaultBreathable() || breathesInSpace(mob)) return false;

        EntityType<?> type = mob.getType();
        if (type.is(GCEntityTypeTags.NEVER_EVOLVES_IN_SPACE)) return false;
        return type.is(GCEntityTypeTags.EVOLVES_IN_SPACE) || mob instanceof Enemy || type.getCategory() == MobCategory.MONSTER;
    }

    /**
     * Returns whether the entity already breathes in space, including previously evolved mobs and
     * entities with the no-air attribute.
     */
    public static boolean breathesInSpace(LivingEntity entity) {
        if (((SpaceEvolvedAccessor) entity).galacticraft$isSpaceEvolved()) return true;
        AttributeInstance attribute = entity.getAttribute(GcApiEntityAttributes.CAN_BREATHE_IN_SPACE);
        return attribute != null && attribute.getValue() >= 0.99D;
    }

    public static void evolve(Mob mob) {
        SpaceEvolvedAccessor evolved = (SpaceEvolvedAccessor) mob;
        evolved.galacticraft$setSpaceEvolved(true);
        evolved.galacticraft$wearSpaceGear(TANK_SIZE);
        if (!mob.getType().is(GCEntityTypeTags.BOSSES)) {
            boostHealth(mob, Galacticraft.CONFIG.evolvedHealthMultiplier());
        }
    }

    /**
     * Scales max health, and current health by the same factor, so a mob its spawner deliberately
     * left wounded or buffed keeps the same share of the larger pool.
     */
    private static void boostHealth(Mob mob, float multiplier) {
        AttributeInstance maxHealth = mob.getAttribute(Attributes.MAX_HEALTH);
        if (multiplier <= 1.0F || maxHealth == null || maxHealth.hasModifier(HEALTH_BOOST) || mob.getMaxHealth() <= 0) return;

        float share = mob.getHealth() / mob.getMaxHealth();
        maxHealth.addPermanentModifier(new AttributeModifier(HEALTH_BOOST, multiplier - 1.0F, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        mob.setHealth(share * mob.getMaxHealth());
    }
}
