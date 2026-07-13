package com.github.skyjack2033.wirelessmehatch.output;

import java.util.function.IntUnaryOperator;

import net.minecraftforge.fluids.FluidStack;

public final class SharedFluidCapacityCalculator {

    private static final long CHANCE_SCALE = 10_000L;

    private SharedFluidCapacityCalculator() {}

    public static int limit(int maxParallel, long remaining, FluidStack[] outputs, IntUnaryOperator chanceGetter) {
        int boundedMaxParallel = Math.max(0, maxParallel);
        long requiredPerParallel = 0L;

        if (outputs != null) {
            for (int index = 0; index < outputs.length; index++) {
                FluidStack output = outputs[index];
                if (output == null || output.amount <= 0) continue;

                long chance = chanceGetter.applyAsInt(index);
                long occurrences = chance <= 0L ? 0L : (chance + CHANCE_SCALE - 1L) / CHANCE_SCALE;
                requiredPerParallel = saturatingAdd(
                    requiredPerParallel,
                    saturatingMultiply(output.amount, occurrences));
            }
        }

        if (requiredPerParallel == 0L) return boundedMaxParallel;

        long boundedRemaining = Math.max(0L, remaining);
        return (int) Math.min((long) boundedMaxParallel, boundedRemaining / requiredPerParallel);
    }

    private static long saturatingMultiply(long left, long right) {
        if (left == 0L || right == 0L) return 0L;
        if (left > Long.MAX_VALUE / right) return Long.MAX_VALUE;
        return left * right;
    }

    private static long saturatingAdd(long left, long right) {
        if (Long.MAX_VALUE - left < right) return Long.MAX_VALUE;
        return left + right;
    }
}
