package com.github.skyjack2033.wirelessmehatch.loader;

import com.github.skyjack2033.wirelessmehatch.Config;
import com.github.skyjack2033.wirelessmehatch.metatileentity.MTEWirelessInputHatchME;
import com.github.skyjack2033.wirelessmehatch.metatileentity.MTEWirelessOutputHatchME;

import gregtech.api.GregTechAPI;

public final class MetaTileEntityLoader {

    private MetaTileEntityLoader() {}

    public static void register() {
        GregTechAPI.METATILEENTITIES[Config.wirelessOutputHatchMeId] = new MTEWirelessOutputHatchME(
            Config.wirelessOutputHatchMeId,
            "hatch.output.me.wireless",
            "Wireless Output Hatch (ME)");
        GregTechAPI.METATILEENTITIES[Config.wirelessInputHatchMeId] = new MTEWirelessInputHatchME(
            Config.wirelessInputHatchMeId,
            "hatch.input.me.wireless",
            "Wireless Input Hatch (ME)");
    }
}
