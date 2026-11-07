/*
 * Copyright (c) 2019-2026 Team Galacticraft
 * Copyright (c) 2026 Colin Vaughn
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

package dev.galacticraft.mod.content.block.jupiter.encounter;

import dev.galacticraft.mod.content.GCBlockEntityTypes;
import dev.galacticraft.mod.content.block.jupiter.JupiterAnimated;
import dev.galacticraft.mod.content.block.jupiter.JupiterText;
import dev.galacticraft.mod.content.item.jupiter.JupiterItems;
import dev.galacticraft.mod.screen.jupiter.StormheartPedestalMenu;
import dev.galacticraft.mod.world.dimension.jupiter.JupiterStorms;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Coordinates the Stormheart encounter: relays shield the Leviathan until disabled; participants
 * claim a core after its defeat. Resets on request, after all rewards are claimed, or when abandoned.
 */
public class StormheartPedestalBlockEntity extends BlockEntity implements MenuProvider, JupiterAnimated {
    public static final int LINK_RANGE = 32;
    public static final int ARENA_RANGE = 48;
    public static final int ABANDON_TICKS = 600;
    public static final int STRIKE_INTERVAL = 100;
    public static final int BUTTON_BEGIN = 0, BUTTON_RESET = 1, BUTTON_CLAIM = 2;
    private final List<BlockPos> relays = new ArrayList<>();
    private final Set<UUID> participants = new HashSet<>();
    private final Set<UUID> claimed = new HashSet<>();
    private int emptyTicks;
    /** The encounter's Leviathan; the core cannot be claimed until it has been slain. Null for legacy encounters. */
    private @Nullable UUID leviathan;
    private boolean leviathanSlain;
    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> StormheartPedestalBlockEntity.this.phase().ordinal();
                case 1 -> StormheartPedestalBlockEntity.this.relays.size();
                case 2 -> StormheartPedestalBlockEntity.this.disabledRelays();
                case 3 -> StormheartPedestalBlockEntity.this.participants.size();
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) { }

        @Override
        public int getCount() {
            return StormheartPedestalMenu.DATA_COUNT;
        }
    };

    public StormheartPedestalBlockEntity(BlockPos pos, BlockState state) {
        super(GCBlockEntityTypes.STORMHEART_PEDESTAL, pos, state);
    }

    public StormheartPedestalBlock.Phase phase() {
        return this.getBlockState().getValue(StormheartPedestalBlock.PHASE);
    }

    public List<BlockPos> relays() {
        return List.copyOf(this.relays);
    }

    public Set<UUID> participants() {
        return Set.copyOf(this.participants);
    }

    private void setPhase(StormheartPedestalBlock.Phase phase) {
        if (this.level != null && this.phase() != phase) this.level.setBlockAndUpdate(this.worldPosition, this.getBlockState().setValue(StormheartPedestalBlock.PHASE, phase));
        this.setChanged();
    }

    /** Starts the encounter; returns false if there was nothing to start. */
    public boolean begin(@Nullable Player starter) {
        if (!(this.level instanceof ServerLevel level) || this.phase() != StormheartPedestalBlock.Phase.IDLE) return false;
        this.relays.clear();
        this.relays.addAll(findRelays(level, this.worldPosition));
        if (this.relays.isEmpty()) {
            if (starter != null) starter.displayClientMessage(Component.translatable(JupiterText.NO_RELAYS), true);
            return false;
        }
        for (BlockPos pos : this.relays) {
            if (level.getBlockEntity(pos) instanceof StormRelayBlockEntity relay) relay.link(this.worldPosition, StormRelayBlock.Mode.ENERGIZED);
        }
        this.participants.clear();
        this.claimed.clear();
        this.emptyTicks = 0;
        if (starter != null) this.participants.add(starter.getUUID());
        this.summonLeviathan(level);
        this.recruit(level);
        this.setPhase(StormheartPedestalBlock.Phase.ACTIVE);
        level.playSound(null, this.worldPosition, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 2, 0.6F);
        level.playSound(null, this.worldPosition, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 4, 0.8F);
        return true;
    }

    private void summonLeviathan(ServerLevel level) {
        var creature = dev.galacticraft.mod.content.GCEntityTypes.STORMHEART_LEVIATHAN.create(level);
        this.leviathanSlain = false;
        if (creature == null) {
            this.leviathan = null;
            return;
        }
        creature.bind(this.worldPosition);
        creature.moveTo(this.worldPosition.getX() + 0.5 + dev.galacticraft.mod.content.entity.jupiter.StormheartLeviathanEntity.APPROACH_RADIUS,
                this.worldPosition.getY() + dev.galacticraft.mod.content.entity.jupiter.StormheartLeviathanEntity.APPROACH_HEIGHT,
                this.worldPosition.getZ() + 0.5, 90, 0);
        level.addFreshEntity(creature);
        this.leviathan = creature.getUUID();
        level.playSound(null, this.worldPosition, SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.HOSTILE, 4, 0.5F);
    }

    /** The living Leviathan of this encounter, if it is loaded. */
    public @Nullable dev.galacticraft.mod.content.entity.jupiter.StormheartLeviathanEntity leviathan() {
        if (this.leviathan == null || !(this.level instanceof ServerLevel level)) return null;
        return level.getEntity(this.leviathan) instanceof dev.galacticraft.mod.content.entity.jupiter.StormheartLeviathanEntity creature
                && creature.isAlive() ? creature : null;
    }

    public boolean isLeviathanSlain() {
        return this.leviathan == null || this.leviathanSlain;
    }

    /** Called by the Leviathan as it dies. */
    public void leviathanSlain() {
        this.leviathanSlain = true;
        this.setChanged();
        if (this.level instanceof ServerLevel level) {
            this.broadcast(level, Component.translatable(JupiterText.LEVIATHAN_SLAIN));
            level.playSound(null, this.worldPosition, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.5F, 1.0F);
        }
    }

    /** Releases every relay and returns to dormant. */
    public void reset(boolean announce) {
        var creature = this.leviathan();
        if (creature != null) creature.discard();
        this.leviathan = null;
        this.leviathanSlain = false;
        if (this.level != null) {
            for (BlockPos pos : this.relays) {
                if (this.level.isLoaded(pos) && this.level.getBlockEntity(pos) instanceof StormRelayBlockEntity relay) relay.link(null, StormRelayBlock.Mode.IDLE);
            }
        }
        if (announce && this.level instanceof ServerLevel level) this.broadcast(level, Component.translatable(JupiterText.ENCOUNTER_RESET));
        this.relays.clear();
        this.participants.clear();
        this.claimed.clear();
        this.emptyTicks = 0;
        if (this.level != null && !this.isRemoved() && this.level.getBlockState(this.worldPosition).is(this.getBlockState().getBlock())) {
            this.setPhase(StormheartPedestalBlock.Phase.IDLE);
        }
    }

    /** Gives {@code player} their Stormheart Core, once per participant per encounter. */
    public void claim(Player player) {
        if (this.phase() != StormheartPedestalBlock.Phase.CORE_EXPOSED) return;
        if (!this.participants.contains(player.getUUID())) {
            player.displayClientMessage(Component.translatable(JupiterText.NOT_A_PARTICIPANT), true);
            return;
        }
        if (!this.isLeviathanSlain()) {
            player.displayClientMessage(Component.translatable(JupiterText.LEVIATHAN_GUARDS), true);
            return;
        }
        if (!this.claimed.add(player.getUUID())) {
            player.displayClientMessage(Component.translatable(JupiterText.CORE_ALREADY_CLAIMED), true);
            return;
        }
        ItemStack core = new ItemStack(JupiterItems.STORMHEART_CORE);
        if (!player.getInventory().add(core)) player.drop(core, false);
        player.displayClientMessage(Component.translatable(JupiterText.CORE_CLAIMED), true);
        this.setChanged();
        if (this.claimed.containsAll(this.participants)) this.reset(false);
    }

    /** Called by a linked relay as it shuts down. */
    void relayDisabled() {
        if (this.phase() != StormheartPedestalBlock.Phase.ACTIVE || !(this.level instanceof ServerLevel level)) return;
        if (this.disabledRelays() < this.relays.size()) return;
        this.setPhase(StormheartPedestalBlock.Phase.CORE_EXPOSED);
        level.playSound(null, this.worldPosition, SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 1.5F, 1.4F);
        this.broadcast(level, Component.translatable(JupiterText.CORE_EXPOSED));
    }

    public int disabledRelays() {
        if (this.level == null) return 0;
        int disabled = 0;
        for (BlockPos pos : this.relays) {
            if (!this.level.isLoaded(pos)) continue;
            BlockState state = this.level.getBlockState(pos);
            // A relay that was broken or replaced can no longer be disarmed, so it counts as down.
            if (!state.hasProperty(StormRelayBlock.MODE) || state.getValue(StormRelayBlock.MODE) == StormRelayBlock.Mode.DISABLED) disabled++;
        }
        return disabled;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, StormheartPedestalBlockEntity pedestal) {
        if (!(level instanceof ServerLevel server) || state.getValue(StormheartPedestalBlock.PHASE) == StormheartPedestalBlock.Phase.IDLE) return;
        List<ServerPlayer> present = pedestal.arenaPlayers(server);
        if (present.isEmpty()) {
            if (++pedestal.emptyTicks >= ABANDON_TICKS) pedestal.reset(true);
            return;
        }
        pedestal.emptyTicks = 0;
        if (state.getValue(StormheartPedestalBlock.PHASE) != StormheartPedestalBlock.Phase.ACTIVE) return;
        pedestal.recruit(server);
        if (level.getGameTime() % 20 == 0) pedestal.relayDisabled();
        if ((level.getGameTime() + (pos.asLong() & 31)) % STRIKE_INTERVAL == 0) {
            ServerPlayer target = present.get(server.random.nextInt(present.size()));
            Vec3 at = target.position().add(server.random.nextInt(7) - 3, 0, server.random.nextInt(7) - 3);
            JupiterStorms.strike(server, at);
        }
    }

    private void recruit(ServerLevel level) {
        for (ServerPlayer player : this.arenaPlayers(level)) {
            if (this.participants.add(player.getUUID())) this.setChanged();
        }
    }

    private List<ServerPlayer> arenaPlayers(ServerLevel level) {
        return level.getEntitiesOfClass(ServerPlayer.class, new AABB(this.worldPosition).inflate(ARENA_RANGE), player -> !player.isSpectator());
    }

    private void broadcast(ServerLevel level, Component message) {
        for (ServerPlayer player : this.arenaPlayers(level)) player.sendSystemMessage(message);
    }

    /** Every loaded Storm Relay within {@link #LINK_RANGE} blocks of {@code center}. */
    static List<BlockPos> findRelays(ServerLevel level, BlockPos center) {
        List<BlockPos> found = new ArrayList<>();
        int minX = SectionPos.blockToSectionCoord(center.getX() - LINK_RANGE), maxX = SectionPos.blockToSectionCoord(center.getX() + LINK_RANGE);
        int minZ = SectionPos.blockToSectionCoord(center.getZ() - LINK_RANGE), maxZ = SectionPos.blockToSectionCoord(center.getZ() + LINK_RANGE);
        for (int cx = minX; cx <= maxX; cx++) {
            for (int cz = minZ; cz <= maxZ; cz++) {
                var chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                for (BlockEntity entity : chunk.getBlockEntities().values()) {
                    if (entity instanceof StormRelayBlockEntity && entity.getBlockPos().closerThan(center, LINK_RANGE + 0.5)) found.add(entity.getBlockPos());
                }
            }
        }
        return found;
    }

    @Override
    public boolean isAnimating() {
        return this.phase() != StormheartPedestalBlock.Phase.IDLE;
    }

    @Override
    public Component getDisplayName() {
        return this.getBlockState().getBlock().getName();
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player player) {
        return new StormheartPedestalMenu(syncId, inventory, this, this.data);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ListTag relays = new ListTag();
        for (BlockPos pos : this.relays) relays.add(NbtUtils.writeBlockPos(pos));
        tag.put("Relays", relays);
        tag.put("Participants", uuids(this.participants));
        tag.put("Claimed", uuids(this.claimed));
        tag.putInt("EmptyTicks", this.emptyTicks);
        if (this.leviathan != null) tag.putUUID("Leviathan", this.leviathan);
        tag.putBoolean("LeviathanSlain", this.leviathanSlain);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.relays.clear();
        for (Tag entry : tag.getList("Relays", Tag.TAG_INT_ARRAY)) NbtUtils.readBlockPos(wrap(entry), "P").ifPresent(this.relays::add);
        readUuids(tag.getList("Participants", Tag.TAG_INT_ARRAY), this.participants);
        readUuids(tag.getList("Claimed", Tag.TAG_INT_ARRAY), this.claimed);
        this.emptyTicks = tag.getInt("EmptyTicks");
        this.leviathan = tag.hasUUID("Leviathan") ? tag.getUUID("Leviathan") : null;
        this.leviathanSlain = tag.getBoolean("LeviathanSlain");
    }

    private static CompoundTag wrap(Tag entry) {
        CompoundTag tag = new CompoundTag();
        tag.put("P", entry);
        return tag;
    }

    private static ListTag uuids(Set<UUID> ids) {
        ListTag list = new ListTag();
        for (UUID id : ids) list.add(NbtUtils.createUUID(id));
        return list;
    }

    private static void readUuids(ListTag list, Set<UUID> into) {
        into.clear();
        for (Tag entry : list) into.add(NbtUtils.loadUUID(entry));
    }
}
