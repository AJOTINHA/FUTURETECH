package dev.futuretech.item;

import dev.futuretech.block.entity.NetworkPanelBlockEntity;
import dev.futuretech.registry.ModBlocks;
import dev.futuretech.registry.ModItems;
import dev.futuretech.teleport.LinkSlots;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/**
 * The link slots of a network panel are where a portable teleporter learns its network: put in
 * the top slot, it comes out of the bottom one carrying the panel's position, and it stays
 * there while the bottom slot is taken. Only a real level has a panel to put it through.
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = "futuretech")
public class PortableTeleporterGameTests {
    private static void aTeleporterPutInTheLinkSlotComesOutLinkedToThePanel(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        level.setBlock(pos, ModBlocks.NETWORK_PANEL.get().defaultBlockState(), Block.UPDATE_ALL);
        if (!(level.getBlockEntity(pos) instanceof NetworkPanelBlockEntity panel)) throw new IllegalStateException("no panel");
        var teleporter = ModItems.PORTABLE_TELEPORTER.get().getDefaultInstance();
        if (PortableTeleporterItem.link(teleporter) != null) throw new IllegalStateException("a new teleporter is linked");

        panel.link().setItem(LinkSlots.IN, teleporter);
        if (!panel.link().getItem(LinkSlots.IN).isEmpty()) throw new IllegalStateException("the in slot kept it");
        ItemStack linked = panel.link().getItem(LinkSlots.OUT);
        if (!(linked.getItem() instanceof PortableTeleporterItem)) throw new IllegalStateException("nothing came out");
        GlobalPos link = PortableTeleporterItem.link(linked);
        if (!GlobalPos.of(level.dimension(), pos).equals(link)) throw new IllegalStateException("linked to " + link);

        // With the out slot taken, the next one waits in the in slot, unlinked.
        var another = ModItems.PORTABLE_TELEPORTER.get().getDefaultInstance();
        panel.link().setItem(LinkSlots.IN, another);
        if (panel.link().getItem(LinkSlots.IN).isEmpty()) throw new IllegalStateException("the second one moved with the out slot full");
        if (PortableTeleporterItem.link(panel.link().getItem(LinkSlots.IN)) != null) throw new IllegalStateException("linked while waiting");
        panel.link().setItem(LinkSlots.OUT, ItemStack.EMPTY);
        if (!panel.link().getItem(LinkSlots.IN).isEmpty()) throw new IllegalStateException("the free out slot did not take it");
        if (PortableTeleporterItem.link(panel.link().getItem(LinkSlots.OUT)) == null) throw new IllegalStateException("the second one is not linked");
        if (PortableTeleporterItem.panel(level.getServer(), panel.link().getItem(LinkSlots.OUT)) != panel) {
            throw new IllegalStateException("the link does not find the panel");
        }
    }

    private static final java.util.Map<String, java.util.function.Consumer<GameTestHelper>> CASES = java.util.Map.of(
            "link", PortableTeleporterGameTests::aTeleporterPutInTheLinkSlotComesOutLinkedToThePanel);

    private static net.minecraft.resources.Identifier id(String name) {
        return net.minecraft.resources.Identifier.fromNamespaceAndPath("futuretech", "portable_teleporter_" + name);
    }

    @net.neoforged.bus.api.SubscribeEvent
    public static void registerFunctions(net.neoforged.neoforge.registries.RegisterEvent event) {
        event.register(net.minecraft.core.registries.Registries.TEST_FUNCTION, registry -> CASES.forEach((name, test) ->
                registry.register(id(name), (java.util.function.Consumer<GameTestHelper>) helper -> {
                    test.accept(helper);
                    helper.succeed();
                })));
    }

    @net.neoforged.bus.api.SubscribeEvent
    public static void register(net.neoforged.neoforge.event.RegisterGameTestsEvent event) {
        var environment = event.registerEnvironment(net.minecraft.resources.Identifier.fromNamespaceAndPath("futuretech", "portable_teleporter"));
        var data = new net.minecraft.gametest.framework.TestData<>(environment,
                net.minecraft.resources.Identifier.fromNamespaceAndPath("futuretech", "portable_battery_empty"), 200, 0, true);
        for (String name : CASES.keySet()) {
            event.registerTest(id(name), new net.minecraft.gametest.framework.FunctionGameTestInstance(
                    net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.TEST_FUNCTION, id(name)), data));
        }
    }
}
