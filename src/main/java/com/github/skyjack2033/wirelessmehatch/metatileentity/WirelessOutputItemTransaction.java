package com.github.skyjack2033.wirelessmehatch.metatileentity;

import net.minecraft.item.ItemStack;

import gregtech.api.interfaces.IOutputBus;
import gregtech.api.interfaces.IOutputBusTransaction;
import gregtech.api.util.GTUtility;

/**
 * Item-side output transaction for wireless hatch. Buffers item output during recipe processing and commits to the
 * item provider's cache on {@link #commit()}.
 */
public class WirelessOutputItemTransaction implements IOutputBusTransaction, IOutputBusTransaction.IRecipeCheckAware {

    private final MTEWirelessOutputHatchME hatch;
    private boolean active = true;
    private boolean recipeCheck;
    private final java.util.Map<GTUtility.ItemId, Long> buffered = new java.util.HashMap<>();

    public WirelessOutputItemTransaction(MTEWirelessOutputHatchME hatch) {
        this.hatch = hatch;
    }

    @Override
    public void setRecipeCheck(boolean recipeCheck) {
        this.recipeCheck = recipeCheck;
    }

    @Override
    public IOutputBus getBus() {
        return new WirelessOutputBusAdapter(hatch);
    }

    @Override
    public boolean hasAvailableSpace() {
        if (!recipeCheck) return true;
        return true; // unlimited capacity
    }

    @Override
    public boolean isFiltered() {
        return false;
    }

    @Override
    public boolean isFilteredToItem(GTUtility.ItemId itemId) {
        return false;
    }

    @Override
    public boolean storePartial(GTUtility.ItemId itemId, ItemStack stack) {
        if (!active) return false;
        if (stack == null || stack.stackSize <= 0) return true;
        buffered.merge(itemId, (long) stack.stackSize, Long::sum);
        stack.stackSize = 0;
        return true;
    }

    @Override
    public void completeItem(GTUtility.ItemId itemId) {}

    @Override
    public void commit() {
        if (!active) return;
        buffered.forEach((itemId, amount) -> {
            ItemStack is = itemId.getItemStack();
            if (is != null) {
                is.stackSize = (int) Math.min(amount, Integer.MAX_VALUE);
                hatch.storePartial(is, false);
            }
        });
        buffered.clear();
        active = false;
    }
}
