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

package dev.galacticraft.mod.gametest;

import dev.galacticraft.mod.content.GCEntityTypes;
import dev.galacticraft.mod.content.item.GCItems;
import dev.galacticraft.mod.tag.GCBlockTags;
import dev.galacticraft.mod.tag.GCEntityTypeTags;
import dev.galacticraft.mod.tag.GCItemTags;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/** Data and item invariants inherited from Galacticraft Legacy's Sensor Glasses. */
public final class SensorGlassesTestSuite implements GalacticraftGameTest {
    @GameTest(template = EMPTY_STRUCTURE)
    public void legacySensorResourcesAreDetectable(GameTestHelper context) {
        if (!Blocks.COAL_ORE.defaultBlockState().is(GCBlockTags.SENSOR_GLASSES_DETECTABLE)
                || !Blocks.DIAMOND_ORE.defaultBlockState().is(GCBlockTags.SENSOR_GLASSES_DETECTABLE)) {
            context.fail("vanilla ores are missing from the Sensor Glasses detectable tag");
        }
        if (!GCEntityTypes.MOON_VILLAGER.is(GCEntityTypeTags.SENSOR_GLASSES_DETECTABLE)
                || !GCEntityTypes.EVOLVED_CREEPER.is(GCEntityTypeTags.SENSOR_GLASSES_DETECTABLE)) {
            context.fail("legacy Galacticraft mobs are missing from the Sensor Glasses detectable tag");
        }
        ItemStack glasses = new ItemStack(GCItems.SENSOR_GLASSES);
        if (!glasses.is(GCItemTags.SENSOR_GLASSES)) {
            context.fail("Sensor Glasses are missing from their compatibility tag");
        }
        if (GCItems.SENSOR_GLASSES.isEnchantable(glasses)) {
            context.fail("Sensor Glasses must remain non-enchantable like the Legacy item");
        }
        if (glasses.getMaxDamage() != 2200) {
            context.fail("Sensor Glasses lost Legacy's 2200 durability");
        }
        context.succeed();
    }
}
