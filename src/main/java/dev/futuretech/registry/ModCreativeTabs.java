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
                        output.accept(ModItems.BATTERY_MK1.get());
                    })
                    .build());

    private ModCreativeTabs() {}
}
