package com.github.skyjack2033.wirelessmehatch.gui;

import java.util.function.BooleanSupplier;

import net.minecraft.item.ItemStack;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.utils.BooleanConsumer;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ToggleButton;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.github.skyjack2033.wirelessmehatch.metatileentity.MTEWirelessOutputHatchME;

import appeng.api.AEApi;
import appeng.api.storage.StorageChannel;
import gregtech.api.modularui2.GTGuis;
import gregtech.api.util.GTUtility;

/**
 * MUI2 GUI for {@link MTEWirelessOutputHatchME}.
 *
 * <p>
 * Mirrors the layout of GT's {@code MTEHatchOutputMEGui} (cell slot + priority + cache/check mode toggles) but adds a
 * wireless connection-status banner showing the bound WAP serial and live connected/disconnected state. The single
 * shared cell slot ({@code mInventory[0]}) backs both the fluid and item providers, so the GUI exposes exactly one cell
 * slot, filtered to accept AE2 storage cells (item or fluid) via {@link #isStorageCell}.
 * </p>
 *
 * <p>
 * The panel is built with {@link GTGuis#mteTemplatePanelBuilder}, which adds the standard GT chrome (title, player
 * inventory, cover tabs, ghost circuit slot, logo) via the {@code doesAdd...} flags. The wireless-specific content is
 * attached as a child {@link Flow} column. Layout uses MUI2's flex {@link Flow} model (no manual pixel positioning),
 * matching GT's own MUI2 hatch GUIs.
 * </p>
 */
public class MTEWirelessOutputHatchMEGui {

    private final MTEWirelessOutputHatchME hatch;

    public MTEWirelessOutputHatchMEGui(MTEWirelessOutputHatchME hatch) {
        this.hatch = hatch;
    }

    /**
     * Builds the {@link ModularPanel} for this hatch. Sync values are registered on the supplied
     * {@link PanelSyncManager}
     * so the client GUI reflects the server-side wireless connection state, priority, and cache mode in real time.
     */
    public ModularPanel build(PosGuiData guiData, PanelSyncManager syncManager, UISettings uiSettings) {
        // Wireless connection state (read-only on the client - the wireless link is server-authoritative).
        BooleanSyncValue connected = new BooleanSyncValue((BooleanSupplier) hatch::isWirelessConnected);
        syncManager.syncValue("wireless_connected", connected);
        // Bound WAP serial (cast to int for sync; full long precision is not needed for a hex display).
        IntSyncValue boundSerial = new IntSyncValue(() -> (int) hatch.getBoundWapSerial());
        syncManager.syncValue("bound_serial", boundSerial);

        // ME priority (client-writable via the text field, mirrors MTEHatchOutputME).
        IntSyncValue priority = new IntSyncValue(hatch::getPriority, hatch::setPriority);
        priority.allowC2S();
        syncManager.syncValue("priority", priority);

        // Cache/check mode toggle (client-writable, mirrors MTEHatchOutputME.getCacheMode).
        BooleanSyncValue cacheMode = new BooleanSyncValue(hatch::isCacheMode, (BooleanConsumer) hatch::setCacheMode);
        cacheMode.allowC2S();
        syncManager.syncValue("cache_mode", cacheMode);

        return GTGuis.mteTemplatePanelBuilder(hatch, guiData, syncManager, uiSettings)
            .setWidth(176)
            .setHeight(190)
            .doesBindPlayerInventory(true)
            .doesAddTitle(true)
            .doesAddCoverTabs(true)
            .doesAddGhostCircuitSlot(true)
            .doesAddGregTechLogo(true)
            .build()
            .child(buildContent(connected, boundSerial, priority, cacheMode));
    }

    /**
     * Builds the wireless-specific content as a vertical flow: status banner, cell slot, priority control, and the
     * cache/check mode toggle.
     */
    private Flow buildContent(BooleanSyncValue connected, IntSyncValue boundSerial, IntSyncValue priority,
        BooleanSyncValue cacheMode) {
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
            // Single shared cell slot (mInventory[0]); filtered to AE2 storage cells.
            .child(
                new ItemSlot().slot(
                    new ModularSlot(hatch.getInventoryHandler(), 0).filter(MTEWirelessOutputHatchMEGui::isStorageCell)))
            // Priority label + editable field (numbersInt mirrors MTEHatchOutputME's priority control).
            .child(
                Flow.row()
                    .childPadding(4)
                    .child(
                        IKey.str("Priority")
                            .asWidget())
                    .child(
                        new TextFieldWidget().value(priority)
                            .numbersInt(0, Integer.MAX_VALUE)
                            .setMaxLength(7)))
            // Cache/check mode toggle (on = cache output, off = check-only / direct push).
            .child(
                Flow.row()
                    .childPadding(4)
                    .child(
                        IKey.dynamic(() -> cacheMode.getBoolValue() ? "Cache" : "Check")
                            .asWidget())
                    .child(new ToggleButton().value(cacheMode)));
    }

    /**
     * Cell-slot filter: accept any item registered as an AE2 storage cell (item or fluid). This mirrors the filter used
     * by GT's {@code MTEHatchOutputME}/{@code MTEHatchOutputBusME} cell slots. Non-cell items are rejected so players
     * do
     * not pollute the shared buffer slot.
     */
    private static boolean isStorageCell(ItemStack stack) {
        if (GTUtility.isStackInvalid(stack)) return false;
        // AEApi cell registry is the canonical test: a stack is a cell iff it yields a cell inventory for either
        // channel. Guarded so the GUI stays usable even if AE2 is absent at GUI-build time.
        try {
            return AEApi.instance()
                .registries()
                .cell()
                .getCellInventory(stack, null, StorageChannel.ITEMS) != null
                || AEApi.instance()
                    .registries()
                    .cell()
                    .getCellInventory(stack, null, StorageChannel.FLUIDS) != null;
        } catch (Throwable ignored) {
            String name = stack.getUnlocalizedName();
            return name != null && name.contains("cell");
        }
    }
}
