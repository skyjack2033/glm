package com.github.skyjack2033.wirelessmehatch.output;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.me.GridAccessException;
import appeng.me.helpers.AENetworkProxy;
import appeng.util.Platform;
import appeng.util.item.AEFluidStack;
import appeng.util.item.AEItemStack;
import gregtech.api.util.GTUtility;

public final class WirelessUnifiedOutputCore {

    private static final String KEY_ITEM_CAPACITY = "itemCapacity";
    private static final String KEY_FLUID_CAPACITY = "fluidCapacity";
    private static final String KEY_ITEMS = "itemCache";
    private static final String KEY_FLUIDS = "fluidCache";
    private static final String KEY_COUNT = "count";
    private static final String KEY_AMOUNT = "amount";

    private final Runnable markDirty;
    private final NetworkAccess networkAccess;
    private final long defaultItemCapacity;
    private final long defaultFluidCapacity;
    private final Map<GTUtility.ItemId, Long> itemCache = new HashMap<>();
    private final Map<GTUtility.FluidId, Long> fluidCache = new HashMap<>();

    private long itemCapacity;
    private long fluidCapacity;
    private long itemCached;
    private long fluidCached;

    public WirelessUnifiedOutputCore(NetworkAccess networkAccess, Runnable markDirty) {
        this(networkAccess, markDirty, Long.MAX_VALUE, Long.MAX_VALUE);
    }

    public WirelessUnifiedOutputCore(NetworkAccess networkAccess, Runnable markDirty, long defaultItemCapacity,
        long defaultFluidCapacity) {
        this.networkAccess = networkAccess;
        this.markDirty = markDirty;
        this.defaultItemCapacity = sanitizeCapacity(defaultItemCapacity);
        this.defaultFluidCapacity = sanitizeCapacity(defaultFluidCapacity);
        this.itemCapacity = this.defaultItemCapacity;
        this.fluidCapacity = this.defaultFluidCapacity;
    }

    public boolean storeItem(ItemStack stack, boolean simulate) {
        if (stack == null || stack.stackSize <= 0) return true;
        long amount = stack.stackSize;
        long accepted = Math.min(amount, Math.max(0L, itemCapacity - itemCached));
        if (accepted <= 0) return false;
        if (!simulate) {
            GTUtility.ItemId key = GTUtility.ItemId.create(stack);
            itemCache.put(key, itemCache.getOrDefault(key, 0L) + accepted);
            itemCached += accepted;
            markDirty.run();
        }
        stack.stackSize -= (int) accepted;
        return accepted == amount;
    }

    public int storeFluid(FluidStack fluid, boolean simulate) {
        if (fluid == null || fluid.amount <= 0) return 0;
        long accepted = Math.min((long) fluid.amount, Math.max(0L, fluidCapacity - fluidCached));
        if (accepted <= 0) return 0;
        if (!simulate) {
            GTUtility.FluidId key = GTUtility.FluidId.create(fluid);
            fluidCache.put(key, fluidCache.getOrDefault(key, 0L) + accepted);
            fluidCached += accepted;
            markDirty.run();
        }
        return (int) accepted;
    }

    public void flush() {
        AENetworkProxy proxy = networkAccess.getProxy();
        if (proxy == null || !proxy.isActive()) return;
        try {
            IStorageGrid storage = proxy.getStorage();
            IEnergyGrid energy = proxy.getEnergy();
            BaseActionSource source = networkAccess.getActionSource();
            flushItems(storage.getItemInventory(), energy, source);
            flushFluids(storage.getFluidInventory(), energy, source);
        } catch (GridAccessException ignored) {}
    }

    public long getItemCapacity() {
        return itemCapacity;
    }

    public void setItemCapacity(long itemCapacity) {
        this.itemCapacity = Math.max(0L, itemCapacity);
        markDirty.run();
    }

    public long getFluidCapacity() {
        return fluidCapacity;
    }

    public void setFluidCapacity(long fluidCapacity) {
        this.fluidCapacity = Math.max(0L, fluidCapacity);
        markDirty.run();
    }

    public long getItemCached() {
        return itemCached;
    }

    public long getFluidCached() {
        return fluidCached;
    }

