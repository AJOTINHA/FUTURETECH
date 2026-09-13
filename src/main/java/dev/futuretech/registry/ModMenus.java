package dev.futuretech.registry;

import dev.futuretech.FutureTech;
import dev.futuretech.menu.BatteryMenu;
import dev.futuretech.menu.ElectricFurnaceMenu;
import dev.futuretech.menu.CrusherMenu;
import dev.futuretech.menu.SolidFuelGeneratorMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> TYPES = DeferredRegister.create(
            Registries.MENU, FutureTech.MOD_ID);
    public static final DeferredHolder<MenuType<?>, MenuType<SolidFuelGeneratorMenu>> SOLID_FUEL_GENERATOR = TYPES.register(
            "solid_fuel_generator", () -> new MenuType<>(SolidFuelGeneratorMenu::new, FeatureFlags.DEFAULT_FLAGS));

    public static final DeferredHolder<MenuType<?>, MenuType<ElectricFurnaceMenu>> ELECTRIC_FURNACE = TYPES.register(
            "electric_furnace", () -> new MenuType<>(ElectricFurnaceMenu::new, FeatureFlags.DEFAULT_FLAGS));

    public static final DeferredHolder<MenuType<?>, MenuType<BatteryMenu>> BATTERY = TYPES.register(
            "battery", () -> new MenuType<>(BatteryMenu::new, FeatureFlags.DEFAULT_FLAGS));

    public static final DeferredHolder<MenuType<?>, MenuType<CrusherMenu>> CRUSHER = TYPES.register(
            "crusher", () -> new MenuType<>(CrusherMenu::new, FeatureFlags.DEFAULT_FLAGS));

    private ModMenus() {}
}
