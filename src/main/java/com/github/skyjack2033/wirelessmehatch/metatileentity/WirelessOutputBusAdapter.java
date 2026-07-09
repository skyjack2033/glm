package com.github.skyjack2033.wirelessmehatch.metatileentity;

import net.minecraft.item.ItemStack;

import gregtech.api.enums.OutputBusType;
import gregtech.api.interfaces.IOutputBus;
import gregtech.api.interfaces.IOutputBusTransaction;
import gregtech.api.util.GTUtility;

/**
 * Adapter that wraps a {@link MTEWirelessOutputHatchME} as an {@link IOutputBus} for GT's item output pipeline
 * ({@code ItemEjectionHelper}, {@code VoidProtectionHelper}, {@code ParallelHelper}). This is needed because the hatch
 * also extends {@code MTEHatchOutput} (which implements {@code IOutputHatch}), and {@code IOutputHatch} and
 * {@code IOutputBus} both declare {@code createTransaction()} with incompatible return types, so the hatch cannot
 * implement both interfaces directly.
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
        return hatch.isFilteredToItem(itemId);
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
}
