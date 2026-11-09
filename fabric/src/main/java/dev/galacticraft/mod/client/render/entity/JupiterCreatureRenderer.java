/*
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

package dev.galacticraft.mod.client.render.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.client.model.MeteorDefenseModel;
import dev.galacticraft.mod.content.entity.jupiter.CloudRayEntity;
import dev.galacticraft.mod.content.entity.jupiter.SalvageDroneEntity;
import dev.galacticraft.mod.content.entity.jupiter.StaticJellyEntity;
import dev.galacticraft.mod.content.entity.jupiter.StormheartLeviathanEntity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Renders Jupiter creature models with emissive textures and blended animation clips.
 * Motion drives lean and bank; hurt and death clips override the normal activity poses.
 */
@Environment(EnvType.CLIENT)
public class JupiterCreatureRenderer<T extends Mob> extends EntityRenderer<T> {
    private static final class Motion {
        double lastTime = Double.NaN;
        float speed, climb, turn;
        float alt, overlay;
    }

    private final String id, base;
    private final float scale, lift;
    private final MeteorDefenseModel model;
    private final ResourceLocation texture, emissive;
    private final Map<T, Motion> motion = new WeakHashMap<>();

    public JupiterCreatureRenderer(EntityRendererProvider.Context context, String id, String base, float scale, float lift, float shadow) {
        super(context);
        this.id = id;
        this.base = base;
        this.scale = scale;
        this.lift = lift;
        this.shadowRadius = shadow;
        this.model = new MeteorDefenseModel(context.getResourceManager(), "entity/jupiter", id);
        this.texture = Constant.id("textures/entity/jupiter/" + id + ".png");
        this.emissive = Constant.id("textures/entity/jupiter/" + id + "_emissive.png");
    }

    private String clip(String name) {
        return "animation." + this.id + "." + name;
    }

    private boolean has(String name) {
        return this.model.hasAnimation(this.clip(name));
    }

    private void play(String name, double seconds, float weight) {
        if (this.has(name)) this.model.animate(this.clip(name), seconds, weight);
    }

    /** Scrubs a held clip authored as 0s = full negative, 1s = neutral, 2s = full positive. */
    private void scrub(String name, float amount) {
        if (this.has(name)) this.model.animate(this.clip(name), 1.0 + Mth.clamp(amount, -1, 1), 1.0F);
    }

