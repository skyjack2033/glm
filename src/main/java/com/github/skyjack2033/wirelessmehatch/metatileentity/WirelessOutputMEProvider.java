package com.github.skyjack2033.wirelessmehatch.metatileentity;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

import appeng.api.config.Actionable;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.me.GridAccessException;
import appeng.util.item.AEFluidStack;
import appeng.util.item.AEItemStack;
import gregtech.api.util.GTUtility;

/**
 * Buffered output provider for wireless ME hatch. Caches items/fluids locally and flushes them to the ME network
 * every tick. This avoids per-item poweredInsert overhead and handles the case where the network is temporarily
 * unavailable.
 */
public class WirelessOutputMEProvider<T extends IAEStack<T>> {

    private final MTEWirelessOutputHatchME hatch;
    private final StorageChannel channel;
    private final long maxCapacity;
    private final Map<Object, Long> cache = new HashMap<>();
    private long totalCached = 0;
    private long flushCounter = 0;

    public WirelessOutputMEProvider(MTEWirelessOutputHatchME hatch, StorageChannel channel, long maxCapacity) {
        this.hatch = hatch;
        this.channel = channel;
        this.maxCapacity = maxCapacity;
    }

    /**
     * Store an AE stack into the cache. Returns true if the entire stack was stored.
     */
    public boolean storePartial(T aeStack, boolean simulate) {
        if (aeStack == null) return false;
        long amount = aeStack.getStackSize();
        if (amount <= 0) return false;

        Object key = getKey(aeStack);
        long current = cache.getOrDefault(key, 0L);
        if (current + amount > maxCapacity) {
            long canStore = maxCapacity - current;
            if (canStore <= 0) {
                aeStack.setStackSize(amount);
                return false;
            }
            if (!simulate) {
                cache.put(key, current + canStore);
                totalCached += canStore;
            }
            aeStack.setStackSize(amount - canStore);
            return false;
        }

        if (!simulate) {
            cache.put(key, current + amount);
            totalCached += amount;
            hatch.markDirty();
        }
        aeStack.setStackSize(0);
        return true;
    }

    /**
     * Flush all cached items/fluids to the ME network. Called every tick.
     */
    public void flushCachedStack() {
        if (cache.isEmpty()) return;
        flushCounter++;
        if (flushCounter % 5 != 0) return; // flush every 5 ticks (0.25s)

        try {
            if (!hatch.getProxy()
                .isActive()) return;
            appeng.api.networking.storage.IStorageGrid storage = hatch.getProxy()
                .getStorage();
            if (storage == null) return;

            @SuppressWarnings("unchecked")
            IMEMonitor<T> monitor = (IMEMonitor<T>) (channel == StorageChannel.ITEMS ? storage.getItemInventory()
                : storage.getFluidInventory());

            java.util.List<Map.Entry<Object, Long>> entries = new java.util.ArrayList<>(cache.entrySet());
            for (Map.Entry<Object, Long> entry : entries) {
                T stack = createStack(entry.getKey(), entry.getValue());
                if (stack == null) continue;
                T leftover = monitor.injectItems(stack, Actionable.MODULATE, hatch.getActionSource());
                long stored = entry.getValue() - (leftover == null ? 0 : leftover.getStackSize());
                if (stored <= 0) continue;
                long remaining = entry.getValue() - stored;
                totalCached -= stored;
                if (remaining <= 0) {
                    cache.remove(entry.getKey());
                } else {
                    cache.put(entry.getKey(), remaining);
                }
            }
        } catch (GridAccessException ignored) {}
    }

    public boolean isFiltered() {
        return false;
    }

    public boolean canStore(T stack) {
        return true;
    }

    public long getAvailableSpace() {
        return maxCapacity - totalCached;
    }

    public void saveNBTData(NBTTagCompound tag) {
        tag.setLong("totalCached", totalCached);
        // Save cache items
        NBTTagCompound cacheTag = new NBTTagCompound();
        int i = 0;
        for (Map.Entry<Object, Long> entry : cache.entrySet()) {
            NBTTagCompound itemTag = new NBTTagCompound();
            if (channel == StorageChannel.ITEMS) {
                ItemStack is = ((GTUtility.ItemId) entry.getKey()).getItemStack();
                itemTag.setTag("item", is.writeToNBT(new NBTTagCompound()));
            } else {
                FluidStack fs = ((GTUtility.FluidId) entry.getKey()).getFluidStack();
                itemTag.setTag("fluid", fs.writeToNBT(new NBTTagCompound()));
            }
            itemTag.setLong("amount", entry.getValue());
            cacheTag.setTag("e" + i, itemTag);
            i++;
        }
        cacheTag.setInteger("count", i);
        tag.setTag("cache", cacheTag);
    }

    public void loadNBTData(NBTTagCompound tag) {
        totalCached = tag.getLong("totalCached");
        cache.clear();
        if (tag.hasKey("cache")) {
            NBTTagCompound cacheTag = tag.getCompoundTag("cache");
            int count = cacheTag.getInteger("count");
            for (int i = 0; i < count; i++) {
                if (!cacheTag.hasKey("e" + i)) continue;
                NBTTagCompound itemTag = cacheTag.getCompoundTag("e" + i);
                long amount = itemTag.getLong("amount");
                if (channel == StorageChannel.ITEMS && itemTag.hasKey("item")) {
                    ItemStack is = ItemStack.loadItemStackFromNBT(itemTag.getCompoundTag("item"));
                    if (is != null) cache.put(GTUtility.ItemId.create(is), amount);
                } else if (itemTag.hasKey("fluid")) {
                    FluidStack fs = FluidStack.loadFluidStackFromNBT(itemTag.getCompoundTag("fluid"));
                    if (fs != null) cache.put(GTUtility.FluidId.create(fs), amount);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Object getKey(T stack) {
        if (channel == StorageChannel.ITEMS) {
            return GTUtility.ItemId.create(((IAEItemStack) stack).getItemStack());
        } else {
            return GTUtility.FluidId.create(((IAEFluidStack) stack).getFluidStack());
        }
    }

    @SuppressWarnings("unchecked")
    private T createStack(Object key, long amount) {
        if (channel == StorageChannel.ITEMS) {
            ItemStack is = ((GTUtility.ItemId) key).getItemStack();
            if (is == null) return null;
            IAEItemStack ae = AEItemStack.create(is);
            ae.setStackSize(amount);
            return (T) ae;
        } else {
            FluidStack fs = ((GTUtility.FluidId) key).getFluidStack();
            if (fs == null) return null;
            IAEFluidStack ae = AEFluidStack.create(fs);
            ae.setStackSize(amount);
            return (T) ae;
        }
    }
}
