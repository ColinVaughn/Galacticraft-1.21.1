package dev.galacticraft.mod.content.entity.vehicle;

import dev.galacticraft.api.universe.celestialbody.CelestialBodyTags;
import dev.galacticraft.api.entity.ControllableEntity;
import dev.galacticraft.api.entity.IgnoreShift;
import dev.galacticraft.machinelib.api.component.MLDataComponents;
import dev.galacticraft.mod.content.block.jupiter.JupiterBlocks;
import dev.galacticraft.mod.content.item.BatteryItem;
import dev.galacticraft.mod.content.item.InfiniteOxygenTankItem;
import dev.galacticraft.mod.content.item.OxygenTankItem;
import dev.galacticraft.mod.content.item.jupiter.JupiterItems;
import dev.galacticraft.mod.world.dimension.jupiter.JupiterEnvironment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;

/** Server-owned atmospheric craft. Empty craft hover; cargo survives as a persistent buoyant wreck. */
public final class CloudDiverEntity extends GCVehicle implements ControllableEntity, IgnoreShift {
    private static final EntityDataAccessor<Integer> ENERGY = SynchedEntityData.defineId(CloudDiverEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> OXYGEN = SynchedEntityData.defineId(CloudDiverEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> HULL = SynchedEntityData.defineId(CloudDiverEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> EMERGENCY = SynchedEntityData.defineId(CloudDiverEntity.class, EntityDataSerializers.BOOLEAN);
    /** The Vehicle Dock currently servicing this diver, which the dock's hose and the open service hatches follow. */
    private static final EntityDataAccessor<java.util.Optional<net.minecraft.core.BlockPos>> DOCK = SynchedEntityData.defineId(CloudDiverEntity.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);
    /** Ticks the dock link survives without a service pass; docks service every 10 ticks. */
    private static final int DOCK_HOLD_TICKS = 25;
    /** Service socket centre on the starboard flank in model pixels; the port socket mirrors it in x. */
    private static final double SOCKET_X = 23.2, SOCKET_Y = 27.5, SOCKET_Z = 7;
    public static final byte COLLECT_EVENT = 61;
    private static final int COLLECT_TICKS = 24;
    private int collectTick = Integer.MIN_VALUE / 2;
    public static final int ENERGY_CAPACITY = 96000, OXYGEN_CAPACITY = 324000;
    /** Cruise speeds in blocks per tick and the top turn rate in degrees per tick. */
    public static final float MAX_SPEED = 0.36F, CLIMB_SPEED = 0.28F, TURN_SPEED = 2.5F;
    private int controls, inputAge, actionCooldown;
    private boolean emergency;
    private float yawRate, riderTurn;
    private final it.unimi.dsi.fastutil.ints.IntSet ridersToTurn = new it.unimi.dsi.fastutil.ints.IntArraySet();
    // Client-only attitude in [-1, 1]; positive lean raises the nose, positive bank lowers starboard.
    private float lean, leanO, bank, bankO, lastForward;
    // Client-only docking progress in [0, 1], easing the hatches open and closed.
    private float docking, dockingO;
    private int dockTicks;
    private final SimpleContainer cargo = new SimpleContainer(27) {
        @Override public boolean stillValid(Player player) { return !isRemoved() && (player.getVehicle() == CloudDiverEntity.this || player.distanceToSqr(CloudDiverEntity.this) < 64); }
    };

    public CloudDiverEntity(EntityType<?> type, Level level) { super(type, level); setNoGravity(true); }
    @Override protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b); b.define(ENERGY,0); b.define(OXYGEN,0); b.define(HULL,300); b.define(EMERGENCY,false); b.define(DOCK,java.util.Optional.empty());
    }
    public int energy() { return entityData.get(ENERGY); }
    public int oxygen() { return entityData.get(OXYGEN); }
    public int hull() { return entityData.get(HULL); }
    public float pressureRating() { return 24; }
    public boolean hasCabinOxygen() { return hull() > 0 && oxygen() > 0; }
    /** Emergency ascent or hull at 30% or less; drives the renderer's alarm strobe. */
    public boolean alarm() { return hull() > 0 && (entityData.get(EMERGENCY) || hull() <= 90); }
    /** Collector arm animation progress in [0, 1), or -1 when no collect is playing. */
    public float collectProgress(float partial) { float p=(tickCount-collectTick+partial)/COLLECT_TICKS; return p>=0 && p<1 ? p : -1; }
    public float lean(float partial) { return Mth.lerp(partial,leanO,lean); }
    public float docking(float partial) { return Mth.lerp(partial,dockingO,docking); }
    public java.util.Optional<net.minecraft.core.BlockPos> dock() { return entityData.get(DOCK); }
    /** Called by a Vehicle Dock on each service pass; the link lapses a little after the passes stop. */
    public void dockWith(net.minecraft.core.BlockPos dock) {
        dockTicks=DOCK_HOLD_TICKS;
        if(!dock.equals(entityData.get(DOCK).orElse(null)))entityData.set(DOCK,java.util.Optional.of(dock.immutable()));
    }
    /** World position of the service socket on whichever flank faces {@code toward}. */
    public Vec3 servicePort(Vec3 toward,float partial) {
        Vec3 origin=getPosition(partial);
        float yaw=(180-getViewYRot(partial))*Mth.DEG_TO_RAD;
        Vec3 starboard=origin.add(new Vec3(SOCKET_X,SOCKET_Y,SOCKET_Z).scale(1/16.0).yRot(yaw));
        Vec3 port=origin.add(new Vec3(-SOCKET_X,SOCKET_Y,SOCKET_Z).scale(1/16.0).yRot(yaw));
        return starboard.distanceToSqr(toward)<=port.distanceToSqr(toward)?starboard:port;
    }
    public float bank(float partial) { return Mth.lerp(partial,bankO,bank); }
    @Override public void handleEntityEvent(byte id) { if(id==COLLECT_EVENT)collectTick=tickCount; else super.handleEntityEvent(id); }
    public SimpleContainer cargo() { return cargo; }
    public int acceptEnergy(int amount) { int n=Math.max(0,Math.min(amount,ENERGY_CAPACITY-energy()));entityData.set(ENERGY,energy()+n);return n; }
    public int acceptOxygen(int amount) { int n=Math.max(0,Math.min(amount,OXYGEN_CAPACITY-oxygen()));entityData.set(OXYGEN,oxygen()+n);return n; }
    public void repair(int amount) { if(hull()>0)entityData.set(HULL,Math.min(300,hull()+amount)); }
    @Override public boolean isControlledByLocalInstance() { return !level().isClientSide; }
    @Override public LivingEntity getControllingPassenger() { return getFirstPassenger() instanceof LivingEntity living ? living : null; }
    @Override protected boolean canAddPassenger(Entity e) { return hull()>0 && getPassengers().size()<2; }
    @Override public boolean shouldIgnoreShiftExit() { return true; }
    @Override public boolean isPickable() { return !isRemoved(); }
    @Override public boolean canBeCollidedWith() { return !isRemoved(); }

    @Override public void inputTick(float side,float forward,boolean up,boolean down,boolean left,boolean right,boolean jump,boolean descend,boolean invert) {
        if(level().isClientSide)return;
        controls=(up?1:0)|(down?2:0)|(left?4:0)|(right?8:0)|(jump?16:0)|(descend?32:0);
        inputAge=0;
    }

    @Override protected void positionRider(Entity passenger, MoveFunction move) {
        if(!hasPassenger(passenger))return;
        double side=getPassengers().indexOf(passenger)==0?-0.5:0.5;
        Vec3 offset=new Vec3(side,0.45,0).yRot(-getYRot()*Mth.DEG_TO_RAD);
        move.accept(passenger,getX()+offset.x,getY()+offset.y,getZ()+offset.z);
        // Riders turn with the hull once per tick, after their own tick, so the camera interpolates smoothly like a boat's.
        if(ridersToTurn.remove(passenger.getId())) { passenger.setYRot(passenger.getYRot()+riderTurn);passenger.setYHeadRot(passenger.getYHeadRot()+riderTurn); }
    }
    @Override protected int interpolationSteps(int requested) { return requested; }
    @Override public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        for(int i=0;i<4;i++) {
            Vec3 offset=new Vec3(3,0,0).yRot(i*Mth.HALF_PI);
            Vec3 p=position().add(offset);
            if(level().noCollision(passenger,passenger.getBoundingBox().move(p.subtract(passenger.position()))))return p;
        }
        return position().add(0,2.5,0);
    }

