package dev.futuretech.client;

import dev.futuretech.FutureTech;
import dev.futuretech.block.BatteryBlock;
import dev.futuretech.block.CableBlock;
import dev.futuretech.api.side.SideConfigurableBlock;
import dev.futuretech.api.side.SideMode;
import dev.futuretech.api.side.client.ConfiguredSideModel;
import dev.futuretech.registry.ModMenus;
import dev.futuretech.registry.ModBlockEntities;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.core.Direction;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
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
        modEventBus.addListener(FutureTechClient::registerRenderers);
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.BATTERY.get(), context -> new BatterySphereRenderer());
    }

    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.SOLID_FUEL_GENERATOR.get(), SolidFuelGeneratorScreen::new);
        event.register(ModMenus.BATTERY.get(), BatteryScreen::new);
        event.register(ModMenus.ELECTRIC_FURNACE.get(), ElectricFurnaceScreen::new);
        event.register(ModMenus.CRUSHER.get(), CrusherScreen::new);
    }

    private static void configureSideModels(ModelEvent.ModifyBakingResult event) {
        Function<Identifier, TextureAtlasSprite> textures = event.getTextureGetter();
        Map<SideMode, TextureAtlasSprite> sprites = new EnumMap<>(SideMode.class);
        SIDE_TEXTURES.forEach((mode, path) ->
                sprites.put(mode, textures.apply(Identifier.fromNamespaceAndPath(FutureTech.MOD_ID, path))));
        Map<SideMode, TextureAtlasSprite> batterySprites = new EnumMap<>(SideMode.class);
        Map<SideMode, TextureAtlasSprite> batteryPreviews = new EnumMap<>(SideMode.class);
        for (SideMode mode : new SideMode[]{SideMode.NONE, SideMode.INPUT, SideMode.OUTPUT}) {
            String suffix = switch (mode) {
                case INPUT -> "_input";
                case OUTPUT -> "_output";
                default -> "";
            };
            batteryPreviews.put(mode, textures.apply(Identifier.fromNamespaceAndPath(
                    FutureTech.MOD_ID, "block/battery_frame_preview" + suffix)));
            if (mode != SideMode.NONE) batterySprites.put(mode, textures.apply(Identifier.fromNamespaceAndPath(
                    FutureTech.MOD_ID, "block/battery_port" + suffix)));
        }
        Map<Direction, Map<SideMode, BlockStateModelPart>> batteryPorts = new EnumMap<>(Direction.class);
        TextureAtlasSprite steel = textures.apply(Identifier.fromNamespaceAndPath(FutureTech.MOD_ID, "block/machine_side"));
        TextureAtlasSprite connectorTexture = textures.apply(Identifier.fromNamespaceAndPath(FutureTech.MOD_ID, "block/cable_connector"));
        Map<Direction, BlockStateModelPart> cableConnectors = new EnumMap<>(Direction.class);
        TextureAtlasSprite cableContact = textures.apply(Identifier.fromNamespaceAndPath(FutureTech.MOD_ID, "block/cable_contact"));
        for (Direction side : Direction.values()) cableConnectors.put(side, new CableConnectorModelPart(side, connectorTexture, cableContact));
        for (Direction side : Direction.values()) {
            batteryPorts.put(side, Map.of(
                    SideMode.INPUT, new BatteryPortModelPart(side, steel, batterySprites.get(SideMode.INPUT)),
                    SideMode.OUTPUT, new BatteryPortModelPart(side, steel, batterySprites.get(SideMode.OUTPUT))));
        }
        event.getBakingResult().blockStateModels().replaceAll((state, model) -> {
            if (state.getBlock() instanceof CableBlock) return new CableConnectorModel(model, cableConnectors);
            if (state.getBlock() instanceof BatteryBlock) return new ConfiguredSideModel(model, Map.of(), batteryPreviews, batteryPorts);
            return state.getBlock() instanceof SideConfigurableBlock ? new ConfiguredSideModel(model, sprites) : model;
        });
    }
}
