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

package dev.galacticraft.mod.screen;

import dev.galacticraft.mod.content.block.entity.machine.RadarBlockEntity;
import dev.galacticraft.mod.world.dimension.meteor.MeteoroidClass;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;

public class RadarMenu extends PowerPortMenu<RadarBlockEntity> {
    private static final int HEADER_DATA = 6;
    private static final int TRACK_DATA = 6;
    private static final int DATA_COUNT = HEADER_DATA + RadarBlockEntity.MAX_TRACKS * TRACK_DATA;

    private final BlockPos radarPos;
    private final ContainerData data;

    public RadarMenu(int syncId, Inventory inventory, BlockPos radarPos) {
        super(GCMenuTypes.RADAR, syncId, inventory, radarPos);
        this.radarPos = radarPos;
        this.data = new SimpleContainerData(DATA_COUNT);
        this.addDataSlots(this.data);
    }

    public RadarMenu(int syncId, Inventory inventory, RadarBlockEntity radar) {
        super(GCMenuTypes.RADAR, syncId, inventory.player, radar);
        this.radarPos = radar.getBlockPos();
        this.data = createData(radar);
        this.addDataSlots(this.data);
    }

    private static ContainerData createData(RadarBlockEntity radar) {
        return new ContainerData() {
            @Override
            public int get(int index) {
                if (index == 0) return radar.getTracks().size();
                if (index == 1) return radar.getLinkedRadarCount();
                if (index == 2) return RadarBlockEntity.DETECTION_RANGE;
                if (index == 3) return radar.getLinkedCannonCount();
                if (index == 4) return (int) Math.min(Integer.MAX_VALUE, radar.energyStorage().getAmount());
                if (index == 5) return (int) Math.min(Integer.MAX_VALUE, radar.energyStorage().getCapacity());
                int trackIndex = (index - HEADER_DATA) / TRACK_DATA;
                int field = (index - HEADER_DATA) % TRACK_DATA;
                if (trackIndex >= radar.getTracks().size()) return 0;
                RadarBlockEntity.MeteorTrack track = radar.getTracks().get(trackIndex);
                return switch (field) {
                    case 0 -> track.estimatedImpact().getX() - radar.getBlockPos().getX();
                    case 1 -> track.estimatedImpact().getZ() - radar.getBlockPos().getZ();
                    case 2 -> track.ticksToImpact();
                    case 3 -> track.uncertaintyRadius();
                    case 4 -> track.type().id();
                    case 5 -> track.size();
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
            }

            @Override
            public int getCount() {
                return DATA_COUNT;
            }
        };
    }

    public int getTrackCount() {
        return this.data.get(0);
    }

    public int getLinkedRadarCount() {
        return this.data.get(1);
    }

    public int getDetectionRange() {
        return Math.max(1, this.data.get(2));
    }

    public int getLinkedCannonCount() {
        return this.data.get(3);
    }

    public int getEnergy() {
        return this.data.get(4);
    }

    public int getEnergyCapacity() {
        return Math.max(1, this.data.get(5));
    }

    public boolean isPowered() {
        return this.getEnergy() >= RadarBlockEntity.ENERGY_PER_TICK
                && this.redstoneMode.isActive(this.state.isPowered());
    }

    public TrackDisplay getTrack(int index) {
        int start = HEADER_DATA + index * TRACK_DATA;
        return new TrackDisplay(this.radarPos.getX() + this.data.get(start),
                this.radarPos.getZ() + this.data.get(start + 1), this.data.get(start + 2),
                this.data.get(start + 3), MeteoroidClass.byId((byte) this.data.get(start + 4)),
                this.data.get(start + 5));
    }

    public BlockPos getRadarPos() {
        return this.radarPos;
    }

    public record TrackDisplay(int impactX, int impactZ, int ticksToImpact, int uncertaintyRadius,
                               MeteoroidClass type, int size) {
    }
}
