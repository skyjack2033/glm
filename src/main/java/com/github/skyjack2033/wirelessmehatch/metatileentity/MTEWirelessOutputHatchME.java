package com.github.skyjack2033.wirelessmehatch.metatileentity;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;

import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.github.skyjack2033.wirelessmehatch.api.IDualOutputHatch;
import com.github.skyjack2033.wirelessmehatch.api.IWirelessMEHatch;
import com.github.skyjack2033.wirelessmehatch.gui.MTEWirelessOutputHatchMEGui;
import com.github.skyjack2033.wirelessmehatch.me.PlayerIdResolver;
import com.github.skyjack2033.wirelessmehatch.me.WapInteractionHandler;
import com.github.skyjack2033.wirelessmehatch.me.WirelessGridManager;

import appeng.api.AEApi;
import appeng.api.implementations.IPowerChannelState;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.MachineSource;
import appeng.api.storage.ISaveProvider;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.util.DimensionalCoord;
import appeng.me.GridAccessException;
import appeng.me.helpers.AENetworkProxy;
import appeng.me.helpers.IGridProxyable;
import appeng.util.item.AEFluidStack;
import appeng.util.item.AEItemStack;
import gregtech.api.enums.ItemList;
import gregtech.api.interfaces.IOutputBusTransaction;
import gregtech.api.interfaces.IOutputHatchTransaction;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchOutput;
import gregtech.api.util.GTUtility;
import gregtech.common.tileentities.machines.outputme.base.MTEHatchOutputMEBase;

/**
 * Wireless merged output hatch. Extends {@link MTEHatchOutput} (fluid base) and adds item-bus output via a second
 * {@link MTEHatchOutputMEBase} provider. Both providers share a single {@link AENetworkProxy} (the fluid provider owns
 * it; the item provider's overridden {@code getProxy()} returns the fluid provider's proxy). The hatch connects to a
 * bound ME network wirelessly through {@link WirelessGridManager}.
 *
 * <p>
 * The class mirrors GT's {@code MTEHatchOutputME} (fluid side) and {@code MTEHatchOutputBusME} (item side), merging
 * both into one tile. Recognised by multiblock controllers as an {@link IDualOutputHatch} (via the
 * {@code MTEMultiBlockBaseMixin} from Task 7) so it receives BOTH fluid output (via {@code fill}) and item output (via
 * {@code storePartial}).
 */
