package dev.futuretech.client;

import static dev.futuretech.client.MachineScreenStyle.*;

import com.mojang.blaze3d.platform.InputConstants;
import dev.futuretech.block.entity.WirelessRedstoneBlockEntity;
import dev.futuretech.redstone.WirelessRedstonePayloads;
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

/**
 * The screen both wireless plates share: one box with a frequency in it. Four digits, nothing
 * else — two plates with the same number are wired together, wherever they are. Done sends the
 * number up and closes; Cancel and Escape leave the plate on the frequency it was on.
 *
 * <p>There is no menu under it — neither plate has a slot or anything to sync — so it is a plain
 * screen over the world, opened by the server with what it shows.
 */
public final class WirelessRedstoneScreen extends Screen {
    private static final int WIDTH = 176;
    private static final int LABEL_X = 7;
    private static final int BOX_X = 72;
    private static final int BOX_WIDTH = 96;
    private static final int BOX_HEIGHT = 12;
    private static final int FREQUENCY_Y = 27;
    private static final int BUTTON_Y = FREQUENCY_Y + BOX_HEIGHT + 10;
    private static final int BUTTON_WIDTH = 76;
    private static final int BUTTON_HEIGHT = 16;
    private static final int DONE_X = 8;
    private static final int CANCEL_X = WIDTH - 8 - BUTTON_WIDTH;
    private static final int HEIGHT = BUTTON_Y + BUTTON_HEIGHT + 8;
    /** Four digits and nothing that is not one. */
    private static final int DIGITS = 4;

    private final BlockPos pos;
    private final int frequency;
    private EditBox frequencyBox;
    private int left;
    private int top;

    private WirelessRedstoneScreen(BlockPos pos, int frequency, Component title) {
        super(title);
        this.pos = pos;
        this.frequency = frequency;
    }

    /** Opens the screen the server asked for, on the plate the player clicked. */
    public static void open(WirelessRedstonePayloads.Open payload) {
        var minecraft = Minecraft.getInstance();
        var level = minecraft.level;
        // The title is the plate's own name, so the screen says which of the two ends this is.
        Component title = level == null ? Component.empty() : level.getBlockState(payload.pos()).getBlock().getName();
        minecraft.gui.setScreen(new WirelessRedstoneScreen(payload.pos(), payload.frequency(), title));
    }

    @Override
    protected void init() {
        super.init();
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
        String typed = frequencyBox == null ? Integer.toString(frequency) : frequencyBox.getValue();
        frequencyBox = new EditBox(font, left + BOX_X, top + FREQUENCY_Y, BOX_WIDTH, BOX_HEIGHT,
                Component.translatable("gui.futuretech.wireless.frequency"));
        frequencyBox.setMaxLength(DIGITS);
        // A frequency is a number: anything else typed or pasted is dropped as it arrives. Putting
        // the digits back calls this again, and the second time there is nothing left to drop.
        frequencyBox.setResponder(value -> {
            String digits = value.replaceAll("\\D", "");
            if (!digits.equals(value)) frequencyBox.setValue(digits);
        });
        frequencyBox.setValue(typed);
        addRenderableWidget(frequencyBox);
        setInitialFocus(frequencyBox);
    }

    /** What is in the box, or the frequency the plate is already on when the box was emptied. */
    private int typed() {
        String value = frequencyBox.getValue();
        if (value.isEmpty()) return frequency;
        return Math.clamp(Integer.parseInt(value), 0, WirelessRedstoneBlockEntity.MAX_FREQUENCY);
    }

    private void done() {
        ClientPacketDistributor.sendToServer(new WirelessRedstonePayloads.Frequency(pos, typed()));
        onClose();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        drawPanel(graphics, left, top, WIDTH, HEIGHT);
        drawButton(graphics, font, left + DONE_X, top + BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT, CommonComponents.GUI_DONE,
                overButton(mouseX, mouseY, left + DONE_X, top + BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT));
        drawButton(graphics, font, left + CANCEL_X, top + BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT, CommonComponents.GUI_CANCEL,
                overButton(mouseX, mouseY, left + CANCEL_X, top + BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.text(font, title, left + LABEL_X, top + 6, TITLE, false);
        graphics.text(font, Component.translatable("gui.futuretech.wireless.frequency"),
                left + LABEL_X, top + FREQUENCY_Y + 2, TEXT, false);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (overButton(event.x(), event.y(), left + DONE_X, top + BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT)) {
            click();
            done();
            return true;
        }
        if (overButton(event.x(), event.y(), left + CANCEL_X, top + BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT)) {
            click();
            onClose();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    /** Enter in the box is Done, the way it is in any box with one button waiting on it. */
    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER) {
            done();
            return true;
        }
        return super.keyPressed(event);
    }

    private static void click() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
