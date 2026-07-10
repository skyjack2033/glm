package com.github.skyjack2033.wirelessmehatch.metatileentity;

import java.lang.reflect.Field;
import java.util.EnumSet;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;

import org.apache.logging.log4j.LogManager;

import com.github.skyjack2033.wirelessmehatch.WirelessMEHatch;
import com.github.skyjack2033.wirelessmehatch.api.IDualOutputHatch;
import com.github.skyjack2033.wirelessmehatch.api.IWirelessMEHatch;
import com.github.skyjack2033.wirelessmehatch.me.WapInteractionHandler;
import com.github.skyjack2033.wirelessmehatch.me.WirelessGridManager;

import gregtech.api.interfaces.IOutputBus;
import gregtech.api.interfaces.IOutputBusTransaction;
import gregtech.api.interfaces.IOutputHatchTransaction;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/**
 * Wireless merged output hatch for multiblocks. Connects to a bound ME network wirelessly (no cables) and routes both
 * item and fluid outputs into ME. Extends MTEHatchOutput (fluid + item inventory base). Registers its item bus adapter
 * into the parent controller's mOutputBusses at runtime via reflection (no Mixin needed - avoids multi-release jar
 * class loading crashes).
 */
public class MTEWirelessOutputHatchME extends gregtech.api.metatileentity.implementations.MTEHatchOutput
    implements IDualOutputHatch, IWirelessMEHatch, appeng.me.helpers.IGridProxyable,
    appeng.api.networking.security.IActionHost, appeng.api.implementations.IPowerChannelState {

    private static final long MAX_CACHE_CAPACITY = Long.MAX_VALUE;
    private final WirelessOutputMEProvider<appeng.api.storage.data.IAEFluidStack> fluidProvider;
    private final WirelessOutputMEProvider<appeng.api.storage.data.IAEItemStack> itemProvider;
    private final WirelessGridManager wirelessManager;
    private appeng.me.helpers.AENetworkProxy proxy;

    public MTEWirelessOutputHatchME(int aID, String aName, String aNameRegional) {
        super(
            aID,
            aName,
            aNameRegional,
            4,
            new String[] { "Wireless ME Output Hatch for GregTech multiblocks", "",
                "Combines a fluid output hatch and an item output bus into one block",
                "Outputs items and fluids directly into your ME network wirelessly",
                "No cables required, cross-dimension capable", "", "Connection method:",
                "  1. Sneak-right-click an AE2 ME Controller with a Memory Card",
                "  2. Right-click this hatch with the bound Memory Card", "  3. To unbind, screwdriver right-click", "",
                "Right-click with storage cell to adjust buffer capacity",
                "Wire-cutter right-click to toggle front-face / all-faces connection" },
            4);

        this.fluidProvider = new WirelessOutputMEProvider<>(
            this,
            appeng.api.storage.StorageChannel.FLUIDS,
            MAX_CACHE_CAPACITY);
        this.itemProvider = new WirelessOutputMEProvider<>(
            this,
            appeng.api.storage.StorageChannel.ITEMS,
            MAX_CACHE_CAPACITY);
        this.wirelessManager = new WirelessGridManager(this, this::onWirelessConnectionChanged);
    }

    public MTEWirelessOutputHatchME(String aName, int aTier, String[] aDescription,
        gregtech.api.interfaces.ITexture[][][] aTextures) {
        super(aName, aTier, aDescription, aTextures);
        this.fluidProvider = new WirelessOutputMEProvider<>(
            this,
            appeng.api.storage.StorageChannel.FLUIDS,
            MAX_CACHE_CAPACITY);
        this.itemProvider = new WirelessOutputMEProvider<>(
            this,
            appeng.api.storage.StorageChannel.ITEMS,
            MAX_CACHE_CAPACITY);
        this.wirelessManager = new WirelessGridManager(this, this::onWirelessConnectionChanged);
    }

    @Override
    public gregtech.api.metatileentity.MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEWirelessOutputHatchME(mName, mTier, mDescriptionArray, mTextures);
    }

    // ---- IWirelessMEHatch ----

    @Override
    public long getBoundWapSerial() {
        return wirelessManager.getBoundWapSerial();
    }

    @Override
    public void setBoundWapSerial(long serial) {
        wirelessManager.bind(serial);
    }

    @Override
    public boolean isWirelessConnected() {
        return wirelessManager.isConnected();
    }

    private void onWirelessConnectionChanged() {
        getBaseMetaTileEntity().markDirty();
    }

    @Override
    public boolean onRightclick(IGregTechTileEntity aBaseMetaTileEntity, EntityPlayer aPlayer) {
        if (aBaseMetaTileEntity.isServerSide()) {
            ItemStack held = aPlayer.getHeldItem();
            if (held != null && WapInteractionHandler.bindHatchFromCard(this, held, aPlayer)) {
                return true;
            }
        }
        return super.onRightclick(aBaseMetaTileEntity, aPlayer);
    }

    @Override
    public void onScrewdriverRightClick(ForgeDirection side, EntityPlayer aPlayer, float aX, float aY, float aZ,
        ItemStack aTool) {
        if (getBaseMetaTileEntity().isServerSide()) {
            WapInteractionHandler.unbindHatch(this, aPlayer);
        }
    }

    // ---- IDualOutputHatch: item side (manual, not via GT IOutputBus) ----

    @Override
    public boolean storePartial(ItemStack stack, boolean simulate) {
        if (stack == null) return false;
        return itemProvider.storePartial(appeng.util.item.AEItemStack.create(stack), simulate);
    }

    @Override
    public IOutputHatchTransaction createFluidTransaction() {
        return new WirelessOutputFluidTransaction(this);
    }

    @Override
    public IOutputBusTransaction createItemTransaction() {
        return new WirelessOutputItemTransaction(this);
    }

    // ---- IDualOutputHatch: fluid side ----

    @Override
    public int fill(ForgeDirection side, FluidStack fluid, boolean doFill) {
        if (fluid == null) return 0;
        appeng.api.storage.data.IAEFluidStack aeFluid = appeng.util.item.AEFluidStack.create(fluid);
        int amount = fluid.amount;
        boolean stored = fluidProvider.storePartial(aeFluid, !doFill);
        return stored ? amount : 0;
    }

    // ---- IAEGridProxyable (shared single proxy) ----

    @Override
    public appeng.me.helpers.AENetworkProxy getProxy() {
        if (proxy == null) {
            proxy = new appeng.me.helpers.AENetworkProxy(this, "proxy", getStackForm(1), true);
            proxy.setFlags(appeng.api.networking.GridFlags.REQUIRE_CHANNEL);
            proxy.setValidSides(EnumSet.noneOf(ForgeDirection.class)); // wireless
        }
        return proxy;
    }

    @Override
    public appeng.api.networking.IGridNode getGridNode(ForgeDirection dir) {
        return getProxy().getNode();
    }

    @Override
    public appeng.api.networking.IGridNode getActionableNode() {
        return getProxy().getNode();
    }

    @Override
    public appeng.api.util.DimensionalCoord getLocation() {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base == null || base.getWorld() == null) return null;
        return new appeng.api.util.DimensionalCoord(
            base.getWorld(),
            base.getXCoord(),
            base.getYCoord(),
            base.getZCoord());
    }

    @Override
    public void securityBreak() {}

    @Override
    public boolean isPowered() {
        return proxy != null && proxy.isPowered();
    }

    @Override
    public boolean isActive() {
        return proxy != null && proxy.isActive();
    }

    // ---- Lifecycle ----

    @Override
    public void onFirstTick(IGregTechTileEntity aBaseMetaTileEntity) {
        super.onFirstTick(aBaseMetaTileEntity);
        getProxy().onReady();
        wirelessManager.tickCheck();
        registerItemBusAdapter();
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);
        if (aBaseMetaTileEntity.isServerSide()) {
            wirelessManager.tickCheck();
            fluidProvider.flushCachedStack();
            itemProvider.flushCachedStack();
        }
    }

    @Override
    public void onRemoval() {
        super.onRemoval();
        wirelessManager.invalidate();
    }

    // ---- NBT ----

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        wirelessManager.writeToNBT(aNBT);
        NBTTagCompound fluidTag = new NBTTagCompound();
        fluidProvider.saveNBTData(fluidTag);
        aNBT.setTag("fluidProvider", fluidTag);
        NBTTagCompound itemTag = new NBTTagCompound();
        itemProvider.saveNBTData(itemTag);
        aNBT.setTag("itemProvider", itemTag);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        wirelessManager.readFromNBT(aNBT);
        if (aNBT.hasKey("fluidProvider")) {
            fluidProvider.loadNBTData(aNBT.getCompoundTag("fluidProvider"));
        }
        if (aNBT.hasKey("itemProvider")) {
            itemProvider.loadNBTData(aNBT.getCompoundTag("itemProvider"));
        }
    }

    /**
     * Register item bus adapter into parent multi-block's mOutputBusses via reflection (without Mixin).
     */
    private void registerItemBusAdapter() {
        try {
            IGregTechTileEntity te = getBaseMetaTileEntity();
            if (te == null || te.getWorld() == null) return;
            World world = te.getWorld();
            int x = te.getXCoord(), y = te.getYCoord(), z = te.getZCoord();

            for (int dx = -6; dx <= 6; dx++) {
                for (int dy = -6; dy <= 6; dy++) {
                    for (int dz = -6; dz <= 6; dz++) {
                        TileEntity neighbor = world.getTileEntity(x + dx, y + dy, z + dz);
                        if (neighbor instanceof IGregTechTileEntity neighborBase && neighborBase
                            .getMetaTileEntity() instanceof gregtech.api.metatileentity.implementations.MTEMultiBlockBase controller) {

                            Field f = gregtech.api.metatileentity.implementations.MTEMultiBlockBase.class
                                .getDeclaredField("mOutputBusses");
                            f.setAccessible(true);
                            @SuppressWarnings("unchecked")
                            List<IOutputBus> busses = (List<IOutputBus>) f.get(controller);

                            WirelessOutputBusAdapter adapter = new WirelessOutputBusAdapter(this);
                            if (!busses.contains(adapter)) {
                                busses.add(adapter);
                            }
                            WirelessMEHatch.LOG.info(
                                "Registered item bus adapter into parent multiblock: {}",
                                controller.getLocalName());
                            return;
                        }
                    }
                }
            }
        } catch (Exception e) {
            LogManager.getLogger("WirelessMEHatch")
                .warn("Failed to register item bus adapter via reflection: {}", e.getMessage());
        }
    }

    // ---- Provider access ----

    WirelessOutputMEProvider<appeng.api.storage.data.IAEFluidStack> getFluidProvider() {
        return fluidProvider;
    }

    WirelessOutputMEProvider<appeng.api.storage.data.IAEItemStack> getItemProvider() {
        return itemProvider;
    }

    appeng.api.networking.security.BaseActionSource getActionSource() {
        return new appeng.api.networking.security.MachineSource(this);
    }

    ItemStack getCellStack() {
        return mInventory[0];
    }
}
