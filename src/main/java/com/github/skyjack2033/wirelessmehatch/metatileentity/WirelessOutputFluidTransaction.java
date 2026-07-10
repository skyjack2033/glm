package com.github.skyjack2033.wirelessmehatch.metatileentity;

import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;

import gregtech.api.interfaces.IOutputHatch;
import gregtech.api.interfaces.IOutputHatchTransaction;
import gregtech.api.util.GTUtility;

public class WirelessOutputFluidTransaction
    implements IOutputHatchTransaction, IOutputHatchTransaction.IRecipeCheckAware {

    private final MTEWirelessOutputHatchME hatch;
    private boolean active = true;
    private boolean recipeCheck;
    private final java.util.Map<GTUtility.FluidId, Long> buffered = new java.util.HashMap<>();

    public WirelessOutputFluidTransaction(MTEWirelessOutputHatchME hatch) {
        this.hatch = hatch;
    }

    @Override
    public void setRecipeCheck(boolean recipeCheck) {
        this.recipeCheck = recipeCheck;
    }

    @Override
    public IOutputHatch getHatch() {
        return hatch;
    }

    @Override
    public boolean hasAvailableSpace() {
        return true;
    }

    @Override
    public boolean isFiltered() {
        return false;
    }

    @Override
    public boolean isFilteredToFluid(GTUtility.FluidId fluidId) {
        return false;
    }

    @Override
    public boolean storePartial(GTUtility.FluidId fluidId, FluidStack stack) {
        if (!active || stack == null || stack.amount <= 0) return true;
        buffered.merge(fluidId, (long) stack.amount, Long::sum);
        stack.amount = 0;
        return true;
    }

    @Override
    public void completeFluid(GTUtility.FluidId fluidId) {}

    @Override
    public void commit() {
        if (!active) return;
        buffered.forEach((fluidId, amount) -> {
            FluidStack fs = fluidId.getFluidStack();
            if (fs != null) {
                fs.amount = (int) Math.min(amount, Integer.MAX_VALUE);
                hatch.fill(ForgeDirection.UNKNOWN, fs, true);
            }
        });
        buffered.clear();
        active = false;
    }
}
