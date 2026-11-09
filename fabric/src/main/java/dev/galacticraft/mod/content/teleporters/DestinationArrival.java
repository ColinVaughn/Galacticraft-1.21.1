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

package dev.galacticraft.mod.content.teleporters;

import dev.galacticraft.mod.attachments.GCServerPlayer;
import dev.galacticraft.mod.content.GCEntityTypes;
import dev.galacticraft.mod.content.GCBlocks;
import dev.galacticraft.mod.content.entity.ParachestEntity;
import dev.galacticraft.mod.content.entity.vehicle.LanderEntity;
import dev.galacticraft.mod.content.item.ParachuteItem;
import dev.galacticraft.mod.world.dimension.GCDimensions;
import net.minecraft.core.NonNullList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/** Both the planet menu and physical spaceflight use the same destination vehicles and cargo ownership. */
public final class DestinationArrival {
    private DestinationArrival() { }

    public static int landingSurface(ServerLevel level, int x, int z) {
        int height = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (level.dimension().equals(GCDimensions.ASTEROID) && height <= level.getMinBuildHeight()) {
            // Sparse asteroid worlds can have no surface at all; GC4 supplied a small fallback platform.
            height = Math.clamp(128, level.getMinBuildHeight() + 1, level.getMaxBuildHeight() - 1);
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                var pos = new BlockPos(x + dx, height - 1, z + dz);
                if (level.isEmptyBlock(pos)) level.setBlockAndUpdate(pos, GCBlocks.ASTEROID_ROCK.defaultBlockState());
            }
        }
        return height;
    }

    public static Vec3 crewOffset(int seat) { return new Vec3((seat % 3) * 4, 0, (seat / 3) * 4); }

    public static Entity fromStoredRocket(ServerPlayer player, Vec3 position) {
        var stored = GCServerPlayer.get(player);
        Entity arrival = spawn(player, position, stored.getRocketStacks(), stored.getFuel());
        stored.getRocketStacks().clear();
        stored.setFuel(0);
        return arrival;
    }

    public static Entity spawn(ServerPlayer player, Vec3 position, NonNullList<ItemStack> cargo, long fuel) {
        var level = player.serverLevel();
        var destination = level.dimension();
        if (destination.equals(Level.OVERWORLD)) {
            DyeColor color = DyeColor.WHITE;
            boolean equippedParachute = false;
            for (int slot = 0; slot < player.galacticraft$getGearInv().getContainerSize(); slot++) {
                if (player.galacticraft$getGearInv().getItem(slot).getItem() instanceof ParachuteItem chute) {
                    equippedParachute = true;
                    if (chute.getColor() != null) color = chute.getColor();
                    break;
                }
            }
            if (fuel > 0 || cargo.stream().anyMatch(stack -> !stack.isEmpty())) {
                var chest = new ParachestEntity(GCEntityTypes.PARACHEST, level, cargo, color, fuel);
                chest.setPos(position.add(3, -2, 0));
                if (!level.addFreshEntity(chest)) throw new IllegalStateException("Could not spawn arrival parachest");
            }
            player.stopRiding();
            player.teleportTo(position.x, position.y, position.z);
            player.fallDistance = 0;
            player.galacticraft$setParachuteOpen(equippedParachute);
            player.setDeltaMovement(0, -.25, 0);
            player.hurtMarked = true;
            return player;
        }
        LanderEntity lander = destination.equals(GCDimensions.MARS) ? GCEntityTypes.LANDING_BALLOONS.create(level)
                : destination.equals(GCDimensions.ASTEROID) ? GCEntityTypes.ENTRY_POD.create(level)
                : destination.equals(GCDimensions.VENUS) ? GCEntityTypes.VENUS_ENTRY_POD.create(level)
                : new LanderEntity(GCEntityTypes.LANDER, level);
        lander.loadCargo(cargo, fuel);
        lander.moveTo(position.x, position.y, position.z, player.getYRot(), 0);
        lander.startDescent();
        if (!level.addFreshEntity(lander)) throw new IllegalStateException("Could not spawn arrival lander");
        player.startRiding(lander, true);
        return lander;
    }
}