    @Override
    public void render(T entity, float yaw, float partialTick, PoseStack poses, MultiBufferSource buffers, int light) {
        double time = entity.tickCount + partialTick;
        double seconds = time / 20.0 + (entity.getId() % 17) * 0.37;
        Motion m = this.motion.computeIfAbsent(entity, key -> new Motion());
        float dt = Double.isNaN(m.lastTime) ? 1.0F : (float) Mth.clamp(time - m.lastTime, 0, 4);
        m.lastTime = time;
        float ease = 1.0F - (float) Math.exp(-dt * 0.25);
        Vec3 velocity = new Vec3(entity.getX() - entity.xo, entity.getY() - entity.yo, entity.getZ() - entity.zo);
        float facing = entity.yBodyRot * Mth.DEG_TO_RAD;
        float forward = (float) (-velocity.x * Mth.sin(facing) + velocity.z * Mth.cos(facing));
        m.speed = Mth.lerp(ease, m.speed, forward);
        m.climb = Mth.lerp(ease, m.climb, (float) velocity.y);
        m.turn = Mth.lerp(ease, m.turn, Mth.wrapDegrees(entity.yBodyRot - entity.yBodyRotO));

        this.model.reset();
        float glow = 1.0F;
        boolean dying = entity.deathTime > 0 && this.has("death");
        if (dying) {
            this.play("death", (entity.deathTime + partialTick) / 20.0 * this.model.length(this.clip("death")), 1.0F);
        } else {
            glow = this.pose(entity, m, velocity, seconds, time, partialTick, ease);
        }
        if (!dying && entity.hurtTime > 0) this.play("hurt", (entity.hurtDuration - entity.hurtTime + partialTick) / 20.0, 1.0F);

        poses.pushPose();
        poses.translate(0, this.lift, 0);
        poses.mulPose(Axis.YP.rotationDegrees(180.0F - Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot)));
        if (entity.deathTime > 0 && !dying) {
            float fall = Math.min(1.0F, (entity.deathTime + partialTick - 1.0F) / 20.0F * 1.6F);
            poses.mulPose(Axis.ZP.rotationDegrees(Mth.sqrt(fall) * 90.0F));
        }
        poses.scale(this.scale, this.scale, this.scale);
        int overlay = OverlayTexture.pack(0, OverlayTexture.v(entity.hurtTime > 0 || entity.deathTime > 0));
        this.model.render(poses, buffers.getBuffer(RenderType.entityCutout(this.texture)), light, overlay);
        var glowBuffer = buffers.getBuffer(RenderType.eyes(this.emissive));
        if (!dying || entity.deathTime < 12) {
            this.model.render(poses, glowBuffer, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            // Eyes blend additively, so a second pass doubles the glow at the height of a charge or burst.
            if (glow > 1.05F) this.model.render(poses, glowBuffer, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        }
        poses.popPose();
        super.render(entity, yaw, partialTick, poses, buffers, light);
    }

    /** Blends the living clips for one creature; returns the glow multiplier. */
    private float pose(T entity, Motion m, Vec3 velocity, double seconds, double time, float partialTick, float ease) {
        if (entity instanceof CloudRayEntity ray) return this.poseRay(ray, m, seconds, ease);
        if (entity instanceof StaticJellyEntity jelly) return this.poseJelly(jelly, m, velocity, seconds, time, partialTick, ease);
        if (entity instanceof SalvageDroneEntity drone) return this.poseDrone(drone, m, seconds, partialTick, ease);
        if (entity instanceof StormheartLeviathanEntity leviathan) return this.poseLeviathan(leviathan, m, seconds, time, partialTick);
        this.play(this.base, seconds, 1.0F);
        return 1.0F;
    }

    private float poseLeviathan(StormheartLeviathanEntity leviathan, Motion m, double seconds, double time, float partialTick) {
        this.play(this.base, seconds, 1.0F);
        this.scrub("pitch", m.climb / 0.3F);
        this.scrub("bank", m.turn / 4.0F);
        if (leviathan.isExposed()) this.play("exposed", seconds, 1.0F);
        double elapsed = (leviathan.actionElapsed() + partialTick) / 20.0;
        float glow = 1.0F;
        switch (leviathan.action()) {
            case ROAR -> {
                this.play("roar", elapsed, 1.0F);
                glow = 2.0F;
            }
            case BREATH_CHARGE -> {
                this.play("breath_charge", elapsed, 1.0F);
                glow = 1.0F + (float) Math.min(1, elapsed / 1.5) * (0.6F + 0.4F * Mth.sin((float) time * 2.2F));
            }
            case BREATH -> {
                this.play("breath", elapsed, 1.0F);
                glow = 2.0F;
            }
            case TAIL_SLAM -> this.play("tail_slam", elapsed, 1.0F);
            case BITE -> this.play("attack", elapsed, 1.0F);
            default -> { }
        }
        return glow;
    }

    private float poseRay(CloudRayEntity ray, Motion m, double seconds, float ease) {
        m.overlay = Mth.lerp(ease, m.overlay, ray.isPanicking() ? 1 : 0);
        m.alt = Mth.lerp(ease, m.alt, m.climb < -0.015F && !ray.isPanicking() ? 1 : 0);
        this.play("idle", seconds, Math.max(0, 1 - m.overlay - m.alt));
        this.play("glide", seconds, m.alt);
        this.play("flee", seconds, m.overlay);
        this.scrub("bank", m.turn / 6.0F);
        return 1.0F;
    }

    private float poseJelly(StaticJellyEntity jelly, Motion m, Vec3 velocity, double seconds, double time, float partialTick, float ease) {
        m.alt = Mth.lerp(ease, m.alt, velocity.length() > 0.025 && jelly.charge() == 0 ? 1 : 0);
        this.play("idle", seconds, 1 - m.alt);
        this.play("swim", seconds, m.alt);
        float glow = 1.0F;
        if (jelly.charge() > 0) {
            this.play("charge", (jelly.charge() + partialTick) / 20.0, 1.0F);
            glow = 1.0F + (jelly.charge() / (float) StaticJellyEntity.CHARGE_TICKS) * (0.5F + 0.5F * Mth.sin((float) time * 1.7F));
        }
        if (jelly.burst() > 0) {
            this.play("discharge", (StaticJellyEntity.BURST_TICKS - jelly.burst() + partialTick) / 20.0, 1.0F);
            glow = 2.0F;
        }
        return glow;
    }

    private float poseDrone(SalvageDroneEntity drone, Motion m, double seconds, float partialTick, float ease) {
        m.overlay = Mth.lerp(ease, m.overlay, drone.isTargeting() ? 0 : 1);
        m.alt = Mth.lerp(ease, m.alt, drone.isTargeting() ? 1 : 0);
        this.play(this.base, seconds, 1.0F);
        this.play("scan", seconds, m.overlay);
        this.play("alert", seconds, m.alt);
        this.scrub("lean", m.speed / 0.24F);
        this.scrub("bank", m.turn / 8.0F);
        if (drone.attackTicks() > 0) this.play("attack", (10 - drone.attackTicks() + partialTick) / 20.0, 1.0F);
        return 1.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return this.texture;
    }
}
