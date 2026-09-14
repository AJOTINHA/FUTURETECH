package dev.futuretech.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class AssemblerResourcesTest {
    @Test
    void tableHasNoOverlappingFacesOnTheSamePlane() throws Exception {
        var elements = readModel("block/assembly_table").getAsJsonArray("elements");
        String[] faces = {"west", "east", "down", "up", "north", "south"};
        for (int i = 0; i < elements.size(); i++) for (int j = i + 1; j < elements.size(); j++) {
            var a = elements.get(i).getAsJsonObject();
            var b = elements.get(j).getAsJsonObject();
            for (int face = 0; face < faces.length; face++) {
                if (!a.getAsJsonObject("faces").has(faces[face]) || !b.getAsJsonObject("faces").has(faces[face])) continue;
                int axis = face / 2;
                String edge = face % 2 == 0 ? "from" : "to";
                double planeA = a.getAsJsonArray(edge).get(axis).getAsDouble();
                double planeB = b.getAsJsonArray(edge).get(axis).getAsDouble();
                if (Math.abs(planeA - planeB) > 0.00001) continue;
                boolean overlaps = true;
                for (int dimension = 0; dimension < 3; dimension++) {
                    if (dimension == axis) continue;
                    double start = Math.max(a.getAsJsonArray("from").get(dimension).getAsDouble(), b.getAsJsonArray("from").get(dimension).getAsDouble());
                    double end = Math.min(a.getAsJsonArray("to").get(dimension).getAsDouble(), b.getAsJsonArray("to").get(dimension).getAsDouble());
                    overlaps &= end - start > 0.00001;
                }
                assertFalse(overlaps, "Overlapping " + faces[face] + " faces on table elements " + i + " and " + j);
            }
        }
    }

    private static JsonObject readModel(String path) throws Exception {
        try (var stream = AssemblerResourcesTest.class.getResourceAsStream("/assets/futuretech/models/" + path + ".json")) {
            assertNotNull(stream, path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    @Test
    void blockAndItemModelsKeepEverySpriteInTheBlocksAtlas() throws Exception {
        Set<String> visited = new HashSet<>();
        for (String name : new String[]{"assembly_table", "transport_arm", "assembly_arm", "assembler_terminal"}) {
            checkModel("block/" + name, visited);
            checkModel("item/" + name, visited);
        }
    }

    private static void checkModel(String path, Set<String> visited) throws Exception {
        if (!visited.add(path)) return;
        JsonObject model = readModel(path);
        if (model.has("textures")) for (var entry : model.getAsJsonObject("textures").entrySet()) {
            String texture = entry.getValue().getAsString();
            assertTrue(texture.startsWith("futuretech:block/"), path + " mixes atlases: " + texture);
            String resource = "/assets/futuretech/textures/" + texture.substring("futuretech:".length()) + ".png";
            assertNotNull(AssemblerResourcesTest.class.getResource(resource), resource);
        }
        if (model.has("parent")) {
            String parent = model.get("parent").getAsString();
            if (parent.startsWith("futuretech:")) checkModel(parent.substring("futuretech:".length()), visited);
        }
    }
}
