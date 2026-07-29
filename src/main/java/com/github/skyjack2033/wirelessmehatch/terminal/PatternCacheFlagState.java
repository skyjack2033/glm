package com.github.skyjack2033.wirelessmehatch.terminal;

public enum PatternCacheFlagState {

    EMPTY,
    OFF,
    ON,
    MIXED;

    public boolean nextValue() {
        return this != ON;
    }
}
