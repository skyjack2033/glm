package com.github.skyjack2033.wirelessmehatch.gui;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.github.skyjack2033.wirelessmehatch.metatileentity.MTEWirelessInputHatchME;

import gregtech.api.modularui2.GTGuis;

/**
 * MUI2 GUI for {@link MTEWirelessInputHatchME}. Shows wireless connection status.
 */
public class MTEWirelessInputHatchMEGui {

    private final MTEWirelessInputHatchME hatch;

    public MTEWirelessInputHatchMEGui(MTEWirelessInputHatchME hatch) {
        this.hatch = hatch;
    }

    public ModularPanel build(PosGuiData guiData, PanelSyncManager syncManager, UISettings uiSettings) {
        BooleanSyncValue connected = new BooleanSyncValue(hatch::isWirelessConnected);
        syncManager.syncValue("wireless_connected", connected);

        IntSyncValue boundSerial = new IntSyncValue(() -> (int) hatch.getBoundWapSerial());
        syncManager.syncValue("bound_serial", boundSerial);

        return GTGuis.mteTemplatePanelBuilder(hatch, guiData, syncManager, uiSettings)
            .setWidth(176)
            .setHeight(166)
            .doesBindPlayerInventory(true)
            .doesAddTitle(true)
            .doesAddGregTechLogo(true)
            .build()
            .child(
                Flow.col()
                    .pos(10, 20)
                    .child(IKey.dynamic(() -> {
                        boolean c = connected.getBoolValue();
                        int serial = boundSerial.getIntValue();
                        String serialText = serial != 0 ? "WAP: 0x" + Integer.toHexString(serial) : "Not bound";
                        return (c ? "\u00A7aConnected" : "\u00A7cDisconnected") + "\u00A77 | " + serialText;
                    })
                        .asWidget()));
    }
}
