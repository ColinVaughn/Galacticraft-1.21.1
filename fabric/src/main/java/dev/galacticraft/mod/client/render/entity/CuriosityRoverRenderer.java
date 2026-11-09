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
import dev.galacticraft.mod.content.entity.mars.CuriosityRoverEntity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Animates the rover from its Blockbench clips. Wheel rotation follows distance traveled;
 * steering and camera clips follow wheel angles and head rotation.
 */
@Environment(EnvType.CLIENT)
public class CuriosityRoverRenderer extends EntityRenderer<CuriosityRoverEntity> {
    private static final String ID = "curiosity_rover";
    /** Blocks rolled per loop of the drive clip: two turns of a wheel 3/16 of a block in radius. */
    private static final double DRIVE_LOOP_DISTANCE = 2 * 2 * Math.PI * (3.0 / 16.0);
    private static final int CALM_GLOW = 0xFFFFFFFF, ANGRY_GLOW = 0xFFFF4A3A;

    private static final class Motion {
        double lastTime = Double.NaN;
        float steer, anger, gaze = 1;
    }

    private final MeteorDefenseModel model;
    private final ResourceLocation texture = Constant.id("textures/entity/mars/" + ID + ".png");
    private final ResourceLocation emissive = Constant.id("textures/entity/mars/" + ID + "_emissive.png");
    private final Map<CuriosityRoverEntity, Motion> motion = new WeakHashMap<>();

    public CuriosityRoverRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.75F;
        this.model = new MeteorDefenseModel(context.getResourceManager(), "entity/mars", ID);
    }

    private static String clip(String name) {
        return "animation." + ID + "." + name;
    }

    private void play(String name, double seconds, float weight) {
        this.model.animate(clip(name), seconds, weight);
    }

    /** Scrubs a held clip authored as 0s = full negative, 1s = neutral, 2s = full positive. */
    private void scrub(String name, float amount, float weight) {
        this.model.animate(clip(name), 1.0 + Mth.clamp(amount, -1, 1), weight);
    }

    @Override
    public void render(CuriosityRoverEntity rover, float yaw, float partialTick, PoseStack poses, MultiBufferSource buffers, int light) {
        double time = rover.tickCount + partialTick;
        double seconds = time / 20.0 + (rover.getId() % 17) * 0.37;
        Motion m = this.motion.computeIfAbsent(rover, key -> new Motion());
        float dt = Double.isNaN(m.lastTime) ? 1.0F : (float) Mth.clamp(time - m.lastTime, 0, 4);
        m.lastTime = time;
        float ease = 1.0F - (float) Math.exp(-dt * 0.25);

        this.model.reset();
        boolean dying = rover.deathTime > 0;
        if (dying) {
            this.play("death", (rover.deathTime + partialTick) / 20.0 * this.model.length(clip("death")), 1.0F);
        } else {
            this.pose(rover, m, seconds, partialTick, ease);
            if (rover.hurtTime > 0) this.play("hurt", (rover.hurtDuration - rover.hurtTime + partialTick) / 20.0, 1.0F);
        }

        poses.pushPose();
        poses.mulPose(Axis.YP.rotationDegrees(180.0F - Mth.rotLerp(partialTick, rover.yBodyRotO, rover.yBodyRot)));
        int overlay = OverlayTexture.pack(0, OverlayTexture.v(rover.hurtTime > 0 || rover.deathTime > 0));
        this.model.render(poses, buffers.getBuffer(RenderType.entityCutoutNoCull(this.texture)), light, overlay);
        if (!dying || rover.deathTime < 10) {
            int glow = FastColor.ARGB32.lerp(m.anger, CALM_GLOW, ANGRY_GLOW);
            this.model.render(poses, buffers.getBuffer(RenderType.eyes(this.emissive)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, glow);
        }
        poses.popPose();
        super.render(rover, yaw, partialTick, poses, buffers, light);
    }

    private void pose(CuriosityRoverEntity rover, Motion m, double seconds, float partialTick, float ease) {
        // Rolling: the drive clip's time is the odometer, so wheel spin always matches ground speed (and reverses).
        double travel = Mth.lerp(partialTick, rover.wheelTravelO, rover.wheelTravel);
        this.play("drive", travel / DRIVE_LOOP_DISTANCE * this.model.length(clip("drive")), 1.0F);

        float turn = Mth.wrapDegrees(rover.yBodyRot - rover.yBodyRotO);
        m.steer = Mth.lerp(ease * 0.5F, m.steer, Mth.clamp(turn / 6.0F, -1, 1));
        this.scrub("steer", m.steer, 1.0F);

        m.anger = Mth.lerp(ease, m.anger, rover.isAngry() ? 1 : 0);
        float headYaw = Mth.wrapDegrees(Mth.rotLerp(partialTick, rover.yHeadRotO, rover.yHeadRot) - Mth.rotLerp(partialTick, rover.yBodyRotO, rover.yBodyRot));
        boolean watching = Math.abs(headYaw) > 4 || m.anger > 0.5F || rover.getXRot() != 0;
        m.gaze = Mth.lerp(ease * 0.5F, m.gaze, watching ? 0 : 1);
        this.play("idle", seconds, m.gaze * (1 - m.anger));
        this.scrub("look_yaw", headYaw / 75.0F, 1 - m.gaze);
        this.scrub("look_pitch", Mth.lerp(partialTick, rover.xRotO, rover.getXRot()) / 30.0F, 1 - m.gaze);

        this.play("alert", this.model.length(clip("alert")), m.anger);
        this.play("drill_spin", seconds, m.anger);

        double elapsed = (rover.actionElapsed() + partialTick) / 20.0;
        switch (rover.action()) {
            case SURVEY -> this.play("survey", elapsed, 1.0F);
            case ATTACK -> this.play("attack", elapsed, 1.0F);
            default -> { }
        }
    }

    @Override
    public ResourceLocation getTextureLocation(CuriosityRoverEntity rover) {
        return this.texture;
    }
}
