package dev.futuretech.client;

import static dev.futuretech.client.MachineScreenStyle.*;

import com.mojang.blaze3d.platform.InputConstants;
import dev.futuretech.block.entity.TesseractBlockEntity;
import dev.futuretech.transfer.TesseractPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.List;

/**
 * The tesseract's screen: a name for it to type (the title stays the block's), then a name to
 * type and a plus to make it a channel,
 * then the world's channels in a list with the one this tesseract is on lit. Clicking a channel
 * puts the tesseract on it there and then, the same one again takes it off; Delete, live only
 * with a channel picked, asks before taking that channel out of the world. Cancel and Escape
 * close; nothing here waits for them, every change went up when it was made.
 *
 * <p>There is no menu under this screen — the block has no slots and nothing to sync — so it is
 * a plain screen over the world, opened by the server with what it shows.
 */
public final class TesseractScreen extends Screen {
    private static final int WIDTH = 176;
    private static final int LABEL_X = 7;
    private static final int BOX_X = 52;
    private static final int BOX_HEIGHT = 12;
    private static final int NAME_Y = 27;
    private static final int NAME_WIDTH = 116;
    private static final int CHANNEL_Y = 47;
    private static final int CHANNEL_WIDTH = 92;
    /** The plus beside the channel box: a slot-sized square, level with the box. */
    private static final int PLUS_X = BOX_X + CHANNEL_WIDTH + 4;
    private static final int PLUS_SIZE = 16;
    private static final int PLUS_Y = CHANNEL_Y - (PLUS_SIZE - BOX_HEIGHT) / 2;
    private static final int ROW_X = 8;
    private static final int ROW_Y = 70;
    private static final int ROW_WIDTH = 160;
    private static final int ROW_HEIGHT = 16;
    private static final int ROW_SPACING = 18;
    private static final int VISIBLE = 5;
    private static final int SCROLL_WIDTH = 6;
    private static final int SCROLL_GUTTER = SCROLL_WIDTH + 2;
    private static final int BUTTON_Y = ROW_Y + VISIBLE * ROW_SPACING + 4;
    private static final int BUTTON_WIDTH = 76;
    private static final int BUTTON_HEIGHT = 16;
    private static final int DELETE_X = 8;
    private static final int CANCEL_X = WIDTH - 8 - BUTTON_WIDTH;
    private static final int HEIGHT = BUTTON_Y + BUTTON_HEIGHT + 8;
    private static final int ROW_BACK = 0xFF56616D;
    private static final int ROW_FACE = 0xFF65717D;
    private static final int SELECTED = 0xFF55E7ED;
    private static final int MUTED = 0xFF8B959F;
    private static final int SCROLL_TRACK = 0xFF3E4752;
    private static final int SCROLL_KNOB = 0xFF8B959F;
    private static final int MIN_KNOB = 12;
    private static final Component PLUS = Component.literal("+");

    private final BlockPos pos;
    private String name;
    private List<String> channels;
    /** The channel this tesseract is on, as last sent; the row it lights. */
    private String chosen;
    private EditBox nameBox;
    private EditBox channelBox;
    private int scrollRow;
    private boolean draggingKnob;
    private int left;
    private int top;

    private TesseractScreen(BlockPos pos, String name, String channel, List<String> channels) {
        super(Component.translatable("block.futuretech.tesseract"));
        this.pos = pos;
        this.name = name;
        this.chosen = channel;
        this.channels = channels;
    }

    /** Opens the screen the server asked for, on the tesseract the player clicked. */
    public static void open(TesseractPayloads.Open payload) {
        Minecraft.getInstance().gui.setScreen(new TesseractScreen(payload.pos(), payload.name(), payload.channel(), payload.channels()));
    }

    /** The screen that is open, if it is one of these, with or without the confirm over it. */
    private static TesseractScreen showing() {
        var screen = Minecraft.getInstance().gui.screen();
        if (screen instanceof TesseractScreen tesseract) return tesseract;
        if (screen instanceof DeleteChannelScreen confirm) return confirm.parent;
        return null;
    }

