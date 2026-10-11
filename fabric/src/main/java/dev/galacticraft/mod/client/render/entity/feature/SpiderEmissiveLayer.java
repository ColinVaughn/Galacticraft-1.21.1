package dev.galacticraft.mod.client.render.entity.feature;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

@Environment(EnvType.CLIENT)
public class SpiderEmissiveLayer<T extends LivingEntity, M extends EntityModel<T>> extends EyesLayer<T, M> {
    private final RenderType layer;

    public SpiderEmissiveLayer(RenderLayerParent<T, M> renderer, ResourceLocation texture) {
        super(renderer);
        this.layer = RenderType.eyes(texture);
    }

    @Override
    public RenderType renderType() {
        return this.layer;
    }
}
