package dev.futuretech.item;

import dev.futuretech.api.upgrade.MachineLevel;
import dev.futuretech.block.entity.NetworkPanelBlockEntity;
import dev.futuretech.block.entity.TeleporterBlockEntity;
import dev.futuretech.registry.ModDataComponents;
import dev.futuretech.teleport.PanelView;
import dev.futuretech.teleport.PortableTeleporterPayloads;
import dev.futuretech.teleport.TeleportTarget;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.ItemAccessEnergyHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A teleporter to carry. It sends the player from wherever they stand to any pad it knows, paid
 * from its own buffer at the top level's fare, and it knows pads two ways: the ones saved on it
 * by shift-clicking them, and, once it is linked to a network panel through that panel's link
 * slots, everywhere that panel's pad can send to — read through the panel whenever it is used,
 * so a card added to a storage on those cables is on the teleporter at once. It charges in the
 * charger like a portable battery, and reaches other dimensions the way an MK4 pad does.
 */
public final class PortableTeleporterItem extends Item {
    public static final int CAPACITY = 100_000;
    /** As many pads as the screen can sensibly list; a network is for more than that. */
    public static final int SAVED_PADS = 32;

    public PortableTeleporterItem(Properties properties) {
        super(properties);
    }

    public EnergyHandler handler(ItemAccess access) {
        return new ItemAccessEnergyHandler(access, ModDataComponents.ENERGY.get(), CAPACITY);
    }

    public static int storedEnergy(ItemStack stack) {
        return Math.clamp(stack.getOrDefault(ModDataComponents.ENERGY.get(), 0), 0, CAPACITY);
    }

    /** The panel the teleporter reads its destinations through, or null while it is not linked. */
    public static @Nullable GlobalPos link(ItemStack stack) {
        return stack.get(ModDataComponents.TELEPORT_LINK.get());
    }

    public static void link(ItemStack stack, GlobalPos panel) {
        stack.set(ModDataComponents.TELEPORT_LINK.get(), panel);
    }