    @Override public void tick() {
        super.tick();
        if(level().isClientSide)tickAttitude(); else tickFlight();
        if(level().isClientSide) {
            dockingO=docking;
            docking=Mth.approach(docking,dock().isPresent()?1:0,1/20F);
        } else if(dockTicks>0 && --dockTicks==0)entityData.set(DOCK,java.util.Optional.empty());
        riderTurn=Mth.wrapDegrees(getYRot()-yRotO);
        ridersToTurn.clear();
        if(riderTurn!=0)for(Entity passenger:getPassengers())ridersToTurn.add(passenger.getId());
    }

    /** Leans into acceleration and climbs and banks into turns, from the motion the client actually sees. */
    private void tickAttitude() {
        leanO=lean;bankO=bank;
        float yaw=getYRot()*Mth.DEG_TO_RAD;
        float forward=(float)((-(getX()-xo)*Mth.sin(yaw)+(getZ()-zo)*Mth.cos(yaw))/MAX_SPEED);
        float accel=(forward-lastForward)/0.08F;
        lastForward=forward;
        float climb=(float)((getY()-yo)/CLIMB_SPEED),turn=Mth.wrapDegrees(getYRot()-yRotO)/TURN_SPEED;
        float leanTarget=Mth.clamp(0.45F*climb-0.35F*forward-0.5F*Mth.clamp(accel,-1,1),-1,1);
        float bankTarget=Mth.clamp(turn*(0.35F+0.65F*Math.min(1,Math.abs(forward))),-1,1);
        lean+=(leanTarget-lean)*0.15F;
        bank+=(bankTarget-bank)*0.15F;
        spawnExhaust();
    }

