package dev.futuretech.client;

import dev.futuretech.FutureTech;
import dev.futuretech.registry.ModMenus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@Mod(value = FutureTech.MOD_ID, dist = Dist.CLIENT)
public final class FutureTechClient {
    public FutureTechClient(IEventBus modEventBus) {
        modEventBus.addListener(FutureTechClient::registerScreens);
    }

    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.SOLID_FUEL_GENERATOR.get(), SolidFuelGeneratorScreen::new);
        event.register(ModMenus.BATTERY.get(), BatteryScreen::new);
    }
}
