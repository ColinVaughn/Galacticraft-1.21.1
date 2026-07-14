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

package dev.galacticraft.neoforge.client.model;

import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.content.GCBlocks;
import dev.galacticraft.machinelib.client.impl.model.MachineBakedModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransform;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Direction;
import net.minecraft.data.models.model.ModelLocationUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.ModelEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.List;

/** Installs loader-native baked replacements for block-entity connected models. */
public final class GCNeoDynamicModels {
    public static void modifyBakingResult(ModelEvent.ModifyBakingResult event) {
        event.getModels().replaceAll((location, model) ->
                location.id().getNamespace().equals(Constant.MOD_ID)
                                && "inventory".equals(location.variant())
                                && model instanceof MachineBakedModel
                        ? new MachineItemModel(model)
                        : model);
        for (Block pipe : GCBlocks.GLASS_FLUID_PIPES.values()) replace(event, pipe, ModelLocationUtils.getModelLocation(pipe), 0.125F);
        replace(event, GCBlocks.GLASS_FLUID_PIPE, Constant.id("block/glass_fluid_pipe"), 0.125F);
        replace(event, GCBlocks.ALUMINUM_WIRE, Constant.id("block/aluminum_wire"), 0.125F);
        replace(event, GCBlocks.HEAVY_ALUMINUM_WIRE, Constant.id("block/heavy_aluminum_wire"), 0.1875F);
        var glass = event.getTextureGetter().apply(new Material(InventoryMenu.BLOCK_ATLAS, Constant.id("block/vacuum_glass_vanilla")));
        var frame = event.getTextureGetter().apply(new Material(InventoryMenu.BLOCK_ATLAS, Constant.id("block/aluminum_decoration")));
        var vacuumGlass = new VacuumGlassBakedModelNeoForge(glass, frame);
        event.getModels().replaceAll((location, model) ->
                location.id().equals(Constant.BakedModel.VACUUM_GLASS_MODEL) && !"inventory".equals(location.variant())
                        ? vacuumGlass
                        : model);
        ResourceLocation cannedFood = BuiltInRegistries.BLOCK.getKey(GCBlocks.CANNED_FOOD);
        event.getModels().replaceAll((location, model) -> location.id().equals(cannedFood) ? new CannedFoodBakedModelNeoForge(model) : model);
    }

    private static void replace(ModelEvent.ModifyBakingResult event, Block block, ResourceLocation texture, float radius) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        var sprite = event.getTextureGetter().apply(new Material(InventoryMenu.BLOCK_ATLAS, texture));
        var replacement = new ConnectedPipeBakedModel(sprite, radius);
        event.getModels().replaceAll((location, model) ->
                location.id().equals(id) && !"inventory".equals(location.variant()) ? replacement : model);
    }

    /**
     * Applies vanilla block-item transforms to NeoForge machine inventory models. ItemTransform stores
     * translations in model units, while the familiar JSON values are expressed in sixteenths of a block.
     */
    private static final class MachineItemModel implements BakedModel {
        private static final ItemTransform THIRD_PERSON = transform(75, 45, 0, 0, 2.5F, 0, 0.375F);
        private static final ItemTransform FIRST_PERSON = transform(0, 135, 0, 0, 0, 0, 0.4F);
        private static final ItemTransform GUI = transform(30, 225, 0, 0, 0, 0, 0.625F);
        private static final ItemTransform GROUND = transform(0, 0, 0, 0, 3, 0, 0.25F);
        private static final ItemTransform FIXED = transform(0, 0, 0, 0, 0, 0, 0.5F);
        private static final ItemTransforms TRANSFORMS = new ItemTransforms(
                THIRD_PERSON, THIRD_PERSON, FIRST_PERSON, FIRST_PERSON,
                ItemTransform.NO_TRANSFORM, GUI, GROUND, FIXED);

        private final BakedModel delegate;
        private final ItemOverrides overrides = new ItemOverrides() {
            @Override
            public BakedModel resolve(BakedModel model, ItemStack stack, @Nullable ClientLevel level,
                                      @Nullable LivingEntity entity, int seed) {
                BakedModel resolved = MachineItemModel.this.delegate.getOverrides().resolve(
                        MachineItemModel.this.delegate, stack, level, entity, seed);
                return resolved == null ? null : new MachineItemModel(resolved);
            }
        };

        private MachineItemModel(BakedModel delegate) {
            this.delegate = delegate;
        }

        private static ItemTransform transform(float rx, float ry, float rz,
                                               float tx, float ty, float tz, float scale) {
            return new ItemTransform(new Vector3f(rx, ry, rz),
                    new Vector3f(tx / 16.0F, ty / 16.0F, tz / 16.0F),
                    new Vector3f(scale, scale, scale));
        }

        @Override
        public @NotNull List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side,
                                                 @NotNull RandomSource random) {
            return this.delegate.getQuads(state, side, random);
        }

        @Override
        public boolean useAmbientOcclusion() {
            return this.delegate.useAmbientOcclusion();
        }

        @Override
        public boolean isGui3d() {
            return this.delegate.isGui3d();
        }

        @Override
        public boolean usesBlockLight() {
            return this.delegate.usesBlockLight();
        }

        @Override
        public boolean isCustomRenderer() {
            return this.delegate.isCustomRenderer();
        }

        @Override
        public @NotNull net.minecraft.client.renderer.texture.TextureAtlasSprite getParticleIcon() {
            return this.delegate.getParticleIcon();
        }

        @Override
        public @NotNull ItemTransforms getTransforms() {
            return TRANSFORMS;
        }

        @Override
        public @NotNull ItemOverrides getOverrides() {
            return this.overrides;
        }
    }

    private GCNeoDynamicModels() {}
}
