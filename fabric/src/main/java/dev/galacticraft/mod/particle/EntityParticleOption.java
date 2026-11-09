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
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class EntityParticleOption implements ParticleOptions {
    private final ParticleType<?> type;
    private final UUID uuid;
    private final Vec3 carrier;

    public EntityParticleOption(ParticleType<?> type, UUID uuid) {
        this(type, uuid, Vec3.ZERO);
    }

    /** @param carrier per-tick motion inherited from the emitter and kept without drag, as in vacuum */
    public EntityParticleOption(ParticleType<?> type, UUID uuid, Vec3 carrier) {
        this.type = type;
        this.uuid = uuid;
        this.carrier = carrier;
    }

    public static MapCodec<EntityParticleOption> codec(ParticleType<? extends EntityParticleOption> type) {
        return RecordCodecBuilder.mapCodec(instance -> instance.group(
                UUIDUtil.CODEC.fieldOf("uuid").forGetter(o -> o.uuid),
                Vec3.CODEC.optionalFieldOf("carrier", Vec3.ZERO).forGetter(o -> o.carrier)
        ).apply(instance, (uuid, carrier) -> new EntityParticleOption(type, uuid, carrier)));
    }

    public static StreamCodec<ByteBuf, EntityParticleOption> streamCodec(ParticleType<?> type) {
        return StreamCodec.composite(UUIDUtil.STREAM_CODEC, o -> o.uuid, ByteBufCodecs.VECTOR3F, o -> o.carrier.toVector3f(),
                (uuid, carrier) -> new EntityParticleOption(type, uuid, new Vec3(carrier)));
    }

    public Vec3 getCarrier() {
        return this.carrier;
    }

    @Override
    public @NotNull ParticleType<?> getType() {
        return this.type;
    }

    public UUID getUuid() {
        return this.uuid;
    }
}
