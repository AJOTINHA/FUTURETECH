package dev.futuretech.block.entity;

import dev.futuretech.menu.NetworkPanelMenu;
import dev.futuretech.registry.ModBlockEntities;
import dev.futuretech.item.PortableTeleporterItem;
import dev.futuretech.item.TeleportCardItem;
import dev.futuretech.teleport.LinkSlots;
import dev.futuretech.teleport.PanelView;
import dev.futuretech.teleport.TeleportTarget;
import dev.futuretech.teleport.TeleporterGrid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Keeps almost nothing of its own. Everything the panel shows is read off its pad and the
 * storages on its cables at the moment it is asked, so there is no state here to sync or to go
 * stale — pull the cable and the next look already shows no pad. The pad is found the same way
 * every time: the nearest teleporter down the network cables, so on a run with several the panel
 * belongs to the one closest to it. What it does keep is its two link slots: a portable
 * teleporter put in the first is linked to this panel and moved to the second, where it waits to
 * be taken.
 */
public final class NetworkPanelBlockEntity extends BlockEntity implements MenuProvider {
    private static final String LINK = "Link";

    private final SimpleContainer link = new SimpleContainer(LinkSlots.SLOTS) {
        @Override
        public void setChanged() {
            super.setChanged();
            linkChanged();
        }
    };

    public NetworkPanelBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.NETWORK_PANEL.get(), pos, state);
    }

    public SimpleContainer link() { return link; }

    /** A portable teleporter in the in slot, with the out slot free, is linked here and moved across. */
    private void linkChanged() {
        setChanged();
        if (!(level instanceof ServerLevel)) return;
        ItemStack stack = link.getItem(LinkSlots.IN);
        if (!(stack.getItem() instanceof PortableTeleporterItem) || !link.getItem(LinkSlots.OUT).isEmpty()) return;
        ItemStack linked = stack.copy();
        PortableTeleporterItem.link(linked, GlobalPos.of(level.dimension(), worldPosition.immutable()));
        link.setItem(LinkSlots.IN, ItemStack.EMPTY);
        link.setItem(LinkSlots.OUT, linked);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        NonNullList<ItemStack> items = NonNullList.withSize(LinkSlots.SLOTS, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input.childOrEmpty(LINK), items);
        for (int slot = 0; slot < LinkSlots.SLOTS; slot++) link.setItem(slot, items.get(slot));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        NonNullList<ItemStack> items = NonNullList.withSize(LinkSlots.SLOTS, ItemStack.EMPTY);
        for (int slot = 0; slot < LinkSlots.SLOTS; slot++) items.set(slot, link.getItem(slot));
        ContainerHelper.saveAllItems(output.child(LINK), items);
    }

    /** A panel taken down hands back whatever was in its link slots. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null) Containers.dropContents(level, pos, link);
    }

    /** The pad this panel is wired to right now, with every destination it can send to. */
    public PanelView view() {
        if (!(level instanceof ServerLevel serverLevel)) return PanelView.EMPTY;
        TeleporterGrid.Walk walk = TeleporterGrid.walk(serverLevel, worldPosition);
        TeleporterBlockEntity pad = padOf(serverLevel, walk);
        if (pad == null) return new PanelView(Optional.empty(), walk.cables());
        List<StorageCardsBlockEntity> storages = new ArrayList<>();
        for (BlockPos pos : walk.storages()) {
            if (serverLevel.getBlockEntity(pos) instanceof StorageCardsBlockEntity storage) storages.add(storage);
        }
        return new PanelView(Optional.of(PanelView.of(pad.getBlockPos(), pad, storages)), walk.cables());
    }

    /**
     * Sets the destination of this panel's pad: the pad's own card when {@code source} is the pad,
     * else a card of a storage on the cables. Both are looked up again rather than taken from the
     * client's last view: a cable cut since would otherwise let the click land on a block the
     * panel no longer reaches.
     */
    public boolean pick(BlockPos source, int slot) {
        if (!(level instanceof ServerLevel serverLevel)) return false;
        TeleporterGrid.Walk walk = TeleporterGrid.walk(serverLevel, worldPosition);
        TeleporterBlockEntity pad = padOf(serverLevel, walk);
        if (pad == null) return false;
        if (source.equals(pad.getBlockPos())) {
            pad.select(null, slot);
            return true;
        }
        if (!walk.storages().contains(source)) return false;
        pad.select(source, slot);
        return true;
    }

    /** Edits a card the pad can send to, name and colour, wherever it is kept; checked like a pick. */
    public boolean editCard(BlockPos source, int slot, String name, int colour) {
        if (!(level instanceof ServerLevel serverLevel)) return false;
        TeleporterGrid.Walk walk = TeleporterGrid.walk(serverLevel, worldPosition);
        TeleporterBlockEntity pad = padOf(serverLevel, walk);
        if (pad == null) return false;
        if (source.equals(pad.getBlockPos())) return pad.editCard(slot, name, colour);
        return walk.storages().contains(source)
                && serverLevel.getBlockEntity(source) instanceof StorageCardsBlockEntity storage
                && storage.editCard(slot, name, colour);
    }

    /**
     * Where the card in {@code slot} of {@code source} points, read down the cables the way a pick
     * is checked: the block has to be the panel's pad or a storage on the same cables. Null for a
     * card that is not there, or a block that is not on the network.
     */
    public @Nullable TeleportTarget target(BlockPos source, int slot) {
        if (!(level instanceof ServerLevel serverLevel)) return null;
        TeleporterGrid.Walk walk = TeleporterGrid.walk(serverLevel, worldPosition);
        TeleporterBlockEntity pad = padOf(serverLevel, walk);
        if (pad == null) return null;
        if (source.equals(pad.getBlockPos())) {
            return slot == TeleporterBlockEntity.CARD_SLOT ? TeleportCardItem.target(pad.getItem(slot)) : null;
        }
        return walk.storages().contains(source) && serverLevel.getBlockEntity(source) instanceof StorageCardsBlockEntity storage
                ? storage.target(slot) : null;
    }

    /** The first pad of the walk, which is the nearest one; null when the cables reach none. */
    private static @Nullable TeleporterBlockEntity padOf(ServerLevel level, TeleporterGrid.Walk walk) {
        for (BlockPos pos : walk.pads()) {
            if (level.getBlockEntity(pos) instanceof TeleporterBlockEntity pad) return pad;
        }
        return null;
    }

    @Override
    public Component getDisplayName() { return Component.translatable("block.futuretech.network_panel"); }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new NetworkPanelMenu(id, inventory, this);
    }
}
