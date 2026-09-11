package dev.futuretech;

import dev.futuretech.registry.ModBlocks;
import dev.futuretech.registry.ModCreativeTabs;
import dev.futuretech.registry.ModItems;
import dev.futuretech.registry.ModBlockEntities;
import dev.futuretech.registry.ModMenus;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(FutureTech.MOD_ID)
public final class FutureTech {
    public static final String MOD_ID = "futuretech";

    public FutureTech(IEventBus modEventBus) {
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModBlockEntities.TYPES.register(modEventBus);
        ModMenus.TYPES.register(modEventBus);
        modEventBus.addListener(ModBlockEntities::registerCapabilities);
        ModCreativeTabs.TABS.register(modEventBus);
    }
}
