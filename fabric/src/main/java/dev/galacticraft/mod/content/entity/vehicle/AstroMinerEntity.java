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

package dev.galacticraft.mod.content.entity.vehicle;

import dev.galacticraft.mod.content.GCBlocks;
import dev.galacticraft.mod.content.block.entity.machine.AstroMinerBaseBlockEntity;
import dev.galacticraft.mod.content.item.GCItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedList;
import java.util.UUID;

/**
 * The Astro Miner: an autonomous mining robot deployed from and serviced by an
 * {@link AstroMinerBaseBlockEntity}. Ported 1:1 from legacy Galacticraft's
 * {@code EntityAstroMiner}. Once deployed it travels along its facing, carves a
 * 4-wide x 3-tall tunnel <em>forward</em> (not just down), hollows a mining room,
 * then retraces its own tunnels back to the base to unload and recharge.
 *
 * <p>Movement and mining are server-authoritative; vanilla entity tracking syncs the
 * position/rotation to clients, and {@link #DATA_AI_STATE}/{@link #DATA_ENERGY} are
 * synced for rendering (mining lasers) and status.</p>
 */
public class AstroMinerEntity extends GCVehicle {
    public static final int INVENTORY_SIZE = 227;
    public static final int MAX_ENERGY = 12000;
    private static final int RETURN_ENERGY = 1000;
    private static final int RETURN_DROPS = 10;
    private static final int MINE_LENGTH = 24;

    private static final double SPEEDUP = 2.5D;
    private static final double SPEED_BASE = 0.022D;
    private static final float ROT_SPEED_BASE = 1.5F;

    // AI states (legacy values).
    public static final int AISTATE_OFFLINE = -1;
    public static final int AISTATE_STUCK = 0;
    public static final int AISTATE_ATBASE = 1;
    public static final int AISTATE_TRAVELLING = 2;
    public static final int AISTATE_MINING = 3;
    public static final int AISTATE_RETURNING = 4;
    public static final int AISTATE_DOCKING = 5;

