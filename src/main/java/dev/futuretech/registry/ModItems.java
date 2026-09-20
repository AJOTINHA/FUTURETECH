package dev.futuretech.registry;

import dev.futuretech.block.EnergyCableTier;
import dev.futuretech.FutureTech;
import dev.futuretech.block.BatteryBlock;
import dev.futuretech.block.EnergyCableBlock;
import dev.futuretech.item.BatteryBlockItem;
import dev.futuretech.item.EnergyCableBlockItem;
import dev.futuretech.item.FacadeItem;
import dev.futuretech.item.FluidTankBlockItem;
import dev.futuretech.item.ItemFilterItem;
import dev.futuretech.item.WrenchItem;
import dev.futuretech.item.AreaToolItem;
import dev.futuretech.item.MachineUpgradeKitItem;
import dev.futuretech.item.OreBlockItem;
import dev.futuretech.item.PortableBatteryItem;
import dev.futuretech.item.PortableTeleporterItem;
import dev.futuretech.item.TeleportCardItem;
import dev.futuretech.item.TieredMachineBlockItem;
import net.minecraft.resources.Identifier;
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

    public static final DeferredItem<TieredMachineBlockItem> SOLAR_GENERATOR = ITEMS.registerItem(
            "solar_generator", properties -> new TieredMachineBlockItem(ModBlocks.SOLAR_GENERATOR.get(), properties),
            properties -> properties.useBlockDescriptionPrefix());

    public static final DeferredItem<TieredMachineBlockItem> STEAM_TURBINE = ITEMS.registerItem(
            "steam_turbine", properties -> new TieredMachineBlockItem(ModBlocks.STEAM_TURBINE.get(), properties),
            properties -> properties.useBlockDescriptionPrefix());

    public static final DeferredItem<TieredMachineBlockItem> WIND_GENERATOR = ITEMS.registerItem(
            "wind_generator", properties -> new TieredMachineBlockItem(ModBlocks.WIND_GENERATOR.get(), properties),
            properties -> properties.useBlockDescriptionPrefix());

    public static final DeferredItem<TieredMachineBlockItem> LAVA_GENERATOR = ITEMS.registerItem(
            "lava_generator", properties -> new TieredMachineBlockItem(ModBlocks.LAVA_GENERATOR.get(), properties),
            properties -> properties.useBlockDescriptionPrefix());

    public static final DeferredItem<TieredMachineBlockItem> BOILER = ITEMS.registerItem(
            "boiler", properties -> new TieredMachineBlockItem(ModBlocks.BOILER.get(), properties),
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

    public static final DeferredItem<TieredMachineBlockItem> CHARGER = ITEMS.registerItem(
            "charger", properties -> new TieredMachineBlockItem(ModBlocks.CHARGER.get(), properties),
            properties -> properties.useBlockDescriptionPrefix());

    public static final DeferredItem<Item> IRON_POWDER = ITEMS.registerSimpleItem("iron_powder");
    public static final DeferredItem<TieredMachineBlockItem> METAL_PRESS = ITEMS.registerItem(
            "metal_press", properties -> new TieredMachineBlockItem(ModBlocks.METAL_PRESS.get(), properties),
            properties -> properties.useBlockDescriptionPrefix());
    public static final DeferredItem<TieredMachineBlockItem> MELTER = ITEMS.registerItem(
            "melter", properties -> new TieredMachineBlockItem(ModBlocks.MELTER.get(), properties),
            properties -> properties.useBlockDescriptionPrefix());
    public static final DeferredItem<TieredMachineBlockItem> EXTRUDER = ITEMS.registerItem(
            "extruder", properties -> new TieredMachineBlockItem(ModBlocks.EXTRUDER.get(), properties),
            properties -> properties.useBlockDescriptionPrefix());
    public static final DeferredItem<TieredMachineBlockItem> SMELTERY = ITEMS.registerItem(
            "smeltery", properties -> new TieredMachineBlockItem(ModBlocks.SMELTERY.get(), properties),
            properties -> properties.useBlockDescriptionPrefix());
    public static final DeferredItem<TieredMachineBlockItem> PAINT_MACHINE = ITEMS.registerItem(
            "paint_machine", properties -> new TieredMachineBlockItem(ModBlocks.PAINT_MACHINE.get(), properties),
            properties -> properties.useBlockDescriptionPrefix());
    public static final DeferredItem<TieredMachineBlockItem> WATER_PUMP = ITEMS.registerItem(
            "water_pump", properties -> new TieredMachineBlockItem(ModBlocks.WATER_PUMP.get(), properties),
            properties -> properties.useBlockDescriptionPrefix());
    public static final DeferredItem<TieredMachineBlockItem> TELEPORTER = ITEMS.registerItem(
            "teleporter", properties -> new TieredMachineBlockItem(ModBlocks.TELEPORTER.get(), properties),
            properties -> properties.useBlockDescriptionPrefix());
    public static final DeferredItem<BlockItem> TIME_CONTROLLER = ITEMS.registerSimpleBlockItem(ModBlocks.TIME_CONTROLLER);
    public static final DeferredItem<BlockItem> WEATHER_CONTROLLER = ITEMS.registerSimpleBlockItem(ModBlocks.WEATHER_CONTROLLER);
    public static final DeferredItem<BlockItem> RAIN_SENSOR = ITEMS.registerSimpleBlockItem(ModBlocks.RAIN_SENSOR);
    /** Remembers one teleporter; in another teleporter's slots it is a destination. */
    public static final DeferredItem<TeleportCardItem> TELEPORT_CARD = ITEMS.registerItem("teleport_card", TeleportCardItem::new);
    /** A teleporter to carry: linked through a network panel, it sends the player anywhere that panel's pad can. */
    public static final DeferredItem<PortableTeleporterItem> PORTABLE_TELEPORTER = ITEMS.registerItem(
            "portable_teleporter", PortableTeleporterItem::new, properties -> properties.stacksTo(1));
    // Tin, lead and silver, from the ore to the parts.
    public static final DeferredItem<OreBlockItem> TIN_ORE = registerOre("tin_ore", "tin", ModBlocks.TIN_ORE);
    public static final DeferredItem<OreBlockItem> DEEPSLATE_TIN_ORE = registerOre("deepslate_tin_ore", "tin", ModBlocks.DEEPSLATE_TIN_ORE);
    public static final DeferredItem<BlockItem> TIN_BLOCK = ITEMS.registerSimpleBlockItem("tin_block", ModBlocks.TIN_BLOCK);
    public static final DeferredItem<BlockItem> RAW_TIN_BLOCK = ITEMS.registerSimpleBlockItem("raw_tin_block", ModBlocks.RAW_TIN_BLOCK);
    public static final DeferredItem<Item> RAW_TIN = ITEMS.registerSimpleItem("raw_tin");
    public static final DeferredItem<Item> TIN_INGOT = ITEMS.registerSimpleItem("tin_ingot");
    public static final DeferredItem<Item> TIN_POWDER = ITEMS.registerSimpleItem("tin_powder");
    public static final DeferredItem<Item> TIN_PLATE = ITEMS.registerSimpleItem("tin_plate");
    public static final DeferredItem<Item> TIN_GEAR = ITEMS.registerSimpleItem("tin_gear");
    public static final DeferredItem<OreBlockItem> LEAD_ORE = registerOre("lead_ore", "lead", ModBlocks.LEAD_ORE);
    public static final DeferredItem<OreBlockItem> DEEPSLATE_LEAD_ORE = registerOre("deepslate_lead_ore", "lead", ModBlocks.DEEPSLATE_LEAD_ORE);
    public static final DeferredItem<BlockItem> LEAD_BLOCK = ITEMS.registerSimpleBlockItem("lead_block", ModBlocks.LEAD_BLOCK);
    public static final DeferredItem<BlockItem> RAW_LEAD_BLOCK = ITEMS.registerSimpleBlockItem("raw_lead_block", ModBlocks.RAW_LEAD_BLOCK);
    public static final DeferredItem<Item> RAW_LEAD = ITEMS.registerSimpleItem("raw_lead");
    public static final DeferredItem<Item> LEAD_INGOT = ITEMS.registerSimpleItem("lead_ingot");
    public static final DeferredItem<Item> LEAD_POWDER = ITEMS.registerSimpleItem("lead_powder");
    public static final DeferredItem<Item> LEAD_PLATE = ITEMS.registerSimpleItem("lead_plate");
    public static final DeferredItem<Item> LEAD_GEAR = ITEMS.registerSimpleItem("lead_gear");
    public static final DeferredItem<OreBlockItem> SILVER_ORE = registerOre("silver_ore", "silver", ModBlocks.SILVER_ORE);
    public static final DeferredItem<OreBlockItem> DEEPSLATE_SILVER_ORE = registerOre("deepslate_silver_ore", "silver", ModBlocks.DEEPSLATE_SILVER_ORE);
    public static final DeferredItem<BlockItem> SILVER_BLOCK = ITEMS.registerSimpleBlockItem("silver_block", ModBlocks.SILVER_BLOCK);
    public static final DeferredItem<BlockItem> RAW_SILVER_BLOCK = ITEMS.registerSimpleBlockItem("raw_silver_block", ModBlocks.RAW_SILVER_BLOCK);
    public static final DeferredItem<Item> RAW_SILVER = ITEMS.registerSimpleItem("raw_silver");
    public static final DeferredItem<Item> SILVER_INGOT = ITEMS.registerSimpleItem("silver_ingot");
    public static final DeferredItem<Item> SILVER_POWDER = ITEMS.registerSimpleItem("silver_powder");
    public static final DeferredItem<Item> SILVER_PLATE = ITEMS.registerSimpleItem("silver_plate");
    public static final DeferredItem<Item> SILVER_GEAR = ITEMS.registerSimpleItem("silver_gear");
    /** Alloyed in the smeltery: a silver and a gold ingot make two. */
    public static final DeferredItem<Item> ELECTRUM_INGOT = ITEMS.registerSimpleItem("electrum_ingot");
    public static final DeferredItem<Item> ELECTRUM_POWDER = ITEMS.registerSimpleItem("electrum_powder");
    /** Alloyed in the smeltery from an iron ingot and two redstone; the conductor of the redstone cable. */
    public static final DeferredItem<Item> RED_ALLOY_INGOT = ITEMS.registerSimpleItem("red_alloy_ingot");
    /** Alloyed in the smeltery from a gold ingot and two ender pearls; what the tesseract and the teleporter are built on. */
    public static final DeferredItem<Item> ENDER_ALLOY_INGOT = ITEMS.registerSimpleItem("ender_alloy_ingot");
    /** Thermal's three redstone coils: an ingot wound with a diagonal of redstone; gold, silver and electrum. */
    public static final DeferredItem<Item> RECEPTION_COIL = ITEMS.registerSimpleItem("reception_coil");
    public static final DeferredItem<Item> TRANSMISSION_COIL = ITEMS.registerSimpleItem("transmission_coil");
    public static final DeferredItem<Item> CONDUCTANCE_COIL = ITEMS.registerSimpleItem("conductance_coil");
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
    public static final DeferredItem<PortableBatteryItem> PORTABLE_BATTERY = registerPortableBattery(PortableBatteryItem.Tier.MK1);
    public static final DeferredItem<PortableBatteryItem> PORTABLE_BATTERY_MK2 = registerPortableBattery(PortableBatteryItem.Tier.MK2);
    public static final DeferredItem<PortableBatteryItem> PORTABLE_BATTERY_MK3 = registerPortableBattery(PortableBatteryItem.Tier.MK3);
    public static final DeferredItem<PortableBatteryItem> PORTABLE_BATTERY_MK4 = registerPortableBattery(PortableBatteryItem.Tier.MK4);

    private static DeferredItem<PortableBatteryItem> registerPortableBattery(PortableBatteryItem.Tier tier) {
        return ITEMS.registerItem(tier.itemName(), properties -> new PortableBatteryItem(tier, properties),
                properties -> properties.stacksTo(1));
    }

    public static final DeferredItem<BatteryBlockItem> BATTERY_MK1 = registerBattery(ModBlocks.BATTERY_MK1);

    public static final DeferredItem<FluidTankBlockItem> FLUID_TANK = ITEMS.registerItem(
            "fluid_tank", properties -> new FluidTankBlockItem(ModBlocks.FLUID_TANK.get(), properties),
            properties -> properties.useBlockDescriptionPrefix().stacksTo(1));

    /** An ore's item, whose tooltip says where the ore generates. */
    private static DeferredItem<OreBlockItem> registerOre(String name, String metal, DeferredBlock<net.minecraft.world.level.block.Block> block) {
        return ITEMS.registerItem(name, properties -> new OreBlockItem(block.get(), metal, properties),
                properties -> properties.useBlockDescriptionPrefix());
    }

    private static DeferredItem<BatteryBlockItem> registerBattery(DeferredBlock<BatteryBlock> block) {
        return ITEMS.registerItem(block.getId().getPath(), properties -> new BatteryBlockItem(block.get(), properties),
                properties -> properties.useBlockDescriptionPrefix());
    }

    public static final DeferredItem<EnergyCableBlockItem> ENERGY_CABLE_MK1 = registerCable(ModBlocks.ENERGY_CABLE_MK1);
    public static final DeferredItem<EnergyCableBlockItem> ENERGY_CABLE_MK2 = registerCable(ModBlocks.ENERGY_CABLE_MK2);
    public static final DeferredItem<EnergyCableBlockItem> ENERGY_CABLE_MK3 = registerCable(ModBlocks.ENERGY_CABLE_MK3);
    public static final DeferredItem<EnergyCableBlockItem> ENERGY_CABLE_MK4 = registerCable(ModBlocks.ENERGY_CABLE_MK4);

    /** The energy cable's item says what its tier moves, so every tier gets the same block item. */
    private static DeferredItem<EnergyCableBlockItem> registerCable(DeferredBlock<EnergyCableBlock> block) {
        return ITEMS.registerItem(block.getId().getPath(), properties -> new EnergyCableBlockItem(block.get(), properties),
                properties -> properties.useBlockDescriptionPrefix());
    }

    public static final DeferredItem<BlockItem> ITEM_CABLE_OPAQUE = ITEMS.registerSimpleBlockItem(ModBlocks.ITEM_CABLE_OPAQUE);
    public static final DeferredItem<BlockItem> ITEM_CABLE = ITEMS.registerSimpleBlockItem(ModBlocks.ITEM_CABLE);

    public static final DeferredItem<BlockItem> FLUID_CABLE_OPAQUE = ITEMS.registerSimpleBlockItem(ModBlocks.FLUID_CABLE_OPAQUE);
    public static final DeferredItem<BlockItem> FLUID_CABLE = ITEMS.registerSimpleBlockItem(ModBlocks.FLUID_CABLE);

    public static final DeferredItem<BlockItem> NETWORK_CABLE = ITEMS.registerSimpleBlockItem(ModBlocks.NETWORK_CABLE);
    public static final DeferredItem<BlockItem> REDSTONE_CABLE = ITEMS.registerSimpleBlockItem(ModBlocks.REDSTONE_CABLE);

    public static final DeferredItem<BlockItem> NETWORK_PANEL = ITEMS.registerSimpleBlockItem(ModBlocks.NETWORK_PANEL);
    // Takes the upgrade kits like a machine, so the item carries the MK the way the machines' items do.
    public static final DeferredItem<TieredMachineBlockItem> STORAGE_CARDS = ITEMS.registerItem(
            "storage_cards", properties -> new TieredMachineBlockItem(ModBlocks.STORAGE_CARDS.get(), properties),
            properties -> properties.useBlockDescriptionPrefix());
    public static final DeferredItem<BlockItem> TESSERACT = ITEMS.registerSimpleBlockItem(ModBlocks.TESSERACT);
    public static final DeferredItem<BlockItem> WIRELESS_TRANSMITTER = ITEMS.registerSimpleBlockItem(ModBlocks.WIRELESS_TRANSMITTER);
    public static final DeferredItem<BlockItem> WIRELESS_RECEIVER = ITEMS.registerSimpleBlockItem(ModBlocks.WIRELESS_RECEIVER);

    /** Filter card for item cable connectors; its list and mode live in data components. */
    // One card per MK: the list doubles each time, and from the MK2 up the card carries how
    // closely it reads what it compares.
    public static final DeferredItem<ItemFilterItem> FILTER = registerFilter(ItemFilterItem.Tier.MK1);
    public static final DeferredItem<ItemFilterItem> FILTER_MK2 = registerFilter(ItemFilterItem.Tier.MK2);
    public static final DeferredItem<ItemFilterItem> FILTER_MK3 = registerFilter(ItemFilterItem.Tier.MK3);
    public static final DeferredItem<ItemFilterItem> FILTER_MK4 = registerFilter(ItemFilterItem.Tier.MK4);

    private static DeferredItem<ItemFilterItem> registerFilter(ItemFilterItem.Tier tier) {
        return ITEMS.registerItem(tier.itemName(), properties -> new ItemFilterItem(tier, properties),
                properties -> properties.stacksTo(1));
    }

    public static final DeferredItem<Item> SPEED_UPGRADE = ITEMS.registerSimpleItem("speed_upgrade");
    /** Cuts a machine's energy draw; see {@link dev.futuretech.api.upgrade.UpgradeInventory#EFFICIENCY_PERCENT}. */
    public static final DeferredItem<Item> EFFICIENCY_UPGRADE = ITEMS.registerSimpleItem("efficiency_upgrade");
    /** In a boiler, swaps the fuel slot for a lava tank; see {@link dev.futuretech.block.entity.BoilerBlockEntity#HEAT_PER_LAVA_MB}. */
    public static final DeferredItem<Item> LAVA_UPGRADE = ITEMS.registerSimpleItem("lava_upgrade");
    /** In a boiler, swaps the fuel slot for an energy buffer; see {@link dev.futuretech.block.entity.BoilerBlockEntity#FE_PER_HEAT}. */
    public static final DeferredItem<Item> ENERGY_UPGRADE = ITEMS.registerSimpleItem("energy_upgrade");
    /** In an extruder, swaps the stone products for sand, gravel and their kin. */
    public static final DeferredItem<Item> SAND_UPGRADE = ITEMS.registerSimpleItem("sand_upgrade");
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

    /** The block it wears rides on the stack, so one item covers every kind of cable. */
    public static final DeferredItem<FacadeItem> FACADE = ITEMS.registerItem("facade", FacadeItem::new);

    public static final DeferredItem<BlockItem> ASSEMBLY_TABLE = ITEMS.registerSimpleBlockItem(ModBlocks.ASSEMBLY_TABLE);
    public static final DeferredItem<BlockItem> TRANSPORT_ARM = ITEMS.registerSimpleBlockItem(ModBlocks.TRANSPORT_ARM);
    public static final DeferredItem<BlockItem> ASSEMBLY_ARM = ITEMS.registerSimpleBlockItem(ModBlocks.ASSEMBLY_ARM);
    public static final DeferredItem<BlockItem> ASSEMBLER_TERMINAL = ITEMS.registerSimpleBlockItem(ModBlocks.ASSEMBLER_TERMINAL);

    static {
        // The energy cable used to be plain "cable": worlds and chests from before keep theirs.
        for (var tier : EnergyCableTier.values()) {
            ITEMS.addAlias(Identifier.fromNamespaceAndPath(FutureTech.MOD_ID, "cable_" + tier.getSerializedName()),
                    Identifier.fromNamespaceAndPath(FutureTech.MOD_ID, tier.blockName()));
        }
    }

    private ModItems() {}
}
