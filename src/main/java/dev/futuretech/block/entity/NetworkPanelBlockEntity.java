package dev.futuretech.block.entity;

import dev.futuretech.menu.NetworkPanelMenu;
import dev.futuretech.registry.ModBlockEntities;
import dev.futuretech.teleport.PanelView;
import dev.futuretech.teleport.TeleporterGrid;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Keeps nothing of its own. Everything the panel shows is read off its pad and the storages on
 * its cables at the moment it is asked, so there is no state here to save, to sync or to go
 * stale — pull the cable and the next look already shows no pad. The pad is found the same way
 * every time: the nearest teleporter down the network cables, so on a run with several the panel
 * belongs to the one closest to it.
 */
public final class NetworkPanelBlockEntity extends BlockEntity implements MenuProvider {
    public NetworkPanelBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.NETWORK_PANEL.get(), pos, state);
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
