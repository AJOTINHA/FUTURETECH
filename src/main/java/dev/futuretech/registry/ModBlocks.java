package dev.futuretech.registry;

import dev.futuretech.FutureTech;
import dev.futuretech.block.BatteryBlock;
import dev.futuretech.block.BatteryTier;
import dev.futuretech.block.CableBlock;
import dev.futuretech.block.CableTier;
import dev.futuretech.block.ElectricFurnaceBlock;
import dev.futuretech.block.CrusherBlock;
import dev.futuretech.block.SolidFuelGeneratorBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(FutureTech.MOD_ID);

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

    // Every tier shares BatteryBlock; only the numbers in BatteryTier change.
    private static DeferredBlock<BatteryBlock> registerBattery(BatteryTier tier) {
        return BLOCKS.registerBlock(tier.blockName(), properties -> new BatteryBlock(tier, properties), properties -> properties
                    .mapColor(MapColor.METAL)
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

    private ModBlocks() {}
}
