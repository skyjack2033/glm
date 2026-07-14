package com.github.skyjack2033.wirelessmehatch;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

public class Config {

    public static int wirelessUnifiedOutputAssemblyMeId = 31701;
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