    /** The world's list changed: a screen that is open shows it, and a channel gone unpicks itself. */
    public static void refresh(List<String> channels) {
        TesseractScreen screen = showing();
        if (screen == null) return;
        screen.channels = channels;
        if (!channels.contains(screen.chosen)) screen.chosen = "";
    }

    /** The server said how many are on the channel the player wants gone: time to ask. */
    public static void confirmDelete(String channel, int count) {
        var gui = Minecraft.getInstance().gui;
        if (gui.screen() instanceof TesseractScreen screen) {
            gui.pushScreenLayer(new DeleteChannelScreen(screen, channel, count));
        }
    }

    @Override
    protected void init() {
        super.init();
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
        String typedName = nameBox == null ? name : nameBox.getValue();
        nameBox = new EditBox(font, left + BOX_X, top + NAME_Y, NAME_WIDTH, BOX_HEIGHT,
                Component.translatable("gui.futuretech.teleporter.name"));
        nameBox.setMaxLength(TesseractBlockEntity.CHANNEL_LENGTH);
        nameBox.setValue(typedName);
        nameBox.setHint(Component.translatable("gui.futuretech.tesseract.name_hint"));
        // Every keystroke goes up: the server keeps the last one, and the name is short.
        nameBox.setResponder(typed -> {
            if (typed.equals(name)) return;
            name = typed;
            ClientPacketDistributor.sendToServer(new TesseractPayloads.Rename(pos, typed));
        });
        addRenderableWidget(nameBox);
        String typedChannel = channelBox == null ? "" : channelBox.getValue();
        channelBox = new EditBox(font, left + BOX_X, top + CHANNEL_Y, CHANNEL_WIDTH, BOX_HEIGHT,
                Component.translatable("gui.futuretech.tesseract.channel"));
        channelBox.setMaxLength(TesseractBlockEntity.CHANNEL_LENGTH);
        channelBox.setValue(typedChannel);
        channelBox.setHint(Component.translatable("gui.futuretech.tesseract.channel_hint"));
        addRenderableWidget(channelBox);
    }

    /** Makes the typed name a channel and puts the tesseract on it; the list comes back from the server. */
    private void create() {
        String channel = channelBox.getValue().strip();
        if (channel.isEmpty()) return;
        ClientPacketDistributor.sendToServer(new TesseractPayloads.Create(channel));
        choose(channel);
        channelBox.setValue("");
    }

    private void choose(String channel) {
        chosen = channel;
        ClientPacketDistributor.sendToServer(new TesseractPayloads.Choose(pos, channel));
    }

    /** Asks the server how many are on the picked channel; the answer opens the confirm. */
    private void askDelete() {
        if (chosen.isEmpty()) return;
        ClientPacketDistributor.sendToServer(new TesseractPayloads.Ask(chosen));
    }

    private boolean canDelete() { return !chosen.isEmpty(); }

    private int rows() { return channels.size(); }

    private int maxScroll() { return Math.max(0, rows() - VISIBLE); }

    private int rowWidth() { return maxScroll() > 0 ? ROW_WIDTH - SCROLL_GUTTER : ROW_WIDTH; }

    private static int rowY(int row) { return ROW_Y + row * ROW_SPACING; }