    private static final EntityDataAccessor<Integer> DATA_AI_STATE = SynchedEntityData.defineId(AstroMinerEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ENERGY = SynchedEntityData.defineId(AstroMinerEntity.class, EntityDataSerializers.INT);
    // The active mining heading, synced so the client can draw the mining lasers.
    private static final EntityDataAccessor<Integer> DATA_FACING = SynchedEntityData.defineId(AstroMinerEntity.class, EntityDataSerializers.INT);

    // headings2 lookahead offsets, indexed by Direction.get3DDataValue() (DOWN,UP,N,S,W,E).
    private static final Vec3i[] HEADINGS2 = {
            new Vec3i(0, -3, 0), new Vec3i(0, 2, 0),
            new Vec3i(0, 0, -3), new Vec3i(0, 0, 2),
            new Vec3i(-3, 0, 0), new Vec3i(2, 0, 0)
    };

    // 12-block rounded cross-sections (offsets from the slab centre), by travel axis.
    private static final int[][] SECTION_XZ = { // UP/DOWN travel (horizontal slab)
            {0, 0, 0}, {1, 0, 0}, {1, 0, -1}, {0, 0, -1}, {0, 0, -2}, {-1, 0, -2},
            {-1, 0, -1}, {-2, 0, -1}, {-2, 0, 0}, {-1, 0, 0}, {-1, 0, 1}, {0, 0, 1}
    };
    private static final int[][] SECTION_XY = { // NORTH/SOUTH travel (vertical slab in XY)
            {0, -2, 0}, {-1, -2, 0}, {0, -1, 0}, {-1, -1, 0}, {1, -1, 0}, {-2, -1, 0},
            {1, 0, 0}, {-2, 0, 0}, {0, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {-1, 1, 0}
    };
    private static final int[][] SECTION_YZ = { // WEST/EAST travel (vertical slab in YZ)
            {0, -2, -1}, {0, -1, 0}, {0, -1, -1}, {0, -1, 1}, {0, -1, -2}, {0, 0, 1},
            {0, 0, -2}, {0, 0, -1}, {0, -2, 0}, {0, 1, -1}, {0, 0, 0}, {0, 1, 0}
    };

    // --- state ---
    private int energy;
    private int aiState = AISTATE_ATBASE;
    private final SimpleContainer inventory = new SimpleContainer(INVENTORY_SIZE);

    private BlockPos posBase;
    private BlockPos waypointBase;
    private BlockPos posTarget;
    private Direction baseFacing = Direction.NORTH;
    private Direction facingAI = Direction.NORTH;
    private Direction lastFacing = Direction.NORTH;
    private UUID ownerUUID;

    private final LinkedList<BlockPos> wayPoints = new LinkedList<>();
    private final LinkedList<BlockPos> minePoints = new LinkedList<>();
    private BlockPos minePointCurrent = null;

    private float targetYaw;
    private float targetPitch;
    private double speed = SPEED_BASE;
    private float rotSpeed = ROT_SPEED_BASE;
    private double speedup = SPEEDUP;
    private boolean noSpeedup = false;
    private boolean stopForTurn = false;

    private double mx, my, mz;
    private int mineCountDown = 0;
    private BlockPos mineLast = null;
    private int tryBlockLimit = 0;
    private int pathBlockedCount = 0;
    private int inventoryDrops = 0;
    private int mineCount = 0;

    public AstroMinerEntity(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_AI_STATE, AISTATE_ATBASE);
        builder.define(DATA_ENERGY, 0);
        builder.define(DATA_FACING, Direction.NORTH.get3DDataValue());
    }

    /** Called by {@link dev.galacticraft.mod.content.item.AstroMinerItem} on deploy. */
    public void prepareDeploy(BlockPos masterPos, Direction facing, UUID owner) {
        this.posBase = masterPos;
        this.baseFacing = facing;
        this.facingAI = facing;
        this.lastFacing = facing;
        this.ownerUUID = owner;
        this.waypointBase = masterPos.relative(facing, 1);
        this.aiState = AISTATE_ATBASE;
        this.entityData.set(DATA_AI_STATE, this.aiState);
        this.targetYaw = yawForFacing(facing);
        this.targetPitch = 0;
        this.setYRot(this.targetYaw);
        this.setXRot(0);
        // Spawn just in front of the base face, at base height.
        BlockPos spawn = masterPos.relative(facing, 2);
        this.setPos(spawn.getX() + 0.5, masterPos.getY(), spawn.getZ() + 0.5);
    }

    private static float yawForFacing(Direction facing) {
        return switch (facing) {
            case SOUTH -> 0F;
            case WEST -> 270F;
            case EAST -> 90F;
            default -> 180F; // NORTH
        };
    }

    public int getEnergy() {
        return this.energy;
    }

    public void setEnergy(int energy) {
        this.energy = Mth.clamp(energy, 0, MAX_ENERGY);
    }

    public int getAiState() {
        return this.level().isClientSide ? this.entityData.get(DATA_AI_STATE) : this.aiState;
    }

    public Container getInventory() {
        return this.inventory;
    }

    public UUID getOwnerUUID() {
        return this.ownerUUID;
    }

    /** Mining heading, synced to the client for laser rendering. */
    public Direction getSyncedFacing() {
        return Direction.from3DDataValue(this.entityData.get(DATA_FACING));
    }

    /**
     * Client-side: the block positions the drill is currently lasering (the mining
     * cross-section one slab ahead of the miner). Empty unless actively mining.
     */
    public List<BlockPos> getLaserTargets() {
        List<BlockPos> targets = new ArrayList<>();
        if (getAiState() != AISTATE_MINING) return targets;
        Direction f = getSyncedFacing();
        int fi = f.get3DDataValue();
        // Matches the slab the server carves while MINING: prepareMove(limit, dist=2).
        BlockPos inFront = new BlockPos(Mth.floor(this.getX() + 0.5), Mth.floor(this.getY() + 1.5), Mth.floor(this.getZ() + 0.5))
                .offset(HEADINGS2[fi]);
        int[][] section = switch (fi & 6) {
            case 2 -> SECTION_XY;
            case 4 -> SECTION_YZ;
            default -> SECTION_XZ;
        };
        for (int[] off : section) {
            BlockPos p = inFront.offset(off[0], off[1], off[2]);
            BlockState st = this.level().getBlockState(p);
            if (!st.isAir() && st.getFluidState().isEmpty()) targets.add(p);
        }
        return targets;
    }

    // ------------------------------------------------------------------ NBT
    @Override
    protected void readAdditionalSaveData(CompoundTag nbt) {
        this.energy = nbt.getInt("Energy");
        this.aiState = nbt.contains("AIState") ? nbt.getInt("AIState") : AISTATE_ATBASE;
        this.mineCount = nbt.getInt("MineCount");
        this.inventoryDrops = nbt.getInt("InventoryDrops");
        this.baseFacing = Direction.from3DDataValue(nbt.getInt("BaseFacing"));
        this.facingAI = Direction.from3DDataValue(nbt.getInt("FacingAI"));
        this.lastFacing = this.facingAI;
        this.posBase = readPos(nbt, "PosBase");
        this.waypointBase = readPos(nbt, "WaypointBase");
        this.posTarget = readPos(nbt, "PosTarget");
        if (nbt.hasUUID("Owner")) this.ownerUUID = nbt.getUUID("Owner");
        readPosList(nbt, "WayPoints", this.wayPoints);
        readPosList(nbt, "MinePoints", this.minePoints);
        if (nbt.contains("Inventory")) {
            net.minecraft.core.NonNullList<ItemStack> tmp = net.minecraft.core.NonNullList.withSize(INVENTORY_SIZE, ItemStack.EMPTY);
            ContainerHelper.loadAllItems(nbt.getCompound("Inventory"), tmp, this.registryAccess());
            for (int i = 0; i < INVENTORY_SIZE; i++) this.inventory.setItem(i, tmp.get(i));
        }
        this.entityData.set(DATA_AI_STATE, this.aiState);
        this.entityData.set(DATA_ENERGY, this.energy);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag nbt) {
        nbt.putInt("Energy", this.energy);
        nbt.putInt("AIState", this.aiState);
        nbt.putInt("MineCount", this.mineCount);
        nbt.putInt("InventoryDrops", this.inventoryDrops);
        nbt.putInt("BaseFacing", this.baseFacing.get3DDataValue());
        nbt.putInt("FacingAI", this.facingAI.get3DDataValue());
        writePos(nbt, "PosBase", this.posBase);
        writePos(nbt, "WaypointBase", this.waypointBase);
        writePos(nbt, "PosTarget", this.posTarget);
        if (this.ownerUUID != null) nbt.putUUID("Owner", this.ownerUUID);
        writePosList(nbt, "WayPoints", this.wayPoints);
        writePosList(nbt, "MinePoints", this.minePoints);
        CompoundTag inv = new CompoundTag();
        ContainerHelper.saveAllItems(inv, stacksView(), this.registryAccess());
        nbt.put("Inventory", inv);
    }

    private net.minecraft.core.NonNullList<ItemStack> stacksView() {
        net.minecraft.core.NonNullList<ItemStack> list = net.minecraft.core.NonNullList.withSize(INVENTORY_SIZE, ItemStack.EMPTY);
        for (int i = 0; i < INVENTORY_SIZE; i++) list.set(i, this.inventory.getItem(i));
        return list;
    }

    private static BlockPos readPos(CompoundTag nbt, String key) {
        return nbt.contains(key) ? BlockPos.of(nbt.getLong(key)) : null;
    }

    private static void writePos(CompoundTag nbt, String key, BlockPos pos) {
        if (pos != null) nbt.putLong(key, pos.asLong());
    }

    private static void readPosList(CompoundTag nbt, String key, LinkedList<BlockPos> list) {
        list.clear();
        ListTag tag = nbt.getList(key, Tag.TAG_LONG);
        for (int i = 0; i < tag.size(); i++) {
            list.add(BlockPos.of(((net.minecraft.nbt.LongTag) tag.get(i)).getAsLong()));
        }
    }

    private static void writePosList(CompoundTag nbt, String key, LinkedList<BlockPos> list) {
        ListTag tag = new ListTag();
        for (BlockPos pos : list) tag.add(net.minecraft.nbt.LongTag.valueOf(pos.asLong()));
        nbt.put(key, tag);
    }

    // ------------------------------------------------------------------ tick
    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide || !(this.level() instanceof ServerLevel level)) {
            return; // client: vanilla interpolation (lasers handled by the renderer)
        }
        if (this.getY() < level.getMinBuildHeight() - 4) {
            this.discard();
            return;
        }

