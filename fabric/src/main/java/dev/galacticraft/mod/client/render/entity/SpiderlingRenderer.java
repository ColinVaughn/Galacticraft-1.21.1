package dev.galacticraft.mod.client.render.entity;

import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.client.model.entity.SpiderlingModel;
import dev.galacticraft.mod.client.render.entity.feature.SpiderEmissiveLayer;
import dev.galacticraft.mod.client.render.entity.model.GCEntityModelLayer;
import dev.galacticraft.mod.content.entity.boss.SpiderlingEntity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

@Environment(EnvType.CLIENT)
public class SpiderlingRenderer extends MobRenderer<SpiderlingEntity, SpiderlingModel> {
    private static final ResourceLocation TEXTURE = Constant.id("textures/entity/boss/spiderling.png");

    public SpiderlingRenderer(EntityRendererProvider.Context context) {
        super(context, new SpiderlingModel(context.bakeLayer(GCEntityModelLayer.SPIDERLING)), 0.4F);
        this.addLayer(new SpiderEmissiveLayer<>(this, Constant.id("textures/entity/boss/spiderling_emissive.png")));
    }

    @Override
    public ResourceLocation getTextureLocation(SpiderlingEntity entity) {
        return TEXTURE;
    }
}
