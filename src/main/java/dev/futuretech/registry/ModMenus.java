package dev.futuretech.registry;

import dev.futuretech.FutureTech;
import dev.futuretech.menu.BatteryMenu;
import dev.futuretech.menu.FluidTankMenu;
import dev.futuretech.menu.CableConnectorMenu;
import dev.futuretech.menu.ElectricFurnaceMenu;
import dev.futuretech.menu.ItemFilterMenu;
import dev.futuretech.menu.LavaGeneratorMenu;
import dev.futuretech.menu.ChargerMenu;
import dev.futuretech.menu.LaneMachineMenu;
import dev.futuretech.menu.MetalPressMenu;
import dev.futuretech.menu.PaintMachineMenu;
import dev.futuretech.menu.SmelteryMenu;
import dev.futuretech.menu.SolidFuelGeneratorMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> TYPES = DeferredRegister.create(
            Registries.MENU, FutureTech.MOD_ID);
    public static final DeferredHolder<MenuType<?>, MenuType<SolidFuelGeneratorMenu>> SOLID_FUEL_GENERATOR = TYPES.register(
            "solid_fuel_generator", () -> new MenuType<>(SolidFuelGeneratorMenu::new, FeatureFlags.DEFAULT_FLAGS));

    public static final DeferredHolder<MenuType<?>, MenuType<LavaGeneratorMenu>> LAVA_GENERATOR = TYPES.register(
            "lava_generator", () -> new MenuType<>(LavaGeneratorMenu::new, FeatureFlags.DEFAULT_FLAGS));

    public static final DeferredHolder<MenuType<?>, MenuType<ElectricFurnaceMenu>> ELECTRIC_FURNACE = TYPES.register(
            "electric_furnace", () -> IMenuTypeExtension.create(ElectricFurnaceMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<BatteryMenu>> BATTERY = TYPES.register(
            "battery", () -> new MenuType<>(BatteryMenu::new, FeatureFlags.DEFAULT_FLAGS));

    public static final DeferredHolder<MenuType<?>, MenuType<FluidTankMenu>> FLUID_TANK = TYPES.register(
            "fluid_tank", () -> IMenuTypeExtension.create(FluidTankMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<LaneMachineMenu>> CRUSHER = TYPES.register(
            "crusher", () -> IMenuTypeExtension.create(LaneMachineMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<ChargerMenu>> CHARGER = TYPES.register(
            "charger", () -> IMenuTypeExtension.create(ChargerMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<LaneMachineMenu>> SAWMILL = TYPES.register(
            "sawmill", () -> IMenuTypeExtension.create(LaneMachineMenu::new));

    // The face and the kind of cable ride in the opening packet, so this one needs the extra data.
    public static final DeferredHolder<MenuType<?>, MenuType<MetalPressMenu>> METAL_PRESS = TYPES.register(
            "metal_press", () -> IMenuTypeExtension.create(MetalPressMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<SmelteryMenu>> SMELTERY = TYPES.register(
            "smeltery", () -> IMenuTypeExtension.create(SmelteryMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<PaintMachineMenu>> PAINT_MACHINE = TYPES.register(
            "paint_machine", () -> IMenuTypeExtension.create(PaintMachineMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<CableConnectorMenu>> CABLE_CONNECTOR = TYPES.register(
            "cable_connector", () -> IMenuTypeExtension.create(CableConnectorMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<ItemFilterMenu>> ITEM_FILTER = TYPES.register(
            "item_filter", () -> IMenuTypeExtension.create(ItemFilterMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<dev.futuretech.menu.AssemblerMenu>> ASSEMBLER = TYPES.register(
            "assembler", () -> IMenuTypeExtension.create(dev.futuretech.menu.AssemblerMenu::new));
    private ModMenus() {}
}
