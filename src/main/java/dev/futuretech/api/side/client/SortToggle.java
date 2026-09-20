package dev.futuretech.api.side.client;

import dev.futuretech.api.side.SideConfigMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.util.List;

/**
 * The sorting button of a machine with several lanes open: a green square to the right of the
 * output column, centred on the block of lane rows, that spreads the inputs over the lanes while
 * it is on. Screens draw it, hand it their clicks and ask it for its tooltip; the menu carries the
 * state and the click travels as {@link SideConfigMenu#BUTTON_AUTO_SORT}.
 */
public final class SortToggle {
    public static final int COLOR = 0xFF3FA34D;
    /** Left edge relative to the panel: past the output column, with room for the tooltip target. */
    public static final int X = 140;
    private static final int ROW_SPACING = 18;
    /** One feed splitting three ways: what sorting does to a stack. */
    private static final String[] SPREAD = {
            "....####....",
            "....####....",
            "....####....",
            "############",
            "############",
            "##..####..##",
            "##..####..##",
            "##..####..##",
            "##..####..##",
            "##..####..##",
            "##..####..##",
    };

    private SortToggle() {}

    /** Top edge relative to the panel, for lane rows starting at {@code rowsTop}: centred on the rows. */
    public static int y(int rowsTop, int lanes) {
        return rowsTop + lanes * ROW_SPACING / 2 - ToggleArt.SIZE / 2;
    }

    public static <M extends AbstractContainerMenu & SideConfigMenu> void draw(GuiGraphicsExtractor graphics, M menu,
                                                                                int x, int y, int mouseX, int mouseY) {
        if (!menu.supportsSorting()) return;
        ToggleArt.draw(graphics, x, y, COLOR, menu.isSorting(), SPREAD, ToggleArt.isOver(mouseX, mouseY, x, y));
    }

    /** The name and the state, while the mouse is over the button. */
    public static <M extends AbstractContainerMenu & SideConfigMenu> void tooltip(GuiGraphicsExtractor graphics, M menu,
                                                                                   int x, int y, int mouseX, int mouseY) {
        if (!menu.supportsSorting() || !ToggleArt.isOver(mouseX, mouseY, x, y)) return;
        graphics.setTooltipForNextFrame(List.of(
                Component.translatable("gui.futuretech.auto.sort").getVisualOrderText(),
                Component.translatable(menu.isSorting() ? "gui.futuretech.auto.on" : "gui.futuretech.auto.off")
                        .getVisualOrderText()), mouseX, mouseY);
    }

    /** Sends the toggle on a left click over the button; true when the click was taken. */
    public static <M extends AbstractContainerMenu & SideConfigMenu> boolean click(M menu, int x, int y, MouseButtonEvent event) {
        if (!menu.supportsSorting() || !ToggleArt.isOver(event.x(), event.y(), x, y)) return false;
        if (event.button() != 0) return true;
        var gameMode = Minecraft.getInstance().gameMode;
        if (gameMode != null) gameMode.handleInventoryButtonClick(menu.containerId, SideConfigMenu.BUTTON_AUTO_SORT);
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        return true;
    }
}
