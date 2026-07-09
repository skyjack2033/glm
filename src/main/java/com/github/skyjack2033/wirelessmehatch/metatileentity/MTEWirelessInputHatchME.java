package com.github.skyjack2033.wirelessmehatch.metatileentity;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidTank;

import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.utils.item.IItemHandlerModifiable;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.github.skyjack2033.wirelessmehatch.api.IWirelessMEHatch;
import com.github.skyjack2033.wirelessmehatch.gui.MTEWirelessInputHatchMEGui;
import com.github.skyjack2033.wirelessmehatch.me.PlayerIdResolver;
import com.github.skyjack2033.wirelessmehatch.me.WapInteractionHandler;
import com.github.skyjack2033.wirelessmehatch.me.WirelessGridManager;

import appeng.api.config.Actionable;
import appeng.api.implementations.IPowerChannelState;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridNode;
import appeng.api.networking.events.MENetworkBootingStatusChange;
import appeng.api.networking.events.MENetworkEventSubscribe;
import appeng.api.networking.events.MENetworkPowerStatusChange;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.MachineSource;
import appeng.api.networking.storage.IStackWatcher;
import appeng.api.networking.storage.IStackWatcherHost;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.api.util.DimensionalCoord;
import appeng.me.GridAccessException;
import appeng.me.helpers.AENetworkProxy;
import appeng.me.helpers.IGridProxyable;
import appeng.util.item.AEFluidStack;
import appeng.util.item.AEItemStack;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchInput;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.recipe.check.SimpleCheckRecipeResult;
import gregtech.api.util.GTUtility;
import gregtech.api.util.shutdown.ShutDownReasonRegistry;
import gregtech.common.tileentities.machines.IDualInputHatch;
import gregtech.common.tileentities.machines.IDualInputInventory;
import gregtech.common.tileentities.machines.IHatchWatcher;

/**
 * Wireless merged input hatch. Extends {@link MTEHatchInput} (fluid base) and implements GT-native
 * {@link IDualInputHatch} so multiblock controllers recognise it as a dual input without any Mixin. It pulls both
 * fluids (via {@code drain}) and items (via the {@link IDualInputInventory} iterator) from a bound ME network
 * wirelessly through {@link WirelessGridManager}.
 *
 * <p>
 * GT's two input ME hatches - {@code MTEHatchInputME} (fluid) and {@code MTEHatchInputBusME} (item) - are copy-pasted
 * rather than sharing a base. This class mirrors BOTH into a single hatch: each holds its own {@link FluidSlot}/
 * {@link ItemSlotME} array, and both pull from one shared {@link AENetworkProxy} (the proxy is created lazily on the
 * host, with {@code setValidSides(EnumSet.noneOf(...))} so the hatch is purely wireless - no physical cable sides).
 * </p>
 *
 * <p>
 * The recipe-processing snapshot/extract flow (startRecipeProcessing -> drain/getStackInSlot read from slots ->
 * endRecipeProcessing commits via {@code Platform.poweredExtraction}) is copied verbatim from the two GT classes.
 * </p>
 */
