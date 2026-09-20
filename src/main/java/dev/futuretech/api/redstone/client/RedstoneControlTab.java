package dev.futuretech.api.redstone.client;

import dev.futuretech.api.gui.MachineTab;
import dev.futuretech.api.redstone.RedstoneControlMenu;
import dev.futuretech.api.redstone.RedstoneMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.util.List;

/**
 * Redstone control tab: one tile per {@link RedstoneMode}, the current one outlined; clicking a
 * tile selects it on the server. A line below says whether the block is powered right now.
 *
 * <p>The mode and signal come from a {@link Source}: a machine's menu, through its data slots and
 * a menu button, or anything else that can say the mode and send a pick — a screen with no menu
 * under it, say, that talks to its block over packets of its own.
 */
public final class RedstoneControlTab<M extends AbstractContainerMenu & RedstoneControlMenu> extends MachineTab {
    /** Where the tab reads the mode and the signal, and where a click goes. */
    public interface Source {
        RedstoneMode redstoneMode();

        boolean isPowered();

        /** The player clicked this mode's tile: send it. */
        void select(RedstoneMode mode);
    }

    private static final int TILE = 16;
    // Tiles are 16 px plus a 1 px border on each side; 2 px of panel show between neighbours.
    private static final int CELL = TILE + 2 + 2;
    private static final int ROW = CELL * RedstoneMode.values().length - (CELL - TILE) + 2;
    private static final int SELECTED = 0xFFEC761C;
    private static final int UNSELECTED = 0xFF56616D;

    private final Source menu;

    public RedstoneControlTab(M menu, Font font) {
        this(new Source() {
            @Override
            public RedstoneMode redstoneMode() { return menu.redstoneMode(); }

            @Override
            public boolean isPowered() { return menu.isPowered(); }

            @Override
            public void select(RedstoneMode mode) {
                var gameMode = Minecraft.getInstance().gameMode;
                if (gameMode != null) gameMode.handleInventoryButtonClick(menu.containerId, RedstoneControlMenu.BUTTON_BASE + mode.ordinal());
            }
        }, font);
    }

    private RedstoneControlTab(Source source, Font font) {
        super(font);
        this.menu = source;
    }

    /** A tab over a source that is not a menu. */
    public static RedstoneControlTab<?> of(Source source, Font font) {
        return new RedstoneControlTab<>(source, font);
    }

    private static void drawModeIcon(GuiGraphicsExtractor graphics, RedstoneMode mode, boolean selected, int x, int y) {
        RedstoneModeIcons.draw(graphics, mode, selected, x, y);
    }

    @Override
    protected Component title() { return Component.translatable("gui.futuretech.redstone"); }

    @Override
    protected int contentWidth() { return ROW; }

    @Override
    protected int contentHeight() { return TILE + 2 + 4 + font.lineHeight; }

    @Override
    protected void drawIcon(GuiGraphicsExtractor graphics, int x, int y) {
        // The tab shows the icon of the mode currently selected, centred in the 20 px square.
        drawModeIcon(graphics, menu.redstoneMode(), true, x + 2, y + 2);
    }

    @Override
    protected void drawContent(GuiGraphicsExtractor graphics, int contentX, int contentY, int mouseX, int mouseY) {
        for (RedstoneMode mode : RedstoneMode.values()) {
            int x = tileX(contentX, mode);
            int y = contentY + 1;
            graphics.fill(x - 1, y - 1, x + TILE + 1, y + TILE + 1, mode == menu.redstoneMode() ? SELECTED : UNSELECTED);
            graphics.fill(x, y, x + TILE, y + TILE, 0xFF8B959F);
            drawModeIcon(graphics, mode, mode == menu.redstoneMode(), x, y);
            if (isFullyOpen() && isOver(mouseX, mouseY, x, y, TILE, TILE)) graphics.fill(x, y, x + TILE, y + TILE, 0x40FFFFFF);
        }
        Component signal = Component.translatable(menu.isPowered() ? "gui.futuretech.redstone.powered" : "gui.futuretech.redstone.unpowered");
        graphics.text(font, signal, contentX + (ROW - font.width(signal)) / 2, contentY + TILE + 2 + 4, TEXT_COLOR, false);
    }

    @Override
    protected void contentTooltip(GuiGraphicsExtractor graphics, int contentX, int contentY, int mouseX, int mouseY) {
        for (RedstoneMode mode : RedstoneMode.values()) {
            if (!isOver(mouseX, mouseY, tileX(contentX, mode), contentY + 1, TILE, TILE)) continue;
            graphics.setTooltipForNextFrame(List.of(
                    Component.translatable(mode.translationKey()).getVisualOrderText(),
                    Component.translatable(mode.descriptionKey()).getVisualOrderText()), mouseX, mouseY);
            return;
        }
    }

    @Override
    protected boolean clickContent(MouseButtonEvent event, int contentX, int contentY) {
        for (RedstoneMode mode : RedstoneMode.values()) {
            if (!isOver(event.x(), event.y(), tileX(contentX, mode), contentY + 1, TILE, TILE)) continue;
            menu.select(mode);
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }
        return false;
    }

    private static int tileX(int contentX, RedstoneMode mode) { return contentX + 1 + mode.ordinal() * CELL; }
}
