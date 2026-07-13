package com.github.skyjack2033.wirelessmehatch.output;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;

import org.junit.BeforeClass;
import org.junit.Test;

import com.github.skyjack2033.wirelessmehatch.api.SharedFluidOutputStore;
import com.github.skyjack2033.wirelessmehatch.api.WirelessOutputCapacityHost;
import com.github.skyjack2033.wirelessmehatch.metatileentity.MTEWirelessUnifiedOutputAssemblyME;

import gregtech.api.interfaces.fluid.IFluidStore;
import gregtech.api.interfaces.tileentity.IVoidable;
import gregtech.api.util.OutputHatchWrapper;
import sun.misc.Unsafe;

public class SharedFluidVoidProtectionHelperTest {

    private static Unsafe unsafe;

    @BeforeClass
    public static void initializeUnsafe() throws ReflectiveOperationException {
        Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
        theUnsafe.setAccessible(true);
        unsafe = (Unsafe) theUnsafe.get(null);
    }

    @Test
    public void returnsNullWhenMachineHasNoSharedFluidStore() {
        IVoidable machine = machine(Collections.singletonList(new OrdinaryStore()));

        assertNull(SharedFluidVoidProtectionHelper.limit(machine, outputs(100), 8, index -> 10_000));
    }

    @Test
    public void wrappedSharedStoreContributesUnderlyingCapacity() {
        MTEWirelessUnifiedOutputAssemblyME shared = sharedOutputHatch(1_000L);

        assertEquals(
            Integer.valueOf(1),
            SharedFluidVoidProtectionHelper
                .limit(machine(Collections.singletonList(wrapper(shared))), outputs(600), 8, index -> 10_000));
    }

    @Test
    public void repeatedWrappersAroundSameSharedStoreAreIdentityDeduplicated() {
        MTEWirelessUnifiedOutputAssemblyME shared = sharedOutputHatch(1_000L);

        assertEquals(
            Integer.valueOf(1),
            SharedFluidVoidProtectionHelper
                .limit(machine(Arrays.asList(wrapper(shared), wrapper(shared))), outputs(600), 8, index -> 10_000));
    }

    @Test
    public void directAndWrappedSameSharedStoreAreIdentityDeduplicated() {
        MTEWirelessUnifiedOutputAssemblyME shared = sharedOutputHatch(1_000L);

        assertEquals(
            Integer.valueOf(1),
            SharedFluidVoidProtectionHelper.limit(
                machine(Arrays.asList((IFluidStore) (Object) shared, wrapper(shared))),
                outputs(600),
                8,
                index -> 10_000));
    }

    @Test
    public void wrappedSharedStoreIsFoundAlongsideOrdinaryStore() {
        MTEWirelessUnifiedOutputAssemblyME shared = sharedOutputHatch(1_000L);

        assertEquals(
            Integer.valueOf(1),
            SharedFluidVoidProtectionHelper
                .limit(machine(Arrays.asList(new OrdinaryStore(), wrapper(shared))), outputs(600), 8, index -> 10_000));
    }

    @Test
    public void wrappedSharedStoreLimitOverridesNativeEarlyZeroOrPerFluidOvercredit() {
        MTEWirelessUnifiedOutputAssemblyME shared = sharedOutputHatch(24_000L);
        Integer nativeEarlyResult = 0;

        Integer sharedResult = SharedFluidVoidProtectionHelper
            .limit(machine(Collections.singletonList(wrapper(shared))), outputs(4_000, 6_000), 8, index -> 10_000);

        assertEquals(Integer.valueOf(0), nativeEarlyResult);
        assertEquals(Integer.valueOf(2), sharedResult);
    }

    @Test
    public void identityDeduplicatesSharedStores() {
        SharedStore shared = new SharedStore(1_000L);
        IVoidable machine = machine(Arrays.asList(shared, shared));

        assertEquals(
            Integer.valueOf(1),
            SharedFluidVoidProtectionHelper.limit(machine, outputs(600), 8, index -> 10_000));
    }

