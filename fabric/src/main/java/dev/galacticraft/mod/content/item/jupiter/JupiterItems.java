package dev.galacticraft.mod.content.item.jupiter;

import dev.galacticraft.mod.content.item.GCItems;
import net.minecraft.world.item.Item;
import java.util.LinkedHashMap;
import java.util.Map;

public final class JupiterItems {
    public static final Map<Item, String> NAMES = new LinkedHashMap<>();
    public static final Item ATMOSPHERIC_ENTRY_UPGRADE = add("atmospheric_entry_upgrade", "Atmospheric Entry Shield", new Item(new Item.Properties().stacksTo(1)));
    public static final Item CLOUD_CONDENSATE = material("cloud_condensate", "Cloud Condensate");
    public static final Item HYDROSULFIDE_CRYSTAL = material("hydrosulfide_crystal", "Hydrosulfide Crystal");
    public static final Item AEROSTAT_SALVAGE = material("aerostat_salvage", "Aerostat Salvage");
    public static final Item CHARGED_CONDENSATE = material("charged_condensate", "Charged Condensate");
    public static final Item JOVIAN_COMPOSITE_PLATE = material("jovian_composite_plate", "Jovian Composite Plate");
    public static final Item STORMHEART_CORE = material("stormheart_core", "Stormheart Core");
    public static final PressureControllerItem PRESSURE_CONTROLLER_MK1 = add("pressure_controller_mk1", "Pressure Field Controller Mk I", new PressureControllerItem(4, 24000));
    public static final PressureControllerItem PRESSURE_CONTROLLER_MK2 = add("pressure_controller_mk2", "Pressure Field Controller Mk II", new PressureControllerItem(24, 96000));

    private static Item material(String id, String name) { return add(id, name, new Item(new Item.Properties())); }
    private static <T extends Item> T add(String id, String name, T item) {
        T result = GCItems.ITEMS.register(id, item);
        NAMES.put(result, name);
        return result;
    }
    public static final CloudDiverItem CLOUD_DIVER = add("cloud_diver", "Cloud Diver", new CloudDiverItem());
    public static void register() { }
}