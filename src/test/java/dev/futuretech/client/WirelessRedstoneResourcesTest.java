package dev.futuretech.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.futuretech.block.WirelessRedstoneBlock;
import dev.futuretech.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The plates are drawn lying on the floor and turned by the blockstate, and what is not made of
 * boxes — the dish and the hedron — is drawn by {@link WirelessRedstoneRenderer}, which turns it
 * the same way by hand. These are the things that break quietly: a turn that stops matching the
 * renderer's, so the dish leans off the mast; a model asking for a sprite that is not there; and a
 * model grown out of the box the block claims, which is a mast you cannot click.
 */
@ExtendWith(EphemeralTestServerProvider.class)
class WirelessRedstoneResourcesTest {
    private static final String[] PLATES = {"wireless_transmitter", "wireless_receiver"};
    private static final String ASSETS = "/assets/futuretech/";

    private static JsonObject read(String path) throws Exception {
        try (var stream = WirelessRedstoneResourcesTest.class.getResourceAsStream(ASSETS + path)) {
            assertNotNull(stream, path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static JsonObject model(String path) throws Exception {
        return read("models/" + path + ".json");
    }

    @Test
    void everyFaceAndTurnMovesTheModelTheWayTheBlockSaysItMoves() throws Exception {
        var spins = WirelessRedstoneBlock.SPIN.getPossibleValues();
        for (String plate : PLATES) {
            var variants = read("blockstates/" + plate + ".json").getAsJsonObject("variants");
            assertEquals(Direction.values().length * spins.size() * 2, variants.size(), plate);
            for (Direction facing : Direction.values()) {
                for (int spin : spins) {
                    // The block's own table: the boxes are built from it and the renderer poses the
                    // dish by it, so a blockstate that disagrees is a dish left behind on its plate.
                    int[] angles = WirelessRedstoneBlock.angles(facing, spin);
                    for (boolean lit : new boolean[]{false, true}) {
                        String key = "facing=" + facing.getSerializedName() + ",lit=" + lit + ",spin=" + spin;
                        var variant = variants.getAsJsonObject(key);
                        assertNotNull(variant, plate + " is missing " + key);
                        String path = variant.get("model").getAsString().replace("futuretech:", "");
                        assertTrue(path.endsWith("_on") == lit, key + " points at " + path);
                        assertEquals(angles[0] == 90, path.contains(WirelessRedstoneBlock.TURNED), key + " points at " + path);
                        assertNotNull(model(path), path);
                        assertEquals(angles[1], variant.has("x") ? variant.get("x").getAsInt() : 0, key + " x");
                        assertEquals(angles[2], variant.has("y") ? variant.get("y").getAsInt() : 0, key + " y");
                    }
                }
            }
        }
    }

    /** On every face the four turns look four different ways, none of them into or out of the face. */
    @Test
    void theFrontGoesRoundWithTheTurnOnEveryFace(MinecraftServer server) {
        for (Direction facing : Direction.values()) {
            var seen = new java.util.HashSet<Direction>();
            for (int spin : WirelessRedstoneBlock.SPIN.getPossibleValues()) {
                Direction front = WirelessRedstoneBlock.front(facing, spin);
                assertNotEquals(facing.getAxis(), front.getAxis(), facing + " spin " + spin + " points " + front);
                assertTrue(seen.add(front), "two turns face " + front);
            }
            assertEquals(4, seen.size(), facing + " should reach every side");
            // A wall plate placed before it could turn keeps looking up, so old worlds do not change.
            if (facing.getAxis().isHorizontal()) assertEquals(Direction.UP, WirelessRedstoneBlock.front(facing, 0));
        }
    }

    @Test
    void everySpriteTheModelsAskForIsThere() throws Exception {
        for (String plate : PLATES) {
            String kind = plate.replace("wireless_", "");
            for (String path : new String[]{"block/wireless/" + kind, "block/wireless/" + kind + "_on",
                    "block/wireless/" + kind + "_turned", "block/wireless/" + kind + "_turned_on", "item/" + plate}) {
                JsonObject model = model(path);
                if (!model.has("textures")) continue;
                for (var entry : model.getAsJsonObject("textures").entrySet()) {
                    String texture = entry.getValue().getAsString();
                    // The mast is vanilla obsidian, which is the game's to ship; the rest is ours.
                    if (texture.startsWith("minecraft:block/")) continue;
                    assertTrue(texture.startsWith("futuretech:block/"), path + " mixes atlases: " + texture);
                    String file = "textures/" + texture.replace("futuretech:", "") + ".png";
                    try (var stream = WirelessRedstoneResourcesTest.class.getResourceAsStream(ASSETS + file)) {
                        assertNotNull(stream, path + " asks for " + file);
                    }
                }
            }
        }
        // What the renderer draws is not in any model, so nothing else would miss it.
        for (String file : new String[]{"textures/entity/wireless_dish.png", "textures/entity/wireless_hedron.png",
                "textures/entity/wireless_digits.png"}) {
            try (var stream = WirelessRedstoneResourcesTest.class.getResourceAsStream(ASSETS + file)) {
                assertNotNull(stream, "the renderer asks for " + file);
            }
        }
    }

    /**
     * The frequency is written on the face of the plate, four digits either side of the lamp, read
     * off a strip of ten. A number that lost a digit on the way, or a strip too short for the cell
     * the renderer reads, would be a plate quietly showing the wrong frequency.
     */
    @Test
    void theFrequencyIsWrittenAsFourDigitsFromAStripOfTen() throws Exception {
        assertArrayEquals(new int[]{1, 2, 3, 4}, WirelessHeadMesh.digitsOf(1234));
        assertArrayEquals(new int[]{0, 0, 4, 2}, WirelessHeadMesh.digitsOf(42), "short numbers keep their noughts");
        assertArrayEquals(new int[]{0, 0, 0, 0}, WirelessHeadMesh.digitsOf(0));
        assertArrayEquals(new int[]{9, 9, 9, 9}, WirelessHeadMesh.digitsOf(9999));
        assertArrayEquals(new int[]{9, 9, 9, 9}, WirelessHeadMesh.digitsOf(123456), "past the last frequency there is");
        assertArrayEquals(new int[]{0, 0, 0, 0}, WirelessHeadMesh.digitsOf(-5));
        // Two faces a digit, so neither side of the plate's face can cull them away.
        assertEquals(8, WirelessHeadMesh.digits(1234).size());

        try (var stream = WirelessRedstoneResourcesTest.class.getResourceAsStream(ASSETS + "textures/entity/wireless_digits.png")) {
            assertNotNull(stream, "the digits");
            var strip = javax.imageio.ImageIO.read(stream);
            assertEquals(4, strip.getWidth(), "three columns of digit and one of air");
            assertTrue(strip.getHeight() >= 60, "ten cells of six rows");
            for (int digit = 0; digit < 10; digit++) {
                boolean drawn = false;
                for (int row = digit * 6; row < digit * 6 + 5 && !drawn; row++) {
                    for (int column = 0; column < 3; column++) {
                        if ((strip.getRGB(column, row) >>> 24) != 0) drawn = true;
                    }
                }
                assertTrue(drawn, "the strip has no " + digit);
            }
        }
    }

    @Test
    void whatIsDrawnStaysInsideWhatCanBeClicked(MinecraftServer server) throws Exception {
        for (var block : new WirelessRedstoneBlock[]{ModBlocks.WIRELESS_TRANSMITTER.get(), ModBlocks.WIRELESS_RECEIVER.get()}) {
            // The models are drawn lying on the floor, so that is the state to measure against.
            var state = block.defaultBlockState().setValue(WirelessRedstoneBlock.FACING, Direction.UP);
            VoxelShape shape = state.getShape(server.overworld(), BlockPos.ZERO);
            String path = "block/wireless/" + (block.kind == WirelessRedstoneBlock.Kind.TRANSMITTER ? "transmitter" : "receiver");
            var elements = model(path).getAsJsonArray("elements");
            for (int i = 0; i < elements.size(); i++) {
                double[] box = bounds(elements.get(i).getAsJsonObject());
                VoxelShape drawn = Block.box(box[0], box[1], box[2], box[3], box[4], box[5]);
                assertFalse(Shapes.joinIsNotEmpty(shape, drawn, BooleanOp.ONLY_SECOND),
                        path + " element " + i + " sticks out of the block's shape");
            }
        }
    }

    /**
     * The box an element takes up once it has been leaned. A model's rotation is one axis and one
     * angle, and the only one here is the receiver's arm leaning out over the plate; anything else
     * is new and wants looking at rather than waving through.
     */
    private static double[] bounds(JsonObject element) {
        var from = element.getAsJsonArray("from");
        var to = element.getAsJsonArray("to");
        double[] box = {from.get(0).getAsDouble(), from.get(1).getAsDouble(), from.get(2).getAsDouble(),
                to.get(0).getAsDouble(), to.get(1).getAsDouble(), to.get(2).getAsDouble()};
        if (!element.has("rotation")) return box;
        var rotation = element.getAsJsonObject("rotation");
        assertEquals("x", rotation.get("axis").getAsString(), "only the arm leans, and it leans on x");
        double angle = Math.toRadians(rotation.get("angle").getAsDouble());
        var origin = rotation.getAsJsonArray("origin");
        double oy = origin.get(1).getAsDouble();
        double oz = origin.get(2).getAsDouble();
        double lowY = Double.MAX_VALUE;
        double highY = -Double.MAX_VALUE;
        double lowZ = Double.MAX_VALUE;
        double highZ = -Double.MAX_VALUE;
        for (double y : new double[]{box[1], box[4]}) {
            for (double z : new double[]{box[2], box[5]}) {
                // A model's rotation goes anticlockwise about its axis — the other way round from
                // the turns a blockstate gives the whole model, which is the trap this fell into.
                double dy = y - oy;
                double dz = z - oz;
                double turnedY = oy + dy * Math.cos(angle) - dz * Math.sin(angle);
                double turnedZ = oz + dy * Math.sin(angle) + dz * Math.cos(angle);
                lowY = Math.min(lowY, turnedY);
                highY = Math.max(highY, turnedY);
                lowZ = Math.min(lowZ, turnedZ);
                highZ = Math.max(highZ, turnedZ);
            }
        }
        return new double[]{box[0], lowY, lowZ, box[3], highY, highZ};
    }
}
