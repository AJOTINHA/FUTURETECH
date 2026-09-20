package dev.futuretech.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.futuretech.FutureTech;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.special.NoDataSpecialModelRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.function.Consumer;

/**
 * The head of a wireless plate on the item: the dish and the floating hedron the block's renderer
 * draws, drawn again wherever the item is — in the hand, on the ground, in the inventory. The plate
 * and its mast are an ordinary model; this only fills in what is not made of boxes, the way the
 * tesseract's core does.
 */
public final class WirelessHeadRenderer implements NoDataSpecialModelRenderer {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(FutureTech.MOD_ID, "wireless_head");
    private static final String RECEIVER = "receiver";
    private static final float SPIN_PER_TICK = 0.9F;
    private static final long SPIN_PERIOD = 400;

    private final boolean receiver;

    private WirelessHeadRenderer(boolean receiver) { this.receiver = receiver; }

    @Override
    public void submit(PoseStack pose, SubmitNodeCollector collector, int lightCoords, int overlayCoords,
                       boolean hasFoil, int outlineColor) {
        // In the hand the plate lies flat and unturned, which is the model the item points at.
        WirelessRedstoneRenderer.submitHead(pose, collector, Direction.UP, 0, receiver, spin(), lightCoords);
    }

    /** The item's hedron turns with the world's clock, so every one of them turns together. */
    private static float spin() {
        var level = Minecraft.getInstance().level;
        return level == null ? 0 : Math.floorMod(level.getGameTime(), SPIN_PERIOD) * SPIN_PER_TICK;
    }

    /** What the item takes up, so it is not cut short: the dish, and the hedron where it floats. */
    @Override
    public void getExtents(Consumer<Vector3fc> output) {
        if (receiver) {
            for (var face : WirelessHeadMesh.DISH) output.accept(new Vector3f(face.a().x(), face.a().y(), face.a().z()));
        }
        float[] centre = receiver ? WirelessHeadMesh.RECEIVER_HEDRON : WirelessHeadMesh.TRANSMITTER_HEDRON;
        for (var face : WirelessHeadMesh.HEDRON) {
            output.accept(new Vector3f(centre[0] + face.a().x(), centre[1] + face.a().y(), centre[2] + face.a().z()));
        }
    }

    /** Which of the two plates this is; there is nothing else to read from the stack. */
    public record Unbaked(String kind) implements NoDataSpecialModelRenderer.Unbaked {
        public static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.STRING.fieldOf("kind").forGetter(Unbaked::kind)).apply(instance, Unbaked::new));

        @Override
        public MapCodec<Unbaked> type() { return MAP_CODEC; }

        @Override
        public WirelessHeadRenderer bake(SpecialModelRenderer.BakingContext context) {
            return new WirelessHeadRenderer(RECEIVER.equals(kind));
        }
    }
}
