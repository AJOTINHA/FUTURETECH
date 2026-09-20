package dev.futuretech.menu;

import dev.futuretech.block.entity.NetworkPanelBlockEntity;
import dev.futuretech.registry.ModMenus;
import dev.futuretech.item.PortableTeleporterItem;
import dev.futuretech.teleport.LinkSlots;
import dev.futuretech.teleport.NetworkPanelPayloads;
import dev.futuretech.teleport.PanelView;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.Nullable;

/**
 * The panel's menu holds the player's slots and the two link slots of the link tab: what it shows
 * lives in other blocks, and arrives as a {@link PanelView} over its own packet. The server
 * rebuilds that view now and then while the screen is open and sends it when it changed, so a
 * card moved in a storage a hundred blocks away shows up here without the player closing anything.
 */
public final class NetworkPanelMenu extends MachineMenu {
    /** Wider than the machines by a slot: the rename button past each row is one, and the margin stays the rows' own. */
    public static final int IMAGE_WIDTH = 188;
    /** The player's inventory, centred in the wider window. */
    public static final int INVENTORY_X = (IMAGE_WIDTH - 9 * 18) / 2;
    /** The list of the pad's cards: same rows and same window as the teleporter's own screen. */
    public static final int ROW_X = 8;
    public static final int ROW_Y = 22;
    public static final int ROW_SPACING = 18;
    public static final int VISIBLE = 5;
    public static final int INVENTORY_Y = ROW_Y + VISIBLE * ROW_SPACING + 14;
    /** Ticks between two walks down the cables. A panel is cheap to read, but not free. */
    private static final int REFRESH_TICKS = 10;

    private final @Nullable NetworkPanelBlockEntity panel;
    private final BlockPos pos;
    private final int inventoryStart;
    private final int inventoryEnd;
    private PanelView view;
    private int untilRefresh = REFRESH_TICKS;

    /**
     * Client side. The list rides in with the opening packet rather than waiting for the first
     * refresh: the screen is right on its first frame, and a panel that shows nothing is showing
     * what the server really found, not what has not arrived yet.
     */
    public NetworkPanelMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory, null, new SimpleContainer(LinkSlots.SLOTS), buffer.readBlockPos(), PanelView.STREAM_CODEC.decode(buffer));
    }

    public NetworkPanelMenu(int id, Inventory inventory, NetworkPanelBlockEntity panel) {
        this(id, inventory, panel, panel.link(), panel.getBlockPos(), panel.view());
    }

    private NetworkPanelMenu(int id, Inventory inventory, @Nullable NetworkPanelBlockEntity panel, Container link,
                             BlockPos pos, PanelView view) {
        super(ModMenus.NETWORK_PANEL.get(), id);
        this.panel = panel;
        this.pos = pos;
        this.view = view;
        this.inventoryStart = 0;
        this.inventoryEnd = 36;
        addStandardInventorySlots(inventory, INVENTORY_X, INVENTORY_Y);
        LinkSlots.addSlots(link, IMAGE_WIDTH, this::addSlot);
        if (panel != null) markSynced();
    }

    public static void writeOpeningData(RegistryFriendlyByteBuf buffer, NetworkPanelBlockEntity panel) {
        buffer.writeBlockPos(panel.getBlockPos());
        PanelView.STREAM_CODEC.encode(buffer, panel.view());
    }

    public BlockPos pos() { return pos; }

    /** What the screen draws: the panel's pad and its cards, as of the last packet. */
    public PanelView view() { return view; }

    public void setView(PanelView view) { this.view = view; }

    /** The server's side of a pick: written to the panel's own pad, and shown back on the next view. */
    public void pick(BlockPos source, int slot) {
        if (panel != null && panel.pick(source, slot)) untilRefresh = 0;
    }

    /** The client's side: ask for the change, and let the next view come back with the answer. */
    public static void sendPick(PanelView.Card card) {
        net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(
                new NetworkPanelPayloads.Pick(card.source(), card.slot()));
    }

    /** The server's side of a card's edit: written to the card where it is kept, and shown back on the next view. */
    public void edit(BlockPos source, int slot, String name, int colour) {
        if (panel != null && panel.editCard(source, slot, name, colour)) untilRefresh = 0;
    }

    /** The client's side: name and colour go up once, when the player is done with them. */
    public static void sendEdit(PanelView.Card card, String name, int colour) {
        net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(
                new NetworkPanelPayloads.Edit(card.source(), card.slot(), name, colour));
    }

    /**
     * Rides on the per-tick broadcast every open menu already gets, so the panel needs no ticker of
     * its own and stops costing anything the moment the screen closes.
     */
    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (panel == null) return;
        if (--untilRefresh > 0) return;
        untilRefresh = REFRESH_TICKS;
        PanelView fresh = panel.view();
        // The opening packet already carried one, so from here only a change is worth a packet.
        if (fresh.equals(view)) return;
        view = fresh;
        for (var viewer : viewers()) {
            PacketDistributor.sendToPlayer(viewer, new NetworkPanelPayloads.View(fresh));
        }
    }

    /** The players with this menu open; on the server that is the one who opened it. */
    private java.util.List<ServerPlayer> viewers() {
        if (!(panel.getLevel() instanceof net.minecraft.server.level.ServerLevel level)) return java.util.List.of();
        var found = new java.util.ArrayList<ServerPlayer>(1);
        for (var player : level.players()) {
            if (player.containerMenu == this) found.add(player);
        }
        return found;
    }

    @Override
    public boolean stillValid(Player player) {
        return panel == null || Container.stillValidBlockEntity(panel, player);
    }

    /** A portable teleporter shift-clicked from the inventory goes into the link's in slot; the link slots empty into the inventory. */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index >= inventoryEnd) {
            if (!moveItemStackTo(stack, inventoryStart, inventoryEnd, true)) return ItemStack.EMPTY;
        } else if (!(stack.getItem() instanceof PortableTeleporterItem)
                || !moveItemStackTo(stack, inventoryEnd + LinkSlots.IN, inventoryEnd + LinkSlots.IN + 1, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return original;
    }
}
