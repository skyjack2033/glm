package com.github.skyjack2033.wirelessmehatch;

import com.github.skyjack2033.wirelessmehatch.loader.MetaTileEntityLoader;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;

public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
        WirelessMEHatch.LOG.info("Loading Wireless ME Hatch " + Tags.VERSION);
    }

    public void init(FMLInitializationEvent event) {
        MetaTileEntityLoader.register();
    }

    public void postInit(FMLPostInitializationEvent event) {}

    public void serverStarting(FMLServerStartingEvent event) {}
}