    /** The row of the list under the pointer, already scrolled, or -1. */
    private int rowAt(double mouseX, double mouseY) {
        int localX = (int) mouseX - left;
        int localY = (int) mouseY - top;
        if (localX < ROW_X || localX >= ROW_X + rowWidth()) return -1;
        for (int row = 0; row < VISIBLE; row++) {
            int y = rowY(row);
            if (localY >= y && localY < y + ROW_HEIGHT) {
                int index = scrollRow + row;
                return index < rows() ? index : -1;
            }
        }
        return -1;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        drawPanel(graphics, left, top, WIDTH, HEIGHT);
        scrollRow = Math.clamp(scrollRow, 0, maxScroll());
        int width = rowWidth();
        int hovered = rowAt(mouseX, mouseY);
        for (int row = 0; row < VISIBLE; row++) {
            int index = scrollRow + row;
            if (index >= rows()) break;
            int rowX = left + ROW_X;
            int rowTop = top + rowY(row);
            boolean lit = channels.get(index).equals(chosen);
            graphics.fill(rowX - 1, rowTop - 1, rowX + width + 1, rowTop + ROW_HEIGHT + 1, lit ? SELECTED : ROW_BACK);
            graphics.fill(rowX, rowTop, rowX + width, rowTop + ROW_HEIGHT, ROW_FACE);
            if (index == hovered) graphics.fill(rowX, rowTop, rowX + width, rowTop + ROW_HEIGHT, 0x40FFFFFF);
        }
        if (maxScroll() > 0) drawScrollbar(graphics);
        drawButton(graphics, font, left + PLUS_X, top + PLUS_Y, PLUS_SIZE, PLUS_SIZE, PLUS,
                overButton(mouseX, mouseY, left + PLUS_X, top + PLUS_Y, PLUS_SIZE, PLUS_SIZE));
        drawButton(graphics, font, left + DELETE_X, top + BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT,
                Component.translatable("gui.futuretech.tesseract.delete"),
                canDelete() && overButton(mouseX, mouseY, left + DELETE_X, top + BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT),
                canDelete());
        drawButton(graphics, font, left + CANCEL_X, top + BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT, CommonComponents.GUI_CANCEL,
                overButton(mouseX, mouseY, left + CANCEL_X, top + BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT));
    }

