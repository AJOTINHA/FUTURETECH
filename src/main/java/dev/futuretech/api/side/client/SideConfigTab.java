package dev.futuretech.api.side.client;

import dev.futuretech.api.gui.MachineTab;
import dev.futuretech.api.side.SideConfigMenu;
import dev.futuretech.api.side.SideMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Face configuration tab: an unfolded view of the block's six faces, drawn with their real
 * textures; left/right clicks cycle forwards/backwards, shift-left-clicking the front closes all.
 *
 * <p>Machines with an inventory also get two toggles down the left edge: a blue arrow that pulls
 * items from whatever sits against an input face, and an orange one that pushes results to whatever
 * sits against an output face. They are drawn in the same colours the tiles use for those modes.
 */
public final class SideConfigTab<M extends AbstractContainerMenu & SideConfigMenu> extends MachineTab {
    private static final int TILE = 16;
    // Tiles are 16 px plus a 1 px mode border on each side; 2 px of panel show between neighbours.
    private static final int CELL = TILE + 2 + 2;
    // Three tiles with their borders, edge to edge.
    private static final int GRID = CELL * 3 - (CELL - TILE) + 2;
    /** Width the auto-transfer column takes on the left, when the machine has any toggle. */
    private static final int COLUMN = CELL + 3;
    private static final int TOGGLE_STEP = CELL + 2;
    private static final int PULL_COLOR = 0xFF1676C4;
    private static final int PUSH_COLOR = 0xFFEC761C;
    /** Compact down arrow, mirrored vertically for output; leaves room for an outline. */
    private static final String[] ARROW = {
            "....####....",
            "....####....",
            "....####....",
            "....####....",
            "....####....",
            "....####....",
            "############",
            ".##########.",
            "..########..",
            "...######...",
            "....####....",
            ".....##....."
    };

    /** Layout of the unfolded cube: column and row of each face relative to the front; the back sits beside the bottom. */
    private enum Face {
        TOP(1, 0, "top"), LEFT(0, 1, "left"), FRONT(1, 1, "front"),
        RIGHT(2, 1, "right"), BOTTOM(1, 2, "bottom"), BACK(2, 2, "back");

        final int column;
        final int row;
        final String key;

        Face(int column, int row, String key) {
            this.column = column;
            this.row = row;
            this.key = "gui.futuretech.side." + key;
        }

        /** World direction of this face for a machine whose front points {@code front}. */
        Direction resolve(Direction front) {
            return switch (this) {
                case TOP -> Direction.UP;
                case BOTTOM -> Direction.DOWN;
                case FRONT -> front;
                case BACK -> front.getOpposite();
                // Seen by a player standing in front of the machine and looking at it.
                case LEFT -> front.getClockWise();
                case RIGHT -> front.getCounterClockWise();
            };
        }
    }

    /** One auto-transfer toggle: which way it moves items, and how it is drawn. */
    private enum Toggle {
        PULL("pull", PULL_COLOR, SideConfigMenu.BUTTON_AUTO_PULL, true),
        PUSH("push", PUSH_COLOR, SideConfigMenu.BUTTON_AUTO_PUSH, false);

        final String key;
        final int color;
        final int buttonId;
        /** Pulling points down, into the machine; pushing points up, out of it. */
        final boolean down;

        Toggle(String key, int color, int buttonId, boolean down) {
            this.key = "gui.futuretech.auto." + key;
            this.color = color;
            this.buttonId = buttonId;
            this.down = down;
        }
    }

    private final M menu;
    private final Map<Direction, TextureAtlasSprite> spriteCache = new EnumMap<>(Direction.class);
    private @Nullable BlockState cachedState;
    private @Nullable BlockStateModel cachedModel;

    public SideConfigTab(M menu, Font font) {
        super(font);
        this.menu = menu;
    }

    @Override
    protected Component title() { return Component.translatable("gui.futuretech.sides"); }

    /** The toggles this machine offers, top to bottom; empty when it has no inventory. */
    private List<Toggle> toggles() {
        List<Toggle> shown = new ArrayList<>(2);
        if (menu.supportsAutoPull()) shown.add(Toggle.PULL);
        if (menu.supportsAutoPush()) shown.add(Toggle.PUSH);
        return shown;
    }

    private int columnWidth() { return toggles().isEmpty() ? 0 : COLUMN; }

    @Override
    protected int contentWidth() { return columnWidth() + GRID; }

    @Override
    protected int contentHeight() { return GRID; }

    @Override
    protected void drawIcon(GuiGraphicsExtractor graphics, int x, int y) {
        // A tiny unfolded cube; the 3x3 cell glyph is 8 px wide, so an offset of 6 centres it in the 20 px tab.
        for (Face face : Face.values()) {
            int px = x + 6 + face.column * 3;
            int py = y + 6 + face.row * 3;
            graphics.fill(px, py, px + 2, py + 2, face == Face.FRONT ? 0xFFEC761C : TITLE_COLOR);
        }
    }

