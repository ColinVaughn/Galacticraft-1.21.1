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
 * Where and when Galacticraft's own mobs are allowed to spawn.
 *
 * <p>A mob with no entry here is not held to looser rules, it is held to <em>no</em> rules:
 * {@link SpawnPlacements#checkSpawnRules} answers yes for any type it has never been told about, and
 * {@link SpawnPlacements#getPlacementType} falls back to
 * {@link SpawnPlacementTypes#NO_RESTRICTIONS}. An unregistered monster therefore ignores the light
 * level entirely and does not need ground to stand on - which is how the evolved mobs came to spawn
 * inside lit, sealed bases.
 *
 * <p>The table lives here, in code both loaders compile, because each loader has to install it a
 * different way: Fabric calls {@code SpawnPlacements.register} directly (widened for access) while
 * NeoForge has to go through {@code RegisterSpawnPlacementsEvent}. Keeping the entries in one place
 * is what stops the two lists from drifting apart, which is what happened before - NeoForge had them
 * and Fabric did not.
 */
public final class GCSpawnPlacements {
    private GCSpawnPlacements() {}

    /**
     * One mob's spawning rules, in the shape {@code SpawnPlacements.register} takes.
     *
     * @param type the mob these rules apply to
     * @param placement where in the world the mob may be put down
     * @param heightmap the surface the spawner picks candidate positions from
     * @param predicate the test a candidate position has to pass
     * @param <T> the mob type
     */
    public record Placement<T extends Mob>(
            EntityType<T> type,
            SpawnPlacementType placement,
            Heightmap.Types heightmap,
            SpawnPlacements.SpawnPredicate<T> predicate
    ) {}

    /**
     * The evolved mobs are the vanilla mobs in spacesuits, and Galacticraft Legacy let them keep the
     * vanilla spawning rules - dark, on solid ground, not on peaceful. {@link Monster#checkMonsterSpawnRules}
     * is that rule, and it reads the light limits off the dimension type, so each planet's
     * {@code monster_spawn_light_level} governs its own surface.
     */
    private static final List<Placement<?>> PLACEMENTS = List.of(
            monster(GCEntityTypes.EVOLVED_ZOMBIE),
            monster(GCEntityTypes.EVOLVED_CREEPER),
            monster(GCEntityTypes.EVOLVED_SKELETON),
            monster(GCEntityTypes.EVOLVED_SPIDER),
            monster(GCEntityTypes.EVOLVED_ENDERMAN),
            monster(GCEntityTypes.EVOLVED_WITCH)
    );

    /** {@return every mob that has spawning rules of its own, for a loader to install} */
    public static List<Placement<?>> all() {
        return PLACEMENTS;
    }

    private static <T extends Monster> Placement<T> monster(EntityType<T> type) {
        return new Placement<>(type, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                Monster::checkMonsterSpawnRules);
    }
}
