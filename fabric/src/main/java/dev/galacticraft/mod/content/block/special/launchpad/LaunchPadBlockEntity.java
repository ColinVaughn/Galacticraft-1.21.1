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

package dev.galacticraft.mod.content.block.special.launchpad;

import dev.architectury.registry.menu.ExtendedMenuProvider;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.api.block.entity.FuelDock;
import dev.galacticraft.mod.api.entity.Dockable;
import dev.galacticraft.mod.content.GCBlockEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.function.IntFunction;
import dev.galacticraft.mod.screen.LaunchPadMenu;
import dev.galacticraft.mod.util.Translations;

public class LaunchPadBlockEntity extends BlockEntity implements FuelDock, ExtendedMenuProvider {
    private UUID entityUUID = null;
    private @Nullable Dockable docked;
    private Type type = Type.ROCKET;
    private int address = -1;
    private int destinationAddress = -1;

    public LaunchPadBlockEntity(BlockPos pos, BlockState state, Type type) {
        super(GCBlockEntityTypes.LAUNCH_PAD, pos, state);
        this.type = type;
    }

    public LaunchPadBlockEntity(BlockPos pos, BlockState state) {
        super(GCBlockEntityTypes.LAUNCH_PAD, pos, state);
    }

    public void setDockedEntity(@Nullable Dockable dockable) {
        if (dockable == null) {
            this.entityUUID = null;
            this.docked = null;
        } else {
            this.entityUUID = dockable.asEntity().getUUID();
            this.docked = dockable;
        }
        this.setChanged();
        if (this.level instanceof ServerLevel serverLevel) {
            serverLevel.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }

    @Override
    public BlockPos getDockPos() {
        return this.getBlockPos();
    }

    public Dockable getDockedEntity() {
        if (this.entityUUID != null) {
            if (this.docked == null && this.level instanceof ServerLevel) {
                var entity = ((ServerLevel) this.level).getEntity(this.entityUUID);
                if (entity instanceof Dockable dockable) {
                    this.docked = dockable;
                    if (dockable.getLandingPad() != this && dockable.isDockValid(this)) {
                        dockable.setPad(this);
                    }
                } else if (entity != null) {
                    this.entityUUID = null;
                    this.setChanged();
                }
            }
        } else {
            this.docked = null;
        }
        return this.docked;
    }

    public boolean hasDockedEntity() {
        return this.getDockedEntity() != null;
    }

    @Override
    protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        super.loadAdditional(nbt, registryLookup);
        this.entityUUID = null;
        this.docked = null;
        this.type = Type.byName(nbt.getString("Type"));
        this.address = nbt.contains("Address") ? nbt.getInt("Address") : -1;
        this.destinationAddress = nbt.contains("DestinationAddress") ? nbt.getInt("DestinationAddress") : -1;

        if (nbt.hasUUID(Constant.Nbt.DOCKED_UUID)) {
            this.entityUUID = nbt.getUUID(Constant.Nbt.DOCKED_UUID);
            if (this.level instanceof ServerLevel serverLevel
                    && serverLevel.getEntity(this.entityUUID) instanceof Dockable dockable) {
                this.docked = dockable;
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        super.saveAdditional(nbt, registryLookup);
        if (this.entityUUID != null) nbt.putUUID(Constant.Nbt.DOCKED_UUID, this.entityUUID);
        nbt.putString("Type", type.getSerializedName());
        nbt.putInt("Address", this.address);
        nbt.putInt("DestinationAddress", this.destinationAddress);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registryLookup) {
        return this.saveWithoutMetadata(registryLookup);
    }

    public UUID getDockedUUID() {
        return this.entityUUID;
    }

    public Type getPadType() {
        return this.type;
    }

    public int getAddress() {
        return this.address;
    }

    public int getDestinationAddress() {
        return this.destinationAddress;
    }

    public boolean setRoute(int address, int destinationAddress) {
        if (!(this.level instanceof ServerLevel serverLevel) || this.type != Type.ROCKET
                || !(this.getBlockState().getBlock() instanceof AbstractLaunchPad)
                || this.getBlockState().getValue(AbstractLaunchPad.PART) != AbstractLaunchPad.Part.CENTER
                || !CargoPadRegistry.isValidAddress(address)
                || destinationAddress < -1 || destinationAddress > CargoPadRegistry.MAX_ADDRESS
                || address == destinationAddress) {
            return false;
        }

        CargoPadRegistry registry = CargoPadRegistry.get(serverLevel.getServer());
        CargoPadRegistry.PadTarget target = CargoPadRegistry.PadTarget.of(serverLevel, this.worldPosition);
        if (!registry.assign(serverLevel.getServer(), address, target)) return false;

        this.address = address;
        this.destinationAddress = destinationAddress;
        this.setChanged();
        serverLevel.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        return true;
    }

    public boolean hasValidDestination() {
        return this.level instanceof ServerLevel serverLevel
                && CargoPadRegistry.get(serverLevel.getServer())
                .contains(this.destinationAddress);
    }

    public void unregisterAddress() {
        if (this.level instanceof ServerLevel serverLevel) {
            CargoPadRegistry.get(serverLevel.getServer()).unregister(
                    CargoPadRegistry.PadTarget.of(serverLevel, this.worldPosition));
        }
    }

    public ContainerData createRouteData() {
        return new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case 0 -> address;
                    case 1 -> destinationAddress;
                    case 2 -> CargoPadRegistry.isValidAddress(address) ? 1 : 0;
                    case 3 -> hasValidDestination() ? 1 : 0;
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
                if (index == 0) address = value;
                if (index == 1) destinationAddress = value;
            }

            @Override
            public int getCount() {
                return LaunchPadMenu.DATA_COUNT;
            }
        };
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable(Translations.Ui.LAUNCH_PAD_ROUTING);
    }

    @Override
    public void saveExtraData(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.worldPosition);
    }

    @Override
    public AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player player) {
        return new LaunchPadMenu(syncId, inventory, this);
    }

    public enum Type implements StringRepresentable {
        ROCKET(0, "rocket"),
        FUEL(1, "fuel");

        public static final StringRepresentable.EnumCodec<Type> CODEC = StringRepresentable.fromEnum(Type::values);
        private static final IntFunction<Type> BY_ID = ByIdMap.continuous(Type::getId, values(), ByIdMap.OutOfBoundsStrategy.ZERO);

        private final int id;
        private final String name;

        Type(int id, String name) {
            this.id = id;
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }

        public int getId() {
            return this.id;
        }

        public static Type byName(String name) {
            return CODEC.byName(name, ROCKET);
        }

        public static Type byId(int id) {
            return BY_ID.apply(id);
        }
    }
}
