package dev.galacticraft.mod.content.item.jupiter;

import dev.galacticraft.mod.content.GCEntityTypes;
import dev.galacticraft.mod.content.entity.vehicle.CloudDiverEntity;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.server.level.ServerLevel;

public final class CloudDiverItem extends Item {
    public CloudDiverItem() { super(new Properties().stacksTo(1)); }
    @Override public InteractionResult useOn(UseOnContext context) {
        if(!(context.getLevel() instanceof ServerLevel level))return InteractionResult.SUCCESS;
        var p=context.getClickedPos().relative(context.getClickedFace());
        CloudDiverEntity diver=new CloudDiverEntity(GCEntityTypes.CLOUD_DIVER,level);
        diver.setPos(p.getX()+0.5,p.getY(),p.getZ()+0.5);diver.setYRot(context.getHorizontalDirection().toYRot());
        if(!level.noCollision(diver,diver.getBoundingBox()) || !level.getEntities(diver,diver.getBoundingBox()).isEmpty())return InteractionResult.FAIL;
        if(context.getPlayer()!=null && context.getPlayer().isCreative()){diver.acceptEnergy(CloudDiverEntity.ENERGY_CAPACITY);diver.acceptOxygen(CloudDiverEntity.OXYGEN_CAPACITY);}
        if(!level.addFreshEntity(diver))return InteractionResult.FAIL;
        if(context.getPlayer()==null || !context.getPlayer().isCreative())context.getItemInHand().shrink(1);
        return InteractionResult.CONSUME;
    }
}