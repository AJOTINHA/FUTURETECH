package dev.futuretech.registry;

import dev.futuretech.FutureTech;
import dev.futuretech.block.BatteryBlock;
import dev.futuretech.item.BatteryBlockItem;
import dev.futuretech.item.FluidTankBlockItem;
import dev.futuretech.item.ItemFilterItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(FutureTech.MOD_ID);

    public static final DeferredItem<BlockItem> MACHINE_CASING = ITEMS.registerSimpleBlockItem(
            "machine_casing", ModBlocks.MACHINE_CASING);

    public static final DeferredItem<BlockItem> SOLID_FUEL_GENERATOR = ITEMS.registerSimpleBlockItem(
            "solid_fuel_generator", ModBlocks.SOLID_FUEL_GENERATOR);

    public static final DeferredItem<BlockItem> ELECTRIC_FURNACE = ITEMS.registerSimpleBlockItem(
            "electric_furnace", ModBlocks.ELECTRIC_FURNACE);

    public static final DeferredItem<BlockItem> CRUSHER = ITEMS.registerSimpleBlockItem("crusher", ModBlocks.CRUSHER);

    public static final DeferredItem<Item> IRON_POWDER = ITEMS.registerSimpleItem("iron_powder");
    public static final DeferredItem<Item> GOLD_POWDER = ITEMS.registerSimpleItem("gold_powder");
    public static final DeferredItem<Item> COPPER_POWDER = ITEMS.registerSimpleItem("copper_powder");

    // Custom block item so the stored charge shows in the tooltip and as a bar.
    public static final DeferredItem<BatteryBlockItem> BATTERY_MK1 = registerBattery(ModBlocks.BATTERY_MK1);

    public static final DeferredItem<FluidTankBlockItem> FLUID_TANK = ITEMS.registerItem(
            "fluid_tank", properties -> new FluidTankBlockItem(ModBlocks.FLUID_TANK.get(), properties),
            properties -> properties.useBlockDescriptionPrefix().stacksTo(1));

    private static DeferredItem<BatteryBlockItem> registerBattery(DeferredBlock<BatteryBlock> block) {
        return ITEMS.registerItem(block.getId().getPath(), properties -> new BatteryBlockItem(block.get(), properties),
                properties -> properties.useBlockDescriptionPrefix());
    }

    public static final DeferredItem<BlockItem> CABLE_MK1 = ITEMS.registerSimpleBlockItem(ModBlocks.CABLE_MK1);

    public static final DeferredItem<BlockItem> ITEM_CABLE_OPAQUE = ITEMS.registerSimpleBlockItem(ModBlocks.ITEM_CABLE_OPAQUE);
    public static final DeferredItem<BlockItem> ITEM_CABLE = ITEMS.registerSimpleBlockItem(ModBlocks.ITEM_CABLE);

    public static final DeferredItem<BlockItem> FLUID_CABLE_OPAQUE = ITEMS.registerSimpleBlockItem(ModBlocks.FLUID_CABLE_OPAQUE);
    public static final DeferredItem<BlockItem> FLUID_CABLE = ITEMS.registerSimpleBlockItem(ModBlocks.FLUID_CABLE);

    /** Filter card for item cable connectors; its list and mode live in data components. */
    public static final DeferredItem<ItemFilterItem> FILTER = ITEMS.registerItem(
            "filter", ItemFilterItem::new, properties -> properties.stacksTo(1));

    public static final DeferredItem<Item> SPEED_UPGRADE = ITEMS.registerSimpleItem("speed_upgrade");

    private ModItems() {}
}
