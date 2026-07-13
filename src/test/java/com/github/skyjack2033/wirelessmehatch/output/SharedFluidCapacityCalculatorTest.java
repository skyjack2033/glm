package com.github.skyjack2033.wirelessmehatch.output;

import static org.junit.Assert.assertEquals;

import java.lang.reflect.Field;
import java.util.function.IntUnaryOperator;

import net.minecraftforge.fluids.FluidStack;

import org.junit.BeforeClass;
import org.junit.Test;

import sun.misc.Unsafe;

public class SharedFluidCapacityCalculatorTest {

    private static final IntUnaryOperator CERTAIN = index -> 10_000;

    private static Unsafe unsafe;

    @BeforeClass
    public static void initializeUnsafe() throws ReflectiveOperationException {
        Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
        theUnsafe.setAccessible(true);
        unsafe = (Unsafe) theUnsafe.get(null);
    }

    @Test
    public void aggregateOutputsFitExactlyOnce() {
        assertEquals(1, limit(8, 10_000L, outputs(4_000, 6_000), CERTAIN));
    }

    @Test
    public void aggregateOutputsRejectIndividualFitsThatOvercommitSharedCapacity() {
        assertEquals(0, limit(8, 10_000L, outputs(6_000, 6_000), CERTAIN));
    }

    @Test
    public void aggregateOutputsFitMultipleParallels() {
        assertEquals(2, limit(8, 24_000L, outputs(4_000, 6_000), CERTAIN));
    }

    @Test
    public void duplicateFluidTypesStillConsumeCapacityPerOutput() {
        assertEquals(1, limit(8, 12_000L, outputs(4_000, 6_000), CERTAIN));
    }

    @Test
    public void nullAndNonPositiveOutputsDoNotConsumeCapacity() {
        FluidStack zero = stack(1);
        zero.amount = 0;
        FluidStack negative = stack(1);
        negative.amount = -1;

        assertEquals(7, limit(7, 0L, new FluidStack[] { null, zero, negative }, CERTAIN));
    }

    @Test
    public void chanceRoundingMatchesPinnedVoidProtectionHelper() {
        FluidStack[] outputs = outputs(100, 100, 100, 100, 100);
        int[] chances = { -1, 0, 1, 10_000, 10_001 };

        assertEquals(1, limit(9, 400L, outputs, index -> chances[index]));
        assertEquals(0, limit(9, 399L, outputs, index -> chances[index]));
    }

    @Test
    public void clampsToNonNegativeMaxParallel() {
        assertEquals(3, limit(3, Long.MAX_VALUE, outputs(1), CERTAIN));
        assertEquals(0, limit(-3, Long.MAX_VALUE, outputs(1), CERTAIN));
    }

    @Test
    public void supportsRemainingCapacityAboveIntegerMaxValue() {
        assertEquals(2, limit(8, (long) Integer.MAX_VALUE + 1L, outputs(1_073_741_824), CERTAIN));
    }

    @Test
    public void handlesMaximumPositiveChanceWithoutOverflow() {
        assertEquals(1, limit(4, 2_147_490_000L, outputs(10_000), index -> Integer.MAX_VALUE));
        assertEquals(0, limit(4, 2_147_489_999L, outputs(10_000), index -> Integer.MAX_VALUE));
    }

    @Test
    public void saturatesRequiredCapacityAddition() {
        FluidStack maximum = stack(Integer.MAX_VALUE);
        FluidStack[] outputs = new FluidStack[20_001];
        java.util.Arrays.fill(outputs, maximum);

        assertEquals(1, limit(4, Long.MAX_VALUE, outputs, index -> Integer.MAX_VALUE));
        assertEquals(0, limit(4, Long.MAX_VALUE - 1L, outputs, index -> Integer.MAX_VALUE));
    }

    @Test
    public void negativeRemainingCapacityNeverProducesNegativeParallels() {
        assertEquals(0, limit(4, -1L, outputs(1), CERTAIN));
    }

    private static int limit(int maxParallel, long remaining, FluidStack[] outputs, IntUnaryOperator chanceGetter) {
        return SharedFluidCapacityCalculator.limit(maxParallel, remaining, outputs, chanceGetter);
    }

    private static FluidStack[] outputs(int... amounts) {
        FluidStack[] outputs = new FluidStack[amounts.length];
        for (int i = 0; i < amounts.length; i++) {
            outputs[i] = stack(amounts[i]);
        }
        return outputs;
    }

    private static FluidStack stack(int amount) {
        try {
            FluidStack stack = (FluidStack) unsafe.allocateInstance(FluidStack.class);
            stack.amount = amount;
            return stack;
        } catch (InstantiationException exception) {
            throw new AssertionError(exception);
        }
    }
}
