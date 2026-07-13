package com.github.skyjack2033.wirelessmehatch.metatileentity;

import java.util.EnumSet;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;

import com.github.skyjack2033.wirelessmehatch.Config;
import com.github.skyjack2033.wirelessmehatch.api.SharedFluidOutputStore;
import com.github.skyjack2033.wirelessmehatch.api.WirelessBindable;
import com.github.skyjack2033.wirelessmehatch.api.WirelessDualRoleOutput;
import com.github.skyjack2033.wirelessmehatch.api.WirelessOutputCapacityHost;
import com.github.skyjack2033.wirelessmehatch.item.ItemWirelessLinkTool;
import com.github.skyjack2033.wirelessmehatch.me.WirelessLinkManager;
import com.github.skyjack2033.wirelessmehatch.me.WirelessLinkTarget;
import com.github.skyjack2033.wirelessmehatch.output.WirelessItemOutputTransaction;
import com.github.skyjack2033.wirelessmehatch.output.WirelessUnifiedOutputCore;
import com.gtnewhorizons.modularui.common.fluid.FluidStackTank;

import appeng.api.implementations.IPowerChannelState;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.MachineSource;
import appeng.api.util.AECableType;
import appeng.api.util.DimensionalCoord;
import appeng.me.helpers.AENetworkProxy;
import appeng.me.helpers.IGridProxyable;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.OutputBusType;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.IOutputBus;
import gregtech.api.interfaces.IOutputBusTransaction;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchOutput;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.GTUtility;

