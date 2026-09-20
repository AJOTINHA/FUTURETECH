package dev.futuretech.client;

import dev.futuretech.FutureTech;
import dev.futuretech.block.BatteryBlock;
import dev.futuretech.block.FluidTankBlock;
import dev.futuretech.block.AbstractCableBlock;
import dev.futuretech.block.CableKind;
import dev.futuretech.api.side.SideConfigurableBlock;
import dev.futuretech.api.side.SideMode;
import dev.futuretech.api.upgrade.MachineLevel;
import dev.futuretech.api.side.client.ConfiguredSideModel;
import dev.futuretech.registry.ModMenus;
import dev.futuretech.registry.ModBlockEntities;
import dev.futuretech.perf.PerfProfiling;
import dev.futuretech.transfer.ItemJourneys;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.core.Direction;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;

@Mod(value = FutureTech.MOD_ID, dist = Dist.CLIENT)
public final class FutureTechClient {
    /** Face texture per mode; a mode left out here keeps the machine's own side texture. */
    private static final Map<SideMode, String> SIDE_TEXTURES = Map.of(
            SideMode.INPUT, "block/machine/machine_side_input",
            SideMode.OUTPUT, "block/machine/machine_side_output",
            SideMode.BOTH, "block/machine/machine_side_input_output");

