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

package dev.galacticraft.mod.compat.geckolib;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.galacticraft.mod.client.render.entity.feature.spacegear.ModelGearAnchors;
import dev.galacticraft.mod.client.render.entity.feature.spacegear.SpaceGearDrawer;
import dev.galacticraft.mod.client.render.entity.feature.spacegear.SpaceGearFit;
import dev.galacticraft.mod.client.render.entity.feature.spacegear.SpaceGearRenderState;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animation.state.BoneSnapshot;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Predicate;

/**
 * Fits space gear to GeckoLib head and body bones. Loaded only when GeckoLib is installed.
 * GeckoLib uses Y-up blocks; gear uses vanilla Y-down pixels. Convert bone geometry into gear
 * coordinates and flip X/Y on the output pose to preserve the animated transform.
 */
public class GeckoSpaceGearLayer<T extends GeoAnimatable> extends GeoRenderLayer<T> {
    private static final Matrix3f FLIP = new Matrix3f().scaling(-1.0F, -1.0F, 1.0F);
    private static final Anchors NONE = new Anchors(null, null, null, null);

    private final Map<BakedGeoModel, Anchors> anchors = Collections.synchronizedMap(new WeakHashMap<>());
    /** Set in {@link #preRender} for the entity being drawn, if it wears gear and its model has somewhere to put it. */
    private @Nullable Anchors current;
    private @Nullable LivingEntity wearer;

    private record Anchors(@Nullable GeoBone head, @Nullable SpaceGearFit.Helmet helmet,
                           @Nullable GeoBone body, @Nullable SpaceGearFit.Tanks tanks) {}

    public GeckoSpaceGearLayer(GeoRenderer<T> renderer) {
        super(renderer);
    }

    @Override
    public void preRender(PoseStack pose, T animatable, BakedGeoModel model, @Nullable RenderType renderType, MultiBufferSource buffers,
                          @Nullable VertexConsumer buffer, float partialTick, int light, int overlay) {
        this.current = null;
        this.wearer = null;
        if (animatable instanceof LivingEntity living && SpaceGearRenderState.wearsGear(living)) {
            Anchors found = this.anchors.computeIfAbsent(model, GeckoSpaceGearLayer::read);
            if (found != NONE) {
                this.current = found;
                this.wearer = living;
            }
        }
    }

    @Override
    public void renderForBone(PoseStack pose, T animatable, GeoBone bone, RenderType renderType, MultiBufferSource buffers,
                              VertexConsumer buffer, float partialTick, int light, int overlay) {
        Anchors anchors = this.current;
        LivingEntity wearer = this.wearer;
        if (anchors == null || wearer == null || animatable != wearer) return;

        if (bone == anchors.head() && anchors.helmet() != null && wearer.galacticraft$hasMask()) {
            pose.pushPose();
            pose.scale(-1.0F, -1.0F, 1.0F);
            SpaceGearDrawer.drawHelmet(pose, buffers, light, anchors.helmet());
            pose.popPose();
            SpaceGearRenderState.markDrawn(wearer);
        }
        if (bone == anchors.body() && anchors.tanks() != null && wearer.galacticraft$hasGear()) {
            pose.pushPose();
            pose.scale(-1.0F, -1.0F, 1.0F);
            SpaceGearDrawer.drawTanks(pose, buffers, light, anchors.tanks(), wearer.galacticraft$tankSize(0), wearer.galacticraft$tankSize(1));
            pose.popPose();
            SpaceGearRenderState.markDrawn(wearer);
        }
    }

    private static Anchors read(BakedGeoModel model) {
        try {
            GeoBone head = find(model, ModelGearAnchors.HEAD_NAMES, ModelGearAnchors.HEAD_FRAGMENT);
            GeoBone body = find(model, ModelGearAnchors.BODY_NAMES, ModelGearAnchors.BODY_FRAGMENT);
            SpaceGearFit.Helmet helmet = head == null ? null : SpaceGearFit.helmet(bounds(head)).orElse(null);
            SpaceGearFit.Tanks tanks = body == null ? null : SpaceGearFit.tanks(bounds(body), restRotation(body)).orElse(null);
            if (helmet == null && tanks == null) return NONE;
            return new Anchors(helmet == null ? null : head, helmet, tanks == null ? null : body, tanks);
        } catch (RuntimeException e) {
            return NONE;
        }
    }

    private static @Nullable GeoBone find(BakedGeoModel model, List<String> names, String fragment) {
        for (String name : names) {
            GeoBone bone = breadthFirst(model, candidate -> candidate.equals(name));
            if (bone != null) return bone;
        }
        return breadthFirst(model, candidate -> candidate.contains(fragment));
    }

    private static @Nullable GeoBone breadthFirst(BakedGeoModel model, Predicate<String> matches) {
        Deque<GeoBone> queue = new ArrayDeque<>(model.topLevelBones());
        while (!queue.isEmpty()) {
            GeoBone bone = queue.poll();
            if (matches.test(bone.getName().toLowerCase(Locale.ROOT)) && bounds(bone) != null) return bone;
            queue.addAll(bone.getChildBones());
        }
        return null;
    }

    /** The bone's own cubes, or its descendants' when it is an empty group, in vanilla pixels. */
    private static @Nullable SpaceGearFit.Box bounds(GeoBone bone) {
        SpaceGearFit.Box own = cubeBounds(bone);
        if (own != null) return own;
        SpaceGearFit.Box bounds = null;
        for (GeoBone child : bone.getChildBones()) {
            SpaceGearFit.Box box = bounds(child);
            if (box != null) bounds = bounds == null ? box : bounds.union(box);
        }
        return bounds;
    }

    private static @Nullable SpaceGearFit.Box cubeBounds(GeoBone bone) {
        SpaceGearFit.Box bounds = null;
        for (GeoCube cube : bone.getCubes()) {
            for (GeoQuad quad : cube.quads()) {
                if (quad == null) continue;
                for (GeoVertex vertex : quad.vertices()) {
                    Vector3f p = vertex.position();
                    SpaceGearFit.Box point = SpaceGearFit.Box.around(new Vector3f(-p.x * 16, -p.y * 16, p.z * 16));
                    bounds = bounds == null ? point : bounds.union(point);
                }
            }
        }
        return bounds;
    }

    /** The bone's resting orientation, ancestors included, carried over into vanilla's flipped frame. */
    private static Matrix3f restRotation(GeoBone bone) {
        Deque<GeoBone> chain = new ArrayDeque<>();
        for (GeoBone link = bone; link != null; link = link.getParent()) chain.push(link);
        Matrix3f rotation = new Matrix3f();
        for (GeoBone link : chain) {
            // GeckoLib only takes the snapshot once the model first animates; until then the bone is at rest.
            BoneSnapshot rest = link.getInitialSnapshot();
            if (rest != null) {
                rotation.rotate(new Quaternionf().rotationZYX(rest.getRotZ(), rest.getRotY(), rest.getRotX()));
            } else {
                rotation.rotate(new Quaternionf().rotationZYX(link.getRotZ(), link.getRotY(), link.getRotX()));
            }
        }
        return new Matrix3f(FLIP).mul(rotation).mul(FLIP);
    }
}
