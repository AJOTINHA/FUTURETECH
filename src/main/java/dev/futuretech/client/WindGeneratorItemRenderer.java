package dev.futuretech.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.special.NoDataSpecialModelRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import java.util.function.Consumer;

/** The item draws the same three-blade turbine, scaled to fit an ordinary item. */
public final class WindGeneratorItemRenderer implements NoDataSpecialModelRenderer {
    private final int mk;
    private WindGeneratorItemRenderer(int mk) { this.mk = mk; }
    @Override public void submit(PoseStack pose, SubmitNodeCollector collector, int light, int overlay, boolean foil, int outline) {
        pose.pushPose();
        pose.translate(.4F, 0, .4F);
        pose.scale(.2F, .2F, .2F);
        WindRotorRenderer.submitTurbine(pose, collector, mk, 0, 0, light, true);
        pose.popPose();
    }
    @Override public void getExtents(Consumer<Vector3fc> output) {
        output.accept(new Vector3f(.2F,0,.4F));
        output.accept(new Vector3f(.8F,1,.6F));
    }
    public record Unbaked(int mk) implements NoDataSpecialModelRenderer.Unbaked {
        public static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.intRange(1,4).fieldOf("mk").forGetter(Unbaked::mk)).apply(instance,Unbaked::new));
        @Override public MapCodec<Unbaked> type() { return MAP_CODEC; }
        @Override public WindGeneratorItemRenderer bake(SpecialModelRenderer.BakingContext context) { return new WindGeneratorItemRenderer(mk); }
    }
}
