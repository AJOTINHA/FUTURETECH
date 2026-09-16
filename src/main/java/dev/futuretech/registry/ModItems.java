package dev.futuretech.registry;

import dev.futuretech.FutureTech;
import dev.futuretech.block.BatteryBlock;
import dev.futuretech.item.BatteryBlockItem;
import dev.futuretech.item.FluidTankBlockItem;
import dev.futuretech.item.ItemFilterItem;
import dev.futuretech.item.WrenchItem;
import dev.futuretech.item.AreaToolItem;
import dev.futuretech.item.MachineUpgradeKitItem;
import dev.futuretech.item.PortableBatteryItem;
import dev.futuretech.item.TieredMachineBlockItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.UnaryOperator;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(FutureTech.MOD_ID);

    public static final DeferredItem<AreaToolItem> WOODEN_HAMMER = registerHammer("wooden_hammer", ToolMaterial.WOOD);
    public static final DeferredItem<AreaToolItem> STONE_HAMMER = registerHammer("stone_hammer", ToolMaterial.STONE);
    public static final DeferredItem<AreaToolItem> COPPER_HAMMER = registerHammer("copper_hammer", ToolMaterial.COPPER);
    public static final DeferredItem<AreaToolItem> IRON_HAMMER = registerHammer("iron_hammer", ToolMaterial.IRON);
    public static final DeferredItem<AreaToolItem> GOLDEN_HAMMER = registerHammer("golden_hammer", ToolMaterial.GOLD);
    public static final DeferredItem<AreaToolItem> DIAMOND_HAMMER = registerHammer("diamond_hammer", ToolMaterial.DIAMOND);
    public static final DeferredItem<AreaToolItem> NETHERITE_HAMMER = registerHammer("netherite_hammer", ToolMaterial.NETHERITE);

    public static final DeferredItem<AreaToolItem> WOODEN_EXCAVATOR = registerExcavator("wooden_excavator", ToolMaterial.WOOD);
    public static final DeferredItem<AreaToolItem> STONE_EXCAVATOR = registerExcavator("stone_excavator", ToolMaterial.STONE);
    public static final DeferredItem<AreaToolItem> COPPER_EXCAVATOR = registerExcavator("copper_excavator", ToolMaterial.COPPER);
    public static final DeferredItem<AreaToolItem> IRON_EXCAVATOR = registerExcavator("iron_excavator", ToolMaterial.IRON);
    public static final DeferredItem<AreaToolItem> GOLDEN_EXCAVATOR = registerExcavator("golden_excavator", ToolMaterial.GOLD);
    public static final DeferredItem<AreaToolItem> DIAMOND_EXCAVATOR = registerExcavator("diamond_excavator", ToolMaterial.DIAMOND);
    public static final DeferredItem<AreaToolItem> NETHERITE_EXCAVATOR = registerExcavator("netherite_excavator", ToolMaterial.NETHERITE);

    public static final DeferredItem<AreaToolItem> WOODEN_LUMBER_AXE = registerLumberAxe("wooden_lumber_axe", ToolMaterial.WOOD);
    public static final DeferredItem<AreaToolItem> STONE_LUMBER_AXE = registerLumberAxe("stone_lumber_axe", ToolMaterial.STONE);
    public static final DeferredItem<AreaToolItem> COPPER_LUMBER_AXE = registerLumberAxe("copper_lumber_axe", ToolMaterial.COPPER);
    public static final DeferredItem<AreaToolItem> IRON_LUMBER_AXE = registerLumberAxe("iron_lumber_axe", ToolMaterial.IRON);
    public static final DeferredItem<AreaToolItem> GOLDEN_LUMBER_AXE = registerLumberAxe("golden_lumber_axe", ToolMaterial.GOLD);
    public static final DeferredItem<AreaToolItem> DIAMOND_LUMBER_AXE = registerLumberAxe("diamond_lumber_axe", ToolMaterial.DIAMOND);
    public static final DeferredItem<AreaToolItem> NETHERITE_LUMBER_AXE = registerLumberAxe("netherite_lumber_axe", ToolMaterial.NETHERITE);

    /** The material's pickaxe, heavier and slower to swing, mining a 3x3 area of pickaxe blocks. */
    private static DeferredItem<AreaToolItem> registerHammer(String name, ToolMaterial material) {
        return registerAreaTool(name, AreaToolItem.Kind.HAMMER, material,
                properties -> properties.pickaxe(material, 4.0F, -3.2F));
    }

    /** The material's shovel, heavier like the hammer, mining a 3x3 area of shovel blocks. */
    private static DeferredItem<AreaToolItem> registerExcavator(String name, ToolMaterial material) {
        return registerAreaTool(name, AreaToolItem.Kind.EXCAVATOR, material,
                properties -> properties.shovel(material, 3.0F, -3.2F));
    }

    /** The material's axe, heavier like the hammer, felling the whole tree from any of its logs. */
    private static DeferredItem<AreaToolItem> registerLumberAxe(String name, ToolMaterial material) {
        return registerAreaTool(name, AreaToolItem.Kind.LUMBER_AXE, material,
                properties -> properties.axe(material, 7.0F, -3.4F));
    }

    private static DeferredItem<AreaToolItem> registerAreaTool(String name, AreaToolItem.Kind kind, ToolMaterial material,
                                                               UnaryOperator<Item.Properties> tool) {
        return ITEMS.registerItem(name, properties -> new AreaToolItem(kind, properties), properties -> {
            tool.apply(properties);
            if (material == ToolMaterial.NETHERITE) properties.fireResistant();
            return properties;
        });
    }

    public static final DeferredItem<BlockItem> MACHINE_CASING = ITEMS.registerSimpleBlockItem(
            "machine_casing", ModBlocks.MACHINE_CASING);

    public static final DeferredItem<TieredMachineBlockItem> SOLID_FUEL_GENERATOR = ITEMS.registerItem(
            "solid_fuel_generator", properties -> new TieredMachineBlockItem(ModBlocks.SOLID_FUEL_GENERATOR.get(), properties),
            properties -> properties.useBlockDescriptionPrefix());

    public static final DeferredItem<TieredMachineBlockItem> LAVA_GENERATOR = ITEMS.registerItem(
            "lava_generator", properties -> new TieredMachineBlockItem(ModBlocks.LAVA_GENERATOR.get(), properties),
            properties -> properties.useBlockDescriptionPrefix());

    public static final DeferredItem<TieredMachineBlockItem> ELECTRIC_FURNACE = ITEMS.registerItem(
            "electric_furnace", properties -> new TieredMachineBlockItem(ModBlocks.ELECTRIC_FURNACE.get(), properties),
            properties -> properties.useBlockDescriptionPrefix());

    public static final DeferredItem<TieredMachineBlockItem> CRUSHER = ITEMS.registerItem(
            "crusher", properties -> new TieredMachineBlockItem(ModBlocks.CRUSHER.get(), properties),
            properties -> properties.useBlockDescriptionPrefix());

    public static final DeferredItem<TieredMachineBlockItem> SAWMILL = ITEMS.registerItem(
            "sawmill", properties -> new TieredMachineBlockItem(ModBlocks.SAWMILL.get(), properties),
            properties -> properties.useBlockDescriptionPrefix());

    public static final DeferredItem<Item> IRON_POWDER = ITEMS.registerSimpleItem("iron_powder");
    public static final DeferredItem<TieredMachineBlockItem> METAL_PRESS = ITEMS.registerItem(
            "metal_press", properties -> new TieredMachineBlockItem(ModBlocks.METAL_PRESS.get(), properties),
            properties -> properties.useBlockDescriptionPrefix());
    public static final DeferredItem<TieredMachineBlockItem> SMELTERY = ITEMS.registerItem(
            "smeltery", properties -> new TieredMachineBlockItem(ModBlocks.SMELTERY.get(), properties),
            properties -> properties.useBlockDescriptionPrefix());
    public static final DeferredItem<Item> GOLD_POWDER = ITEMS.registerSimpleItem("gold_powder");
    public static final DeferredItem<Item> COPPER_POWDER = ITEMS.registerSimpleItem("copper_powder");
    public static final DeferredItem<Item> STEEL_INGOT = ITEMS.registerSimpleItem("steel_ingot");
    public static final DeferredItem<Item> STEEL_PLATE = ITEMS.registerSimpleItem("steel_plate");
    public static final DeferredItem<Item> STEEL_GEAR = ITEMS.registerSimpleItem("steel_gear");

    public static final DeferredItem<Item> IRON_PLATE = ITEMS.registerSimpleItem("iron_plate");
    public static final DeferredItem<Item> GOLD_PLATE = ITEMS.registerSimpleItem("gold_plate");
    public static final DeferredItem<Item> COPPER_PLATE = ITEMS.registerSimpleItem("copper_plate");
    public static final DeferredItem<Item> NETHERITE_PLATE = ITEMS.registerSimpleItem("netherite_plate");
    public static final DeferredItem<Item> IRON_GEAR = ITEMS.registerSimpleItem("iron_gear");
    public static final DeferredItem<Item> GOLD_GEAR = ITEMS.registerSimpleItem("gold_gear");
    public static final DeferredItem<Item> COPPER_GEAR = ITEMS.registerSimpleItem("copper_gear");
    public static final DeferredItem<Item> NETHERITE_GEAR = ITEMS.registerSimpleItem("netherite_gear");
    public static final DeferredItem<Item> PLATE_MOLD = ITEMS.registerSimpleItem("plate_mold", properties -> properties.stacksTo(1));
    public static final DeferredItem<Item> GEAR_MOLD = ITEMS.registerSimpleItem("gear_mold", properties -> properties.stacksTo(1));

    // Custom block item so the stored charge shows in the tooltip and as a bar.
    public static final DeferredItem<PortableBatteryItem> PORTABLE_BATTERY = ITEMS.registerItem(
            "portable_battery", PortableBatteryItem::new, properties -> properties.stacksTo(1));

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
    public static final DeferredItem<Item> CHIP = ITEMS.registerSimpleItem("chip");

    public static final DeferredItem<MachineUpgradeKitItem> UPGRADE_KIT_MK2 = ITEMS.registerItem(
            "upgrade_kit_mk2", properties -> new MachineUpgradeKitItem(2, properties));
    public static final DeferredItem<MachineUpgradeKitItem> UPGRADE_KIT_MK3 = ITEMS.registerItem(
            "upgrade_kit_mk3", properties -> new MachineUpgradeKitItem(3, properties));
    public static final DeferredItem<MachineUpgradeKitItem> UPGRADE_KIT_MK4 = ITEMS.registerItem(
            "upgrade_kit_mk4", properties -> new MachineUpgradeKitItem(4, properties));

    /** Right-click any block to rotate it. */
    public static final DeferredItem<WrenchItem> WRENCH = ITEMS.registerItem(
            "wrench", WrenchItem::new, properties -> properties.stacksTo(1));

    public static final DeferredItem<BlockItem> ASSEMBLY_TABLE = ITEMS.registerSimpleBlockItem(ModBlocks.ASSEMBLY_TABLE);
    public static final DeferredItem<BlockItem> TRANSPORT_ARM = ITEMS.registerSimpleBlockItem(ModBlocks.TRANSPORT_ARM);
    public static final DeferredItem<BlockItem> ASSEMBLY_ARM = ITEMS.registerSimpleBlockItem(ModBlocks.ASSEMBLY_ARM);
    public static final DeferredItem<BlockItem> ASSEMBLER_TERMINAL = ITEMS.registerSimpleBlockItem(ModBlocks.ASSEMBLER_TERMINAL);

    private ModItems() {}
}
