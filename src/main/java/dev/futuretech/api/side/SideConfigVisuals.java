package dev.futuretech.api.side;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.TagValueOutput;
import net.neoforged.neoforge.model.data.ModelData;
import net.neoforged.neoforge.model.data.ModelProperty;

/** Immutable render snapshots and small update tags shared by configurable machines. */
public final class SideConfigVisuals {
    /** Every face mode in one value: {@value #BITS} bits per {@link Direction} ordinal, holding a {@link SideMode} ordinal. */
    public static final ModelProperty<Integer> FACE_MODES = new ModelProperty<>();
    private static final int BITS = 3;
    private static final int MASK = (1 << BITS) - 1;

    private SideConfigVisuals() {}

    public static int faceModes(SideConfig sides) {
        int packed = 0;
        for (Direction side : Direction.values()) {
            packed |= sides.mode(side).ordinal() << (side.ordinal() * BITS);
        }
        return packed;
    }

    /** Every face on one mode, for render paths that draw before a block entity's data arrives. */
    public static int faceModes(SideMode uniform) {
        int packed = 0;
        for (Direction side : Direction.values()) packed |= uniform.ordinal() << (side.ordinal() * BITS);
        return packed;
    }

    public static SideMode mode(int packed, Direction side) {
        return SideMode.byOrdinal((packed >>> (side.ordinal() * BITS)) & MASK);
    }

    public static ModelData modelData(SideConfig sides) {
        return ModelData.builder().with(FACE_MODES, faceModes(sides)).build();
    }

    /** Only face configuration is public render data; inventories and energy stay in their menus. */
    public static CompoundTag updateTag(SideConfig sides) {
        var output = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
        sides.save(output);
        return output.buildResult();
    }

    /** Server: sends an update packet. Client: refreshes the snapshot and rebuilds the chunk mesh. */
    public static void refresh(BlockEntity entity) {
        entity.requestModelDataUpdate();
        var level = entity.getLevel();
        if (level != null) {
            var state = entity.getBlockState();
            level.sendBlockUpdated(entity.getBlockPos(), state, state, Block.UPDATE_CLIENTS);
        }
    }
}
