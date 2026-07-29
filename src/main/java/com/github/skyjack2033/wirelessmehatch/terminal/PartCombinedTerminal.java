package com.github.skyjack2033.wirelessmehatch.terminal;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import appeng.api.networking.events.MENetworkBootingStatusChange;
import appeng.api.networking.events.MENetworkEventSubscribe;
import appeng.api.parts.ICraftingTerminal;
import appeng.api.parts.IInterfaceTerminal;
import appeng.client.texture.CableBusTextures;
import appeng.core.sync.GuiBridge;
import appeng.parts.reporting.PartPatternTerminal;
import appeng.tile.inventory.AppEngInternalInventory;

public final class PartCombinedTerminal extends PartPatternTerminal implements ICraftingTerminal, IInterfaceTerminal {

    private static final String CRAFTING_GRID_NBT = "combinedCraftingGrid";
    private static final String PATTERN_CACHE_NBT = "combinedPatternCache";
    private static final int PATTERN_CACHE_SIZE = 54;

    private final AppEngInternalInventory craftingGrid;
    private final PatternCacheInventory patternCache;
    private long interfaceUpdateRevision;

    public PartCombinedTerminal(ItemStack stack) {
        super(stack);
        craftingGrid = new AppEngInternalInventory(this, 9, 64);
        patternCache = new PatternCacheInventory(this, PATTERN_CACHE_SIZE);
    }

    @Override
    public IInventory getInventoryByName(String name) {
        if ("crafting".equals(name)) return craftingGrid;
        if ("patternCache".equals(name)) return patternCache;
        return super.getInventoryByName(name);
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        craftingGrid.readFromNBT(tag, CRAFTING_GRID_NBT);
        patternCache.readFromNBT(tag, PATTERN_CACHE_NBT);
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        craftingGrid.writeToNBT(tag, CRAFTING_GRID_NBT);
        patternCache.writeToNBT(tag, PATTERN_CACHE_NBT);
    }

    @Override
    public void getDrops(List<ItemStack> drops, boolean wrenched) {
        super.getDrops(drops, wrenched);
        for (int slot = 0; slot < craftingGrid.getSizeInventory(); slot++) {
            ItemStack stack = craftingGrid.getStackInSlot(slot);
            if (stack != null) drops.add(stack.copy());
        }
        for (int slot = 0; slot < patternCache.getSizeInventory(); slot++) {
            ItemStack stack = patternCache.getStackInSlot(slot);
            if (stack != null) drops.add(stack.copy());
        }
    }

    @Override
    public ItemStack getPrimaryGuiIcon() {
        return TerminalRegistry.combinedTerminalStack();
    }

    @Override
    public CableBusTextures getFrontBright() {
        return CableBusTextures.PartPatternTerm_Bright;
    }

    @Override
    public CableBusTextures getFrontColored() {
        return CableBusTextures.PartCraftingTerm_Colored;
    }

    @Override
    public CableBusTextures getFrontDark() {
        return CableBusTextures.PartInterfaceTerm_Dark;
    }

    @Override
    public GuiBridge getGui(EntityPlayer player) {
        return GuiBridge.GUI_PATTERN_TERMINAL;
    }

    public IInventory getCraftingGrid() {
        return craftingGrid;
    }

    public PatternCacheInventory getPatternCache() {
        return patternCache;
    }

    @Override
    public boolean needsUpdate() {
        return false;
    }

    @MENetworkEventSubscribe
    public void onNetworkBootingChanged(MENetworkBootingStatusChange event) {
        if (!event.isBooting) interfaceUpdateRevision++;
    }

    public long getInterfaceUpdateRevision() {
        return interfaceUpdateRevision;
    }

    public boolean openCombinedTerminal(EntityPlayer player) {
        if (player == null || host == null || tile == null) return false;
        if (player.worldObj.isRemote) return true;
        if (!GuiBridge.GUI_PATTERN_TERMINAL
            .hasPermissions(tile, tile.xCoord, tile.yCoord, tile.zCoord, getSide(), player)) return false;

        CombinedTerminalGuiHandler.open(player, this);
        return true;
    }

}
