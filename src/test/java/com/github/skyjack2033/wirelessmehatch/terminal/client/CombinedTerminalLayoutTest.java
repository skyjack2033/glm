package com.github.skyjack2033.wirelessmehatch.terminal.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CombinedTerminalLayoutTest {

    @Test
    public void switchesToCompactLayoutBelowNormalMinimumHeight() {
        assertFalse(CombinedTerminalLayout.isCompact(328));
        assertTrue(CombinedTerminalLayout.isCompact(327));
        assertEquals(3, CombinedTerminalLayout.interfaceRows(328));
        assertEquals(1, CombinedTerminalLayout.interfaceRows(327));
    }

    @Test
    public void compactLayoutFitsOneMeAndOneInterfaceRowIn256Pixels() {
        assertEquals(78, CombinedTerminalLayout.interfaceReservedSpace(3));
        assertEquals(42, CombinedTerminalLayout.interfaceReservedSpace(1));
        assertEquals(328, CombinedTerminalLayout.totalHeight(3, 3));
        assertEquals(256, CombinedTerminalLayout.totalHeight(1, 1));
    }

    @Test
    public void centersTheFull338PixelWidthIncludingOnNarrowScreens() {
        assertEquals(71, CombinedTerminalLayout.guiLeft(480));
        assertEquals(0, CombinedTerminalLayout.guiLeft(338));
        assertEquals(-9, CombinedTerminalLayout.guiLeft(320));
    }

    @Test
    public void scrollsTheInterfaceListOnlyWhenNeededToRevealTheSelection() {
        assertEquals(0, CombinedTerminalLayout.scrollToReveal(7, 0, 3, 10));
        assertEquals(5, CombinedTerminalLayout.scrollToReveal(0, 7, 3, 10));
        assertEquals(4, CombinedTerminalLayout.scrollToReveal(4, 5, 3, 10));
        assertEquals(7, CombinedTerminalLayout.scrollToReveal(99, 8, 3, 10));
        assertEquals(3, CombinedTerminalLayout.scrollToReveal(2, 3, 1, 5));
        assertEquals(0, CombinedTerminalLayout.scrollToReveal(8, -1, 1, 0));
    }
}
