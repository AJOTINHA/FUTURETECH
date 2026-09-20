package dev.futuretech.item;

import dev.futuretech.registry.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandlerUtil;
import net.neoforged.neoforge.transfer.energy.ItemAccessEnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

/**
 * A battery carried in the inventory. Switched on with Shift + right click, it feeds every other
 * item in the player's inventory that stores energy, a little each tick, and glints while it does.
 * Right-clicking a block that holds energy fills the battery from it. Each MK doubles the
 * capacity and the charge rate; an upgrade kit in the crafting grid moves a battery up a level.
 */
public final class PortableBatteryItem extends Item {
    /** Capacity and per-tick charge rate of each level; MK1 fills an MK1 battery block in ten seconds. */
    public enum Tier {
        MK1(200_000, 500), MK2(400_000, 1_000), MK3(800_000, 2_000), MK4(1_600_000, 4_000);

        public final int capacity;
        public final int chargePerTick;

        Tier(int capacity, int chargePerTick) {
            this.capacity = capacity;
            this.chargePerTick = chargePerTick;
        }

        public int mk() { return ordinal() + 1; }

        /** Registry name: the MK1 keeps the plain name. */
        public String itemName() { return this == MK1 ? "portable_battery" : "portable_battery_mk" + mk(); }
    }

    public final Tier tier;

    public PortableBatteryItem(Tier tier, Properties properties) {
        super(properties);
        this.tier = tier;
    }

    /** The tier of any stack; MK1 for anything that is not a portable battery. */
    public static Tier tier(ItemStack stack) {
        return stack.getItem() instanceof PortableBatteryItem battery ? battery.tier : Tier.MK1;
    }

    /** The item's own store, backed by the energy component of the stack the access points at. */
    public EnergyHandler handler(ItemAccess access) {
        return new ItemAccessEnergyHandler(access, ModDataComponents.ENERGY.get(), tier.capacity);
    }

    public static int storedEnergy(ItemStack stack) {
        return Math.clamp(stack.getOrDefault(ModDataComponents.ENERGY.get(), 0), 0, tier(stack).capacity);
    }

    public static boolean isActive(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.ACTIVE.get(), false);
    }

    /** Flips the switch; the glint is the vanilla override, so no renderer needs to know about the battery. */
    public static void setActive(ItemStack stack, boolean active) {
        stack.set(ModDataComponents.ACTIVE.get(), active);
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, active);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!player.isShiftKeyDown()) return InteractionResult.PASS;
        toggle(level, player, player.getItemInHand(hand));
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        if (player.isShiftKeyDown()) {
            toggle(context.getLevel(), player, context.getItemInHand());
            return InteractionResult.SUCCESS;
        }
        // A plain click on something that stores energy tops the battery up from it.
        EnergyHandler source = context.getLevel().getCapability(Capabilities.Energy.BLOCK, context.getClickedPos(), context.getClickedFace());
        if (source == null) return InteractionResult.PASS;
        if (context.getLevel().isClientSide()) return InteractionResult.SUCCESS;
        int moved;
        try (var transaction = Transaction.openRoot()) {
            moved = EnergyHandlerUtil.move(source, handler(ItemAccess.forPlayerInteraction(player, context.getHand())), tier.capacity, transaction);
            transaction.commit();
        }
        if (moved > 0) {
            context.getLevel().playSound(null, context.getClickedPos(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 0.4F, 1.6F);
            player.sendOverlayMessage(Component.translatable("item.futuretech.portable_battery.pulled", String.format("%,d", moved)));
        }
        return InteractionResult.SUCCESS;
    }

    private static void toggle(Level level, Player player, ItemStack stack) {
        boolean active = !isActive(stack);
        setActive(stack, active);
        level.playSound(null, player.blockPosition(), SoundEvents.LEVER_CLICK, SoundSource.PLAYERS, 0.5F, active ? 1.4F : 1.0F);
        player.sendOverlayMessage(Component.translatable(active
                ? "item.futuretech.portable_battery.on" : "item.futuretech.portable_battery.off"));
    }

    /**
     * Ticked by the player's inventory, and by whatever else carries the battery for the player
     * and ticks it as an inventory would — a Curios slot on the belt does, and the battery keeps
     * charging the inventory from there. In the inventory the stack is reached through its
     * slot, the way every other transfer does; anywhere else the stack itself is written, since
     * nothing but its components ever changes.
     */
    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
        if (!isActive(stack) || storedEnergy(stack) <= 0 || !(owner instanceof Player player)) return;
        int self = findSlot(player, stack);
        charge(player, stack, handler(self < 0 ? ItemAccess.forStack(stack) : ItemAccess.forPlayerSlot(player, self)));
    }

    /** The slot of this very stack in the player's inventory, or -1 if it is somewhere else (cursor, container, curio). */
    private static int findSlot(Player player, ItemStack stack) {
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (inventory.getItem(slot) == stack) return slot;
        }
        return -1;
    }

    /**
     * Spreads this tick's budget over the other energy items in the inventory, first slot first.
     * The battery's own stack is skipped wherever it sits, and so are other portable batteries,
     * so two of them never pump energy back and forth.
     */
    static int charge(Player player, ItemStack batteryStack, EnergyHandler battery) {
        var inventory = player.getInventory();
        int budget = tier(batteryStack).chargePerTick;
        int moved = 0;
        for (int slot = 0; slot < inventory.getContainerSize() && budget > 0; slot++) {
            ItemStack target = inventory.getItem(slot);
            if (target == batteryStack || target.isEmpty() || target.getItem() instanceof PortableBatteryItem) continue;
            EnergyHandler handler = ItemAccess.forPlayerSlot(player, slot).getCapability(Capabilities.Energy.ITEM);
            if (handler == null || EnergyHandlerUtil.isFull(handler)) continue;
            try (var transaction = Transaction.openRoot()) {
                int sent = EnergyHandlerUtil.move(battery, handler, budget, transaction);
                transaction.commit();
                budget -= sent;
                moved += sent;
            }
        }
        return moved;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, lines, flag);
        lines.accept(Component.translatable("gui.futuretech.stored", String.format("%,d", storedEnergy(stack)), String.format("%,d", tier.capacity)));
        lines.accept(isActive(stack)
                ? Component.translatable("item.futuretech.portable_battery.on").withStyle(ChatFormatting.AQUA)
                : Component.translatable("item.futuretech.portable_battery.off").withStyle(ChatFormatting.GRAY));
    }

    @Override
    public boolean isBarVisible(ItemStack stack) { return storedEnergy(stack) > 0; }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Mth.clamp(Math.round(13.0F * storedEnergy(stack) / tier.capacity), 0, 13);
    }

    @Override
    public int getBarColor(ItemStack stack) { return 0x55E7ED; }
}
