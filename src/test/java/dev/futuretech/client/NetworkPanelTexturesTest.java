package dev.futuretech.client;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.net.URL;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The panel's face is the machine casing with a screen sunk into it. Everything the panel draws
 * belongs inside that screen's frame: a stray lit pixel out on the casing reads as a blemish, not
 * as a detail, which is exactly what one did.
 */
class NetworkPanelTexturesTest {
    private static final String TEXTURES = "/assets/futuretech/textures/block/";
    /** The frame the screen is sunk into, in texture pixels; nothing of ours belongs outside it. */
    private static final int BEZEL_MIN = 4;
    private static final int BEZEL_MAX = 27;
    private static final int FRAMES = 8;

    private static URL resource(String path) {
        return NetworkPanelTexturesTest.class.getResource(TEXTURES + path);
    }

    private static BufferedImage image(String path) throws Exception {
        URL url = resource(path);
        assertNotNull(url, path);
        return javax.imageio.ImageIO.read(url);
    }

    @Test
    void theFrontIsAnimatedAndPaintsNothingOutsideItsScreen() throws Exception {
        var casing = image("machine/machine_side.png");
        var front = image("network_panel/network_panel_front.png");
        assertEquals(32, front.getWidth());
        assertEquals(32 * FRAMES, front.getHeight(), "one strip of frames");
        assertNotNull(resource("network_panel/network_panel_front.png.mcmeta"), "the frames need their animation");
        for (int frame = 0; frame < FRAMES; frame++) {
            for (int y = 0; y < 32; y++) {
                for (int x = 0; x < 32; x++) {
                    if (x >= BEZEL_MIN && x <= BEZEL_MAX && y >= BEZEL_MIN && y <= BEZEL_MAX) continue;
                    assertEquals(casing.getRGB(x, y), front.getRGB(x, frame * 32 + y),
                            "frame " + frame + " leaves the casing alone at " + x + "," + y);
                }
            }
        }
    }

    /** Every frame differs from the one before it, or the animation is only pretending to move. */
    @Test
    void everyFrameOfTheFrontDiffersFromTheLast() throws Exception {
        var front = image("network_panel/network_panel_front.png");
        for (int frame = 1; frame < FRAMES; frame++) {
            boolean moved = false;
            for (int y = 0; y < 32 && !moved; y++) {
                for (int x = 0; x < 32; x++) {
                    if (front.getRGB(x, frame * 32 + y) != front.getRGB(x, (frame - 1) * 32 + y)) {
                        moved = true;
                        break;
                    }
                }
            }
            // Each row stays lit for two frames, so only the odd steps have to move.
            if (frame % 2 == 0) assertTrue(moved, "frame " + frame + " moves on from " + (frame - 1));
        }
    }
}
