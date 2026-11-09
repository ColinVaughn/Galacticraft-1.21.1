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

import dev.galacticraft.mod.client.render.entity.feature.OxygenMaskRenderLayer;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class OxygenMaskRenderLayerTest {
    /**
     * Exported models often nest the head under the body. The layer used to call
     * {@code root().getChild("head")}, which throws for them, so building the renderer crashed the game.
     */
    @Test
    void aModelWithANestedHeadDoesNotCrashTheLayer() {
        ModelPart head = new ModelPart(List.of(), Map.of());
        ModelPart body = new ModelPart(List.of(), Map.of("head", head));
        ModelPart root = new ModelPart(List.of(), Map.of("body", body));
        HierarchicalModel<LivingEntity> model = new HierarchicalModel<>() {
            @Override
            public ModelPart root() {
                return root;
            }

            @Override
            public void setupAnim(LivingEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
            }
        };
        RenderLayerParent<LivingEntity, HierarchicalModel<LivingEntity>> parent = new RenderLayerParent<>() {
            @Override
            public HierarchicalModel<LivingEntity> getModel() {
                return model;
            }

            @Override
            public ResourceLocation getTextureLocation(LivingEntity entity) {
                return ResourceLocation.withDefaultNamespace("missing");
            }
        };

        assertDoesNotThrow(() -> new OxygenMaskRenderLayer<>(parent));
    }
}
