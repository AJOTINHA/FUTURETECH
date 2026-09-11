package dev.futuretech.api.side.client;

import dev.futuretech.api.side.SideConfigMenu;
import dev.futuretech.api.side.SideMode;
import dev.futuretech.client.MachineScreenStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Drop-in face configuration for machine screens, in the style of a side tab: a small square glued
 * to the screen's top-right corner that grows into a panel when clicked, showing the block's six
 * faces drawn with their real textures; clicking a face cycles its mode on the server. Screens
 * forward background drawing, tooltips and mouse clicks to it.
 */
public final class SideConfigPanel<M extends AbstractContainerMenu & SideConfigMenu> {
    public static final int BUTTON_SIZE = 20;
    private static final int TILE = 16;
    // Tiles are 16 px plus a 1 px mode border on each side; 2 px of panel show between neighbours.
    private static final int CELL = TILE + 2 + 2;
    private static final int PADDING = 6;
    private static final int GRID_WIDTH = CELL * 3 - (CELL - TILE);
    private static final int PANEL_HEIGHT = BUTTON_SIZE + PADDING + GRID_WIDTH + PADDING;
    /** Opening and closing slide over this many milliseconds. */
    private static final double SLIDE_MILLIS = 150.0;

    private static final int TITLE = 0xFFFFFFFF;

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

    private final M menu;
    private final Font font;
    private final Map<Direction, TextureAtlasSprite> spriteCache = new EnumMap<>(Direction.class);
    private @Nullable BlockState cachedState;
    private boolean open;
    // 0 = closed, 1 = fully open; moves towards the target every frame for the slide.
    private double slide;
    private long lastFrameNanos;
    private int buttonX;
    private int buttonY;
    private int panelWidth;
    private int gridX;
    private int gridY;

    public SideConfigPanel(M menu, Font font) {
        this.menu = menu;
        this.font = font;
    }

    public boolean isOpen() { return open; }

    /** Draws the toggle button and, when open, the face panel. Call from {@code extractBackground}. */
    public void render(GuiGraphicsExtractor graphics, int leftPos, int topPos, int imageWidth, int mouseX, int mouseY) {
        // The tab is glued to the screen's right edge, sharing its border, and grows in place when opened.
        buttonX = leftPos + imageWidth - 2;
        buttonY = topPos + 1;
        Component title = Component.translatable("gui.futuretech.sides");
        panelWidth = Math.max(PADDING * 2 + GRID_WIDTH, BUTTON_SIZE + font.width(title) + PADDING + 2);
        gridX = buttonX + (panelWidth - GRID_WIDTH) / 2;
        gridY = buttonY + BUTTON_SIZE + PADDING;
        advanceSlide();

        int width = BUTTON_SIZE + (int) Math.round((panelWidth - BUTTON_SIZE) * slide);
        int height = BUTTON_SIZE + (int) Math.round((PANEL_HEIGHT - BUTTON_SIZE) * slide);
        boolean hovered = slide <= 0 && isOver(mouseX, mouseY, buttonX, buttonY, BUTTON_SIZE, BUTTON_SIZE);
        // Same cut corners, shadow and header band as the machine screens; collapsed, the band fills the tab.
        MachineScreenStyle.drawPanel(graphics, buttonX, buttonY, width, height);
        if (hovered) graphics.fill(buttonX + 2, buttonY + 3, buttonX + width - 2, buttonY + BUTTON_SIZE - 2, 0x1AFFFFFF);
        drawIcon(graphics);
        if (slide <= 0) return;

        // Content only shows inside the part of the tab that has grown so far.
        graphics.enableScissor(buttonX, buttonY, buttonX + width - 2, buttonY + height - 2);
        graphics.text(font, title, buttonX + BUTTON_SIZE, buttonY + 6, TITLE, false);
        drawFaces(graphics, mouseX, mouseY);
        graphics.disableScissor();
    }

