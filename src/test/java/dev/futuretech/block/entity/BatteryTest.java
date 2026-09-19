package dev.futuretech.block.entity;

import static dev.futuretech.block.entity.BatteryBlockEntity.*;

import dev.futuretech.block.BatteryBlock;
import dev.futuretech.block.BatteryTier;
import dev.futuretech.api.upgrade.MachineLevel;
import dev.futuretech.api.side.SideMode;
import dev.futuretech.api.redstone.RedstoneMode;
import dev.futuretech.energy.EnergySync;
import dev.futuretech.item.BatteryBlockItem;
import dev.futuretech.registry.ModBlocks;
import dev.futuretech.registry.ModDataComponents;
import dev.futuretech.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.level.storage.TagValueInput;
import net.neoforged.neoforge.transfer.energy.EnergyHandlerUtil;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class BatteryTest {
    private static final int CAPACITY = BatteryTier.MK1.capacity();

    @Test
    void frameCollisionIsOpenButClicksStopAtEveryFace(MinecraftServer server) {
        var state = ModBlocks.BATTERY_MK1.get().defaultBlockState();
        var shape = state.getCollisionShape(server.overworld(), BlockPos.ZERO);
        var emptyCentre = net.minecraft.world.level.block.Block.box(3, 0, 3, 13, 16, 13);
        var cornerBeam = net.minecraft.world.level.block.Block.box(0, 4, 0, 2, 12, 2);
        assertFalse(state.canOcclude());
        assertFalse(net.minecraft.world.phys.shapes.Shapes.joinIsNotEmpty(
                shape, emptyCentre, net.minecraft.world.phys.shapes.BooleanOp.AND));
        assertTrue(net.minecraft.world.phys.shapes.Shapes.joinIsNotEmpty(
                shape, cornerBeam, net.minecraft.world.phys.shapes.BooleanOp.AND));
        var centre = new net.minecraft.world.phys.Vec3(0.5, 0.5, 0.5);
        for (var face : Direction.values()) {
            var offset = new net.minecraft.world.phys.Vec3(face.getStepX(), face.getStepY(), face.getStepZ());
            var hit = state.getShape(server.overworld(), BlockPos.ZERO).clip(
                    centre.add(offset.scale(2)), centre.subtract(offset.scale(2)), BlockPos.ZERO);
            assertNotNull(hit, "Clicks must stop at the battery's " + face + " face");
            assertEquals(face, hit.getDirection());
        }
    }

    @Test
    void renderChargeTracksChunkAndLiveUpdatesWithoutChangingStoredEnergy(MinecraftServer server) {
        var client = chargedBattery(123);
        for (int amount : new int[]{0, CAPACITY / 4, CAPACITY / 2, CAPACITY, 0}) {
            var source = chargedBattery(amount);
            var tag = source.getUpdateTag(server.registryAccess());
            assertFalse(tag.contains("Energy"));
            var input = TagValueInput.create(ProblemReporter.DISCARDING, server.registryAccess(), tag);
            if (amount == CAPACITY / 2) client.onDataPacket(null, input);
            else client.handleUpdateTag(input);
            assertEquals((float)amount / CAPACITY, client.visualCharge(0), 0.001F);
            assertEquals(123, client.energy().getAmountAsInt());
        }
        var invalid = chargedBattery(0).getUpdateTag(server.registryAccess());
        invalid.putInt("VisualCharge", 2000);
        client.handleUpdateTag(TagValueInput.create(ProblemReporter.DISCARDING, server.registryAccess(), invalid));
        assertEquals(1.0F, client.visualCharge(0));
        invalid.putInt("VisualCharge", -100);
        client.handleUpdateTag(TagValueInput.create(ProblemReporter.DISCARDING, server.registryAccess(), invalid));
        assertEquals(0.0F, client.visualCharge(0));
    }
    private static BatteryBlockEntity battery() {
        return new BatteryBlockEntity(BlockPos.ZERO, ModBlocks.BATTERY_MK1.get().defaultBlockState());
    }

    private static BatteryBlockEntity chargedBattery(int amount) {
        var battery = battery();
        var stack = new ItemStack(ModItems.BATTERY_MK1.get());
        stack.set(ModDataComponents.ENERGY.get(), amount);
        battery.applyComponentsFromItemStack(stack);
        return battery;
    }

    private static int insert(BatteryBlockEntity battery, int amount) {
        try (var transaction = Transaction.openRoot()) {
            int inserted = battery.energy().insert(amount, transaction);
            transaction.commit();
            return inserted;
        }
    }

    private static int extract(BatteryBlockEntity battery, int amount) {
        try (var transaction = Transaction.openRoot()) {
            int extracted = battery.energy().extract(amount, transaction);
            transaction.commit();
            return extracted;
        }
    }

    @Test
    void chargeAndDischargeAreEachCappedPerTick(MinecraftServer server) {
        var battery = battery();
        battery.beginTick();
        assertEquals(150, insert(battery, 150));
        assertEquals(50, insert(battery, 1000));
        assertEquals(0, insert(battery, 1));
        assertEquals(200, battery.energy().getAmountAsInt());

        // Output has its own budget in the same tick, and both reset on the next one.
        assertEquals(200, extract(battery, 1000));
        assertEquals(0, extract(battery, 1));
        battery.beginTick();
        assertEquals(200, battery.menuData().get(DATA_INPUT));
        assertEquals(200, battery.menuData().get(DATA_OUTPUT));
        assertEquals(200, insert(battery, 1000));
        assertEquals(200, battery.energy().getAmountAsInt());
    }

    @Test
    void upgradesDoubleCapacityAndBothRatesWithoutResettingChargeOrTickBudgets(MinecraftServer server) {
        var battery = chargedBattery(80_000);
        var handler = battery.energy();
        battery.sideConfig().set(Direction.NORTH, SideMode.INPUT);
        battery.sideConfig().set(Direction.SOUTH, SideMode.OUTPUT);
        battery.redstoneControl().setMode(RedstoneMode.HIGH);
        battery.upgrades().setItem(0, new ItemStack(ModItems.SPEED_UPGRADE.get()));
        int previousRate = 200;
        for (int mk = 2; mk <= 4; mk++) {
            battery.beginTick();
            assertEquals(75, insert(battery, 75));
            assertEquals(25, extract(battery, 25));
            int stored = battery.energy().getAmountAsInt();
            battery.setBlockState(battery.getBlockState().setValue(MachineLevel.MK, mk));
            int rate = previousRate * 2;
            assertSame(handler, battery.energy(), "Existing capability references must keep working");
            assertEquals(stored, battery.energy().getAmountAsInt());
            assertEquals(100_000 << (mk - 1), battery.energy().getCapacityAsInt());
            assertEquals(mk - 1, battery.menuData().get(DATA_TIER));
            assertEquals(mk, battery.upgrades().unlocked());
            assertTrue(battery.upgrades().getItem(0).is(ModItems.SPEED_UPGRADE.get()));
            assertEquals(SideMode.INPUT, battery.sideConfig().mode(Direction.NORTH));
            assertEquals(SideMode.OUTPUT, battery.sideConfig().mode(Direction.SOUTH));
            assertEquals(RedstoneMode.HIGH, battery.redstoneControl().mode());
            assertEquals(rate - 75, insert(battery, Integer.MAX_VALUE));
            assertEquals(rate - 25, extract(battery, Integer.MAX_VALUE));
            assertEquals(0, insert(battery, 1));
            assertEquals(0, extract(battery, 1));

            battery.beginTick();
            assertEquals(rate, battery.menuData().get(DATA_INPUT));
            assertEquals(rate, battery.menuData().get(DATA_OUTPUT));
            assertEquals(rate, insert(battery, Integer.MAX_VALUE));
            assertEquals(rate, extract(battery, Integer.MAX_VALUE));
            // Rotating the same tier must not refund its tick budget either.
            battery.setBlockState(battery.getBlockState().setValue(BatteryBlock.FACING, Direction.EAST));
            assertEquals(0, insert(battery, 1));
            assertEquals(0, extract(battery, 1));
            previousRate = rate;
        }
    }

    @Test
    void upgradedTransfersRestoreTheirBudgetsWhenATransactionIsAborted(MinecraftServer server) {
        var battery = chargedBattery(80_000);
        battery.setBlockState(battery.getBlockState().setValue(MachineLevel.MK, 4));
        try (var transaction = Transaction.openRoot()) {
            assertEquals(1_600, battery.energy().insert(10_000, transaction));
            assertEquals(1_600, battery.energy().extract(10_000, transaction));
        }
        assertEquals(80_000, battery.energy().getAmountAsInt());
        assertEquals(1_600, insert(battery, 10_000));
        assertEquals(1_600, extract(battery, 10_000));
    }

    @Test
    void everyTierKeepsItsLevelAndFullChargeThroughItemPlacementAndReload(MinecraftServer server) {
        for (var tier : BatteryTier.values()) {
            var state = ModBlocks.BATTERY_MK1.get().defaultBlockState()
                    .setValue(MachineLevel.MK, tier.ordinal() + 1);
            var original = new BatteryBlockEntity(BlockPos.ZERO, state);
            var charge = new ItemStack(ModItems.BATTERY_MK1.get());
            charge.set(ModDataComponents.ENERGY.get(), tier.capacity());
            original.applyComponentsFromItemStack(charge);
            assertEquals(0, insert(original, 1));
            assertEquals(1000, original.getUpdateTag(server.registryAccess()).getIntOr("VisualCharge", -1));

            var dropped = new ItemStack(ModItems.BATTERY_MK1.get());
            dropped.applyComponents(original.collectComponents());
            dropped.set(DataComponents.BLOCK_STATE,
                    BlockItemStateProperties.EMPTY.with(MachineLevel.MK, tier.ordinal() + 1));
            assertEquals(tier, ModItems.BATTERY_MK1.get().tier(dropped));
            assertEquals(tier.capacity(), BatteryBlockItem.storedEnergy(dropped));
            assertEquals(13, dropped.getItem().getBarWidth(dropped));
            assertTrue(dropped.getHoverName().getString().contains("MK" + (tier.ordinal() + 1)));

            var placed = battery();
            placed.setBlockState(dropped.get(DataComponents.BLOCK_STATE).apply(placed.getBlockState()));
            placed.applyComponentsFromItemStack(dropped);
            assertEquals(tier, placed.tier());
            assertEquals(tier.capacity(), placed.energy().getAmountAsInt());
            var restored = new BatteryBlockEntity(BlockPos.ZERO, placed.getBlockState());
            restored.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, server.registryAccess(),
                    placed.saveWithoutMetadata(server.registryAccess())));
            assertEquals(tier.capacity(), restored.energy().getAmountAsInt());
            assertEquals(tier.capacity(), restored.energy().getCapacityAsInt());
            assertEquals(tier.transferPerTick(), extract(restored, Integer.MAX_VALUE));
        }
    }

    @Test
    void coreColorsMatchTheMachineTextureCorners(MinecraftServer server) throws Exception {
        assertEquals(0xFFFFFF, BatteryTier.MK1.lineColor());
        for (var tier : new BatteryTier[]{BatteryTier.MK2, BatteryTier.MK3, BatteryTier.MK4}) {
            String path = "/assets/futuretech/textures/block/machine/" + tier.getSerializedName() + "/machine_side.png";
            try (var stream = getClass().getResourceAsStream(path)) {
                assertNotNull(stream, path);
                var texture = javax.imageio.ImageIO.read(stream);
                assertEquals(texture.getRGB(0, 0) & 0xFFFFFF, tier.lineColor());
            }
        }
    }

    @Test
    void menuReportsPreviousTickTransfersAndSplitsEnergy(MinecraftServer server) {
        var battery = chargedBattery(70_000);
        battery.beginTick();
        insert(battery, 120);
        extract(battery, 45);
        battery.beginTick();
        assertEquals(120, battery.menuData().get(DATA_INPUT));
        assertEquals(45, battery.menuData().get(DATA_OUTPUT));
        assertEquals(70_075, EnergySync.unpack(
                (short) battery.menuData().get(DATA_ENERGY_LOW), (short) battery.menuData().get(DATA_ENERGY_HIGH)));
        battery.beginTick();
        assertEquals(0, battery.menuData().get(DATA_INPUT));
        assertEquals(0, battery.menuData().get(DATA_OUTPUT));
    }

    @Test
    void generatorOutputChargesTheBatteryAtTheGeneratorsRate(MinecraftServer server) {
        var generator = new SolidFuelGeneratorBlockEntity(BlockPos.ZERO,
                ModBlocks.SOLID_FUEL_GENERATOR.get().defaultBlockState());
        generator.setItem(0, new ItemStack(Items.COAL));
        var battery = battery();
        for (int tick = 0; tick < 100; tick++) {
            generator.beginTick();
            generator.generateEnergy(server.fuelValues());
            battery.beginTick();
            EnergyHandlerUtil.move(generator.energy(), battery.energy(), Integer.MAX_VALUE, null);
        }
        // 40 FE/t generated, drained fully each tick because 80 FE/t output exceeds it.
        assertEquals(100 * SolidFuelGeneratorBlockEntity.GENERATION_PER_TICK, battery.energy().getAmountAsInt());
        assertEquals(0, generator.energy().getAmountAsInt());
    }

    @Test
    void fullBatteryRejectsEnergyAndFeedsAConsumer(MinecraftServer server) {
        var battery = chargedBattery(CAPACITY);
        battery.beginTick();
        assertEquals(0, insert(battery, 100));
        var consumer = new SimpleEnergyHandler(500);
        assertEquals(200, EnergyHandlerUtil.move(battery.energy(), consumer, 500, null));
        assertEquals(CAPACITY - 200, battery.energy().getAmountAsInt());
    }

    @Test
    void savedChargeSurvivesReload(MinecraftServer server) {
        var original = chargedBattery(12_345);
        var restored = battery();
        restored.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, server.registryAccess(),
                original.saveWithoutMetadata(server.registryAccess())));
        assertEquals(12_345, restored.energy().getAmountAsInt());
    }

    @Test
    void chargeTravelsWithTheItemAndBackIntoThePlacedBlock(MinecraftServer server) {
        var placed = chargedBattery(65_000);
        var components = placed.collectComponents();
        assertEquals(65_000, components.get(ModDataComponents.ENERGY.get()));

        var dropped = new ItemStack(ModItems.BATTERY_MK1.get());
        dropped.applyComponents(components);
        assertEquals(65_000, BatteryBlockItem.storedEnergy(dropped));
        assertTrue(dropped.getItem().isBarVisible(dropped));
        assertEquals(8, dropped.getItem().getBarWidth(dropped));

        var replaced = battery();
        replaced.applyComponentsFromItemStack(dropped);
        assertEquals(65_000, replaced.energy().getAmountAsInt());

        // An empty battery carries no component, so fresh items stay stackable and bar-less.
        assertFalse(battery().collectComponents().has(ModDataComponents.ENERGY.get()));
        var fresh = new ItemStack(ModItems.BATTERY_MK1.get());
        assertFalse(fresh.getItem().isBarVisible(fresh));
        assertEquals(0, BatteryBlockItem.storedEnergy(fresh));
    }

    @Test
    void overchargedItemsAreClampedToCapacity(MinecraftServer server) {
        var battery = chargedBattery(CAPACITY * 3);
        assertEquals(CAPACITY, battery.energy().getAmountAsInt());
        var stack = new ItemStack(ModItems.BATTERY_MK1.get());
        stack.set(ModDataComponents.ENERGY.get(), CAPACITY * 3);
        assertEquals(CAPACITY, BatteryBlockItem.storedEnergy(stack));
        assertEquals(13, stack.getItem().getBarWidth(stack));
    }

    @Test
    void tierFlowsFromBlockToEntityItemAndMenu(MinecraftServer server) {
        var battery = battery();
        assertEquals(BatteryTier.MK1, battery.tier());
        assertEquals(BatteryTier.MK1, ModItems.BATTERY_MK1.get().tier());
        assertEquals(BatteryTier.MK1, ModBlocks.BATTERY_MK1.get().tier());
        assertEquals(BatteryTier.MK1.ordinal(), battery.menuData().get(DATA_TIER));
        assertEquals(CAPACITY, battery.energy().getCapacityAsInt());
        assertEquals("battery_mk1", ModBlocks.BATTERY_MK1.getId().getPath());
        assertEquals("battery_mk1", ModItems.BATTERY_MK1.getId().getPath());
    }

    @Test
    void frontFollowsTheFacingPropertyAndEveryFaceStartsClosed(MinecraftServer server) {
        var state = ModBlocks.BATTERY_MK1.get().defaultBlockState();
        assertEquals(Direction.NORTH, state.getValue(BatteryBlock.FACING));
        assertEquals(Direction.NORTH, battery().front());
        var east = new BatteryBlockEntity(BlockPos.ZERO, state.setValue(BatteryBlock.FACING, Direction.EAST));
        assertEquals(Direction.EAST, east.front());
        for (Direction side : Direction.values()) {
            assertFalse(east.sideConfig().allowsEnergyInput(side), side.toString());
            assertFalse(east.sideConfig().allowsEnergyOutput(side), side.toString());
        }
        // The machine's own sideless access is never blocked by the configuration.
        assertTrue(east.sideConfig().allowsEnergyInput(null));
        assertTrue(east.sideConfig().allowsEnergyOutput(null));
    }

    @Test
    void batteryRecipeIsLoadedByTheServer(MinecraftServer server) {
        assertTrue(server.getRecipeManager().byKey(ResourceKey.create(
                Registries.RECIPE, Identifier.fromNamespaceAndPath("futuretech", "battery_mk1"))).isPresent());
    }
}
