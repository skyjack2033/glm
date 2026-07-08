package com.github.skyjack2033.wirelessmehatch.gui;

import java.util.function.BooleanSupplier;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.FluidSlotSyncHandler;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.layout.Grid;
import com.cleanroommc.modularui.widgets.slot.FluidSlot;
import com.github.skyjack2033.wirelessmehatch.metatileentity.MTEWirelessInputHatchME;

import gregtech.api.modularui2.GTGuis;
import gregtech.common.modularui2.widget.builder.ItemSlotGridBuilder;

/**
 * MUI2 GUI for {@link MTEWirelessInputHatchME}.
 *
 * <p>
 * Mirrors the layout of GT's {@code MTEHatchInputMEGui}/{@code MTEHatchInputBusMEGui}: two phantom config-slot grids
 * (16
 * item filters + 16 fluid filters) that let the player choose which items/fluids to pull from the bound ME network,
 * plus
 * the wireless connection-status banner. The config grids are backed by the hatch's {@code ItemConfigHandler} (item
 * filters) and {@code FluidConfigTank}s (fluid filters); placing a ghost item/fluid sets the slot's config filter (see
 * {@link MTEWirelessInputHatchME#setItemConfig}/{@code setFluidConfig}).
 * </p>
 *
 * <p>
 * The phantom-item grid uses GT's {@link ItemSlotGridBuilder} exactly like
 * {@code MTEHatchInputBusMEGui.createFilterSlots};
 * the phantom-fluid grid builds one {@link FluidSlot} per config tank with a
 * {@link FluidSlotSyncHandler#phantom(boolean)
 * phantom} sync handler, mirroring {@code MTEHatchInputMEGui.createFilterSlots}.
 * </p>
 */
public class MTEWirelessInputHatchMEGui {

    /** Config grids are 4x4 (16 slots), matching GT's ME input hatches. */
    private static final int GRID_WIDTH = 4;
    private static final int GRID_HEIGHT = 4;

    private final MTEWirelessInputHatchME hatch;

    public MTEWirelessInputHatchMEGui(MTEWirelessInputHatchME hatch) {
        this.hatch = hatch;
    }

    /**
     * Builds the {@link ModularPanel} for this hatch. Sync values are registered on the supplied
     * {@link PanelSyncManager}
     * so the client GUI reflects the server-side wireless connection state in real time.
     */
    public ModularPanel build(PosGuiData guiData, PanelSyncManager syncManager, UISettings uiSettings) {
        // Wireless connection state (read-only on the client - the wireless link is server-authoritative).
        BooleanSyncValue connected = new BooleanSyncValue((BooleanSupplier) hatch::isWirelessConnected);
        syncManager.syncValue("wireless_connected", connected);
        // Bound WAP serial (cast to int for sync; full long precision is not needed for a hex display).
        IntSyncValue boundSerial = new IntSyncValue(() -> (int) hatch.getBoundWapSerial());
        syncManager.syncValue("bound_serial", boundSerial);

        return GTGuis.mteTemplatePanelBuilder(hatch, guiData, syncManager, uiSettings)
            .setWidth(176)
            .setHeight(230)
            .doesBindPlayerInventory(true)
            .doesAddTitle(true)
            .doesAddCoverTabs(true)
            .doesAddGhostCircuitSlot(true)
            .doesAddGregTechLogo(true)
            .build()
            .child(buildContent(connected, boundSerial, syncManager));
    }

    /**
     * Builds the wireless-specific content as a vertical flow: status banner, then the item config grid, then the fluid
     * config grid. Each grid is a 4x4 phantom-slot matrix backed by the hatch's config handlers.
     */
    private Flow buildContent(BooleanSyncValue connected, IntSyncValue boundSerial, PanelSyncManager syncManager) {
        return Flow.col()
            .childPadding(4)
            // Status banner: dynamic text reflecting the live connection state + bound WAP serial.
            .child(IKey.dynamic(() -> {
                boolean c = connected.getBoolValue();
                int serial = boundSerial.getIntValue();
                String serialText = serial != 0 ? "WAP 0x" + Integer.toHexString(serial) : "No WAP bound";
                return (c ? "\u00A7a+ Connected" : "\u00A7c- Disconnected") + "\u00A77 | " + serialText;
            })
                .asWidget())
            // Item filter label + 4x4 phantom-item grid. Placing a ghost item sets that slot's config filter.
            .child(
                IKey.str("\u00A77Item filters")
                    .asWidget())
            .child(createItemFilterGrid(syncManager))
            // Fluid filter label + 4x4 phantom-fluid grid. Placing a ghost fluid sets that slot's config filter.
            .child(
                IKey.str("\u00A77Fluid filters")
                    .asWidget())
            .child(createFluidFilterGrid(syncManager));
    }

    /**
     * Builds the 4x4 phantom-item config grid, mirroring {@code MTEHatchInputBusMEGui.createFilterSlots}. Each slot is
     * backed by {@link MTEWirelessInputHatchME#getItemConfigHandler()}; placing a ghost item into a slot calls
     * {@link MTEWirelessInputHatchME#setItemConfig(int, net.minecraft.item.ItemStack)}.
     */
    private Grid createItemFilterGrid(PanelSyncManager syncManager) {
        return new ItemSlotGridBuilder(hatch.getItemConfigHandler(), syncManager).size(GRID_WIDTH, GRID_HEIGHT)
            .slotGroupKey("item_filter_inv")
            .canPut(true)
            .canTake(true)
            .build();
    }

    /**
     * Builds the 4x4 phantom-fluid config grid, mirroring {@code MTEHatchInputMEGui.createFilterSlots}. Each cell is a
     * {@link FluidSlot} whose {@link FluidSlotSyncHandler} is built from the slot's {@link MTEWirelessInputHatchME
     * #getFluidConfigTank(int)} and marked phantom + non-amount-controlling. Placing a ghost fluid sets that slot's
     * config filter via {@link MTEWirelessInputHatchME#setFluidConfig(int, net.minecraftforge.fluids.FluidStack)}.
     */
    private Grid createFluidFilterGrid(PanelSyncManager syncManager) {
        syncManager.registerSlotGroup("fluid_filter_inv", 1);
        Grid grid = new Grid();
        for (int row = 0; row < GRID_HEIGHT; row++) {
            // Build one phantom FluidSlot widget per cell, then lay them out as a single row.
            com.cleanroommc.modularui.api.widget.IWidget[] rowWidgets = new com.cleanroommc.modularui.api.widget.IWidget[GRID_WIDTH];
            for (int col = 0; col < GRID_WIDTH; col++) {
                int slotIndex = row * GRID_WIDTH + col;
                FluidSlotSyncHandler handler = new FluidSlotSyncHandler(hatch.getFluidConfigTank(slotIndex))
                    .phantom(true)
                    .controlsAmount(false);
                rowWidgets[col] = new FluidSlot().syncHandler(handler);
            }
            grid.row(rowWidgets);
        }
        return grid;
    }
}
