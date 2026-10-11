package dev.galacticraft.mod.world.gen.feature;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.galacticraft.machinelib.api.component.MLDataComponents;
import dev.galacticraft.mod.content.GCLootTables;
import dev.galacticraft.mod.content.item.BatteryItem;
import dev.galacticraft.mod.content.item.OxygenTankItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

import java.util.List;

/** Survival loot respects configured capacities and both loaders' storage implementations. */
public final class ExpeditionSupplyFunction extends LootItemConditionalFunction {
    public static final MapCodec<ExpeditionSupplyFunction> CODEC = RecordCodecBuilder.mapCodec(instance ->
            commonFields(instance).apply(instance, ExpeditionSupplyFunction::new));

    public ExpeditionSupplyFunction(List<LootItemCondition> conditions) { super(conditions); }

    @Override public LootItemFunctionType<ExpeditionSupplyFunction> getType() { return GCLootTables.EXPEDITION_SUPPLY; }

    @Override protected ItemStack run(ItemStack stack, LootContext context) {
        fill(stack, 0.4 + context.getRandom().nextDouble() * 0.35);
        return stack;
    }

    public static void fill(ItemStack stack, double fraction) {
        fraction = Math.max(0, Math.min(1, fraction));
        if (stack.getItem() instanceof OxygenTankItem tank) {
            stack.set(MLDataComponents.AMOUNT.get(), (long) (tank.capacity * fraction));
        } else if (stack.getItem() instanceof BatteryItem battery) {
            battery.setStoredEnergy(stack, (long) (battery.getEnergyCapacity(stack) * fraction));
        }
    }
}
