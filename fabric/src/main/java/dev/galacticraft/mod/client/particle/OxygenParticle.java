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

package dev.galacticraft.mod.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.particles.SimpleParticleType;

/** The original Sensor Glasses oxygen-leak particle, ported from Galacticraft Legacy. */
public final class OxygenParticle extends TextureSheetParticle {
    private static final float START_SCALE = 0.1F;

    private final double startX;
    private final double startY;
    private final double startZ;

    private OxygenParticle(ClientLevel level, double x, double y, double z,
                           double xSpeed, double ySpeed, double zSpeed, SpriteSet sprites) {
        super(level, x, y, z, 0.0D, 0.0D, 0.0D);
        this.startX = this.x = x;
        this.startY = this.y = y;
        this.startZ = this.z = z;
        this.xd = xSpeed;
        this.yd = ySpeed;
        this.zd = zSpeed;
        this.quadSize = START_SCALE;
        this.rCol = 0.7F;
        this.gCol = 0.7F;
        this.bCol = 1.0F;
        this.lifetime = this.random.nextInt(10) + 40;
        this.hasPhysics = false;
        this.setSprite(sprites.get(this.random.nextInt(8), 8));
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public float getQuadSize(float partialTick) {
        float progress = ((float) this.age + partialTick) / (float) this.lifetime;
        float remaining = 1.0F - progress;
        return START_SCALE * (1.0F - remaining * remaining);
    }

    @Override
    public int getLightColor(float partialTick) {
        int light = super.getLightColor(partialTick);
        float progress = (float) this.age / (float) this.lifetime;
        progress *= progress;
        progress *= progress;
        int skyLight = Math.min(240, LightTexture.sky(light) + (int) (progress * 240.0F));
        return LightTexture.pack(LightTexture.block(light), skyLight);
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;

        float progress = (float) this.age / (float) this.lifetime;
        float verticalProgress = progress;
        float motionProgress = 1.0F - (-progress + progress * progress * 2.0F);
        this.setPos(
                this.startX + this.xd * motionProgress,
                this.startY + this.yd * motionProgress + 1.0F - verticalProgress,
                this.startZ + this.zd * motionProgress
        );

        if (this.age++ >= this.lifetime) {
            this.remove();
        }
    }

    public record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z,
                                       double xSpeed, double ySpeed, double zSpeed) {
            return new OxygenParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, this.sprites);
        }
    }
}
