/* Copyright (c) 2026 Team Galacticraft. Licensed under the MIT license. */
package dev.galacticraft.api.universe.celestialbody;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.galacticraft.api.registry.AddonRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** Physical flight and surface-sky representation of a registered body, synchronized with its datapack. */
public record SpaceflightConfig(ResourceKey<CelestialBody<?, ?>> body, Vec3 center, double radius,
                                ResourceLocation texture, double period, double tilt, double phase, double skyRadius) {
    private static final Codec<Vec3> CENTER = Vec3.CODEC.validate(value ->
            Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z)
                    && Math.abs(value.x) < 29_000_000 && Math.abs(value.y) < 29_000_000 && Math.abs(value.z) < 29_000_000
                    ? DataResult.success(value) : DataResult.error(() -> "Spaceflight center must be finite and inside the world border"));
    public static final Codec<SpaceflightConfig> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceKey.codec(AddonRegistries.CELESTIAL_BODY).fieldOf("body").forGetter(SpaceflightConfig::body),
            CENTER.fieldOf("center").forGetter(SpaceflightConfig::center),
            Codec.doubleRange(1, 1_000_000).fieldOf("radius").forGetter(SpaceflightConfig::radius),
            ResourceLocation.CODEC.fieldOf("texture").forGetter(SpaceflightConfig::texture),
            Codec.doubleRange(0, 1_000_000_000).optionalFieldOf("rotation_period", 0.0).forGetter(SpaceflightConfig::period),
            Codec.doubleRange(-360, 360).optionalFieldOf("axial_tilt", 0.0).forGetter(SpaceflightConfig::tilt),
            Codec.doubleRange(-360, 360).optionalFieldOf("phase", 0.0).forGetter(SpaceflightConfig::phase),
            Codec.doubleRange(0.01, 1_000_000).optionalFieldOf("sky_radius", 42.0).forGetter(SpaceflightConfig::skyRadius)
    ).apply(i, SpaceflightConfig::new));
}