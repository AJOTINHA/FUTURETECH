package dev.futuretech.api.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;

import java.util.List;

/**
 * Stacks {@link MachineTab}s down the right edge of a machine screen. Screens forward background
 * drawing, tooltips and mouse clicks here; an open tab pushes the ones below it down.
 */
public final class TabStrip {
    /** Panel rows between two stacked tabs. */
    private static final int SPACING = 2;

    private final List<MachineTab> tabs;

    public TabStrip(MachineTab... tabs) {
        this.tabs = List.of(tabs);
    }

    /** Call from {@code extractBackground}. Tabs share the screen's right border, one pixel below its top. */
    public void render(GuiGraphicsExtractor graphics, int leftPos, int topPos, int imageWidth, int mouseX, int mouseY) {
        int x = leftPos + imageWidth - 2;
        int y = topPos + 1;
        for (MachineTab tab : tabs) {
            tab.render(graphics, x, y, mouseX, mouseY);
            y += tab.height() + SPACING;
        }
    }

    /** Call from {@code extractTooltip}. */
    public void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        tabs.forEach(tab -> tab.extractTooltip(graphics, mouseX, mouseY));
    }

    /** Call from {@code mouseClicked}; returns true when a tab consumed the click. */
    public boolean mouseClicked(MouseButtonEvent event) {
        for (MachineTab tab : tabs) {
            if (tab.mouseClicked(event)) return true;
        }
        return false;
    }
}
