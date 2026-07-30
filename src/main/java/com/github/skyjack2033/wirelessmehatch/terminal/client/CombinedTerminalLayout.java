package com.github.skyjack2033.wirelessmehatch.terminal.client;

final class CombinedTerminalLayout {

    static final int WIDTH = 338;
    static final int LEFT_PANEL_WIDTH = 195;
    static final int SLOT_SIZE = 18;

    static final int CACHE_X = 204;
    static final int CACHE_Y = 19;
    static final int CACHE_COLUMNS = 6;
    static final int CACHE_SCROLLBAR_X = 321;

    static final int INTERFACE_LIST_X = 8;
    static final int INTERFACE_LIST_WIDTH = 116;
    static final int INTERFACE_LIST_SCROLLBAR_X = 127;
    static final int INTERFACE_PATTERN_X = 145;
    static final int INTERFACE_PATTERN_COLUMNS = InterfacePatternGrid.COLUMNS;
    static final int INTERFACE_PATTERN_SCROLLBAR_X = 312;
    static final int INTERFACE_SEARCH_HEIGHT = 12;

    static final int RIGHT_CONTENT_X = 204;
    static final int MANUAL_GRID_X = 213;
    static final int MANUAL_OUTPUT_X = 307;
    static final int MANUAL_ARROW_X = 291;

    static final int BASE_PATTERN_RESERVED_SPACE = 81;
    static final int NORMAL_INTERFACE_ROWS = 3;
    static final int COMPACT_INTERFACE_ROWS = 1;

    private static final int BASE_TERMINAL_HEIGHT = 115;
    private static final int INTERFACE_FIXED_SPACE = 24;
    private static final int TOP_HEADER_HEIGHT = 18;
    private static final int INTERFACE_SEARCH_OFFSET = 3;
    private static final int INTERFACE_HEADER_HEIGHT = 18;
    private static final int BOTTOM_SECTION_HEIGHT = 180;

    private CombinedTerminalLayout() {}

    static boolean isCompact(int screenHeight) {
        return screenHeight < totalHeight(3, NORMAL_INTERFACE_ROWS);
    }

    static int interfaceRows(int screenHeight) {
        return isCompact(screenHeight) ? COMPACT_INTERFACE_ROWS : NORMAL_INTERFACE_ROWS;
    }

    static int interfaceReservedSpace(int rows) {
        return INTERFACE_FIXED_SPACE + Math.max(0, rows) * SLOT_SIZE;
    }

    static int reservedSpace(int interfaceRows) {
        return BASE_PATTERN_RESERVED_SPACE + interfaceReservedSpace(interfaceRows);
    }

    static int totalHeight(int meRows, int interfaceRows) {
        return BASE_TERMINAL_HEIGHT + Math.max(0, meRows) * SLOT_SIZE + reservedSpace(interfaceRows);
    }

    static int interfaceTop(int meRows) {
        return TOP_HEADER_HEIGHT + Math.max(0, meRows) * SLOT_SIZE;
    }

    static int interfaceSearchTop(int meRows) {
        return interfaceTop(meRows) + INTERFACE_SEARCH_OFFSET;
    }

    static int interfaceViewportTop(int meRows) {
        return interfaceTop(meRows) + INTERFACE_HEADER_HEIGHT;
    }

    static int bottomSectionTop(int totalHeight) {
        return totalHeight - BOTTOM_SECTION_HEIGHT;
    }

    static int interfacePanelHeight(int totalHeight, int meRows) {
        return Math.max(0, bottomSectionTop(totalHeight) - interfaceTop(meRows));
    }

    static int bottomTitleY(int totalHeight) {
        return bottomSectionTop(totalHeight) + 4;
    }

    static int manualGridTop(int totalHeight) {
        return bottomSectionTop(totalHeight) + 23;
    }

    static int manualOutputTop(int totalHeight) {
        return bottomSectionTop(totalHeight) + 41;
    }

    static int batchTitleY(int totalHeight) {
        return bottomSectionTop(totalHeight) + 86;
    }

    static int batchButtonTop(int totalHeight, int row) {
        return bottomSectionTop(totalHeight) + 98 + row * 21;
    }

    static int batchToggleTop(int totalHeight) {
        return bottomSectionTop(totalHeight) + 140;
    }

    static int guiLeft(int screenWidth) {
        return (screenWidth - WIDTH) / 2;
    }

    static int scrollToReveal(int currentScroll, int selectedIndex, int visibleRows, int itemCount) {
        int safeRows = Math.max(1, visibleRows);
        int maxScroll = Math.max(0, itemCount - safeRows);
        int scroll = Math.max(0, Math.min(currentScroll, maxScroll));
        if (selectedIndex < 0 || selectedIndex >= itemCount) return scroll;
        if (selectedIndex < scroll) return selectedIndex;
        if (selectedIndex >= scroll + safeRows) return selectedIndex - safeRows + 1;
        return scroll;
    }
}
