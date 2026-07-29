package com.github.skyjack2033.wirelessmehatch.terminal;

import net.minecraft.item.ItemStack;

import appeng.api.implementations.ICraftingPatternItem;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.tile.inventory.IAEAppEngInventory;

public final class PatternCacheInventory extends AppEngInternalInventory {

    public PatternCacheInventory(IAEAppEngInventory owner, int size) {
        super(owner, size, 1);
        setMaxStackSize(1);
    }

    @Override
    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        return stack != null && stack.getItem() instanceof ICraftingPatternItem;
    }
}
