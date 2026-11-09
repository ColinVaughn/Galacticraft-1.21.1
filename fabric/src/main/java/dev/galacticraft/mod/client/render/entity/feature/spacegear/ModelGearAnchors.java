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

package dev.galacticraft.mod.client.render.entity.feature.spacegear;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.mixin.client.AnimalModelAgeableListModel;
import dev.galacticraft.mod.mixin.client.ModelPartAccessor;
import net.minecraft.client.model.AgeableListModel;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Finds animated head and body paths for gear placement. Uses named hierarchical parts, then
 * {@link AgeableListModel} part lists, then model fields. Unreadable models use hitbox fitting.
 */
public final class ModelGearAnchors {
    private static final Logger LOGGER = LoggerFactory.getLogger(Constant.MOD_ID + "/space_gear");
    private static final ModelGearAnchors NONE = new ModelGearAnchors(null, null, false);

    /** Exact part names, best first, compared in lower case. Shared with the GeckoLib bone search. */
    public static final List<String> HEAD_NAMES = List.of("head", "skull");
    public static final List<String> BODY_NAMES = List.of("body", "torso", "chest", "upper_body", "upperbody", "body1", "body0");
    /** Failing an exact name, the first part whose name contains this. */
    public static final String HEAD_FRAGMENT = "head";
    public static final String BODY_FRAGMENT = "body";

    private final @Nullable Anchor<SpaceGearFit.Helmet> head;
    private final @Nullable Anchor<SpaceGearFit.Tanks> body;
    /** Whether the model draws babies with {@link AgeableListModel}'s own head and body transforms. */
    private final boolean ageableList;

    /**
     * One piece of gear's attachment.
     *
     * @param path the parts from the outermost down to the anchor part, each of which applies its
     *             own transform before the next
     * @param fit the gear's placement in the anchor part's space
     */
    public record Anchor<F>(List<ModelPart> path, F fit) {
        public ModelPart part() {
            return this.path.getLast();
        }

        /** Moves {@code pose} into the anchor part's space, as the part currently stands. */
        public void enter(PoseStack pose) {
            for (ModelPart part : this.path) {
                part.translateAndRotate(pose);
            }
        }
    }

    private ModelGearAnchors(@Nullable Anchor<SpaceGearFit.Helmet> head, @Nullable Anchor<SpaceGearFit.Tanks> body, boolean ageableList) {
        this.head = head;
        this.body = body;
        this.ageableList = ageableList;
    }

    public @Nullable Anchor<SpaceGearFit.Helmet> head() {
        return this.head;
    }

    public @Nullable Anchor<SpaceGearFit.Tanks> body() {
        return this.body;
    }

    public boolean isEmpty() {
        return this.head == null && this.body == null;
    }

    public boolean ageableList() {
        return this.ageableList;
    }

    /** Reads a model. Never throws: an unreadable model has no anchors. */
    public static ModelGearAnchors of(EntityModel<?> model) {
        try {
            return read(model);
        } catch (RuntimeException | LinkageError e) {
            LOGGER.debug("Could not fit space gear to {}; falling back to its hitbox", model.getClass().getName(), e);
            return NONE;
        }
    }

    private static ModelGearAnchors read(EntityModel<?> model) {
        if (model instanceof HierarchicalModel<?> hierarchical) {
            ModelPart root = hierarchical.root();
            return anchors(find(root, HEAD_NAMES, HEAD_FRAGMENT), find(root, BODY_NAMES, BODY_FRAGMENT), false);
        }
        // Typed, so it survives obfuscation: vanilla's field names are not "head" and "body" outside development.
        if (model instanceof HumanoidModel<?> humanoid) {
            return anchors(List.of(humanoid.head), List.of(humanoid.body), true);
        }
        NamedParts named = namedFields(model);
        if (model instanceof AgeableListModel<?> && model instanceof AnimalModelAgeableListModel lists) {
            List<ModelPart> heads = new ArrayList<>();
            lists.callGetHeadParts().forEach(heads::add);
            List<ModelPart> head = named.head() != null ? named.head() : bulkiest(heads, List.of());
            List<ModelPart> body = named.body() != null ? named.body() : bulkiest(lists.callGetBodyParts(), heads);
            return anchors(head, body, true);
        }
        return anchors(named.head(), named.body(), false);
    }

