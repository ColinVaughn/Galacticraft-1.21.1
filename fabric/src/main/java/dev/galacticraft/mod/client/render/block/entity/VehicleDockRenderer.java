/*
 * Copyright (c) 2019-2026 Team Galacticraft
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

package dev.galacticraft.mod.client.render.block.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.content.block.jupiter.machine.VehicleDockBlockEntity;
import dev.galacticraft.mod.content.entity.vehicle.CloudDiverEntity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Animates the dock boom and hose toward a linked Cloud Diver. The service clip and hose pulses
 * play while resources are transferring.
 */
@Environment(EnvType.CLIENT)
public class VehicleDockRenderer extends JupiterMachineRenderer<VehicleDockBlockEntity> {
    private static final ResourceLocation TEXTURE = Constant.id("textures/model/jupiter/vehicle_dock.png");
    /** A dark rubber texel on the reel drum. */
    private static final float HOSE_U = 3.5F / 128, HOSE_V = 63.5F / 128;
    private static final float HOSE_RADIUS = 0.045F;
    private static final int HOSE_SEGMENTS = 20;
    private static final String[] TIP_CHAIN = {"root", "arm", "boom", "nozzle", "hose_port"};

    /** Per-dock client state: eased connection, and the arm's current and wanted swing in radians. */
    private static final class Motion { double time = Double.NaN; float connect, aim; }
    private static final Map<VehicleDockBlockEntity, Motion> MOTION = new WeakHashMap<>();

    public VehicleDockRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(VehicleDockBlockEntity dock, float partialTick, PoseStack poses, MultiBufferSource buffers, int light, int overlay) {
        var level = dock.getLevel();
        if (level == null) return;
        BlockPos pos = dock.getBlockPos();
        Machine machine = machine(dock.getBlockState().getBlock());
        var model = machine.model();
        double ticks = level.getGameTime() + partialTick;
        double seconds = ticks / 20.0 + (pos.asLong() & 63) / 8.0;
        float facing = dock.getBlockState().hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                ? 180 - dock.getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING).toYRot() : 0;

        CloudDiverEntity diver = linkedDiver(dock);
        Motion motion = MOTION.computeIfAbsent(dock, key -> new Motion());
        float dt = Double.isNaN(motion.time) ? 0 : (float) Mth.clamp((ticks - motion.time) / 20.0, 0, 0.25);
        motion.time = ticks;
        motion.connect = Mth.approach(motion.connect, diver != null ? 1 : 0, dt * 1.5F);
        float connect = motion.connect * motion.connect * (3 - 2 * motion.connect);

        model.reset();
        Vec3 port = null;
        if (motion.connect <= 0) {
            model.animate(machine.idle(), seconds);
            motion.aim = 0;
        } else {
            model.animate("animation.vehicle_dock.connect", connect);
            if (dock.isAnimating()) model.animate("animation.vehicle_dock.service", seconds);
            if (diver != null) {
                port = diver.servicePort(Vec3.atCenterOf(pos), partialTick).subtract(Vec3.atLowerCornerOf(pos));
                float wanted = aimAt(model, facing, port);
                motion.aim += Mth.wrapDegrees((wanted - motion.aim) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD * Math.min(1, dt * 5);
            }
            model.bone("arm").yRot += motion.aim * connect;
        }

        poses.pushPose();
        poses.translate(0.5, 0, 0.5);
        poses.mulPose(Axis.YP.rotationDegrees(facing));
        drawPosed(machine, poses, buffers, light, overlay);
        poses.popPose();

        if (port != null && connect > 0.6F) {
            Vector3f tip = tip(model, facing);
            Vec3 start = new Vec3(tip.x, tip.y, tip.z);
            // Pay the hose out as the boom finishes extending, and never draw it stretched across the map.
            if (start.distanceTo(port) < 6) {
                Vec3 end = start.lerp(port, Mth.clamp((connect - 0.6F) / 0.4F, 0, 1));
                drawHose(poses, buffers, dock, start, end, dock.isAnimating(), ticks);
            }
        }
    }

    private static CloudDiverEntity linkedDiver(VehicleDockBlockEntity dock) {
        BlockPos pos = dock.getBlockPos();
        var divers = dock.getLevel().getEntitiesOfClass(CloudDiverEntity.class, new AABB(pos).inflate(5, 6, 5),
                diver -> diver.isAlive() && diver.dock().filter(pos::equals).isPresent());
        return divers.isEmpty() ? null : divers.getFirst();
    }

    /** Nozzle tip in block-local coordinates, for the model as currently posed. */
    private static Vector3f tip(dev.galacticraft.mod.client.model.MeteorDefenseModel model, float facing) {
        PoseStack local = new PoseStack();
        local.translate(0.5, 0, 0.5);
        local.mulPose(Axis.YP.rotationDegrees(facing));
        local.scale(-1, -1, 1);
        for (String bone : TIP_CHAIN) model.bone(bone).translateAndRotate(local);
        return local.last().pose().transformPosition(0, 0, 0, new Vector3f());
    }

