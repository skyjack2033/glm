package com.github.skyjack2033.wirelessmehatch.metatileentity;

import net.minecraft.item.ItemStack;

import gregtech.api.enums.OutputBusType;
import gregtech.api.interfaces.IOutputBus;
import gregtech.api.interfaces.IOutputBusTransaction;
import gregtech.api.util.GTUtility;

/**
 * Adapter that wraps a {@link MTEWirelessOutputHatchME} as an {@link IOutputBus} for GT's item output pipeline
 * (ItemEjectionHelper, VoidProtectionHelper, ParallelHelper). Needed because the hatch extends MTEHatchOutput (which
 * implements IOutputHatch), and IOutputHatch.createTransaction() conflicts with IOutputBus.createTransaction() on
 * return type. Registered into parent multi-block's mOutputBusses via reflection (no Mixin).
 */
public class WirelessOutputBusAdapter implements IOutputBus {

    private final MTEWirelessOutputHatchME hatch;

    public WirelessOutputBusAdapter(MTEWirelessOutputHatchME hatch) {
        this.hatch = hatch;
    }

    @Override
    public boolean isFiltered() {
        return hatch.getItemProvider()
            .isFiltered();
    }

    @Override
    public boolean isFilteredToItem(GTUtility.ItemId itemId) {
        return false;
    }

    @Override
    public OutputBusType getBusType() {
        return OutputBusType.MECacheUnfiltered;
    }

    @Override
    public boolean storePartial(ItemStack stack) {
        return hatch.storePartial(stack, false);
    }

    @Override
    public boolean storePartial(ItemStack stack, boolean simulate) {
        return hatch.storePartial(stack, simulate);
    }

    @Override
    public IOutputBusTransaction createTransaction() {
        return hatch.createItemTransaction();
    }

    public MTEWirelessOutputHatchME getHatch() {
        return hatch;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof WirelessOutputBusAdapter other && other.hatch == hatch;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(hatch);
    }
}
