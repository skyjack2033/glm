package com.github.skyjack2033.wirelessmehatch.terminal.client;

final class CombinedTerminalLayout {

    static final int WIDTH = 338;
    static final int BASE_PATTERN_RESERVED_SPACE = 81;
    static final int NORMAL_INTERFACE_ROWS = 3;
    static final int COMPACT_INTERFACE_ROWS = 1;

    private static final int BASE_TERMINAL_HEIGHT = 115;
    private static final int INTERFACE_FIXED_SPACE = 24;
    private static final int SLOT_SIZE = 18;

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
