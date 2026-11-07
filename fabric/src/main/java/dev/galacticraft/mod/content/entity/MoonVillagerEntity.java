/*
 * Copyright (c) 2019-2026 Team Galacticraft
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

import dev.galacticraft.mod.accessor.MoonVillagerMarker;
import dev.galacticraft.mod.content.GCEntityTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public class MoonVillagerEntity extends Villager {
    public MoonVillagerEntity(EntityType<? extends MoonVillagerEntity> entityType, Level level) {
        super(entityType, level);
    }

    /**
     * Restores marked Moon villagers that a carrying mod rebuilt as vanilla villagers. Copies the
     * stand-in's saved data and discards it to prevent duplicate addition. The Moon villager biome
     * type alone does not qualify.
     *
     * @return the restored Moon villager, or {@code null} if no replacement is needed
     */
    public static @Nullable MoonVillagerEntity restoreCarried(ServerLevel level, Entity entity) {
        if (entity.getType() != EntityType.VILLAGER
                || !(entity instanceof MoonVillagerMarker marker) || !marker.galacticraft$isMoonVillager()) {
            return null;
        }
        MoonVillagerEntity moonVillager = GCEntityTypes.MOON_VILLAGER.create(level);
        if (moonVillager == null) return null;
        moonVillager.load(entity.saveWithoutId(new CompoundTag()));
        entity.discard();
        return moonVillager;
    }
}