public class MTEWirelessOutputHatchME extends MTEHatchOutput
    implements IDualOutputHatch, IWirelessMEHatch, IGridProxyable, IActionHost, IPowerChannelState {

    /**
     * Note: GT's {@code MTEHatchOutputME} also implements {@code ICellContainer} (which extends AE2's
     * {@code ISaveProvider}) and {@code IActionHost} (via {@code IGridProxyable extends IActionHost}). This hatch does
     * not need full cell-container behaviour, so {@code getISaveProvider()} returns {@code null} (the cell-inventory
     * helper used during recipe-check works without a save callback) and {@code IActionHost} is supplied transitively
     * by
     * {@link IGridProxyable}.
     */

    /** Default ME cache capacity - effectively unlimited so the wireless hatch buffers any recipe output. */
    private static final long MAX_CACHE_CAPACITY = Long.MAX_VALUE;

    private static final int TIER = 4; // HV, same as MTEHatchOutputME

    private final MTEHatchOutputMEBase<IAEFluidStack> fluidProvider;
    private final MTEHatchOutputMEBase<IAEItemStack> itemProvider;
    private final WirelessGridManager wirelessManager;

    private BaseActionSource actionSource;
    private EntityPlayer lastClickedPlayer;

    public MTEWirelessOutputHatchME(int aID, String aName, String aNameRegional) {
        super(
            aID,
            aName,
            aNameRegional,
            TIER,
            new String[] { "Wireless ME Output Hatch for Multiblocks",
                "Merges fluid output hatch and item output bus into one block",
                "Connects to your ME network wirelessly - no cables needed",
                "Stores items and fluids directly into the bound ME network",
                "Capacity: Unlimited (insert a storage cell for partitioning)", "How to connect:",
                "  1. Sneak-right-click an AE2 ME Controller with a Memory Card",
                "  2. Right-click this hatch with the bound Memory Card", "  3. Screwdriver right-click to unbind",
                "Compatible with all GregTech multiblock machines" },
            1);
        this.fluidProvider = new OutputProvider<IAEFluidStack>(createFluidEnvironment(), MAX_CACHE_CAPACITY);
        this.itemProvider = new OutputProvider<IAEItemStack>(createItemEnvironment(), MAX_CACHE_CAPACITY);
        ((OutputProvider<IAEItemStack>) this.itemProvider).setProxyOwner(this.fluidProvider);
        this.wirelessManager = new WirelessGridManager(this, this::onWirelessConnectionChanged);
    }

    public MTEWirelessOutputHatchME(String aName, int aTier, String[] aDescription,
        gregtech.api.interfaces.ITexture[][][] aTextures) {
        super(aName, aTier, aDescription, aTextures);
        this.fluidProvider = new OutputProvider<IAEFluidStack>(createFluidEnvironment(), MAX_CACHE_CAPACITY);
        this.itemProvider = new OutputProvider<IAEItemStack>(createItemEnvironment(), MAX_CACHE_CAPACITY);
        ((OutputProvider<IAEItemStack>) this.itemProvider).setProxyOwner(this.fluidProvider);
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

    /**
     * {@code WirelessGridManager} coalesces its internal destroy→establish callbacks into a single notification per net
     * state change (see {@code suppressCallback}), so this fires at most once per bind/reconnect. {@code markDirty()}
     * is
     * itself idempotent, so even a stray double-fire is safe.
     */
    private void onWirelessConnectionChanged() {
        markDirty();
    }

    // ---- Right-click binding (Memory Card) ----

    @Override
    public boolean onRightclick(IGregTechTileEntity aBaseMetaTileEntity, EntityPlayer aPlayer) {
        // Memory Card binding only on server side - on client, always fall through to super so GUI opens.
        if (aBaseMetaTileEntity.isServerSide()) {
            ItemStack held = aPlayer.getHeldItem();
            if (held != null && WapInteractionHandler.bindHatchFromCard(this, held, aPlayer)) {
                return true;
            }
        }
        lastClickedPlayer = aPlayer;
        return super.onRightclick(aBaseMetaTileEntity, aPlayer);
    }

    /**
     * Screwdriver-right-click unbinds the hatch (matches the MemoryCardHandler flow). Sneaking delegates to the fluid
     * provider's screwdriver handler so the cache/check-mode toggle remains accessible.
     */
    @Override
    public void onScrewdriverRightClick(ForgeDirection side, EntityPlayer aPlayer, float aX, float aY, float aZ,
        ItemStack aTool) {
        if (aPlayer.isSneaking()) {
            fluidProvider.onScrewdriverRightClick(side, aPlayer, aX, aY, aZ, aTool);
        } else {
            WapInteractionHandler.unbindHatch(this, aPlayer);
        }
    }

    // ---- IDualOutputHatch: item side ----

    /**
     * Store an item stack into the item provider. Mirrors {@code MTEHatchOutputBusME.storePartial}: the passed stack is
     * reduced to the leftover that could not be stored.
     *
     * @return true if the entire stack was stored (stack is now empty).
     */
    @Override
    public boolean storePartial(ItemStack stack, boolean simulate) {
        if (stack == null) return false;
        IAEItemStack aeStack = AEItemStack.create(stack);
        if (aeStack == null) return false;
        itemProvider.storePartial(aeStack, simulate);
        // storePartial mutates the AE stack's size to the remainder; mirror GT by writing it back onto the ItemStack.
        stack.stackSize = (int) aeStack.getStackSize();
        return stack.stackSize == 0;
    }

    @Override
    public IOutputHatchTransaction createFluidTransaction() {
        return new WirelessOutputFluidTransaction(this);
    }

    @Override
    public IOutputBusTransaction createItemTransaction() {
        return new WirelessOutputItemTransaction(this);
    }

    /** @return the fluid provider, used by the transaction classes. */
    MTEHatchOutputMEBase<IAEFluidStack> getFluidProvider() {
        return fluidProvider;
    }

    /** @return the item provider, used by the transaction classes. */
    MTEHatchOutputMEBase<IAEItemStack> getItemProvider() {
        return itemProvider;
    }

    /**
     * @return the cell stack in slot 0 (shared between fluid and item providers for now). Used by the transaction
     *         classes when recipe-checking against a storage cell.
     */
    ItemStack getCellStack() {
        return mInventory[0];
    }

    // ---- GUI accessors (used by MTEWirelessOutputHatchMEGui) ----

    /**
     * @return the fluid provider's ME priority. Both providers share one {@link AENetworkProxy} and therefore one grid
     *         membership, so a single priority value is sufficient for the GUI.
     */
    public int getPriority() {
        return fluidProvider.getPriority();
    }

    /** @see #getPriority() */
    public void setPriority(int priority) {
        fluidProvider.setPriority(priority);
    }

    /**
     * @return the fluid provider's cache/check mode flag ({@code MTEHatchOutputMEBase.getCacheMode()}).
     */
    public boolean isCacheMode() {
        return fluidProvider.getCacheMode();
    }

    /** @see #isCacheMode() */
    public void setCacheMode(boolean cacheMode) {
        fluidProvider.setCacheMode(cacheMode);
    }

    /**
     * @return a cached item-stack count summary for the GUI (sum of the fluid + item caches). Computed on demand.
     */
    public long getCachedItemCount() {
        return fluidProvider.getCachedAmount() + itemProvider.getCachedAmount();
    }

    // ---- Fluid output (mirrors MTEHatchOutputME.fill) ----

    /**
     * Side-less fill as used by GT's recipe output dispatch. Converts the {@link FluidStack} to an
     * {@link IAEFluidStack}
     * and pushes it through the fluid provider, returning the amount actually stored.
     */
    @Override
    public int fill(FluidStack aFluid, boolean doFill) {
        if (aFluid == null) return 0;
        AEFluidStack aeFluid = AEFluidStack.create(aFluid);
        fluidProvider.storePartial(aeFluid, !doFill);
        return aFluid.amount - (int) aeFluid.getStackSize();
    }

    @Override
    public int fill_default(ForgeDirection aSide, FluidStack aFluid, boolean doFill) {
        return fill(aFluid, doFill);
    }

    // ---- IGridProxyable / IPowerChannelState (single shared proxy) ----

    /**
     * Single shared {@link AENetworkProxy}. The fluid provider owns and lazily creates it (via
     * {@link MTEHatchOutputMEBase#getProxy()}); the item provider and this host both delegate to it. Valid sides follow
     * GT's default ({@link MTEHatchOutputMEBase#updateValidGridProxySides()}), so a physical cable on the front face
     * can
     * also connect - the wireless link from {@link WirelessGridManager} is established independently of valid sides.
     */
    @Override
    public AENetworkProxy getProxy() {
        return fluidProvider.getProxy();
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
        return new DimensionalCoord(base.getWorld(), base.getXCoord(), base.getYCoord(), base.getZCoord());
    }

    @Override
    public void securityBreak() {
        // No item to break on grid security violation - matches MTEHatchOutputME.
    }

    @Override
    public boolean isPowered() {
        return getProxy() != null && getProxy().isPowered();
    }

    @Override
    public boolean isActive() {
        return getProxy() != null && getProxy().isActive();
    }

    // ---- Lifecycle ----

    @Override
    public void onFirstTick(IGregTechTileEntity aBaseMetaTileEntity) {
        super.onFirstTick(aBaseMetaTileEntity);
        getProxy().onReady();
        // I2: stamp the owner's AE2 player ID onto the grid node so the hatch passes Security Terminal checks. Done
        // after
        // onReady() so the node exists.
        PlayerIdResolver
            .applyOwnerPlayerId(this, aBaseMetaTileEntity.getOwnerUuid(), aBaseMetaTileEntity.getOwnerName());
        fluidProvider.updateState();
        wirelessManager.tickCheck();
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);
        if (aBaseMetaTileEntity.isServerSide()) {
            wirelessManager.tickCheck();
            fluidProvider.onPostTick(aBaseMetaTileEntity, aTick);
            itemProvider.onPostTick(aBaseMetaTileEntity, aTick);
        }
    }

    @Override
    public void onRemoval() {
        super.onRemoval();
        wirelessManager.invalidate();
    }

    @Override
    public void inValidate() {
        super.inValidate();
        wirelessManager.invalidate();
    }

    // ---- NBT ----

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        wirelessManager.writeToNBT(aNBT);
        // Each provider persists into its own sub-compound so the keys (cache/myPriority/...) do not collide.
        NBTTagCompound fluidTag = new NBTTagCompound();
        fluidProvider.saveNBTData(fluidTag);
        aNBT.setTag("fluidProvider", fluidTag);
        NBTTagCompound itemTag = new NBTTagCompound();
        itemProvider.saveNBTData(itemTag);
        // I3: both providers share a single AENetworkProxy, and MTEHatchOutputMEBase.saveNBTData writes that proxy's
        // NBT (under the "proxy" key) into whichever tag it is handed. Persisting it in BOTH sub-compounds is
        // redundant and lets the item provider's later load clobber the fluid provider's proxy state. The fluid
        // provider owns the proxy, so strip the duplicate proxy tag from the item provider's sub-compound.
        itemTag.removeTag("proxy");
        aNBT.setTag("itemProvider", itemTag);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        wirelessManager.readFromNBT(aNBT);
        // Load the owning (fluid) provider first - it restores the shared proxy's NBT. The item provider's sub-compound
        // has its proxy tag stripped on save (see saveNBTData), so it only restores its own cache/priority state.
        fluidProvider.loadNBTData(aNBT.getCompoundTag("fluidProvider"));
        itemProvider.loadNBTData(aNBT.getCompoundTag("itemProvider"));
        restoreLegacyCachedStacks(aNBT);
    }

    /**
     * Backwards-compat: older saves may have a top-level {@code cachedFluids} list (from the MTEHatchOutputME layout).
     * Re-add those entries to the fluid provider cache.
     */
    private void restoreLegacyCachedStacks(NBTTagCompound aNBT) {
        if (aNBT.hasKey("cachedFluids")) {
            NBTTagList list = aNBT.getTagList("cachedFluids", 10);
            for (int i = 0; i < list.tagCount(); i++) {
                NBTTagCompound entry = list.getCompoundTagAt(i);
                NBTTagCompound fluidTag = entry.getCompoundTag("fluidStack");
                IAEFluidStack stack = AEApi.instance()
                    .storage()
                    .createFluidStack(GTUtility.loadFluid(fluidTag));
                if (stack != null) {
                    stack.setStackSize(entry.getLong("size"));
                    fluidProvider.addToCache(stack);
                }
            }
        }
    }

    // ---- IActionHost / ISaveProvider / Environment support ----

    /** @return the cached action source for ME storage operations (matches MTEHatchOutputME). */
    BaseActionSource getActionSource() {
        if (actionSource == null) {
            actionSource = new MachineSource(this);
        }
        return actionSource;
    }

    EntityPlayer getLastClickedPlayer() {
        return lastClickedPlayer;
    }

    // ---- Environment<IAEFluidStack> ----

    private MTEHatchOutputMEBase.Environment<IAEFluidStack> createFluidEnvironment() {
        return new MTEHatchOutputMEBase.Environment<>() {

            @Override
            public IGregTechTileEntity getBaseMetaTileEntity() {
                return MTEWirelessOutputHatchME.this.getBaseMetaTileEntity();
            }

            @Override
            public IGridProxyable getIGridProxyable() {
                return MTEWirelessOutputHatchME.this;
            }

            @Override
            public StorageChannel getChannel() {
                return StorageChannel.FLUIDS;
            }

            @Override
            public ItemStack getCellStack() {
                return mInventory[0];
            }

            @Override
            public ISaveProvider getISaveProvider() {
                // No separate cell-save callback needed for a wireless buffer hatch.
                return null;
            }

            @Override
            public BaseActionSource getActionSource() {
                return MTEWirelessOutputHatchME.this.getActionSource();
            }

            @Override
            public EntityPlayer getLastClickedPlayer() {
                return lastClickedPlayer;
            }

            @Override
            public appeng.api.storage.IMEInventory<IAEFluidStack> getNetworkInvtory() throws GridAccessException {
                return getProxy().getStorage()
                    .getFluidInventory();
            }

            @Override
            public NBTTagCompound saveStackToNBT(IAEFluidStack stack) {
                NBTTagCompound tag = new NBTTagCompound();
                NBTTagCompound fluidTag = new NBTTagCompound();
                stack.getFluidStack()
                    .writeToNBT(fluidTag);
                tag.setTag("fluidStack", fluidTag);
                tag.setLong("size", stack.getStackSize());
                return tag;
            }

            @Override
            public IAEFluidStack loadStackFromNBT(NBTTagCompound tag) {
                FluidStack fluid = GTUtility.loadFluid(tag.getCompoundTag("fluidStack"));
                if (fluid == null) return null;
                IAEFluidStack stack = AEFluidStack.create(fluid);
                stack.setStackSize(tag.getLong("size"));
                return stack;
            }

            @Override
            public String getCopiedDataIdentifier(EntityPlayer player) {
                return "wirelessOutputHatchME.fluid";
            }

            @Override
            public byte getColor() {
                return MTEWirelessOutputHatchME.this.getColor();
            }

            @Override
            public ItemStack getVisual() {
                return ItemList.Hatch_Output_ME.get(1);
            }

            @Override
            public void dispatchMarkDirty() {
                markDirty();
            }

            @Override
            public MTEHatchOutputMEBase<IAEFluidStack> getProvider() {
                return fluidProvider;
            }

            @Override
            public String getEnableKey() {
                return "GT5U.hatch.fluid.filter.enable";
            }

            @Override
            public String getDisableKey() {
                return "GT5U.hatch.fluid.filter.disable";
            }
        };
    }

    // ---- Environment<IAEItemStack> ----

    private MTEHatchOutputMEBase.Environment<IAEItemStack> createItemEnvironment() {
        return new MTEHatchOutputMEBase.Environment<>() {

            @Override
            public IGregTechTileEntity getBaseMetaTileEntity() {
                return MTEWirelessOutputHatchME.this.getBaseMetaTileEntity();
            }

            @Override
            public IGridProxyable getIGridProxyable() {
                return MTEWirelessOutputHatchME.this;
            }

            @Override
            public StorageChannel getChannel() {
                return StorageChannel.ITEMS;
            }

            @Override
            public ItemStack getCellStack() {
                // Reuse the same single cell slot as the fluid side for simplicity (GUI in Task 9 can split this).
                return mInventory[0];
            }

            @Override
            public ISaveProvider getISaveProvider() {
                // No separate cell-save callback needed for a wireless buffer hatch.
                return null;
            }

            @Override
            public BaseActionSource getActionSource() {
                return MTEWirelessOutputHatchME.this.getActionSource();
            }

            @Override
            public EntityPlayer getLastClickedPlayer() {
                return lastClickedPlayer;
            }

            @Override
            public appeng.api.storage.IMEInventory<IAEItemStack> getNetworkInvtory() throws GridAccessException {
                return getProxy().getStorage()
                    .getItemInventory();
            }

            @Override
            public NBTTagCompound saveStackToNBT(IAEItemStack stack) {
                NBTTagCompound tag = new NBTTagCompound();
                tag.setTag("itemStack", GTUtility.saveItem(stack.getItemStack()));
                tag.setLong("size", stack.getStackSize());
                return tag;
            }

            @Override
            public IAEItemStack loadStackFromNBT(NBTTagCompound tag) {
                ItemStack item = GTUtility.loadItem(tag.getCompoundTag("itemStack"));
                if (item == null) return null;
                IAEItemStack stack = AEItemStack.create(item);
                stack.setStackSize(tag.getLong("size"));
                return stack;
            }

            @Override
            public String getCopiedDataIdentifier(EntityPlayer player) {
                return "wirelessOutputHatchME.item";
            }

            @Override
            public byte getColor() {
                return MTEWirelessOutputHatchME.this.getColor();
            }

            @Override
            public ItemStack getVisual() {
                return ItemList.Hatch_Output_Bus_ME.get(1);
            }

            @Override
            public void dispatchMarkDirty() {
                markDirty();
            }

            @Override
            public MTEHatchOutputMEBase<IAEItemStack> getProvider() {
                return itemProvider;
            }

            @Override
            public String getEnableKey() {
                return "GT5U.hatch.item.filter.enable";
            }

            @Override
            public String getDisableKey() {
                return "GT5U.hatch.item.filter.disable";
            }
        };
    }

    // ---- Textures (ME overlay, matching MTEHatchOutputME) ----

    @Override
    public gregtech.api.interfaces.ITexture[] getTexturesActive(gregtech.api.interfaces.ITexture aBaseTexture) {
        return new gregtech.api.interfaces.ITexture[] { aBaseTexture, gregtech.api.render.TextureFactory
            .of(gregtech.api.enums.Textures.BlockIcons.OVERLAY_ME_FLUID_HATCH_ACTIVE) };
    }

    @Override
    public gregtech.api.interfaces.ITexture[] getTexturesInactive(gregtech.api.interfaces.ITexture aBaseTexture) {
        return new gregtech.api.interfaces.ITexture[] { aBaseTexture,
            gregtech.api.render.TextureFactory.of(gregtech.api.enums.Textures.BlockIcons.OVERLAY_ME_FLUID_HATCH) };
    }

    @Override
    public byte getTierForStructure() {
        return (byte) (gregtech.api.enums.GTValues.V.length - 2);
    }

    // ---- GUI ----

    @Override
    protected boolean useMui2() {
        return true;
    }

    @Override
    public ModularPanel buildUI(PosGuiData guiData, PanelSyncManager syncManager, UISettings uiSettings) {
        return new MTEWirelessOutputHatchMEGui(this).build(guiData, syncManager, uiSettings);
    }

    /**
     * Concrete subclass of the abstract {@link MTEHatchOutputMEBase} used by both the fluid and item providers.
     *
     * <p>
     * {@link MTEHatchOutputMEBase} is declared {@code abstract} (GT instantiates it only via an anonymous subclass), so
     * this class provides the concrete type. It also implements shared-proxy behaviour: when {@code owner} is null (the
     * fluid provider) it creates and owns its own {@link AENetworkProxy} via {@code super.getProxy()}; when
     * {@code owner}
     * is set (the item provider) {@link #getProxy()} returns the owner's proxy so both providers share a single grid
     * node - required because the wireless {@code IGridConnection} from {@link WirelessGridManager} binds to exactly
     * one
     * node. The delegating (item) provider no-ops {@link #updateValidGridProxySides()} so it cannot fight the owning
     * provider's side configuration.
     */
    private static final class OutputProvider<T extends appeng.api.storage.data.IAEStack<T>>
        extends MTEHatchOutputMEBase<T> {

        private MTEHatchOutputMEBase<?> owner;

        OutputProvider(Environment<T> env, long baseCapacity) {
            super(env, baseCapacity);
        }

        void setProxyOwner(MTEHatchOutputMEBase<?> owner) {
            this.owner = owner;
        }

        @Override
        public AENetworkProxy getProxy() {
            return owner != null ? owner.getProxy() : super.getProxy();
        }

        @Override
        public void updateValidGridProxySides() {
            // Only the owning (fluid) provider configures sides; the delegating (item) provider must stay out of it.
            if (owner == null) {
                super.updateValidGridProxySides();
            }
        }
    }
}