    private void drawFaces(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        Direction front = menu.front();
        for (Face face : Face.values()) {
            Direction side = face.resolve(front);
            int x = tileX(face);
            int y = tileY(face);
            int outline = modeColor(menu.sideMode(side));
            graphics.fill(x - 1, y - 1, x + TILE + 1, y + TILE + 1, outline);
            TextureAtlasSprite sprite = faceSprite(side);
            if (sprite != null) graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, TILE, TILE);
            else graphics.fill(x, y, x + TILE, y + TILE, 0xFF8B959F);
            if (isFullyOpen() && isOver(mouseX, mouseY, x, y, TILE, TILE)) graphics.fill(x, y, x + TILE, y + TILE, 0x40FFFFFF);
        }
    }

    private boolean isFullyOpen() { return open && slide >= 1; }

    private void advanceSlide() {
        long now = System.nanoTime();
        double elapsedMillis = lastFrameNanos == 0 ? 0 : (now - lastFrameNanos) / 1_000_000.0;
        lastFrameNanos = now;
        double step = elapsedMillis / SLIDE_MILLIS;
        slide = open ? Math.min(1, slide + step) : Math.max(0, slide - step);
    }

    /** Shows the hovered face's name and mode. Call from {@code extractTooltip}. */
    public void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (isOver(mouseX, mouseY, buttonX, buttonY, BUTTON_SIZE, BUTTON_SIZE)) {
            graphics.setTooltipForNextFrame(Component.translatable("gui.futuretech.sides"), mouseX, mouseY);
            return;
        }
        if (!isFullyOpen()) return;
        Direction front = menu.front();
        for (Face face : Face.values()) {
            if (!isOver(mouseX, mouseY, tileX(face), tileY(face), TILE, TILE)) continue;
            SideMode mode = menu.sideMode(face.resolve(front));
            List<net.minecraft.util.FormattedCharSequence> lines = new ArrayList<>(List.of(
                    Component.translatable(face.key).getVisualOrderText(),
                    Component.translatable(mode.translationKey()).getVisualOrderText()));
            if (face == Face.FRONT) lines.add(Component.translatable("gui.futuretech.side.clear_hint").getVisualOrderText());
            graphics.setTooltipForNextFrame(lines, mouseX, mouseY);
            return;
        }
    }

    /** Handles a left click; returns true when the click landed on the button or a face. */
    public boolean mouseClicked(MouseButtonEvent event) {
        if (event.button() != 0) return false;
        double mouseX = event.x();
        double mouseY = event.y();
        if (isOver(mouseX, mouseY, buttonX, buttonY, BUTTON_SIZE, BUTTON_SIZE)) {
            open = !open;
            return true;
        }
        if (!isFullyOpen()) return false;
        Direction front = menu.front();
        for (Face face : Face.values()) {
            if (!isOver(mouseX, mouseY, tileX(face), tileY(face), TILE, TILE)) continue;
            var gameMode = Minecraft.getInstance().gameMode;
            // Shift-clicking the front is the quick way to close every face at once.
            int buttonId = face == Face.FRONT && event.hasShiftDown()
                    ? SideConfigMenu.BUTTON_CLEAR_ALL : face.resolve(front).ordinal();
            if (gameMode != null) gameMode.handleInventoryButtonClick(menu.containerId, buttonId);
            // Same click as vanilla menu buttons.
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }
        return false;
    }

    private int tileX(Face face) { return gridX + face.column * CELL; }

    private int tileY(Face face) { return gridY + face.row * CELL; }

    private static boolean isOver(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private static int modeColor(SideMode mode) {
        return switch (mode) {
            case NONE -> 0xFF56616D;
            case INPUT -> 0xFF1676C4;
            case OUTPUT -> 0xFFEC761C;
            case BOTH -> 0xFF3FA34D;
        };
    }

    private void drawIcon(GuiGraphicsExtractor graphics) {
        // A tiny unfolded cube marks the tab.
        // The 3x3 cell glyph is 8 px wide, so an offset of 6 centres it in the 20 px tab.
        int cx = buttonX + 5;
        int cy = buttonY + 5;
        for (Face face : Face.values()) {
            int x = cx + 1 + face.column * 3;
            int y = cy + face.row * 3 + 1;
            graphics.fill(x, y, x + 2, y + 2, face == Face.FRONT ? 0xFFEC761C : TITLE);
        }
    }

    /** Sprite the block model uses for {@code side}; resolved once per state and cached. */
    private @Nullable TextureAtlasSprite faceSprite(Direction side) {
        BlockState state = menu.displayState();
        if (state != cachedState) {
            spriteCache.clear();
            cachedState = state;
        }
        if (spriteCache.containsKey(side)) return spriteCache.get(side);
        var level = Minecraft.getInstance().level;
        if (level == null) return null;
        TextureAtlasSprite sprite = null;
        BlockStateModel model = Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(state);
        List<BlockStateModelPart> parts = new ArrayList<>();
        model.collectParts(level, BlockPos.ZERO, state, RandomSource.create(42), parts);
        for (BlockStateModelPart part : parts) {
            var quads = part.getQuads(side);
            if (!quads.isEmpty()) {
                sprite = quads.getFirst().materialInfo().sprite();
                break;
            }
        }
        if (sprite == null) sprite = model.particleMaterial(level, BlockPos.ZERO, state).sprite();
        spriteCache.put(side, sprite);
        return sprite;
    }
}
