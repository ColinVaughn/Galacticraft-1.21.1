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

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.galacticraft.mod.content.entity.vehicle.RocketEntity;
import dev.galacticraft.mod.particle.EntityParticleOption;
import dev.galacticraft.mod.particle.LaunchSmokeParticleOption;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public class LaunchFlameParticle extends TextureSheetParticle {
    private final SpriteSet sprites;
    private final float smokeParticleScale;
    private final boolean spawnSmokeShort;
    private final UUID ridingEntity;
    private Vec3 carrier;
    private final @Nullable RocketEntity emitter;
    private Vec3 emitterPosition;
    private final Vec3 towardEngine;

    public LaunchFlameParticle(ClientLevel level, double posX, double posY, double posZ, double motX, double motY, double motZ, boolean launched, EntityParticleOption particleData, SpriteSet sprites) {
        super(level, posX, posY, posZ, 0.0D, 0.0D, 0.0D);
        this.xd = motX;
        this.yd = motY;
        this.zd = motZ;
        // A flame carried by a moving engine starts where the nozzle was a tick ago, so its spawn frame
        // interpolates along with the rocket instead of hanging a tick ahead of it.
        this.carrier = particleData.getCarrier();
        this.ridingEntity = particleData.getUuid();
        var player = this.ridingEntity == null ? null : level.getPlayerByUUID(this.ridingEntity);
        this.emitter = player != null && player.getVehicle() instanceof RocketEntity rocket && rocket.isInSpaceflight() ? rocket : null;
        this.emitterPosition = this.emitter == null ? Vec3.ZERO : this.emitter.position();
        this.xo = posX - this.carrier.x;
        this.yo = posY - this.carrier.y;
        this.zo = posZ - this.carrier.z;
        // Smoke billows back up the plume toward the engine: straight up under a launching rocket, and along
        // the exhaust axis for a carried flame, whose engine may point any way.
        Vec3 exhaust = new Vec3(motX, motY, motZ);
        this.towardEngine = (this.emitter != null || this.carrier.lengthSqr() > 0) && exhaust.lengthSqr() > 1.0E-6 ? exhaust.normalize().reverse() : new Vec3(0, 1, 0);
        this.rCol = 255F / 255F;
        this.gCol = 120F / 255F + this.random.nextFloat() / 3;
        this.bCol = 55F / 255F;
        this.quadSize *= launched ? 4F : 0.1F;
        this.smokeParticleScale = this.quadSize;
        this.lifetime = (int) (this.lifetime * 1F);
        this.hasPhysics = true;
        this.spawnSmokeShort = launched;
        this.sprites = sprites;
        this.setSpriteFromAge(sprites);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_OPAQUE;
    }

    @Override
    public void render(VertexConsumer consumer, Camera camera, float partialTicks) {
        RenderSystem.depthMask(false);
        RenderSystem.disableDepthTest();
        float var8 = (this.age + partialTicks) / this.lifetime * 32.0F;

        if (var8 < 0.0F) {
            var8 = 0.0F;
        }

        if (var8 > 1.0F) {
            var8 = 1.0F;
        }

        this.quadSize = this.smokeParticleScale * var8;
        super.render(consumer, camera, partialTicks);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;

        if (this.age++ >= this.lifetime) {
            this.smoke(this.spawnSmokeShort, 1.0F);
            this.smoke(this.spawnSmokeShort, 2.5F);
            if (!this.spawnSmokeShort) {
                this.smoke(false, 1.0F);
                this.smoke(false, 2.5F);
            }
            this.remove();
        } else {
            this.setSpriteFromAge(sprites);
            this.yd += 0.001D;
            // Follow the emitter's actual displacement; a frozen velocity drifts off-axis during turns and corrections.
            if (this.emitter != null && !this.emitter.isRemoved()) {
                Vec3 position = this.emitter.position();
                this.carrier = position.subtract(this.emitterPosition);
                this.emitterPosition = position;
            }
            // The carried motion is exempt from drag: the flame keeps pace with the engine and only its exhaust slows.
            this.move(this.xd + this.carrier.x, this.yd + this.carrier.y, this.zd + this.carrier.z);

            this.gCol += 0.01F;

            if (this.y == this.yo) {
                this.xd *= 1.1D;
                this.zd *= 1.1D;
            }

            this.xd *= 0.9599999785423279D;
            this.yd *= 0.9599999785423279D;
            this.zd *= 0.9599999785423279D;

            if (this.level.random.nextInt(5) == 1) {
                final List<Entity> entities = this.level.getEntitiesOfClass(Entity.class, this.getBoundingBox().inflate(1.0D, 0.5D, 1.0D));

                if (entities != null) {
                    for (Entity entity : entities) {
                        if (entity instanceof LivingEntity && entity.isAlive() && !entity.isOnFire() && !entity.getUUID().equals(this.ridingEntity)) {
                            entity.igniteForSeconds(3);
//                            GalacticraftCore.packetPipeline.sendToServer(new PacketSimple(EnumSimplePacket.S_SET_ENTITY_FIRE, GCCoreUtil.getDimensionType(entity.level()), new Object[]{entity.getEntityId()}));
                        }
                    }
                }
            }
        }
    }

    private void smoke(boolean launched, float scale) {
        Vec3 at = this.towardEngine.scale(this.random.nextDouble() * 2).add(this.x, this.y, this.z);
        this.level.addParticle(new LaunchSmokeParticleOption(launched, scale), at.x, at.y, at.z,
                this.xd + this.carrier.x, this.yd + this.carrier.y, this.zd + this.carrier.z);
    }

    @Override
    public int getLightColor(float partialTick) {
        return LightTexture.FULL_BRIGHT;
    }

    public record LaunchedProvider(SpriteSet sprites) implements ParticleProvider<EntityParticleOption> {
        @Override
        public Particle createParticle(EntityParticleOption typeIn, ClientLevel worldIn, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
            return new LaunchFlameParticle(worldIn, x, y, z, xSpeed, ySpeed, zSpeed, true, typeIn, this.sprites);
        }
    }

    public record Provider(SpriteSet sprites) implements ParticleProvider<EntityParticleOption> {
        @Override
        public Particle createParticle(EntityParticleOption typeIn, ClientLevel worldIn, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
            return new LaunchFlameParticle(worldIn, x, y, z, xSpeed, ySpeed, zSpeed, false, typeIn, this.sprites);
        }
    }
}
