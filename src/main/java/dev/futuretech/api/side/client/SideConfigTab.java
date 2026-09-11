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
import net.minecraft.core.BlockPos;
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
 * textures; clicking a face cycles its mode on the server, shift-clicking the front closes all.
 */
public final class SideConfigTab<M extends AbstractContainerMenu & SideConfigMenu> extends MachineTab {
    private static final int TILE = 16;
    // Tiles are 16 px plus a 1 px mode border on each side; 2 px of panel show between neighbours.
    private static final int CELL = TILE + 2 + 2;
    // Three tiles with their borders, edge to edge.
    private static final int GRID = CELL * 3 - (CELL - TILE) + 2;

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
    private final Map<Direction, TextureAtlasSprite> spriteCache = new EnumMap<>(Direction.class);
    private @Nullable BlockState cachedState;

    public SideConfigTab(M menu, Font font) {
        super(font);
        this.menu = menu;
    }

    @Override
    protected Component title() { return Component.translatable("gui.futuretech.sides"); }

    @Override
    protected int contentWidth() { return GRID; }

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
        Direction front = menu.front();
        for (Face face : Face.values()) {
            Direction side = face.resolve(front);
            int x = tileX(contentX, face);
            int y = tileY(contentY, face);
            graphics.fill(x - 1, y - 1, x + TILE + 1, y + TILE + 1, modeColor(menu.sideMode(side)));
            TextureAtlasSprite sprite = faceSprite(side);
            if (sprite != null) graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, TILE, TILE);
            else graphics.fill(x, y, x + TILE, y + TILE, 0xFF8B959F);
            if (isFullyOpen() && isOver(mouseX, mouseY, x, y, TILE, TILE)) graphics.fill(x, y, x + TILE, y + TILE, 0x40FFFFFF);
        }
    }

    @Override
    protected void contentTooltip(GuiGraphicsExtractor graphics, int contentX, int contentY, int mouseX, int mouseY) {
        Direction front = menu.front();
        for (Face face : Face.values()) {
            if (!isOver(mouseX, mouseY, tileX(contentX, face), tileY(contentY, face), TILE, TILE)) continue;
            SideMode mode = menu.sideMode(face.resolve(front));
            List<FormattedCharSequence> lines = new ArrayList<>(List.of(
                    Component.translatable(face.key).getVisualOrderText(),
                    Component.translatable(mode.translationKey()).getVisualOrderText()));
            if (face == Face.FRONT) lines.add(Component.translatable("gui.futuretech.side.clear_hint").getVisualOrderText());
            graphics.setTooltipForNextFrame(lines, mouseX, mouseY);
            return;
        }
    }

    @Override
    protected boolean clickContent(MouseButtonEvent event, int contentX, int contentY) {
        Direction front = menu.front();
        for (Face face : Face.values()) {
            if (!isOver(event.x(), event.y(), tileX(contentX, face), tileY(contentY, face), TILE, TILE)) continue;
            // Shift-clicking the front is the quick way to close every face at once.
            int buttonId = face == Face.FRONT && event.hasShiftDown()
                    ? SideConfigMenu.BUTTON_CLEAR_ALL : face.resolve(front).ordinal();
            var gameMode = Minecraft.getInstance().gameMode;
            if (gameMode != null) gameMode.handleInventoryButtonClick(menu.containerId, buttonId);
            // Same click as vanilla menu buttons.
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }
        return false;
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
