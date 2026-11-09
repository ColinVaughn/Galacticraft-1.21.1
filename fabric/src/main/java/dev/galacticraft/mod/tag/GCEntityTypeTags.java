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

package dev.galacticraft.mod.tag;

import dev.galacticraft.mod.Constant;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;

public class GCEntityTypeTags {
    public static final TagKey<EntityType<?>> HAS_FOOTPRINTS = TagKey.create(Registries.ENTITY_TYPE, Constant.id("has_footprints"));
    public static final TagKey<EntityType<?>> HAS_OXYGEN_SETUP = TagKey.create(Registries.ENTITY_TYPE, Constant.id("has_oxygen_setup"));
    public static final TagKey<EntityType<?>> HAS_PET_INVENTORY = TagKey.create(Registries.ENTITY_TYPE, Constant.id("has_pet_inventory"));
    public static final TagKey<EntityType<?>> IMMUNE_TO_ACID = TagKey.create(Registries.ENTITY_TYPE, Constant.id("immune_to_acid"));
    public static final TagKey<EntityType<?>> SENSITIVE_TO_ACID = TagKey.create(Registries.ENTITY_TYPE, Constant.id("sensitive_to_acid"));
    public static final TagKey<EntityType<?>> CAN_REENTER_ATMOSPHERE = TagKey.create(Registries.ENTITY_TYPE, Constant.id("can_reenter_atmosphere"));
    /** Legacy Galacticraft mobs rendered with the Sensor Glasses emissive texture. */
    public static final TagKey<EntityType<?>> SENSOR_GLASSES_DETECTABLE = TagKey.create(Registries.ENTITY_TYPE, Constant.id("sensor_glasses_detectable"));
    /** Mobs evolved for space on arrival in an airless dimension even though they are not hostile. */
    public static final TagKey<EntityType<?>> EVOLVES_IN_SPACE = TagKey.create(Registries.ENTITY_TYPE, Constant.id("evolves_in_space"));
    /** Hostile mobs that are never evolved for space. Takes precedence over {@link #EVOLVES_IN_SPACE}. */
    public static final TagKey<EntityType<?>> NEVER_EVOLVES_IN_SPACE = TagKey.create(Registries.ENTITY_TYPE, Constant.id("never_evolves_in_space"));
    /** The cross-loader convention tag for bosses, which keep their own health when evolved. */
    public static final TagKey<EntityType<?>> BOSSES = TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath("c", "bosses"));

    public static void register() {
    }
}