    /** Client-only flame and smoke from both nozzles, scaled by thrust and trailing the hull's motion. */
    private void spawnExhaust() {
        if(getPassengers().isEmpty() || energy()<=0 || hull()<=0)return;
        Vec3 motion=new Vec3(getX()-xo,getY()-yo,getZ()-zo);
        float thrust=(float)Math.min(1,motion.length()/MAX_SPEED),yaw=getYRot()*Mth.DEG_TO_RAD;
        // Model space to world: +x is the hull's right (starboard), -z is its bow; units are 1/16 block.
        double fx=-Mth.sin(yaw),fz=Mth.cos(yaw),rx=-Mth.cos(yaw),rz=-Mth.sin(yaw);
        for(int side=-1;side<=1;side+=2) {
            double x=getX()+rx*side*27.5/16-fx*31/16,y=getY()+15/16.0,z=getZ()+rz*side*27.5/16-fz*31/16;
            double push=0.06+0.12*thrust;
            if(random.nextFloat()<0.25F+0.75F*thrust)level().addParticle(ParticleTypes.FLAME,x+jitter(0.15),y+jitter(0.15),z+jitter(0.15),
                    motion.x*0.6-fx*push+jitter(0.01),motion.y*0.6+jitter(0.01),motion.z*0.6-fz*push+jitter(0.01));
            if(random.nextFloat()<0.4F*thrust)level().addParticle(ParticleTypes.SMOKE,x-fx*0.5+jitter(0.2),y+jitter(0.2),z-fz*0.5+jitter(0.2),
                    motion.x*0.4-fx*push*0.6,motion.y*0.4+0.01,motion.z*0.4-fz*push*0.6);
        }
    }
    private double jitter(double size) { return (random.nextDouble()-0.5)*2*size; }