        this.stopForTurn = !checkRotation();
        this.noSpeedup = false;

        // Pre-carve when the heading changes so a full 3-slab bore opens up.
        if (this.lastFacing != this.facingAI) {
            this.lastFacing = this.facingAI;
            prepareMove(12, 0);
            prepareMove(12, 1);
            prepareMove(12, 2);
        }

        // Energy gate + drain while active (no drain at base/stuck).
        if (this.aiState > AISTATE_ATBASE) {
            if (this.energy <= 0) {
                freeze();
            } else if (this.tickCount % 2 == 0) {
                this.energy--;
            }
        }

        switch (this.aiState) {
            case AISTATE_STUCK -> tickStuck();
            case AISTATE_ATBASE -> atBase(level);
            case AISTATE_TRAVELLING -> {
                if (!moveToTarget(level)) prepareMove(2, 2);
            }
            case AISTATE_MINING -> {
                if (!doMining(level) && this.tickCount % 2 == 0) {
                    this.energy--;
                    prepareMove(1, 2);
                }
            }
            case AISTATE_RETURNING -> {
                moveToBase();
                prepareMove(4, 1);
            }
            case AISTATE_DOCKING -> tickDocking();
            default -> {
            }
        }

        // Apply motion (the miner carves its own path, so no collision resolution).
        if (this.mx != 0 || this.my != 0 || this.mz != 0) {
            this.setPos(this.getX() + this.mx, this.getY() + this.my, this.getZ() + this.mz);
        }

