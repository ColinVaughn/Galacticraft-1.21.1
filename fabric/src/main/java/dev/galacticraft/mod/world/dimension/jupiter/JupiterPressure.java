package dev.galacticraft.mod.world.dimension.jupiter;

import dev.galacticraft.api.universe.celestialbody.CelestialBodyTags;
import dev.architectury.networking.NetworkManager;
import dev.galacticraft.mod.content.entity.damage.GCDamageTypes;
import dev.galacticraft.mod.content.item.jupiter.PressureControllerItem;
import dev.galacticraft.mod.network.s2c.JupiterPressurePayload;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import java.util.Map;
import java.util.WeakHashMap;

public final class JupiterPressure {
    private static final Map<ServerPlayer, Exposure> EXPOSURE = new WeakHashMap<>();
    private static final int GRACE_TICKS = 100;
    private static final String ASCENT_READY = "jupiter_ascent_ready";
    private static final class Exposure { int unsafeTicks, ascentTicks; float ascentRating; }
    private JupiterPressure() { }

    public static ItemStack controller(Container accessories) {
        ItemStack best = ItemStack.EMPTY;
        for (int i = 0; i < accessories.getContainerSize(); i++) {
            ItemStack stack = accessories.getItem(i);
            if (stack.getItem() instanceof PressureControllerItem item && item.getStoredEnergy(stack) > 0
                    && (best.isEmpty() || item.rating() > ((PressureControllerItem) best.getItem()).rating())) best = stack;
        }
        return best;
    }

    public static void tick(ServerPlayer player) {
        if (!CelestialBodyTags.is(player.level(), CelestialBodyTags.GAS_GIANT) || !player.isAlive() || player.isCreative() || player.isSpectator()) {
            EXPOSURE.remove(player);
            return;
        }
        Exposure state = EXPOSURE.computeIfAbsent(player, ignored -> new Exposure());
        float pressure = JupiterEnvironment.pressure(player.getY());
        Container accessories = player.galacticraft$getAccessories();
        for (int i = 0; i < accessories.getContainerSize(); i++) armAscent(accessories.getItem(i));
        ItemStack stack = controller(accessories);
        PressureControllerItem item = stack.getItem() instanceof PressureControllerItem controller ? controller : null;
        float rating = item == null ? 1 : item.rating();
        if (state.ascentTicks > 0) rating = Math.max(rating, state.ascentRating);
        float cabinRating = player.getVehicle() instanceof dev.galacticraft.mod.content.entity.vehicle.CloudDiverEntity diver && diver.hull() > 0 ? diver.pressureRating() : 1;
        // A sealed, powered Pressure Stabilizer habitat shelters anyone standing inside it, without draining their controller.
        cabinRating = Math.max(cabinRating, PressureHabitats.ratingAt(player.level(), player.blockPosition()));
        rating = Math.max(rating, cabinRating);
        boolean safe = pressure <= rating + 0.001F;
        if (safe) state.unsafeTicks = 0;
        else if (++state.unsafeTicks >= GRACE_TICKS && state.unsafeTicks % 20 == 0) {
            player.hurt(player.damageSources().source(GCDamageTypes.PRESSURE), Math.min(12, 1 + (pressure - rating) / 3));
        }
        if (state.ascentTicks > 0 && !player.isPassenger()) {
            state.ascentTicks--;
            if (player.verticalCollision && !player.onGround() && player.getDeltaMovement().y <= 0) state.ascentTicks = 0;
            else {
                player.setDeltaMovement(player.getDeltaMovement().x * 0.8, 0.8, player.getDeltaMovement().z * 0.8);
                player.hurtMarked = true;
                player.fallDistance = 0;
                if (player.getY() >= 240) state.ascentTicks = 0;
            }
        }
        if (player.tickCount % 20 != 0) return;
        if (item != null && pressure > cabinRating) {
            long drain = 8 + (long) Math.ceil(pressure * 4);
            item.setStoredEnergy(stack, Math.max(0, item.getStoredEnergy(stack) - drain));
            accessories.setChanged();
        }
        long energy = item == null ? 0 : item.getStoredEnergy(stack);
        long capacity = item == null ? 1 : item.getEnergyCapacity(stack);
        NetworkManager.sendToPlayer(player, new JupiterPressurePayload(pressure, rating, (float) energy / capacity, state.unsafeTicks));
        if (!safe) player.displayClientMessage(Component.translatable("message.galacticraft.jupiter.overpressure", String.format(java.util.Locale.ROOT, "%.1f", pressure), (int) rating), true);
        else if (item != null && energy < capacity / 10) player.displayClientMessage(Component.translatable("message.galacticraft.jupiter.low_charge"), true);
    }

    public static void armAscent(ItemStack stack) {
        if (stack.getItem() instanceof PressureControllerItem item && item.getStoredEnergy(stack) == item.getEnergyCapacity(stack)) {
            if (!stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getBoolean(ASCENT_READY))
                CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putBoolean(ASCENT_READY, true));
        }
    }

    public static void emergencyAscent(ServerPlayer player, ItemStack stack) {
        armAscent(stack);
        if (!CelestialBodyTags.is(player.level(), CelestialBodyTags.GAS_GIANT) || player.isPassenger()
                || !(stack.getItem() instanceof PressureControllerItem item) || item.getStoredEnergy(stack) < 4000
                || !stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getBoolean(ASCENT_READY)) {
            player.displayClientMessage(Component.translatable("message.galacticraft.jupiter.ascent_unavailable"), true);
            return;
        }
        item.setStoredEnergy(stack, item.getStoredEnergy(stack) - 4000);
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putBoolean(ASCENT_READY, false));
        Exposure state = EXPOSURE.computeIfAbsent(player, ignored -> new Exposure());
        state.ascentTicks = 400;
        state.ascentRating = item.rating();
        player.displayClientMessage(Component.translatable("message.galacticraft.jupiter.ascent"), true);
    }
}