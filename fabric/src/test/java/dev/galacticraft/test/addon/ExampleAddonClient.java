package dev.galacticraft.test.addon;

import dev.galacticraft.api.entity.rocket.render.RocketPartRenderer;
import dev.galacticraft.api.entity.rocket.render.RocketPartRendererRegistry;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class ExampleAddonClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        RocketPartRendererRegistry.INSTANCE.register(ExampleAddon.FUEL_POD, RocketPartRenderer.item(new ItemStack(Items.BUCKET)));
    }
}