        this.entityData.set(DATA_AI_STATE, this.aiState);
        this.entityData.set(DATA_ENERGY, this.energy);
        this.entityData.set(DATA_FACING, this.facingAI.get3DDataValue());
    }

    private void tickStuck() {
        zeroMotion();
        if (this.tickCount % 600 == 0) {
            setState(AISTATE_RETURNING);
            if (this.energy <= 0) this.energy = 20; // enough to limp home
        }
    }

    private void tickDocking() {
        this.speed = SPEED_BASE / 1.6D;
        this.rotSpeed = (float) (ROT_SPEED_BASE / 1.6D);
        if (this.waypointBase == null) {
            setState(AISTATE_STUCK);
            return;
        }
        if (moveToPos(this.waypointBase, true)) {
            setState(AISTATE_ATBASE);
            zeroMotion();
            this.speed = SPEED_BASE;
            this.rotSpeed = ROT_SPEED_BASE;
        }
    }

    private AstroMinerBaseBlockEntity getBase() {
        if (this.posBase == null || !this.level().isLoaded(this.posBase)) return null;
        BlockState state = this.level().getBlockState(this.posBase);
        if (!state.is(GCBlocks.ASTRO_MINER_BASE)) return null;
        return this.level().getBlockEntity(this.posBase) instanceof AstroMinerBaseBlockEntity base ? base : null;
    }

    private void atBase(ServerLevel level) {
        zeroMotion();
        AstroMinerBaseBlockEntity base = getBase();
        if (base == null) {
            freeze(); // base destroyed; wait to be rebuilt
            return;
        }
        this.inventoryDrops = 0;
        // Unload one stack every 5 ticks.
        boolean unloaded = false;
        if (this.tickCount % 5 == 0) {
            unloaded = emptyOneStack(base);
        }
        // Recharge.
        if (base.hasEnergyToRun() && this.energy < MAX_ENERGY) {
            this.energy += (int) base.extractEnergyForMiner(16);
        }
        // Fully charged, empty, and the base can hold more: pick the next area and go.
        if (this.energy >= MAX_ENERGY && !unloaded && !hasCargo() && base.hasHoldSpace()) {
            BlockPos target = findNextTarget(base);
            if (target != null) {
                this.posTarget = target;
                this.wayPoints.clear();
                this.wayPoints.add(this.waypointBase);
                this.mineCount = 0;
                setState(AISTATE_TRAVELLING);
            }
        }
    }

    private boolean emptyOneStack(AstroMinerBaseBlockEntity base) {
        for (int i = 0; i < this.inventory.getContainerSize(); i++) {
            ItemStack stack = this.inventory.getItem(i);
            if (!stack.isEmpty()) {
                ItemStack leftover = base.addToHold(stack.copy());
                if (leftover.getCount() != stack.getCount()) {
                    this.inventory.setItem(i, leftover);
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasCargo() {
        for (int i = 0; i < this.inventory.getContainerSize(); i++) {
            if (!this.inventory.getItem(i).isEmpty()) return true;
        }
        return false;
    }

    private BlockPos findNextTarget(AstroMinerBaseBlockEntity base) {
        if (!this.minePoints.isEmpty() && this.pathBlockedCount < 2) {
            return this.minePoints.getFirst();
        }
        BlockPos next = base.pollNextTarget();
        this.pathBlockedCount = 0;
        return next;
    }

    private boolean moveToTarget(ServerLevel level) {
        if (this.energy < RETURN_ENERGY || this.inventoryDrops > RETURN_DROPS) {
            setState(AISTATE_RETURNING);
            return true;
        }
        if (this.posTarget == null) {
            setState(AISTATE_STUCK);
            return true;
        }
        if (moveToPos(this.posTarget, false)) {
            this.wayPoints.add(this.posTarget);
            setMinePoints();
            setState(AISTATE_MINING);
            return true;
        }
        return false;
    }

    private boolean doMining(ServerLevel level) {
        if (this.energy < RETURN_ENERGY || this.inventoryDrops > RETURN_DROPS || this.minePoints.isEmpty()) {
            if (this.minePointCurrent != null) this.minePoints.addFirst(this.minePointCurrent);
            setState(AISTATE_RETURNING);
            return true;
        }
        this.minePointCurrent = this.minePoints.getFirst();
        if (moveToPos(this.minePointCurrent, false)) {
            this.minePoints.removeFirst();
            this.minePointCurrent = null;
            return true;
        }
        return false;
    }

    private void moveToBase() {
        if (this.wayPoints.isEmpty()) {
            setState(AISTATE_DOCKING);
            this.facingAI = this.baseFacing;
            return;
        }
        BlockPos node = this.wayPoints.getLast();
        if (moveToPos(node, true)) {
            this.wayPoints.removeLast();
        }
    }

    // ------------------------------------------------------------- movement
    private boolean checkRotation() {
        // pitch
        float dPitch = Mth.wrapDegrees(this.targetPitch - this.getXRot());
        if (Math.abs(dPitch) < this.rotSpeed) {
            this.setXRot(this.targetPitch);
        } else {
            this.setXRot(this.getXRot() + Math.signum(dPitch) * (float) this.rotSpeed);
        }
        // yaw
        float dYaw = Mth.wrapDegrees(this.targetYaw - this.getYRot());
        if (Math.abs(dYaw) < this.rotSpeed) {
            this.setYRot(this.targetYaw);
            return true;
        }
        this.setYRot(this.getYRot() + Math.signum(dYaw) * (float) this.rotSpeed);
        return false;
    }

    /** Move one cardinal axis toward {@code target}; returns true when fully aligned. */
    private boolean moveToPos(BlockPos target, boolean reverse) {
        double tx = target.getX() + 0.5;
        double ty = target.getY() - 1;
        double tz = target.getZ() + 0.5;
        boolean zFirst = reverse != (this.baseFacing.get3DDataValue() < 4);
        if (zFirst) {
            if (notAligned(this.getZ(), tz)) { moveToPosZ(tz); return false; }
            if (notAligned(this.getY(), ty)) { moveToPosY(ty); return false; }
            if (notAligned(this.getX(), tx)) { moveToPosX(tx); return false; }
        } else {
            if (notAligned(this.getX(), tx)) { moveToPosX(tx); return false; }
            if (notAligned(this.getY(), ty)) { moveToPosY(ty); return false; }
            if (notAligned(this.getZ(), tz)) { moveToPosZ(tz); return false; }
        }
        zeroMotion();
        return true;
    }

    private static boolean notAligned(double a, double b) {
        return Math.abs(a - b) > 1.0E-3D;
    }

    private void moveToPosX(double x) {
        this.targetPitch = 0;
        double diff = x - this.getX();
        if (diff < 0) {
            if (this.aiState != AISTATE_DOCKING) this.targetYaw = 270;
            this.mx = -this.speed;
            if (this.mx * this.speedup <= diff) { this.mx = diff; this.noSpeedup = true; }
            this.facingAI = Direction.WEST;
        } else {
            if (this.aiState != AISTATE_DOCKING) this.targetYaw = 90;
            this.mx = this.speed;
            if (this.mx * this.speedup >= diff) { this.mx = diff; this.noSpeedup = true; }
            this.facingAI = Direction.EAST;
        }
        if (this.stopForTurn) this.mx = 0;
        this.my = 0;
        this.mz = 0;
    }

    private void moveToPosZ(double z) {
        this.targetPitch = 0;
        double diff = z - this.getZ();
        if (diff < 0) {
            if (this.aiState != AISTATE_DOCKING) this.targetYaw = 180;
            this.mz = -this.speed;
            if (this.mz * this.speedup <= diff) { this.mz = diff; this.noSpeedup = true; }
            this.facingAI = Direction.NORTH;
        } else {
            if (this.aiState != AISTATE_DOCKING) this.targetYaw = 0;
            this.mz = this.speed;
            if (this.mz * this.speedup >= diff) { this.mz = diff; this.noSpeedup = true; }
            this.facingAI = Direction.SOUTH;
        }
        if (this.stopForTurn) this.mz = 0;
        this.mx = 0;
        this.my = 0;
    }

    private void moveToPosY(double y) {
        double diff = y - this.getY();
        if (diff < 0) {
            this.targetPitch = -90;
            this.my = -this.speed;
            if (this.my * this.speedup <= diff) { this.my = diff; this.noSpeedup = true; }
            this.facingAI = Direction.DOWN;
        } else {
            this.targetPitch = 90;
            this.my = this.speed;
            if (this.my * this.speedup >= diff) { this.my = diff; this.noSpeedup = true; }
            this.facingAI = Direction.UP;
        }
        this.mx = 0;
        this.mz = 0;
    }

    private void zeroMotion() {
        this.mx = 0;
        this.my = 0;
        this.mz = 0;
    }

    // -------------------------------------------------------------- mining
    /**
     * Carve one cross-section slab {@code dist} blocks ahead along {@code facingAI},
     * breaking up to {@code limit} blocks this call. Returns true if the way is barred.
     */
    private boolean prepareMove(int limit, int dist) {
        if (this.mineCountDown > 0) {
            this.mineCountDown--;
            return false;
        }
        if (!(this.level() instanceof ServerLevel level)) return false;

        BlockPos inFront = new BlockPos(Mth.floor(this.getX() + 0.5), Mth.floor(this.getY() + 1.5), Mth.floor(this.getZ() + 0.5));
        int fi = this.facingAI.get3DDataValue();
        if (dist == 2) {
            inFront = inFront.offset(HEADINGS2[fi]);
        } else {
            int d = dist;
            if ((fi & 1) == 0) d++; // DOWN/NORTH/WEST reach one block further
            if (d > 0) {
                Vec3i n = this.facingAI.getNormal();
                inFront = inFront.offset(n.getX() * d, n.getY() * d, n.getZ() * d);
            }
        }

        if (!inFront.equals(this.mineLast) && this.aiState != AISTATE_ATBASE) {
            this.mineCountDown = 3;
            this.mineLast = inFront;
            return false;
        }
        if (this.posBase != null && isWithinBase(inFront)) {
            tryBackIn();
            return false;
        }

        this.tryBlockLimit = limit;
        int[][] section = switch (fi & 6) {
            case 2 -> SECTION_XY; // N/S
            case 4 -> SECTION_YZ; // W/E
            default -> SECTION_XZ; // U/D
        };
        boolean wayBarred = false;
        for (int[] off : section) {
            wayBarred |= tryMineBlock(level, inFront.offset(off[0], off[1], off[2]));
        }

        if (wayBarred) {
            zeroMotion();
            switch (this.aiState) {
                case AISTATE_TRAVELLING -> setState(AISTATE_RETURNING);
                case AISTATE_MINING -> {
                    this.pathBlockedCount++;
                    setState(AISTATE_RETURNING);
                }
                case AISTATE_RETURNING -> tryBackIn();
                default -> freeze();
            }
        } else if (this.tryBlockLimit == limit && !this.noSpeedup) {
            // Nothing mined: coast through the already-open tunnel.
            this.mx *= this.speedup;
            this.my *= this.speedup;
            this.mz *= this.speedup;
        }
        return wayBarred;
    }

    private boolean isWithinBase(BlockPos pos) {
        return Math.abs(pos.getX() - this.posBase.getX()) <= 1
                && Math.abs(pos.getY() - this.posBase.getY()) <= 1
                && Math.abs(pos.getZ() - this.posBase.getZ()) <= 1;
    }

    /** Returns true if the block is blocking (unmineable); otherwise mines it. */
    private boolean tryMineBlock(ServerLevel level, BlockPos pos) {
        if (level.isOutsideBuildHeight(pos)) return true;
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return false;
        Block block = state.getBlock();
        if (isNoMine(block)) return true;

        FluidState fluid = state.getFluidState();
        if (!fluid.isEmpty()) {
            // Avoid fresh lava sources unless we're already fleeing home.
            if (fluid.getType() == Fluids.LAVA && fluid.isSource() && this.aiState != AISTATE_RETURNING) {
                return true;
            }
            return false; // pass through water / flowing
        }
        if (state.getDestroySpeed(level, pos) < 0) return true; // unbreakable
        if (state.hasBlockEntity()) return true; // skip chests, spawners, pipes...

        if (this.tryBlockLimit <= 0) return false; // budget spent this call
        this.tryBlockLimit--;

        BlockEntity be = state.hasBlockEntity() ? level.getBlockEntity(pos) : null;
        for (ItemStack drop : Block.getDrops(state, level, pos, be)) {
            ItemStack leftover = addToInventory(drop);
            if (!leftover.isEmpty()) {
                Block.popResource(level, pos, leftover);
                this.inventoryDrops++;
            }
        }
        level.destroyBlock(pos, false);
        return false;
    }

    private static boolean isNoMine(Block block) {
        return block == Blocks.BEDROCK || block == Blocks.MOSSY_COBBLESTONE
                || block == Blocks.END_PORTAL || block == Blocks.END_PORTAL_FRAME
                || block == Blocks.NETHER_PORTAL || block == Blocks.STONE_BRICKS
                || block == Blocks.FARMLAND || block == Blocks.RAIL
                || block == Blocks.LEVER || block == Blocks.REDSTONE_WIRE
                || block == Blocks.BARRIER;
    }

    private ItemStack addToInventory(ItemStack stack) {
        ItemStack remaining = this.inventory.addItem(stack);
        if (remaining.getCount() != stack.getCount()) this.mineCount++;
        return remaining;
    }

    /**
     * Build the 14-node closed box path (legacy setMinePoints) around the current target,
     * oriented by {@code baseFacing}, spanning +/-MINE_LENGTH so driving it hollows a room.
     */
    private void setMinePoints() {
        if (!this.minePoints.isEmpty()) return;
        BlockPos c = this.posTarget;
        int len = MINE_LENGTH;
        // otherEnd is negated for NORTH/WEST bases (they mine toward -Z/-X).
        int end = (this.baseFacing == Direction.NORTH || this.baseFacing == Direction.WEST) ? -len : len;
        boolean zAxis = this.baseFacing.getAxis() == Direction.Axis.Z;
        // Lattice offsets (long axis, lateral, vertical) traced as a serpentine box.
        int[][] pattern = {
                {0, 0, 0}, {0, 4, 0}, {end, 4, 0}, {end, 2, 3}, {0, 2, 3},
                {0, -2, 3}, {end, -2, 3}, {end, -4, 0}, {0, -4, 0}, {0, -2, -3},
                {end, -2, -3}, {end, 2, -3}, {0, 2, -3}, {0, 0, 0}
        };
        for (int[] p : pattern) {
            int along = p[0], lateral = p[1], vertical = p[2];
            int dx, dz;
            if (zAxis) {
                dx = lateral;
                dz = along;
            } else {
                dx = along;
                dz = lateral;
            }
            this.minePoints.add(c.offset(dx, vertical, dz));
        }
    }

    private void tryBackIn() {
        if (this.waypointBase == null) {
            freeze();
            return;
        }
        double distSq = this.distanceToSqr(this.waypointBase.getX() + 0.5, this.waypointBase.getY(), this.waypointBase.getZ() + 0.5);
        if (distSq < 9.1D) {
            setState(AISTATE_DOCKING);
            this.targetYaw = yawForFacing(this.baseFacing);
        } else {
            freeze();
        }
    }

    private void freeze() {
        setState(AISTATE_STUCK);
        zeroMotion();
    }

    private void setState(int state) {
        this.aiState = state;
        this.entityData.set(DATA_AI_STATE, state);
    }

    // ------------------------------------------------------------- misc
    @Override
    public boolean isPickable() {
        return !this.isRemoved();
    }

    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    @Override
    public ItemStack getDropItem() {
        return new ItemStack(GCItems.ASTRO_MINER);
    }

    @Override
    public void destroy(ItemStack selfAsItem) {
        // Spill mined cargo, then drop the miner item and unlink from the base.
        if (this.level() instanceof ServerLevel level) {
            for (int i = 0; i < this.inventory.getContainerSize(); i++) {
                ItemStack stack = this.inventory.getItem(i);
                if (!stack.isEmpty()) Block.popResource(level, this.blockPosition(), stack);
            }
        }
        unlinkFromBase();
        super.destroy(selfAsItem);
    }

    @Override
    public void remove(RemovalReason reason) {
        if (reason.shouldDestroy()) {
            unlinkFromBase();
        }
        super.remove(reason);
    }

    private void unlinkFromBase() {
        AstroMinerBaseBlockEntity base = getBase();
        if (base != null && this.getUUID().equals(base.getLinkedMiner())) {
            base.setLinkedMiner(null);
        }
    }
}
