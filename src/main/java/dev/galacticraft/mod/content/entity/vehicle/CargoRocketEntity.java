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

import dev.galacticraft.mod.screen.VehicleInventoryMenu;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerListener;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The Cargo Rocket vehicle. It carries a 27-slot cargo hold (exposed through a
 * chest-like GUI) and supports a simple launch/travel sequence: on ignition it
 * ascends under increasing thrust, and once it clears {@link #TRAVEL_HEIGHT}
 * above its launch point it delivers its cargo and despawns.
 *
 * <p>Auto-target selection is stubbed: with no planet-routing system wired in
 * this pass, "delivery" drops the cargo back at the launch origin (a ParaChest
 * style drop) rather than at a chosen destination. Automated cargo loading from
 * adjacent machines is likewise out of scope here.</p>
 */
public class CargoRocketEntity extends GCVehicle implements ContainerListener, ContainerVehicle, ExtendedScreenHandlerFactory<Integer> {
    public static final int INVENTORY_SIZE = 27;

    /** Height above the launch origin at which the rocket delivers its cargo. */
    private static final int TRAVEL_HEIGHT = 300;
    private static final double MAX_ASCENT_SPEED = 1.2D;
    /** Safety cap so a blocked ascent still delivers instead of ascending forever. */
    private static final int MAX_LAUNCH_TICKS = 20 * 60;

    protected SimpleContainer inventory;

    private boolean launching = false;
    private int launchTicks = 0;
    private BlockPos launchOrigin = null;

    public CargoRocketEntity(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    protected int getInventorySize() {
        return INVENTORY_SIZE;
    }

    protected void createInventory() {
        SimpleContainer old = this.inventory;
        this.inventory = new SimpleContainer(this.getInventorySize());
        if (old != null) {
            old.removeListener(this);
            int size = Math.min(old.getContainerSize(), this.inventory.getContainerSize());
            for (int i = 0; i < size; ++i) {
                ItemStack stack = old.getItem(i);
                if (!stack.isEmpty()) {
                    this.inventory.setItem(i, stack.copy());
                }
            }
        }
        this.inventory.addListener(this);
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        createInventory();
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag nbt) {
        this.launching = nbt.getBoolean("Launching");
        this.launchTicks = nbt.getInt("LaunchTicks");
        this.launchOrigin = nbt.contains("LaunchOrigin") ? BlockPos.of(nbt.getLong("LaunchOrigin")) : null;
        createInventory();
        if (nbt.contains("Inventory")) {
            this.inventory.fromTag(nbt.getList("Inventory", Tag.TAG_COMPOUND), this.registryAccess());
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag nbt) {
        nbt.putBoolean("Launching", this.launching);
        nbt.putInt("LaunchTicks", this.launchTicks);
        if (this.launchOrigin != null) {
            nbt.putLong("LaunchOrigin", this.launchOrigin.asLong());
        }
        if (this.inventory != null) {
            nbt.put("Inventory", this.inventory.createTag(this.registryAccess()));
        }
    }

    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    @Override
    public boolean isPickable() {
        return !this.isRemoved();
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (!this.level().isClientSide) {
            // Sneak-right-click ignites the launch; a plain right-click opens the cargo GUI.
            if (player.isSecondaryUseActive()) {
                ignite();
                return InteractionResult.CONSUME;
            }
            player.openMenu(this);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Begins the launch sequence if not already ascending. Records the launch
     * origin so the (stubbed) delivery can drop cargo back at the pad.
     */
    public void ignite() {
        if (this.launching) {
            return;
        }
        this.launching = true;
        this.launchTicks = 0;
        this.launchOrigin = this.blockPosition();
        this.ejectPassengers();
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.NEUTRAL, 1.0F, 1.0F);
    }

    @Override
    public void tick() {
        super.tick();

        if (this.level().isClientSide || !(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        if (!this.launching) {
            return;
        }

        this.launchTicks++;

        // Ramp thrust so the ascent starts gently and accelerates.
        double ascentSpeed = Math.min(MAX_ASCENT_SPEED, 0.05D + this.launchTicks * 0.01D);
        this.setDeltaMovement(new Vec3(0.0D, ascentSpeed, 0.0D));
        this.move(MoverType.SELF, this.getDeltaMovement());

        serverLevel.sendParticles(ParticleTypes.FLAME, this.getX(), this.getY(), this.getZ(),
                6, 0.2D, 0.0D, 0.2D, 0.01D);
        serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY(), this.getZ(),
                4, 0.2D, 0.0D, 0.2D, 0.01D);

        int baseY = this.launchOrigin != null ? this.launchOrigin.getY() : (int) this.getY();
        if (this.getY() >= baseY + TRAVEL_HEIGHT || this.launchTicks >= MAX_LAUNCH_TICKS) {
            deliverCargo(serverLevel);
        }
    }

    /**
     * Delivers the cargo. Targeting is stubbed, so the cargo is dropped at the
     * launch origin (ParaChest-style) and the rocket is discarded.
     */
    private void deliverCargo(ServerLevel level) {
        BlockPos dropPos = this.launchOrigin != null ? this.launchOrigin : this.blockPosition();
        Containers.dropContents(level, dropPos, this.inventory);
        this.remove(RemovalReason.DISCARDED);
    }

    @Override
    public SlotAccess getSlot(int mappedIndex) {
        return SlotAccess.forContainer(this.inventory, mappedIndex);
    }

    @Override
    public Container getVehicleInventory() {
        return this.inventory;
    }

    @Override
    public Integer getScreenOpeningData(ServerPlayer player) {
        return this.getId();
    }

    @Override
    public AbstractContainerMenu createMenu(int syncId, Inventory playerInventory, Player player) {
        return new VehicleInventoryMenu(syncId, playerInventory, this.inventory);
    }

    @Override
    public void containerChanged(Container sender) {
    }
}
