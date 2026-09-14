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
import dev.futuretech.block.CrusherBlock;
import dev.futuretech.block.MetalPressBlock;
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

    public static final DeferredBlock<Block> MACHINE_CASING = BLOCKS.registerSimpleBlock(
            "machine_casing", properties -> properties
                    .mapColor(MapColor.METAL)
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

    public static final DeferredBlock<ElectricFurnaceBlock> ELECTRIC_FURNACE = BLOCKS.registerBlock(
            "electric_furnace", ElectricFurnaceBlock::new, properties -> properties
                    .mapColor(MapColor.METAL)
                    .strength(3.5F, 6.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .lightLevel(state -> state.getValue(ElectricFurnaceBlock.LIT) ? 10 : 0));

    public static final DeferredBlock<CrusherBlock> CRUSHER = BLOCKS.registerBlock(
            "crusher", CrusherBlock::new, properties -> properties
                    .mapColor(MapColor.METAL).strength(3.5F, 6.0F).sound(SoundType.METAL)
                    .requiresCorrectToolForDrops());

    public static final DeferredBlock<BatteryBlock> BATTERY_MK1 = registerBattery(BatteryTier.MK1);

    public static final DeferredBlock<MetalPressBlock> METAL_PRESS = BLOCKS.registerBlock(
            "metal_press", MetalPressBlock::new, properties -> properties
                    .mapColor(MapColor.METAL).strength(3.5F, 6.0F).sound(SoundType.METAL)
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
                    .dynamicShape()
                    .noOcclusion());
    }

    public static final DeferredBlock<ItemCableBlock> ITEM_CABLE_OPAQUE = registerItemCable(ItemCableTier.OPAQUE);
    public static final DeferredBlock<ItemCableBlock> ITEM_CABLE = registerItemCable(ItemCableTier.STANDARD);

    public static final DeferredBlock<FluidCableBlock> FLUID_CABLE_OPAQUE = registerFluidCable(FluidCableTier.OPAQUE);
    public static final DeferredBlock<FluidCableBlock> FLUID_CABLE = registerFluidCable(FluidCableTier.STANDARD);

    private static DeferredBlock<FluidCableBlock> registerFluidCable(FluidCableTier tier) {
        return BLOCKS.registerBlock(tier.blockName(), properties -> new FluidCableBlock(tier, properties), properties -> properties
                    .mapColor(MapColor.COLOR_GREEN)
                    .strength(1.0F, 3.0F)
                    .sound(SoundType.COPPER)
                    .dynamicShape()
                    .noOcclusion());
    }

    private static DeferredBlock<ItemCableBlock> registerItemCable(ItemCableTier tier) {
        return BLOCKS.registerBlock(tier.blockName(), properties -> new ItemCableBlock(tier, properties), properties -> properties
                    .mapColor(MapColor.COLOR_YELLOW)
                    .strength(1.0F, 3.0F)
                    .sound(SoundType.COPPER)
                    .dynamicShape()
                    .noOcclusion());
    }

    private ModBlocks() {}
}
