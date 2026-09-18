package dev.futuretech.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CableResourcesTest {
    private static final List<String> SIDES = List.of("north", "east", "south", "west", "up", "down");
    private static final int[] TURN_X = {0, 0, 0, 0, 270, 90};
    private static final int[] TURN_Y = {0, 90, 180, 270, 0, 0};

    private static JsonObject resource(String path) throws Exception {
        try (var stream = CableResourcesTest.class.getResourceAsStream("/assets/futuretech/" + path)) {
            assertNotNull(stream, path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static JsonObject model(String id) throws Exception {
        return resource("models/" + id.substring("futuretech:".length()) + ".json");
    }

    private static int turn(JsonObject apply, String axis) {
        return apply.has(axis) ? apply.get(axis).getAsInt() : 0;
    }

    private static List<JsonObject> selected(JsonObject state, int mask) {
        var result = new ArrayList<JsonObject>();
        for (var part : state.getAsJsonArray("multipart")) {
            var entry = part.getAsJsonObject();
            if (!entry.has("when") || matches(entry.getAsJsonObject("when"), mask)) {
                result.add(entry.getAsJsonObject("apply"));
            }
        }
        return result;
    }

    private static boolean matches(JsonObject when, int mask) {
        if (when.has("OR")) {
            for (var term : when.getAsJsonArray("OR")) if (matches(term.getAsJsonObject(), mask)) return true;
            return false;
        }
        if (when.has("AND")) {
            for (var term : when.getAsJsonArray("AND")) if (!matches(term.getAsJsonObject(), mask)) return false;
            return true;
        }
        for (var condition : when.entrySet()) {
            int index = SIDES.indexOf(condition.getKey());
            assertTrue(index >= 0, condition.getKey());
            if (((mask & (1 << index)) != 0) != condition.getValue().getAsBoolean()) return false;
        }
        return true;
    }

    /** Every box stays inside the block and every texture it names actually ships. */
    private void validate(String id) throws Exception {
        var geometry = model(id);
        for (var element : geometry.getAsJsonArray("elements")) {
            var cube = element.getAsJsonObject();
            for (int axis = 0; axis < 3; axis++) {
                double from = cube.getAsJsonArray("from").get(axis).getAsDouble();
                double to = cube.getAsJsonArray("to").get(axis).getAsDouble();
                assertTrue(from >= 0 && from < to && to <= 16, id);
            }
            for (var face : cube.getAsJsonObject("faces").entrySet()) {
                String ref = face.getValue().getAsJsonObject().get("texture").getAsString().substring(1);
                String texture = geometry.getAsJsonObject("textures").get(ref).getAsString();
                assertNotNull(getClass().getResource("/assets/futuretech/textures/"
                        + texture.substring("futuretech:".length()) + ".png"), texture);
            }
        }
    }

    @Test
    void everyConnectionStateIsAStraightRunOrANodeWithAnArmOrCapPerFace() throws Exception {
        var state = resource("blockstates/cable_mk1.json");
        for (int mask = 0; mask < 64; mask++) {
            var parts = selected(state, mask);
            boolean straight = mask == 5 || mask == 10 || mask == 48;
            assertEquals(straight ? 1 : 7, parts.size(), "A straight run, or a node with six face parts: " + mask);
            for (var part : parts) validate(part.get("model").getAsString());
            if (straight) {
                var run = parts.getFirst();
                assertEquals("futuretech:block/cable_mk1_line", run.get("model").getAsString());
                assertEquals(mask == 48 ? 270 : 0, turn(run, "x"));
                assertEquals(mask == 10 ? 90 : 0, turn(run, "y"));
                continue;
            }
            assertTrue(parts.stream().anyMatch(p -> p.get("model").getAsString().equals("futuretech:block/cable_mk1_node")),
                    "Anything but a straight run keeps the node: " + mask);
            for (int side = 0; side < 6; side++) {
                boolean connected = (mask & (1 << side)) != 0;
                String wanted = "futuretech:block/cable_mk1_" + (connected ? "arm" : "cap");
                int x = TURN_X[side];
                int y = TURN_Y[side];
                assertTrue(parts.stream().anyMatch(p -> p.get("model").getAsString().equals(wanted)
                                && turn(p, "x") == x && turn(p, "y") == y),
                        SIDES.get(side) + (connected ? " needs an arm on " : " needs a cap on ") + mask);
            }
        }
    }

    /**
     * The regression this guards: the run used to be four four-unit segments, and every joint
     * between them showed up in game as a thin dark line across the cable. One box per section,
     * spanning the whole block, is what removed them. Never slice it again.
     */
    @Test
    void straightRunIsOneBoxPerSectionWithNoFacesAcrossItsAxis() throws Exception {
        var elements = model("futuretech:block/cable_mk1_line").getAsJsonArray("elements");
        assertEquals(13, elements.size(), "One box per section of the cross section, never segments");
        for (var element : elements) {
            var cube = element.getAsJsonObject();
            assertEquals(0, cube.getAsJsonArray("from").get(2).getAsDouble(), "Boxes span the whole block");
            assertEquals(16, cube.getAsJsonArray("to").get(2).getAsDouble(), "Boxes span the whole block");
            var faces = cube.getAsJsonObject("faces");
            assertFalse(faces.has("north"), "A run is capped by its neighbour, never by itself");
            assertFalse(faces.has("south"), "A run is capped by its neighbour, never by itself");
        }
    }

    /**
     * The item cable is the same geometry in another skin: every model is a child of the energy
     * cable's, only the core textures change, and the blockstate is the same multipart renamed.
     */
    @Test
    void itemCableReusesTheCableGeometryWithItsOwnSkin() throws Exception {
        for (String part : List.of("arm", "cap", "line", "node")) {
            var model = model("futuretech:block/item_cable_opaque_" + part);
            assertEquals("futuretech:block/cable_mk1_" + part, model.get("parent").getAsString(), part);
            assertFalse(model.has("elements"), "No geometry of its own: " + part);
            var textures = model.getAsJsonObject("textures");
            assertEquals(2, textures.size(), "Only the core changes colour: " + part);
            for (var texture : textures.entrySet()) {
                String path = texture.getValue().getAsString();
                assertTrue(path.startsWith("futuretech:block/item_cable/item_cable_opaque"), path);
                assertNotNull(getClass().getResource("/assets/futuretech/textures/"
                        + path.substring("futuretech:".length()) + ".png"), path);
            }
        }
        var item = model("futuretech:item/item_cable_opaque");
        assertEquals("futuretech:item/cable_mk1", item.get("parent").getAsString());
        var energy = resource("blockstates/cable_mk1.json").toString();
        var items = resource("blockstates/item_cable_opaque.json").toString();
        assertEquals(energy.replace("futuretech:block/cable_mk1_", "futuretech:block/item_cable_opaque_"), items);
    }

    /**
     * Both fluid cables are the item cable's recipe again: children of the energy cable's models
     * with their own core textures. The see-through one gets its window from the texture alone:
     * the game picks each quad's layer from the sprite's alpha, so a model key would be ignored,
     * and fully transparent pixels land on the cutout layer, which needs no sorting.
     */
    @Test
    void fluidCablesReuseTheCableGeometryWithTheirOwnSkins() throws Exception {
        for (String name : List.of("fluid_cable_opaque", "fluid_cable")) {
            boolean glass = name.equals("fluid_cable");
            for (String part : List.of("arm", "cap", "line", "node")) {
                var model = model("futuretech:block/" + name + "_" + part);
                assertEquals("futuretech:block/cable_mk1_" + part, model.get("parent").getAsString(), part);
                assertFalse(model.has("elements"), "No geometry of its own: " + part);
                var textures = model.getAsJsonObject("textures");
                assertEquals(2, textures.size(), "Only the core changes: " + part);
                boolean window = false;
                for (var texture : textures.entrySet()) {
                    String path = texture.getValue().getAsString();
                    assertTrue(path.startsWith("futuretech:block/fluid_cable/" + name), path);
                    var png = getClass().getResource("/assets/futuretech/textures/"
                            + path.substring("futuretech:".length()) + ".png");
                    assertNotNull(png, path);
                    var image = javax.imageio.ImageIO.read(png);
                    for (int y = 0; y < image.getHeight(); y++) {
                        for (int x = 0; x < image.getWidth(); x++) {
                            int alpha = image.getRGB(x, y) >>> 24;
                            assertTrue(alpha == 0 || alpha == 255, "Cutout, never translucent: " + path);
                            window |= alpha == 0;
                        }
                    }
                }
                assertFalse(model.has("render_type"), "The layer comes from the texture: " + part);
                assertEquals(glass, window, "Only the glass cable has a window: " + part);
            }
            var energy = resource("blockstates/cable_mk1.json").toString();
            var fluid = resource("blockstates/" + name + ".json").toString();
            assertEquals(energy.replace("futuretech:block/cable_mk1_", "futuretech:block/" + name + "_"), fluid);
        }
    }

    /**
     * The network cable is the same recipe once more: children of the energy cable's models with
     * its own purple core, and the same multipart renamed. It carries nothing yet, so its skin is
     * opaque throughout — a window would promise something moving inside that is not there.
     */
    @Test
    void networkCableReusesTheCableGeometryWithItsOwnSkin() throws Exception {
        for (String part : List.of("arm", "cap", "line", "node")) {
            var model = model("futuretech:block/network_cable_" + part);
            assertEquals("futuretech:block/cable_mk1_" + part, model.get("parent").getAsString(), part);
            assertFalse(model.has("elements"), "No geometry of its own: " + part);
            var textures = model.getAsJsonObject("textures");
            assertEquals(2, textures.size(), "Only the core changes colour: " + part);
            for (var texture : textures.entrySet()) {
                String path = texture.getValue().getAsString();
                assertTrue(path.startsWith("futuretech:block/network_cable/network_cable"), path);
                var png = getClass().getResource("/assets/futuretech/textures/"
                        + path.substring("futuretech:".length()) + ".png");
                assertNotNull(png, path);
                var image = javax.imageio.ImageIO.read(png);
                for (int y = 0; y < image.getHeight(); y++) {
                    for (int x = 0; x < image.getWidth(); x++) {
                        assertEquals(255, image.getRGB(x, y) >>> 24, "Opaque throughout: " + path);
                    }
                }
            }
        }
        var item = model("futuretech:item/network_cable");
        assertEquals("futuretech:item/cable_mk1", item.get("parent").getAsString());
        var energy = resource("blockstates/cable_mk1.json").toString();
        var network = resource("blockstates/network_cable.json").toString();
        assertEquals(energy.replace("futuretech:block/cable_mk1_", "futuretech:block/network_cable_"), network);
    }

    /**
     * The redstone cable, the same again in red: the energy cable's models with their own core,
     * opaque throughout since nothing moves inside a wire, and the same multipart renamed.
     */
    @Test
    void redstoneCableReusesTheCableGeometryWithItsOwnSkin() throws Exception {
        for (String part : List.of("arm", "cap", "line", "node")) {
            var model = model("futuretech:block/redstone_cable_" + part);
            assertEquals("futuretech:block/cable_mk1_" + part, model.get("parent").getAsString(), part);
            assertFalse(model.has("elements"), "No geometry of its own: " + part);
            var textures = model.getAsJsonObject("textures");
            assertEquals(2, textures.size(), "Only the core changes colour: " + part);
            for (var texture : textures.entrySet()) {
                String path = texture.getValue().getAsString();
                assertTrue(path.startsWith("futuretech:block/redstone_cable/redstone_cable"), path);
                var png = getClass().getResource("/assets/futuretech/textures/"
                        + path.substring("futuretech:".length()) + ".png");
                assertNotNull(png, path);
                var image = javax.imageio.ImageIO.read(png);
                for (int y = 0; y < image.getHeight(); y++) {
                    for (int x = 0; x < image.getWidth(); x++) {
                        assertEquals(255, image.getRGB(x, y) >>> 24, "Opaque throughout: " + path);
                    }
                }
            }
        }
        var item = model("futuretech:item/redstone_cable");
        assertEquals("futuretech:item/cable_mk1", item.get("parent").getAsString());
        var energy = resource("blockstates/cable_mk1.json").toString();
        var redstone = resource("blockstates/redstone_cable.json").toString();
        assertEquals(energy.replace("futuretech:block/cable_mk1_", "futuretech:block/redstone_cable_"), redstone);
    }

    /**
     * The tiers past MK1 are the MK1 models in the MK's colour — yellow, red, cyan, the colours
     * of the battery's lines — with the same multipart renamed; only the core changes.
     */
    @Test
    void cableTiersReuseTheCableGeometryInTheirOwnColours() throws Exception {
        for (String tier : List.of("cable_mk2", "cable_mk3", "cable_mk4")) {
            for (String part : List.of("arm", "cap", "line", "node")) {
                var model = model("futuretech:block/" + tier + "_" + part);
                assertEquals("futuretech:block/cable_mk1_" + part, model.get("parent").getAsString(), part);
                assertFalse(model.has("elements"), "No geometry of its own: " + part);
                var textures = model.getAsJsonObject("textures");
                assertEquals(2, textures.size(), "Only the core changes colour: " + part);
                for (var texture : textures.entrySet()) {
                    String path = texture.getValue().getAsString();
                    assertTrue(path.startsWith("futuretech:block/" + tier + "/" + tier), path);
                    assertNotNull(getClass().getResource("/assets/futuretech/textures/"
                            + path.substring("futuretech:".length()) + ".png"), path);
                }
            }
            var item = model("futuretech:item/" + tier);
            assertEquals("futuretech:item/cable_mk1", item.get("parent").getAsString());
            var energy = resource("blockstates/cable_mk1.json").toString();
            var other = resource("blockstates/" + tier + ".json").toString();
            assertEquals(energy.replace("futuretech:block/cable_mk1_", "futuretech:block/" + tier + "_"), other);
        }
    }

    /**
     * The collar's contact ships for every kind, and for every tier of the energy cable, so no
     * cable can pull a missing texture into the atlas.
     */
    @Test
    void everyKindAndTierHasItsContactSprite() {
        for (var kind : dev.futuretech.block.CableKind.values()) {
            if (kind == dev.futuretech.block.CableKind.ENERGY) continue;
            // The same path AbstractCableBlock.contactTexture names.
            assertNotNull(getClass().getResource("/assets/futuretech/textures/block/"
                    + kind.id() + "/" + kind.id() + "_contact.png"), kind.name());
        }
        for (var tier : dev.futuretech.block.CableTier.values()) {
            // And the one CableBlock.contactTexture names for its tier.
            assertNotNull(getClass().getResource("/assets/futuretech/textures/block/"
                    + tier.blockName() + "/cable_contact.png"), tier.name());
        }
    }

    private static final String[] FACE_NAMES = {"west", "east", "down", "up", "north", "south"};

    /** Whether another box of the model touches this face over its whole area, hiding it for good. */
    private static boolean covered(JsonObject box, int face, java.util.List<JsonObject> others) {
        int axis = face / 2;
        boolean atMax = face % 2 == 1;
        double plane = (atMax ? box.getAsJsonArray("to") : box.getAsJsonArray("from")).get(axis).getAsDouble();
        for (var other : others) {
            double touch = (atMax ? other.getAsJsonArray("from") : other.getAsJsonArray("to")).get(axis).getAsDouble();
            if (touch != plane) continue;
            boolean inside = true;
            for (int a = 0; a < 3 && inside; a++) {
                if (a == axis) continue;
                inside = other.getAsJsonArray("from").get(a).getAsDouble() <= box.getAsJsonArray("from").get(a).getAsDouble()
                        && other.getAsJsonArray("to").get(a).getAsDouble() >= box.getAsJsonArray("to").get(a).getAsDouble();
            }
            if (inside) return true;
        }
        return false;
    }

    /**
     * The plain item cable is the cage with nothing inside. The MK1 models leave out the faces
     * the core used to hide, so those come back here; faces another bar still covers stay out,
     * and the arm and the run draw nothing across the cable's axis, since the neighbour, the node
     * or the collar's plug closes their ends and a face of their own there would fight it for the
     * same pixels.
     */
    @Test
    void itemCableIsTheCageAloneWithOnlyTheFacesThatCanBeSeen() throws Exception {
        for (String part : List.of("block/item_cable_arm", "block/item_cable_cap",
                "block/item_cable_line", "block/item_cable_node", "item/item_cable")) {
            boolean openEnds = part.endsWith("_arm") || part.endsWith("_line");
            var model = model("futuretech:" + part);
            var source = model("futuretech:" + part.replace("item_cable", "cable_mk1"));
            int cage = 0;
            for (var element : source.getAsJsonArray("elements")) {
                var faces = element.getAsJsonObject().getAsJsonObject("faces");
                boolean core = faces.entrySet().stream().anyMatch(face -> {
                    String texture = face.getValue().getAsJsonObject().get("texture").getAsString();
                    return texture.equals("#cable") || texture.equals("#node");
                });
                if (!core) cage++;
            }
            var boxes = new ArrayList<JsonObject>();
            for (var element : model.getAsJsonArray("elements")) boxes.add(element.getAsJsonObject());
            assertEquals(cage, boxes.size(), "Only the cage stays: " + part);
            for (var box : boxes) {
                var faces = box.getAsJsonObject("faces");
                var others = boxes.stream().filter(other -> other != box).toList();
                for (int face = 0; face < 6; face++) {
                    int axis = face / 2;
                    double plane = (face % 2 == 1 ? box.getAsJsonArray("to") : box.getAsJsonArray("from")).get(axis).getAsDouble();
                    boolean onEnd = openEnds && axis == 2;
                    boolean hidden = onEnd || covered(box, face, others);
                    assertEquals(!hidden, faces.has(FACE_NAMES[face]),
                            part + ": " + FACE_NAMES[face] + " of " + box.getAsJsonArray("from") + box.getAsJsonArray("to"));
                }
                for (var face : faces.entrySet()) {
                    String texture = face.getValue().getAsJsonObject().get("texture").getAsString();
                    assertTrue(texture.equals("#gray") || texture.equals("#white"), texture);
                }
            }
            assertFalse(model.getAsJsonObject("textures").has("cable"), part);
            assertFalse(model.getAsJsonObject("textures").has("node"), part);
        }
        var energy = resource("blockstates/cable_mk1.json").toString();
        var items = resource("blockstates/item_cable.json").toString();
        assertEquals(energy.replace("futuretech:block/cable_mk1_", "futuretech:block/item_cable_"), items);
    }

    @Test
    void inventoryShowsTheNodeWithAllSixFacesClosed() throws Exception {
        var item = resource("models/item/cable_mk1.json").getAsJsonArray("elements");
        assertEquals(37, item.size(), "Thirteen node boxes and four closing bars on each of the six faces");
        var placed = new HashMap<String, JsonObject>();
        for (var element : item) {
            var cube = element.getAsJsonObject();
            placed.put(cube.get("name") + "" + cube.getAsJsonArray("from") + cube.getAsJsonArray("to"), cube);
        }
        for (var element : model("futuretech:block/cable_mk1_node").getAsJsonArray("elements")) {
            var cube = element.getAsJsonObject();
            var shown = placed.get(cube.get("name") + "" + cube.getAsJsonArray("from") + cube.getAsJsonArray("to"));
            assertNotNull(shown, "The item keeps every node box");
            // Closing a face buries some of the node's own faces, so the item may drop one of
            // them, never invent one the node does not have.
            for (var face : shown.getAsJsonObject("faces").entrySet()) {
                assertTrue(cube.getAsJsonObject("faces").has(face.getKey()), "No face the node lacks");
            }
        }
        for (int axis = 0; axis < 3; axis++) {
            for (int near = 4, far = 5; near == 4 || near == 11; near += 7, far += 7) {
                int bars = 0;
                for (var element : item) {
                    var cube = element.getAsJsonObject();
                    if (!cube.get("name").getAsString().equals("white")) continue;
                    if (cube.getAsJsonArray("from").get(axis).getAsDouble() == near
                            && cube.getAsJsonArray("to").get(axis).getAsDouble() == far) bars++;
                }
                assertEquals(4, bars, "Four bars close the face at " + near + " on axis " + axis);
            }
        }
    }
}
