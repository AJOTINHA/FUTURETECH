package dev.futuretech.client;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.BlockDestructionProgress;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AreaMiningPreviewTest {
    private static final int PLAYER = 15;

    @Test
    void allTenStagesFollowLocalMiningInsteadOfAnotherPlayersProgress() {
        var local = new BlockDestructionProgress(PLAYER, BlockPos.ZERO);
        var other = new BlockDestructionProgress(42, BlockPos.ZERO);
        other.setProgress(8);
        for (int stage = 0; stage <= 9; stage++) {
            local.setProgress(stage);
            assertEquals(stage, AreaMiningPreview.localMiningStage(true, stage, PLAYER, List.of(local, other)));
        }
    }

    @Test
    void releasingAttackAndCompletingTheBreakClearTheExtraCracks() {
        var progress = List.of(new BlockDestructionProgress(PLAYER, BlockPos.ZERO));
        assertEquals(-1, AreaMiningPreview.localMiningStage(false, 7, PLAYER, progress));
        assertEquals(-1, AreaMiningPreview.localMiningStage(true, -1, PLAYER, progress));
        assertEquals(-1, AreaMiningPreview.localMiningStage(true, 10, PLAYER, progress));
    }

    @Test
    void movingCrosshairDoesNotTransferCracksToAnUnminedBlockOrAnotherPlayersBlock() {
        assertEquals(-1, AreaMiningPreview.localMiningStage(true, 7, PLAYER, null));
        assertEquals(-1, AreaMiningPreview.localMiningStage(true, 7, PLAYER, List.of()));
        var other = new BlockDestructionProgress(42, BlockPos.ZERO);
        other.setProgress(7);
        assertEquals(-1, AreaMiningPreview.localMiningStage(true, 7, PLAYER, List.of(other)));
    }
}
