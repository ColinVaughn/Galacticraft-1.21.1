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

package dev.galacticraft.mod.content;

import dev.galacticraft.mod.Constant;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.LootTable;

public class GCLootTables {
    public static final ResourceKey<LootTable> BASIC_MOON_RUINS_CHEST = Constant.key(Registries.LOOT_TABLE, Constant.LootTable.BASIC_MOON_RUINS_CHEST);
    public static final ResourceKey<LootTable> MOON_CAVE_EXPEDITION_CACHE = Constant.key(Registries.LOOT_TABLE, Constant.LootTable.MOON_CAVE_EXPEDITION_CACHE);

    public static final ResourceKey<LootTable> DUNGEON_TIER_1 = Constant.key(Registries.LOOT_TABLE, Constant.LootTable.DUNGEON_TIER_1);
    public static final ResourceKey<LootTable> DUNGEON_TIER_2 = Constant.key(Registries.LOOT_TABLE, Constant.LootTable.DUNGEON_TIER_2);
    public static final ResourceKey<LootTable> DUNGEON_TIER_3 = Constant.key(Registries.LOOT_TABLE, Constant.LootTable.DUNGEON_TIER_3);

    /**
     * @return the dungeon chest loot table for the given dungeon tier, clamped to a valid tier.
     */
    public static ResourceKey<LootTable> dungeonChest(int tier) {
        return switch (tier) {
            case 2 -> DUNGEON_TIER_2;
            case 3 -> DUNGEON_TIER_3;
            default -> DUNGEON_TIER_1;
        };
    }
}
