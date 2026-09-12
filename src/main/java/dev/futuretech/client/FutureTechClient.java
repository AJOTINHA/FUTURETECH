package dev.futuretech.client;

import dev.futuretech.FutureTech;
import dev.futuretech.api.side.SideConfigurableBlock;
import dev.futuretech.api.side.SideMode;
import dev.futuretech.api.side.client.ConfiguredSideModel;
import dev.futuretech.registry.ModMenus;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;

@Mod(value = FutureTech.MOD_ID, dist = Dist.CLIENT)
public final class FutureTechClient {
    /** Face texture per mode; a mode left out here keeps the machine's own side texture. */
    private static final Map<SideMode, String> SIDE_TEXTURES = Map.of(
            SideMode.INPUT, "block/machine_side_input",
            SideMode.OUTPUT, "block/machine_side_output",
            SideMode.BOTH, "block/machine_side_input_output");

    public FutureTechClient(IEventBus modEventBus) {
        modEventBus.addListener(FutureTechClient::registerScreens);
        modEventBus.addListener(FutureTechClient::configureSideModels);
    }

    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.SOLID_FUEL_GENERATOR.get(), SolidFuelGeneratorScreen::new);
        event.register(ModMenus.BATTERY.get(), BatteryScreen::new);
        event.register(ModMenus.ELECTRIC_FURNACE.get(), ElectricFurnaceScreen::new);
    }

    private static void configureSideModels(ModelEvent.ModifyBakingResult event) {
        Function<Identifier, TextureAtlasSprite> textures = event.getTextureGetter();
        Map<SideMode, TextureAtlasSprite> sprites = new EnumMap<>(SideMode.class);
        SIDE_TEXTURES.forEach((mode, path) ->
                sprites.put(mode, textures.apply(Identifier.fromNamespaceAndPath(FutureTech.MOD_ID, path))));
        event.getBakingResult().blockStateModels().replaceAll((state, model) ->
                state.getBlock() instanceof SideConfigurableBlock ? new ConfiguredSideModel(model, sprites) : model);
    }
}
