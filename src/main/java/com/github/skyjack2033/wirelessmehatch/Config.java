package com.github.skyjack2033.wirelessmehatch;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

public class Config {

    public static int wirelessUnifiedOutputAssemblyMeId = 17001;
    public static int legacyWirelessInputHatchMeId = 17002;
    public static long defaultItemCapacity = Long.MAX_VALUE;
    public static long defaultFluidCapacity = Long.MAX_VALUE;

    public static void synchronizeConfiguration(File configFile) {
        Configuration configuration = new Configuration(configFile);

        wirelessUnifiedOutputAssemblyMeId = configuration.getInt(
            "wirelessUnifiedOutputAssemblyMeId",
            "metaTileEntityIds",
            wirelessUnifiedOutputAssemblyMeId,
            17000,
            32000,
            "MetaTileEntity ID for the Wireless Unified Output Assembly (ME). Change if it conflicts with another mod.");

        int legacyInputIdDefault = configuration
            .get(
                "metaTileEntityIds",
                "wirelessInputHatchMeId",
                legacyWirelessInputHatchMeId,
                "Legacy config key for the removed Wireless Input Hatch (ME).")
            .getInt(legacyWirelessInputHatchMeId);
        legacyWirelessInputHatchMeId = configuration.getInt(
            "legacyWirelessInputHatchMeId",
            "metaTileEntityIds",
            legacyInputIdDefault,
            17000,
            32000,
            "MetaTileEntity ID reserved for old Wireless Input Hatch (ME) save compatibility.");

        defaultItemCapacity = parseLong(
            configuration
                .get(
                    "outputCapacity",
                    "defaultItemCapacity",
                    Long.toString(defaultItemCapacity),
                    "Default cached item capacity for the wireless output assembly, in item units.")
                .getString(),
            Long.MAX_VALUE);

        defaultFluidCapacity = parseLong(
            configuration
                .get(
                    "outputCapacity",
                    "defaultFluidCapacity",
                    Long.toString(defaultFluidCapacity),
                    "Default cached fluid capacity for the wireless output assembly, in millibuckets.")
                .getString(),
            Long.MAX_VALUE);

        if (configuration.hasChanged()) {
            configuration.save();
        }
    }

    private static long parseLong(String raw, long fallback) {
        try {
            return Math.max(0L, Long.parseLong(raw));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
