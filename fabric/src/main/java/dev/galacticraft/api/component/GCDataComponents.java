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

package dev.galacticraft.api.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import io.netty.buffer.ByteBuf;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.galacticraft.api.fluid.FluidData;
import dev.galacticraft.api.rocket.RocketData;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.util.StreamCodecs;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;

import java.util.List;
import java.util.function.UnaryOperator;

@SuppressWarnings({"unchecked", "rawtypes"})
public class GCDataComponents {
    public static final DataComponentType<RocketData> ROCKET_DATA = Registry.register(
            BuiltInRegistries.DATA_COMPONENT_TYPE,
            Constant.id("rocket_data"),
            DataComponentType.<RocketData>builder()
                    .persistent(RocketData.CODEC)
                    .networkSynchronized(RocketData.STREAM_CODEC)
                    .build()
    );
    public static final DataComponentType<Long> AMOUNT = register("amount", b -> b
            .persistent(Codec.LONG).networkSynchronized(StreamCodecs.LONG));
    public static final DataComponentType<Integer> COLOR = register("color", b -> b
            .persistent(ExtraCodecs.NON_NEGATIVE_INT).networkSynchronized(ByteBufCodecs.INT));
    public static final DataComponentType<Integer> TICKS_UNTIL_COOL = register("ticks_until_cool", b -> b
            .persistent(ExtraCodecs.NON_NEGATIVE_INT).networkSynchronized(ByteBufCodecs.INT));
    public static final DataComponentType<Boolean> CREATIVE = register("creative", b -> b
            .persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL));
    public static final DataComponentType<Integer> BUGGY_TYPE = register("buggy_type", b -> b
            .persistent(Codec.intRange(0, 2)).networkSynchronized(ByteBufCodecs.VAR_INT));
    /** A preset flag design id, see {@link dev.galacticraft.mod.content.FlagDesigns}. */
    public static final DataComponentType<ResourceLocation> FLAG_DESIGN = register("flag_design", b -> b
            .persistent(ResourceLocation.CODEC).networkSynchronized(ResourceLocation.STREAM_CODEC));
    /** The space race team a flag flies for, see {@link dev.galacticraft.mod.spacerace.TeamFlagOwner}. */
    public static final DataComponentType<dev.galacticraft.mod.spacerace.TeamFlagOwner> TEAM_FLAG = register("team_flag", b -> b
            .persistent(dev.galacticraft.mod.spacerace.TeamFlagOwner.CODEC)
            .networkSynchronized(dev.galacticraft.mod.spacerace.TeamFlagOwner.STREAM_CODEC));

    private static final Codec<ResourceKey<?>> ANY_KEY_CODEC = RecordCodecBuilder.create(i ->
            i.group(
                    ResourceLocation.CODEC.fieldOf("registry").forGetter(ResourceKey::registry),
                    ResourceLocation.CODEC.fieldOf("location").forGetter(ResourceKey::location)
            ).apply(i, (r, l) -> ResourceKey.create(ResourceKey.createRegistryKey(r), l)));
    private static final StreamCodec<ByteBuf, ResourceKey<?>> ANY_KEY_STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC,
            ResourceKey::registry,
            ResourceLocation.STREAM_CODEC,
            ResourceKey::location,
            (r, l) -> ResourceKey.create(ResourceKey.createRegistryKey(r), l)
    );

    /**
     * Accepts a rocket part research ID such as {@code mypack:rocket_engine/ion}, or its object form.
     */
    public static final Codec<ResourceKey<?>> ROCKET_PART_KEY_CODEC = Codec.withAlternative(
            Codec.STRING.comapFlatMap(GCDataComponents::parseRocketPartKey, GCDataComponents::writeRocketPartKey), ANY_KEY_CODEC).validate(key ->
            java.util.Arrays.stream(dev.galacticraft.api.rocket.part.RocketPartTypes.values())
                    .anyMatch(type -> type.key.equals(key.registryKey()))
                    ? DataResult.success(key) : DataResult.error(() -> "Not a rocket part registry: " + key.registry()));

    public static final DataComponentType<ResourceKey<?>> KEY = register("key", b -> b
            .persistent(ANY_KEY_CODEC).networkSynchronized(ANY_KEY_STREAM_CODEC));

    /**
     * Rocket parts unlocked by consuming a schematic in the rocket workbench. Set on an item
     * or stack as research IDs, for example {@code mypack:rocket_engine/ion}.
     */
    public static final DataComponentType<List<ResourceKey<?>>> ROCKET_PART_UNLOCKS = register("rocket_part_unlocks", b -> b
            .persistent(ROCKET_PART_KEY_CODEC.listOf()).networkSynchronized(ANY_KEY_STREAM_CODEC.apply(ByteBufCodecs.list())));

    public static final DataComponentType<FluidData> FLUID_DATA = Registry.register(
            BuiltInRegistries.DATA_COMPONENT_TYPE,
            Constant.id("fluid_data"),
            DataComponentType.<FluidData>builder()
                    .persistent(FluidData.CODEC)
                    .networkSynchronized(FluidData.STREAM_CODEC)
                    .build()
    );

    private static DataResult<ResourceKey<?>> parseRocketPartKey(String text) {
        ResourceLocation id = ResourceLocation.tryParse(text);
        int slash = id == null ? -1 : id.getPath().indexOf('/');
        if (slash <= 0) return DataResult.error(() -> "Expected <namespace>:rocket_<slot>/<part>, got " + text);
        ResourceKey registry = ResourceKey.createRegistryKey(Constant.id(id.getPath().substring(0, slash)));
        ResourceKey<?> key = ResourceKey.create(registry, id.withPath(id.getPath().substring(slash + 1)));
        return DataResult.success(key);
    }

    private static String writeRocketPartKey(ResourceKey<?> key) {
        return dev.galacticraft.api.rocket.part.RocketPart.researchId(key).toString();
    }

    private static <T> DataComponentType<T> register(String id, UnaryOperator<DataComponentType.Builder<T>> op) {
        return Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Constant.id(id), op.apply(DataComponentType.builder()).build());
    }

    public static void init() {
    }
}
