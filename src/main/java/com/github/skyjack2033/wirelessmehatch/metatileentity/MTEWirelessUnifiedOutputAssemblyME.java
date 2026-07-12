package com.github.skyjack2033.wirelessmehatch.metatileentity;

import java.util.EnumSet;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;

import com.github.skyjack2033.wirelessmehatch.Config;
import com.github.skyjack2033.wirelessmehatch.api.WirelessBindable;
import com.github.skyjack2033.wirelessmehatch.api.WirelessOutputCapacityHost;
import com.github.skyjack2033.wirelessmehatch.me.WirelessLinkManager;
import com.github.skyjack2033.wirelessmehatch.me.WirelessLinkTarget;
import com.github.skyjack2033.wirelessmehatch.output.MultiblockOutputAttachment;
import com.github.skyjack2033.wirelessmehatch.output.WirelessFluidOutputDelegate;
import com.github.skyjack2033.wirelessmehatch.output.WirelessItemOutputTransaction;
import com.github.skyjack2033.wirelessmehatch.output.WirelessUnifiedOutputCore;

import appeng.api.implementations.IPowerChannelState;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.MachineSource;
import appeng.api.util.AECableType;
import appeng.api.util.DimensionalCoord;
import appeng.me.helpers.AENetworkProxy;
import appeng.me.helpers.IGridProxyable;
import gregtech.api.enums.OutputBusType;
import gregtech.api.interfaces.IOutputBusTransaction;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchOutputBus;

