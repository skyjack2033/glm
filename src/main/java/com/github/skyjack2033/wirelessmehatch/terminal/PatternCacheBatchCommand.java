package com.github.skyjack2033.wirelessmehatch.terminal;

public enum PatternCacheBatchCommand {

    MULTIPLY_2(2, true),
    MULTIPLY_3(3, true),
    MULTIPLY_5(5, true),
    DIVIDE_2(2, false),
    DIVIDE_3(3, false),
    DIVIDE_5(5, false),
    ITEM_SUBSTITUTION_ON,
    ITEM_SUBSTITUTION_OFF,
    OUTPUT_SUBSTITUTION_ON,
    OUTPUT_SUBSTITUTION_OFF;

    private final int factor;
    private final boolean multiplication;

    PatternCacheBatchCommand() {
        this(0, false);
    }

    PatternCacheBatchCommand(int factor, boolean multiplication) {
        this.factor = factor;
        this.multiplication = multiplication;
    }

    public boolean isScaling() {
        return factor > 0;
    }

    public int getFactor() {
        return factor;
    }

    public boolean isMultiplication() {
        return multiplication;
    }
}
