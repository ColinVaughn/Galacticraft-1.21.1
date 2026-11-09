package dev.galacticraft.mod.content.teleporters;

import dev.galacticraft.api.universe.celestialbody.CelestialBody;
import dev.galacticraft.api.universe.celestialbody.landable.teleporter.type.CelestialTeleporterType;
import dev.galacticraft.impl.universe.celestialbody.landable.teleporter.config.DefaultCelestialTeleporterConfig;
import dev.galacticraft.mod.content.item.jupiter.JupiterItems;
import dev.galacticraft.mod.world.dimension.jupiter.JupiterLanding;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

public final class JupiterCelestialTeleporterType extends CelestialTeleporterType<DefaultCelestialTeleporterConfig> {
    public static final JupiterCelestialTeleporterType INSTANCE = new JupiterCelestialTeleporterType();
    private JupiterCelestialTeleporterType() { super(DefaultCelestialTeleporterConfig.CODEC); }

    public static boolean hasEntryShield(ServerPlayer player) {
        return player.isCreative() || player.getInventory().contains(new ItemStack(JupiterItems.ATMOSPHERIC_ENTRY_UPGRADE));
    }

    @Override
    public void onEnterAtmosphere(ServerLevel level, Entity entity, CelestialBody<?, ?> body, CelestialBody<?, ?> fromBody, DefaultCelestialTeleporterConfig config) {
        if (entity instanceof ServerPlayer player && !hasEntryShield(player)) {
            player.sendSystemMessage(Component.translatable("message.galacticraft.jupiter.entry_shield"));
            return;
        }
        var landing = JupiterLanding.prepare(level, entity.getX(), entity.getZ());
        if (landing.isEmpty()) {
            if (entity instanceof ServerPlayer player) player.sendSystemMessage(Component.translatable("message.galacticraft.jupiter.no_landing"));
            return;
        }
        var pos = landing.get();
        double y = Math.min(308, pos.getY() + 18);
        entity.teleportTo(level, pos.getX() + 0.5, y, pos.getZ() + 0.5, NO_RELATIVE_MOVEMENT, -90, 0);
        if (entity instanceof ServerPlayer player) {
            DestinationArrival.fromStoredRocket(player, player.position());
        }
    }
}