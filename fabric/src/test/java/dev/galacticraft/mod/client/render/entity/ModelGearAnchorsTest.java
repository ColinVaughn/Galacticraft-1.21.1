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

package dev.galacticraft.mod.client.render.entity;

import dev.galacticraft.mod.client.render.entity.feature.spacegear.ModelGearAnchors;
import net.minecraft.client.model.AgeableListModel;
import net.minecraft.client.model.CowModel;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Cow;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ModelGearAnchorsTest {
    /**
     * A humanoid's body-part list also holds its hat overlay, which is the bulkiest part in it. Picking
     * by size put the tanks on the back of the hat, at head height.
     */
    @Test
    void aHumanoidsTanksHangOffItsBodyNotItsHat() {
        HumanoidModel<LivingEntity> model = new HumanoidModel<>(HumanoidModel.createMesh(CubeDeformation.NONE, 0).getRoot().bake(64, 64));
        ModelGearAnchors anchors = ModelGearAnchors.of(model);
        assertNotNull(anchors.head(), "no helmet anchor");
        assertNotNull(anchors.body(), "no tank anchor");
        assertSame(model.head, anchors.head().part(), "the helmet should hang off the head");
        assertSame(model.body, anchors.body().part(), "the tanks should hang off the body, not the hat");
    }

    /**
     * Outside development vanilla's fields carry obfuscated names, so the part lists are all there is
     * to go on. The bulkiest body part is still not allowed to be an overlay sitting on the head.
     */
    @Test
    void withoutUsefulFieldNamesAHeadOverlayIsStillNotTheBody() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("p0", CubeListBuilder.create().addBox(-4, -8, -4, 8, 8, 8), PartPose.ZERO);
        root.addOrReplaceChild("p1", CubeListBuilder.create().addBox(-4, 0, -2, 8, 12, 4), PartPose.ZERO);
        root.addOrReplaceChild("p2", CubeListBuilder.create().addBox(-4, -8, -4, 8, 8, 8, new CubeDeformation(0.5F)), PartPose.ZERO);
        ModelPart baked = root.bake(64, 64);
        ObscureModel model = new ObscureModel(baked.getChild("p0"), baked.getChild("p1"), baked.getChild("p2"));

        ModelGearAnchors anchors = ModelGearAnchors.of(model);
        assertNotNull(anchors.body(), "no tank anchor");
        assertSame(model.p1, anchors.body().part(), "the tanks went on the head overlay instead of the body");
        assertNotNull(anchors.head(), "no helmet anchor");
        assertSame(model.p0, anchors.head().part());
    }

    /** Stands in for an obfuscated vanilla model: nothing about its field names says which part is which. */
    private static final class ObscureModel extends AgeableListModel<LivingEntity> {
        final ModelPart p0, p1, p2;

        ObscureModel(ModelPart p0, ModelPart p1, ModelPart p2) {
            this.p0 = p0;
            this.p1 = p1;
            this.p2 = p2;
        }

        @Override
        protected Iterable<ModelPart> headParts() {
            return List.of(this.p0);
        }

        @Override
        protected Iterable<ModelPart> bodyParts() {
            return List.of(this.p1, this.p2);
        }

        @Override
        public void setupAnim(LivingEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        }
    }

    @Test
    void aQuadrupedUsesItsHeadAndBody() {
        ModelPart root = CowModel.createBodyLayer().bakeRoot();
        ModelGearAnchors anchors = ModelGearAnchors.of(new CowModel<Cow>(root));
        assertNotNull(anchors.head(), "no helmet anchor");
        assertNotNull(anchors.body(), "no tank anchor");
        assertSame(root.getChild("head"), anchors.head().part());
        assertSame(root.getChild("body"), anchors.body().part());
    }

    @Test
    void aNestedHeadIsFoundWithThePathToIt() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition body = mesh.getRoot().addOrReplaceChild("body", CubeListBuilder.create().addBox(-4, 0, -2, 8, 12, 4), PartPose.offset(0, 0, 0));
        body.addOrReplaceChild("head", CubeListBuilder.create().addBox(-4, -8, -4, 8, 8, 8), PartPose.ZERO);
        ModelPart root = mesh.getRoot().bake(64, 64);

        ModelGearAnchors anchors = ModelGearAnchors.of(hierarchical(root));
        assertNotNull(anchors.head(), "the nested head was not found");
        ModelPart torso = root.getChild("body");
        assertEquals(List.of(root, torso, torso.getChild("head")), anchors.head().path(),
                "the helmet has to follow every part above the head");
    }

    @Test
    void aModelWithNothingRecognisableHasNoAnchors() {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("cube", CubeListBuilder.create().addBox(-4, 16, -4, 8, 8, 8), PartPose.ZERO);
        assertTrue(ModelGearAnchors.of(hierarchical(mesh.getRoot().bake(64, 64))).isEmpty());
    }

    private static HierarchicalModel<LivingEntity> hierarchical(ModelPart root) {
        return new HierarchicalModel<>() {
            @Override
            public ModelPart root() {
                return root;
            }

            @Override
            public void setupAnim(LivingEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
            }
        };
    }
}
