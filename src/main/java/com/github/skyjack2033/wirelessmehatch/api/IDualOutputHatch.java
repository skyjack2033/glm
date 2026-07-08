package com.github.skyjack2033.wirelessmehatch.api;

import net.minecraft.item.ItemStack;

import gregtech.api.interfaces.IOutputBusTransaction;
import gregtech.api.interfaces.IOutputHatchTransaction;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;

/**
 * Marks a MetaTileEntity that acts as BOTH a fluid output hatch and an item output bus simultaneously. Recognised by
 * {@code MTEMultiBlockBaseMixin} so that multiblock controllers treat the implementor as a dual output (added to
 * {@code mDualOutputHatches}).
 *
 * <p>
 * Symmetric to GT's native {@code IDualInputHatch} for the input side.
 */
public interface IDualOutputHatch extends IMetaTileEntity {

    /** Create a transaction for fluid output (mirrors {@code IOutputHatch#createTransaction}). */
    IOutputHatchTransaction createFluidTransaction();

    /** Create a transaction for item output (mirrors {@code IOutputBus#createTransaction}). */
    IOutputBusTransaction createItemTransaction();

    /**
     * Try to store an item stack into this output (delegates to the item provider).
     *
     * @param stack    item to store (mutated: stack size reduced by amount stored)
     * @param simulate if true, only test whether storage is possible without mutating state
     * @return true if (in simulate mode) storage would succeed / (in real mode) storage happened
     */
    boolean storePartial(ItemStack stack, boolean simulate);
}
