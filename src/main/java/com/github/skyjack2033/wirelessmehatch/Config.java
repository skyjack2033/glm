package com.github.skyjack2033.wirelessmehatch;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

public class Config {

    public static int wirelessOutputHatchMeId = 17001;
    public static int wirelessInputHatchMeId = 17002;

    public static void synchronizeConfiguration(File configFile) {
        Configuration configuration = new Configuration(configFile);

        wirelessOutputHatchMeId = configuration.getInt(
            "wirelessOutputHatchMeId",
            "metaTileEntityIds",
            wirelessOutputHatchMeId,
            17000,
            32000,
            "MetaTileEntity ID for the Wireless Output Hatch (ME). Change if it conflicts with another mod.");

        wirelessInputHatchMeId = configuration.getInt(
            "wirelessInputHatchMeId",
            "metaTileEntityIds",
            wirelessInputHatchMeId,
            17000,
            32000,
            "MetaTileEntity ID for the Wireless Input Hatch (ME). Change if it conflicts with another mod.");

        if (configuration.hasChanged()) {
            configuration.save();
        }
    }
}
