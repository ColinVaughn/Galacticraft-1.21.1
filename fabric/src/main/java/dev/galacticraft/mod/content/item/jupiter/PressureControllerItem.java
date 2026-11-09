package dev.galacticraft.mod.content.item.jupiter;

import dev.galacticraft.api.item.Accessory;
import dev.galacticraft.mod.content.item.BatteryItem;
import dev.galacticraft.mod.world.dimension.jupiter.JupiterPressure;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import java.util.List;

public final class PressureControllerItem extends BatteryItem implements Accessory {
    private final int rating;

    public PressureControllerItem(int rating, long capacity) {
        super(new Properties().stacksTo(1), capacity, 500);
        this.rating = rating;
    }

    public int rating() { return rating; }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                JupiterPressure.emergencyAscent(serverPlayer, stack);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        Container accessories = player.galacticraft$getAccessories();
        for (int slot = 0; slot < accessories.getContainerSize(); slot++) {
            if (accessories.getItem(slot).isEmpty()) {
                if (!level.isClientSide) {
                    accessories.setItem(slot, stack.copyWithCount(1));
                    if (!player.isCreative()) stack.shrink(1);
                    accessories.setChanged();
                }
                return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
            }
        }
        return InteractionResultHolder.fail(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flags) {
        super.appendHoverText(stack, context, tooltip, flags);
        tooltip.add(Component.translatable("tooltip.galacticraft.pressure_rating", rating));
        tooltip.add(Component.translatable("tooltip.galacticraft.pressure_controller"));
    }
}