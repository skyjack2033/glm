package com.github.skyjack2033.wirelessmehatch.metatileentity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

import javax.imageio.ImageIO;

import org.junit.Test;

public class WirelessAssemblyTextureAssetTest {

    private static final String[] TEXTURES = {
        "/assets/wirelessmehatch/textures/blocks/iconsets/OVERLAY_WIRELESS_UNIFIED_OUTPUT_ASSEMBLY.png",
        "/assets/wirelessmehatch/textures/blocks/iconsets/OVERLAY_WIRELESS_UNIFIED_OUTPUT_ASSEMBLY_ACTIVE.png" };

    @Test
    public void statusOverlaysAreHardEdgedSixteenPixelIcons() throws IOException {
        for (String texture : TEXTURES) {
            try (InputStream stream = getClass().getResourceAsStream(texture)) {
                assertNotNull("Missing texture " + texture, stream);
                BufferedImage image = ImageIO.read(stream);
                assertNotNull("Unreadable texture " + texture, image);
                assertEquals("Unexpected width for " + texture, 16, image.getWidth());
                assertEquals("Unexpected height for " + texture, 16, image.getHeight());

                for (int y = 0; y < image.getHeight(); y++) {
                    for (int x = 0; x < image.getWidth(); x++) {
                        int alpha = image.getRGB(x, y) >>> 24;
                        assertTrue("Partial alpha in " + texture, alpha == 0 || alpha == 255);
                        if (x < 2 || x >= 14 || y < 2 || y >= 14) {
                            assertEquals("Opaque border pixel in " + texture, 0, alpha);
                        }
                    }
                }
            }
        }
    }
}
