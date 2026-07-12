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

package dev.galacticraft.mod.particle;

import com.mojang.serialization.MapCodec;
import dev.galacticraft.mod.Constant.Particle;
import dev.galacticraft.mod.content.GCRegistry;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.function.Function;

/** Particle registrations that use vanilla particle types instead of Fabric API factories. */
public class GCParticleTypes {
    public static final GCRegistry<ParticleType<?>> PARTICLES = new GCRegistry<>(BuiltInRegistries.PARTICLE_TYPE);

    public static final SimpleParticleType DRIPPING_CRUDE_OIL = simple(Particle.DRIPPING_CRUDE_OIL);
    public static final SimpleParticleType FALLING_CRUDE_OIL = simple(Particle.FALLING_CRUDE_OIL);
    public static final SimpleParticleType DRIPPING_FUEL = simple(Particle.DRIPPING_FUEL);
    public static final SimpleParticleType FALLING_FUEL = simple(Particle.FALLING_FUEL);
    public static final SimpleParticleType DRIPPING_SULFURIC_ACID = simple(Particle.DRIPPING_SULFURIC_ACID);
    public static final SimpleParticleType FALLING_SULFURIC_ACID = simple(Particle.FALLING_SULFURIC_ACID);
    public static final SimpleParticleType CRYOGENIC_PARTICLE = simple(Particle.CRYOGENIC_PARTICLE);
    public static final SimpleParticleType LANDER_FLAME_PARTICLE = simple(Particle.LANDER_FLAME);
    public static final SimpleParticleType SPARK_PARTICLE = simple(Particle.SPARK);
    public static final SimpleParticleType SPLASH_VENUS = simple(Particle.SPLASH_VENUS);

    public static final ParticleType<LaunchSmokeParticleOption> LAUNCH_SMOKE_PARTICLE = PARTICLES.register(
            Particle.LAUNCH_SMOKE, complex(type -> LaunchSmokeParticleOption.CODEC, type -> LaunchSmokeParticleOption.STREAM_CODEC));
    public static final ParticleType<EntityParticleOption> LAUNCH_FLAME = PARTICLES.register(
            Particle.LAUNCH_FLAME, complex(EntityParticleOption::codec, EntityParticleOption::streamCodec));
    public static final ParticleType<EntityParticleOption> LAUNCH_FLAME_LAUNCHED = PARTICLES.register(
            Particle.LAUNCH_FLAME_LAUNCHED, complex(EntityParticleOption::codec, EntityParticleOption::streamCodec));
    public static final ParticleType<ScaleParticleType> ACID_VAPOR_PARTICLE = PARTICLES.register(
            Particle.ACID_VAPOR_PARTICLE, complex(ScaleParticleType::codec, ScaleParticleType::streamCodec));

    private static SimpleParticleType simple(String id) {
        return PARTICLES.register(id, new SimpleParticleType(false));
    }

    private static <T extends ParticleOptions> ParticleType<T> complex(
            Function<ParticleType<T>, MapCodec<T>> codec,
            Function<ParticleType<T>, StreamCodec<? super RegistryFriendlyByteBuf, T>> streamCodec) {
        return new ParticleType<T>(false) {
            @Override
            public MapCodec<T> codec() {
                return codec.apply(this);
            }

            @Override
            public StreamCodec<? super RegistryFriendlyByteBuf, T> streamCodec() {
                return streamCodec.apply(this);
            }
        };
    }

    public static void register() {
    }
}
