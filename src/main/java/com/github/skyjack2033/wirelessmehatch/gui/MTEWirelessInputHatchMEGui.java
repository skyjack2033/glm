package com.github.skyjack2033.wirelessmehatch.gui;

import java.util.function.BooleanSupplier;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.github.skyjack2033.wirelessmehatch.metatileentity.MTEWirelessInputHatchME;

import gregtech.api.gui.modularui2.GTGuis;

/**
 * MUI2 GUI for {@link MTEWirelessInputHatchME}.
 *
 * <p>
 * Mirrors the layout of GT's {@code MTEHatchInputMEGui}/{@code MTEHatchInputBusMEGui} conceptually (connection status +
 * filter/stock slot grids + circuit slot), but the wireless variant's filter slots are pure ME-backed snapshots rather
 * than Forge inventories (see {@link MTEWirelessInputHatchME#itemSlots}/{@code fluidSlots}). Until the hatch exposes
 * {@code IItemHandlerModifiable} wrappers for those config slots, this GUI renders the wireless connection-status
 * banner
 * (the hatch's defining feature) plus an informational summary, and relies on {@code GTBaseGuiBuilder}'s
 * {@code doesAddGhostCircuitSlot} for the integrated circuit slot.
 * </p>
 *
 * <p>
 * The connection-status banner (bound WAP serial + connected/disconnected state) is wired identically to
 * {@link MTEWirelessOutputHatchMEGui}: read-only {@link BooleanSyncValue} + {@link IntSyncValue} registered on the
 * {@link PanelSyncManager}, rendered via {@link IKey#dynamic}.
 * </p>
 */
public class MTEWirelessInputHatchMEGui {

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
            .setHeight(190)
            .doesBindPlayerInventory(true)
            .doesAddTitle(true)
            .doesAddCoverTabs(true)
            .doesAddGhostCircuitSlot(true)
            .doesAddGregTechLogo(true)
            .build()
            .child(buildContent(connected, boundSerial));
    }

    /**
     * Builds the wireless-specific content as a vertical flow: status banner plus an informational summary of the
     * configured ME filter slots (item and fluid).
     */
    private Flow buildContent(BooleanSyncValue connected, IntSyncValue boundSerial) {
        return Flow.col()
            .childPadding(6)
            // Status banner: dynamic text reflecting the live connection state + bound WAP serial.
            .child(IKey.dynamic(() -> {
                boolean c = connected.getBoolValue();
                int serial = boundSerial.getIntValue();
                String serialText = serial != 0 ? "WAP 0x" + Integer.toHexString(serial) : "No WAP bound";
                return (c ? "\u00A7a+ Connected" : "\u00A7c- Disconnected") + "\u00A77 | " + serialText;
            })
                .asWidget())
            // Informational line: how many item / fluid filters are currently configured.
            .child(IKey.dynamic(() -> {
                int items = countItemFilters();
                int fluids = countFluidFilters();
                return "\u00A77Filters: " + items + " items, " + fluids + " fluids";
            })
                .asWidget())
            // Help text so players know how the wireless variant differs from GT's ME input hatches.
            .child(
                IKey.str(
                    "\u00A7oRight-click with an AE Memory Card to bind a WAP.\n\u00A7oScrewdriver-right-click to unbind.")
                    .asWidget());
    }

    /** @return the number of non-null item config slots (informational display). */
    private int countItemFilters() {
        int n = 0;
        for (MTEWirelessInputHatchME.ItemSlotME slot : hatch.getItemSlots()) {
            if (slot != null && slot.config != null) n++;
        }
        return n;
    }

    /** @return the number of non-null fluid config slots (informational display). */
    private int countFluidFilters() {
        int n = 0;
        for (MTEWirelessInputHatchME.FluidSlot slot : hatch.getFluidSlots()) {
            if (slot != null && slot.config != null) n++;
        }
        return n;
    }
}