public class MTEWirelessUnifiedOutputAssemblyME extends MTEHatchOutput implements IOutputBus, WirelessDualRoleOutput,
    SharedFluidOutputStore, WirelessBindable, WirelessOutputCapacityHost, IGridProxyable, IActionHost,
    IPowerChannelState, WirelessUnifiedOutputCore.NetworkAccess, WirelessLinkManager.ProxyContext {

    private final WirelessUnifiedOutputCore outputCore;
    private final FluidStackTank aggregateFluidTank;
    private final WirelessLinkManager linkManager;
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
        this.aggregateFluidTank = createAggregateFluidTank();
        this.linkManager = new WirelessLinkManager(this, this, this::markDirty);
    }

    public MTEWirelessUnifiedOutputAssemblyME(String name, int tier, String[] description, ITexture[][][] textures) {
        super(name, tier, description, textures);
        this.outputCore = new WirelessUnifiedOutputCore(
            this,
            this::markDirty,
            Config.defaultItemCapacity,
            Config.defaultFluidCapacity);
        this.aggregateFluidTank = createAggregateFluidTank();
        this.linkManager = new WirelessLinkManager(this, this, this::markDirty);
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity tileEntity) {
        return new MTEWirelessUnifiedOutputAssemblyME(mName, mTier, mDescriptionArray, mTextures);
    }

    @Override
    public ITexture[] getTexturesActive(ITexture baseTexture) {
        return new ITexture[] { baseTexture, TextureFactory.of(Textures.BlockIcons.OVERLAY_ME_HATCH) };
    }

    @Override
    public ITexture[] getTexturesInactive(ITexture baseTexture) {
        return new ITexture[] { baseTexture, TextureFactory.of(Textures.BlockIcons.OVERLAY_ME_HATCH) };
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
    public boolean isFiltered() {
        return false;
    }

    @Override
    public boolean isFilteredToItem(GTUtility.ItemId itemId) {
        return false;
    }

    @Override
    public OutputBusType getBusType() {
        return OutputBusType.MECacheUnfiltered;
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
    public int fill(FluidStack fluid, boolean doFill) {
        return outputCore.storeFluid(fluid, !doFill);
    }

    @Override
    public int fill(ForgeDirection side, FluidStack fluid, boolean doFill) {
        return 0;
    }

    @Override
    public FluidStack drain(int maxDrain, boolean doDrain) {
        return null;
    }

    @Override
    public FluidStack drain(ForgeDirection side, int maxDrain, boolean doDrain) {
        return null;
    }

    @Override
    public FluidStack drain(ForgeDirection side, FluidStack resource, boolean doDrain) {
        return null;
    }

    @Override
    public FluidStack drain(ForgeDirection side, FluidStack resource, int maxDrain, boolean doDrain) {
        return null;
    }

    @Override
    public boolean canFill(ForgeDirection side, Fluid fluid) {
        return false;
    }

    @Override
    public boolean canDrain(ForgeDirection side, Fluid fluid) {
        return false;
    }

    @Override
    public boolean canStoreFluid(FluidStack fluid) {
        return fluid != null && fluid.getFluid() != null && fluid.amount > 0 && getSharedFluidRemainingCapacity() > 0L;
    }

    @Override
    public boolean isEmptyAndAcceptsAnyFluid() {
        return outputCore.getFluidCached() == 0L && outputCore.getFluidCapacity() > 0L;
    }

    @Override
    public boolean doesFillContainers() {
        return false;
    }

    @Override
    public boolean doesEmptyContainers() {
        return false;
    }

    @Override
    public boolean canTankBeFilled() {
        return false;
    }

    @Override
    public boolean canTankBeEmptied() {
        return false;
    }

    @Override
    public boolean isFluidInputAllowed(FluidStack fluid) {
        return false;
    }

    @Override
    public boolean isLiquidOutput(ForgeDirection side) {
        return false;
    }

    @Override
    public boolean isValidSlot(int index) {
        return false;
    }

    @Override
    public boolean allowPutStack(IGregTechTileEntity baseTile, int index, ForgeDirection side, ItemStack stack) {
        return false;
    }

    @Override
    public boolean allowPullStack(IGregTechTileEntity baseTile, int index, ForgeDirection side, ItemStack stack) {
        return false;
    }

    @Override
    public FluidStackTank getFluidTank() {
        return aggregateFluidTank;
    }

    @Override
    public FluidStack getFillableStack() {
        mFluid = null;
        return null;
    }

    @Override
    public FluidStack setFillableStack(FluidStack fluid) {
        mFluid = null;
        return null;
    }

    @Override
    public FluidStack getDrainableStack() {
        mFluid = null;
        return null;
    }

    @Override
    public FluidStack setDrainableStack(FluidStack fluid) {
        mFluid = null;
        return null;
    }

    @Override
    public FluidStack getFluid() {
        return null;
    }

    @Override
    public int getFluidAmount() {
        return boundedInt(outputCore.getFluidCached());
    }

    @Override
    public FluidTankInfo[] getTankInfo(ForgeDirection side) {
        return GTValues.emptyFluidTankInfo;
    }

    @Override
    public int getCapacity() {
        return boundedInt(outputCore.getFluidCapacity());
    }

    @Override
    public long getSharedFluidRemainingCapacity() {
        long capacity = outputCore.getFluidCapacity();
        long cached = outputCore.getFluidCached();
        return cached >= capacity ? 0L : capacity - cached;
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
        outputCore.setFluidCapacity(Math.max(capacity, outputCore.getFluidCached()));
    }

    public long getItemCached() {
        return outputCore.getItemCached();
    }

    public long getFluidCached() {
        return outputCore.getFluidCached();
    }

    @Override
    public String[] getInfoData() {
        return new String[] { "Item cached: " + outputCore.getItemCached() + " / " + outputCore.getItemCapacity(),
            "Fluid cached: " + outputCore.getFluidCached() + " / " + outputCore.getFluidCapacity(),
            "Wireless target: " + (hasWirelessTarget() ? "Bound" : "Unbound"),
            "ME connection: " + (isWirelessConnected() ? "Connected" : "Disconnected") };
    }

    public WirelessLinkTarget getWirelessTarget() {
        return linkManager.getTarget();
    }

    public String getLastWirelessFailure() {
        return linkManager.getLastFailure();
    }

    @Override
    public boolean onRightclick(IGregTechTileEntity baseTile, EntityPlayer player) {
        if (bindFromHeldTool(player)) return true;
        return true;
    }

    @Override
    public boolean onRightclick(IGregTechTileEntity baseTile, EntityPlayer player, ForgeDirection side, float x,
        float y, float z) {
        if (bindFromHeldTool(player)) return true;
        return true;
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
    }

    static boolean shouldShowConnectedTexture(boolean wirelessConnected, boolean proxyActive) {
        return wirelessConnected && proxyActive;
    }

    @Override
    public void onPostTick(IGregTechTileEntity baseTile, long tick) {
        super.onPostTick(baseTile, tick);
        if (baseTile.isServerSide()) {
            linkManager.tickCheck();
            if (tick % 20L == 0L) {
                baseTile.setActive(shouldShowConnectedTexture(isWirelessConnected(), getProxy().isActive()));
            }
            outputCore.flush();
        }
    }

    @Override
    public void onRemoval() {
        super.onRemoval();
        linkManager.invalidate();
    }

    @Override
    public void saveNBTData(NBTTagCompound tag) {
        mFluid = null;
        super.saveNBTData(tag);
        tag.removeTag("mFluid");
        linkManager.writeToNBT(tag);
        NBTTagCompound output = new NBTTagCompound();
        outputCore.writeToNBT(output);
        tag.setTag("wirelessOutput", output);
    }

    @Override
    public void loadNBTData(NBTTagCompound tag) {
        super.loadNBTData(tag);
        FluidStack inheritedFluid = mFluid;
        mFluid = null;
        linkManager.readFromNBT(tag);
        loadOutputData(tag, inheritedFluid);
    }

    void loadOutputData(NBTTagCompound tag, FluidStack inheritedFluid) {
        mFluid = null;
        if (tag.hasKey("wirelessOutput")) {
            outputCore.readFromNBT(tag.getCompoundTag("wirelessOutput"));
            return;
        }

        boolean hasLegacyItemProvider = tag.hasKey("itemProvider");
        boolean hasLegacyFluidCache = hasLegacyFluidCache(tag);
        if (hasLegacyItemProvider || tag.hasKey("fluidProvider")) {
            outputCore.readLegacyProviderNBT(tag.getCompoundTag("itemProvider"), tag.getCompoundTag("fluidProvider"));
        }
        if (!hasLegacyFluidCache && inheritedFluid != null
            && inheritedFluid.getFluid() != null
            && inheritedFluid.amount > 0) {
            migrateInheritedFluid(inheritedFluid);
        }
    }

    private void migrateInheritedFluid(FluidStack inheritedFluid) {
        long configuredCapacity = outputCore.getFluidCapacity();
        long requiredCapacity = saturatingAdd(outputCore.getFluidCached(), inheritedFluid.amount);
        if (requiredCapacity > configuredCapacity) {
            outputCore.setFluidCapacity(requiredCapacity);
        }
        outputCore.storeFluid(inheritedFluid, false);
        outputCore.setFluidCapacity(configuredCapacity);
    }

    private static boolean hasLegacyFluidCache(NBTTagCompound tag) {
        if (!tag.hasKey("fluidProvider")) return false;
        NBTTagCompound provider = tag.getCompoundTag("fluidProvider");
        return provider.hasKey("cache");
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

    private FluidStackTank createAggregateFluidTank() {
        return new FluidStackTank(() -> null, fluid -> {}, this::getCapacity) {

            @Override
            public int fill(FluidStack resource, boolean doFill) {
                return 0;
            }

            @Override
            public FluidStack drain(int maxDrain, boolean doDrain) {
                return null;
            }

            @Override
            public FluidStack getFluid() {
                return null;
            }

            @Override
            public int getFluidAmount() {
                return MTEWirelessUnifiedOutputAssemblyME.this.getFluidAmount();
            }

            @Override
            public int getCapacity() {
                return MTEWirelessUnifiedOutputAssemblyME.this.getCapacity();
            }

            @Override
            public FluidTankInfo getInfo() {
                return new FluidTankInfo(null, getCapacity());
            }

            @Override
            public int getCanFillAmount() {
                return 0;
            }

            @Override
            public void validateFluid() {}
        };
    }

    private static int boundedInt(long value) {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, value));
    }

    private static long saturatingAdd(long left, long right) {
        if (right > 0L && left > Long.MAX_VALUE - right) return Long.MAX_VALUE;
        return Math.max(0L, left + right);
    }

    private boolean bindFromHeldTool(EntityPlayer player) {
        ItemStack held = player.getHeldItem();
        if (held != null && held.getItem() instanceof ItemWirelessLinkTool tool) {
            return tool.bindTargetToAssembly(held, player, this);
        }
        return false;
    }
}