    private void tickFlight() {
        if(++inputAge>10)controls=0;
        if(actionCooldown>0)actionCooldown--;
        boolean wreck=hull()<=0;
        if(wreck) { yawRate=0;setDeltaMovement(0,getY()<240?0.12:0,0);move(MoverType.SELF,getDeltaMovement());return; }
        boolean occupied=!getPassengers().isEmpty();
        boolean powered=energy()>0;
        if(occupied && (energy()<1000 || oxygen()<2000))emergency=true;
        if(getY()>=240 && energy()>=1000 && oxygen()>=2000)emergency=false;
        entityData.set(EMERGENCY,emergency);
        boolean piloted=occupied && powered;
        // Ease turn rate and thrust to preserve hull inertia.
        float turn=piloted?((controls&8)!=0?1:0)-((controls&4)!=0?1:0):0;
        yawRate+=(turn*TURN_SPEED-yawRate)*0.15F;
        if(Math.abs(yawRate)<0.01F)yawRate=0;
        setYRot(getYRot()+yawRate);
        double thrust=piloted?(((controls&1)!=0?1:0)-((controls&2)!=0?1:0))*MAX_SPEED:0;
        double vertical=piloted?(((controls&16)!=0?1:0)-((controls&32)!=0?1:0))*CLIMB_SPEED:0;
        if(emergency && occupied && getY()<240) { vertical=0.35;thrust=0; }
        Vec3 target=new Vec3(0,vertical,thrust).yRot(-getYRot()*Mth.DEG_TO_RAD),current=getDeltaMovement();
        Vec3 velocity=new Vec3(current.x+(target.x-current.x)*0.08,current.y+(target.y-current.y)*0.12,current.z+(target.z-current.z)*0.08);
        if(velocity.lengthSqr()<0.00001)velocity=Vec3.ZERO;
        setDeltaMovement(velocity);move(MoverType.SELF,velocity);
        if((horizontalCollision || verticalCollision) && velocity.length()>0.22 && tickCount%10==0)damageHull(2+(int)(velocity.length()*8));
        if(tickCount%20==0) {
            long rate=getPassengers().stream().filter(LivingEntity.class::isInstance).map(LivingEntity.class::cast)
                    .mapToLong(e->Math.max(1,e.galacticraft$oxygenConsumptionRate())*20).sum();
            entityData.set(OXYGEN,(int)Math.max(0,oxygen()-rate));
            entityData.set(ENERGY,Math.max(0,energy()-(occupied?1:0)-(velocity.lengthSqr()>0.001?20:0)));
            if(CelestialBodyTags.is(level(), CelestialBodyTags.GAS_GIANT)) {
                float excess=JupiterEnvironment.pressure(getY())-pressureRating();
                if(excess>0.001)damageHull(5+(int)(excess*2));
            }
            Component beacon=beaconBearing();
            for(Entity e:getPassengers())if(e instanceof ServerPlayer player)player.displayClientMessage(Component.translatable(
                    "gui.galacticraft.jupiter.diver",hull(),Math.round(energy()*100F/ENERGY_CAPACITY),Math.round(oxygen()*100F/OXYGEN_CAPACITY),emergency?" !":"").append(beacon),true);
        }
    }

    /** Range and compass bearing to the nearest broadcasting Survey Beacon, for the pilot's HUD; empty when there is none. */
    private Component beaconBearing() {
        if(!(level() instanceof ServerLevel server))return Component.empty();
        var nearest=dev.galacticraft.mod.world.dimension.jupiter.SurveyBeacons.get(server).nearest(position(),null,1);
        if(nearest.isEmpty())return Component.empty();
        var beacon=nearest.get(0);
        return Component.translatable(dev.galacticraft.mod.content.block.jupiter.JupiterText.DIVER_BEACON,beacon.name(),
                dev.galacticraft.mod.world.dimension.jupiter.SurveyBeacons.distance(position(),beacon.pos()),
                dev.galacticraft.mod.world.dimension.jupiter.SurveyBeacons.bearing(position(),beacon.pos()));
    }

    private void damageHull(int damage) {
        entityData.set(HULL,Math.max(0,hull()-Math.max(0,damage)));
        if(hull()==0) { controls=0;ejectPassengers();entityData.set(ENERGY,0);entityData.set(OXYGEN,0);setCustomName(Component.translatable("entity.galacticraft.cloud_diver.wreck")); }
    }
    @Override public boolean hurt(DamageSource source,float amount) {
        if(level().isClientSide || isInvulnerableTo(source) || hull()<=0)return false;
        damageHull((int)Math.ceil(amount));markHurt();return true;
    }

