package com.github.skyjack2033.wirelessmehatch.terminal.client;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class InterfacePatternGridTest {

    @Test
    public void calculatesRowsForFixedNineColumnGrid() {
        assertEquals(0, InterfacePatternGrid.totalRows(0));
        assertEquals(1, InterfacePatternGrid.totalRows(1));
        assertEquals(1, InterfacePatternGrid.totalRows(9));
        assertEquals(2, InterfacePatternGrid.totalRows(10));
        assertEquals(2, InterfacePatternGrid.totalRows(18));
        assertEquals(3, InterfacePatternGrid.totalRows(19));
        assertEquals(456, InterfacePatternGrid.totalRows(4096));
    }

    @Test
    public void mapsScrolledRowsToContinuousInventorySlots() {
        assertEquals(9, InterfacePatternGrid.slotAt(20, 1, 0, 0));
        assertEquals(17, InterfacePatternGrid.slotAt(20, 1, 0, 8));
        assertEquals(18, InterfacePatternGrid.slotAt(20, 1, 1, 0));
        assertEquals(19, InterfacePatternGrid.slotAt(20, 1, 1, 1));
        assertEquals(-1, InterfacePatternGrid.slotAt(20, 1, 1, 2));
    }

    @Test
    public void rejectsCoordinatesOutsideTheGridOrInventory() {
        assertEquals(-1, InterfacePatternGrid.slotAt(20, -1, 0, 0));
        assertEquals(-1, InterfacePatternGrid.slotAt(20, 0, -1, 0));
        assertEquals(-1, InterfacePatternGrid.slotAt(20, 0, 0, -1));
        assertEquals(-1, InterfacePatternGrid.slotAt(20, 0, 0, 9));
        assertEquals(-1, InterfacePatternGrid.slotAt(0, 0, 0, 0));
    }
}
