package dev.galacticraft.mod.client.render.entity;

import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.client.model.entity.SpiderEggModel;
import dev.galacticraft.mod.client.render.entity.feature.SpiderEmissiveLayer;
import dev.galacticraft.mod.client.render.entity.model.GCEntityModelLayer;
import dev.galacticraft.mod.content.entity.boss.SpiderEggEntity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

@Environment(EnvType.CLIENT)
public class SpiderEggRenderer extends MobRenderer<SpiderEggEntity, SpiderEggModel> {
    private static final ResourceLocation TEXTURE = Constant.id("textures/entity/boss/spider_egg.png");

    public SpiderEggRenderer(EntityRendererProvider.Context context) {
        super(context, new SpiderEggModel(context.bakeLayer(GCEntityModelLayer.SPIDER_EGG)), 0.45F);
        this.addLayer(new SpiderEmissiveLayer<>(this, Constant.id("textures/entity/boss/spider_egg_emissive.png")));
    }

    @Override
    public ResourceLocation getTextureLocation(SpiderEggEntity entity) {
        return TEXTURE;
    }
}
