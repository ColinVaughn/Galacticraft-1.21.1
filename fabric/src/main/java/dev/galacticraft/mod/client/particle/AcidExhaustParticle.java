/* Copyright (c) 2026 Team Galacticraft. Licensed under the MIT license. */
package dev.galacticraft.mod.client.particle;

import dev.galacticraft.mod.particle.ScaleParticleType;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.util.Mth;

/** Rising yellow smoke matching GC4's geothermal acid exhaust. */
public class AcidExhaustParticle extends TextureSheetParticle {
    private final SpriteSet sprites;

    protected AcidExhaustParticle(ClientLevel level, double x, double y, double z,
                                  double dx, double dy, double dz, SpriteSet sprites, float scale) {
        super(level, x, y, z, 0, 0, 0);
        this.sprites = sprites;
        xd = xd * 0.1 + dx / 10;
        yd = yd * 0.1 + dy;
        zd = zd * 0.1 + dz / 10;
        rCol = gCol = 0.8F + random.nextFloat() * 0.1F;
        bCol = random.nextFloat() * 0.1F;
        scale(scale);
        lifetime = (int) ((int) (8 / (random.nextDouble() * 0.8 + 0.2)) * scale * 5);
        hasPhysics = true;
        setSpriteFromAge(sprites);
    }

    @Override public ParticleRenderType getRenderType() { return ParticleRenderType.PARTICLE_SHEET_OPAQUE; }

    @Override public float getQuadSize(float partialTick) {
        return quadSize * Mth.clamp((age + partialTick) / lifetime * 32, 0, 1);
    }

    @Override public void tick() {
        xo = x;
        yo = y;
        zo = z;
        if (age++ >= lifetime) {
            remove();
            return;
        }
        setSpriteFromAge(sprites);
        yd += 0.004;
        move(xd, yd, zd);
        if (y == yo) {
            xd *= 1.1;
            zd *= 1.1;
        }
        xd *= 0.96;
        yd *= 0.96;
        zd *= 0.96;
        if (onGround) {
            xd *= 0.7;
            zd *= 0.7;
        }
    }

    public record Provider(SpriteSet sprites) implements ParticleProvider<ScaleParticleType> {
        @Override public Particle createParticle(ScaleParticleType type, ClientLevel level, double x, double y, double z,
                                                  double dx, double dy, double dz) {
            return new AcidExhaustParticle(level, x, y, z, dx, dy, dz, sprites, type.getScale());
        }
    }
}
