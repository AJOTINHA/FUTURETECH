package dev.futuretech.registry;

import dev.futuretech.FutureTech;
import dev.futuretech.block.BatteryBlock;
import dev.futuretech.item.BatteryBlockItem;
import net.minecraft.world.item.BlockItem;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(FutureTech.MOD_ID);

    public static final DeferredItem<BlockItem> MACHINE_CASING = ITEMS.registerSimpleBlockItem(
            "machine_casing", ModBlocks.MACHINE_CASING);

    public static final DeferredItem<BlockItem> SOLID_FUEL_GENERATOR = ITEMS.registerSimpleBlockItem(
            "solid_fuel_generator", ModBlocks.SOLID_FUEL_GENERATOR);

    // Custom block item so the stored charge shows in the tooltip and as a bar.
    public static final DeferredItem<BatteryBlockItem> BATTERY_MK1 = registerBattery(ModBlocks.BATTERY_MK1);

    private static DeferredItem<BatteryBlockItem> registerBattery(DeferredBlock<BatteryBlock> block) {
        return ITEMS.registerItem(block.getId().getPath(), properties -> new BatteryBlockItem(block.get(), properties),
                properties -> properties.useBlockDescriptionPrefix());
    }

    public static final DeferredItem<BlockItem> CABLE_MK1 = ITEMS.registerSimpleBlockItem(ModBlocks.CABLE_MK1);

    private ModItems() {}
}
