package dev.futuretech.registry;

import dev.futuretech.FutureTech;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(
            Registries.CREATIVE_MODE_TAB, FutureTech.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register(
            "main", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.futuretech"))
                    .icon(() -> ModItems.UPGRADE_KIT_MK4.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.MACHINE_CASING.get());
                        // A sample facade: any block makes one at the bench, this is the one on show.
                        output.accept(dev.futuretech.item.FacadeItem.sample());
                        output.accept(ModItems.ASSEMBLY_TABLE.get());
                        output.accept(ModItems.TRANSPORT_ARM.get());
                        output.accept(ModItems.ASSEMBLY_ARM.get());
                        output.accept(ModItems.ASSEMBLER_TERMINAL.get());
                        output.accept(ModItems.SOLID_FUEL_GENERATOR.get());
                        output.accept(ModItems.LAVA_GENERATOR.get());
                        output.accept(ModItems.ELECTRIC_FURNACE.get());
                        output.accept(ModItems.CRUSHER.get());
                        output.accept(ModItems.SAWMILL.get());
                        output.accept(ModItems.CHARGER.get());
                        output.accept(ModItems.METAL_PRESS.get());
                        output.accept(ModItems.PLATE_MOLD.get());
                        output.accept(ModItems.GEAR_MOLD.get());
                        output.accept(ModItems.SMELTERY.get());
                        output.accept(ModItems.MELTER.get());
                        output.accept(ModItems.EXTRUDER.get());
                        output.accept(ModItems.PAINT_MACHINE.get());
                        output.accept(ModItems.WATER_PUMP.get());
                        output.accept(ModItems.TELEPORTER.get());
                        output.accept(ModItems.TIME_CONTROLLER.get());
                        output.accept(ModItems.WEATHER_CONTROLLER.get());
                        output.accept(ModItems.RAIN_SENSOR.get());
                        output.accept(ModItems.TELEPORT_CARD.get());
                        output.accept(ModItems.PORTABLE_TELEPORTER.get());
                        output.accept(ModItems.BATTERY_MK1.get());
                        output.accept(ModItems.PORTABLE_BATTERY.get());
                        output.accept(ModItems.PORTABLE_BATTERY_MK2.get());
                        output.accept(ModItems.PORTABLE_BATTERY_MK3.get());
                        output.accept(ModItems.PORTABLE_BATTERY_MK4.get());
                        output.accept(ModItems.FLUID_TANK.get());
                        output.accept(ModItems.ENERGY_CABLE_MK1.get());
                        output.accept(ModItems.ENERGY_CABLE_MK2.get());
                        output.accept(ModItems.ENERGY_CABLE_MK3.get());
                        output.accept(ModItems.ENERGY_CABLE_MK4.get());
                        output.accept(ModItems.ITEM_CABLE_OPAQUE.get());
                        output.accept(ModItems.ITEM_CABLE.get());
                        output.accept(ModItems.FLUID_CABLE_OPAQUE.get());
                        output.accept(ModItems.FLUID_CABLE.get());
                        output.accept(ModItems.NETWORK_CABLE.get());
                        output.accept(ModItems.REDSTONE_CABLE.get());
                        output.accept(ModItems.NETWORK_PANEL.get());
                        output.accept(ModItems.STORAGE_CARDS.get());
                        output.accept(ModItems.TESSERACT.get());
                        output.accept(ModItems.WIRELESS_TRANSMITTER.get());
                        output.accept(ModItems.WIRELESS_RECEIVER.get());
                        output.accept(ModItems.FILTER.get());
                        output.accept(ModItems.FILTER_MK2.get());
                        output.accept(ModItems.FILTER_MK3.get());
                        output.accept(ModItems.FILTER_MK4.get());
                        output.accept(ModItems.SPEED_UPGRADE.get());
                        output.accept(ModItems.EFFICIENCY_UPGRADE.get());
                        output.accept(ModItems.CHIP.get());
                        output.accept(ModItems.UPGRADE_KIT_MK2.get());
                        output.accept(ModItems.UPGRADE_KIT_MK3.get());
                        output.accept(ModItems.UPGRADE_KIT_MK4.get());
                        output.accept(ModItems.WRENCH.get());
                        output.accept(ModItems.WOODEN_HAMMER.get());
                        output.accept(ModItems.STONE_HAMMER.get());
                        output.accept(ModItems.COPPER_HAMMER.get());
                        output.accept(ModItems.IRON_HAMMER.get());
                        output.accept(ModItems.GOLDEN_HAMMER.get());
                        output.accept(ModItems.DIAMOND_HAMMER.get());
                        output.accept(ModItems.NETHERITE_HAMMER.get());
                        output.accept(ModItems.WOODEN_EXCAVATOR.get());
                        output.accept(ModItems.STONE_EXCAVATOR.get());
                        output.accept(ModItems.COPPER_EXCAVATOR.get());
                        output.accept(ModItems.IRON_EXCAVATOR.get());
                        output.accept(ModItems.GOLDEN_EXCAVATOR.get());
                        output.accept(ModItems.DIAMOND_EXCAVATOR.get());
                        output.accept(ModItems.NETHERITE_EXCAVATOR.get());
                        output.accept(ModItems.WOODEN_LUMBER_AXE.get());
                        output.accept(ModItems.STONE_LUMBER_AXE.get());
                        output.accept(ModItems.COPPER_LUMBER_AXE.get());
                        output.accept(ModItems.IRON_LUMBER_AXE.get());
                        output.accept(ModItems.GOLDEN_LUMBER_AXE.get());
                        output.accept(ModItems.DIAMOND_LUMBER_AXE.get());
                        output.accept(ModItems.NETHERITE_LUMBER_AXE.get());
                        output.accept(ModItems.IRON_POWDER.get());
                        output.accept(ModItems.GOLD_POWDER.get());
                        output.accept(ModItems.COPPER_POWDER.get());
                        output.accept(ModItems.IRON_PLATE.get());
                        output.accept(ModItems.STEEL_INGOT.get());
                        output.accept(ModItems.STEEL_PLATE.get());
                        output.accept(ModItems.STEEL_GEAR.get());
                        output.accept(ModItems.GOLD_PLATE.get());
                        output.accept(ModItems.COPPER_PLATE.get());
                        output.accept(ModItems.NETHERITE_PLATE.get());
                        output.accept(ModItems.IRON_GEAR.get());
                        output.accept(ModItems.GOLD_GEAR.get());
                        output.accept(ModItems.COPPER_GEAR.get());
                        output.accept(ModItems.NETHERITE_GEAR.get());
                    })
                    .build());

    private ModCreativeTabs() {}
}
