package com.github.skyjack2033.wirelessmehatch.loader;

import com.github.skyjack2033.wirelessmehatch.Config;
import com.github.skyjack2033.wirelessmehatch.metatileentity.MTELegacyWirelessInputHatchME;
import com.github.skyjack2033.wirelessmehatch.metatileentity.MTEWirelessUnifiedOutputAssemblyME;

import gregtech.api.GregTechAPI;

public final class MetaTileEntityLoader {

    private MetaTileEntityLoader() {}

    public static void register() {
        GregTechAPI.METATILEENTITIES[Config.wirelessUnifiedOutputAssemblyMeId] = new MTEWirelessUnifiedOutputAssemblyME(
            Config.wirelessUnifiedOutputAssemblyMeId,
            "hatch.output.me.wireless.unified",
            "Wireless Unified Output Assembly (ME)");
        if (Config.legacyWirelessInputHatchMeId != Config.wirelessUnifiedOutputAssemblyMeId) {
            GregTechAPI.METATILEENTITIES[Config.legacyWirelessInputHatchMeId] = new MTELegacyWirelessInputHatchME(
                Config.legacyWirelessInputHatchMeId,
                "hatch.input.me.wireless.legacy",
                "Legacy Wireless Input Hatch (ME)");
        }
    }
}
