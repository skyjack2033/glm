package com.github.skyjack2033.wirelessmehatch.terminal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraftforge.common.util.ForgeDirection;

import com.github.skyjack2033.wirelessmehatch.mixin.ContainerInterfaceTerminalAccessor;
import com.github.skyjack2033.wirelessmehatch.mixin.InterfaceTerminalInvTrackerAccessor;

import appeng.api.config.SecurityPermissions;
import appeng.api.networking.IGridNode;
import appeng.api.parts.IPartHost;
import appeng.container.ContainerOpenContext;
import appeng.container.PrimaryGui;
import appeng.container.implementations.ContainerInterfaceTerminal;
import appeng.container.implementations.ContainerPatternTerm;
import appeng.container.slot.AppEngSlot;
import appeng.container.slot.SlotCraftingMatrix;
import appeng.container.slot.SlotCraftingTerm;
import appeng.helpers.InventoryAction;
import appeng.tile.inventory.AppEngInternalInventory;

public final class CombinedTerminalContainer extends ContainerPatternTerm {

    private static final long ACTION_TYPE_MASK = 0xC000000000000000L;
    private static final long INTERFACE_ACTION_MARKER = 0x8000000000000000L;
    private static final long CACHE_ACTION_MARKER = 0xC000000000000000L;
    private static final long ACTION_PAYLOAD_MASK = 0x3FFFFFFFFFFFFFFFL;

    private final PartCombinedTerminal terminal;
    private final IInventory manualCraftingGrid;
    private final AppEngInternalInventory manualOutput;
    private final SlotCraftingTerm manualOutputSlot;
    private final List<AppEngSlot> manualCraftingSlots = new ArrayList<>(9);
    private final List<AppEngSlot> patternCacheSlots = new ArrayList<>(54);
    private final ContainerInterfaceTerminal interfaceDelegate;
    private final InterfaceUpdateRevisionTracker interfaceUpdateTracker;

