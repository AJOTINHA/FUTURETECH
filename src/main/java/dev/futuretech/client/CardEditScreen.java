package dev.futuretech.client;

import static dev.futuretech.client.MachineScreenStyle.*;

import com.mojang.blaze3d.platform.InputConstants;
import dev.futuretech.menu.NetworkPanelMenu;
import dev.futuretech.menu.TeleporterMenu;
import dev.futuretech.teleport.PanelView;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * One card of the panel's pad, opened from its pencil: the name the rows show for it, and the
 * colour the pad's beam takes while it is the destination. Done sends both up at once; Cancel and
 * Escape go back to the panel with nothing sent. It is a layer pushed over the panel's screen,
 * which stays underneath with its menu open, and popping the layer lands back on it as it was.
 */
public final class CardEditScreen extends Screen {
    private static final int WIDTH = 176;
    private static final int HEIGHT = 124;
    private static final int LABEL_X = 7;
    private static final int NAME_X = 42;
    private static final int NAME_Y = 23;
    private static final int NAME_WIDTH = 126;
    private static final int NAME_HEIGHT = 12;
    private static final int BEAM_LABEL_Y = 44;
    /** The swatches: two rows of eight, each the size of a slot, on the slot's spacing. */
    private static final int SWATCH_COLUMNS = 8;
    private static final int SWATCH_SIZE = 16;
    private static final int SWATCH_SPACING = 18;
    private static final int SWATCH_X = (WIDTH - SWATCH_COLUMNS * SWATCH_SPACING + 2) / 2;
    private static final int SWATCH_Y = 56;
    private static final int BUTTON_Y = 96;
    private static final int BUTTON_WIDTH = 78;
    private static final int BUTTON_HEIGHT = 20;
    private static final int SWATCH_BORDER = 0xFF56616D;
    private static final int SWATCH_CHOSEN = 0xFFFFFFFF;

    /** The colours on offer, the pad's own cyan first. */
    private static final int[] PALETTE = {
            0x55E7ED, 0x1676C4, 0x3B5BDB, 0x8A5CF6, 0xD64BF2, 0xFF6BB5, 0xEE3C40, 0xFF8C1A,
            0xFDCC02, 0xC8F542, 0x5BE36B, 0x1FA97A, 0xFFFFFF, 0xB8C2CC, 0x6B7480, 0x283541,
    };

    private final PanelView.Card card;
    private int colour;
    private EditBox nameBox;
    private int left;
    private int top;

    public CardEditScreen(PanelView.Card card) {
        super(Component.translatable("gui.futuretech.network_panel.rename"));
        this.card = card;
        this.colour = card.colour();
    }

    @Override
    protected void init() {
        super.init();
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
        String typed = nameBox == null ? card.name() : nameBox.getValue();
        nameBox = new EditBox(font, left + NAME_X, top + NAME_Y, NAME_WIDTH, NAME_HEIGHT,
                Component.translatable("gui.futuretech.teleporter.name"));
        nameBox.setMaxLength(TeleporterMenu.NAME_LENGTH);
        nameBox.setValue(typed);
        addRenderableWidget(nameBox);
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> done())
                .bounds(left + 8, top + BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
                .bounds(left + WIDTH - 8 - BUTTON_WIDTH, top + BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT).build());
        setInitialFocus(nameBox);
    }

    private void done() {
        NetworkPanelMenu.sendEdit(card.slot(), nameBox.getValue(), colour);
        onClose();
    }

    /** Pops this layer, back to the panel underneath: the screen's own close, which never closes the menu. */
    @Override
    public void onClose() { minecraft.gui.popScreenLayer(); }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        drawPanel(graphics, left, top, WIDTH, HEIGHT);
        for (int index = 0; index < PALETTE.length; index++) {
            int x = swatchX(index);
            int y = swatchY(index);
            boolean chosen = PALETTE[index] == colour;
            graphics.fill(x - 1, y - 1, x + SWATCH_SIZE + 1, y + SWATCH_SIZE + 1, chosen ? SWATCH_CHOSEN : SWATCH_BORDER);
            graphics.fill(x, y, x + SWATCH_SIZE, y + SWATCH_SIZE, 0xFF000000 | PALETTE[index]);
            if (swatchAt(mouseX, mouseY) == index) graphics.fill(x, y, x + SWATCH_SIZE, y + SWATCH_SIZE, 0x40FFFFFF);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.text(font, title, left + LABEL_X, top + 6, TITLE, false);
        graphics.text(font, Component.translatable("gui.futuretech.teleporter.name"), left + LABEL_X, top + NAME_Y + 2, TEXT, false);
        graphics.text(font, Component.translatable("gui.futuretech.network_panel.beam"), left + LABEL_X, top + BEAM_LABEL_Y, TEXT, false);
    }

    private int swatchX(int index) { return left + SWATCH_X + (index % SWATCH_COLUMNS) * SWATCH_SPACING; }

    private int swatchY(int index) { return top + SWATCH_Y + (index / SWATCH_COLUMNS) * SWATCH_SPACING; }

    /** The swatch under the pointer, or -1. */
    private int swatchAt(double mouseX, double mouseY) {
        for (int index = 0; index < PALETTE.length; index++) {
            int x = swatchX(index);
            int y = swatchY(index);
            if (mouseX >= x && mouseX < x + SWATCH_SIZE && mouseY >= y && mouseY < y + SWATCH_SIZE) return index;
        }
        return -1;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int swatch = swatchAt(event.x(), event.y());
        if (swatch >= 0) {
            colour = PALETTE[swatch];
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    /** Enter is Done, from anywhere on the screen; Escape is the screen's own, back to the panel. */
    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER) {
            done();
            return true;
        }
        return super.keyPressed(event);
    }

    /** The world keeps going underneath, the way it does under the panel. */
    @Override
    public boolean isPauseScreen() { return false; }
}
