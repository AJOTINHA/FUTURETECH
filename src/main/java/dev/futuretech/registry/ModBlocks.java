package dev.futuretech.registry;

import dev.futuretech.FutureTech;
import dev.futuretech.block.BatteryBlock;
import dev.futuretech.block.FluidTankBlock;
import dev.futuretech.block.BatteryTier;
import dev.futuretech.block.EnergyCableBlock;
import dev.futuretech.block.EnergyCableTier;
import dev.futuretech.block.ElectricFurnaceBlock;
import dev.futuretech.block.FluidCableBlock;
import dev.futuretech.block.FluidCableTier;
import dev.futuretech.block.ItemCableBlock;
import dev.futuretech.block.ItemCableTier;
import dev.futuretech.block.LavaGeneratorBlock;
import dev.futuretech.block.NetworkCableBlock;
import dev.futuretech.block.RedstoneCableBlock;
import dev.futuretech.block.NetworkPanelBlock;
import dev.futuretech.block.StorageCardsBlock;
import dev.futuretech.block.TesseractBlock;
import dev.futuretech.block.WirelessRedstoneBlock;
import dev.futuretech.block.ChargerBlock;
import dev.futuretech.block.LaneMachineBlock;
import dev.futuretech.block.LaneMachineKind;
import dev.futuretech.block.MetalPressBlock;
import dev.futuretech.block.MachineCasingBlock;
import dev.futuretech.block.PaintMachineBlock;
import dev.futuretech.block.MelterBlock;
import dev.futuretech.block.ExtruderBlock;
import dev.futuretech.block.SmelteryBlock;
import dev.futuretech.block.TeleporterBlock;
import dev.futuretech.block.ControllerBlock;
import dev.futuretech.block.ControllerKind;
import dev.futuretech.block.RainSensorBlock;
import dev.futuretech.block.WaterPumpBlock;
import dev.futuretech.block.SolidFuelGeneratorBlock;
import net.minecraft.resources.Identifier;
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

    public static final DeferredBlock<MelterBlock> MELTER = BLOCKS.registerBlock(
            "melter", MelterBlock::new, properties -> properties
                    .mapColor(MapColor.METAL).strength(3.5F, 6.0F).sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .lightLevel(state -> state.getValue(MelterBlock.LIT) ? 13 : 0));

    // Stirring is not hot work, so the extruder has no light level; its front lights up on its own.
    public static final DeferredBlock<ExtruderBlock> EXTRUDER = BLOCKS.registerBlock(
            "extruder", ExtruderBlock::new, properties -> properties
                    .mapColor(MapColor.METAL).strength(3.5F, 6.0F).sound(SoundType.METAL)
                    .requiresCorrectToolForDrops());

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

    /** The controllers: one block per kind, setting the time of day or the weather; energy on every face, no tiers. */
    public static final DeferredBlock<ControllerBlock> TIME_CONTROLLER = registerController(ControllerKind.TIME);
    public static final DeferredBlock<ControllerBlock> WEATHER_CONTROLLER = registerController(ControllerKind.WEATHER);

    /** A daylight detector for rain: the detector's own slab, stone sound, no tool needed. */
    public static final DeferredBlock<RainSensorBlock> RAIN_SENSOR = BLOCKS.registerBlock(
            "rain_sensor", RainSensorBlock::new, properties -> properties
                    .mapColor(MapColor.STONE).strength(0.5F).sound(SoundType.STONE));

    private static DeferredBlock<ControllerBlock> registerController(ControllerKind kind) {
        return BLOCKS.registerBlock(kind.blockName(), properties -> new ControllerBlock(kind, properties), properties -> properties
                .mapColor(MapColor.METAL).strength(3.5F, 6.0F).sound(SoundType.METAL)
                .requiresCorrectToolForDrops());
    }

    // The panel has no energy and nothing to tick; it is a screen onto the cables beside it.
    public static final DeferredBlock<NetworkPanelBlock> NETWORK_PANEL = BLOCKS.registerBlock(
            "network_panel", NetworkPanelBlock::new, properties -> properties
                    .mapColor(MapColor.COLOR_PURPLE).strength(3.5F, 6.0F).sound(SoundType.METAL)
                    .noOcclusion()
                    .requiresCorrectToolForDrops());

    // The card storage holds the network's destinations; no energy and nothing to tick either.
    public static final DeferredBlock<StorageCardsBlock> STORAGE_CARDS = BLOCKS.registerBlock(
            "storage_cards", StorageCardsBlock::new, properties -> properties
                    .mapColor(MapColor.METAL).strength(3.5F, 6.0F).sound(SoundType.METAL)
                    .requiresCorrectToolForDrops());

    // The wireless plates take no energy and never tick: one reads the redstone around it, the
    // other gives it back somewhere else, and between them there is only a frequency.
    public static final DeferredBlock<WirelessRedstoneBlock> WIRELESS_TRANSMITTER =
            registerWireless("wireless_transmitter", WirelessRedstoneBlock.Kind.TRANSMITTER);
    public static final DeferredBlock<WirelessRedstoneBlock> WIRELESS_RECEIVER =
            registerWireless("wireless_receiver", WirelessRedstoneBlock.Kind.RECEIVER);

    private static DeferredBlock<WirelessRedstoneBlock> registerWireless(String name, WirelessRedstoneBlock.Kind kind) {
        return BLOCKS.registerBlock(name, properties -> new WirelessRedstoneBlock(kind, properties), properties -> properties
                    .mapColor(MapColor.METAL).strength(3.5F, 6.0F).sound(SoundType.METAL)
                    .noOcclusion().requiresCorrectToolForDrops()
                    // The crystal and the dish carry a light of their own while there is a signal.
                    .lightLevel(state -> state.getValue(WirelessRedstoneBlock.LIT) ? 3 : 0));
    }

    // The tesseract is an open frame: no occlusion, so the cube inside and the light show through.
    public static final DeferredBlock<TesseractBlock> TESSERACT = BLOCKS.registerBlock(
            "tesseract", TesseractBlock::new, properties -> properties
                    .mapColor(MapColor.METAL).strength(3.5F, 6.0F).sound(SoundType.METAL)
                    .noOcclusion().requiresCorrectToolForDrops());

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

    public static final DeferredBlock<EnergyCableBlock> ENERGY_CABLE_MK1 = registerCable(EnergyCableTier.MK1);
    public static final DeferredBlock<EnergyCableBlock> ENERGY_CABLE_MK2 = registerCable(EnergyCableTier.MK2);
    public static final DeferredBlock<EnergyCableBlock> ENERGY_CABLE_MK3 = registerCable(EnergyCableTier.MK3);
    public static final DeferredBlock<EnergyCableBlock> ENERGY_CABLE_MK4 = registerCable(EnergyCableTier.MK4);

    private static DeferredBlock<EnergyCableBlock> registerCable(EnergyCableTier tier) {
        return BLOCKS.registerBlock(tier.blockName(), properties -> new EnergyCableBlock(tier, properties), properties -> properties
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

    // One size as well: a wire is a wire.
    public static final DeferredBlock<RedstoneCableBlock> REDSTONE_CABLE = BLOCKS.registerBlock(
            "redstone_cable", RedstoneCableBlock::new, properties -> properties
                    .mapColor(MapColor.COLOR_RED)
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

    static {
        // The energy cable used to be plain "cable": worlds and chests from before keep theirs.
        for (var tier : EnergyCableTier.values()) {
            BLOCKS.addAlias(Identifier.fromNamespaceAndPath(FutureTech.MOD_ID, "cable_" + tier.getSerializedName()),
                    Identifier.fromNamespaceAndPath(FutureTech.MOD_ID, tier.blockName()));
        }
    }

    private ModBlocks() {}
}
