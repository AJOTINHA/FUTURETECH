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

import java.util.Optional;

/**
 * Keeps nothing of its own. Everything the panel shows is read off its pad at the moment it is
 * asked, so there is no state here to save, to sync or to go stale — pull the cable and the next
 * look already shows no pad. The pad is found the same way every time: the nearest teleporter down
 * the network cables, so on a run with several the panel belongs to the one closest to it.
 */
public final class NetworkPanelBlockEntity extends BlockEntity implements MenuProvider {
    public NetworkPanelBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.NETWORK_PANEL.get(), pos, state);
    }

    /** The pad this panel is wired to right now, read as its own screen would show it. */
    public PanelView view() {
        if (!(level instanceof ServerLevel serverLevel)) return PanelView.EMPTY;
        TeleporterGrid.Walk walk = TeleporterGrid.walk(serverLevel, worldPosition);
        TeleporterBlockEntity pad = padOf(serverLevel, walk);
        return new PanelView(pad == null ? Optional.empty() : Optional.of(PanelView.of(pad.getBlockPos(), pad)),
                walk.cables());
    }

    /**
     * Sets the destination of this panel's pad. The pad is looked up again rather than taken from
     * the client's last view: a cable cut since would otherwise let the click land on a pad the
     * panel no longer reaches.
     */
    public boolean pick(int card) {
        if (!(level instanceof ServerLevel serverLevel)) return false;
        TeleporterBlockEntity pad = padOf(serverLevel, TeleporterGrid.walk(serverLevel, worldPosition));
        if (pad == null) return false;
        pad.select(card);
        return true;
    }

    /** Edits a card of this panel's pad, name and colour; the pad is looked up as for a pick. */
    public boolean editCard(int slot, String name, int colour) {
        if (!(level instanceof ServerLevel serverLevel)) return false;
        TeleporterBlockEntity pad = padOf(serverLevel, TeleporterGrid.walk(serverLevel, worldPosition));
        return pad != null && pad.editCard(slot, name, colour);
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
