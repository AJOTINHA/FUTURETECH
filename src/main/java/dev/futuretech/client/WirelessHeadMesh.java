package dev.futuretech.client;

import java.util.ArrayList;
import java.util.List;

/**
 * The two pieces of a wireless plate that are not boxes: the receiver's dish and the hedron that
 * floats over both plates. Everything here is in block space, standing the way the models are
 * drawn — the plate lying on the floor — and the renderer turns it to whichever face the plate
 * was mounted on.
 *
 * <p>The dish is WR-CBE's own shape, read off its {@code logic.obj}: a ring of eight points around
 * a point in the middle, drawn twice a pixel apart so the bowl has a back, and a band joining the
 * two rings at the rim. Its numbers are kept as they are given rather than worked out from a
 * radius and a tilt, because the tilt is what makes it look like a dish pointed at the sky.
 */
public final class WirelessHeadMesh {
    /** A corner of a face: where it is, and where it reads the texture. */
    public record Vertex(float x, float y, float z, float u, float v) {}

    /** A face, as four corners; a triangle repeats its last corner, the way the battery's shell does. */
    public record Face(Vertex a, Vertex b, Vertex c, Vertex d) {}

    /** The middle of the front of the dish, which is where its cone meets. */
    private static final float[] DISH_FRONT = {0.5F, 0.544F, 0.325F};
    /** The same for the back of it, a pixel behind and below: the dish has a thickness. */
    private static final float[] DISH_BACK = {0.5F, 0.494F, 0.274F};
    /** The eight points of the front rim, going round. */
    private static final float[][] FRONT_RIM = {
            {0.5F, 0.825F, 0.184F}, {0.712F, 0.763F, 0.246F}, {0.8F, 0.613F, 0.396F}, {0.712F, 0.463F, 0.546F},
            {0.5F, 0.401F, 0.609F}, {0.288F, 0.463F, 0.546F}, {0.2F, 0.613F, 0.396F}, {0.288F, 0.763F, 0.246F}};
    /** The back rim, the same eight points carried behind the dish. */
    private static final float[][] BACK_RIM = {
            {0.5F, 0.775F, 0.135F}, {0.712F, 0.713F, 0.2F}, {0.8F, 0.563F, 0.347F}, {0.712F, 0.413F, 0.497F},
            {0.5F, 0.351F, 0.559F}, {0.288F, 0.413F, 0.497F}, {0.2F, 0.563F, 0.347F}, {0.288F, 0.713F, 0.2F}};

    /** Where the hedron floats: over the transmitter's mast, and at the receiver's dish. */
    public static final float[] TRANSMITTER_HEDRON = {0.5F, 0.835F, 0.3125F};
    // Its lower point rests on the end of the arm, where the arm stops, so the two touch
    // without the arm running on through the middle of it.
    public static final float[] RECEIVER_HEDRON = {0.5F, 0.943F, 0.539F};
    /**
     * How far over the hedron leans. The transmitter's stands up over its mast; the receiver's is
     * held by the arm, so it lies along the arm and carries on where the arm's tip stops — the
     * same 22.5 degrees the model leans that arm, not the steeper line of the dish.
     */
    public static final float TRANSMITTER_HEDRON_LEAN = 0;
    public static final float RECEIVER_HEDRON_LEAN = 22.5F;
    /** The hedron is a little taller than it is wide, which is what keeps it reading as a crystal. */
    private static final float HEDRON_HEIGHT = 0.21F;
    private static final float HEDRON_WIDTH = 0.16F;

    /** Where the frequency is written: two digits before the lamp and two after it, in blocks. */
    private static final float DIGIT_LIFT = 2.02F / 16;
    private static final float DIGIT_TOP = 9.5F / 16;
    private static final float DIGIT_BOTTOM = 12.5F / 16;
    private static final float DIGIT_WIDTH = 2F / 16;
    private static final float[] DIGIT_STARTS = {2F / 16, 4.5F / 16, 9.5F / 16, 12F / 16};
    /** The strip they are read from: ten cells of six rows, of which five are the digit itself. */
    private static final float DIGIT_CELL = 6F / 64;
    private static final float DIGIT_ROWS = 5F / 64;
    private static final float DIGIT_COLUMNS = 3F / 4;
    private static final int PLACES = 4;
    private static final int TEN = 10;

    public static final List<Face> DISH = dish();
    /** Every digit in every place, drawn once: a frequency only has to pick its four out. */
    private static final List<List<Face>> DIGIT_FACES = digitFaces();
    /** Drawn about its own middle: the renderer puts it where it floats and spins it there. */
    public static final List<Face> HEDRON = hedron();

    private WirelessHeadMesh() {}