    public void writeToNBT(NBTTagCompound tag) {
        tag.setLong(KEY_ITEM_CAPACITY, itemCapacity);
        tag.setLong(KEY_FLUID_CAPACITY, fluidCapacity);
        writeItems(tag);
        writeFluids(tag);
    }

    public void readFromNBT(NBTTagCompound tag) {
        itemCapacity = tag.hasKey(KEY_ITEM_CAPACITY) ? sanitizeCapacity(tag.getLong(KEY_ITEM_CAPACITY))
            : defaultItemCapacity;
        fluidCapacity = tag.hasKey(KEY_FLUID_CAPACITY) ? sanitizeCapacity(tag.getLong(KEY_FLUID_CAPACITY))
            : defaultFluidCapacity;
        itemCache.clear();
        fluidCache.clear();
        itemCached = 0L;
        fluidCached = 0L;
        readItems(tag);
        readFluids(tag);
    }

    public void readLegacyProviderNBT(NBTTagCompound itemProvider, NBTTagCompound fluidProvider) {
        itemCache.clear();
        fluidCache.clear();
        itemCached = 0L;
        fluidCached = 0L;
        readLegacyItems(itemProvider);
        readLegacyFluids(fluidProvider);
    }

    private void flushItems(IMEInventory<IAEItemStack> inventory, IEnergyGrid energy, BaseActionSource source) {
        for (Map.Entry<GTUtility.ItemId, Long> entry : new java.util.ArrayList<>(itemCache.entrySet())) {
            long remaining = entry.getValue();
            while (remaining > 0) {
                int chunk = (int) Math.min(Integer.MAX_VALUE, remaining);
                ItemStack stack = entry.getKey()
                    .getItemStack();
                if (stack == null) break;
                stack.stackSize = chunk;
                IAEItemStack aeStack = AEItemStack.create(stack);
                aeStack.setStackSize(chunk);
                IAEItemStack leftover = (IAEItemStack) Platform.poweredInsert(energy, inventory, aeStack, source);
                long inserted = chunk - (leftover == null ? 0L : leftover.getStackSize());
                if (inserted <= 0) break;
                remaining -= inserted;
                itemCached -= inserted;
            }
            if (remaining <= 0) {
                itemCache.remove(entry.getKey());
            } else {
                itemCache.put(entry.getKey(), remaining);
            }
        }
    }

    private void flushFluids(IMEInventory<IAEFluidStack> inventory, IEnergyGrid energy, BaseActionSource source) {
        for (Map.Entry<GTUtility.FluidId, Long> entry : new java.util.ArrayList<>(fluidCache.entrySet())) {
            long remaining = entry.getValue();
            while (remaining > 0) {
                int chunk = (int) Math.min(Integer.MAX_VALUE, remaining);
                FluidStack stack = entry.getKey()
                    .getFluidStack();
                if (stack == null) break;
                stack.amount = chunk;
                IAEFluidStack aeStack = AEFluidStack.create(stack);
                aeStack.setStackSize(chunk);
                IAEFluidStack leftover = (IAEFluidStack) Platform.poweredInsert(energy, inventory, aeStack, source);
                long inserted = chunk - (leftover == null ? 0L : leftover.getStackSize());
                if (inserted <= 0) break;
                remaining -= inserted;
                fluidCached -= inserted;
            }
            if (remaining <= 0) {
                fluidCache.remove(entry.getKey());
            } else {
                fluidCache.put(entry.getKey(), remaining);
            }
        }
    }

    private void writeItems(NBTTagCompound tag) {
        NBTTagCompound items = new NBTTagCompound();
        int i = 0;
        for (Map.Entry<GTUtility.ItemId, Long> entry : itemCache.entrySet()) {
            ItemStack stack = entry.getKey()
                .getItemStack();
            if (stack == null) continue;
            NBTTagCompound item = new NBTTagCompound();
            item.setTag("stack", stack.writeToNBT(new NBTTagCompound()));
            item.setLong(KEY_AMOUNT, entry.getValue());
            items.setTag("e" + i++, item);
        }
        items.setInteger(KEY_COUNT, i);
        tag.setTag(KEY_ITEMS, items);
    }