    /**
     * The extra arm swing (radians) that brings the nozzle closest to {@code target} across the ground. Searched rather
     * than solved, so it holds whatever the Bedrock axis mirroring does to rotation signs.
     */
    private static float aimAt(dev.galacticraft.mod.client.model.MeteorDefenseModel model, float facing, Vec3 target) {
        var arm = model.bone("arm");
        float base = arm.yRot, best = 0;
        double bestDistance = Double.MAX_VALUE;
        for (float step : new float[]{15, 3, 0.5F}) {
            float around = best;
            float span = step == 15 ? 180 : step * 5;
            for (float offset = -span; offset <= span; offset += step) {
                float candidate = around + offset * Mth.DEG_TO_RAD;
                arm.yRot = base + candidate;
                Vector3f tip = tip(model, facing);
                double dx = tip.x - target.x, dz = tip.z - target.z, distance = dx * dx + dz * dz;
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = candidate;
                }
            }
        }
        arm.yRot = base;
        return best;
    }

    private static Vec3 hosePoint(Vec3 start, Vec3 end, float t) {
        double sag = 0.2 + 0.1 * start.distanceTo(end);
        return start.lerp(end, t).subtract(0, sag * 4 * t * (1 - t), 0);
    }

    private static void drawHose(PoseStack poses, MultiBufferSource buffers, VehicleDockBlockEntity dock, Vec3 start, Vec3 end, boolean flowing, double ticks) {
        var level = dock.getLevel();
        BlockPos origin = dock.getBlockPos();
        Vec3[] points = new Vec3[HOSE_SEGMENTS + 1];
        int[] lights = new int[HOSE_SEGMENTS + 1];
        for (int i = 0; i <= HOSE_SEGMENTS; i++) {
            points[i] = hosePoint(start, end, i / (float) HOSE_SEGMENTS);
            lights[i] = LevelRenderer.getLightColor(level, BlockPos.containing(points[i].add(Vec3.atLowerCornerOf(origin))));
        }
        PoseStack.Pose pose = poses.last();
        VertexConsumer rubber = buffers.getBuffer(RenderType.entityCutout(TEXTURE));
        for (int i = 0; i < HOSE_SEGMENTS; i++) tube(rubber, pose, points[i], points[i + 1], HOSE_RADIUS, lights[i], lights[i + 1]);
        if (!flowing) return;
        VertexConsumer glow = buffers.getBuffer(RenderType.lightning());
        for (int k = 0; k < 4; k++) {
            float t = (float) ((ticks / 20.0 * 0.9 + k / 4.0) % 1.0);
            float a = Math.max(0, t - 0.025F), b = Math.min(1, t + 0.025F);
            boolean oxygen = k % 2 == 1;
            glowRing(glow, pose, hosePoint(start, end, a), hosePoint(start, end, b), HOSE_RADIUS * 1.5F,
                    oxygen ? 0.1F : 0.12F, oxygen ? 0.42F : 0.6F, oxygen ? 0.6F : 0.22F); // Keep additive pulses dim enough to retain their color.
        }
    }

    /** Two perpendicular unit vectors across the segment from {@code a} to {@code b}. */
    private static Vec3[] frame(Vec3 a, Vec3 b) {
        Vec3 along = b.subtract(a).normalize();
        Vec3 side = along.cross(new Vec3(0, 1, 0));
        if (side.lengthSqr() < 1e-6) side = along.cross(new Vec3(1, 0, 0));
        side = side.normalize();
        return new Vec3[]{side, along.cross(side).normalize()};
    }

    private static void tube(VertexConsumer out, PoseStack.Pose pose, Vec3 a, Vec3 b, float radius, int lightA, int lightB) {
        Vec3[] f = frame(a, b);
        Vec3[] corners = {f[0].add(f[1]), f[0].scale(-1).add(f[1]), f[0].scale(-1).subtract(f[1]), f[0].subtract(f[1])};
        for (int i = 0; i < 4; i++) {
            Vec3 c0 = corners[i].scale(radius), c1 = corners[(i + 1) % 4].scale(radius);
            Vec3 normal = c0.add(c1).normalize();
            vertex(out, pose, a.add(c0), lightA, normal);
            vertex(out, pose, b.add(c0), lightB, normal);
            vertex(out, pose, b.add(c1), lightB, normal);
            vertex(out, pose, a.add(c1), lightA, normal);
        }
    }

    private static void vertex(VertexConsumer out, PoseStack.Pose pose, Vec3 p, int light, Vec3 normal) {
        out.addVertex(pose, (float) p.x, (float) p.y, (float) p.z).setColor(1F, 1F, 1F, 1F).setUv(HOSE_U, HOSE_V)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
    }

    private static void glowRing(VertexConsumer out, PoseStack.Pose pose, Vec3 a, Vec3 b, float radius, float red, float green, float blue) {
        Vec3[] f = frame(a, b);
        Vec3[] corners = {f[0].add(f[1]), f[0].scale(-1).add(f[1]), f[0].scale(-1).subtract(f[1]), f[0].subtract(f[1])};
        for (int i = 0; i < 4; i++) {
            Vec3 c0 = corners[i].scale(radius), c1 = corners[(i + 1) % 4].scale(radius);
            for (Vec3 p : new Vec3[]{a.add(c0), b.add(c0), b.add(c1), a.add(c1)}) {
                out.addVertex(pose, (float) p.x, (float) p.y, (float) p.z).setColor(red, green, blue, 0.9F);
            }
        }
    }
}
