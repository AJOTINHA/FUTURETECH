package dev.futuretech.api.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;

import java.util.List;

/**
 * Stacks {@link MachineTab}s down the right edge of a machine screen. Screens forward background
 * drawing, tooltips and mouse clicks here; an open tab pushes the ones below it down. Tabs whose
 * content has a fixed position (menu slots) must therefore come first on the strip.
 */
public final class TabStrip {
    /** Panel rows between two stacked tabs. */
    public static final int SPACING = 2;
    /** Rows between the screen's top edge and the first tab. */
    public static final int TOP_OFFSET = 1;

    private final List<MachineTab> tabs;

    public TabStrip(MachineTab... tabs) {
        this.tabs = List.of(tabs);
    }

    /** Call from {@code extractBackground}. Tabs share the screen's right border, one pixel below its top. */
    public void render(GuiGraphicsExtractor graphics, int leftPos, int topPos, int imageWidth, int mouseX, int mouseY) {
        int x = leftPos + imageWidth - 2;
        int y = topPos + TOP_OFFSET;
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

    public MachineTab get(int index) { return tabs.get(index); }
}
