package dev.futuretech.api.facade;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.model.data.ModelProperty;

import java.util.Map;

/** What a facade is and which blocks may be worn as one, shared by the item, the cable and the model. */
public final class CableFacades {
    /** The block each covered face wears; a face with no entry shows the bare cable. */
    public static final ModelProperty<Map<Direction, BlockState>> FACADES = new ModelProperty<>();
    /** How deep the panel sits, in model units: thick enough to read as a wall, thin enough to clear the collars. */
    public static final float THICKNESS = 2.0F;

    private CableFacades() {}

    /**
     * Whether a block may be worn as a facade. The panel is built by dressing a slab in the block's
     * own face sprites, which only holds for a plain full cube: anything with its own shape shows
     * through where the slab cuts it, and anything with a block entity would have to run logic that
     * is not there. Checked without a level, because the item carries the state anywhere.
     */
    public static boolean isValid(BlockState state) {
        return !state.isAir()
                && state.getRenderShape() == RenderShape.MODEL
                && state.isSolidRender()
                && !state.hasBlockEntity()
                && state.getFluidState().isEmpty()
                && state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
    }
}
