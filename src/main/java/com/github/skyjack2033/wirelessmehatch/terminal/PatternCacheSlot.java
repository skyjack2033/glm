package com.github.skyjack2033.wirelessmehatch.terminal;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.IInventory;

import appeng.container.slot.SlotRestrictedInput;

public final class PatternCacheSlot extends SlotRestrictedInput {

    public PatternCacheSlot(IInventory inventory, int slot, int x, int y, InventoryPlayer playerInventory) {
        super(PlacableItemType.ENCODED_PATTERN, inventory, slot, x, y, playerInventory);
        setStackLimit(1);
    }
}