    public CombinedTerminalContainer(InventoryPlayer playerInventory, PartCombinedTerminal terminal) {
        super(playerInventory, terminal, true);
        this.terminal = terminal;
        setOpenContext(createOpenContext(terminal));
        this.manualCraftingGrid = terminal.getCraftingGrid();
        this.manualOutput = new AppEngInternalInventory(null, 1);

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                int slot = row * 3 + column;
                AppEngSlot craftingSlot = new SlotCraftingMatrix(
                    this,
                    manualCraftingGrid,
                    slot,
                    37 + column * 18,
                    -72 + row * 18);
                manualCraftingSlots.add(craftingSlot);
                addSlotToContainer(craftingSlot);
            }
        }

        manualOutputSlot = new SlotCraftingTerm(
            playerInventory.player,
            getActionSource(),
            getPowerSource(),
            terminal,
            manualCraftingGrid,
            manualCraftingGrid,
            manualOutput,
            131,
            -54,
            this);
        addSlotToContainer(manualOutputSlot);

        for (int row = 0; row < 6; row++) {
            for (int column = 0; column < 9; column++) {
                int slot = row * 9 + column;
                AppEngSlot cacheSlot = new PatternCacheSlot(
                    terminal.getPatternCache(),
                    slot,
                    8 + column * 18,
                    -198 + row * 18,
                    playerInventory);
                patternCacheSlots.add(cacheSlot);
                addSlotToContainer(cacheSlot);
            }
        }

        interfaceDelegate = new ContainerInterfaceTerminal(playerInventory, terminal);
        interfaceUpdateTracker = new InterfaceUpdateRevisionTracker(terminal.getInterfaceUpdateRevision());
        onCraftMatrixChanged(manualCraftingGrid);
    }

    @Override
    public void onCraftMatrixChanged(IInventory inventory) {
        if (manualCraftingGrid == null || manualOutputSlot == null || inventory != manualCraftingGrid) {
            super.onCraftMatrixChanged(inventory);
            return;
        }

        Container ignored = new appeng.container.ContainerNull();
        InventoryCrafting vanillaGrid = new InventoryCrafting(ignored, 3, 3);
        for (int slot = 0; slot < 9; slot++) {
            vanillaGrid.setInventorySlotContents(slot, manualCraftingGrid.getStackInSlot(slot));
        }
        manualOutputSlot.putStack(
            CraftingManager.getInstance()
                .findMatchingRecipe(vanillaGrid, getPlayerInv().player.worldObj));
    }

    @Override
    public void saveChanges() {
        super.saveChanges();
        terminal.saveChanges();
    }

    @Override
    public void detectAndSendChanges() {
        if (!isTerminalAttached()) {
            setValidContainer(false);
            return;
        }

        super.detectAndSendChanges();
        if (!isTerminalAttached()) {
            setValidContainer(false);
            return;
        }

        if (interfaceUpdateTracker.consume(terminal.getInterfaceUpdateRevision())) {
            interfaceDelegate.scheduleUpdate();
        }
        interfaceDelegate.detectAndSendChanges();
    }

    @Override
    public void doAction(EntityPlayerMP player, InventoryAction action, int slot, long id) {
        if (player == null || player != getPlayerInv().player
            || !isTerminalAttached()
            || !hasAccess(SecurityPermissions.CRAFT, false)) return;

        if (isCacheActionId(id)) {
            PatternCacheBatchCommand command = decodeCacheCommand(id);
            if (command != null) {
                PatternCacheBatchProcessor.Result result = PatternCacheBatchProcessor
                    .apply(terminal.getPatternCache(), command);
                terminal.saveChanges();
                player.addChatMessage(
                    new ChatComponentTranslation(
                        "gui.wirelessmehatch.combined_terminal.batch_result",
                        result.getChanged(),
                        result.getSkipped()));
                return;
            }
        }
        if (isInterfaceActionId(id)) {
            if (!hasAccess(SecurityPermissions.BUILD, false)) return;
            long entryId = decodeInterfaceEntryId(id);
            if (!isValidInterfaceSlot(getTrackedInterfaceInventory(entryId), slot)) return;
            interfaceDelegate.doAction(player, action, slot, entryId);
            return;
        }
        super.doAction(player, action, slot, id);
    }

    @Override
    public boolean canInteractWith(net.minecraft.entity.player.EntityPlayer player) {
        return isTerminalAttached() && super.canInteractWith(player);
    }

    @Override
    public PrimaryGui createPrimaryGui() {
        return new CombinedTerminalPrimaryGui(terminal.getPrimaryGuiIcon(), terminal.getTile(), terminal.getSide());
    }

    public List<AppEngSlot> getManualCraftingSlots() {
        return Collections.unmodifiableList(manualCraftingSlots);
    }

    public SlotCraftingTerm getManualOutputSlot() {
        return manualOutputSlot;
    }

    public List<AppEngSlot> getPatternCacheSlots() {
        return Collections.unmodifiableList(patternCacheSlots);
    }

    public void scheduleInterfaceUpdate() {
        if (isTerminalAttached()) interfaceDelegate.scheduleUpdate();
    }

    public PatternCacheFlagState getItemSubstitutionState() {
        return PatternCacheBatchProcessor.getItemSubstitutionState(terminal.getPatternCache());
    }

    public PatternCacheFlagState getOutputSubstitutionState() {
        return PatternCacheBatchProcessor.getOutputSubstitutionState(terminal.getPatternCache());
    }

    private static ContainerOpenContext createOpenContext(PartCombinedTerminal terminal) {
        ContainerOpenContext context = new ContainerOpenContext(terminal);
        TileEntity tile = terminal.getTile();
        if (tile != null) {
            context.setWorld(tile.getWorldObj());
            context.setX(tile.xCoord);
            context.setY(tile.yCoord);
            context.setZ(tile.zCoord);
        }
        context.setSide(terminal.getSide());
        return context;
    }

    private boolean isTerminalAttached() {
        TileEntity tile = terminal.getTile();
        IPartHost host = terminal.getHost();
        ForgeDirection side = terminal.getSide();
        if (tile == null || tile.isInvalid()
            || tile.getWorldObj() == null
            || host == null
            || host.getTile() != tile
            || side == null
            || side.ordinal() < 0
            || side.ordinal() > 5) return false;
        if (tile.getWorldObj()
            .getTileEntity(tile.xCoord, tile.yCoord, tile.zCoord) != tile || host.getPart(side) != terminal)
            return false;

        IGridNode actionableNode = terminal.getActionableNode();
        return actionableNode != null;
    }

    private IInventory getTrackedInterfaceInventory(long entryId) {
        Map<Long, ?> trackedById = ((ContainerInterfaceTerminalAccessor) (Object) interfaceDelegate)
            .wirelessmehatch$getTrackedById();
        Object tracker = trackedById == null ? null : trackedById.get(entryId);
        return tracker instanceof InterfaceTerminalInvTrackerAccessor
            ? ((InterfaceTerminalInvTrackerAccessor) tracker).wirelessmehatch$getPatterns()
            : null;
    }

    static boolean isValidInterfaceSlot(IInventory inventory, int slot) {
        return inventory != null && slot >= 0 && slot < inventory.getSizeInventory();
    }

    public static long encodeInterfaceEntryId(long entryId) {
        if (entryId < 0 || entryId > ACTION_PAYLOAD_MASK) {
            throw new IllegalArgumentException("Interface entry ID is outside the supported range");
        }
        return INTERFACE_ACTION_MARKER | entryId;
    }

    public static long encodeCacheCommand(PatternCacheBatchCommand command) {
        if (command == null) throw new IllegalArgumentException("Cache command must not be null");
        return CACHE_ACTION_MARKER | command.ordinal();
    }

    static boolean isInterfaceActionId(long id) {
        return (id & ACTION_TYPE_MASK) == INTERFACE_ACTION_MARKER;
    }

    static boolean isCacheActionId(long id) {
        return (id & ACTION_TYPE_MASK) == CACHE_ACTION_MARKER;
    }

    static long decodeInterfaceEntryId(long id) {
        return id & ACTION_PAYLOAD_MASK;
    }

    static PatternCacheBatchCommand decodeCacheCommand(long id) {
        long payload = id & ACTION_PAYLOAD_MASK;
        PatternCacheBatchCommand[] commands = PatternCacheBatchCommand.values();
        return payload < commands.length ? commands[(int) payload] : null;
    }
}