    public FutureTechClient(IEventBus modEventBus) {
        modEventBus.addListener(FutureTechClient::registerScreens);
        modEventBus.addListener((net.neoforged.neoforge.client.event.RegisterFluidModelsEvent event) -> {
            var material = new net.minecraft.client.resources.model.sprite.Material(Identifier.fromNamespaceAndPath(FutureTech.MOD_ID, "block/steam"));
            event.register(new net.minecraft.client.renderer.block.FluidModel.Unbaked(material, material, null, null), dev.futuretech.registry.ModFluids.STEAM);
        });
        modEventBus.addListener((net.neoforged.neoforge.client.event.RegisterSpecialModelRendererEvent event) ->
                event.register(Identifier.fromNamespaceAndPath(FutureTech.MOD_ID, "wind_turbine"), WindGeneratorItemRenderer.Unbaked.MAP_CODEC));
        modEventBus.addListener(FutureTechClient::configureSideModels);
        modEventBus.addListener(FutureTechClient::registerRenderers);
        // The tesseract item's core: the End's portal drawn inside the frame, wherever the item is.
        modEventBus.addListener((net.neoforged.neoforge.client.event.RegisterSpecialModelRendererEvent event) ->
                event.register(net.minecraft.resources.Identifier.fromNamespaceAndPath(FutureTech.MOD_ID, "tesseract_core"),
                        TesseractCoreRenderer.Unbaked.MAP_CODEC));
        // The head of a wireless plate on the item: the dish and the hedron, which are not boxes.
        modEventBus.addListener((net.neoforged.neoforge.client.event.RegisterSpecialModelRendererEvent event) ->
                event.register(WirelessHeadRenderer.ID, WirelessHeadRenderer.Unbaked.MAP_CODEC));
        modEventBus.addListener((net.neoforged.neoforge.client.event.RegisterItemModelsEvent event) ->
                event.register(FacadeItemModel.ID, FacadeItemModel.Unbaked.MAP_CODEC));
        // Items travelling through cables are drawn from journeys the server reports.
        ItemJourneys.setDrawer(ItemTravel::add, ItemTravel::end);
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> ItemTravel.tick());
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> FacadeVisibility.tick());
        // /futuretech perf: the server's tick measurements drawn over the blocks and on the HUD.
        PerfProfiling.setViewer(PerfOverlay::accept);
        NeoForge.EVENT_BUS.addListener(PerfOverlay::submitLabels);
        NeoForge.EVENT_BUS.addListener(PerfOverlay::drawTotal);
        NeoForge.EVENT_BUS.addListener(AreaMiningPreview::extractOutline);
        NeoForge.EVENT_BUS.addListener(AreaMiningPreview::extractBreaking);
        // A cable or facade in hand shows where it would go and what it would look like there.
        NeoForge.EVENT_BUS.addListener(CablePlacementPreview::submit);
        NeoForge.EVENT_BUS.addListener(SyncedRecipes::onReceived);
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.ASSEMBLER.get(), AssemblerRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.FLUID_TANK.get(), context -> new FluidTankRenderer());
        event.registerBlockEntityRenderer(ModBlockEntities.BATTERY.get(), context -> new BatterySphereRenderer());
        event.registerBlockEntityRenderer(ModBlockEntities.ITEM_CABLE.get(), ItemCableRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.FLUID_CABLE.get(), FluidCableRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.TELEPORTER.get(), context -> new TeleporterBeamRenderer());
        event.registerBlockEntityRenderer(ModBlockEntities.TESSERACT.get(), context -> new TesseractRenderer());
        event.registerBlockEntityRenderer(ModBlockEntities.WIRELESS_REDSTONE.get(), context -> new WirelessRedstoneRenderer());
        event.registerBlockEntityRenderer(ModBlockEntities.SOLAR_GENERATOR.get(), context -> new SolarPanelRenderer());
        event.registerBlockEntityRenderer(ModBlockEntities.WIND_GENERATOR.get(), context -> new WindRotorRenderer());
    }

    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.ASSEMBLER.get(), AssemblerScreen::new);
        event.register(ModMenus.FLUID_TANK.get(), FluidTankScreen::new);
        event.register(ModMenus.SOLID_FUEL_GENERATOR.get(), SolidFuelGeneratorScreen::new);
        event.register(ModMenus.LAVA_GENERATOR.get(), LavaGeneratorScreen::new);
        event.register(ModMenus.BOILER.get(), BoilerScreen::new);
        event.register(ModMenus.STEAM_TURBINE.get(), SteamTurbineScreen::new);
        event.register(ModMenus.SOLAR_GENERATOR.get(), SolarGeneratorScreen::new);
        event.register(ModMenus.WIND_GENERATOR.get(), WindGeneratorScreen::new);
        event.register(ModMenus.BATTERY.get(), BatteryScreen::new);
        event.register(ModMenus.ELECTRIC_FURNACE.get(), ElectricFurnaceScreen::new);
        event.register(ModMenus.CRUSHER.get(), LaneMachineScreen::new);
        event.register(ModMenus.SAWMILL.get(), LaneMachineScreen::new);
        event.register(ModMenus.CHARGER.get(), ChargerScreen::new);
        event.register(ModMenus.METAL_PRESS.get(), MetalPressScreen::new);
        event.register(ModMenus.SMELTERY.get(), SmelteryScreen::new);
        event.register(ModMenus.MELTER.get(), MelterScreen::new);
        event.register(ModMenus.EXTRUDER.get(), ExtruderScreen::new);
        event.register(ModMenus.PAINT_MACHINE.get(), PaintMachineScreen::new);
        event.register(ModMenus.WATER_PUMP.get(), WaterPumpScreen::new);
        event.register(ModMenus.TELEPORTER.get(), TeleporterScreen::new);
        event.register(ModMenus.CONTROLLER.get(), ControllerScreen::new);
        event.register(ModMenus.NETWORK_PANEL.get(), NetworkPanelScreen::new);
        event.register(ModMenus.STORAGE_CARDS.get(), StorageCardsScreen::new);
        event.register(ModMenus.CABLE_CONNECTOR.get(), CableConnectorScreen::new);
        event.register(ModMenus.ITEM_FILTER.get(), ItemFilterScreen::new);
    }

    private static void configureSideModels(ModelEvent.ModifyBakingResult event) {
        Function<Identifier, TextureAtlasSprite> textures = event.getTextureGetter();
        // The solar panel's renderer draws with the block models' own sprites: the panel top of each level and its side sheet.
        for (int mk = 1; mk <= MachineLevel.MAX; mk++) {
            WindRotorRenderer.setSprites(mk,
                    textures.apply(Identifier.fromNamespaceAndPath(FutureTech.MOD_ID, "block/wind_generator/blade_mk" + mk)),
                    textures.apply(Identifier.fromNamespaceAndPath(FutureTech.MOD_ID, "block/wind_generator/steel")),
                    textures.apply(Identifier.fromNamespaceAndPath(FutureTech.MOD_ID, "block/wind_generator/tower")),
                    textures.apply(Identifier.fromNamespaceAndPath(FutureTech.MOD_ID, "block/wind_generator/housing_mk" + mk)),
                    textures.apply(Identifier.fromNamespaceAndPath(FutureTech.MOD_ID, "block/wind_generator/housing_bottom")),
                    textures.apply(Identifier.fromNamespaceAndPath(FutureTech.MOD_ID, "block/wind_generator/mk" + mk)),
                    textures.apply(Identifier.fromNamespaceAndPath(FutureTech.MOD_ID, "block/wind_generator/lid_mk" + mk)));
            String panel = "block/solar_generator/panel" + (mk == 1 ? "" : "_mk" + mk);
            String side = "block/solar_generator/frame_mk" + mk;
            SolarPanelRenderer.setSprites(mk, textures.apply(Identifier.fromNamespaceAndPath(FutureTech.MOD_ID, panel)),
                    textures.apply(Identifier.fromNamespaceAndPath(FutureTech.MOD_ID, side)),
                    textures.apply(Identifier.fromNamespaceAndPath(FutureTech.MOD_ID, "block/solar_generator/back_mk" + mk)),
                    textures.apply(Identifier.fromNamespaceAndPath(FutureTech.MOD_ID, "block/solar_generator/mast_mk" + mk)));
        }
        Map<SideMode, TextureAtlasSprite> sprites = new EnumMap<>(SideMode.class);
        SIDE_TEXTURES.forEach((mode, path) ->
                sprites.put(mode, textures.apply(Identifier.fromNamespaceAndPath(FutureTech.MOD_ID, path))));
        Map<Integer, Map<SideMode, TextureAtlasSprite>> tierSprites = new java.util.HashMap<>();
        tierSprites.put(1, sprites);
        for (int mk = 2; mk <= 4; mk++) {
            Map<SideMode, TextureAtlasSprite> perTier = new EnumMap<>(SideMode.class);
            String folder = "block/machine/mk" + mk + "/";
            SIDE_TEXTURES.forEach((mode, path) -> perTier.put(mode, textures.apply(
                    Identifier.fromNamespaceAndPath(FutureTech.MOD_ID, folder + path.substring(path.lastIndexOf('/') + 1)))));
            tierSprites.put(mk, perTier);
        }
        Map<SideMode, TextureAtlasSprite> batterySprites = new EnumMap<>(SideMode.class);
        Map<SideMode, TextureAtlasSprite> batteryPreviews = new EnumMap<>(SideMode.class);
        for (SideMode mode : new SideMode[]{SideMode.NONE, SideMode.INPUT, SideMode.OUTPUT}) {
            String suffix = switch (mode) {
                case INPUT -> "_input";
                case OUTPUT -> "_output";
                default -> "";
            };
            batteryPreviews.put(mode, textures.apply(Identifier.fromNamespaceAndPath(
                    FutureTech.MOD_ID, "block/battery/battery_frame_preview" + suffix)));
            if (mode != SideMode.NONE) batterySprites.put(mode, textures.apply(Identifier.fromNamespaceAndPath(
                    FutureTech.MOD_ID, "block/battery/battery_port" + suffix)));
        }
        Map<Direction, Map<SideMode, BlockStateModelPart>> batteryPorts = new EnumMap<>(Direction.class);
        Map<SideMode, TextureAtlasSprite> tankPreviews = new EnumMap<>(batteryPreviews);
        tankPreviews.put(SideMode.BOTH, batteryPreviews.get(SideMode.INPUT));
        Map<Direction, Map<SideMode, BlockStateModelPart>> tankPorts = new EnumMap<>(Direction.class);
        // The frame's material is a UV sheet, not a face icon. Use its assembled front for None.
        tankPreviews.put(SideMode.NONE, batteryPreviews.get(SideMode.NONE));
        TextureAtlasSprite steel = textures.apply(Identifier.fromNamespaceAndPath(FutureTech.MOD_ID, "block/machine/machine_side"));
        // One collar per connector mode: the band on its rim says what that connector does without
        // the player opening it. A closed connector keeps the plain steel, which reads as no band.
        // The bands mean direction, so they are the same blue and orange on every kind; only the
        // contact plugging the bore takes the cable's own colour, so the collars are built once
        // per contact sprite — one per kind, and one per tier of the energy cable.
        Map<SideMode, String> collarTextures = Map.of(
                SideMode.NONE, "block/cable_connector/cable_connector",
                SideMode.OUTPUT, "block/cable_connector/cable_connector_insert",
                SideMode.INPUT, "block/cable_connector/cable_connector_extract",
                SideMode.BOTH, "block/cable_connector/cable_connector_both");
        Map<SideMode, TextureAtlasSprite> collars = new EnumMap<>(SideMode.class);
        collarTextures.forEach((mode, path) ->
                collars.put(mode, textures.apply(Identifier.fromNamespaceAndPath(FutureTech.MOD_ID, path))));
        Map<String, Map<Direction, Map<SideMode, BlockStateModelPart>>> connectorsByContact = new java.util.HashMap<>();
        Function<AbstractCableBlock, Map<Direction, Map<SideMode, BlockStateModelPart>>> connectorsFor = cable ->
                connectorsByContact.computeIfAbsent(cable.contactTexture(), path -> {
                    TextureAtlasSprite contact = textures.apply(Identifier.fromNamespaceAndPath(FutureTech.MOD_ID, path));
                    Map<Direction, Map<SideMode, BlockStateModelPart>> connectors = new EnumMap<>(Direction.class);
                    for (Direction side : Direction.values()) {
                        Map<SideMode, BlockStateModelPart> perMode = new EnumMap<>(SideMode.class);
                        collars.forEach((mode, collar) -> perMode.put(mode, new CableConnectorModelPart(side, collar, contact)));
                        connectors.put(side, perMode);
                    }
                    return connectors;
                });
        for (Direction side : Direction.values()) {
            batteryPorts.put(side, Map.of(
                    SideMode.INPUT, new BatteryPortModelPart(side, steel, batterySprites.get(SideMode.INPUT)),
                    SideMode.OUTPUT, new BatteryPortModelPart(side, steel, batterySprites.get(SideMode.OUTPUT))));
            var input = batterySprites.get(SideMode.INPUT);
            var output = batterySprites.get(SideMode.OUTPUT);
            tankPorts.put(side, Map.of(
                    SideMode.INPUT, new BatteryPortModelPart(side, steel, input, input, 14.55F),
                    SideMode.OUTPUT, new BatteryPortModelPart(side, steel, output, output, 14.55F),
                    SideMode.BOTH, new BatteryPortModelPart(side, steel, input, output, 14.55F)));
        }
        // The panels hold sprites from the atlas that was just thrown away.
        FacadeModelPart.clearCache();
        FacadeModelPart.setPinSprite(steel);
        event.getBakingResult().blockStateModels().replaceAll((state, model) -> {
            if (state.getBlock() instanceof AbstractCableBlock cable) return new CableConnectorModel(model, cable.kind(), connectorsFor.apply(cable));
            if (state.getBlock() instanceof BatteryBlock) return new ConfiguredSideModel(model, Map.of(), batteryPreviews, batteryPorts);
            if (state.getBlock() instanceof FluidTankBlock) return new ConfiguredSideModel(model, Map.of(), tankPreviews, tankPorts);
            // Keep dedicated generator faces visible; the side tab still shows the configured modes.
            if (state.getBlock() instanceof dev.futuretech.block.SolarGeneratorBlock
                    || state.getBlock() instanceof dev.futuretech.block.WindGeneratorBlock) {
                return new ConfiguredSideModel(model, Map.of(), tierSprites.get(MachineLevel.of(state)), Map.of());
            }
            return state.getBlock() instanceof SideConfigurableBlock
                    ? new ConfiguredSideModel(model, tierSprites.get(MachineLevel.of(state))) : model;
        });
    }
}