    @Override
    protected void drawContent(GuiGraphicsExtractor graphics, int contentX, int contentY, int mouseX, int mouseY) {
        drawToggles(graphics, contentX, contentY, mouseX, mouseY);
        int gridX = contentX + columnWidth();
        Direction front = menu.front();
        for (Face face : Face.values()) {
            Direction side = face.resolve(front);
            int x = tileX(gridX, face);
            int y = tileY(contentY, face);
            graphics.fill(x - 1, y - 1, x + TILE + 1, y + TILE + 1, modeColor(menu.sideMode(side)));
            TextureAtlasSprite sprite = faceSprite(side);
            if (sprite != null) graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, TILE, TILE);
            else graphics.fill(x, y, x + TILE, y + TILE, 0xFF8B959F);
            if (isFullyOpen() && isOver(mouseX, mouseY, x, y, TILE, TILE)) graphics.fill(x, y, x + TILE, y + TILE, 0x40FFFFFF);
        }
    }

    private void drawToggles(GuiGraphicsExtractor graphics, int contentX, int contentY, int mouseX, int mouseY) {
        List<Toggle> shown = toggles();
        for (int index = 0; index < shown.size(); index++) {
            Toggle toggle = shown.get(index);
            int x = contentX + 1;
            int y = toggleY(contentY, index, shown.size());
            boolean on = isOn(toggle);
            graphics.fill(x - 1, y - 1, x + TILE + 1, y + TILE + 1, on ? toggle.color : 0xFF56616D);
            graphics.fill(x, y, x + TILE, y + TILE, 0xFF65717D);
            graphics.fill(x, y, x + TILE, y + 1, 0xFFB5C0CA);
            graphics.fill(x, y + 1, x + 1, y + TILE, 0xFF9AA7B3);
            graphics.fill(x + 1, y + TILE - 1, x + TILE, y + TILE, 0xFF394651);
            graphics.fill(x + TILE - 1, y + 1, x + TILE, y + TILE, 0xFF394651);
            drawArrow(graphics, x, y, on ? toggle.color : 0xFF56616D, toggle.down);
            if (isFullyOpen() && isOver(mouseX, mouseY, x, y, TILE, TILE)) {
                graphics.fill(x, y, x + TILE, y + TILE, 0x40FFFFFF);
            }
        }
    }

    /** Inset pixel arrow with a dark outline and a light edge for contrast at GUI scale. */
    private static void drawArrow(GuiGraphicsExtractor graphics, int x, int y, int color, boolean down) {
        int red = color >> 16 & 255;
        int green = color >> 8 & 255;
        int blue = color & 255;
        int highlight = 0xFF000000 | ((red * 3 + 255) / 4 << 16)
                | ((green * 3 + 255) / 4 << 8) | (blue * 3 + 255) / 4;
        // Paint the outline first so adjacent rows cannot cover the coloured face.
        for (int row = 0; row < ARROW.length; row++) {
            for (int column = 0; column < ARROW[row].length(); column++) {
                if (!arrowPixel(column, row, down)) continue;
                int px = x + 2 + column;
                int py = y + 2 + row;
                graphics.fill(px - 1, py, px + 2, py + 1, 0xFF26333F);
                graphics.fill(px, py - 1, px + 1, py + 2, 0xFF26333F);
            }
        }
        for (int row = 0; row < ARROW.length; row++) {
            for (int column = 0; column < ARROW[row].length(); column++) {
                if (!arrowPixel(column, row, down)) continue;
                boolean lightEdge = !arrowPixel(column - 1, row, down) || !arrowPixel(column, row - 1, down);
                int px = x + 2 + column;
                int py = y + 2 + row;
                graphics.fill(px, py, px + 1, py + 1, lightEdge ? highlight : color);
            }
        }
    }

    private static boolean arrowPixel(int column, int row, boolean down) {
        if (row < 0 || row >= ARROW.length || column < 0 || column >= ARROW[0].length()) return false;
        return ARROW[down ? row : ARROW.length - 1 - row].charAt(column) == '#';
    }

    private boolean isOn(Toggle toggle) {
        return toggle == Toggle.PULL ? menu.isAutoPulling() : menu.isAutoPushing();
    }

    /** Centre the toggle group vertically beside the cube, including each button's border. */
    private static int toggleY(int contentY, int index, int count) {
        int groupHeight = (count - 1) * TOGGLE_STEP + TILE + 2;
        return contentY + (GRID - groupHeight) / 2 + 1 + index * TOGGLE_STEP;
    }

    @Override
    protected void contentTooltip(GuiGraphicsExtractor graphics, int contentX, int contentY, int mouseX, int mouseY) {
        List<Toggle> shown = toggles();
        for (int index = 0; index < shown.size(); index++) {
            if (!isOver(mouseX, mouseY, contentX + 1, toggleY(contentY, index, shown.size()), TILE, TILE)) continue;
            Toggle toggle = shown.get(index);
            graphics.setTooltipForNextFrame(List.of(
                    Component.translatable(toggle.key).getVisualOrderText(),
                    Component.translatable(toggle.key + ".desc").getVisualOrderText(),
                    Component.translatable(isOn(toggle) ? "gui.futuretech.auto.on" : "gui.futuretech.auto.off")
                            .getVisualOrderText()), mouseX, mouseY);
            return;
        }
        int gridX = contentX + columnWidth();
        Direction front = menu.front();
        for (Face face : Face.values()) {
            if (!isOver(mouseX, mouseY, tileX(gridX, face), tileY(contentY, face), TILE, TILE)) continue;
            SideMode mode = menu.sideMode(face.resolve(front));
            List<FormattedCharSequence> lines = new ArrayList<>(List.of(
                    Component.translatable(face.key).getVisualOrderText(),
                    Component.translatable(mode.translationKey()).getVisualOrderText(),
                    Component.translatable("gui.futuretech.side.cycle_hint").getVisualOrderText()));
            if (face == Face.FRONT) lines.add(Component.translatable("gui.futuretech.side.clear_hint").getVisualOrderText());
            graphics.setTooltipForNextFrame(lines, mouseX, mouseY);
            return;
        }
    }

    @Override
    protected boolean acceptsContentButton(int button) { return button == 0 || button == 1; }

    @Override
    protected boolean clickContent(MouseButtonEvent event, int contentX, int contentY) {
        List<Toggle> shown = toggles();
        for (int index = 0; index < shown.size(); index++) {
            if (!isOver(event.x(), event.y(), contentX + 1, toggleY(contentY, index, shown.size()), TILE, TILE)) continue;
            if (event.button() != 0) return true;
            send(shown.get(index).buttonId);
            return true;
        }
        int gridX = contentX + columnWidth();
        Direction front = menu.front();
        for (Face face : Face.values()) {
            if (!isOver(event.x(), event.y(), tileX(gridX, face), tileY(contentY, face), TILE, TILE)) continue;
            // Shift-left-click closes all faces; right-click always goes back one mode.
            int buttonId = face == Face.FRONT && event.hasShiftDown() && event.button() == 0
                    ? SideConfigMenu.BUTTON_CLEAR_ALL : face.resolve(front).ordinal()
                    + (event.button() == 1 ? SideConfigMenu.BUTTON_REVERSE_BASE : 0);
            send(buttonId);
            return true;
        }
        return false;
    }

    /** Sends a menu button and plays the same click as vanilla buttons. */
    private void send(int buttonId) {
        var gameMode = Minecraft.getInstance().gameMode;
        if (gameMode != null) gameMode.handleInventoryButtonClick(menu.containerId, buttonId);
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    private static int tileX(int contentX, Face face) { return contentX + 1 + face.column * CELL; }

    private static int tileY(int contentY, Face face) { return contentY + 1 + face.row * CELL; }

    private static int modeColor(SideMode mode) {
        return switch (mode) {
            case NONE -> 0xFF56616D;
            case INPUT -> 0xFF1676C4;
            case OUTPUT -> 0xFFEC761C;
            case BOTH -> 0xFF3FA34D;
        };
    }

    /** Sprite the block model uses for {@code side}; resolved once per state and cached. */
    private @Nullable TextureAtlasSprite faceSprite(Direction side) {
        BlockState state = menu.displayState();
        BlockStateModel model = Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(state);
        if (model instanceof ConfiguredSideModel configured) {
            TextureAtlasSprite modeSprite = configured.spriteFor(menu.sideMode(side));
            if (modeSprite != null) return modeSprite;
        }
        if (state != cachedState || model != cachedModel) {
            spriteCache.clear();
            cachedState = state;
            cachedModel = model;
        }
        if (spriteCache.containsKey(side)) return spriteCache.get(side);
        TextureAtlasSprite sprite = null;
        List<BlockStateModelPart> parts = new ArrayList<>();
        // Menu data selects the mode above; do not accidentally sample a machine at world origin.
        model.collectParts(RandomSource.create(42), parts);
        for (BlockStateModelPart part : parts) {
            var quads = part.getQuads(side);
            if (!quads.isEmpty()) {
                sprite = quads.getFirst().materialInfo().sprite();
                break;
            }
        }
        if (sprite == null) sprite = model.particleMaterial().sprite();
        spriteCache.put(side, sprite);
        return sprite;
    }
}
