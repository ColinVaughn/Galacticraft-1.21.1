/* Copyright (c) 2026 Team Galacticraft. Licensed under the MIT license. */
package dev.galacticraft.mod.content;

import dev.galacticraft.api.registry.AddonRegistries;
import dev.galacticraft.api.universe.celestialbody.SpaceflightConfig;
import dev.galacticraft.mod.Constant;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.phys.Vec3;
import java.util.List;

/** Datagen defaults; the running game consumes the synchronized spaceflight registry. */
public final class GCSpaceflight {
    private GCSpaceflight() { }
    public static final List<SpaceflightConfig> DEFAULTS = List.of(
            body("earth", 0, 1024, 0, 96, 0.99727, 23.44, 0),
            body("moon", 0, 1024, 2400, 26, 0, 1.54, 90),
            body("mars", 626.7027304758813, 1024, 8626.702730475881, 51, 1.02596, 25.19, 0),
            body("venus", -13256.58516481257, 1024, 2451.185918096057, 91, 243.025, 177.36, 0),
            body("mercury", -6871.333527025293, 1024, -3100.9856485934974, 37, 58.646, 0.03, 0),
            body("asteroid", 6289.419162443239, 1024, -8249.999999999998, 7, 0.3781, 4, 0),
            body("jupiter", 11918.58428704209, 1024, 11499.999999999998, 1053, 0.41354, 3.13, 0),
            body("sol", -8000, 1024, 0, 1200, 25.38, 7.25, 0));
    private static SpaceflightConfig body(String name, double x, double y, double z, double radius,
                                          double period, double tilt, double phase) {
        return new SpaceflightConfig(ResourceKey.create(AddonRegistries.CELESTIAL_BODY, Constant.id(name)), new Vec3(x,y,z), radius,
                Constant.id("textures/environment/solar_system/" + (name.equals("sol") ? "sun" : name) + ".png"), period, tilt, phase, 42);
    }
    public static void bootstrap(BootstrapContext<SpaceflightConfig> context) {
        DEFAULTS.forEach(config -> context.register(ResourceKey.create(AddonRegistries.SPACEFLIGHT, config.body().location()), config));
    }
}