package dev.futuretech.item;

import dev.futuretech.registry.ModItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class AreaToolItemTest {
    @Test
    void hammersInheritPickaxeMiningRepairAndEnchantmentsAndHaveRecipes(MinecraftServer server) {
        assertMatchesVanilla(server, List.of(ModItems.WOODEN_HAMMER, ModItems.STONE_HAMMER, ModItems.COPPER_HAMMER,
                        ModItems.IRON_HAMMER, ModItems.GOLDEN_HAMMER, ModItems.DIAMOND_HAMMER, ModItems.NETHERITE_HAMMER),
                List.of(Items.WOODEN_PICKAXE, Items.STONE_PICKAXE, Items.COPPER_PICKAXE, Items.IRON_PICKAXE,
                        Items.GOLDEN_PICKAXE, Items.DIAMOND_PICKAXE, Items.NETHERITE_PICKAXE),
                ItemTags.PICKAXES, BlockTags.MINEABLE_WITH_PICKAXE);
    }

    @Test
    void excavatorsInheritShovelMiningRepairAndEnchantmentsAndHaveRecipes(MinecraftServer server) {
        assertMatchesVanilla(server, List.of(ModItems.WOODEN_EXCAVATOR, ModItems.STONE_EXCAVATOR, ModItems.COPPER_EXCAVATOR,
                        ModItems.IRON_EXCAVATOR, ModItems.GOLDEN_EXCAVATOR, ModItems.DIAMOND_EXCAVATOR, ModItems.NETHERITE_EXCAVATOR),
                List.of(Items.WOODEN_SHOVEL, Items.STONE_SHOVEL, Items.COPPER_SHOVEL, Items.IRON_SHOVEL,
                        Items.GOLDEN_SHOVEL, Items.DIAMOND_SHOVEL, Items.NETHERITE_SHOVEL),
                ItemTags.SHOVELS, BlockTags.MINEABLE_WITH_SHOVEL);
    }

    @Test
    void lumberAxesInheritAxeMiningRepairAndEnchantmentsAndHaveRecipes(MinecraftServer server) {
        assertMatchesVanilla(server, List.of(ModItems.WOODEN_LUMBER_AXE, ModItems.STONE_LUMBER_AXE, ModItems.COPPER_LUMBER_AXE,
                        ModItems.IRON_LUMBER_AXE, ModItems.GOLDEN_LUMBER_AXE, ModItems.DIAMOND_LUMBER_AXE, ModItems.NETHERITE_LUMBER_AXE),
                List.of(Items.WOODEN_AXE, Items.STONE_AXE, Items.COPPER_AXE, Items.IRON_AXE,
                        Items.GOLDEN_AXE, Items.DIAMOND_AXE, Items.NETHERITE_AXE),
                ItemTags.AXES, BlockTags.MINEABLE_WITH_AXE);
        assertEquals(AreaToolItem.Reach.TREE, ModItems.IRON_LUMBER_AXE.get().reach());
        assertEquals(AreaToolItem.Reach.PLANE, ModItems.IRON_HAMMER.get().reach());
        assertEquals(AreaToolItem.Reach.PLANE, ModItems.IRON_EXCAVATOR.get().reach());
    }

    private static void assertMatchesVanilla(MinecraftServer server, List<DeferredItem<AreaToolItem>> tools, List<Item> vanilla,
                                             TagKey<Item> itemTag, TagKey<net.minecraft.world.level.block.Block> mineable) {
        for (int i = 0; i < tools.size(); i++) {
            var tool = tools.get(i).get().getDefaultInstance();
            var reference = vanilla.get(i).getDefaultInstance();
            assertEquals(reference.get(DataComponents.TOOL), tool.get(DataComponents.TOOL));
            assertEquals(reference.getMaxDamage(), tool.getMaxDamage());
            assertEquals(reference.get(DataComponents.REPAIRABLE), tool.get(DataComponents.REPAIRABLE));
            assertEquals(reference.get(DataComponents.ENCHANTABLE), tool.get(DataComponents.ENCHANTABLE));
            assertEquals(reference.get(DataComponents.DAMAGE_RESISTANT), tool.get(DataComponents.DAMAGE_RESISTANT));
            assertTrue(tool.is(itemTag));
            assertEquals(mineable, tools.get(i).get().mineable());
            var id = tools.get(i).getId();
            assertTrue(server.getRecipeManager().getRecipes().stream().anyMatch(recipe -> recipe.id().identifier().equals(id)), id.toString());
        }
    }
}
