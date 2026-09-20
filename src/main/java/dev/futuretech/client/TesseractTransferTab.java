package dev.futuretech.client;

import dev.futuretech.api.gui.MachineTab;
import dev.futuretech.api.side.SideMode;
import dev.futuretech.block.entity.TesseractBlockEntity.Kind;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.List;

/**
 * What a tesseract carries, one tile per kind — items, energy, fluids and the teleport network —
 * with the tile's border saying whether it sends, receives, both or neither, in the side
 * configuration's colours. Left click goes to the next mode, right click to the one before, and
 * the pick goes wherever the screen sends it.
 */
public final class TesseractTransferTab extends MachineTab {
    /** Where the tab reads each kind's mode, and where a click goes. */
    public interface Source {
        SideMode mode(Kind kind);

        void select(Kind kind, SideMode mode);
    }

    private static final int TILE = 16;
    // Tiles are 16 px plus a 1 px border on each side; 2 px of panel show between neighbours.
    private static final int CELL = TILE + 2 + 2;
    private static final int ROW = CELL * Kind.values().length - (CELL - TILE) + 2;
    private static final int FACE = 0xFF8B959F;
    private static final int INK = 0xFF283541;
    /** The kinds' icons, one 16 by 16 bitmap each: a crate, a bolt, a drop and the teleporter's chevrons. */
    private static final String[][] ICONS = {
            {
                    "................",
                    "................",
                    "...##########...",
                    "..#..........#..",
                    "..#.##....##.#..",
                    "..#.##....##.#..",
                    "..############..",
                    "..#..........#..",
                    "..#..........#..",
                    "..#...####...#..",
                    "..#..........#..",
                    "..#..........#..",
                    "..############..",
                    "................",
                    "................",
                    "................",
            },
            {
                    "................",
                    ".........##.....",
                    "........##......",
                    ".......##.......",
                    "......##........",
                    ".....######.....",
                    "....######......",
                    ".......##.......",
                    "......##........",
                    ".....##.........",
                    "....##..........",
                    "................",
                    "................",
                    "................",
                    "................",
                    "................",
            },
            {
                    "................",
                    ".......##.......",
                    ".......##.......",
                    "......####......",
                    "......####......",
                    ".....######.....",
                    ".....######.....",
                    "....########....",
                    "....########....",
                    "....########....",
                    "....##.#####....",
                    ".....##.###.....",
                    "......####......",
                    "................",
                    "................",
                    "................",
            },
            {
                    "................",
                    "................",
                    "...##....##.....",
                    "....##....##....",
                    ".....##....##...",
                    "......##....##..",
                    ".......##....##.",
                    "......##....##..",
                    ".....##....##...",
                    "....##....##....",
                    "...##....##.....",
                    "................",
                    "................",
                    "................",
                    "................",
                    "................",
            },
    };

    private final Source source;

    public TesseractTransferTab(Source source, Font font) {
        super(font);
        this.source = source;
    }

    @Override
    protected Component title() { return Component.translatable("gui.futuretech.tesseract.transfer"); }

    @Override
    protected int contentWidth() { return ROW; }

    @Override
    protected int contentHeight() { return TILE + 2; }

    @Override
    protected void drawIcon(GuiGraphicsExtractor graphics, int x, int y) {
        // Two arrows passing each other: what goes in one way comes out the other.
        graphics.fill(x + 4, y + 6, x + 13, y + 8, TITLE_COLOR);
        graphics.fill(x + 11, y + 4, x + 13, y + 6, TITLE_COLOR);
        graphics.fill(x + 13, y + 6, x + 15, y + 8, TITLE_COLOR);
        graphics.fill(x + 11, y + 8, x + 13, y + 10, TITLE_COLOR);
        graphics.fill(x + 7, y + 12, x + 16, y + 14, 0xFFEC761C);
        graphics.fill(x + 7, y + 10, x + 9, y + 12, 0xFFEC761C);
        graphics.fill(x + 5, y + 12, x + 7, y + 14, 0xFFEC761C);
        graphics.fill(x + 7, y + 14, x + 9, y + 16, 0xFFEC761C);
    }

    @Override
    protected void drawContent(GuiGraphicsExtractor graphics, int contentX, int contentY, int mouseX, int mouseY) {
        for (Kind kind : Kind.values()) {
            int x = tileX(contentX, kind);
            int y = contentY + 1;
            graphics.fill(x - 1, y - 1, x + TILE + 1, y + TILE + 1, modeColor(source.mode(kind)));
            graphics.fill(x, y, x + TILE, y + TILE, FACE);
            String[] icon = ICONS[kind.ordinal()];
            for (int row = 0; row < icon.length; row++) {
                for (int col = 0; col < icon[row].length(); col++) {
                    if (icon[row].charAt(col) == '#') graphics.fill(x + col, y + row, x + col + 1, y + row + 1, INK);
                }
            }
            if (isFullyOpen() && isOver(mouseX, mouseY, x, y, TILE, TILE)) graphics.fill(x, y, x + TILE, y + TILE, 0x40FFFFFF);
        }
    }

    @Override
    protected void contentTooltip(GuiGraphicsExtractor graphics, int contentX, int contentY, int mouseX, int mouseY) {
        for (Kind kind : Kind.values()) {
            if (!isOver(mouseX, mouseY, tileX(contentX, kind), contentY + 1, TILE, TILE)) continue;
            graphics.setTooltipForNextFrame(List.of(
                    Component.translatable(kind.translationKey()).getVisualOrderText(),
                    Component.translatable(modeKey(source.mode(kind))).getVisualOrderText()), mouseX, mouseY);
            return;
        }
    }

    @Override
    protected boolean acceptsContentButton(int button) { return button == 0 || button == 1; }

    @Override
    protected boolean clickContent(MouseButtonEvent event, int contentX, int contentY) {
        for (Kind kind : Kind.values()) {
            if (!isOver(event.x(), event.y(), tileX(contentX, kind), contentY + 1, TILE, TILE)) continue;
            SideMode[] modes = SideMode.values();
            int step = event.button() == 1 ? modes.length - 1 : 1;
            source.select(kind, modes[(source.mode(kind).ordinal() + step) % modes.length]);
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }
        return false;
    }

    private static int tileX(int contentX, Kind kind) { return contentX + 1 + kind.ordinal() * CELL; }

    /** The tesseract's words for the modes: it sends and receives, it has no inside to put things in. */
    private static String modeKey(SideMode mode) { return "gui.futuretech.tesseract.mode." + mode.getSerializedName(); }

    private static int modeColor(SideMode mode) {
        return switch (mode) {
            case NONE -> 0xFF56616D;
            case INPUT -> 0xFF1676C4;
            case OUTPUT -> 0xFFEC761C;
            case BOTH -> 0xFF3FA34D;
        };
    }
}