    private static ModelGearAnchors anchors(@Nullable List<ModelPart> headPath, @Nullable List<ModelPart> bodyPath, boolean ageableList) {
        Anchor<SpaceGearFit.Helmet> head = null;
        if (headPath != null) {
            Optional<SpaceGearFit.Helmet> helmet = SpaceGearFit.helmet(bounds(headPath.getLast()));
            if (helmet.isPresent()) head = new Anchor<>(List.copyOf(headPath), helmet.get());
        }
        Anchor<SpaceGearFit.Tanks> body = null;
        if (bodyPath != null) {
            Optional<SpaceGearFit.Tanks> tanks = SpaceGearFit.tanks(bounds(bodyPath.getLast()), restRotation(bodyPath));
            if (tanks.isPresent()) body = new Anchor<>(List.copyOf(bodyPath), tanks.get());
        }
        return head == null && body == null ? NONE : new ModelGearAnchors(head, body, ageableList);
    }

    /**
     * Searches a part tree for the best-named part: an exact name from {@code names} (earlier names
     * win), and failing that, any name containing {@code fragment}. Shallower parts win ties.
     *
     * @return the path from {@code root} to the part, or null when nothing matches
     */
    static @Nullable List<ModelPart> find(ModelPart root, List<String> names, String fragment) {
        for (String name : names) {
            List<ModelPart> path = breadthFirst(root, candidate -> candidate.equals(name));
            if (path != null) return path;
        }
        return breadthFirst(root, candidate -> candidate.contains(fragment));
    }

    private static @Nullable List<ModelPart> breadthFirst(ModelPart root, Predicate<String> matches) {
        record Visit(ModelPart part, List<ModelPart> path) {}
        Deque<Visit> queue = new ArrayDeque<>();
        queue.add(new Visit(root, List.of(root)));
        while (!queue.isEmpty()) {
            Visit visit = queue.poll();
            for (Map.Entry<String, ModelPart> child : children(visit.part()).entrySet()) {
                List<ModelPart> path = new ArrayList<>(visit.path());
                path.add(child.getValue());
                if (matches.test(child.getKey().toLowerCase(Locale.ROOT)) && hasGeometry(child.getValue())) return path;
                queue.add(new Visit(child.getValue(), path));
            }
        }
        return null;
    }

    /**
     * The part with the most volume, as a one-part path. Part lists carry extras - legs, tails, and
     * overlays such as a humanoid's hat, which is bulkier than its body - so anything lying mostly
     * inside one of the {@code heads} is passed over.
     */
    private static @Nullable List<ModelPart> bulkiest(Iterable<ModelPart> parts, List<ModelPart> heads) {
        List<SpaceGearFit.Box> headSpace = new ArrayList<>();
        for (ModelPart head : heads) {
            SpaceGearFit.Box box = placed(head);
            if (box != null) headSpace.add(box);
        }
        ModelPart best = null;
        float bestVolume = 0;
        for (ModelPart part : parts) {
            SpaceGearFit.Box box = placed(part);
            if (box == null || heads.contains(part)) continue;
            float volume = volume(box);
            if (volume <= bestVolume || headSpace.stream().anyMatch(head -> overlap(box, head) > volume / 2)) continue;
            best = part;
            bestVolume = volume;
        }
        return best == null ? null : List.of(best);
    }

    /** A top-level part's extent in model space, placed by its resting pose. */
    private static @Nullable SpaceGearFit.Box placed(ModelPart part) {
        SpaceGearFit.Box own = bounds(part);
        if (own == null) return null;
        Matrix4f rest = restTransform(part.getInitialPose());
        SpaceGearFit.Box placed = null;
        for (int corner = 0; corner < 8; corner++) {
            Vector3f point = rest.transformPosition(new Vector3f(
                    (corner & 1) == 0 ? own.minX() : own.maxX(),
                    (corner & 2) == 0 ? own.minY() : own.maxY(),
                    (corner & 4) == 0 ? own.minZ() : own.maxZ()));
            placed = union(placed, SpaceGearFit.Box.around(point));
        }
        return placed;
    }

    private static float volume(SpaceGearFit.Box box) {
        return box.size(0) * box.size(1) * box.size(2);
    }

    private static float overlap(SpaceGearFit.Box a, SpaceGearFit.Box b) {
        float shared = 1;
        for (int axis = 0; axis < 3; axis++) {
            shared *= Math.max(0, Math.min(a.max(axis), b.max(axis)) - Math.max(a.min(axis), b.min(axis)));
        }
        return shared;
    }

    /** Head and body paths found through field names; either may be null. */
    private record NamedParts(@Nullable List<ModelPart> head, @Nullable List<ModelPart> body) {}