    @Override public InteractionResult interact(Player player,InteractionHand hand) {
        if(level().isClientSide)return InteractionResult.SUCCESS;
        ItemStack held=player.getItemInHand(hand);
        if(held.getItem() instanceof BatteryItem battery && hull()>0) {
            long n=Math.min(battery.getStoredEnergy(held),Math.min(battery.getEnergyMaxOutput(held),ENERGY_CAPACITY-energy()));
            int accepted=acceptEnergy((int)n);battery.setStoredEnergy(held,battery.getStoredEnergy(held)-accepted);return InteractionResult.CONSUME;
        }
        if(held.getItem() instanceof OxygenTankItem && hull()>0) {
            long n=Math.min(OXYGEN_CAPACITY-oxygen(),OxygenTankItem.getStorage(held).getAmount());
            if(!(held.getItem() instanceof InfiniteOxygenTankItem))held.set(MLDataComponents.AMOUNT.get(),OxygenTankItem.getStorage(held).getAmount()-n);
            acceptOxygen((int)n);return InteractionResult.CONSUME;
        }
        if(held.is(JupiterItems.AEROSTAT_SALVAGE) && hull()>0 && hull()<300) { repair(30);if(!player.isCreative())held.shrink(1);return InteractionResult.CONSUME; }
        if(player.isSecondaryUseActive() || hull()<=0) { openCargo((ServerPlayer)player);return InteractionResult.CONSUME; }
        return player.startRiding(this)?InteractionResult.CONSUME:InteractionResult.PASS;
    }
    public void openCargo(ServerPlayer player) {
        if(player.getVehicle()!=this && player.distanceToSqr(this)>64)return;
        player.openMenu(new SimpleMenuProvider((id,inv,p)->ChestMenu.threeRows(id,inv,cargo),Component.translatable("container.galacticraft.cloud_diver")));
    }
    public void collect(ServerPlayer player) {
        if(getControllingPassenger()!=player || hull()<=0 || energy()<100 || actionCooldown>0 || !(level() instanceof ServerLevel level))return;
        actionCooldown=10;
        Vec3 start=player.getEyePosition(),end=start.add(player.getLookAngle().scale(8));
        BlockHitResult hit=level.clip(new ClipContext(start,end,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,player));
        BlockPos pos=hit.getBlockPos();var state=level.getBlockState(pos);
        if(hit.getType()!=HitResult.Type.BLOCK || !(state.is(JupiterBlocks.HYDROSULFIDE_CRYSTAL_CLUSTER)||state.is(JupiterBlocks.CHARGED_CONDENSATE_CLUSTER))
                || !level.mayInteract(player,pos) || !player.mayUseItemAt(pos,hit.getDirection(),heldTool(player)))return;
        var drops=Block.getDrops(state,level,pos,level.getBlockEntity(pos),player,heldTool(player));
        if(!level.destroyBlock(pos,false,player))return;
        entityData.set(ENERGY,energy()-100);
        level.broadcastEntityEvent(this,COLLECT_EVENT);
        for(ItemStack drop:drops){ItemStack remainder=cargo.addItem(drop);if(!remainder.isEmpty())spawnAtLocation(remainder);}
    }
    private ItemStack heldTool(Player player) { return player.getMainHandItem(); }

    @Override protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(ENERGY,Mth.clamp(tag.getInt("Energy"),0,ENERGY_CAPACITY));
        entityData.set(OXYGEN,Mth.clamp(tag.getInt("Oxygen"),0,OXYGEN_CAPACITY));entityData.set(HULL,Mth.clamp(tag.getInt("Hull"),0,300));
        cargo.fromTag(tag.getList("Cargo",10),registryAccess());emergency=tag.getBoolean("Emergency");entityData.set(EMERGENCY,emergency);
    }
    @Override protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Energy",energy());tag.putInt("Oxygen",oxygen());tag.putInt("Hull",hull());
        tag.put("Cargo",cargo.createTag(registryAccess()));tag.putBoolean("Emergency",emergency);
    }
}