    @Test
    public void distinctEqualStoresBothContributeCapacity() {
        SharedStore first = new EqualSharedStore(600L);
        SharedStore second = new EqualSharedStore(600L);
        IVoidable machine = machine(Arrays.asList(first, second));

        assertEquals(
            Integer.valueOf(2),
            SharedFluidVoidProtectionHelper.limit(machine, outputs(600), 8, index -> 10_000));
    }

    @Test
    public void sharedCapacitySumSaturatesAtLongMaximum() {
        IVoidable machine = machine(Arrays.asList(new SharedStore(Long.MAX_VALUE), new SharedStore(1L)));

        assertEquals(
            Integer.valueOf(4),
            SharedFluidVoidProtectionHelper.limit(machine, outputs(Integer.MAX_VALUE), 4, index -> 10_000));
    }

    private static IVoidable machine(List<? extends IFluidStore> stores) {
        return (IVoidable) Proxy.newProxyInstance(
            IVoidable.class.getClassLoader(),
            new Class<?>[] { IVoidable.class },
            (proxy, method, arguments) -> {
                if (method.getName()
                    .equals("getFluidOutputSlots")) return stores;
                Class<?> returnType = method.getReturnType();
                if (returnType == boolean.class) return false;
                if (returnType == int.class) return 0;
                if (returnType == long.class) return 0L;
                return null;
            });
    }

    private static FluidStack[] outputs(int... amounts) {
        FluidStack[] outputs = new FluidStack[amounts.length];
        for (int index = 0; index < amounts.length; index++) {
            outputs[index] = output(amounts[index]);
        }
        return outputs;
    }

    private static FluidStack output(int amount) {
        try {
            FluidStack stack = (FluidStack) unsafe.allocateInstance(FluidStack.class);
            stack.amount = amount;
            return stack;
        } catch (InstantiationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static MTEWirelessUnifiedOutputAssemblyME sharedOutputHatch(long remaining) {
        MTEWirelessUnifiedOutputAssemblyME hatch = new MTEWirelessUnifiedOutputAssemblyME(
            "test",
            4,
            new String[0],
            null);
        ((WirelessOutputCapacityHost) (Object) hatch).setFluidCapacity(remaining);
        return hatch;
    }

    private static OutputHatchWrapper wrapper(MTEWirelessUnifiedOutputAssemblyME hatch) {
        return new OutputHatchWrapper(hatch, fluid -> true);
    }

    private static class OrdinaryStore implements IFluidStore {

        @Override
        public boolean isEmptyAndAcceptsAnyFluid() {
            return true;
        }

        @Override
        public boolean canStoreFluid(FluidStack fluidStack) {
            return true;
        }

        @Override
        public int fill(FluidStack resource, boolean doFill) {
            return 0;
        }

        @Override
        public FluidStack drain(int maxDrain, boolean doDrain) {
            return null;
        }

        @Override
        public FluidStack getFluid() {
            return null;
        }

        @Override
        public int getFluidAmount() {
            return 0;
        }

        @Override
        public int getCapacity() {
            return 0;
        }

        @Override
        public FluidTankInfo getInfo() {
            return new FluidTankInfo(null, 0);
        }
    }

    private static class SharedStore extends OrdinaryStore implements SharedFluidOutputStore {

        private final long remaining;

        private SharedStore(long remaining) {
            this.remaining = remaining;
        }

        @Override
        public long getSharedFluidRemainingCapacity() {
            return remaining;
        }
    }

    private static final class EqualSharedStore extends SharedStore {

        private EqualSharedStore(long remaining) {
            super(remaining);
        }

        @Override
        public boolean equals(Object object) {
            return object instanceof EqualSharedStore;
        }

        @Override
        public int hashCode() {
            return 1;
        }
    }
}
