package com.github.skyjack2033.wirelessmehatch.output;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.function.IntUnaryOperator;

import net.minecraftforge.fluids.FluidStack;

import com.github.skyjack2033.wirelessmehatch.api.SharedFluidOutputStore;

import gregtech.api.interfaces.fluid.IFluidStore;
import gregtech.api.interfaces.tileentity.IVoidable;
import gregtech.api.util.OutputHatchWrapper;

public final class SharedFluidVoidProtectionHelper {

    private SharedFluidVoidProtectionHelper() {}

    public static Integer limit(IVoidable machine, FluidStack[] fluidOutputs, int maxParallel,
        IntUnaryOperator chanceGetter) {
        List<? extends IFluidStore> stores = machine.getFluidOutputSlots(fluidOutputs);
        IdentityHashMap<SharedFluidOutputStore, Boolean> sharedStores = new IdentityHashMap<>();
        long remaining = 0L;

        for (IFluidStore store : stores) {
            IFluidStore physicalStore = store instanceof OutputHatchWrapper wrapper ? wrapper.unwrap() : store;
            if (!(physicalStore instanceof SharedFluidOutputStore sharedStore)
                || sharedStores.put(sharedStore, Boolean.TRUE) != null) {
                continue;
            }

            remaining = saturatingAdd(remaining, Math.max(0L, sharedStore.getSharedFluidRemainingCapacity()));
        }

        if (sharedStores.isEmpty()) return null;
        return SharedFluidCapacityCalculator.limit(maxParallel, remaining, fluidOutputs, chanceGetter);
    }

    private static long saturatingAdd(long left, long right) {
        if (Long.MAX_VALUE - left < right) return Long.MAX_VALUE;
        return left + right;
    }
}
