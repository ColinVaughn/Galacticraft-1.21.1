package dev.galacticraft.mod.content.block.jupiter;

import dev.galacticraft.mod.content.block.jupiter.encounter.StormRelayBlock;
import dev.galacticraft.mod.content.block.jupiter.encounter.StormheartPedestalBlock;

import dev.galacticraft.mod.content.GCBlocks;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.shapes.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class JupiterBlocks {
    public static final Map<Block, String> NAMES = new LinkedHashMap<>();
    /** Stairs and slabs, each mapped to the full block whose textures and recipe they derive from. */
    public static final Map<Block, Block> VARIANT_BASES = new LinkedHashMap<>();
    public static final Block AEROSTAT_HULL = add("aerostat_hull", "Aerostat Hull", new Block(metal(3)));
    public static final Block REINFORCED_AEROSTAT_HULL = add("reinforced_aerostat_hull", "Reinforced Aerostat Hull", new Block(metal(6).explosionResistance(60)));
    public static final Block AEROSTAT_GRATING = add("aerostat_grating", "Aerostat Grating", new Grating(metal(2).noOcclusion()));
    public static final Block PRESSURE_GLASS = add("pressure_glass", "Pressure Glass", new PressureGlass(metal(3).noOcclusion().isViewBlocking((s,l,p)->false)));
    public static final Block SEALED_HATCH = add("sealed_hatch", "Sealed Hatch", new Hatch(metal(3).noOcclusion()));
    /** Quilted gas bag that holds up wrecks and facilities; it softens a landing like a hay bale. */
    public static final Block BUOYANCY_CELL = add("buoyancy_cell", "Buoyancy Cell", new BuoyancyCell(BlockBehaviour.Properties.of()
            .mapColor(net.minecraft.world.level.material.MapColor.TERRACOTTA_ORANGE).strength(1.0F).sound(SoundType.WOOL)));
    public static final Block PACKED_CLOUD_STAIRS = stairs("packed_cloud_stairs", "Packed Cloud Stairs", GCBlocks.PACKED_CLOUD);
    public static final Block PACKED_CLOUD_SLAB = slab("packed_cloud_slab", "Packed Cloud Slab", GCBlocks.PACKED_CLOUD);
    public static final Block AEROSTAT_HULL_STAIRS = stairs("aerostat_hull_stairs", "Aerostat Hull Stairs", AEROSTAT_HULL);
    public static final Block AEROSTAT_HULL_SLAB = slab("aerostat_hull_slab", "Aerostat Hull Slab", AEROSTAT_HULL);
    public static final Block REINFORCED_AEROSTAT_HULL_STAIRS = stairs("reinforced_aerostat_hull_stairs", "Reinforced Aerostat Hull Stairs", REINFORCED_AEROSTAT_HULL);
    public static final Block REINFORCED_AEROSTAT_HULL_SLAB = slab("reinforced_aerostat_hull_slab", "Reinforced Aerostat Hull Slab", REINFORCED_AEROSTAT_HULL);
    public static final Block HYDROSULFIDE_CRYSTAL_CLUSTER = add("hydrosulfide_crystal_cluster", "Hydrosulfide Crystal Cluster", new Cluster(BlockBehaviour.Properties.of().strength(1).noOcclusion().sound(SoundType.AMETHYST_CLUSTER)));
    public static final Block CHARGED_CONDENSATE_CLUSTER = add("charged_condensate_cluster", "Charged Condensate Cluster", new Cluster(BlockBehaviour.Properties.of().strength(2).noOcclusion().lightLevel(s->7).sound(SoundType.AMETHYST_CLUSTER)));

    public static final Block ATMOSPHERIC_COLLECTOR = machine("atmospheric_collector", "Atmospheric Collector", JupiterMachineBlock.Kind.ATMOSPHERIC_COLLECTOR, 0);
    public static final Block CONDENSATE_SEPARATOR = machine("condensate_separator", "Condensate Separator", JupiterMachineBlock.Kind.CONDENSATE_SEPARATOR, 0);
    public static final Block LIGHTNING_GROUNDER = machine("lightning_grounder", "Lightning Grounder", JupiterMachineBlock.Kind.LIGHTNING_GROUNDER, 4);
    public static final Block PRESSURE_STABILIZER = machine("pressure_stabilizer", "Pressure Stabilizer", JupiterMachineBlock.Kind.PRESSURE_STABILIZER, 0);
    public static final Block STORM_CAPACITOR = machine("storm_capacitor", "Storm Capacitor", JupiterMachineBlock.Kind.STORM_CAPACITOR, 5);
    /** Encounter machinery: near-indestructible so an arena cannot be dismantled mid-fight. */
    public static final Block STORM_RELAY = add("storm_relay", "Storm Relay", new StormRelayBlock(metal(50).explosionResistance(1200).noOcclusion()
            .lightLevel(s -> switch (s.getValue(StormRelayBlock.MODE)) { case ENERGIZED -> 12; case IDLE -> 4; case DISABLED -> 1; })));
    public static final Block STORMHEART_PEDESTAL = add("stormheart_pedestal", "Stormheart Pedestal", new StormheartPedestalBlock(metal(50).explosionResistance(1200).noOcclusion()
            .lightLevel(s -> s.getValue(StormheartPedestalBlock.PHASE) == StormheartPedestalBlock.Phase.IDLE ? 6 : 13)));
    public static final Block SURVEY_BEACON = machine("survey_beacon", "Survey Beacon", JupiterMachineBlock.Kind.SURVEY_BEACON, 6);
    public static final Block VEHICLE_DOCK = machine("vehicle_dock", "Vehicle Dock", JupiterMachineBlock.Kind.VEHICLE_DOCK, 3);
    /** Installations drawn and animated entirely by their Blockbench models. */
    public static final List<Block> MACHINES = List.of(ATMOSPHERIC_COLLECTOR, CONDENSATE_SEPARATOR, LIGHTNING_GROUNDER, PRESSURE_STABILIZER,
            STORM_CAPACITOR, STORM_RELAY, STORMHEART_PEDESTAL, SURVEY_BEACON, VEHICLE_DOCK);
    private static Block machine(String id, String name, JupiterMachineBlock.Kind kind, int light) {
        return add(id, name, new JupiterMachineBlock(metal(4).noOcclusion().lightLevel(s -> light), kind));
    }
    private static Block stairs(String id, String name, Block base) {
        Block block = add(id, name, new StairBlock(base.defaultBlockState(), BlockBehaviour.Properties.ofFullCopy(base)));
        VARIANT_BASES.put(block, base);
        return block;
    }
    private static Block slab(String id, String name, Block base) {
        Block block = add(id, name, new SlabBlock(BlockBehaviour.Properties.ofFullCopy(base)));
        VARIANT_BASES.put(block, base);
        return block;
    }
    private static BlockBehaviour.Properties metal(float strength) { return BlockBehaviour.Properties.of().strength(strength).sound(SoundType.METAL); }
    private static Block add(String id, String name, Block block) {
        Block result = GCBlocks.BLOCKS.registerWithItem(id, block); NAMES.put(result, name); return result;
    }
    public static void register() { }
    private static final class BuoyancyCell extends Block {
        BuoyancyCell(Properties p) { super(p); }
        @Override public void fallOn(net.minecraft.world.level.Level level, BlockState state, BlockPos pos, net.minecraft.world.entity.Entity entity, float fallDistance) {
            entity.causeFallDamage(fallDistance, 0.2F, level.damageSources().fall());
        }
    }
    private static final class PressureGlass extends HalfTransparentBlock { PressureGlass(Properties p) { super(p); } }
    private static final class Hatch extends TrapDoorBlock { Hatch(Properties p) { super(BlockSetType.IRON, p); } }
    private static final class Grating extends Block {
        Grating(Properties p) { super(p); }
        @Override protected VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) { return box(0,0,0,16,3,16); }
    }
    /** Grows up from cloud tops or, when {@link #HANGING}, down from exposed undersides. */
    public static final class Cluster extends Block {
        public static final net.minecraft.world.level.block.state.properties.BooleanProperty HANGING =
                net.minecraft.world.level.block.state.properties.BlockStateProperties.HANGING;
        Cluster(Properties p) { super(p); this.registerDefaultState(this.stateDefinition.any().setValue(HANGING, false)); }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(HANGING); }
        @Override public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
            return this.defaultBlockState().setValue(HANGING, context.getClickedFace() == net.minecraft.core.Direction.DOWN);
        }
        @Override protected VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
            return s.getValue(HANGING) ? box(2,2,2,14,16,14) : box(2,0,2,14,14,14);
        }
    }
}