    /**
     * Finds head and body paths from model fields, including nested parts. Vanilla field names are
     * obfuscated outside development, so typed model access takes precedence.
     */
    private static NamedParts namedFields(EntityModel<?> model) {
        List<Field> fields = new ArrayList<>();
        for (Class<?> type = model.getClass(); type != null && type != EntityModel.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (field.getType() == ModelPart.class && !Modifier.isStatic(field.getModifiers())) fields.add(field);
            }
        }
        List<ModelPart> roots = new ArrayList<>();
        ModelPart head = null;
        ModelPart body = null;
        for (Field field : fields) {
            ModelPart part;
            try {
                field.setAccessible(true);
                part = (ModelPart) field.get(model);
            } catch (ReflectiveOperationException | RuntimeException e) {
                continue;
            }
            if (part == null) continue;
            roots.add(part);
            String name = field.getName().toLowerCase(Locale.ROOT);
            if (head == null && HEAD_NAMES.contains(name)) head = part;
            if (body == null && BODY_NAMES.contains(name)) body = part;
        }
        return new NamedParts(pathTo(head, roots), pathTo(body, roots));
    }

    private static @Nullable List<ModelPart> pathTo(@Nullable ModelPart target, List<ModelPart> roots) {
        if (target == null) return null;
        List<ModelPart> longest = List.of(target);
        for (ModelPart root : roots) {
            if (root == target) continue;
            List<ModelPart> path = pathFrom(root, target);
            if (path != null && path.size() > longest.size()) longest = path;
        }
        return longest;
    }

    private static @Nullable List<ModelPart> pathFrom(ModelPart root, ModelPart target) {
        record Visit(ModelPart part, List<ModelPart> path) {}
        Deque<Visit> queue = new ArrayDeque<>();
        queue.add(new Visit(root, List.of(root)));
        while (!queue.isEmpty()) {
            Visit visit = queue.poll();
            for (ModelPart child : children(visit.part()).values()) {
                List<ModelPart> path = new ArrayList<>(visit.path());
                path.add(child);
                if (child == target) return path;
                queue.add(new Visit(child, path));
            }
        }
        return null;
    }

    /**
     * Uses a part's own cubes, or its children in their resting poses if the part is empty.
     * Ignoring children of nonempty parts keeps horns, hats, and ears from enlarging the helmet.
     */
    static @Nullable SpaceGearFit.Box bounds(ModelPart part) {
        SpaceGearFit.Box own = cubeBounds(part, new Matrix4f());
        if (own != null) return own;
        return descendantBounds(part, new Matrix4f());
    }

    private static @Nullable SpaceGearFit.Box descendantBounds(ModelPart part, Matrix4f toAnchor) {
        SpaceGearFit.Box bounds = null;
        for (ModelPart child : children(part).values()) {
            Matrix4f childToAnchor = new Matrix4f(toAnchor).mul(restTransform(child.getInitialPose()));
            SpaceGearFit.Box box = union(cubeBounds(child, childToAnchor), descendantBounds(child, childToAnchor));
            bounds = union(bounds, box);
        }
        return bounds;
    }

    private static @Nullable SpaceGearFit.Box cubeBounds(ModelPart part, Matrix4f toAnchor) {
        SpaceGearFit.Box bounds = null;
        for (ModelPart.Cube cube : cubes(part)) {
            for (int corner = 0; corner < 8; corner++) {
                Vector3f point = toAnchor.transformPosition(new Vector3f(
                        (corner & 1) == 0 ? cube.minX : cube.maxX,
                        (corner & 2) == 0 ? cube.minY : cube.maxY,
                        (corner & 4) == 0 ? cube.minZ : cube.maxZ));
                bounds = union(bounds, SpaceGearFit.Box.around(point));
            }
        }
        return bounds;
    }

    /** A part's resting transform, in pixels, as {@link ModelPart#translateAndRotate} would apply it. */
    private static Matrix4f restTransform(PartPose pose) {
        return new Matrix4f().translate(pose.x, pose.y, pose.z)
                .rotate(new Quaternionf().rotationZYX(pose.zRot, pose.yRot, pose.xRot));
    }

    /** The anchor part's resting orientation in model space: every part on the path, rotated in turn. */
    private static Matrix3f restRotation(List<ModelPart> path) {
        Matrix3f rotation = new Matrix3f();
        for (ModelPart part : path) {
            PartPose pose = part.getInitialPose();
            rotation.rotate(new Quaternionf().rotationZYX(pose.zRot, pose.yRot, pose.xRot));
        }
        return rotation;
    }

    private static boolean hasGeometry(ModelPart part) {
        return bounds(part) != null;
    }

    private static @Nullable SpaceGearFit.Box union(@Nullable SpaceGearFit.Box a, @Nullable SpaceGearFit.Box b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.union(b);
    }

    private static List<ModelPart.Cube> cubes(ModelPart part) {
        return ((ModelPartAccessor) (Object) part).galacticraft$getCubes();
    }

    private static Map<String, ModelPart> children(ModelPart part) {
        return ((ModelPartAccessor) (Object) part).galacticraft$getChildren();
    }
}