    /** The pads saved on the teleporter itself, oldest first. */
    public static List<TeleportTarget> saved(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.TELEPORT_TARGETS.get(), List.of());
    }

    /**
     * Saves the pad at {@code pos}, or forgets it when it is saved already; false when the list
     * is full. A pad is the same pad wherever it was saved from, so it is known by where it is.
     */
    public static boolean toggleSaved(ItemStack stack, TeleportTarget pad) {
        List<TeleportTarget> saved = new ArrayList<>(saved(stack));
        boolean removed = saved.removeIf(known -> known.pos().equals(pad.pos()));
        if (!removed) {
            if (saved.size() >= SAVED_PADS) return false;
            saved.add(pad);
        }
        stack.set(ModDataComponents.TELEPORT_TARGETS.get(), List.copyOf(saved));
        return true;
    }

    private static boolean isSaved(ItemStack stack, GlobalPos pos) {
        for (TeleportTarget known : saved(stack)) if (known.pos().equals(pos)) return true;
        return false;
    }

    /** The slot a saved pad is listed under: below zero, so it cannot be mistaken for a slot on the network. */
    private static int savedSlot(int index) { return -1 - index; }

    /** What a trip from {@code from} to {@code target} costs the teleporter: the top level's fare. */
    public static int cost(GlobalPos from, TeleportTarget target) {
        return TeleporterBlockEntity.cost(from, target, MachineLevel.MAX);
    }

    /** The panel the stack is linked to, if it still stands; the chunk is read as it is, loaded or not. */
    public static @Nullable NetworkPanelBlockEntity panel(MinecraftServer server, ItemStack stack) {
        GlobalPos link = link(stack);
        if (link == null) return null;
        ServerLevel level = server.getLevel(link.dimension());
        return level != null && level.getBlockEntity(link.pos()) instanceof NetworkPanelBlockEntity panel ? panel : null;
    }

    /**
     * Everywhere the teleporter can send {@code player} from where they stand: the pads saved on
     * it first, then the panel's destinations when it is linked to one that still stands, each
     * with whether a pad still stands there; the pad's own level does not limit the teleporter.
     */
    public static List<PanelView.Card> destinations(ServerPlayer player, ItemStack stack, @Nullable NetworkPanelBlockEntity panel) {
        ServerLevel level = player.level();
        GlobalPos from = GlobalPos.of(level.dimension(), player.blockPosition());
        List<PanelView.Card> priced = new ArrayList<>();
        List<TeleportTarget> saved = saved(stack);
        for (int index = 0; index < saved.size(); index++) {
            TeleportTarget target = saved.get(index);
            priced.add(new PanelView.Card(target.pos().pos(), savedSlot(index), target.name(), target.colour(),
                    cost(from, target), true, TeleporterBlockEntity.padStands(level, target)));
        }
        PanelView.Pad pad = panel == null ? null : panel.view().padOrNull();
        if (pad == null) return priced;
        for (PanelView.Card card : pad.cards()) {
            TeleportTarget target = panel.target(card.source(), card.slot());
            if (target == null) continue;
            priced.add(new PanelView.Card(card.source(), card.slot(), card.name(), card.colour(),
                    cost(from, target), true, card.present()));
        }
        return priced;
    }

    /** Right-click: the list of destinations, or a word on why there is none. */
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer serverPlayer) || !(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }
        NetworkPanelBlockEntity panel = link(stack) == null ? null : panel(serverLevel.getServer(), stack);
        if (saved(stack).isEmpty()) {
            if (link(stack) == null) {
                player.sendOverlayMessage(Component.translatable("item.futuretech.portable_teleporter.unlinked"));
                return InteractionResult.FAIL;
            }
            if (panel == null) {
                player.sendOverlayMessage(Component.translatable("item.futuretech.portable_teleporter.panel_missing"));
                return InteractionResult.FAIL;
            }
        }
        PortableTeleporterPayloads.open(serverPlayer, hand, destinations(serverPlayer, stack, panel), storedEnergy(stack));
        return InteractionResult.SUCCESS;
    }

    /** Shift-click on a pad saves it on the teleporter, or forgets it when it was saved already. */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || !context.isSecondaryUseActive()) return InteractionResult.PASS;
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (!(level.getBlockEntity(pos) instanceof TeleporterBlockEntity pad)) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        ItemStack stack = context.getItemInHand();
        GlobalPos where = GlobalPos.of(level.dimension(), pos.immutable());
        boolean known = isSaved(stack, where);
        if (!toggleSaved(stack, new TeleportTarget(where, pad.displayName()))) {
            player.sendOverlayMessage(Component.translatable("item.futuretech.portable_teleporter.full", SAVED_PADS));
            return InteractionResult.FAIL;
        }
        player.sendOverlayMessage(Component.translatable(known
                ? "item.futuretech.portable_teleporter.forgot" : "item.futuretech.portable_teleporter.saved", pad.displayName()));
        level.playSound(null, pos, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 0.4F, known ? 0.8F : 1.6F);
        return InteractionResult.SUCCESS;
    }

    /**
     * The trip the player picked on the screen: the card is read again through the panel, the
     * pad there has to stand, the buffer has to cover the fare, and then the player goes the way
     * a pad sends them. Whatever stops it is said to the player.
     */
    public static void go(ServerPlayer player, InteractionHand hand, BlockPos source, int slot) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(stack.getItem() instanceof PortableTeleporterItem) || !(player.level() instanceof ServerLevel level)) return;
        TeleportTarget target;
        if (slot < 0) {
            // A pad saved on the teleporter: listed by its index, and checked to still be the one at that spot.
            int index = -1 - slot;
            List<TeleportTarget> saved = saved(stack);
            target = index < saved.size() && saved.get(index).pos().pos().equals(source) ? saved.get(index) : null;
        } else {
            NetworkPanelBlockEntity panel = panel(level.getServer(), stack);
            if (panel == null) {
                player.sendOverlayMessage(Component.translatable("item.futuretech.portable_teleporter.panel_missing"));
                return;
            }
            target = panel.target(source, slot);
        }
        if (target == null) {
            player.sendOverlayMessage(Component.translatable("message.futuretech.teleporter.network"));
            return;
        }
        if (!TeleporterBlockEntity.padStands(level, target)) {
            player.sendOverlayMessage(Component.translatable("message.futuretech.teleporter.missing", target.name()));
            return;
        }
        int fare = cost(GlobalPos.of(level.dimension(), player.blockPosition()), target);
        if (storedEnergy(stack) < fare) {
            player.sendOverlayMessage(Component.translatable("message.futuretech.teleporter.energy", fare));
            return;
        }
        if (!TeleporterBlockEntity.travel(level, player, target)) {
            player.sendOverlayMessage(Component.translatable("message.futuretech.teleporter.missing", target.name()));
            return;
        }
        stack.set(ModDataComponents.ENERGY.get(), storedEnergy(stack) - fare);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, lines, flag);
        lines.accept(Component.translatable("gui.futuretech.stored", String.format("%,d", storedEnergy(stack)), String.format("%,d", CAPACITY)));
        lines.accept(link(stack) == null
                ? Component.translatable("item.futuretech.portable_teleporter.unlinked").withStyle(ChatFormatting.GRAY)
                : Component.translatable("item.futuretech.portable_teleporter.linked").withStyle(ChatFormatting.AQUA));
        int saved = saved(stack).size();
        if (saved > 0) lines.accept(Component.translatable("item.futuretech.portable_teleporter.saved_count", saved).withStyle(ChatFormatting.GRAY));
    }

    @Override
    public boolean isBarVisible(ItemStack stack) { return storedEnergy(stack) > 0; }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Mth.clamp(Math.round(13.0F * storedEnergy(stack) / CAPACITY), 0, 13);
    }

    @Override
    public int getBarColor(ItemStack stack) { return 0x55E7ED; }
}
