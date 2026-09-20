package dev.futuretech.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.special.NoDataSpecialModelRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import org.joml.Vector3fc;

import java.util.function.Consumer;

/**
 * The tesseract's core on the item: the same cube of the End the block shows, drawn inside the
 * frame wherever the item is — in the hand, on the ground, in the inventory. The frame is an
 * ordinary model; this only fills it, the way the block's renderer fills the block.
 */
public final class TesseractCoreRenderer implements NoDataSpecialModelRenderer {
    @Override
    public void submit(PoseStack pose, SubmitNodeCollector collector, int lightCoords, int overlayCoords, boolean hasFoil, int outlineColor) {
        TesseractRenderer.submitCore(pose, collector);
    }

    @Override
    public void getExtents(Consumer<Vector3fc> output) { TesseractRenderer.VERTICES.forEach(output); }

    /** Nothing to read from the stack and nothing to bake: one renderer serves every tesseract. */
    public record Unbaked() implements NoDataSpecialModelRenderer.Unbaked {
        public static final MapCodec<Unbaked> MAP_CODEC = MapCodec.unit(new Unbaked());

        @Override
        public MapCodec<Unbaked> type() { return MAP_CODEC; }

        @Override
        public TesseractCoreRenderer bake(SpecialModelRenderer.BakingContext context) { return new TesseractCoreRenderer(); }
    }
}
