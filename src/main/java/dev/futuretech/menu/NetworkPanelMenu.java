package dev.futuretech.menu;

import dev.futuretech.block.entity.NetworkPanelBlockEntity;
import dev.futuretech.registry.ModMenus;
import dev.futuretech.teleport.NetworkPanelPayloads;
import dev.futuretech.teleport.PanelView;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.Container;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.Nullable;

/**
 * The panel's menu holds no slots but the player's own: what it shows lives in another block, and
 * arrives as a {@link PanelView} over its own packet. The server rebuilds that view now and then
 * while the screen is open and sends it when it changed, so a card moved in the pad a hundred
 * blocks away shows up here without the player closing anything.
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
    private PanelView view;
    private int untilRefresh = REFRESH_TICKS;

    /**
     * Client side. The list rides in with the opening packet rather than waiting for the first
     * refresh: the screen is right on its first frame, and a panel that shows nothing is showing
     * what the server really found, not what has not arrived yet.
     */
    public NetworkPanelMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory, null, buffer.readBlockPos(), PanelView.STREAM_CODEC.decode(buffer));
    }

    public NetworkPanelMenu(int id, Inventory inventory, NetworkPanelBlockEntity panel) {
        this(id, inventory, panel, panel.getBlockPos(), panel.view());
    }

    private NetworkPanelMenu(int id, Inventory inventory, @Nullable NetworkPanelBlockEntity panel, BlockPos pos,
                             PanelView view) {
        super(ModMenus.NETWORK_PANEL.get(), id);
        this.panel = panel;
        this.pos = pos;
        this.view = view;
        addStandardInventorySlots(inventory, INVENTORY_X, INVENTORY_Y);
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
    public void pick(int card) {
        if (panel != null && panel.pick(card)) untilRefresh = 0;
    }

    /** The client's side: ask for the change, and let the next view come back with the answer. */
    public static void sendPick(int card) {
        net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(
                new NetworkPanelPayloads.Pick(card));
    }

    /** The server's side of a card's edit: written to the card in the panel's own pad, and shown back on the next view. */
    public void edit(int slot, String name, int colour) {
        if (panel != null && panel.editCard(slot, name, colour)) untilRefresh = 0;
    }

    /** The client's side: name and colour go up once, when the player is done with them. */
    public static void sendEdit(int slot, String name, int colour) {
        net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(
                new NetworkPanelPayloads.Edit(slot, name, colour));
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

    /** No slots but the player's own, so nothing here has anywhere else to go. */
    @Override
    public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
}