    /**
     * Both cones and the rim between them. Every face is added the other way round as well: the
     * bowl is open, so the inside of it is seen as often as the outside and a culled face would
     * leave a hole in the dish.
     */
    private static List<Face> dish() {
        List<Face> faces = new ArrayList<>();
        Vertex front = at(DISH_FRONT, 0.5F, 0.5F);
        Vertex back = at(DISH_BACK, 0.5F, 0.5F);
        for (int corner = 0; corner < FRONT_RIM.length; corner++) {
            int next = (corner + 1) % FRONT_RIM.length;
            // The stone is read as a circle around the middle of the texture, so the grain runs
            // out from the centre of the dish rather than sideways across it.
            Vertex frontA = rim(FRONT_RIM[corner], corner);
            Vertex frontB = rim(FRONT_RIM[next], next);
            Vertex backA = rim(BACK_RIM[corner], corner);
            Vertex backB = rim(BACK_RIM[next], next);
            both(faces, new Face(front, frontA, frontB, frontB));
            both(faces, new Face(back, backB, backA, backA));
            both(faces, new Face(frontA, backA, backB, frontB));
        }
        return List.copyOf(faces);
    }

    /** An octahedron: a point at the top, a point at the bottom, and four corners round the middle. */
    private static List<Face> hedron() {
        List<Face> faces = new ArrayList<>();
        Vertex[] middle = new Vertex[4];
        for (int corner = 0; corner < 4; corner++) {
            double angle = corner * Math.PI / 2;
            middle[corner] = new Vertex((float) (Math.cos(angle) * HEDRON_WIDTH), 0, (float) (Math.sin(angle) * HEDRON_WIDTH),
                    0, 0.5F);
        }
        for (int corner = 0; corner < 4; corner++) {
            Vertex a = middle[corner];
            Vertex b = middle[(corner + 1) % 4];
            // The map is a gradient down its height: the top of the hedron reads the top of it,
            // which is the red, and the bottom reads the yellow.
            Vertex top = new Vertex(0, HEDRON_HEIGHT, 0, 0.5F, 0);
            Vertex bottom = new Vertex(0, -HEDRON_HEIGHT, 0, 0.5F, 1);
            both(faces, new Face(top, uv(a, 0, 0.5F), uv(b, 1, 0.5F), uv(b, 1, 0.5F)));
            both(faces, new Face(bottom, uv(b, 1, 0.5F), uv(a, 0, 0.5F), uv(a, 0, 0.5F)));
        }
        return List.copyOf(faces);
    }

    /** The four digits a frequency is written as, highest first; a short one is padded with noughts. */
    public static int[] digitsOf(int frequency) {
        int value = Math.clamp(frequency, 0, 9999);
        return new int[]{value / 1000, value / 100 % TEN, value / TEN % TEN, value % TEN};
    }

    /**
     * The frequency as it is written on the face of the plate: a flat little panel per digit,
     * lying just over the stone. It is drawn to be read from the front of the plate — the side its
     * redstone is on — so the digits run west to east with their tops towards the back, the way a
     * name written on the floor reads to whoever is standing at the foot of it.
     */
    public static List<Face> digits(int frequency) {
        int[] digits = digitsOf(frequency);
        List<Face> faces = new ArrayList<>(PLACES * 2);
        for (int place = 0; place < PLACES; place++) faces.addAll(DIGIT_FACES.get(place * TEN + digits[place]));
        return faces;
    }

    private static List<List<Face>> digitFaces() {
        List<List<Face>> places = new ArrayList<>(PLACES * TEN);
        for (int place = 0; place < PLACES; place++) {
            for (int digit = 0; digit < TEN; digit++) {
                float x0 = DIGIT_STARTS[place];
                float x1 = x0 + DIGIT_WIDTH;
                float v0 = digit * DIGIT_CELL;
                List<Face> faces = new ArrayList<>(2);
                both(faces, new Face(
                        new Vertex(x0, DIGIT_LIFT, DIGIT_TOP, 0, v0),
                        new Vertex(x1, DIGIT_LIFT, DIGIT_TOP, DIGIT_COLUMNS, v0),
                        new Vertex(x1, DIGIT_LIFT, DIGIT_BOTTOM, DIGIT_COLUMNS, v0 + DIGIT_ROWS),
                        new Vertex(x0, DIGIT_LIFT, DIGIT_BOTTOM, 0, v0 + DIGIT_ROWS)));
                places.add(List.copyOf(faces));
            }
        }
        return List.copyOf(places);
    }

    /** The face as given and the same face reversed, so neither side of it can be culled away. */
    private static void both(List<Face> faces, Face face) {
        faces.add(face);
        faces.add(new Face(face.d(), face.c(), face.b(), face.a()));
    }

    private static Vertex at(float[] point, float u, float v) {
        return new Vertex(point[0], point[1], point[2], u, v);
    }

    /** A rim point, reading the texture from the edge of the circle it sits on. */
    private static Vertex rim(float[] point, int corner) {
        double angle = corner * Math.PI / 4;
        return at(point, (float) (0.5 + Math.cos(angle) * 0.45), (float) (0.5 + Math.sin(angle) * 0.45));
    }

    private static Vertex uv(Vertex vertex, float u, float v) {
        return new Vertex(vertex.x(), vertex.y(), vertex.z(), u, v);
    }
}
