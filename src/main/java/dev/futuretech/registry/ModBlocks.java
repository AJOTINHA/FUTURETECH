package dev.futuretech.registry;

import dev.futuretech.FutureTech;
import dev.futuretech.block.BatteryBlock;
import dev.futuretech.block.FluidTankBlock;
import dev.futuretech.block.BatteryTier;
import dev.futuretech.block.CableBlock;
import dev.futuretech.block.CableTier;
import dev.futuretech.block.ElectricFurnaceBlock;
import dev.futuretech.block.FluidCableBlock;
import dev.futuretech.block.FluidCableTier;
import dev.futuretech.block.ItemCableBlock;
import dev.futuretech.block.ItemCableTier;
import dev.futuretech.block.LavaGeneratorBlock;
import dev.futuretech.block.NetworkCableBlock;
import dev.futuretech.block.NetworkPanelBlock;
import dev.futuretech.block.ChargerBlock;
import dev.futuretech.block.LaneMachineBlock;
import dev.futuretech.block.LaneMachineKind;
import dev.futuretech.block.MetalPressBlock;
import dev.futuretech.block.MachineCasingBlock;
import dev.futuretech.block.PaintMachineBlock;
import dev.futuretech.block.SmelteryBlock;
import dev.futuretech.block.TeleporterBlock;
import dev.futuretech.block.WaterPumpBlock;
import dev.futuretech.block.SolidFuelGeneratorBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(FutureTech.MOD_ID);

    public static final DeferredBlock<dev.futuretech.block.AssemblerBlock> ASSEMBLY_TABLE = assembler("assembly_table", dev.futuretech.block.AssemblerBlock.Kind.TABLE);
    public static final DeferredBlock<dev.futuretech.block.AssemblerBlock> TRANSPORT_ARM = assembler("transport_arm", dev.futuretech.block.AssemblerBlock.Kind.TRANSPORT);
    public static final DeferredBlock<dev.futuretech.block.AssemblerBlock> ASSEMBLY_ARM = assembler("assembly_arm", dev.futuretech.block.AssemblerBlock.Kind.ASSEMBLY);
    public static final DeferredBlock<dev.futuretech.block.AssemblerBlock> ASSEMBLER_TERMINAL = assembler("assembler_terminal", dev.futuretech.block.AssemblerBlock.Kind.TERMINAL);

    private static DeferredBlock<dev.futuretech.block.AssemblerBlock> assembler(String name, dev.futuretech.block.AssemblerBlock.Kind kind) {
        return BLOCKS.registerBlock(name, properties -> new dev.futuretech.block.AssemblerBlock(kind, properties),
                properties -> properties.mapColor(MapColor.METAL).noOcclusion().strength(3.5F, 6).sound(SoundType.METAL).requiresCorrectToolForDrops());
    }

    public static final DeferredBlock<MachineCasingBlock> MACHINE_CASING = BLOCKS.registerBlock(
            "machine_casing", MachineCasingBlock::new, properties -> properties
                    .mapColor(MapColor.METAL)
                    .noOcclusion()
                    .strength(3.5F, 6.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops());

    public static final DeferredBlock<SolidFuelGeneratorBlock> SOLID_FUEL_GENERATOR = BLOCKS.registerBlock(
            "solid_fuel_generator", SolidFuelGeneratorBlock::new, properties -> properties
                    .mapColor(MapColor.METAL)
                    .strength(3.5F, 6.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .lightLevel(state -> state.getValue(SolidFuelGeneratorBlock.LIT) ? 10 : 0));

    public static final DeferredBlock<LavaGeneratorBlock> LAVA_GENERATOR = BLOCKS.registerBlock(
            "lava_generator", LavaGeneratorBlock::new, properties -> properties
                    .mapColor(MapColor.METAL)
                    .strength(3.5F, 6.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .lightLevel(state -> state.getValue(LavaGeneratorBlock.LIT) ? 13 : 0));

    public static final DeferredBlock<ElectricFurnaceBlock> ELECTRIC_FURNACE = BLOCKS.registerBlock(
            "electric_furnace", ElectricFurnaceBlock::new, properties -> properties
                    .mapColor(MapColor.METAL)
                    .strength(3.5F, 6.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .lightLevel(state -> state.getValue(ElectricFurnaceBlock.LIT) ? 10 : 0));

    public static final DeferredBlock<LaneMachineBlock> CRUSHER = BLOCKS.registerBlock(
            "crusher", properties -> new LaneMachineBlock(LaneMachineKind.CRUSHER, properties), properties -> properties
                    .mapColor(MapColor.METAL).strength(3.5F, 6.0F).sound(SoundType.METAL)
                    .requiresCorrectToolForDrops());

    public static final DeferredBlock<LaneMachineBlock> SAWMILL = BLOCKS.registerBlock(
            "sawmill", properties -> new LaneMachineBlock(LaneMachineKind.SAWMILL, properties), properties -> properties
                    .mapColor(MapColor.METAL).strength(3.5F, 6.0F).sound(SoundType.METAL)
                    .requiresCorrectToolForDrops());

    public static final DeferredBlock<ChargerBlock> CHARGER = BLOCKS.registerBlock(
            "charger", ChargerBlock::new, properties -> properties
                    .mapColor(MapColor.METAL).strength(3.5F, 6.0F).sound(SoundType.METAL)
                    .requiresCorrectToolForDrops());

    public static final DeferredBlock<BatteryBlock> BATTERY_MK1 = registerBattery(BatteryTier.MK1);

    public static final DeferredBlock<MetalPressBlock> METAL_PRESS = BLOCKS.registerBlock(
            "metal_press", MetalPressBlock::new, properties -> properties
                    .mapColor(MapColor.METAL).strength(3.5F, 6.0F).sound(SoundType.METAL)
                    .requiresCorrectToolForDrops());

    public static final DeferredBlock<SmelteryBlock> SMELTERY = BLOCKS.registerBlock(
            "smeltery", SmelteryBlock::new, properties -> properties
                    .mapColor(MapColor.METAL).strength(3.5F, 6.0F).sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .lightLevel(state -> state.getValue(SmelteryBlock.LIT) ? 13 : 0));

    public static final DeferredBlock<PaintMachineBlock> PAINT_MACHINE = BLOCKS.registerBlock(
            "paint_machine", PaintMachineBlock::new, properties -> properties
                    .mapColor(MapColor.METAL).strength(3.5F, 6.0F).sound(SoundType.METAL)
                    .requiresCorrectToolForDrops());

    public static final DeferredBlock<WaterPumpBlock> WATER_PUMP = BLOCKS.registerBlock(
            "water_pump", WaterPumpBlock::new, properties -> properties
                    .mapColor(MapColor.METAL).strength(3.5F, 6.0F).sound(SoundType.METAL)
                    .requiresCorrectToolForDrops());

    public static final DeferredBlock<TeleporterBlock> TELEPORTER = BLOCKS.registerBlock(
            "teleporter", TeleporterBlock::new, properties -> properties
                    .mapColor(MapColor.METAL).strength(3.5F, 6.0F).sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .lightLevel(state -> state.getValue(TeleporterBlock.LIT) ? 10 : 0));

    // The panel has no energy and nothing to tick; it is a screen onto the cables beside it.
    public static final DeferredBlock<NetworkPanelBlock> NETWORK_PANEL = BLOCKS.registerBlock(
            "network_panel", NetworkPanelBlock::new, properties -> properties
                    .mapColor(MapColor.COLOR_PURPLE).strength(3.5F, 6.0F).sound(SoundType.METAL)
                    .noOcclusion()
                    .requiresCorrectToolForDrops());

    public static final DeferredBlock<FluidTankBlock> FLUID_TANK = BLOCKS.registerBlock(
            "fluid_tank", FluidTankBlock::new, properties -> properties
                    .mapColor(MapColor.METAL).noOcclusion().strength(3.5F, 6.0F)
                    .sound(SoundType.METAL).requiresCorrectToolForDrops());

    // Every tier shares BatteryBlock; only the numbers in BatteryTier change.
    private static DeferredBlock<BatteryBlock> registerBattery(BatteryTier tier) {
        return BLOCKS.registerBlock(tier.blockName(), properties -> new BatteryBlock(tier, properties), properties -> properties
                    .mapColor(MapColor.METAL)
                    .noOcclusion()
                    .strength(3.5F, 6.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops());
    }

    public static final DeferredBlock<CableBlock> CABLE_MK1 = registerCable(CableTier.MK1);

    private static DeferredBlock<CableBlock> registerCable(CableTier tier) {
        return BLOCKS.registerBlock(tier.blockName(), properties -> new CableBlock(tier, properties), properties -> properties
                    .mapColor(MapColor.COLOR_ORANGE)
                    .strength(1.0F, 3.0F)
                    .sound(SoundType.COPPER)
                    .noOcclusion());
    }

    public static final DeferredBlock<ItemCableBlock> ITEM_CABLE_OPAQUE = registerItemCable(ItemCableTier.OPAQUE);
    public static final DeferredBlock<ItemCableBlock> ITEM_CABLE = registerItemCable(ItemCableTier.STANDARD);

    public static final DeferredBlock<FluidCableBlock> FLUID_CABLE_OPAQUE = registerFluidCable(FluidCableTier.OPAQUE);
    public static final DeferredBlock<FluidCableBlock> FLUID_CABLE = registerFluidCable(FluidCableTier.STANDARD);

    // One size, so no tier to register per: the block is its own entry.
    public static final DeferredBlock<NetworkCableBlock> NETWORK_CABLE = BLOCKS.registerBlock(
            "network_cable", NetworkCableBlock::new, properties -> properties
                    .mapColor(MapColor.COLOR_PURPLE)
                    .strength(1.0F, 3.0F)
                    .sound(SoundType.COPPER)
                    .noOcclusion());

    private static DeferredBlock<FluidCableBlock> registerFluidCable(FluidCableTier tier) {
        return BLOCKS.registerBlock(tier.blockName(), properties -> new FluidCableBlock(tier, properties), properties -> properties
                    .mapColor(MapColor.COLOR_GREEN)
                    .strength(1.0F, 3.0F)
                    .sound(SoundType.COPPER)
                    .noOcclusion());
    }

    private static DeferredBlock<ItemCableBlock> registerItemCable(ItemCableTier tier) {
        return BLOCKS.registerBlock(tier.blockName(), properties -> new ItemCableBlock(tier, properties), properties -> properties
                    .mapColor(MapColor.COLOR_YELLOW)
                    .strength(1.0F, 3.0F)
                    .sound(SoundType.COPPER)
                    .noOcclusion());
    }

    private ModBlocks() {}
}
