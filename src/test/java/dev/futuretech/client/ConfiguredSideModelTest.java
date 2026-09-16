package dev.futuretech.client;

import dev.futuretech.api.side.SideMode;
import dev.futuretech.api.side.client.ConfiguredSideModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConfiguredSideModelTest {
    private static final BlockStateModel FRAME = new BlockStateModel() {
        @Override public void collectParts(RandomSource random, List<BlockStateModelPart> output) {}
        @Override public Material.Baked particleMaterial() { throw new UnsupportedOperationException(); }
        @Override public int materialFlags() { return 0; }
    };

    @Test
    void batteryModelAcceptsEmptyRetextureAndPreviewMapsDuringResourceLoading() {
        // Battery ports add geometry, so the replacement texture map is deliberately Map.of().
        // EnumMap(Map) cannot infer its key type from that empty map and used to abort startup.
        var model = assertDoesNotThrow(() -> new ConfiguredSideModel(FRAME, Map.of(), Map.of(), Map.of()));
        for (SideMode mode : SideMode.values()) assertNull(model.spriteFor(mode));
    }

    @Test
    void modelWithNoConfiguredTexturesCanUseTheDefaultConstructor() {
        assertDoesNotThrow(() -> new ConfiguredSideModel(FRAME, Map.of()));
    }
}
