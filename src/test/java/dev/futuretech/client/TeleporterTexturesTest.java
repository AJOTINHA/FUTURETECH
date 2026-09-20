package dev.futuretech.client;

import dev.futuretech.api.upgrade.MachineLevel;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.net.URL;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The pad's top wears its level's corners, the same coloured L the machine casing wears. It is
 * drawn onto that casing rather than painted by hand, so the guard here is that the only thing
 * telling one level's top from another is exactly what tells their casings apart: the ring in the
 * middle must come out identical at every level, and no corner may drift off the machines' colour.
 */
class TeleporterTexturesTest {
    private static final String TEXTURES = "/assets/futuretech/textures/block/";

    private static URL resource(String path) {
        return TeleporterTexturesTest.class.getResource(TEXTURES + path);
    }

    private static BufferedImage image(String path) throws Exception {
        URL url = resource(path);
        assertNotNull(url, path);
        return javax.imageio.ImageIO.read(url);
    }

    private static String top(int mk) {
        return mk == 1 ? "teleporter/teleporter_top" : "teleporter/mk" + mk + "/teleporter_top";
    }

    private static String casing(int mk) {
        return mk == 1 ? "machine/machine_side" : "machine/mk" + mk + "/machine_side";
    }

    @Test
    void everyLevelsTopIsTheLevelsCasingWithTheSameRingOnIt() throws Exception {
        var baseTop = image(top(1) + ".png");
        var baseCasing = image(casing(1) + ".png");
        assertEquals(32, baseTop.getWidth());
        assertEquals(32, baseTop.getHeight());
        for (int mk = 2; mk <= MachineLevel.MAX; mk++) {
            var tierTop = image(top(mk) + ".png");
            var tierCasing = image(casing(mk) + ".png");
            assertEquals(baseTop.getWidth(), tierTop.getWidth(), "MK" + mk);
            assertEquals(baseTop.getHeight(), tierTop.getHeight(), "MK" + mk);
            int changed = 0;
            for (int y = 0; y < 32; y++) {
                for (int x = 0; x < 32; x++) {
                    boolean casingDiffers = baseCasing.getRGB(x, y) != tierCasing.getRGB(x, y);
                    boolean topDiffers = baseTop.getRGB(x, y) != tierTop.getRGB(x, y);
                    // Away from the corners the two tops are the same pixel: that is the ring,
                    // and a level must never redraw it.
                    assertEquals(casingDiffers, topDiffers,
                            "MK" + mk + " differs from MK1 exactly where its casing does, at " + x + "," + y);
                    if (!topDiffers) continue;
                    assertEquals(tierCasing.getRGB(x, y), tierTop.getRGB(x, y),
                            "MK" + mk + " takes the corner colour from its casing, at " + x + "," + y);
                    changed++;
                }
            }
            assertTrue(changed > 0, "MK" + mk + " has corners of its own");
        }
    }

    /** The lit top is eight frames at every level, and every one of them ships its animation. */
    @Test
    void everyLevelsLitTopIsAnimatedTheSameWay() throws Exception {
        for (int mk = 1; mk <= MachineLevel.MAX; mk++) {
            var lit = image(top(mk) + "_on.png");
            assertEquals(32, lit.getWidth(), "MK" + mk);
            assertEquals(32 * 8, lit.getHeight(), "MK" + mk + " is eight frames tall");
            assertNotNull(resource(top(mk) + "_on.png.mcmeta"), "MK" + mk + " ships its animation");
        }
    }
}
