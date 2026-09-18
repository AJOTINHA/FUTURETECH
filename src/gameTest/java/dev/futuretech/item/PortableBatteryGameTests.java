package dev.futuretech.item;

import com.mojang.authlib.GameProfile;
import dev.futuretech.registry.ModDataComponents;
import dev.futuretech.registry.ModItems;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.util.FakePlayer;

import java.util.UUID;

/**
 * The portable battery is ticked by whoever carries it, and a fake player in a real level is
 * the only place to see it charge. In the inventory it charges the other slots; carried
 * anywhere else that ticks it as an inventory would — a Curios slot on the belt — it charges
 * the inventory from there all the same, and never itself nor another portable battery.
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = "futuretech")
public class PortableBatteryGameTests {
    private static void chargesTheInventoryFromTheInventoryAndFromABeltAlike(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "BeltTest"));
        var block = ModItems.BATTERY_MK1.get().getDefaultInstance();
        player.getInventory().setItem(0, block);
        var portable = ModItems.PORTABLE_BATTERY.get().getDefaultInstance();
        portable.set(ModDataComponents.ENERGY.get(), PortableBatteryItem.Tier.MK1.capacity);
        int rate = PortableBatteryItem.Tier.MK1.chargePerTick;

        // Switched off, nothing moves.
        ModItems.PORTABLE_BATTERY.get().inventoryTick(portable, level, player, null);
        assertEquals(0, BatteryBlockItem.storedEnergy(block), "switched off");
        PortableBatteryItem.setActive(portable, true);

        // On the belt: the stack is nowhere in the inventory, and still charges it.
        ModItems.PORTABLE_BATTERY.get().inventoryTick(portable, level, player, null);
        assertEquals(rate, BatteryBlockItem.storedEnergy(block), "charged from the belt");
        assertEquals(PortableBatteryItem.Tier.MK1.capacity - rate, PortableBatteryItem.storedEnergy(portable), "the belt's stack itself was drained");

        // In the inventory: the same, through its slot, and it never charges itself.
        player.getInventory().setItem(5, portable);
        ModItems.PORTABLE_BATTERY.get().inventoryTick(portable, level, player, null);
        assertEquals(2 * rate, BatteryBlockItem.storedEnergy(block), "charged from the inventory");
        assertEquals(PortableBatteryItem.Tier.MK1.capacity - 2 * rate, PortableBatteryItem.storedEnergy(player.getInventory().getItem(5)), "drained through its slot");

        // Another portable battery in the inventory is left alone.
        var other = ModItems.PORTABLE_BATTERY.get().getDefaultInstance();
        player.getInventory().setItem(7, other);
        ModItems.PORTABLE_BATTERY.get().inventoryTick(portable, level, player, null);
        assertEquals(0, PortableBatteryItem.storedEnergy(other), "another portable battery is skipped");
        assertEquals(3 * rate, BatteryBlockItem.storedEnergy(block), "and the block still charges");
        player.discard();
    }

    private static void assertEquals(long expected, long actual, String message) {
        if (expected != actual) throw new IllegalStateException(message + ": " + expected + " != " + actual);
    }

    private static final java.util.Map<String, java.util.function.Consumer<GameTestHelper>> CASES = java.util.Map.of(
            "belt", PortableBatteryGameTests::chargesTheInventoryFromTheInventoryAndFromABeltAlike);

    private static net.minecraft.resources.Identifier id(String name) {
        return net.minecraft.resources.Identifier.fromNamespaceAndPath("futuretech", "portable_battery_" + name);
    }

    /** Plain test functions, the way the area tools' cases are registered. */
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
        var environment = event.registerEnvironment(net.minecraft.resources.Identifier.fromNamespaceAndPath("futuretech", "portable_battery"));
        var data = new net.minecraft.gametest.framework.TestData<>(environment,
                net.minecraft.resources.Identifier.fromNamespaceAndPath("futuretech", "portable_battery_empty"), 200, 0, true);
        for (String name : CASES.keySet()) {
            event.registerTest(id(name), new net.minecraft.gametest.framework.FunctionGameTestInstance(
                    net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.TEST_FUNCTION, id(name)), data));
        }
    }
}
