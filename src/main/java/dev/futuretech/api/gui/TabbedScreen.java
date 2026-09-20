package dev.futuretech.api.gui;

/**
 * A screen that hangs a {@link TabStrip} off its edges. The tabs stand outside the panel, where
 * an overlay such as JEI's ingredient list would otherwise sit on top of them; the JEI plugin
 * asks every screen that is one of these for its tabs' boxes and keeps the list clear of them.
 */
public interface TabbedScreen {
    TabStrip tabs();
}
