package dev.futuretech.api.side.client;

import dev.futuretech.api.gui.MachineTab;
import dev.futuretech.api.side.SideConfigMenu;
import dev.futuretech.api.side.SideMode;
import dev.futuretech.api.side.SlotRole;
import dev.futuretech.block.FluidTankBlock;
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
 *
 * <p>While the tab is open, the panel's slots wear the same colours: a blue frame on the slots the
 * input faces feed and an orange one on the slots the output faces empty.
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
        PULL("pull", PULL_COLOR, SideConfigMenu.BUTTON_AUTO_PULL, ARROW),
        PUSH("push", PUSH_COLOR, SideConfigMenu.BUTTON_AUTO_PUSH, ToggleArt.flipped(ARROW));

        final String key;
        final int color;
        final int buttonId;
        /** Pulling points down, into the machine; pushing points up, out of it. */
        final String[] glyph;

        Toggle(String key, int color, int buttonId, String[] glyph) {
            this.key = "gui.futuretech.auto." + key;
            this.color = color;
            this.buttonId = buttonId;
            this.glyph = glyph;
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

    /** Frames the input and output slots in their face colours while the tab is open; the frame is the slot's own 1 px border. */
    @Override
    public void drawPanelOverlay(GuiGraphicsExtractor graphics, int leftPos, int topPos) {
        if (!isOpen()) return;
        for (int index = 0; index < menu.slots.size(); index++) {
            SlotRole role = menu.slotRole(index);
            if (role == SlotRole.NONE) continue;
            var slot = menu.slots.get(index);
            if (!slot.isActive()) continue;
            int x = leftPos + slot.x;
            int y = topPos + slot.y;
            int color = modeColor(role == SlotRole.INPUT ? SideMode.INPUT : SideMode.OUTPUT);
            graphics.fill(x - 1, y - 1, x + TILE + 1, y, color);
            graphics.fill(x - 1, y + TILE, x + TILE + 1, y + TILE + 1, color);
            graphics.fill(x - 1, y, x, y + TILE, color);
            graphics.fill(x + TILE, y, x + TILE + 1, y + TILE, color);
        }
    }

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
            if (menu.displayState().getBlock() instanceof FluidTankBlock) {
                // Glass behind the assembled frame; the neutral border still identifies a closed face.
                graphics.fill(x + 3, y + 3, x + 13, y + 13, 0xFF526570);
                graphics.fill(x + 4, y + 4, x + 5, y + 8, 0xFF91AAB5);
                graphics.fill(x + 5, y + 4, x + 8, y + 5, 0xFF91AAB5);
                graphics.fill(x + 9, y + 11, x + 12, y + 12, 0xFF718B99);
            }
            TextureAtlasSprite sprite = faceSprite(side);
            if (sprite != null) graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, TILE, TILE);
            else graphics.fill(x, y, x + TILE, y + TILE, 0xFF8B959F);
            if (menu.sideMode(side) == SideMode.BOTH && menu.displayState().getBlock() instanceof FluidTankBlock) {
                var model = Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(menu.displayState());
                if (model instanceof ConfiguredSideModel configured && configured.spriteFor(SideMode.OUTPUT) != null) {
                    graphics.enableScissor(x + TILE / 2, y, x + TILE, y + TILE);
                    graphics.blitSprite(RenderPipelines.GUI_TEXTURED, configured.spriteFor(SideMode.OUTPUT), x, y, TILE, TILE);
                    graphics.disableScissor();
                }
            }
            if (isFullyOpen() && isOver(mouseX, mouseY, x, y, TILE, TILE)) graphics.fill(x, y, x + TILE, y + TILE, 0x40FFFFFF);
        }
    }

    private void drawToggles(GuiGraphicsExtractor graphics, int contentX, int contentY, int mouseX, int mouseY) {
        List<Toggle> shown = toggles();
        for (int index = 0; index < shown.size(); index++) {
            Toggle toggle = shown.get(index);
            int x = contentX + 1;
            int y = toggleY(contentY, index, shown.size());
            ToggleArt.draw(graphics, x, y, toggle.color, isOn(toggle), toggle.glyph,
                    isFullyOpen() && isOver(mouseX, mouseY, x, y, TILE, TILE));
        }
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
            graphics.setTooltipForNextFrame(List.of(
                    Component.translatable(face.key).getVisualOrderText(),
                    Component.translatable(mode.translationKey()).getVisualOrderText()), mouseX, mouseY);
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