public class MTEWirelessUnifiedOutputAssemblyME extends MTEHatchOutputBus
    implements WirelessBindable, WirelessOutputCapacityHost, IGridProxyable, IActionHost, IPowerChannelState,
    WirelessUnifiedOutputCore.NetworkAccess, WirelessLinkManager.ProxyContext {

    private final WirelessUnifiedOutputCore outputCore;
    private final WirelessLinkManager linkManager;
    private final WirelessFluidOutputDelegate fluidDelegate;
    private final MultiblockOutputAttachment attachment;
    private BaseActionSource actionSource;

    public MTEWirelessUnifiedOutputAssemblyME(int id, String name, String regionalName) {
        super(
            id,
            name,
            regionalName,
            4,
            new String[] { "Wireless Unified Output Assembly for GTNH multiblocks",
                "Acts as both an item output bus and a fluid output hatch", "Bind with a Wireless Link Tool",
                "Outputs cached items and fluids into the bound ME network" },
            4);
        this.outputCore = new WirelessUnifiedOutputCore(
            this,
            this::markDirty,
            Config.defaultItemCapacity,
            Config.defaultFluidCapacity);
        this.linkManager = new WirelessLinkManager(this, this, this::markDirty);
        this.fluidDelegate = new WirelessFluidOutputDelegate(name, mTier, mDescriptionArray, mTextures, outputCore);
        this.attachment = new MultiblockOutputAttachment(this, fluidDelegate);
    }

    public MTEWirelessUnifiedOutputAssemblyME(String name, int tier, String[] description, ITexture[][][] textures) {
        super(name, tier, description, textures);
        this.outputCore = new WirelessUnifiedOutputCore(
            this,
            this::markDirty,
            Config.defaultItemCapacity,
            Config.defaultFluidCapacity);
        this.linkManager = new WirelessLinkManager(this, this, this::markDirty);
        this.fluidDelegate = new WirelessFluidOutputDelegate(name, tier, description, textures, outputCore);
        this.attachment = new MultiblockOutputAttachment(this, fluidDelegate);
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity tileEntity) {
        return new MTEWirelessUnifiedOutputAssemblyME(mName, mTier, mDescriptionArray, mTextures);
    }

    @Override
    public boolean bindWirelessTarget(WirelessLinkTarget target) {
        if (target == null) return false;
        linkManager.bind(target);
        return true;
    }

    @Override
    public void clearWirelessTarget() {
        linkManager.clear();
    }

    @Override
    public boolean hasWirelessTarget() {
        return linkManager.hasTarget();
    }

    @Override
    public boolean isWirelessConnected() {
        return linkManager.isConnected();
    }

    @Override
    public boolean storePartial(ItemStack stack, boolean simulate) {
        return outputCore.storeItem(stack, simulate);
    }

    @Override
    public IOutputBusTransaction createTransaction() {
        return new WirelessItemOutputTransaction(this, outputCore);
    }

    @Override
    public boolean isFiltered() {
        return false;
    }

    @Override
    public boolean isFilteredToItem(gregtech.api.util.GTUtility.ItemId itemId) {
        return false;
    }

    @Override
    public OutputBusType getBusType() {
        return OutputBusType.MECacheUnfiltered;
    }

    @Override
    public long getItemCapacity() {
        return outputCore.getItemCapacity();
    }

    @Override
    public void setItemCapacity(long capacity) {
        outputCore.setItemCapacity(capacity);
    }

    @Override
    public long getFluidCapacity() {
        return outputCore.getFluidCapacity();
    }

    @Override
    public void setFluidCapacity(long capacity) {
        outputCore.setFluidCapacity(capacity);
    }

    public long getItemCached() {
        return outputCore.getItemCached();
    }

    public long getFluidCached() {
        return outputCore.getFluidCached();
    }

    public WirelessLinkTarget getWirelessTarget() {
        return linkManager.getTarget();
    }

    public String getLastWirelessFailure() {
        return linkManager.getLastFailure();
    }

    @Override
    public boolean onRightclick(IGregTechTileEntity baseTile, EntityPlayer player) {
        ItemStack held = player.getHeldItem();
        if (held != null
            && held.getItem() instanceof com.github.skyjack2033.wirelessmehatch.item.ItemWirelessLinkTool tool) {
            return tool.bindTargetToAssembly(held, player, this);
        }
        return super.onRightclick(baseTile, player);
    }

    @Override
    public void onScrewdriverRightClick(ForgeDirection side, EntityPlayer player, float x, float y, float z,
        ItemStack tool) {
        clearWirelessTarget();
    }

    @Override
    public void onFirstTick(IGregTechTileEntity baseTile) {
        super.onFirstTick(baseTile);
        linkManager.onReady();
        fluidDelegate.setBaseMetaTileEntity(baseTile);
    }

    @Override
    public void onPostTick(IGregTechTileEntity baseTile, long tick) {
        super.onPostTick(baseTile, tick);
        if (baseTile.isServerSide()) {
            linkManager.tickCheck();
            attachment.tick(baseTile);
            outputCore.flush();
        }
    }

    @Override
    public void onRemoval() {
        attachment.detach();
        super.onRemoval();
        linkManager.invalidate();
    }

    @Override
    public void saveNBTData(NBTTagCompound tag) {
        super.saveNBTData(tag);
        linkManager.writeToNBT(tag);
        NBTTagCompound output = new NBTTagCompound();
        outputCore.writeToNBT(output);
        tag.setTag("wirelessOutput", output);
    }

    @Override
    public void loadNBTData(NBTTagCompound tag) {
        super.loadNBTData(tag);
        linkManager.readFromNBT(tag);
        if (tag.hasKey("wirelessOutput")) {
            outputCore.readFromNBT(tag.getCompoundTag("wirelessOutput"));
        } else if (tag.hasKey("itemProvider") || tag.hasKey("fluidProvider")) {
            outputCore.readLegacyProviderNBT(tag.getCompoundTag("itemProvider"), tag.getCompoundTag("fluidProvider"));
        }
    }

    @Override
    public AENetworkProxy getProxy() {
        return linkManager.getProxy();
    }

    @Override
    public IGridNode getGridNode(ForgeDirection dir) {
        return getProxy().getNode();
    }

    @Override
    public IGridNode getActionableNode() {
        return getProxy().getNode();
    }

    @Override
    public DimensionalCoord getLocation() {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base == null || base.getWorld() == null) return null;
        return new DimensionalCoord(base.getWorld(), base.getXCoord(), base.getYCoord(), base.getZCoord());
    }

    @Override
    public void gridChanged() {}

    @Override
    public void securityBreak() {}

    @Override
    public boolean isPowered() {
        return getProxy().isPowered();
    }

    @Override
    public boolean isActive() {
        return getProxy().isActive();
    }

    @Override
    public AECableType getCableConnectionType(ForgeDirection side) {
        return AECableType.NONE;
    }

    @Override
    public BaseActionSource getActionSource() {
        if (actionSource == null) {
            actionSource = new MachineSource(this);
        }
        return actionSource;
    }

    @Override
    public ItemStack getProxyItem() {
        return getStackForm(1);
    }

    @Override
    public EnumSet<ForgeDirection> getValidSides() {
        return EnumSet.noneOf(ForgeDirection.class);
    }

    @Override
    public AECableType getCableType(ForgeDirection side) {
        return AECableType.NONE;
    }
}
