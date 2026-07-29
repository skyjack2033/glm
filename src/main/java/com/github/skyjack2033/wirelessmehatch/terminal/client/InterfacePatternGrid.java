package com.github.skyjack2033.wirelessmehatch.terminal.client;

final class InterfacePatternGrid {

    static final int COLUMNS = 9;

    private InterfacePatternGrid() {}

    static int totalRows(int inventorySize) {
        int safeSize = Math.max(0, inventorySize);
        return (int) (((long) safeSize + COLUMNS - 1) / COLUMNS);
    }

    static int slotAt(int inventorySize, int firstRow, int visibleRow, int column) {
        if (inventorySize <= 0 || firstRow < 0 || visibleRow < 0 || column < 0 || column >= COLUMNS) return -1;

        long absoluteRow = (long) firstRow + visibleRow;
        long slot = absoluteRow * COLUMNS + column;
        return slot < inventorySize ? (int) slot : -1;
    }
}