    private void writeFluids(NBTTagCompound tag) {
        NBTTagCompound fluids = new NBTTagCompound();
        int i = 0;
        for (Map.Entry<GTUtility.FluidId, Long> entry : fluidCache.entrySet()) {
            FluidStack stack = entry.getKey()
                .getFluidStack();
            if (stack == null) continue;
            NBTTagCompound fluid = new NBTTagCompound();
            fluid.setTag("stack", stack.writeToNBT(new NBTTagCompound()));
            fluid.setLong(KEY_AMOUNT, entry.getValue());
            fluids.setTag("e" + i++, fluid);
        }
        fluids.setInteger(KEY_COUNT, i);
        tag.setTag(KEY_FLUIDS, fluids);
    }

    private void readItems(NBTTagCompound tag) {
        if (!tag.hasKey(KEY_ITEMS)) return;
        NBTTagCompound items = tag.getCompoundTag(KEY_ITEMS);
        int count = items.getInteger(KEY_COUNT);
        for (int i = 0; i < count; i++) {
            NBTTagCompound item = items.getCompoundTag("e" + i);
            ItemStack stack = ItemStack.loadItemStackFromNBT(item.getCompoundTag("stack"));
            long amount = item.getLong(KEY_AMOUNT);
            if (stack != null && amount > 0) {
                itemCache.put(GTUtility.ItemId.create(stack), amount);
                itemCached += amount;
            }
        }
    }

    private void readFluids(NBTTagCompound tag) {
        if (!tag.hasKey(KEY_FLUIDS)) return;
        NBTTagCompound fluids = tag.getCompoundTag(KEY_FLUIDS);
        int count = fluids.getInteger(KEY_COUNT);
        for (int i = 0; i < count; i++) {
            NBTTagCompound fluid = fluids.getCompoundTag("e" + i);
            FluidStack stack = FluidStack.loadFluidStackFromNBT(fluid.getCompoundTag("stack"));
            long amount = fluid.getLong(KEY_AMOUNT);
            if (stack != null && amount > 0) {
                fluidCache.put(GTUtility.FluidId.create(stack), amount);
                fluidCached += amount;
            }
        }
    }

    private void readLegacyItems(NBTTagCompound providerTag) {
        if (providerTag == null || !providerTag.hasKey("cache")) return;
        NBTTagCompound cache = providerTag.getCompoundTag("cache");
        int count = cache.getInteger(KEY_COUNT);
        for (int i = 0; i < count; i++) {
            NBTTagCompound entry = cache.getCompoundTag("e" + i);
            if (!entry.hasKey("item")) continue;
            ItemStack stack = ItemStack.loadItemStackFromNBT(entry.getCompoundTag("item"));
            long amount = entry.getLong(KEY_AMOUNT);
            if (stack != null && amount > 0L) {
                GTUtility.ItemId key = GTUtility.ItemId.create(stack);
                itemCache.put(key, itemCache.getOrDefault(key, 0L) + amount);
                itemCached += amount;
            }
        }
    }

    private void readLegacyFluids(NBTTagCompound providerTag) {
        if (providerTag == null || !providerTag.hasKey("cache")) return;
        NBTTagCompound cache = providerTag.getCompoundTag("cache");
        int count = cache.getInteger(KEY_COUNT);
        for (int i = 0; i < count; i++) {
            NBTTagCompound entry = cache.getCompoundTag("e" + i);
            if (!entry.hasKey("fluid")) continue;
            FluidStack stack = FluidStack.loadFluidStackFromNBT(entry.getCompoundTag("fluid"));
            long amount = entry.getLong(KEY_AMOUNT);
            if (stack != null && amount > 0L) {
                GTUtility.FluidId key = GTUtility.FluidId.create(stack);
                fluidCache.put(key, fluidCache.getOrDefault(key, 0L) + amount);
                fluidCached += amount;
            }
        }
    }

    private static long sanitizeCapacity(long capacity) {
        return Math.max(0L, capacity);
    }

    public interface NetworkAccess {

        AENetworkProxy getProxy();

        BaseActionSource getActionSource();
    }
}