    private void drawScrollbar(GuiGraphicsExtractor graphics) {
        int barX = left + ROW_X + ROW_WIDTH - SCROLL_WIDTH;
        int barTop = top + ROW_Y - 1;
        int height = VISIBLE * ROW_SPACING;
        graphics.fill(barX, barTop, barX + SCROLL_WIDTH, barTop + height, SCROLL_TRACK);
        int knob = Math.max(MIN_KNOB, height * VISIBLE / Math.max(1, rows()));
        int knobY = barTop + (height - knob) * scrollRow / Math.max(1, maxScroll());
        graphics.fill(barX, knobY, barX + SCROLL_WIDTH, knobY + knob, SCROLL_KNOB);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.text(font, title, left + LABEL_X, top + 6, TITLE, false);
        graphics.text(font, Component.translatable("gui.futuretech.teleporter.name"), left + LABEL_X, top + NAME_Y + 2, TEXT, false);
        graphics.text(font, Component.translatable("gui.futuretech.tesseract.channel"), left + LABEL_X, top + CHANNEL_Y + 2, TEXT, false);
        if (channels.isEmpty()) {
            graphics.text(font, Component.translatable("gui.futuretech.tesseract.none"), left + ROW_X + 4, top + rowY(0) + 5, MUTED, false);
            return;
        }
        int width = rowWidth();
        for (int row = 0; row < VISIBLE; row++) {
            int index = scrollRow + row;
            if (index >= rows()) break;
            int textY = top + rowY(row) + (ROW_HEIGHT - font.lineHeight) / 2 + 1;
            graphics.text(font, font.plainSubstrByWidth(channels.get(index), width - 8), left + ROW_X + 4, textY, TITLE, false);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (overButton(event.x(), event.y(), left + PLUS_X, top + PLUS_Y, PLUS_SIZE, PLUS_SIZE)) {
            click();
            create();
            return true;
        }
        if (overButton(event.x(), event.y(), left + DELETE_X, top + BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT)) {
            if (!canDelete()) return true;
            click();
            askDelete();
            return true;
        }
        if (overButton(event.x(), event.y(), left + CANCEL_X, top + BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT)) {
            click();
            onClose();
            return true;
        }
        if (maxScroll() > 0 && isOverScrollbar(event.x(), event.y())) {
            draggingKnob = true;
            dragTo(event.y());
            return true;
        }
        int index = rowAt(event.x(), event.y());
        if (index >= 0) {
            // The same channel again takes the tesseract off every channel.
            String channel = channels.get(index);
            choose(channel.equals(chosen) ? "" : channel);
            click();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    static void click() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    private boolean isOverScrollbar(double mouseX, double mouseY) {
        int barX = left + ROW_X + ROW_WIDTH - SCROLL_WIDTH;
        int barTop = top + ROW_Y - 1;
        int height = VISIBLE * ROW_SPACING;
        return mouseX >= barX && mouseX < barX + SCROLL_WIDTH && mouseY >= barTop && mouseY < barTop + height;
    }

    private void dragTo(double mouseY) {
        int height = VISIBLE * ROW_SPACING;
        int knob = Math.max(MIN_KNOB, height * VISIBLE / Math.max(1, rows()));
        int travel = height - knob;
        if (travel <= 0) return;
        double offset = mouseY - top - (ROW_Y - 1) - knob / 2.0;
        scrollRow = Math.clamp((int) Math.round(offset * maxScroll() / travel), 0, maxScroll());
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (draggingKnob) {
            dragTo(event.y());
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        draggingKnob = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (maxScroll() > 0 && scrollY != 0) {
            scrollRow = Math.clamp(scrollRow - (int) Math.signum(scrollY), 0, maxScroll());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    /** Enter in the channel box makes the channel, the way the plus does. */
    @Override
    public boolean keyPressed(KeyEvent event) {
        if ((event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER) && channelBox.isFocused()) {
            create();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    /**
     * The question before a channel goes: which channel, and how many tesseracts are on it right
     * now. A layer over the tesseract's screen, which stays underneath; either answer pops it.
     */
    private static final class DeleteChannelScreen extends Screen {
        private static final int WIDTH = 176;
        private static final int HEIGHT = 74;
        private static final int TEXT_Y = 26;
        private static final int COUNT_Y = 38;
        private static final int BUTTON_Y = 50;

        private final TesseractScreen parent;
        private final String channel;
        private final int count;
        private int left;
        private int top;

        private DeleteChannelScreen(TesseractScreen parent, String channel, int count) {
            super(Component.translatable("gui.futuretech.tesseract.delete_title"));
            this.parent = parent;
            this.channel = channel;
            this.count = count;
        }

        @Override
        protected void init() {
            super.init();
            left = (width - WIDTH) / 2;
            top = (height - HEIGHT) / 2;
        }

        private void delete() {
            ClientPacketDistributor.sendToServer(new TesseractPayloads.Delete(channel));
            onClose();
        }

        @Override
        public void onClose() { minecraft.gui.popScreenLayer(); }

        @Override
        public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            super.extractBackground(graphics, mouseX, mouseY, partialTick);
            drawPanel(graphics, left, top, WIDTH, HEIGHT);
            drawButton(graphics, font, left + DELETE_X, top + BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT,
                    Component.translatable("gui.futuretech.tesseract.delete"),
                    overButton(mouseX, mouseY, left + DELETE_X, top + BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT));
            drawButton(graphics, font, left + CANCEL_X, top + BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT, CommonComponents.GUI_CANCEL,
                    overButton(mouseX, mouseY, left + CANCEL_X, top + BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT));
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            super.extractRenderState(graphics, mouseX, mouseY, partialTick);
            graphics.text(font, title, left + LABEL_X, top + 6, TITLE, false);
            graphics.text(font, Component.translatable("gui.futuretech.tesseract.delete_ask", channel), left + LABEL_X, top + TEXT_Y, TEXT, false);
            graphics.text(font, Component.translatable("gui.futuretech.tesseract.delete_count", count), left + LABEL_X, top + COUNT_Y, TEXT, false);
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            if (overButton(event.x(), event.y(), left + DELETE_X, top + BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT)) {
                click();
                delete();
                return true;
            }
            if (overButton(event.x(), event.y(), left + CANCEL_X, top + BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT)) {
                click();
                onClose();
                return true;
            }
            return super.mouseClicked(event, doubleClick);
        }

        @Override
        public boolean isPauseScreen() { return false; }
    }
}
