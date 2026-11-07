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

package dev.galacticraft.mod.content;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnPlacementType;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.List;

/**
 * Shared spawning rules installed by each loader. Unregistered types bypass spawn predicates,
 * including light checks, so every evolved mob needs an entry. Fabric registers directly;
 * NeoForge uses {@code RegisterSpawnPlacementsEvent}.
 */
public final class GCSpawnPlacements {
    private GCSpawnPlacements() {}

    /**
     * Spawning rules for one mob type.
     *
     * @param type the mob these rules apply to
     * @param placement where the mob may spawn
     * @param heightmap the surface used to choose candidate positions
     * @param predicate the spawn condition
     */
    public record Placement<T extends Mob>(
            EntityType<T> type,
            SpawnPlacementType placement,
            Heightmap.Types heightmap,
            SpawnPlacements.SpawnPredicate<T> predicate
    ) {}

    /**
     * Evolved mobs use vanilla hostile spawn checks; burrowers and rovers supply custom predicates.
     */
    private static final List<Placement<?>> PLACEMENTS = List.of(
            new Placement<>(GCEntityTypes.LUNAR_BURROWER, SpawnPlacementTypes.ON_GROUND,
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, dev.galacticraft.mod.content.entity.LunarBurrowerEntity::checkSpawnRules),
            new Placement<>(GCEntityTypes.CURIOSITY_ROVER, SpawnPlacementTypes.ON_GROUND,
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, dev.galacticraft.mod.content.entity.mars.CuriosityRoverEntity::checkSpawnRules),
            monster(GCEntityTypes.EVOLVED_ZOMBIE),
            monster(GCEntityTypes.EVOLVED_CREEPER),
            monster(GCEntityTypes.EVOLVED_SKELETON),
            monster(GCEntityTypes.EVOLVED_SPIDER),
            monster(GCEntityTypes.EVOLVED_ENDERMAN),
            monster(GCEntityTypes.EVOLVED_WITCH)
    );

    /** Returns the spawning rules each loader must install. */
    public static List<Placement<?>> all() {
        return PLACEMENTS;
    }

    private static <T extends Monster> Placement<T> monster(EntityType<T> type) {
        return new Placement<>(type, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                Monster::checkMonsterSpawnRules);
    }
}
