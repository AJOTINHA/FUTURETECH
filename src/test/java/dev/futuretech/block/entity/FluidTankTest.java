package dev.futuretech.block.entity;

import dev.futuretech.item.FluidTankBlockItem;
import dev.futuretech.item.StoredTankFluid;
import dev.futuretech.api.side.SideMode;
import dev.futuretech.api.redstone.RedstoneMode;
import dev.futuretech.api.redstone.RedstoneControlMenu;
import dev.futuretech.menu.FluidTankMenu;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import dev.futuretech.registry.ModBlocks;
import dev.futuretech.registry.ModDataComponents;
import dev.futuretech.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.TagValueInput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static dev.futuretech.block.entity.FluidTankBlockEntity.CAPACITY;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class FluidTankTest {
    private static FluidResource water() { return FluidResource.of(Fluids.WATER); }
    private static FluidResource lava() { return FluidResource.of(Fluids.LAVA); }

    private static FluidTankBlockEntity tank() {
        return new FluidTankBlockEntity(BlockPos.ZERO, ModBlocks.FLUID_TANK.get().defaultBlockState());
    }

    private static int insert(FluidTankBlockEntity tank, FluidResource fluid, int amount) {
        try (var tx = Transaction.openRoot()) {
            int inserted = tank.fluids().insert(fluid, amount, tx);
            tx.commit();
            return inserted;
        }
    }

    private static int extract(FluidTankBlockEntity tank, FluidResource fluid, int amount) {
        try (var tx = Transaction.openRoot()) {
            int extracted = tank.fluids().extract(fluid, amount, tx);
            tx.commit();
            return extracted;
        }
    }

    @Test
    void capacityMixingAndEmptying(MinecraftServer server) {
        var tank = tank();
        assertEquals(0, tank.comparatorSignal());
        assertEquals(1_000, insert(tank, water(), 1_000));
        assertEquals(0, insert(tank, lava(), 1_000));
        assertEquals(0, extract(tank, lava(), 1_000));
        assertEquals(15_000, insert(tank, water(), Integer.MAX_VALUE));
        assertEquals(CAPACITY, tank.contents().getAmount());
        assertEquals(15, tank.comparatorSignal());
        assertEquals(0, insert(tank, water(), 1));
        assertEquals(8_000, extract(tank, water(), 8_000));
        assertEquals(8, tank.comparatorSignal());
        assertEquals(8_000, extract(tank, water(), Integer.MAX_VALUE));
        assertTrue(tank.contents().isEmpty());
        assertEquals(0, tank.comparatorSignal());
        assertEquals(1_000, insert(tank, lava(), 1_000));
    }

    @Test
    void rolledBackTransfersLeaveNoFluidBehind(MinecraftServer server) {
        var tank = tank();
        try (var tx = Transaction.openRoot()) {
            assertEquals(2_000, tank.fluids().insert(water(), 2_000, tx));
        }
        assertTrue(tank.contents().isEmpty());
        insert(tank, water(), 4_000);
        try (var tx = Transaction.openRoot()) {
            assertEquals(4_000, tank.fluids().extract(water(), 4_000, tx));
        }
        assertEquals(4_000, tank.contents().getAmount());
    }

    @Test
    void fluidComponentsPersistThroughSaveAndDroppedItem(MinecraftServer server) {
        var original = tank();
        var namedWater = new FluidStack(Fluids.WATER, 7_250);
        namedWater.set(DataComponents.CUSTOM_NAME, Component.literal("Test water"));
        insert(original, FluidResource.of(namedWater), namedWater.getAmount());
        assertEquals(0, insert(original, water(), 1_000));
        var loaded = tank();
        loaded.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, server.registryAccess(),
                original.saveWithoutMetadata(server.registryAccess())));
        assertTrue(FluidStack.matches(namedWater, loaded.contents()));

        var drop = new ItemStack(ModItems.FLUID_TANK.get());
        drop.applyComponents(loaded.collectComponents());
        assertTrue(FluidStack.matches(namedWater, FluidTankBlockItem.storedFluid(drop)));
        var placed = tank();
        placed.applyComponentsFromItemStack(drop);
        assertTrue(FluidStack.matches(namedWater, placed.contents()));
        assertTrue(drop.getItem().isBarVisible(drop));
        assertEquals(6, drop.getItem().getBarWidth(drop));
        assertFalse(tank().collectComponents().has(ModDataComponents.TANK_FLUID.get()));
    }

    @Test
    void oversizedItemContentsAreClamped(MinecraftServer server) {
        var item = new ItemStack(ModItems.FLUID_TANK.get());
        item.set(ModDataComponents.TANK_FLUID.get(), StoredTankFluid.of(new FluidStack(Fluids.LAVA, CAPACITY * 3)));
        var placed = tank();
        placed.applyComponentsFromItemStack(item);
        assertEquals(CAPACITY, placed.contents().getAmount());
    }

    @Test
    void initialAndLivePacketsShowTheFluidWithoutMutatingStorage(MinecraftServer server) {
        var source = tank();
        var client = tank();
        for (int amount : new int[]{0, 1_000, 8_000, CAPACITY, 0}) {
            extract(source, water(), CAPACITY);
            insert(source, water(), amount);
            client.onDataPacket(null, TagValueInput.create(ProblemReporter.DISCARDING, server.registryAccess(),
                    source.getUpdateTag(server.registryAccess())));
            assertEquals(amount / (float)CAPACITY, client.visualFill(0), 0.00001F);
            if (amount > 0) assertEquals(Fluids.WATER, client.visualFluid().getFluid());
            assertTrue(client.contents().isEmpty());
        }
        insert(source, lava(), 4_000);
        client.handleUpdateTag(TagValueInput.create(ProblemReporter.DISCARDING, server.registryAccess(),
                source.getUpdateTag(server.registryAccess())));
        assertEquals(Fluids.LAVA, client.visualFluid().getFluid());
        assertEquals(0.25F, client.visualFill(0));
    }

    @Test
    void bucketsTransferWholeAmountsWithoutLoss(MinecraftServer server) {
        var tank = tank();
        var inventory = new ItemStacksResourceHandler(1);
        inventory.set(0, ItemResource.of(Items.WATER_BUCKET), 1);
        var access = ItemAccess.forHandlerIndex(inventory, 0).oneByOne();
        var bucket = access.getCapability(Capabilities.Fluid.ITEM);
        assertNotNull(bucket);
        assertEquals(1_000, ResourceHandlerUtil.move(bucket, tank.fluids(), f -> true, Integer.MAX_VALUE, null));
        assertEquals(1_000, tank.contents().getAmount());
        assertTrue(inventory.getResource(0).is(Items.BUCKET));
        bucket = access.getCapability(Capabilities.Fluid.ITEM);
        assertEquals(1_000, ResourceHandlerUtil.move(tank.fluids(), bucket, f -> true, Integer.MAX_VALUE, null));
        assertTrue(tank.contents().isEmpty());
        assertTrue(inventory.getResource(0).is(Items.WATER_BUCKET));

        insert(tank, water(), CAPACITY - 500);
        bucket = access.getCapability(Capabilities.Fluid.ITEM);
        assertEquals(0, ResourceHandlerUtil.move(bucket, tank.fluids(), f -> true, Integer.MAX_VALUE, null));
        assertEquals(CAPACITY - 500, tank.contents().getAmount());
        assertTrue(inventory.getResource(0).is(Items.WATER_BUCKET));
        inventory.set(0, ItemResource.of(Items.LAVA_BUCKET), 1);
        bucket = access.getCapability(Capabilities.Fluid.ITEM);
        assertEquals(0, ResourceHandlerUtil.move(bucket, tank.fluids(), f -> true, Integer.MAX_VALUE, null));
        assertEquals(CAPACITY - 500, tank.contents().getAmount());
        assertTrue(inventory.getResource(0).is(Items.LAVA_BUCKET));
        assertEquals(1, inventory.getAmountAsInt(0));
    }

    @Test
    void allFacesStartClosedAndRecipeLoads(MinecraftServer server) {
        var tank = tank();
        var state = tank.getBlockState();
        // The provider only needs the supplied block entity; the ephemeral server has no levels.
        for (var side : Direction.values()) {
            assertEquals(SideMode.NONE, tank.sideConfig().mode(side));
            assertNull(Capabilities.Fluid.BLOCK.getCapability(null, BlockPos.ZERO, state, tank, side));
            tank.sideConfig().set(side, SideMode.BOTH);
            var handler = Capabilities.Fluid.BLOCK.getCapability(null, BlockPos.ZERO, state, tank, side);
            assertNotNull(handler);
            assertEquals(CAPACITY, handler.getCapacityAsInt(0, water()));
            assertEquals(SideMode.BOTH, tank.sideConfig().mode(side));
        }
        assertSame(tank.fluids(), Capabilities.Fluid.BLOCK.getCapability(null, BlockPos.ZERO, state, tank, null));
        assertFalse(state.canOcclude());
        assertEquals(net.minecraft.world.phys.shapes.Shapes.block(), state.getCollisionShape(null, BlockPos.ZERO));
        assertTrue(server.getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE,
                Identifier.fromNamespaceAndPath("futuretech", "fluid_tank"))).isPresent());
    }

    @Test
    void guiInputFillsAnEmptyBucketAndStopsBehindTheFilledOutput(MinecraftServer server) {
        var tank = tank();
        insert(tank, water(), 3_000);
        tank.inventory().setItem(0, new ItemStack(Items.BUCKET, 3));
        assertTrue(tank.processContainer());
        assertEquals(2_000, tank.contents().getAmount());
        assertEquals(2, tank.inventory().getItem(0).getCount());
        assertTrue(tank.inventory().getItem(1).is(Items.WATER_BUCKET));
        assertFalse(tank.processContainer());
        assertEquals(2_000, tank.contents().getAmount());
        assertEquals(2, tank.inventory().getItem(0).getCount());
        tank.inventory().removeItem(1, 1);
        assertTrue(tank.processContainer());
        assertEquals(1_000, tank.contents().getAmount());
        assertEquals(1, tank.inventory().getItem(0).getCount());
    }

    @Test
    void guiInputEmptiesFilledBucketsAndStacksOnlyCompatibleResults(MinecraftServer server) {
        var tank = tank();
        tank.inventory().setItem(0, new ItemStack(Items.LAVA_BUCKET));
        tank.inventory().setItem(1, new ItemStack(Items.BUCKET, 15));
        assertTrue(tank.processContainer());
        assertEquals(Fluids.LAVA, tank.contents().getFluid());
        assertEquals(1_000, tank.contents().getAmount());
        assertTrue(tank.inventory().getItem(0).isEmpty());
        assertEquals(16, tank.inventory().getItem(1).getCount());

        tank.inventory().setItem(0, new ItemStack(Items.LAVA_BUCKET));
        assertFalse(tank.processContainer());
        assertEquals(1_000, tank.contents().getAmount());
        assertTrue(tank.inventory().getItem(0).is(Items.LAVA_BUCKET));
        tank.inventory().setItem(1, new ItemStack(Items.STONE));
        assertFalse(tank.processContainer());
        assertEquals(1_000, tank.contents().getAmount());
        assertTrue(tank.inventory().getItem(1).is(Items.STONE));
    }

    @Test
    void guiWaitsForEnoughFluidSpaceOrMatchingFluid(MinecraftServer server) {
        var tank = tank();
        tank.inventory().setItem(0, new ItemStack(Items.BUCKET));
        assertFalse(tank.processContainer());
        insert(tank, water(), 999);
        assertFalse(tank.processContainer());
        assertEquals(999, tank.contents().getAmount());
        assertTrue(tank.inventory().getItem(0).is(Items.BUCKET));
        assertTrue(tank.inventory().getItem(1).isEmpty());

        tank.inventory().setItem(0, new ItemStack(Items.LAVA_BUCKET));
        assertFalse(tank.processContainer());
        assertEquals(999, tank.contents().getAmount());
        insert(tank, water(), CAPACITY);
        tank.inventory().setItem(0, new ItemStack(Items.WATER_BUCKET));
        assertFalse(tank.processContainer());
        extract(tank, water(), 999);
        assertFalse(tank.processContainer());
        assertEquals(CAPACITY - 999, tank.contents().getAmount());
        extract(tank, water(), 1);
        assertTrue(tank.processContainer());
        assertEquals(CAPACITY, tank.contents().getAmount());
        assertTrue(tank.inventory().getItem(1).is(Items.BUCKET));
    }

    @Test
    void guiSlotsSurviveReloadAndCompletedOutputNeverFeedsBack(MinecraftServer server) {
        var tank = tank();
        tank.inventory().setItem(0, new ItemStack(Items.WATER_BUCKET));
        assertTrue(tank.processContainer());
        assertFalse(tank.processContainer());
        assertEquals(1_000, tank.contents().getAmount());
        tank.inventory().setItem(0, new ItemStack(Items.BUCKET, 4));
        var restored = tank();
        restored.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, server.registryAccess(),
                tank.saveWithoutMetadata(server.registryAccess())));
        assertEquals(1_000, restored.contents().getAmount());
        assertTrue(restored.inventory().getItem(0).is(Items.BUCKET));
        assertEquals(4, restored.inventory().getItem(0).getCount());
        assertTrue(restored.inventory().getItem(1).is(Items.BUCKET));
        assertFalse(restored.processContainer());
        assertEquals(1_000, restored.contents().getAmount());
    }

    @Test
    void guiInputAcceptsOnlyFluidContainers(MinecraftServer server) {
        var tank = tank();
        assertTrue(tank.inventory().canPlaceItem(0, new ItemStack(Items.BUCKET)));
        assertTrue(tank.inventory().canPlaceItem(0, new ItemStack(Items.WATER_BUCKET)));
        assertTrue(tank.inventory().canPlaceItem(0, new ItemStack(Items.LAVA_BUCKET)));
        assertFalse(tank.inventory().canPlaceItem(0, new ItemStack(Items.STONE)));
        assertFalse(tank.inventory().canPlaceItem(0, ItemStack.EMPTY));
        assertFalse(tank.inventory().canPlaceItem(1, new ItemStack(Items.BUCKET)));
    }

    @Test
    void sidesAndRedstoneGateCachedFluidHandlers(MinecraftServer server) {
        var tank = tank();
        tank.sideConfig().set(Direction.NORTH, SideMode.BOTH);
        var handler = tank.handler(Direction.NORTH);
        assertNotNull(handler);
        tank.sideConfig().set(Direction.NORTH, SideMode.INPUT);
        try (var tx = Transaction.openRoot()) {
            assertEquals(1_000, handler.insert(water(), 1_000, tx));
            assertEquals(0, handler.extract(water(), 1_000, tx));
            tx.commit();
        }
        tank.sideConfig().set(Direction.NORTH, SideMode.OUTPUT);
        try (var tx = Transaction.openRoot()) {
            assertEquals(0, handler.insert(water(), 1_000, tx));
            assertEquals(500, handler.extract(water(), 500, tx));
            tx.commit();
        }
        tank.sideConfig().set(Direction.NORTH, SideMode.NONE);
        assertNull(tank.handler(Direction.NORTH));
        try (var tx = Transaction.openRoot()) {
            assertEquals(0, handler.insert(water(), 1_000, tx));
            assertEquals(0, handler.extract(water(), 1_000, tx));
        }
        tank.sideConfig().set(Direction.NORTH, SideMode.BOTH);
        tank.redstoneControl().setMode(RedstoneMode.HIGH);
        tank.inventory().setItem(0, new ItemStack(Items.WATER_BUCKET));
        assertFalse(tank.processContainer());
        try (var tx = Transaction.openRoot()) {
            assertEquals(0, handler.insert(water(), 1_000, tx));
            assertEquals(0, handler.extract(water(), 1_000, tx));
        }
        assertEquals(500, tank.contents().getAmount());
        tank.redstoneControl().setMode(RedstoneMode.LOW);
        assertTrue(tank.processContainer());
        assertEquals(1_500, tank.contents().getAmount());
    }

    @Test
    void configuredFacesReachRenderSnapshotsOnLoadAndLiveUpdates(MinecraftServer server) {
        var source = tank();
        var client = tank();
        for (SideMode mode : SideMode.values()) {
            source.sideConfig().set(Direction.NORTH, mode);
            source.sideConfig().set(Direction.UP, mode);
            var packet = source.getUpdateTag(server.registryAccess());
            client.onDataPacket(null, TagValueInput.create(ProblemReporter.DISCARDING, server.registryAccess(), packet));
            int modes = client.getModelData().get(dev.futuretech.api.side.SideConfigVisuals.FACE_MODES);
            assertEquals(mode, dev.futuretech.api.side.SideConfigVisuals.mode(modes, Direction.NORTH));
            assertEquals(mode, dev.futuretech.api.side.SideConfigVisuals.mode(modes, Direction.UP));
            assertEquals(SideMode.NONE, dev.futuretech.api.side.SideConfigVisuals.mode(modes, Direction.SOUTH));
        }
        var loaded = tank();
        loaded.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, server.registryAccess(),
                source.saveWithoutMetadata(server.registryAccess())));
        assertEquals(source.getModelData().get(dev.futuretech.api.side.SideConfigVisuals.FACE_MODES),
                loaded.getModelData().get(dev.futuretech.api.side.SideConfigVisuals.FACE_MODES));
    }

    @Test
    void tabsPersistConfigurationAndUpgrades(MinecraftServer server) {
        var tank = tank();
        tank.sideConfig().set(Direction.UP, SideMode.INPUT);
        tank.sideConfig().set(Direction.DOWN, SideMode.OUTPUT);
        tank.sideConfig().set(Direction.SOUTH, SideMode.NONE);
        tank.redstoneControl().setMode(RedstoneMode.HIGH);
        tank.upgrades().setItem(2, new ItemStack(ModItems.SPEED_UPGRADE.get()));
        var restored = tank();
        restored.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, server.registryAccess(),
                tank.saveWithoutMetadata(server.registryAccess())));
        assertEquals(SideMode.INPUT, restored.sideConfig().mode(Direction.UP));
        assertEquals(SideMode.OUTPUT, restored.sideConfig().mode(Direction.DOWN));
        assertEquals(SideMode.NONE, restored.sideConfig().mode(Direction.SOUTH));
        assertEquals(RedstoneMode.HIGH, restored.redstoneControl().mode());
        assertTrue(restored.upgrades().getItem(2).is(ModItems.SPEED_UPGRADE.get()));
    }

    @Test
    void menuHasOutputProtectionUpgradeSlotsAndWorkingTabButtons(MinecraftServer server) {
        var tank = tank();
        var inventory = new Inventory(null, new EntityEquipment());
        var menu = new FluidTankMenu(1, inventory, tank);
        assertEquals(42, menu.slots.size());
        assertEquals(FluidTankMenu.INPUT_X, menu.slots.get(0).x);
        assertEquals(FluidTankMenu.OUTPUT_X, menu.slots.get(1).x);
        assertFalse(menu.slots.get(1).mayPlace(new ItemStack(Items.BUCKET)));
        assertTrue(menu.slots.get(38).mayPlace(new ItemStack(ModItems.SPEED_UPGRADE.get())));
        assertFalse(menu.slots.get(38).mayPlace(new ItemStack(Items.BUCKET)));
        assertTrue(menu.clickMenuButton(null, Direction.UP.ordinal()));
        assertEquals(SideMode.INPUT, menu.sideMode(Direction.UP));
        assertTrue(menu.clickMenuButton(null, RedstoneControlMenu.BUTTON_BASE + RedstoneMode.HIGH.ordinal()));
        assertEquals(RedstoneMode.HIGH, menu.redstoneMode());
        assertFalse(menu.clickMenuButton(null, 999));
    }
}
