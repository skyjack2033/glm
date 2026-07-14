package com.github.skyjack2033.wirelessmehatch.loader;

import com.github.skyjack2033.wirelessmehatch.Config;
import com.github.skyjack2033.wirelessmehatch.metatileentity.MTEWirelessUnifiedOutputAssemblyME;

import gregtech.api.GregTechAPI;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;

public final class MetaTileEntityLoader {

    private MetaTileEntityLoader() {}

    public static void register() {
        register(GregTechAPI.METATILEENTITIES, MTEWirelessUnifiedOutputAssemblyME::new);
    }

    static void register(IMetaTileEntity[] registry, MetaTileEntityFactory factory) {
        int id = Config.wirelessUnifiedOutputAssemblyMeId;
        registry[id] = factory.create(id, "hatch.output.me.wireless.unified", "Wireless Unified Output Assembly (ME)");
    }

    @FunctionalInterface
    interface MetaTileEntityFactory {

        IMetaTileEntity create(int id, String name, String regionalName);
    }
}
