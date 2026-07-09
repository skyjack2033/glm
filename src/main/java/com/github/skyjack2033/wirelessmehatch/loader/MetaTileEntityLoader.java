package com.github.skyjack2033.wirelessmehatch.loader;

import com.github.skyjack2033.wirelessmehatch.metatileentity.MTEWirelessInputHatchME;
import com.github.skyjack2033.wirelessmehatch.metatileentity.MTEWirelessOutputHatchME;

import gregtech.api.GregTechAPI;

public final class MetaTileEntityLoader {

    // ID range: >= 17000, verify no collision with -Dgt.debug=true at runtime.
    // GT reserves < 2048 and 4096-6099 (Frames/Pipes). Adjust if these IDs are taken.
    public static final int WIRELESS_OUTPUT_HATCH_ME_ID = 17001;
    public static final int WIRELESS_INPUT_HATCH_ME_ID = 17002;

    private MetaTileEntityLoader() {}

    public static void register() {
        GregTechAPI.METATILEENTITIES[WIRELESS_OUTPUT_HATCH_ME_ID] = new MTEWirelessOutputHatchME(
            WIRELESS_OUTPUT_HATCH_ME_ID,
            "hatch.output.me.wireless",
            "Wireless Output Hatch (ME)");
        GregTechAPI.METATILEENTITIES[WIRELESS_INPUT_HATCH_ME_ID] = new MTEWirelessInputHatchME(
            WIRELESS_INPUT_HATCH_ME_ID,
            "hatch.input.me.wireless",
            "Wireless Input Hatch (ME)");
    }
}
