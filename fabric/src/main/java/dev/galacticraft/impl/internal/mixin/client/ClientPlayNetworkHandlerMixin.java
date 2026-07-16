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

package dev.galacticraft.impl.internal.mixin.client;

import dev.galacticraft.api.accessor.GearInventoryProvider;
import dev.galacticraft.api.client.accessor.ClientSatelliteAccessor;
import dev.galacticraft.api.registry.AddonRegistries;
import dev.galacticraft.api.universe.celestialbody.CelestialBody;
import dev.galacticraft.dynamicdimensions.impl.registry.RegistryUtil;
import dev.galacticraft.impl.universe.celestialbody.type.SatelliteType;
import dev.galacticraft.impl.universe.position.config.SatelliteConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Environment(EnvType.CLIENT)
@Mixin(ClientPacketListener.class)
public abstract class ClientPlayNetworkHandlerMixin implements ClientSatelliteAccessor {
    private final @Unique Map<ResourceLocation, CelestialBody<SatelliteConfig, SatelliteType>> satellites = new HashMap<>();
    private final @Unique List<SatelliteListener> listeners = new ArrayList<>();
    private @Unique ItemStack[] galacticraft$gearBeforeDimensionChange = new ItemStack[0];

    @Shadow
    public abstract RegistryAccess.Frozen registryAccess();

    @Inject(method = "handleRespawn", at = @At("HEAD"))
    private void galacticraft$captureGearBeforeDimensionChange(ClientboundRespawnPacket packet, CallbackInfo ci) {
        // Packet handlers enter once on the network thread before being rescheduled onto the client thread.
        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.isSameThread()) return;

        LocalPlayer player = minecraft.player;
        if (player == null || player.level().dimension().equals(packet.commonPlayerSpawnInfo().dimension())) {
            this.galacticraft$gearBeforeDimensionChange = new ItemStack[0];
            return;
        }

        Container gear = ((GearInventoryProvider) player).galacticraft$getGearInv();
        this.galacticraft$gearBeforeDimensionChange = new ItemStack[gear.getContainerSize()];
        for (int slot = 0; slot < gear.getContainerSize(); slot++) {
            this.galacticraft$gearBeforeDimensionChange[slot] = gear.getItem(slot).copy();
        }
    }

    @Inject(method = "handleRespawn", at = @At("RETURN"))
    private void galacticraft$restoreGearAfterDimensionChange(ClientboundRespawnPacket packet, CallbackInfo ci) {
        ItemStack[] gearBeforeDimensionChange = this.galacticraft$gearBeforeDimensionChange;
        this.galacticraft$gearBeforeDimensionChange = new ItemStack[0];

        LocalPlayer player = Minecraft.getInstance().player;
        if (gearBeforeDimensionChange.length == 0 || player == null) return;

        Container gear = ((GearInventoryProvider) player).galacticraft$getGearInv();
        for (int slot = 0; slot < Math.min(gear.getContainerSize(), gearBeforeDimensionChange.length); slot++) {
            gear.setItem(slot, gearBeforeDimensionChange[slot]);
        }
    }

    @Override
    public Map<ResourceLocation, CelestialBody<SatelliteConfig, SatelliteType>> galacticraft$getSatellites() {
        return this.satellites;
    }

    @Override
    public void galacticraft$addSatellite(CelestialBody<SatelliteConfig, SatelliteType> satellite, boolean newlyCreated) {
        ResourceLocation id = satellite.config().getId();
        CelestialBody<SatelliteConfig, SatelliteType> previous = this.satellites.put(id, satellite);
        var registry = this.registryAccess().registryOrThrow(AddonRegistries.CELESTIAL_BODY);
        if (newlyCreated && !registry.containsKey(id)) {
            RegistryUtil.registerUnfreeze(registry, id, satellite);
        }
        for (SatelliteListener listener : this.listeners) {
            if (previous != null) {
                listener.onSatelliteUpdated(previous, false);
            }
            listener.onSatelliteUpdated(satellite, true);
        }
    }

    @Override
    public void galacticraft$removeSatellite(ResourceLocation id) {
        CelestialBody<SatelliteConfig, SatelliteType> removed = this.satellites.remove(id);
        RegistryUtil.unregister(this.registryAccess().registryOrThrow(AddonRegistries.CELESTIAL_BODY), id);
        for (SatelliteListener listener : this.listeners) {
            listener.onSatelliteUpdated(removed, false);
        }
    }

    @Override
    public void galacticraft$updateSatellite(CelestialBody<SatelliteConfig, SatelliteType> satellite) {
        this.galacticraft$removeSatellite(satellite.config().getId());
        this.galacticraft$addSatellite(satellite, true);
    }

    @Override
    public void addListener(SatelliteListener listener) {
        this.listeners.add(listener);
    }

    @Override
    public void removeListener(SatelliteListener listener) {
        this.listeners.remove(listener);
    }
}
