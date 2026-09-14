package dev.futuretech.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class FluidTankResourcesTest {
    private static JsonObject json(String path) throws Exception {
        try (var stream = FluidTankResourcesTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(stream, path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    @Test
    void batteryFrameIsPreservedAndGlassSealsAllSixFaces() throws Exception {
        var battery = json("assets/futuretech/models/block/battery_mk1.json");
        var tank = json("assets/futuretech/models/block/fluid_tank.json");
        var frame = battery.getAsJsonArray("elements");
        var elements = tank.getAsJsonArray("elements");
        assertEquals(frame.size() + 6, elements.size());
        for (int i = 0; i < frame.size(); i++) assertEquals(frame.get(i), elements.get(i));
        assertEquals("minecraft:block/glass", tank.getAsJsonObject("textures").get("glass").getAsString());
        for (int axis = 0; axis < 3; axis++) for (int face = 0; face < 2; face++) {
            var pane = elements.get(frame.size() + axis * 2 + face).getAsJsonObject();
            var from = pane.getAsJsonArray("from");
            var to = pane.getAsJsonArray("to");
            assertEquals(0.25, to.get(axis).getAsDouble() - from.get(axis).getAsDouble());
            // The fluid's 2..14 bounds stay strictly behind each pane, avoiding overlapping surfaces.
            if (face == 0) assertTrue(to.get(axis).getAsDouble() < 2);
            else assertTrue(from.get(axis).getAsDouble() > 14);
            for (int other = 0; other < 3; other++) if (other != axis) {
                assertTrue(from.get(other).getAsDouble() < 2);
                assertTrue(to.get(other).getAsDouble() > 14);
            }
            assertEquals(6, pane.getAsJsonObject("faces").size());
        }
    }

    @Test
    void droppedTankCopiesItsFluidComponent() throws Exception {
        var loot = json("data/futuretech/loot_table/blocks/fluid_tank.json");
        var entry = loot.getAsJsonArray("pools").get(0).getAsJsonObject()
                .getAsJsonArray("entries").get(0).getAsJsonObject();
        assertEquals("futuretech:fluid_tank", entry.get("name").getAsString());
        var copy = entry.getAsJsonArray("functions").get(0).getAsJsonObject();
        assertEquals("minecraft:copy_components", copy.get("function").getAsString());
        assertEquals("block_entity", copy.get("source").getAsString());
        assertEquals("futuretech:tank_fluid", copy.getAsJsonArray("include").get(0).getAsString());
    }
}
