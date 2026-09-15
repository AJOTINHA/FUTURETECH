package dev.futuretech.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.futuretech.perf.PerfSamplesPayload;
import dev.futuretech.perf.TickProfiler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;

import java.util.List;

/**
 * Draws what {@code /futuretech perf} measures: a label over every measured block within reach,
 * with the block's name and its cost per tick, and one line on the HUD with the mod's total for
 * the dimension. Colours say at a glance what matters: green is negligible, yellow is worth a
 * look, red is a block that is costing real tick time.
 */
public final class PerfOverlay {
    /** Below this the block is not worth a second look; above {@link #RED_MICROS} it is. */
    private static final int YELLOW_MICROS = 50;
    private static final int RED_MICROS = 200;
    /** One tick's budget at 20 ticks per second. */
    private static final int TICK_MICROS = 50_000;
    private static final int GREEN = 0xFF55FF55;
    private static final int YELLOW = 0xFFFFFF55;
    private static final int RED = 0xFFFF5555;
    private static final int FULL_BRIGHT = 15728880;

    private static boolean enabled;
    private static int totalMicros;
    private static int count;
    private static List<TickProfiler.Entry> entries = List.of();

    private PerfOverlay() {}

    /** A report from the server; an "off" report clears everything. */
    public static void accept(PerfSamplesPayload payload) {
        enabled = payload.enabled();
        totalMicros = payload.totalMicros();
        count = payload.count();
        entries = payload.enabled() ? payload.entries() : List.of();
    }

    /** Labels float a little above each measured block and face the camera, like name tags. */
    public static void submitLabels(SubmitCustomGeometryEvent event) {
        if (!enabled || entries.isEmpty()) return;
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        var camera = event.getLevelRenderState().cameraRenderState;
        Vec3 eye = camera.pos;
        PoseStack pose = event.getPoseStack();
        for (TickProfiler.Entry entry : entries) {
            var pos = entry.pos();
            Component name = level.getBlockState(pos).getBlock().getName();
            Component label = Component.empty().append(name).append(" ")
                    .append(Component.literal(entry.micros() + " µs").withColor(colour(entry.micros()) & 0xFFFFFF));
            pose.pushPose();
            // The name tag adds half a block on its own, so this puts the text 1.3 blocks up.
            pose.translate(pos.getX() + 0.5 - eye.x, pos.getY() + 0.8 - eye.y, pos.getZ() + 0.5 - eye.z);
            event.getSubmitNodeCollector().submitNameTag(pose, Vec3.ZERO, 0, label, true, FULL_BRIGHT, camera);
            pose.popPose();
        }
    }

    /** The dimension's total in the top-right corner, as microseconds and as a share of the tick. */
    public static void drawTotal(RenderGuiEvent.Post event) {
        if (!enabled) return;
        var minecraft = Minecraft.getInstance();
        var graphics = event.getGuiGraphics();
        String line = String.format("FUTURETECH %d µs/tick, %d blocos, %.1f%% do tick",
                totalMicros, count, 100.0 * totalMicros / TICK_MICROS);
        int width = minecraft.font.width(line);
        graphics.text(minecraft.font, line, graphics.guiWidth() - width - 4, 4, totalColour(totalMicros), true);
    }

    /** The whole mod under a twentieth of the tick is fine; over a fifth it is the thing to fix. */
    private static int totalColour(int micros) {
        return micros >= TICK_MICROS / 5 ? RED : micros >= TICK_MICROS / 20 ? YELLOW : GREEN;
    }

    private static int colour(int micros) {
        return micros >= RED_MICROS ? RED : micros >= YELLOW_MICROS ? YELLOW : GREEN;
    }
}
