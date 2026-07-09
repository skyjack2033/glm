package com.github.skyjack2033.wirelessmehatch.metatileentity;

import net.minecraft.item.ItemStack;

import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.util.item.AEItemStack;
import gregtech.api.interfaces.IOutputBus;
import gregtech.api.interfaces.IOutputBusTransaction;
import gregtech.api.util.GTUtility;
import gregtech.common.tileentities.machines.outputme.base.MTEHatchOutputMEBase;
import gregtech.common.tileentities.machines.outputme.util.AECacheCounter;

/**
 * Item-side output transaction for {@link MTEWirelessOutputHatchME}. Mirrors GT's
 * {@code MTEHatchOutputBusME.MEOutputBusTransaction}: buffers item output into a local {@link AECacheCounter} during
 * recipe processing (or against a freshly opened cell when recipe-checking) and commits it into the item provider's ME
 * cache on {@link #commit()}.
 *
 * <p>
 * {@link #getBus()} returns {@code null} because the wireless hatch is a dual output recognised via the
 * {@code MTEMultiBlockBaseMixin} (Task 7), not a standalone GT {@link IOutputBus}. The filtering methods are overridden
 * to consult the item provider directly, so the default {@code getBus()}-based path is never used.
 */
public class WirelessOutputItemTransaction implements IOutputBusTransaction, IOutputBusTransaction.IRecipeCheckAware {

    private final MTEWirelessOutputHatchME hatch;
    private final MTEHatchOutputMEBase<IAEItemStack> provider;
    private final AECacheCounter<GTUtility.ItemId> cache = new AECacheCounter<>();
    private final long availableSpace;
    private boolean active = true;
    private boolean recipeCheck;
    private appeng.api.storage.IMEInventoryHandler<IAEItemStack> cell;

    public WirelessOutputItemTransaction(MTEWirelessOutputHatchME hatch) {
        this.hatch = hatch;
        this.provider = hatch.getItemProvider();
        this.availableSpace = provider.getAvailableSpace();
    }

    @Override
    public void setRecipeCheck(boolean recipeCheck) {
        this.recipeCheck = recipeCheck;
        if (!recipeCheck) return;
        if (!provider.shouldCheck()) return;
        provider.flushCachedStack();
        cell = appeng.api.AEApi.instance()
            .registries()
            .cell()
            .getCellInventory(
                hatch.getCellStack()
                    .copy(),
                null,
                StorageChannel.ITEMS);
    }

    @Override
    public IOutputBus getBus() {
        return new WirelessOutputBusAdapter(hatch);
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
    public boolean isFilteredToItem(GTUtility.ItemId itemId) {
        return provider.canStore(AEItemStack.create(itemId.getItemStack()));
    }

    @Override
    public boolean storePartial(GTUtility.ItemId itemId, ItemStack stack) {
        if (!active) {
            throw new IllegalStateException("Cannot add to a transaction after committing it");
        }
        if (recipeCheck && provider.shouldCheck() && cell != null) {
            IAEItemStack request = AEItemStack.create(stack);
            IAEItemStack left = (IAEItemStack) cell
                .injectItems(request, appeng.api.config.Actionable.MODULATE, hatch.getActionSource());
            int used = stack.stackSize - (left == null ? 0 : (int) left.getStackSize());
            cache.insert(itemId, used);
            stack.stackSize -= used;
            return stack.stackSize == 0;
        }
        if (!hasAvailableSpace() || !isFilteredToItem(itemId)) {
            return false;
        }
        cache.insert(itemId, stack.stackSize);
        stack.stackSize = 0;
        return true;
    }

    @Override
    public void completeItem(GTUtility.ItemId itemId) {
        // No-op, matching MTEHatchOutputBusME.MEOutputBusTransaction.
    }

    @Override
    public void commit() {
        cache.iterateAll((itemId, amount) -> {
            IAEItemStack stack = AEItemStack.create(itemId.getItemStack());
            stack.setStackSize(amount);
            provider.addToCache(stack);
        });
        hatch.markDirty();
        active = false;
    }
}
