package dev.futuretech.api.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.Rect2i;

import java.util.List;

/**
 * Stacks {@link MachineTab}s down the edges of a machine screen. Screens forward background
 * drawing, tooltips and mouse clicks here; an open tab pushes the ones below it down. Each edge
 * stacks on its own, so tabs whose content has a fixed position (menu slots) must come first
 * among the tabs sharing their side.
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

    /** Call from {@code extractBackground}. Tabs share the screen's side border, one pixel below its top. */
    public void render(GuiGraphicsExtractor graphics, int leftPos, int topPos, int imageWidth, int mouseX, int mouseY) {
        int leftY = topPos + TOP_OFFSET;
        int rightY = topPos + TOP_OFFSET;
        for (MachineTab tab : tabs) {
            boolean left = tab.side() == MachineTab.Side.LEFT;
            tab.render(graphics, left ? leftPos + 2 : leftPos + imageWidth - 2, left ? leftY : rightY, mouseX, mouseY);
            if (left) leftY += tab.height() + SPACING;
            else rightY += tab.height() + SPACING;
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

    /** Call from {@code keyPressed}; returns true when a tab consumed the key. */
    public boolean keyPressed(KeyEvent event) {
        for (MachineTab tab : tabs) {
            if (tab.keyPressed(event)) return true;
        }
        return false;
    }

    /** Call from {@code charTyped}; returns true when a tab consumed the character. */
    public boolean charTyped(CharacterEvent event) {
        for (MachineTab tab : tabs) {
            if (tab.charTyped(event)) return true;
        }
        return false;
    }

    public MachineTab get(int index) { return tabs.get(index); }

    /** Every tab's box as of the last frame: the ground the screen holds beyond its panel. */
    public List<Rect2i> areas() { return tabs.stream().map(MachineTab::bounds).toList(); }
}
