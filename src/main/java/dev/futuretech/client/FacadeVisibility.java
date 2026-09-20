package dev.futuretech.client;

import dev.futuretech.item.WrenchItem;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.player.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The see-through mode: sneaking with the wrench in hand takes every facade off the picture, so a
 * player can find the cable they buried without unbuilding the wall. Letting go puts them back.
 *
 * <p>Purely what is drawn. The panels are still there for the block's shape, so nothing about
 * walking into them or clicking them changes while they are out of sight.
 *
 * <p>Turning the mode on and off changes what those cables draw, and the chunk mesher is holding
 * the old geometry. Rebuilding the whole world for that is what the game does on F3+A, and it
 * shows: the screen blinks. So every covered cable the mesher walks past is noted here, and a
 * switch dirties only those blocks, which is a handful of sections instead of all of them.
 */
public final class FacadeVisibility {
    /** Covered cables the mesher has drawn, written from mesher threads and read on the client thread. */
    private static final Set<BlockPos> covered = ConcurrentHashMap.newKeySet();
    private static boolean hidden;

    private FacadeVisibility() {}

    /** Whether the panels are out of sight this frame. */
    public static boolean hidden() { return hidden; }

    /** Noted while the section is meshed, covered or not, so a switch can find its way back here. */
    public static void note(BlockPos pos) { covered.add(pos.immutable()); }

    /** Follows the player's hands once a tick; a change redraws the cables that wear a panel. */
    public static void tick() {
        Minecraft client = Minecraft.getInstance();
        boolean wanted = holdsWrench(client.player) && client.player.isShiftKeyDown();
        if (wanted == hidden) return;
        hidden = wanted;
        var level = client.level;
        var extractor = client.levelExtractor;
        if (level == null || extractor == null) {
            covered.clear();
            return;
        }
        // Sections, not blocks: a section is what the mesher builds, and several covered cables
        // usually share one. Asking for the same one twice would rebuild it twice.
        Set<Long> sections = new HashSet<>();
        // A position whose cable is gone drops out here, which is what keeps the set from growing.
        covered.removeIf(pos -> {
            if (!level.isLoaded(pos)) return true;
            if (!(level.getBlockState(pos).getBlock() instanceof dev.futuretech.block.AbstractCableBlock)) return true;
            sections.add(SectionPos.asLong(pos));
            return false;
        });
        for (long section : sections) {
            // Straight to the section: asking by block compares the old and new state, finds them
            // equal, and decides nothing needs drawing again.
            extractor.setSectionDirty(SectionPos.x(section), SectionPos.y(section), SectionPos.z(section));
        }
    }

    private static boolean holdsWrench(Player player) {
        return player != null && (player.getMainHandItem().getItem() instanceof WrenchItem
                || player.getOffhandItem().getItem() instanceof WrenchItem);
    }
}
