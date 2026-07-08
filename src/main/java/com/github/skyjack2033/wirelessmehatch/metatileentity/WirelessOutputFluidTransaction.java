package com.github.skyjack2033.wirelessmehatch.metatileentity;

import net.minecraftforge.fluids.FluidStack;

import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.util.item.AEFluidStack;
import gregtech.api.interfaces.IOutputHatchTransaction;
import gregtech.api.util.GTUtility;
import gregtech.common.tileentities.machines.outputme.base.MTEHatchOutputMEBase;
import gregtech.common.tileentities.machines.outputme.util.AECacheCounter;

/**
 * Fluid-side output transaction for {@link MTEWirelessOutputHatchME}. Mirrors GT's
 * {@code MTEHatchOutputME.MEOutputHatchTransaction}: buffers fluid output into a local {@link AECacheCounter} during
 * recipe processing (or against a freshly opened cell when recipe-checking) and commits it into the fluid provider's
 * ME cache on {@link #commit()}.
 */
public class WirelessOutputFluidTransaction
    implements IOutputHatchTransaction, IOutputHatchTransaction.IRecipeCheckAware {

    private final MTEWirelessOutputHatchME hatch;
    private final MTEHatchOutputMEBase<IAEFluidStack> provider;
    private final AECacheCounter<GTUtility.FluidId> cache = new AECacheCounter<>();
    private final long availableSpace;
    private boolean active = true;
    private boolean recipeCheck;
    private appeng.api.storage.IMEInventoryHandler<IAEFluidStack> cell;

    public WirelessOutputFluidTransaction(MTEWirelessOutputHatchME hatch) {
        this.hatch = hatch;
        this.provider = hatch.getFluidProvider();
        this.availableSpace = provider.getAvailableSpace();
    }

    @Override
    public void setRecipeCheck(boolean recipeCheck) {
        this.recipeCheck = recipeCheck;
        if (!recipeCheck) return;
        if (!provider.shouldCheck()) return;
        provider.flushCachedStack();
        // Save provider is null (wireless buffer hatch has no separate cell-save callback); the cell inventory helper
        // works without one.
        cell = appeng.api.AEApi.instance()
            .registries()
            .cell()
            .getCellInventory(
                hatch.getCellStack()
                    .copy(),
                null,
                StorageChannel.FLUIDS);
    }

    @Override
    public MTEWirelessOutputHatchME getHatch() {
        return hatch;
    }

    @Override
    public boolean hasAvailableSpace() {
        if (!recipeCheck) return true;
        return cache.getTotal() < availableSpace;
    }

    @Override
    public boolean isFiltered() {
        return provider.isFiltered();
    }

    @Override
    public boolean isFilteredToFluid(GTUtility.FluidId fluidId) {
        return provider.canStore(AEFluidStack.create(fluidId.getFluidStack()));
    }

    @Override
    public boolean storePartial(GTUtility.FluidId fluidId, FluidStack fluid) {
        if (!active) {
            throw new IllegalStateException("Cannot add to a transaction after committing it");
        }
        if (recipeCheck && provider.shouldCheck() && cell != null) {
            IAEFluidStack request = AEFluidStack.create(fluid);
            IAEFluidStack left = (IAEFluidStack) cell
                .injectItems(request, appeng.api.config.Actionable.MODULATE, hatch.getActionSource());
            int used = fluid.amount - (left == null ? 0 : (int) left.getStackSize());
            cache.insert(fluidId, used);
            fluid.amount -= used;
            return fluid.amount == 0;
        }
        if (!hasAvailableSpace() || !isFilteredToFluid(fluidId)) {
            return false;
        }
        cache.insert(fluidId, fluid.amount);
        fluid.amount = 0;
        return true;
    }

    @Override
    public void completeFluid(GTUtility.FluidId fluidId) {
        // No-op, matching MTEHatchOutputME.MEOutputHatchTransaction.
    }

    @Override
    public void commit() {
        cache.iterateAll((fluidId, amount) -> {
            IAEFluidStack stack = AEFluidStack.create(fluidId.getFluidStack());
            stack.setStackSize(amount);
            provider.addToCache(stack);
        });
        hatch.markDirty();
        active = false;
    }
}
