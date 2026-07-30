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
    public void keepsCustomColumnsInsideTheGuiWidth() {
        assertEquals(195, CombinedTerminalLayout.LEFT_PANEL_WIDTH);
        assertEquals(204, CombinedTerminalLayout.CACHE_X);
        assertEquals(312, CombinedTerminalLayout.CACHE_X + CombinedTerminalLayout.CACHE_COLUMNS * 18);
        assertTrue(CombinedTerminalLayout.CACHE_SCROLLBAR_X >= 312);
        assertTrue(CombinedTerminalLayout.CACHE_SCROLLBAR_X < CombinedTerminalLayout.WIDTH);

        assertEquals(124, CombinedTerminalLayout.INTERFACE_LIST_X + CombinedTerminalLayout.INTERFACE_LIST_WIDTH);
        assertTrue(CombinedTerminalLayout.INTERFACE_LIST_SCROLLBAR_X >= 124);
        assertTrue(CombinedTerminalLayout.INTERFACE_LIST_SCROLLBAR_X < CombinedTerminalLayout.INTERFACE_PATTERN_X);
        assertEquals(
            307,
            CombinedTerminalLayout.INTERFACE_PATTERN_X + CombinedTerminalLayout.INTERFACE_PATTERN_COLUMNS * 18);
        assertTrue(CombinedTerminalLayout.INTERFACE_PATTERN_SCROLLBAR_X >= 307);
        assertTrue(CombinedTerminalLayout.INTERFACE_PATTERN_SCROLLBAR_X < CombinedTerminalLayout.WIDTH);
    }

    @Test
    public void reservesAFramedSearchHeaderAboveInterfaceRows() {
        int interfaceTop = CombinedTerminalLayout.interfaceTop(3);
        int searchTop = CombinedTerminalLayout.interfaceSearchTop(3);
        int searchFrameBottom = searchTop + CombinedTerminalLayout.INTERFACE_SEARCH_HEIGHT + 1;

        assertEquals(72, interfaceTop);
        assertEquals(75, searchTop);
        assertTrue(searchFrameBottom < CombinedTerminalLayout.interfaceViewportTop(3));
        assertEquals(90, CombinedTerminalLayout.interfaceViewportTop(3));
    }

    @Test
    public void alignsBottomSectionsWithoutCoveringTheInterfacePanel() {
        int totalHeight = CombinedTerminalLayout.totalHeight(3, 3);
        int bottomTop = CombinedTerminalLayout.bottomSectionTop(totalHeight);

        assertEquals(148, bottomTop);
        assertEquals(76, CombinedTerminalLayout.interfacePanelHeight(totalHeight, 3));
        assertEquals(152, CombinedTerminalLayout.bottomTitleY(totalHeight));
        assertEquals(171, CombinedTerminalLayout.manualGridTop(totalHeight));
        assertEquals(189, CombinedTerminalLayout.manualOutputTop(totalHeight));
        assertEquals(234, CombinedTerminalLayout.batchTitleY(totalHeight));
        assertEquals(246, CombinedTerminalLayout.batchButtonTop(totalHeight, 0));
        assertEquals(267, CombinedTerminalLayout.batchButtonTop(totalHeight, 1));
        assertEquals(288, CombinedTerminalLayout.batchToggleTop(totalHeight));
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