public class MTEWirelessInputHatchME extends MTEHatchInput
    implements IDualInputHatch, IWirelessMEHatch, IGridProxyable, IActionHost, IPowerChannelState, IStackWatcherHost,
    gregtech.common.tileentities.machines.IRecipeProcessingAwareHatch {

    /** Number of ME-backed fluid slots and item slots (16 each, matching GT's ME input hatches). */
    public static final int SLOT_COUNT = 16;

    /** HV tier - same as GT's {@code MTEHatchInputME}/{@code MTEHatchInputBusME} base tier. */
    private static final int TIER = 4;

    private final FluidSlot[] fluidSlots = new FluidSlot[SLOT_COUNT];
    private final ItemSlotME[] itemSlots = new ItemSlotME[SLOT_COUNT];
    private final WirelessGridManager wirelessManager;

    private AENetworkProxy gridProxy;
    private BaseActionSource requestSource;
    private IStackWatcher watcher;

    /**
     * Snapshot flag: while {@code true}, {@code drain}/{@code getStackInSlot} serve from the slot snapshots instead of
     * hitting the ME network (mirrors GT's {@code processingRecipe}).
     */
    private boolean processingRecipe = false;
    /**
     * ME activity snapshot captured at {@link #startRecipeProcessing} so {@link #isAllowedToWork()} is stable
     * mid-recipe.
     */
    private boolean cachedActivity = false;

    public MTEWirelessInputHatchME(int aID, String aName, String aNameRegional) {
        super(
            aID,
            aName,
            aNameRegional,
            TIER,
            new String[] { "Wireless ME Input Hatch for Multiblocks",
                "Merges fluid input hatch and item input bus into one block",
                "Pulls items and fluids from your ME network wirelessly - no cables needed",
                "16 item and 16 fluid config slots for filtering", "How to connect:",
                "  1. Sneak-right-click an AE2 ME Controller with a Memory Card",
                "  2. Right-click this hatch with the bound Memory Card", "  3. Screwdriver right-click to unbind",
                "Compatible with all GregTech multiblock machines" });
        initSlots();
        this.wirelessManager = new WirelessGridManager(this, this::onWirelessConnectionChanged);
    }

    public MTEWirelessInputHatchME(String aName, int aTier, String[] aDescription,
        gregtech.api.interfaces.ITexture[][][] aTextures) {
        super(aName, aTier, aDescription, aTextures);
        initSlots();
        this.wirelessManager = new WirelessGridManager(this, this::onWirelessConnectionChanged);
    }

    private void initSlots() {
        for (int i = 0; i < SLOT_COUNT; i++) {
            fluidSlots[i] = new FluidSlot();
            itemSlots[i] = new ItemSlotME();
        }
    }

    /** @return the ME-backed item config slots (read-only view for the GUI). */
    public ItemSlotME[] getItemSlots() {
        return itemSlots;
    }

    /** @return the ME-backed fluid config slots (read-only view for the GUI). */
    public FluidSlot[] getFluidSlots() {
        return fluidSlots;
    }

    /**
     * Set the configured filter item for config slot {@code slot}. Used by the GUI's ghost-slot handler. Passing
     * {@code null} (or an empty/invalid stack) clears the slot. Replaces the slot object and clears its extracted
     * snapshot.
     */
    public void setItemConfig(int slot, ItemStack config) {
        if (slot < 0 || slot >= SLOT_COUNT) return;
        ItemStack cfg = (config == null || GTUtility.isStackInvalid(config)) ? null : config.copy();
        if (cfg != null) {
            // Ghost item: config stores a single representative item, not a count.
            cfg.stackSize = 1;
            itemSlots[slot] = new ItemSlotME(cfg);
        } else {
            itemSlots[slot] = new ItemSlotME();
        }
        updateItemSlotQuietly(slot);
        markDirty();
    }

    /**
     * Set the configured filter fluid for config slot {@code slot}. Used by the GUI's ghost-tank handler. Passing
     * {@code null} clears the slot. Replaces the slot object and clears its extracted snapshot.
     */
    public void setFluidConfig(int slot, FluidStack config) {
        if (slot < 0 || slot >= SLOT_COUNT) return;
        FluidStack cfg = (config == null || config.amount <= 0) ? null : config.copy();
        if (cfg != null) {
            // Ghost fluid: config stores a single FluidUnit representative, not a bulk amount.
            cfg.amount = 1000;
            fluidSlots[slot] = new FluidSlot(cfg);
        } else {
            fluidSlots[slot] = new FluidSlot();
        }
        updateFluidSlotQuietly(slot);
        markDirty();
    }

    /**
     * @return an {@link IItemHandlerModifiable} view over the item config slots, for the GUI's ghost-item grid. Writing
     *         a stack into a slot sets that slot's config (ghost); the handler never holds real inventory.
     */
    public IItemHandlerModifiable getItemConfigHandler() {
        return new ItemConfigHandler();
    }

    /**
     * @return the config-tank for a given fluid config slot index, for the GUI's ghost-fluid widget. {@code fill} sets
     *         the slot's config fluid; {@code drain} clears it.
     */
    public IFluidTank getFluidConfigTank(int slot) {
        return new FluidConfigTank(slot);
    }

    /**
     * Refresh a single item slot's extracted snapshot without throwing on grid failure (used after a config change).
     */
    private void updateItemSlotQuietly(int i) {
        try {
            updateItemSlot(i);
        } catch (GridAccessException ignored) {
            // Network unavailable - leave the snapshot empty until the next refresh.
        }
    }

    /**
     * Refresh a single fluid slot's extracted snapshot without throwing on grid failure (used after a config change).
     */
    private void updateFluidSlotQuietly(int i) {
        try {
            updateFluidSlot(i);
        } catch (GridAccessException ignored) {
            // Network unavailable - leave the snapshot empty until the next refresh.
        }
    }

    @Override
    public gregtech.api.metatileentity.MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEWirelessInputHatchME(mName, mTier, mDescriptionArray, mTextures);
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
     * state change (see {@code suppressCallback}), so this fires at most once per bind/reconnect.
     * {@code markDirty()} is itself idempotent, so even a stray double-fire is safe.
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
        return super.onRightclick(aBaseMetaTileEntity, aPlayer);
    }

    @Override
    public void onScrewdriverRightClick(ForgeDirection side, EntityPlayer aPlayer, float aX, float aY, float aZ,
        ItemStack aTool) {
        WapInteractionHandler.unbindHatch(this, aPlayer);
    }

    // ---- IDualInputHatch ----

    /**
     * Returns a single {@link IDualInputInventory} exposing the item and fluid slot snapshots. The inventory's
     * {@code getItemInputs()}/{@code getFluidInputs()} read the live {@code extracted} stacks, so during recipe
     * processing (after {@link #startRecipeProcessing}) controllers see what the ME network can provide.
     */
    @Override
    public Iterator<? extends IDualInputInventory> inventories() {
        return Collections.singleton(new DualInputInventoryImpl())
            .iterator();
    }

    @Override
    public Optional<IDualInputInventory> getFirstNonEmptyInventory() {
        DualInputInventoryImpl inv = new DualInputInventoryImpl();
        return inv.isEmpty() ? Optional.empty() : Optional.of(inv);
    }

    @Override
    public boolean supportsFluids() {
        return true;
    }

    @Override
    public ItemStack[] getSharedItems() {
        // No shared/persistent item slots (everything is ME-backed); return empty.
        return new ItemStack[0];
    }

    // updateTexture(int) and updateCraftingIcon(ItemStack) are inherited as final from MTEHatch; they already satisfy
    // IDualInputHatch's abstract declarations, so they are deliberately not re-declared here.

    @Override
    public void addWatcher(IHatchWatcher aWatcher) {
        // No watcher wiring needed for the wireless variant (expedited recipe-check is not exposed).
    }

    @Override
    public void removeWatcher(IHatchWatcher aWatcher) {
        // No watcher wiring needed for the wireless variant.
    }

    // ---- Fluid input (drain) - mirror MTEHatchInputME.drain ----

    /**
     * Side-less drain as used by GT's recipe input dispatch. Only {@link ForgeDirection#UNKNOWN} is served. During
     * {@link #processingRecipe} the matching fluid slot's snapshot is served (and decremented); otherwise the ME fluid
     * inventory is queried directly (simulate or {@code poweredExtraction}).
     */
    @Override
    public FluidStack drain(ForgeDirection side, FluidStack fluid, int amount, boolean doDrain) {
        if (side != ForgeDirection.UNKNOWN) return null;
        if (fluid == null) return null;
        if (processingRecipe) {
            FluidSlot slot = getMatchingFluidSlot(fluid, true);
            if (slot == null || slot.extracted == null) return null;
            int toDrain = Math.min(slot.extractedAmount, amount);
            FluidStack result = GTUtility.copyAmount(toDrain, slot.extracted);
            if (doDrain) {
                slot.extracted.amount -= toDrain;
            }
            return result;
        }
        FluidSlot slot = getMatchingFluidSlot(fluid, false);
        if (slot == null) return null;
        IAEFluidStack request = AEFluidStack.create(fluid);
        request.setStackSize(amount);
        AENetworkProxy proxy;
        IMEMonitor<IAEFluidStack> fluidInv;
        try {
            proxy = getProxy();
            fluidInv = proxy.getStorage()
                .getFluidInventory();
        } catch (GridAccessException e) {
            return null;
        }
        IAEFluidStack extracted;
        if (doDrain) {
            try {
                extracted = (IAEFluidStack) appeng.util.Platform
                    .poweredExtraction(proxy.getEnergy(), fluidInv, request, getRequestSource());
            } catch (GridAccessException e) {
                return null;
            }
            updateAllInformationSlots();
        } else {
            extracted = (IAEFluidStack) fluidInv.extractItems(request, Actionable.SIMULATE, getRequestSource());
        }
        return extracted == null ? null : extracted.getFluidStack();
    }

    /** {@inheritDoc} This hatch ignores the requested amount and drains {@code aFluid.amount}. */
    @Override
    public FluidStack drain(ForgeDirection side, FluidStack aFluid, boolean doDrain) {
        if (aFluid == null) return null;
        return drain(side, aFluid, aFluid.amount, doDrain);
    }

    /**
     * Mirror of {@code MTEHatchInputME.getMatchingSlot}: find a configured slot whose config fluid matches, with the
     * processing-mode validity checks ({@code checkExtracted}: when true, the slot must have a snapshot to serve).
     */
    private FluidSlot getMatchingFluidSlot(FluidStack fluid, boolean checkExtracted) {
        if (fluid == null || !isAllowedToWork()) return null;
        for (FluidSlot slot : fluidSlots) {
            if (slot == null) continue;
            if (checkExtracted && (slot.extracted == null || slot.extractedAmount == 0)) continue;
            if (!GTUtility.areFluidsEqual(slot.config, fluid)) continue;
            return slot;
        }
        return null;
    }

    // ---- Recipe processing (mirror both input ME hatches) ----

    @Override
    public void startRecipeProcessing() {
        cachedActivity = isAllowedToWork();
        processingRecipe = true;
        updateAllInformationSlots();
    }

    /**
     * Merge of {@code MTEHatchInputME.endRecipeProcessing} (fluid) and {@code MTEHatchInputBusME.endRecipeProcessing}
     * (item): for each slot with a snapshot, commit the actually-consumed amount
     * ({@code extractedAmount - extracted.amount/stackSize}) via {@code Platform.poweredExtraction}. On grid failure
     * or partial extraction, stop the controller and report a persistent failure.
     */
    @Override
    public CheckRecipeResult endRecipeProcessing(MTEMultiBlockBase controller) {
        CheckRecipeResult result = CheckRecipeResultRegistry.SUCCESSFUL;
        AENetworkProxy proxy;
        IMEMonitor<IAEFluidStack> fluidInv;
        IMEMonitor<IAEItemStack> itemInv;
        appeng.api.networking.energy.IEnergyGrid energy;
        try {
            proxy = getProxy();
            fluidInv = proxy.getStorage()
                .getFluidInventory();
            itemInv = proxy.getStorage()
                .getItemInventory();
            energy = proxy.getEnergy();
        } catch (GridAccessException e) {
            controller.stopMachine(ShutDownReasonRegistry.CRITICAL_NONE);
            return SimpleCheckRecipeResult.ofFailurePersistOnShutdown("stocking_hatch_fail_extraction");
        }
        // Fluid side.
        for (FluidSlot slot : fluidSlots) {
            if (slot == null || slot.extracted == null || slot.extractedAmount == 0) continue;
            int toExtract = slot.extractedAmount - slot.extracted.amount;
            if (toExtract <= 0) continue;
            slot.extractedAmount = slot.extracted.amount;
            IAEFluidStack request = AEFluidStack.create(slot.extracted);
            request.setStackSize(toExtract);
            IAEFluidStack extracted = (IAEFluidStack) appeng.util.Platform
                .poweredExtraction(energy, fluidInv, request, getRequestSource());
            if (extracted == null || extracted.getStackSize() != toExtract) {
                controller.stopMachine(ShutDownReasonRegistry.CRITICAL_NONE);
                result = SimpleCheckRecipeResult.ofFailurePersistOnShutdown("stocking_hatch_fail_extraction");
            }
        }
        // Item side.
        for (ItemSlotME slot : itemSlots) {
            if (slot == null || slot.extracted == null || slot.extractedAmount == 0) continue;
            int toExtract = slot.extractedAmount - slot.extracted.stackSize;
            if (toExtract <= 0) continue;
            IAEItemStack request = slot.createAEStack(toExtract);
            IAEItemStack extracted = (IAEItemStack) appeng.util.Platform
                .poweredExtraction(energy, itemInv, request, getRequestSource());
            if (extracted == null || extracted.getStackSize() != toExtract) {
                controller.stopMachine(ShutDownReasonRegistry.CRITICAL_NONE);
                result = SimpleCheckRecipeResult.ofFailurePersistOnShutdown("stocking_bus_fail_extraction");
            }
        }
        processingRecipe = false;
        return result;
    }

    // ---- Slot snapshot refresh (mirror updateInformationSlot of both classes) ----

    private void updateAllInformationSlots() {
        if (!isAllowedToWork()) {
            clearExtractedStacks();
            return;
        }
        try {
            for (int i = 0; i < SLOT_COUNT; i++) {
                updateFluidSlot(i);
                updateItemSlot(i);
            }
        } catch (GridAccessException ignored) {
            // Network unavailable - leave snapshots as-is.
        }
    }

    /**
     * Mirror of {@code MTEHatchInputME.updateInformationSlot}: simulate-extract Integer.MAX_VALUE of the slot's config
     * fluid from the ME fluid inventory and store the result in {@code slot.extracted}/{@code extractedAmount}.
     */
    private void updateFluidSlot(int i) throws GridAccessException {
        FluidSlot slot = i >= 0 && i < fluidSlots.length ? fluidSlots[i] : null;
        if (slot == null) return;
        if (!isAllowedToWork()) {
            slot.resetExtracted();
            return;
        }
        IMEMonitor<IAEFluidStack> fluidInv = getProxy().getStorage()
            .getFluidInventory();
        IAEFluidStack request = AEFluidStack.create(slot.config);
        request.setStackSize(Integer.MAX_VALUE);
        IAEFluidStack found = (IAEFluidStack) fluidInv.extractItems(request, Actionable.SIMULATE, getRequestSource());
        slot.extracted = found == null ? null : found.getFluidStack();
        slot.extractedAmount = slot.extracted == null ? 0 : slot.extracted.amount;
    }

    /**
     * Mirror of {@code MTEHatchInputBusME.updateInformationSlot}: simulate-extract Integer.MAX_VALUE of the slot's
     * config item from the ME item inventory and store the result in {@code slot.extracted}/{@code extractedAmount}.
     */
    private void updateItemSlot(int i) throws GridAccessException {
        ItemSlotME slot = i >= 0 && i < itemSlots.length ? itemSlots[i] : null;
        if (slot == null) return;
        if (!isAllowedToWork()) {
            slot.resetExtracted();
            return;
        }
        IMEMonitor<IAEItemStack> itemInv = getProxy().getStorage()
            .getItemInventory();
        IAEItemStack request = slot.createAEStack(Integer.MAX_VALUE);
        IAEItemStack found = (IAEItemStack) itemInv.extractItems(request, Actionable.SIMULATE, getRequestSource());
        slot.extracted = found == null ? null : found.getItemStack();
        slot.extractedAmount = slot.extracted == null ? 0 : slot.extracted.stackSize;
    }

    private void clearExtractedStacks() {
        for (FluidSlot slot : fluidSlots) {
            if (slot != null) slot.resetExtracted();
        }
        for (ItemSlotME slot : itemSlots) {
            if (slot != null) slot.resetExtracted();
        }
    }

    // ---- IGridProxyable (single shared proxy, wireless) ----

    /**
     * Lazily creates the single shared {@link AENetworkProxy} (used by both the fluid and item paths). Valid sides are
     * empty so the hatch connects only via the wireless {@code IGridConnection} from {@link WirelessGridManager} - it
     * deliberately does NOT call {@code updateValidGridProxySides()} (no physical cable).
     */
    @Override
    public AENetworkProxy getProxy() {
        if (gridProxy == null) {
            IGregTechTileEntity base = getBaseMetaTileEntity();
            if (base instanceof IGridProxyable) {
                gridProxy = new AENetworkProxy(this, "proxy", getStackForm(1), true);
                gridProxy.setFlags(GridFlags.REQUIRE_CHANNEL);
                gridProxy.setValidSides(EnumSet.noneOf(ForgeDirection.class)); // wireless
            }
        }
        return gridProxy;
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
        // No item to break on grid security violation - matches GT's ME input hatches.
    }

    // ---- IPowerChannelState ----

    @Override
    public boolean isPowered() {
        return getProxy() != null && getProxy().isPowered();
    }

    @Override
    public boolean isActive() {
        return getProxy() != null && getProxy().isActive();
    }

    /**
     * ME power/booting status changes flip the tile's active state (mirrors GT's
     * {@code powerChangeME}/{@code bootChangeME}).
     */
    @MENetworkEventSubscribe
    public void powerChangeME(MENetworkPowerStatusChange e) {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base != null) base.setActive(isActive());
    }

    @MENetworkEventSubscribe
    public void bootChangeME(MENetworkBootingStatusChange e) {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base != null) base.setActive(isActive());
    }

    // ---- IStackWatcherHost ----

    @Override
    public void updateWatcher(IStackWatcher aWatcher) {
        this.watcher = aWatcher;
        configureWatchers();
    }

    /** Mirrors GT: reset the watcher and (no-op here) re-register watched stacks. */
    private void configureWatchers() {
        if (watcher != null) {
            watcher.clear();
            // Expedited recipe-check watcher wiring is intentionally omitted (see concerns).
        }
    }

    @Override
    public void onStackChange(IItemList aList, appeng.api.storage.data.IAEStack aFullStack,
        appeng.api.storage.data.IAEStack aDiffStack, BaseActionSource aSource, StorageChannel aChannel) {
        // No expedited recipe-check wiring - react to no stack changes.
    }

    // ---- Work-state & helpers ----

    /**
     * Mirror of GT's {@code isAllowedToWork}: returns the cached activity snapshot mid-recipe; otherwise requires the
     * tile to be allowed to work AND the ME proxy to be active. Also folds in the wireless requirement so a hatch that
     * has lost its WAP link cannot serve inputs.
     */
    public boolean isAllowedToWork() {
        if (processingRecipe) return cachedActivity;
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base == null || !base.isAllowedToWork()) return false;
        if (!wirelessManager.isConnected()) return false;
        try {
            return getProxy().isActive();
        } catch (Exception ignored) {
            return false;
        }
    }

    /** Lazy action source used for ME storage operations (mirrors GT's {@code getRequestSource}). */
    private BaseActionSource getRequestSource() {
        if (requestSource == null) {
            requestSource = new MachineSource(this);
        }
        return requestSource;
    }

    /** Mirror of GT's {@code proxyCheckup}: ensure the proxy is ready before use. */
    private void proxyCheckup() {
        AENetworkProxy proxy = getProxy();
        if (!proxy.isReady()) proxy.onReady();
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
        wirelessManager.tickCheck();
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);
        if (aBaseMetaTileEntity.isServerSide()) {
            wirelessManager.tickCheck();
            if (aTick % 20 == 0) {
                aBaseMetaTileEntity.setActive(isActive());
            }
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
        if (getProxy() != null) {
            getProxy().writeToNBT(aNBT);
        }
        saveSlotList(aNBT, "fluidSlots", fluidSlots);
        saveSlotList(aNBT, "itemSlots", itemSlots);
    }

    private void saveSlotList(NBTTagCompound aNBT, String key, Object[] slots) {
        NBTTagList list = new NBTTagList();
        for (int i = 0; i < slots.length; i++) {
            NBTTagCompound entry = new NBTTagCompound();
            if (slots[i] instanceof FluidSlot fluid && fluid != null) {
                fluid.writeToNBT(entry);
            } else if (slots[i] instanceof ItemSlotME item && item != null) {
                item.writeToNBT(entry);
            } else {
                continue;
            }
            entry.setInteger("index", i);
            list.appendTag(entry);
        }
        aNBT.setTag(key, list);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        wirelessManager.readFromNBT(aNBT);
        if (aNBT.hasKey("proxy") && getProxy() != null) {
            getProxy().readFromNBT(aNBT);
        }
        loadFluidSlots(aNBT);
        loadItemSlots(aNBT);
    }

    private void loadFluidSlots(NBTTagCompound aNBT) {
        NBTTagList list = aNBT.getTagList("fluidSlots", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            int index = entry.getInteger("index");
            if (index < 0 || index >= SLOT_COUNT) continue;
            FluidSlot slot = FluidSlot.readFromNBT(entry);
            if (slot != null) fluidSlots[index] = slot;
        }
    }

    private void loadItemSlots(NBTTagCompound aNBT) {
        NBTTagList list = aNBT.getTagList("itemSlots", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            int index = entry.getInteger("index");
            if (index < 0 || index >= SLOT_COUNT) continue;
            ItemSlotME slot = ItemSlotME.readFromNBT(entry);
            if (slot != null) itemSlots[index] = slot;
        }
    }

    // ---- Tank info (mirrors MTEHatchInputME.getTankInfo / getStoredFluids) ----

    /** Exposes the ME-provided fluid snapshots so multiblock controllers can probe available fluids. */
    public FluidStack[] getStoredFluids() {
        try {
            updateAllInformationSlots();
        } catch (Exception ignored) {}
        int count = 0;
        for (FluidSlot slot : fluidSlots) {
            if (slot != null && slot.extracted != null) count++;
        }
        FluidStack[] out = new FluidStack[count];
        int j = 0;
        for (FluidSlot slot : fluidSlots) {
            if (slot != null && slot.extracted != null) {
                out[j++] = slot.extracted.copy();
            }
        }
        return out;
    }

    @Override
    public FluidTankInfo[] getTankInfo(ForgeDirection aSide) {
        FluidStack[] stored = getStoredFluids();
        FluidTankInfo[] infos = new FluidTankInfo[stored.length];
        for (int i = 0; i < stored.length; i++) {
            infos[i] = new FluidTankInfo(stored[i], stored[i].amount);
        }
        return infos;
    }

    @Override
    public boolean canTankBeFilled() {
        return true;
    }

    @Override
    public boolean canTankBeEmptied() {
        return true;
    }

    // ---- Textures (ME overlay, matching MTEHatchInputME) ----

    @Override
    public gregtech.api.interfaces.ITexture[] getTexturesActive(gregtech.api.interfaces.ITexture aBaseTexture) {
        return new gregtech.api.interfaces.ITexture[] { aBaseTexture, gregtech.api.render.TextureFactory
            .of(gregtech.api.enums.Textures.BlockIcons.OVERLAY_ME_INPUT_FLUID_HATCH_ACTIVE) };
    }

    @Override
    public gregtech.api.interfaces.ITexture[] getTexturesInactive(gregtech.api.interfaces.ITexture aBaseTexture) {
        return new gregtech.api.interfaces.ITexture[] { aBaseTexture, gregtech.api.render.TextureFactory
            .of(gregtech.api.enums.Textures.BlockIcons.OVERLAY_ME_INPUT_FLUID_HATCH) };
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
        return new MTEWirelessInputHatchMEGui(this).build(guiData, syncManager, uiSettings);
    }

    // ---- Inner slot classes (copied from GT's MTEHatchInputME.Slot / MTEHatchInputBusME.Slot) ----

    /**
     * Fluid slot snapshot, copied from {@code MTEHatchInputME$Slot}. Holds a {@code config} fluid (the configured
     * filter) plus a {@code extracted}/{@code extractedAmount} snapshot of what the ME network can currently supply.
     *
     * <p>
     * The {@code config} field is mutable so the GUI's ghost-slot handler can set it; the slot is replaced in the
     * {@link #fluidSlots} array whenever the config changes (see {@link #setFluidConfig(int, FluidStack)}).
     * </p>
     */
    public static final class FluidSlot {

        public FluidStack config;
        public int extractedAmount;
        public FluidStack extracted;

        public FluidSlot() {
            this.config = null;
        }

        public FluidSlot(FluidStack config) {
            this.config = config;
        }

        /**
         * Replace this slot's configured filter fluid. Clears the extracted snapshot so the next snapshot refresh
         * re-reads the ME network for the new filter.
         */
        public void setConfig(FluidStack newConfig) {
            this.config = newConfig == null ? null : newConfig.copy();
            resetExtracted();
        }

        public void resetExtracted() {
            this.extracted = null;
            this.extractedAmount = 0;
        }

        public FluidStack getOriginalExtracted() {
            if (extracted == null) return null;
            return GTUtility.copyAmount(extractedAmount, extracted);
        }

        public FluidSlot copy() {
            FluidSlot out = new FluidSlot(config == null ? null : config.copy());
            out.extracted = extracted;
            out.extractedAmount = extractedAmount;
            return out;
        }

        public void writeToNBT(NBTTagCompound tag) {
            if (config == null) return;
            tag.setTag("config", config.writeToNBT(new NBTTagCompound()));
            if (extracted != null) {
                tag.setTag("extracted", extracted.writeToNBT(new NBTTagCompound()));
                tag.setInteger("extractedAmount", extractedAmount);
            }
        }

        public static FluidSlot readFromNBT(NBTTagCompound tag) {
            FluidStack config = FluidStack.loadFluidStackFromNBT(tag.getCompoundTag("config"));
            if (config == null) return null;
            FluidSlot out = new FluidSlot(config);
            if (tag.hasKey("extracted")) {
                out.extracted = FluidStack.loadFluidStackFromNBT(tag.getCompoundTag("extracted"));
                out.extractedAmount = tag.getInteger("extractedAmount");
            }
            return out;
        }
    }

    /**
     * Item slot snapshot, copied from {@code MTEHatchInputBusME$Slot}. Holds a {@code config} item (the configured
     * filter) plus a {@code extracted}/{@code extractedAmount} snapshot and a cached {@code prototypeStack} used to
     * build AE request stacks.
     *
     * <p>
     * The {@code config} field is mutable so the GUI's ghost-slot handler can set it; {@code setConfig} rebuilds the
     * cached {@code prototypeStack} and clears the extracted snapshot. The slot is replaced in the {@link #itemSlots}
     * array whenever the config changes (see {@link #setItemConfig(int, ItemStack)}).
     * </p>
     */
    public static final class ItemSlotME {

        public ItemStack config;
        public int extractedAmount;
        public ItemStack extracted;
        private IAEItemStack prototypeStack;

        public ItemSlotME() {
            this.config = null;
            this.prototypeStack = null;
        }

        public ItemSlotME(ItemStack config) {
            this.config = config;
            this.prototypeStack = config == null ? null : AEItemStack.create(config);
        }

        /**
         * Replace this slot's configured filter item. Rebuilds the cached {@code prototypeStack} and clears the
         * extracted
         * snapshot so the next snapshot refresh re-reads the ME network for the new filter.
         */
        public void setConfig(ItemStack newConfig) {
            this.config = newConfig == null ? null : newConfig.copy();
            this.prototypeStack = this.config == null ? null : AEItemStack.create(this.config);
            resetExtracted();
        }

        public void resetExtracted() {
            this.extracted = null;
            this.extractedAmount = 0;
        }

        public IAEItemStack createAEStack(long size) {
            if (prototypeStack == null) return null;
            IAEItemStack out = prototypeStack.copy();
            out.setStackSize(size);
            return out;
        }

        public ItemSlotME copy() {
            ItemSlotME out = new ItemSlotME(config == null ? null : config.copy());
            out.extracted = extracted;
            out.extractedAmount = extractedAmount;
            return out;
        }

        public void writeToNBT(NBTTagCompound tag) {
            if (config == null) return;
            tag.setTag("config", config.writeToNBT(new NBTTagCompound()));
            if (extracted != null) {
                tag.setTag("extracted", extracted.writeToNBT(new NBTTagCompound()));
                tag.setInteger("extractedAmount", extractedAmount);
            }
        }

        public static ItemSlotME readFromNBT(NBTTagCompound tag) {
            ItemStack config = ItemStack.loadItemStackFromNBT(tag.getCompoundTag("config"));
            if (config == null) return null;
            ItemSlotME out = new ItemSlotME(config);
            if (tag.hasKey("extracted")) {
                out.extracted = ItemStack.loadItemStackFromNBT(tag.getCompoundTag("extracted"));
                out.extractedAmount = tag.getInteger("extractedAmount");
            }
            return out;
        }
    }

    // ---- Config-slot handlers (ghost items / fluids for the GUI) ----

    /**
     * {@link IItemHandlerModifiable} view over the {@link #itemSlots} config array, used by the GUI's ghost-item grid.
     * Each slot reports the slot's configured filter item (stack size 1). {@link #setStackInSlot} writes a config
     * (ghost) - the item is never held as real inventory, and {@link #insertItem}/{@link #extractItem} are no-ops that
     * return the input unchanged so the slot behaves as phantom-only.
     */
    private final class ItemConfigHandler implements IItemHandlerModifiable {

        @Override
        public int getSlots() {
            return SLOT_COUNT;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            if (slot < 0 || slot >= SLOT_COUNT || itemSlots[slot] == null) return null;
            ItemStack config = itemSlots[slot].config;
            return config == null ? null : config.copy();
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            // Phantom slot: insert is handled via setStackInSlot by the ghost-slot machinery; never consume the input.
            return stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            // Phantom slot: nothing real to extract.
            return null;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        public void setStackInSlot(int slot, ItemStack stack) {
            setItemConfig(slot, stack);
        }
    }

    /**
     * {@link IFluidTank} view over a single {@link #fluidSlots} config entry, used by the GUI's ghost-fluid widget.
     * {@link #fill} sets the slot's config fluid (ghost); {@link #drain} clears it. The tank reports a 1-bucket
     * capacity and the configured fluid so the widget renders the filter.
     */
    public final class FluidConfigTank implements IFluidTank {

        private final int slotIndex;

        FluidConfigTank(int slotIndex) {
            this.slotIndex = slotIndex;
        }

        private FluidSlot slot() {
            if (slotIndex < 0 || slotIndex >= SLOT_COUNT) return null;
            return fluidSlots[slotIndex];
        }

        @Override
        public FluidStack getFluid() {
            FluidSlot slot = slot();
            return slot == null || slot.config == null ? null : slot.config.copy();
        }

        @Override
        public int getFluidAmount() {
            FluidSlot slot = slot();
            return slot == null || slot.config == null ? 0 : slot.config.amount;
        }

        @Override
        public int getCapacity() {
            return 1000;
        }

        @Override
        public FluidTankInfo getInfo() {
            return new FluidTankInfo(getFluid(), getCapacity());
        }

        @Override
        public int fill(FluidStack resource, boolean doFill) {
            if (resource == null || resource.getFluid() == null) return 0;
            if (doFill) {
                setFluidConfig(slotIndex, resource);
            }
            return resource.amount;
        }

        @Override
        public FluidStack drain(int maxDrain, boolean doDrain) {
            FluidSlot slot = slot();
            if (slot == null || slot.config == null) return null;
            FluidStack out = slot.config.copy();
            if (doDrain) {
                setFluidConfig(slotIndex, null);
            }
            return out;
        }
    }

    /**
     * Single {@link IDualInputInventory} exposed to GT. {@code getItemInputs()} reads the item slot snapshots;
     * {@code getFluidInputs()} reads the fluid slot snapshots. Only non-null, non-empty snapshots are included so
     * recipe logic sees exactly what the ME network currently provides.
     */
    private final class DualInputInventoryImpl implements IDualInputInventory {

        @Override
        public boolean isEmpty() {
            for (ItemSlotME slot : itemSlots) {
                if (slot != null && slot.extracted != null && slot.extracted.stackSize > 0) return false;
            }
            for (FluidSlot slot : fluidSlots) {
                if (slot != null && slot.extracted != null && slot.extracted.amount > 0) return false;
            }
            return true;
        }

        @Override
        public ItemStack[] getItemInputs() {
            int count = 0;
            for (ItemSlotME slot : itemSlots) {
                if (slot != null && slot.extracted != null && slot.extracted.stackSize > 0) count++;
            }
            ItemStack[] out = new ItemStack[count];
            int j = 0;
            for (ItemSlotME slot : itemSlots) {
                if (slot != null && slot.extracted != null && slot.extracted.stackSize > 0) {
                    out[j++] = slot.extracted.copy();
                }
            }
            return out;
        }

        @Override
        public FluidStack[] getFluidInputs() {
            int count = 0;
            for (FluidSlot slot : fluidSlots) {
                if (slot != null && slot.extracted != null && slot.extracted.amount > 0) count++;
            }
            FluidStack[] out = new FluidStack[count];
            int j = 0;
            for (FluidSlot slot : fluidSlots) {
                if (slot != null && slot.extracted != null && slot.extracted.amount > 0) {
                    out[j++] = slot.extracted.copy();
                }
            }
            return out;
        }
    }

    // Suppress unused-import warnings for helpers that are only referenced reflectively/by future tasks.
    @SuppressWarnings("unused")
    private static void unusedImportAnchors(List<Object> ignored) {
        // anchors keep imports of types that Task 8/9 will use without lint churn
        ignored.add(IGridNode.class);
    }
}
