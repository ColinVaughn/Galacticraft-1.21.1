package dev.galacticraft.mod.client.render.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.client.model.CloudDiverAnimations;
import dev.galacticraft.mod.content.entity.vehicle.CloudDiverEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Geometry and UVs exported from art/blockbench/jupiter/cloud_diver.bbmodel; its clips play from cloud_diver.animation.json. */
public final class CloudDiverRenderer extends EntityRenderer<CloudDiverEntity> {
    private static final ResourceLocation TEXTURE=Constant.id("textures/entity/cloud_diver.png"),EMISSIVE=Constant.id("textures/entity/cloud_diver_emissive.png");
    private static final int FULL_BRIGHT=15728880,ALARM_RED=0xFFFF4A3A;
    private static final String[] SIDES={"port","starboard"};
    private final ModelPart model=createModel(),body=model.getChild("diver");
    private final CloudDiverAnimations animations;
    public CloudDiverRenderer(EntityRendererProvider.Context context){
        super(context);shadowRadius=1.5F;
        animations=new CloudDiverAnimations(Minecraft.getInstance().getResourceManager());
    }
    @Override public ResourceLocation getTextureLocation(CloudDiverEntity diver){return TEXTURE;}
    @Override public void render(CloudDiverEntity diver,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
        boolean powered=diver.energy()>0 && diver.hull()>0,alarm=diver.alarm();
        double dx=diver.getX()-diver.xo,dy=diver.getY()-diver.yo,dz=diver.getZ()-diver.zo;
        float thrust=powered?(float)Math.min(1,Math.sqrt(dx*dx+dy*dy+dz*dz)/0.3):0;
        animate(diver,partial,(diver.tickCount+partial)/20.0,powered,thrust,(float)dy,Mth.wrapDegrees(diver.getYRot()-diver.yRotO));
        pose.pushPose();pose.mulPose(Axis.YP.rotationDegrees(180-yaw));
        pose.scale(-1F,-1F,1F); // The mesh uses vanilla y-down model space.
        body.translateAndRotate(pose);
        var solid=buffers.getBuffer(RenderType.entityCutout(TEXTURE));
        for(String part:new String[]{"shell","trim","thrusters","interior","lamps","service"})body.getChild(part).render(pose,solid,light,OverlayTexture.NO_OVERLAY);
        body.getChild("canopy").render(pose,buffers.getBuffer(RenderType.entityTranslucentCull(TEXTURE)),light,OverlayTexture.NO_OVERLAY);
        if(powered){
            var glow=buffers.getBuffer(RenderType.eyes(EMISSIVE));
            for(String part:new String[]{"shell","trim","interior"})body.getChild(part).render(pose,glow,FULL_BRIGHT,OverlayTexture.NO_OVERLAY);
            float exhaust=0.35F+0.65F*thrust;
            body.getChild("thrusters").render(pose,glow,FULL_BRIGHT,OverlayTexture.NO_OVERLAY,0xFF000000|Mth.color(exhaust,exhaust,exhaust));
            ModelPart lamps=body.getChild("lamps");
            pose.pushPose();
            lamps.translateAndRotate(pose); // Apply the group offset before rendering its children individually.
            boolean strobe=diver.tickCount/3%2==0;
            if(!alarm || strobe)lamps.getChild("headlights").render(pose,glow,FULL_BRIGHT,OverlayTexture.NO_OVERLAY,alarm?ALARM_RED:-1);
            if(alarm?strobe:diver.tickCount%30<3)lamps.getChild("beacons").render(pose,glow,FULL_BRIGHT,OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
        float docking=diver.docking(partial);
        if(docking>0.6F) {
            // Charging sockets pulse green while a dock is connected, powered or not.
            float pulse=0.55F+0.45F*Mth.sin((diver.tickCount+partial)*0.25F);
            ModelPart service=body.getChild("service");
            pose.pushPose();
            service.translateAndRotate(pose);
            service.getChild("sockets").render(pose,buffers.getBuffer(RenderType.eyes(EMISSIVE)),FULL_BRIGHT,OverlayTexture.NO_OVERLAY,0xFF000000|Mth.color(pulse,pulse,pulse));
            pose.popPose();
        }
        pose.popPose();super.render(diver,yaw,partial,pose,buffers,light);
    }
    /**
     * Samples attitude and gimbal clips from the diver's climb and turn rates.
     */
    private void animate(CloudDiverEntity diver,float partial,double seconds,boolean powered,float thrust,float climb,float turn){
        model.getAllParts().forEach(part->{part.resetPose();part.xScale=part.yScale=part.zScale=1;});
        ModelPart trim=body.getChild("trim"),thrusters=body.getChild("thrusters");
        float active=powered?1:0;
        animations.apply(body,"hover","diver",seconds,powered && diver.getPassengers().isEmpty()?1:0);
        animations.apply(body,"lean","diver",(diver.lean(partial)+1)/2,1);
        animations.apply(body,"bank","diver",(diver.bank(partial)+1)/2,1);
        animations.apply(trim.getChild("scanner"),"scanner","scanner",seconds,active);
        for(String side:SIDES){
            ModelPart pod=thrusters.getChild(side+"_pod");
            animations.apply(pod,"pitch",side+"_pod",(Mth.clamp(climb/0.3F,-1,1)+1)/2,active);
            animations.apply(pod,"yaw",side+"_pod",(Mth.clamp(turn/2F,-1,1)+1)/2,active);
            animations.apply(pod.getChild(side+"_glow"),"exhaust",side+"_glow",seconds,thrust);
            // Hidden rather than scaled to zero: a zero scale leaves a singular pose that blanks the whole entity.
            ModelPart plume=pod.getChild(side+"_plume");
            plume.visible=powered && !diver.getPassengers().isEmpty();
            if(!plume.visible)continue;
            animations.apply(plume,"plume",side+"_plume",0.15+0.85*thrust,1);
            animations.apply(plume,"exhaust",side+"_plume",seconds,thrust);
        }
        float docking=diver.docking(partial);
        if(docking>0) {
            double time=docking*animations.length("dock");
            ModelPart service=body.getChild("service");
            for(String side:SIDES) {
                animations.apply(service.getChild(side+"_hatch"),"dock",side+"_hatch",time,1);
                animations.apply(thrusters.getChild(side+"_pod"),"dock",side+"_pod",time,1);
            }
        }
        float progress=diver.collectProgress(partial);
        if(progress<0)return;
        double time=progress*animations.length("collect");
        ModelPart arm=trim.getChild("arm"),forearm=arm.getChild("forearm"),claw=forearm.getChild("claw");
        animations.apply(arm,"collect","arm",time,1);
        animations.apply(forearm,"collect","forearm",time,1);
        animations.apply(claw.getChild("jaw_upper"),"collect","jaw_upper",time,1);
        animations.apply(claw.getChild("jaw_lower"),"collect","jaw_lower",time,1);
    }
    private static ModelPart createModel(){
        MeshDefinition mesh=new MeshDefinition();PartDefinition root=mesh.getRoot();
        PartDefinition diver=root.addOrReplaceChild("diver",CubeListBuilder.create(),PartPose.offset(0F,-22F,0F));
        PartDefinition diverShell=diver.addOrReplaceChild("shell",CubeListBuilder.create(),PartPose.offset(0F,22F,0F));
        diverShell.addOrReplaceChild("crown",CubeListBuilder.create().texOffs(0,49).addBox(-14F,-45F,-21F,28F,4F,43F,new CubeDeformation(-0.12F)),PartPose.ZERO);
        diverShell.addOrReplaceChild("keel",CubeListBuilder.create().texOffs(0,0).addBox(-14F,-7F,-22F,28F,4F,44F,new CubeDeformation(-0.04F)),PartPose.ZERO);
        diverShell.addOrReplaceChild("port_side_lower",CubeListBuilder.create().texOffs(113,146).addBox(18F,-26F,-22F,4F,15F,44F,new CubeDeformation(-0.04F)),PartPose.ZERO);
        diverShell.addOrReplaceChild("starboard_side_lower",CubeListBuilder.create().texOffs(0,195).addBox(-22F,-26F,-22F,4F,15F,44F,new CubeDeformation(-0.04F)),PartPose.ZERO);
        diverShell.addOrReplaceChild("port_side_upper",CubeListBuilder.create().texOffs(97,206).addBox(18F,-37F,-22F,4F,5F,44F,new CubeDeformation(-0.04F)),PartPose.ZERO);
        diverShell.addOrReplaceChild("starboard_side_upper",CubeListBuilder.create().texOffs(194,206).addBox(-22F,-37F,-22F,4F,5F,44F,new CubeDeformation(-0.04F)),PartPose.ZERO);
        diverShell.addOrReplaceChild("port_side_port_fwd",CubeListBuilder.create().texOffs(293,101).addBox(18F,-32F,-22F,4F,6F,14F,new CubeDeformation(-0.04F)),PartPose.ZERO);
        diverShell.addOrReplaceChild("starboard_side_port_fwd",CubeListBuilder.create().texOffs(110,295).addBox(-22F,-32F,-22F,4F,6F,14F,new CubeDeformation(-0.04F)),PartPose.ZERO);
        diverShell.addOrReplaceChild("port_side_port_aft",CubeListBuilder.create().texOffs(69,256).addBox(18F,-32F,-2F,4F,6F,24F,new CubeDeformation(-0.04F)),PartPose.ZERO);
        diverShell.addOrReplaceChild("starboard_side_port_aft",CubeListBuilder.create().texOffs(126,256).addBox(-22F,-32F,-2F,4F,6F,24F,new CubeDeformation(-0.04F)),PartPose.ZERO);
        diverShell.addOrReplaceChild("port_shoulder",CubeListBuilder.create().texOffs(0,97).addBox(-6.157F,-2F,-22F,12.314F,4F,44F,new CubeDeformation(-0.08F)),PartPose.offsetAndRotation(16.586F,-39.586F,0F,0.0F,0.0F,0.7853982F));
        diverShell.addOrReplaceChild("starboard_shoulder",CubeListBuilder.create().texOffs(113,97).addBox(-6.157F,-2F,-22F,12.314F,4F,44F,new CubeDeformation(-0.08F)),PartPose.offsetAndRotation(-16.586F,-39.586F,0F,0.0F,0.0F,-0.7853982F));
        diverShell.addOrReplaceChild("port_bilge",CubeListBuilder.create().texOffs(145,0).addBox(-6.157F,-2F,-22F,12.314F,4F,44F),PartPose.offsetAndRotation(16.586F,-8.414F,0F,0.0F,0.0F,-0.7853982F));
        diverShell.addOrReplaceChild("starboard_bilge",CubeListBuilder.create().texOffs(0,146).addBox(-6.157F,-2F,-22F,12.314F,4F,44F),PartPose.offsetAndRotation(-16.586F,-8.414F,0F,0.0F,0.0F,0.7853982F));
        diverShell.addOrReplaceChild("rear_cap_center",CubeListBuilder.create().texOffs(210,146).addBox(-14F,-45F,22F,28F,42F,3F,new CubeDeformation(-0.04F)),PartPose.ZERO);
        diverShell.addOrReplaceChild("port_rear_cap_side",CubeListBuilder.create().texOffs(147,295).addBox(14F,-37F,22F,8F,26F,3F,new CubeDeformation(-0.04F)),PartPose.ZERO);
        diverShell.addOrReplaceChild("starboard_rear_cap_side",CubeListBuilder.create().texOffs(170,306).addBox(-22F,-37F,22F,8F,26F,3F,new CubeDeformation(-0.04F)),PartPose.ZERO);
        diverShell.addOrReplaceChild("port_rear_cap_upper_corner",CubeListBuilder.create().texOffs(328,151).addBox(-5.657F,-5.657F,22F,11.314F,11.314F,3F),PartPose.offsetAndRotation(14F,-37F,0F,0.0F,0.0F,0.7853982F));
        diverShell.addOrReplaceChild("starboard_rear_cap_upper_corner",CubeListBuilder.create().texOffs(330,101).addBox(-5.657F,-5.657F,22F,11.314F,11.314F,3F),PartPose.offsetAndRotation(-14F,-37F,0F,0.0F,0.0F,-0.7853982F));
        diverShell.addOrReplaceChild("port_rear_cap_lower_corner",CubeListBuilder.create().texOffs(193,325).addBox(-5.657F,-5.657F,22F,11.314F,11.314F,3F),PartPose.offsetAndRotation(14F,-11F,0F,0.0F,0.0F,0.7853982F));
        diverShell.addOrReplaceChild("starboard_rear_cap_lower_corner",CubeListBuilder.create().texOffs(71,327).addBox(-5.657F,-5.657F,22F,11.314F,11.314F,3F),PartPose.offsetAndRotation(-14F,-11F,0F,0.0F,0.0F,-0.7853982F));
        diverShell.addOrReplaceChild("hatch",CubeListBuilder.create().texOffs(71,309).addBox(-7F,-32F,25F,14F,16F,1F),PartPose.ZERO);
        diverShell.addOrReplaceChild("cargo_locker",CubeListBuilder.create().texOffs(293,88).addBox(-11F,-12F,25F,22F,8F,4F),PartPose.ZERO);
        diverShell.addOrReplaceChild("chin_core",CubeListBuilder.create().texOffs(143,49).addBox(-18F,-21F,-30F,36F,10F,16F,new CubeDeformation(-0.04F)),PartPose.ZERO);
        diverShell.addOrReplaceChild("chin_belly",CubeListBuilder.create().texOffs(291,200).addBox(-10F,-11F,-30F,20F,8F,8F,new CubeDeformation(-0.04F)),PartPose.ZERO);
        diverShell.addOrReplaceChild("port_chin_bilge",CubeListBuilder.create().texOffs(301,245).addBox(-6.157F,-2.5F,-30F,12.314F,5F,8F),PartPose.offsetAndRotation(12.232F,-8.768F,0F,0.0F,0.0F,-0.7853982F));
        diverShell.addOrReplaceChild("starboard_chin_bilge",CubeListBuilder.create().texOffs(301,259).addBox(-6.157F,-2.5F,-30F,12.314F,5F,8F),PartPose.offsetAndRotation(-12.232F,-8.768F,0F,0.0F,0.0F,0.7853982F));
        diverShell.addOrReplaceChild("chin_glacis",CubeListBuilder.create().texOffs(143,76).addBox(-18F,-1F,-4.9F,36F,2F,9.8F),PartPose.offsetAndRotation(0F,-19.5F,-26.5F,0.418879F,0.0F,0.0F));
        diverShell.addOrReplaceChild("viewport_head",CubeListBuilder.create().texOffs(210,192).addBox(-18F,-41F,-15F,36F,2F,4F),PartPose.ZERO);
        diverShell.addOrReplaceChild("viewport_sill",CubeListBuilder.create().texOffs(143,89).addBox(-18F,-22F,-15F,36F,3F,4F),PartPose.ZERO);
        diverShell.addOrReplaceChild("port_viewport_jamb",CubeListBuilder.create().texOffs(79,342).addBox(15F,-39F,-15F,3F,17F,4F),PartPose.ZERO);
        diverShell.addOrReplaceChild("starboard_viewport_jamb",CubeListBuilder.create().texOffs(13,347).addBox(-18F,-39F,-15F,3F,17F,4F),PartPose.ZERO);
        PartDefinition diverTrim=diver.addOrReplaceChild("trim",CubeListBuilder.create(),PartPose.offset(0F,22F,0F));
        diverTrim.addOrReplaceChild("bow_ring_top",CubeListBuilder.create().texOffs(273,174).addBox(-14.6F,-46.5F,-25.5F,29.2F,3.5F,4.5F,new CubeDeformation(-0.12F)),PartPose.ZERO);
        diverTrim.addOrReplaceChild("bow_ring_bottom",CubeListBuilder.create().texOffs(238,283).addBox(-14.6F,-5F,-25.5F,29.2F,3.5F,4.5F,new CubeDeformation(-0.04F)),PartPose.ZERO);
        diverTrim.addOrReplaceChild("port_bow_ring_side",CubeListBuilder.create().texOffs(33,307).addBox(20F,-37.6F,-25.5F,3.5F,27.2F,4.5F,new CubeDeformation(-0.08F)),PartPose.ZERO);
        diverTrim.addOrReplaceChild("starboard_bow_ring_side",CubeListBuilder.create().texOffs(52,309).addBox(-23.5F,-37.6F,-25.5F,3.5F,27.2F,4.5F,new CubeDeformation(-0.08F)),PartPose.ZERO);
        diverTrim.addOrReplaceChild("port_bow_ring_shoulder",CubeListBuilder.create().texOffs(320,300).addBox(-6.694F,-1.75F,-25.5F,13.387F,3.5F,4.5F,new CubeDeformation(-0.04F)),PartPose.offsetAndRotation(17.813F,-40.813F,0F,0.0F,0.0F,0.7853982F));
        diverTrim.addOrReplaceChild("starboard_bow_ring_shoulder",CubeListBuilder.create().texOffs(321,27).addBox(-6.693F,-1.75F,-25.5F,13.387F,3.5F,4.5F,new CubeDeformation(-0.04F)),PartPose.offsetAndRotation(-17.813F,-40.813F,0F,0.0F,0.0F,-0.7853982F));
        diverTrim.addOrReplaceChild("port_bow_ring_bilge",CubeListBuilder.create().texOffs(321,37).addBox(-6.694F,-1.75F,-25.5F,13.387F,3.5F,4.5F),PartPose.offsetAndRotation(17.813F,-7.187F,0F,0.0F,0.0F,-0.7853982F));
        diverTrim.addOrReplaceChild("starboard_bow_ring_bilge",CubeListBuilder.create().texOffs(324,310).addBox(-6.693F,-1.75F,-25.5F,13.387F,3.5F,4.5F),PartPose.offsetAndRotation(-17.813F,-7.187F,0F,0.0F,0.0F,0.7853982F));
        diverTrim.addOrReplaceChild("mid_ring_top",CubeListBuilder.create().texOffs(291,217).addBox(-14.6F,-46.5F,0F,29.2F,2.5F,3F,new CubeDeformation(-0.04F)),PartPose.ZERO);
        diverTrim.addOrReplaceChild("mid_ring_bottom",CubeListBuilder.create().texOffs(291,224).addBox(-14.6F,-4F,0F,29.2F,2.5F,3F,new CubeDeformation(-0.04F)),PartPose.ZERO);
        diverTrim.addOrReplaceChild("port_mid_ring_side",CubeListBuilder.create().texOffs(249,331).addBox(21F,-37.6F,0F,2.5F,27.2F,3F,new CubeDeformation(-0.04F)),PartPose.ZERO);
        diverTrim.addOrReplaceChild("starboard_mid_ring_side",CubeListBuilder.create().texOffs(293,333).addBox(-23.5F,-37.6F,0F,2.5F,27.2F,3F,new CubeDeformation(-0.04F)),PartPose.ZERO);
        diverTrim.addOrReplaceChild("port_mid_ring_shoulder",CubeListBuilder.create().texOffs(332,132).addBox(-6.693F,-1.25F,0F,13.386F,2.5F,3F),PartPose.offsetAndRotation(18.166F,-41.166F,0F,0.0F,0.0F,0.7853982F));
        diverTrim.addOrReplaceChild("starboard_mid_ring_shoulder",CubeListBuilder.create().texOffs(332,139).addBox(-6.693F,-1.25F,0F,13.386F,2.5F,3F),PartPose.offsetAndRotation(-18.166F,-41.166F,0F,0.0F,0.0F,-0.7853982F));
        diverTrim.addOrReplaceChild("port_mid_ring_bilge",CubeListBuilder.create().texOffs(13,340).addBox(-6.693F,-1.25F,0F,13.386F,2.5F,3F),PartPose.offsetAndRotation(18.166F,-6.834F,0F,0.0F,0.0F,-0.7853982F));
        diverTrim.addOrReplaceChild("starboard_mid_ring_bilge",CubeListBuilder.create().texOffs(185,340).addBox(-6.693F,-1.25F,0F,13.386F,2.5F,3F),PartPose.offsetAndRotation(-18.166F,-6.834F,0F,0.0F,0.0F,0.7853982F));
        diverTrim.addOrReplaceChild("aft_ring_top",CubeListBuilder.create().texOffs(291,231).addBox(-14.6F,-46.5F,11F,29.2F,2.5F,3F,new CubeDeformation(-0.04F)),PartPose.ZERO);
        diverTrim.addOrReplaceChild("aft_ring_bottom",CubeListBuilder.create().texOffs(291,238).addBox(-14.6F,-4F,11F,29.2F,2.5F,3F,new CubeDeformation(-0.04F)),PartPose.ZERO);
        diverTrim.addOrReplaceChild("port_aft_ring_side",CubeListBuilder.create().texOffs(306,333).addBox(21F,-37.6F,11F,2.5F,27.2F,3F,new CubeDeformation(-0.04F)),PartPose.ZERO);
        diverTrim.addOrReplaceChild("starboard_aft_ring_side",CubeListBuilder.create().texOffs(0,336).addBox(-23.5F,-37.6F,11F,2.5F,27.2F,3F,new CubeDeformation(-0.04F)),PartPose.ZERO);
        diverTrim.addOrReplaceChild("port_aft_ring_shoulder",CubeListBuilder.create().texOffs(340,273).addBox(-6.693F,-1.25F,11F,13.386F,2.5F,3F),PartPose.offsetAndRotation(18.166F,-41.166F,0F,0.0F,0.0F,0.7853982F));
        diverTrim.addOrReplaceChild("starboard_aft_ring_shoulder",CubeListBuilder.create().texOffs(340,280).addBox(-6.693F,-1.25F,11F,13.386F,2.5F,3F),PartPose.offsetAndRotation(-18.166F,-41.166F,0F,0.0F,0.0F,-0.7853982F));
        diverTrim.addOrReplaceChild("port_aft_ring_bilge",CubeListBuilder.create().texOffs(46,342).addBox(-6.693F,-1.25F,11F,13.386F,2.5F,3F),PartPose.offsetAndRotation(18.166F,-6.834F,0F,0.0F,0.0F,-0.7853982F));
        diverTrim.addOrReplaceChild("starboard_aft_ring_bilge",CubeListBuilder.create().texOffs(342,174).addBox(-6.693F,-1.25F,11F,13.386F,2.5F,3F),PartPose.offsetAndRotation(-18.166F,-6.834F,0F,0.0F,0.0F,0.7853982F));
        diverTrim.addOrReplaceChild("stern_ring_top",CubeListBuilder.create().texOffs(110,287).addBox(-14.6F,-46.5F,21.5F,29.2F,3F,4F,new CubeDeformation(-0.04F)),PartPose.ZERO);
        diverTrim.addOrReplaceChild("stern_ring_bottom",CubeListBuilder.create().texOffs(291,192).addBox(-14.6F,-4.5F,21.5F,29.2F,3F,4F,new CubeDeformation(-0.04F)),PartPose.ZERO);
        diverTrim.addOrReplaceChild("port_stern_ring_side",CubeListBuilder.create().texOffs(263,317).addBox(20.5F,-37.6F,21.5F,3F,27.2F,4F,new CubeDeformation(-0.04F)),PartPose.ZERO);
        diverTrim.addOrReplaceChild("starboard_stern_ring_side",CubeListBuilder.create().texOffs(278,317).addBox(-23.5F,-37.6F,21.5F,3F,27.2F,4F,new CubeDeformation(-0.04F)),PartPose.ZERO);
        diverTrim.addOrReplaceChild("port_stern_ring_shoulder",CubeListBuilder.create().texOffs(328,166).addBox(-6.693F,-1.5F,21.5F,13.387F,3F,4F),PartPose.offsetAndRotation(17.989F,-40.989F,0F,0.0F,0.0F,0.7853982F));
        diverTrim.addOrReplaceChild("starboard_stern_ring_shoulder",CubeListBuilder.create().texOffs(330,116).addBox(-6.694F,-1.5F,21.5F,13.387F,3F,4F),PartPose.offsetAndRotation(-17.989F,-40.989F,0F,0.0F,0.0F,-0.7853982F));
        diverTrim.addOrReplaceChild("port_stern_ring_bilge",CubeListBuilder.create().texOffs(324,331).addBox(-6.693F,-1.5F,21.5F,13.387F,3F,4F),PartPose.offsetAndRotation(17.989F,-7.011F,0F,0.0F,0.0F,-0.7853982F));
        diverTrim.addOrReplaceChild("starboard_stern_ring_bilge",CubeListBuilder.create().texOffs(332,124).addBox(-6.694F,-1.5F,21.5F,13.387F,3F,4F),PartPose.offsetAndRotation(-17.989F,-7.011F,0F,0.0F,0.0F,0.7853982F));
        diverTrim.addOrReplaceChild("visor_brow",CubeListBuilder.create().texOffs(273,184).addBox(-14.6F,-44F,-25.5F,29.2F,2F,4.5F,new CubeDeformation(-0.08F)),PartPose.ZERO);
        diverTrim.addOrReplaceChild("viewport_bezel_top",CubeListBuilder.create().texOffs(210,199).addBox(-17F,-41F,-16F,34F,2F,1F),PartPose.ZERO);
        diverTrim.addOrReplaceChild("viewport_bezel_bottom",CubeListBuilder.create().texOffs(236,84).addBox(-17F,-22F,-16F,34F,2F,1F),PartPose.ZERO);
        diverTrim.addOrReplaceChild("port_viewport_bezel_side",CubeListBuilder.create().texOffs(68,349).addBox(15F,-39F,-16F,2F,17F,1F),PartPose.ZERO);
        diverTrim.addOrReplaceChild("starboard_viewport_bezel_side",CubeListBuilder.create().texOffs(281,349).addBox(-17F,-39F,-16F,2F,17F,1F),PartPose.ZERO);
        diverTrim.addOrReplaceChild("port_porthole_bezel_top",CubeListBuilder.create().texOffs(348,200).addBox(21.6F,-33.5F,-9.5F,1.2F,1.5F,9F),PartPose.ZERO);
        diverTrim.addOrReplaceChild("starboard_porthole_bezel_top",CubeListBuilder.create().texOffs(206,348).addBox(-22.8F,-33.5F,-9.5F,1.2F,1.5F,9F),PartPose.ZERO);
        diverTrim.addOrReplaceChild("port_porthole_bezel_bottom",CubeListBuilder.create().texOffs(227,348).addBox(21.6F,-26F,-9.5F,1.2F,1.5F,9F),PartPose.ZERO);
        diverTrim.addOrReplaceChild("starboard_porthole_bezel_bottom",CubeListBuilder.create().texOffs(28,349).addBox(-22.8F,-26F,-9.5F,1.2F,1.5F,9F),PartPose.ZERO);
        diverTrim.addOrReplaceChild("port_porthole_bezel_fwd",CubeListBuilder.create().texOffs(137,316).addBox(21.6F,-32F,-9.5F,1.2F,6F,1.5F),PartPose.ZERO);
        diverTrim.addOrReplaceChild("starboard_porthole_bezel_fwd",CubeListBuilder.create().texOffs(162,325).addBox(-22.8F,-32F,-9.5F,1.2F,6F,1.5F),PartPose.ZERO);
        diverTrim.addOrReplaceChild("port_porthole_bezel_aft",CubeListBuilder.create().texOffs(178,356).addBox(21.6F,-32F,-2F,1.2F,6F,1.5F),PartPose.ZERO);
        diverTrim.addOrReplaceChild("starboard_porthole_bezel_aft",CubeListBuilder.create().texOffs(358,188).addBox(-22.8F,-32F,-2F,1.2F,6F,1.5F),PartPose.ZERO);
        diverTrim.addOrReplaceChild("hatch_rim_top",CubeListBuilder.create().texOffs(320,290).addBox(-9F,-34F,25F,18F,2F,2F),PartPose.ZERO);
        diverTrim.addOrReplaceChild("hatch_rim_bottom",CubeListBuilder.create().texOffs(320,295).addBox(-9F,-16F,25F,18F,2F,2F),PartPose.ZERO);
        diverTrim.addOrReplaceChild("port_hatch_rim_side",CubeListBuilder.create().texOffs(150,345).addBox(7F,-32F,25F,2F,16F,2F),PartPose.ZERO);
        diverTrim.addOrReplaceChild("starboard_hatch_rim_side",CubeListBuilder.create().texOffs(115,348).addBox(-9F,-32F,25F,2F,16F,2F),PartPose.ZERO);
        diverTrim.addOrReplaceChild("hatch_wheel",CubeListBuilder.create().texOffs(358,27).addBox(-3F,-26.5F,26F,6F,5F,1F),PartPose.ZERO);
        diverTrim.addOrReplaceChild("cargo_latch",CubeListBuilder.create().texOffs(13,336).addBox(-3F,-9F,29F,6F,2F,1F),PartPose.ZERO);
        diverTrim.addOrReplaceChild("port_chin_skid",CubeListBuilder.create().texOffs(315,46).addBox(6F,-3F,-28F,7F,3F,10F),PartPose.ZERO);
        diverTrim.addOrReplaceChild("starboard_chin_skid",CubeListBuilder.create().texOffs(315,60).addBox(-13F,-3F,-28F,7F,3F,10F),PartPose.ZERO);
        diverTrim.addOrReplaceChild("port_pod_pylon",CubeListBuilder.create().texOffs(238,293).addBox(21.5F,-18F,-1F,2.5F,6F,17F),PartPose.ZERO);
        diverTrim.addOrReplaceChild("starboard_pod_pylon",CubeListBuilder.create().texOffs(279,293).addBox(-24F,-18F,-1F,2.5F,6F,17F),PartPose.ZERO);
        diverTrim.addOrReplaceChild("port_upper_fin_root",CubeListBuilder.create().texOffs(273,151).addBox(-1F,-0.75F,8F,7F,1.5F,20F,new CubeDeformation(-0.04F)),PartPose.offsetAndRotation(18F,-41F,0F,0.0F,0.0F,-0.7853982F));
        diverTrim.addOrReplaceChild("starboard_upper_fin_root",CubeListBuilder.create().texOffs(183,283).addBox(-6F,-0.75F,8F,7F,1.5F,20F,new CubeDeformation(-0.04F)),PartPose.offsetAndRotation(-18F,-41F,0F,0.0F,0.0F,0.7853982F));
        diverTrim.addOrReplaceChild("port_upper_fin_tip",CubeListBuilder.create().texOffs(102,316).addBox(6F,-0.75F,16F,4.5F,1.5F,12F),PartPose.offsetAndRotation(18F,-41F,0F,0.0F,0.0F,-0.7853982F));
        diverTrim.addOrReplaceChild("starboard_upper_fin_tip",CubeListBuilder.create().texOffs(317,0).addBox(-10.5F,-0.75F,16F,4.5F,1.5F,12F),PartPose.offsetAndRotation(-18F,-41F,0F,0.0F,0.0F,0.7853982F));
        diverTrim.addOrReplaceChild("port_grounding_rod",CubeListBuilder.create().texOffs(0,307).addBox(10.5F,-0.5F,16F,1F,1F,15F),PartPose.offsetAndRotation(18F,-41F,0F,0.0F,0.0F,-0.7853982F));
        diverTrim.addOrReplaceChild("starboard_grounding_rod",CubeListBuilder.create().texOffs(307,273).addBox(-11.5F,-0.5F,16F,1F,1F,15F),PartPose.offsetAndRotation(-18F,-41F,0F,0.0F,0.0F,0.7853982F));
        diverTrim.addOrReplaceChild("port_lower_fin",CubeListBuilder.create().texOffs(0,285).addBox(-1F,-0.75F,8F,8F,1.5F,19F,new CubeDeformation(-0.04F)),PartPose.offsetAndRotation(18F,-7F,0F,0.0F,0.0F,0.7853982F));
        diverTrim.addOrReplaceChild("starboard_lower_fin",CubeListBuilder.create().texOffs(55,287).addBox(-7F,-0.75F,8F,8F,1.5F,19F,new CubeDeformation(-0.04F)),PartPose.offsetAndRotation(-18F,-7F,0F,0.0F,0.0F,-0.7853982F));
        diverTrim.addOrReplaceChild("port_landing_pad",CubeListBuilder.create().texOffs(315,74).addBox(20F,-1.5F,17F,6F,1.5F,11F),PartPose.ZERO);
        diverTrim.addOrReplaceChild("starboard_landing_pad",CubeListBuilder.create().texOffs(228,317).addBox(-26F,-1.5F,17F,6F,1.5F,11F),PartPose.ZERO);
        diverTrim.addOrReplaceChild("scanner_mast",CubeListBuilder.create().texOffs(357,300).addBox(-1.5F,-48F,-13F,3F,3F,4F),PartPose.ZERO);
        diverTrim.addOrReplaceChild("dorsal_spine_5",CubeListBuilder.create().texOffs(267,124).addBox(-0.5F,-5F,-0.5F,1F,5F,1F),PartPose.offsetAndRotation(0F,-45F,5F,-0.4363323F,0.0F,0.0F));
        diverTrim.addOrReplaceChild("dorsal_spine_8",CubeListBuilder.create().texOffs(267,131).addBox(-0.5F,-5F,-0.5F,1F,5F,1F),PartPose.offsetAndRotation(0F,-45F,8F,-0.4363323F,0.0F,0.0F));
        diverTrim.addOrReplaceChild("dorsal_spine_17",CubeListBuilder.create().texOffs(267,138).addBox(-0.5F,-5F,-0.5F,1F,5F,1F),PartPose.offsetAndRotation(0F,-45F,17F,-0.4363323F,0.0F,0.0F));
        diverTrim.addOrReplaceChild("dorsal_spine_20",CubeListBuilder.create().texOffs(177,287).addBox(-0.5F,-5F,-0.5F,1F,5F,1F),PartPose.offsetAndRotation(0F,-45F,20F,-0.4363323F,0.0F,0.0F));
        diverTrim.addOrReplaceChild("dorsal_spine_base",CubeListBuilder.create().texOffs(226,124).addBox(-1.5F,-46F,4F,3F,1F,17F),PartPose.ZERO);
        diverTrim.addOrReplaceChild("sample_basket",CubeListBuilder.create().texOffs(193,306).addBox(-26F,-10F,-26F,4.5F,6F,12F),PartPose.ZERO);
        diverTrim.addOrReplaceChild("port_headlight_bezel",CubeListBuilder.create().texOffs(356,217).addBox(8F,-18F,-31F,7F,6F,1F),PartPose.ZERO);
        diverTrim.addOrReplaceChild("starboard_headlight_bezel",CubeListBuilder.create().texOffs(356,225).addBox(-15F,-18F,-31F,7F,6F,1F),PartPose.ZERO);
        diverTrim.addOrReplaceChild("port_floodlight_housing",CubeListBuilder.create().texOffs(342,353).addBox(10F,-49F,-25F,5F,2.5F,4F),PartPose.ZERO);
        diverTrim.addOrReplaceChild("starboard_floodlight_housing",CubeListBuilder.create().texOffs(354,10).addBox(-15F,-49F,-25F,5F,2.5F,4F),PartPose.ZERO);
        PartDefinition diverTrimScanner=diverTrim.addOrReplaceChild("scanner",CubeListBuilder.create(),PartPose.offset(0F,-45F,-11F));
        diverTrimScanner.addOrReplaceChild("scanner_dish",CubeListBuilder.create().texOffs(317,15).addBox(-4.5F,-0.75F,-4.5F,9F,1.5F,9F),PartPose.offsetAndRotation(0F,-3.4F,0F,-0.4363323F,0.0F,0.0F));
        diverTrimScanner.addOrReplaceChild("scanner_horn",CubeListBuilder.create().texOffs(281,199).addBox(-0.5F,-1.5F,-3.5F,1F,1F,2.5F),PartPose.offsetAndRotation(0F,-3.4F,0F,-0.4363323F,0.0F,0.0F));
        diverTrimScanner.addOrReplaceChild("scanner_indicator",CubeListBuilder.create().texOffs(359,105).addBox(-1F,-1.25F,-1F,2F,0.5F,2F),PartPose.offsetAndRotation(0F,-3.4F,0F,-0.4363323F,0.0F,0.0F));
        PartDefinition diverTrimArm=diverTrim.addOrReplaceChild("arm",CubeListBuilder.create(),PartPose.offset(23.5F,-7F,-12F));
        diverTrimArm.addOrReplaceChild("arm_shoulder",CubeListBuilder.create().texOffs(346,88).addBox(-2F,-3F,-3F,4F,6F,6F),PartPose.ZERO);
        diverTrimArm.addOrReplaceChild("arm_upper",CubeListBuilder.create().texOffs(293,317).addBox(-1.5F,-1.5F,-13F,3F,3F,12F,new CubeDeformation(-0.04F)),PartPose.ZERO);
        PartDefinition diverTrimArmForearm=diverTrimArm.addOrReplaceChild("forearm",CubeListBuilder.create(),PartPose.offset(0F,0F,-14F));
        diverTrimArmForearm.addOrReplaceChild("arm_elbow",CubeListBuilder.create().texOffs(352,0).addBox(-2F,-2.5F,-2F,4F,5F,4F),PartPose.ZERO);
        diverTrimArmForearm.addOrReplaceChild("arm_forearm",CubeListBuilder.create().texOffs(342,181).addBox(-10.5F,-1.5F,-1.5F,10.5F,3F,3F),PartPose.offsetAndRotation(0F,0F,0F,0.0F,-0.4363323F,0.0F));
        PartDefinition diverTrimArmForearmClaw=diverTrimArmForearm.addOrReplaceChild("claw",CubeListBuilder.create(),PartPose.offset(-9F,0F,-4F));
        diverTrimArmForearmClaw.addOrReplaceChild("arm_wrist",CubeListBuilder.create().texOffs(357,319).addBox(-2F,-2.5F,-2F,4F,5F,2.5F),PartPose.ZERO);
        PartDefinition diverTrimArmForearmClawJawUpper=diverTrimArmForearmClaw.addOrReplaceChild("jaw_upper",CubeListBuilder.create(),PartPose.offset(0F,-2.5F,0F));
        diverTrimArmForearmClawJawUpper.addOrReplaceChild("arm_jaw_upper",CubeListBuilder.create().texOffs(236,76).addBox(-1.5F,-1.5F,-2F,3F,1.5F,2F),PartPose.ZERO);
        PartDefinition diverTrimArmForearmClawJawLower=diverTrimArmForearmClaw.addOrReplaceChild("jaw_lower",CubeListBuilder.create(),PartPose.offset(0F,2.5F,0F));
        diverTrimArmForearmClawJawLower.addOrReplaceChild("arm_jaw_lower",CubeListBuilder.create().texOffs(358,40).addBox(-1.5F,0F,-2F,3F,1.5F,2F),PartPose.ZERO);
        PartDefinition diverThrusters=diver.addOrReplaceChild("thrusters",CubeListBuilder.create(),PartPose.offset(0F,22F,0F));
        PartDefinition diverThrustersPortPod=diverThrusters.addOrReplaceChild("port_pod",CubeListBuilder.create(),PartPose.offset(27.5F,-15F,8F));
        diverThrustersPortPod.addOrReplaceChild("port_pod_intake",CubeListBuilder.create().texOffs(100,331).addBox(-4.5F,-6F,-17F,9F,12F,4F),PartPose.ZERO);
        diverThrustersPortPod.addOrReplaceChild("port_pod_core",CubeListBuilder.create().texOffs(226,89).addBox(-3.5F,-4F,-13F,7F,8F,26F),PartPose.ZERO);
        diverThrustersPortPod.addOrReplaceChild("port_pod_spine_top",CubeListBuilder.create().texOffs(183,256).addBox(-2.5F,-5.5F,-13F,5F,1.5F,24F),PartPose.ZERO);
        diverThrustersPortPod.addOrReplaceChild("port_pod_spine_bottom",CubeListBuilder.create().texOffs(258,0).addBox(-2.5F,4F,-13F,5F,1.5F,24F),PartPose.ZERO);
        diverThrustersPortPod.addOrReplaceChild("port_bell_throat",CubeListBuilder.create().texOffs(97,195).addBox(-2.5F,-3F,13F,5F,6F,2F),PartPose.ZERO);
        diverThrustersPortPod.addOrReplaceChild("port_bell_mid",CubeListBuilder.create().texOffs(185,347).addBox(-3.5F,-4F,15F,7F,8F,3F),PartPose.ZERO);
        diverThrustersPortPod.addOrReplaceChild("port_bell_skirt",CubeListBuilder.create().texOffs(342,245).addBox(-4.5F,-5F,18F,9F,10F,3F),PartPose.ZERO);
        diverThrustersPortPod.addOrReplaceChild("port_pod_feed_collar",CubeListBuilder.create().texOffs(342,339).addBox(-4.1F,-4.8F,-3F,8.2F,9.6F,3F),PartPose.ZERO);
        diverThrustersPortPod.addOrReplaceChild("port_bell_lip_top",CubeListBuilder.create().texOffs(332,146).addBox(-4.5F,-5.5F,21F,9F,1.5F,2F),PartPose.ZERO);
        diverThrustersPortPod.addOrReplaceChild("port_bell_lip_bottom",CubeListBuilder.create().texOffs(350,72).addBox(-4.5F,4F,21F,9F,1.5F,2F),PartPose.ZERO);
        diverThrustersPortPod.addOrReplaceChild("port_bell_lip_outer",CubeListBuilder.create().texOffs(291,245).addBox(3F,-4F,21F,1.5F,8F,2F),PartPose.ZERO);
        diverThrustersPortPod.addOrReplaceChild("port_bell_lip_inner",CubeListBuilder.create().texOffs(228,306).addBox(-4.5F,-4F,21F,1.5F,8F,2F),PartPose.ZERO);
        PartDefinition diverThrustersPortPodPortGlow=diverThrustersPortPod.addOrReplaceChild("port_glow",CubeListBuilder.create(),PartPose.offset(0F,0F,21.3F));
        diverThrustersPortPodPortGlow.addOrReplaceChild("port_nozzle_glow",CubeListBuilder.create().texOffs(357,146).addBox(-3F,-4F,-0.3F,6F,8F,0.6F),PartPose.ZERO);
        PartDefinition diverThrustersPortPodPortPlume=diverThrustersPortPod.addOrReplaceChild("port_plume",CubeListBuilder.create(),PartPose.offset(0F,0F,22.5F));
        diverThrustersPortPodPortPlume.addOrReplaceChild("port_plume_core",CubeListBuilder.create().texOffs(384,0).addBox(-1F,-1F,0F,2F,2F,8F),PartPose.ZERO);
        diverThrustersPortPodPortPlume.addOrReplaceChild("port_plume_inner",CubeListBuilder.create().texOffs(384,12).addBox(-2.5F,-2.5F,0F,5F,5F,2.5F,new CubeDeformation(-0.04F)),PartPose.ZERO);
        diverThrustersPortPodPortPlume.addOrReplaceChild("port_plume_mid",CubeListBuilder.create().texOffs(384,22).addBox(-2F,-2F,2.5F,4F,4F,3F),PartPose.ZERO);
        diverThrustersPortPodPortPlume.addOrReplaceChild("port_plume_tail",CubeListBuilder.create().texOffs(384,31).addBox(-1.25F,-1.25F,5.5F,2.5F,2.5F,3.5F),PartPose.ZERO);
        PartDefinition diverThrustersStarboardPod=diverThrusters.addOrReplaceChild("starboard_pod",CubeListBuilder.create(),PartPose.offset(-27.5F,-15F,8F));
        diverThrustersStarboardPod.addOrReplaceChild("starboard_pod_intake",CubeListBuilder.create().texOffs(222,331).addBox(-4.5F,-6F,-17F,9F,12F,4F),PartPose.ZERO);
        diverThrustersStarboardPod.addOrReplaceChild("starboard_pod_core",CubeListBuilder.create().texOffs(248,49).addBox(-3.5F,-4F,-13F,7F,8F,26F),PartPose.ZERO);
        diverThrustersStarboardPod.addOrReplaceChild("starboard_pod_spine_top",CubeListBuilder.create().texOffs(242,256).addBox(-2.5F,-5.5F,-13F,5F,1.5F,24F),PartPose.ZERO);
        diverThrustersStarboardPod.addOrReplaceChild("starboard_pod_spine_bottom",CubeListBuilder.create().texOffs(273,124).addBox(-2.5F,4F,-13F,5F,1.5F,24F),PartPose.ZERO);
        diverThrustersStarboardPod.addOrReplaceChild("starboard_bell_throat",CubeListBuilder.create().texOffs(356,233).addBox(-2.5F,-3F,13F,5F,6F,2F),PartPose.ZERO);
        diverThrustersStarboardPod.addOrReplaceChild("starboard_bell_mid",CubeListBuilder.create().texOffs(94,348).addBox(-3.5F,-4F,15F,7F,8F,3F),PartPose.ZERO);
        diverThrustersStarboardPod.addOrReplaceChild("starboard_bell_skirt",CubeListBuilder.create().texOffs(342,259).addBox(-4.5F,-5F,18F,9F,10F,3F),PartPose.ZERO);
        diverThrustersStarboardPod.addOrReplaceChild("starboard_pod_feed_collar",CubeListBuilder.create().texOffs(127,345).addBox(-4.1F,-4.8F,-3F,8.2F,9.6F,3F),PartPose.ZERO);
        diverThrustersStarboardPod.addOrReplaceChild("starboard_bell_lip_top",CubeListBuilder.create().texOffs(348,212).addBox(-4.5F,-5.5F,21F,9F,1.5F,2F),PartPose.ZERO);
        diverThrustersStarboardPod.addOrReplaceChild("starboard_bell_lip_bottom",CubeListBuilder.create().texOffs(350,77).addBox(-4.5F,4F,21F,9F,1.5F,2F),PartPose.ZERO);
        diverThrustersStarboardPod.addOrReplaceChild("starboard_bell_lip_outer",CubeListBuilder.create().texOffs(170,295).addBox(-4.5F,-4F,21F,1.5F,8F,2F),PartPose.ZERO);
        diverThrustersStarboardPod.addOrReplaceChild("starboard_bell_lip_inner",CubeListBuilder.create().texOffs(127,331).addBox(3F,-4F,21F,1.5F,8F,2F),PartPose.ZERO);
        PartDefinition diverThrustersStarboardPodStarboardGlow=diverThrustersStarboardPod.addOrReplaceChild("starboard_glow",CubeListBuilder.create(),PartPose.offset(0F,0F,21.3F));
        diverThrustersStarboardPodStarboardGlow.addOrReplaceChild("starboard_nozzle_glow",CubeListBuilder.create().texOffs(357,156).addBox(-3F,-4F,-0.3F,6F,8F,0.6F),PartPose.ZERO);
        PartDefinition diverThrustersStarboardPodStarboardPlume=diverThrustersStarboardPod.addOrReplaceChild("starboard_plume",CubeListBuilder.create(),PartPose.offset(0F,0F,22.5F));
        diverThrustersStarboardPodStarboardPlume.addOrReplaceChild("starboard_plume_core",CubeListBuilder.create().texOffs(412,0).addBox(-1F,-1F,0F,2F,2F,8F),PartPose.ZERO);
        diverThrustersStarboardPodStarboardPlume.addOrReplaceChild("starboard_plume_inner",CubeListBuilder.create().texOffs(412,12).addBox(-2.5F,-2.5F,0F,5F,5F,2.5F,new CubeDeformation(-0.04F)),PartPose.ZERO);
        diverThrustersStarboardPodStarboardPlume.addOrReplaceChild("starboard_plume_mid",CubeListBuilder.create().texOffs(412,22).addBox(-2F,-2F,2.5F,4F,4F,3F),PartPose.ZERO);
        diverThrustersStarboardPodStarboardPlume.addOrReplaceChild("starboard_plume_tail",CubeListBuilder.create().texOffs(412,31).addBox(-1.25F,-1.25F,5.5F,2.5F,2.5F,3.5F),PartPose.ZERO);
        PartDefinition diverInterior=diver.addOrReplaceChild("interior",CubeListBuilder.create(),PartPose.offset(0F,22F,0F));
        diverInterior.addOrReplaceChild("port_seat_pedestal",CubeListBuilder.create().texOffs(49,349).addBox(6F,-13.5F,-3F,4F,6.5F,5F),PartPose.ZERO);
        diverInterior.addOrReplaceChild("starboard_seat_pedestal",CubeListBuilder.create().texOffs(262,349).addBox(-10F,-13.5F,-3F,4F,6.5F,5F),PartPose.ZERO);
        diverInterior.addOrReplaceChild("port_seat_pan",CubeListBuilder.create().texOffs(0,324).addBox(4F,-16.5F,-6F,8F,3F,8F),PartPose.ZERO);
        diverInterior.addOrReplaceChild("starboard_seat_pan",CubeListBuilder.create().texOffs(324,319).addBox(-12F,-16.5F,-6F,8F,3F,8F),PartPose.ZERO);
        diverInterior.addOrReplaceChild("port_seat_back",CubeListBuilder.create().texOffs(162,336).addBox(4F,-32F,2F,8F,15.5F,3F),PartPose.ZERO);
        diverInterior.addOrReplaceChild("starboard_seat_back",CubeListBuilder.create().texOffs(319,339).addBox(-12F,-32F,2F,8F,15.5F,3F),PartPose.ZERO);
        diverInterior.addOrReplaceChild("port_seat_headrest",CubeListBuilder.create().texOffs(354,18).addBox(5F,-37F,2.5F,6F,5F,2.5F),PartPose.ZERO);
        diverInterior.addOrReplaceChild("starboard_seat_headrest",CubeListBuilder.create().texOffs(159,356).addBox(-11F,-37F,2.5F,6F,5F,2.5F),PartPose.ZERO);
        diverInterior.addOrReplaceChild("control_column",CubeListBuilder.create().texOffs(137,325).addBox(-2.5F,-19F,-11F,5F,12F,7F),PartPose.ZERO);
        diverInterior.addOrReplaceChild("aft_bulkhead_rack",CubeListBuilder.create().texOffs(0,255).addBox(-14F,-30F,16F,28F,23F,6F,new CubeDeformation(-0.04F)),PartPose.ZERO);
        PartDefinition diverLamps=diver.addOrReplaceChild("lamps",CubeListBuilder.create(),PartPose.offset(0F,22F,0F));
        PartDefinition diverLampsBeacons=diverLamps.addOrReplaceChild("beacons",CubeListBuilder.create(),PartPose.ZERO);
        diverLampsBeacons.addOrReplaceChild("port_grounding_beacon",CubeListBuilder.create().texOffs(359,109).addBox(10F,-0.75F,30.5F,2F,1.5F,1.5F),PartPose.offsetAndRotation(18F,-41F,0F,0.0F,0.0F,-0.7853982F));
        diverLampsBeacons.addOrReplaceChild("starboard_grounding_beacon",CubeListBuilder.create().texOffs(124,359).addBox(-12F,-0.75F,30.5F,2F,1.5F,1.5F),PartPose.offsetAndRotation(-18F,-41F,0F,0.0F,0.0F,0.7853982F));
        PartDefinition diverLampsHeadlights=diverLamps.addOrReplaceChild("headlights",CubeListBuilder.create(),PartPose.ZERO);
        diverLampsHeadlights.addOrReplaceChild("port_headlight",CubeListBuilder.create().texOffs(350,82).addBox(9F,-17F,-31.5F,5F,4F,0.5F),PartPose.ZERO);
        diverLampsHeadlights.addOrReplaceChild("starboard_headlight",CubeListBuilder.create().texOffs(358,34).addBox(-14F,-17F,-31.5F,5F,4F,0.5F),PartPose.ZERO);
        diverLampsHeadlights.addOrReplaceChild("port_floodlight",CubeListBuilder.create().texOffs(342,188).addBox(10.5F,-48.5F,-25.5F,4F,1.5F,0.5F),PartPose.ZERO);
        diverLampsHeadlights.addOrReplaceChild("starboard_floodlight",CubeListBuilder.create().texOffs(359,101).addBox(-14.5F,-48.5F,-25.5F,4F,1.5F,0.5F),PartPose.ZERO);
        PartDefinition diverCanopy=diver.addOrReplaceChild("canopy",CubeListBuilder.create(),PartPose.offset(0F,22F,0F));
        diverCanopy.addOrReplaceChild("viewport_glass",CubeListBuilder.create().texOffs(258,27).addBox(-15F,-39F,-13.4F,30F,17F,0.8F),PartPose.ZERO);
        diverCanopy.addOrReplaceChild("port_porthole_glass",CubeListBuilder.create().texOffs(350,46).addBox(19.4F,-32F,-8F,1.2F,6F,6F),PartPose.ZERO);
        diverCanopy.addOrReplaceChild("starboard_porthole_glass",CubeListBuilder.create().texOffs(350,59).addBox(-20.6F,-32F,-8F,1.2F,6F,6F),PartPose.ZERO);
        PartDefinition diverService=diver.addOrReplaceChild("service",CubeListBuilder.create(),PartPose.offset(0F,22F,0F));
        PartDefinition diverServiceStarboardHatch=diverService.addOrReplaceChild("starboard_hatch",CubeListBuilder.create(),PartPose.offset(-23F,-24F,4F));
        diverServiceStarboardHatch.addOrReplaceChild("starboard_hatch_panel",CubeListBuilder.create().texOffs(400,380).addBox(-1F,-7F,0F,1F,7F,6F),PartPose.ZERO);
        PartDefinition diverServicePortHatch=diverService.addOrReplaceChild("port_hatch",CubeListBuilder.create(),PartPose.offset(23F,-24F,4F));
        diverServicePortHatch.addOrReplaceChild("port_hatch_panel",CubeListBuilder.create().texOffs(400,380).addBox(0F,-7F,0F,1F,7F,6F),PartPose.ZERO);
        PartDefinition diverServiceSockets=diverService.addOrReplaceChild("sockets",CubeListBuilder.create(),PartPose.ZERO);
        diverServiceSockets.addOrReplaceChild("starboard_socket",CubeListBuilder.create().texOffs(420,380).addBox(-23F,-30F,5F,1F,5F,4F),PartPose.ZERO);
        diverServiceSockets.addOrReplaceChild("port_socket",CubeListBuilder.create().texOffs(420,380).addBox(22F,-30F,5F,1F,5F,4F),PartPose.ZERO);
        return LayerDefinition.create(mesh,512,512).bakeRoot();
    }
}
