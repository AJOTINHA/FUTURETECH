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
                    .icon(() -> ModItems.MACHINE_CASING.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.MACHINE_CASING.get());
                        output.accept(ModItems.SOLID_FUEL_GENERATOR.get());
                        output.accept(ModItems.ELECTRIC_FURNACE.get());
                        output.accept(ModItems.CRUSHER.get());
                        output.accept(ModItems.BATTERY_MK1.get());
                        output.accept(ModItems.CABLE_MK1.get());
                        output.accept(ModItems.ITEM_CABLE_OPAQUE.get());
                        output.accept(ModItems.ITEM_CABLE.get());
                        output.accept(ModItems.FILTER.get());
                        output.accept(ModItems.SPEED_UPGRADE.get());
                        output.accept(ModItems.IRON_POWDER.get());
                        output.accept(ModItems.GOLD_POWDER.get());
                        output.accept(ModItems.COPPER_POWDER.get());
                    })
                    .build());

    private ModCreativeTabs() {}
}
