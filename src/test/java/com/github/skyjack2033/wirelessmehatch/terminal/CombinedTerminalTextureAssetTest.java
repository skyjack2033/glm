package com.github.skyjack2033.wirelessmehatch.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

import javax.imageio.ImageIO;

import org.junit.Test;

public class CombinedTerminalTextureAssetTest {

    private static final String TEXTURE = "/assets/wirelessmehatch/textures/items/combined_terminal.png";

    @Test
    public void itemTextureIsAReadableHardEdgedSixteenPixelIcon() throws IOException {
        try (InputStream stream = getClass().getResourceAsStream(TEXTURE)) {
            assertNotNull("Missing texture " + TEXTURE, stream);
            BufferedImage image = ImageIO.read(stream);
            assertNotNull("Unreadable texture " + TEXTURE, image);
            assertEquals(16, image.getWidth());
            assertEquals(16, image.getHeight());

            int opaquePixels = 0;
            Set<Integer> opaqueColors = new HashSet<>();
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    int pixel = image.getRGB(x, y);
                    int alpha = pixel >>> 24;
                    assertTrue("Partial alpha at (" + x + ", " + y + ")", alpha == 0 || alpha == 255);
                    if (x < 2 || x >= 14 || y < 2 || y >= 14) {
                        assertEquals("Opaque border pixel at (" + x + ", " + y + ")", 0, alpha);
                    }
                    if (alpha == 255) {
                        opaquePixels++;
                        opaqueColors.add(pixel);
                    }
                }
            }

            assertTrue("Texture is visually empty", opaquePixels >= 48);
            assertTrue("Texture does not contain enough visual detail", opaqueColors.size() >= 6);
        }
    }
}
