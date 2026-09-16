package dev.futuretech.item;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.futuretech.api.upgrade.MachineLevel;
import dev.futuretech.block.entity.LaneMachineBlockEntity;
import dev.futuretech.block.entity.ElectricFurnaceBlockEntity;
import dev.futuretech.block.entity.SolidFuelGeneratorBlockEntity;
import dev.futuretech.registry.ModBlocks;
import dev.futuretech.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.functions.CopyBlockState;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class MachineUpgradeKitTest {
    private static List<Block> machines() {
        return List.of(ModBlocks.SOLID_FUEL_GENERATOR.get(), ModBlocks.ELECTRIC_FURNACE.get(), ModBlocks.CRUSHER.get(), ModBlocks.SAWMILL.get(), ModBlocks.CHARGER.get(),
                ModBlocks.METAL_PRESS.get(), ModBlocks.BATTERY_MK1.get(), ModBlocks.FLUID_TANK.get());
    }

    @Test
    void kitsAllowOnlyTheNextLevelOnSupportedMachines() {
        var kits = List.of(ModItems.UPGRADE_KIT_MK2.get(), ModItems.UPGRADE_KIT_MK3.get(), ModItems.UPGRADE_KIT_MK4.get());
        for (int i = 0; i < kits.size(); i++) assertEquals(i + 2, kits.get(i).targetLevel());
        for (Block block : machines()) {
            assertEquals(1, MachineLevel.of(block.defaultBlockState()));
            for (int current = 1; current <= 4; current++) {
                var state = block.defaultBlockState().setValue(MachineLevel.MK, current);
                for (var kit : kits) assertEquals(current + 1 == kit.targetLevel(), MachineLevel.canUpgrade(state, kit.targetLevel()));
                assertFalse(MachineLevel.canUpgrade(state, 5));
                assertFalse(MachineLevel.canUpgrade(state, 1));
            }
        }
        for (var block : List.of(Blocks.STONE, ModBlocks.MACHINE_CASING.get())) {
            for (var kit : kits) assertFalse(MachineLevel.canUpgrade(block.defaultBlockState(), kit.targetLevel()));
        }
    }

    @Test
    void worldStateRoundTripKeepsLevelFacingAndActiveState() {
        for (var block : machines()) for (int mk = 1; mk <= 4; mk++) {
            var state = block.defaultBlockState().setValue(MachineLevel.MK, mk)
                    .setValue(AbstractFurnaceBlock.FACING, Direction.WEST);
            if (state.hasProperty(AbstractFurnaceBlock.LIT)) state = state.setValue(AbstractFurnaceBlock.LIT, true);
            var saved = BlockState.CODEC.encodeStart(NbtOps.INSTANCE, state).getOrThrow();
            assertEquals(state, BlockState.CODEC.parse(NbtOps.INSTANCE, saved).getOrThrow());
        }
    }

    @Test
    void oldWorldStatesWithoutMkLoadAsMk1() {
        for (var block : machines()) {
            var saved = BlockState.CODEC.encodeStart(JsonOps.INSTANCE, block.defaultBlockState()).getOrThrow().getAsJsonObject();
            saved.getAsJsonObject("Properties").remove("mk");
            assertEquals(1, MachineLevel.of(BlockState.CODEC.parse(JsonOps.INSTANCE, saved).getOrThrow()));
        }
    }

    @Test
    void pickedUpMachineComponentsRestoreLevelWithoutRestoringOldFacing() {
        for (var block : machines()) for (int mk = 2; mk <= 4; mk++) {
            var item = new ItemStack(block);
            var properties = BlockItemStateProperties.EMPTY.with(MachineLevel.MK, mk);
            var encoded = BlockItemStateProperties.CODEC.encodeStart(NbtOps.INSTANCE, properties).getOrThrow();
            item.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.CODEC.parse(NbtOps.INSTANCE, encoded).getOrThrow());
            var placement = block.defaultBlockState().setValue(AbstractFurnaceBlock.FACING, Direction.EAST);
            var restored = item.get(DataComponents.BLOCK_STATE).apply(placement);
            assertEquals(mk, MachineLevel.of(restored));
            assertEquals(Direction.EAST, restored.getValue(AbstractFurnaceBlock.FACING));
            assertTrue(item.getHoverName().getString().contains("MK" + mk));
        }
    }

    @Test
    void menusReadTheCurrentLevelAfterAnInPlaceUpgrade() {
        var furnace = new ElectricFurnaceBlockEntity(BlockPos.ZERO, ModBlocks.ELECTRIC_FURNACE.get().defaultBlockState());
        var crusher = new LaneMachineBlockEntity(dev.futuretech.block.LaneMachineKind.CRUSHER, BlockPos.ZERO, ModBlocks.CRUSHER.get().defaultBlockState());
        var generator = new SolidFuelGeneratorBlockEntity(BlockPos.ZERO, ModBlocks.SOLID_FUEL_GENERATOR.get().defaultBlockState());
        for (int mk = 1; mk <= 4; mk++) {
            furnace.setBlockState(furnace.getBlockState().setValue(MachineLevel.MK, mk));
            crusher.setBlockState(crusher.getBlockState().setValue(MachineLevel.MK, mk));
            generator.setBlockState(generator.getBlockState().setValue(MachineLevel.MK, mk));
            assertEquals(mk, furnace.menuData().get(ElectricFurnaceBlockEntity.DATA_MK));
            assertEquals(mk, crusher.menuData().get(LaneMachineBlockEntity.DATA_MK));
            assertEquals(mk, generator.menuData().get(SolidFuelGeneratorBlockEntity.DATA_MK));
        }
    }

    @Test
    void everyMachineDropCopiesTheRegisteredMkProperty() throws Exception {
        for (String machine : List.of("solid_fuel_generator", "electric_furnace", "crusher", "sawmill", "charger", "metal_press", "battery_mk1")) {
            try (var stream = getClass().getResourceAsStream("/data/futuretech/loot_table/blocks/" + machine + ".json")) {
                assertNotNull(stream);
                var table = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
                var function = table.getAsJsonArray("pools").get(0).getAsJsonObject().getAsJsonArray("entries")
                        .get(0).getAsJsonObject().getAsJsonArray("functions").get(0);
                var copy = CopyBlockState.MAP_CODEC.codec().parse(JsonOps.INSTANCE, function).getOrThrow();
                var encoded = CopyBlockState.MAP_CODEC.codec().encodeStart(JsonOps.INSTANCE, copy).getOrThrow().getAsJsonObject();
                assertEquals("mk", encoded.getAsJsonArray("properties").get(0).getAsString());
            }
        }
    }
}
