package com.github.skyjack2033.wirelessmehatch.output;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.item.ItemStack;

import gregtech.api.interfaces.IOutputBus;
import gregtech.api.interfaces.IOutputBusTransaction;
import gregtech.api.util.GTUtility;

public final class WirelessItemOutputTransaction implements IOutputBusTransaction {

    private final IOutputBus bus;
    private final WirelessUnifiedOutputCore core;
    private final Map<GTUtility.ItemId, Long> buffered = new HashMap<>();
    private long bufferedTotal;
    private boolean active = true;

    public WirelessItemOutputTransaction(IOutputBus bus, WirelessUnifiedOutputCore core) {
        this.bus = bus;
        this.core = core;
    }

    @Override
    public IOutputBus getBus() {
        return bus;
    }

    @Override
    public boolean hasAvailableSpace() {
        return active && getAvailableSpace() > 0L;
    }

    @Override
    public boolean storePartial(GTUtility.ItemId itemId, ItemStack stack) {
        if (!active) return false;
        if (stack == null || stack.stackSize <= 0) return true;

        long accepted = Math.min(stack.stackSize, getAvailableSpace());
        if (accepted <= 0L) return false;

        GTUtility.ItemId key = itemId == null ? GTUtility.ItemId.create(stack) : itemId;
        buffered.put(key, buffered.getOrDefault(key, 0L) + accepted);
        bufferedTotal += accepted;
        stack.stackSize -= (int) accepted;
        return stack.stackSize <= 0;
    }

    @Override
    public void completeItem(GTUtility.ItemId itemId) {}

    @Override
    public void commit() {
        if (!active) return;
        active = false;
        for (Map.Entry<GTUtility.ItemId, Long> entry : buffered.entrySet()) {
            long remaining = entry.getValue();
            while (remaining > 0L) {
                ItemStack stack = entry.getKey()
                    .getItemStack();
                if (stack == null) break;
                int chunk = (int) Math.min(Integer.MAX_VALUE, remaining);
                stack.stackSize = chunk;
                core.storeItem(stack, false);
                long stored = chunk - stack.stackSize;
                if (stored <= 0L) break;
                remaining -= stored;
            }
        }
        buffered.clear();
        bufferedTotal = 0L;
    }

    private long getAvailableSpace() {
        long used = core.getItemCached() + bufferedTotal;
        if (used < 0L) return 0L;
        return Math.max(0L, core.getItemCapacity() - used);
    }
}
