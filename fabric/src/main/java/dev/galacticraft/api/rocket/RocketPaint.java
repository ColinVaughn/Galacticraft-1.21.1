/* Copyright (c) 2026 Team Galacticraft. Licensed under the MIT license. */
package dev.galacticraft.api.rocket;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.galacticraft.api.rocket.part.RocketPartTypes;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Missing colors keep the original textured finish, including the legacy rocket tint. */
public record RocketPaint(Optional<Integer> cone, Optional<Integer> body, Optional<Integer> fin,
                          Optional<Integer> engine, Optional<Integer> booster) {
    public static final RocketPaint NONE = new RocketPaint(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    private static final Codec<Integer> COLOR = Codec.INT.validate(color -> color >>> 24 == 255
            ? DataResult.success(color) : DataResult.error(() -> "Rocket paint must be opaque"));
    public static final Codec<RocketPaint> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            COLOR.optionalFieldOf("cone").forGetter(RocketPaint::cone),
            COLOR.optionalFieldOf("body").forGetter(RocketPaint::body),
            COLOR.optionalFieldOf("fin").forGetter(RocketPaint::fin),
            COLOR.optionalFieldOf("engine").forGetter(RocketPaint::engine),
            COLOR.optionalFieldOf("booster").forGetter(RocketPaint::booster)
    ).apply(instance, RocketPaint::new));

    public static final StreamCodec<ByteBuf, RocketPaint> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.optional(ByteBufCodecs.INT), RocketPaint::cone,
            ByteBufCodecs.optional(ByteBufCodecs.INT), RocketPaint::body,
            ByteBufCodecs.optional(ByteBufCodecs.INT), RocketPaint::fin,
            ByteBufCodecs.optional(ByteBufCodecs.INT), RocketPaint::engine,
            ByteBufCodecs.optional(ByteBufCodecs.INT), RocketPaint::booster, RocketPaint::new);

    public RocketPaint {
        for (var value : java.util.List.of(cone, body, fin, engine, booster)) {
            if (value.isPresent() && value.get() >>> 24 != 255) throw new IllegalArgumentException("Rocket paint must be opaque");
        }
    }

    public Optional<Integer> color(RocketPartTypes part) {
        return switch (part) {
            case CONE -> cone;
            case BODY -> body;
            case FIN -> fin;
            case ENGINE -> engine;
            case BOOSTER -> booster;
            case UPGRADE -> Optional.empty();
        };
    }

    public RocketPaint withColor(RocketPartTypes part, @Nullable Integer color) {
        var value = Optional.ofNullable(color);
        return switch (part) {
            case CONE -> new RocketPaint(value, body, fin, engine, booster);
            case BODY -> new RocketPaint(cone, value, fin, engine, booster);
            case FIN -> new RocketPaint(cone, body, value, engine, booster);
            case ENGINE -> new RocketPaint(cone, body, fin, value, booster);
            case BOOSTER -> new RocketPaint(cone, body, fin, engine, value);
            case UPGRADE -> this;
        };
    }
}