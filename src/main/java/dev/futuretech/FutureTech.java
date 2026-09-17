package dev.futuretech;

import dev.futuretech.registry.ModBlocks;
import dev.futuretech.registry.ModCreativeTabs;
import dev.futuretech.registry.ModDataComponents;
import dev.futuretech.registry.ModItems;
import dev.futuretech.registry.ModBlockEntities;
import dev.futuretech.registry.ModMenus;
import dev.futuretech.registry.ModRecipes;
import dev.futuretech.perf.PerfProfiling;
import dev.futuretech.transfer.ItemJourneys;
import dev.futuretech.item.AreaMining;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(FutureTech.MOD_ID)
public final class FutureTech {
    public static final String MOD_ID = "futuretech";

    public FutureTech(IEventBus modEventBus) {
        ModDataComponents.TYPES.register(modEventBus);
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModBlockEntities.TYPES.register(modEventBus);
        ModMenus.TYPES.register(modEventBus);
        ModRecipes.TYPES.register(modEventBus);
        ModRecipes.SERIALIZERS.register(modEventBus);
        ModRecipes.BOOKS.register(modEventBus);
        modEventBus.addListener(ModBlockEntities::registerCapabilities);
        modEventBus.addListener(ItemJourneys::register);
        modEventBus.addListener(PerfProfiling::registerPayloads);
        modEventBus.addListener(dev.futuretech.teleport.TeleporterRenamePayload::register);
        modEventBus.addListener(dev.futuretech.teleport.NetworkPanelPayloads::register);
        NeoForge.EVENT_BUS.addListener(PerfProfiling::registerCommands);
        NeoForge.EVENT_BUS.addListener(PerfProfiling::onServerTick);
        NeoForge.EVENT_BUS.addListener(AreaMining::onBreak);
        NeoForge.EVENT_BUS.addListener(AreaMining::onServerTick);
        NeoForge.EVENT_BUS.addListener(ModRecipes::syncToClients);
        ModCreativeTabs.TABS.register(modEventBus);
    }
}
