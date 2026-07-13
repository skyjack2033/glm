package com.github.skyjack2033.wirelessmehatch.output;

import java.util.List;

import com.github.skyjack2033.wirelessmehatch.api.WirelessDualRoleOutput;

import gregtech.api.interfaces.IOutputBus;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchOutput;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.base.MTESteamMultiBlockBase;

public final class DualRoleOutputBusHelper {

    private DualRoleOutputBusHelper() {}

    public static void appendIdentity(List<IOutputBus> busses, IOutputBus candidate) {
        for (IOutputBus existing : busses) {
            if (existing == candidate) return;
        }
        busses.add(candidate);
    }

    static boolean isCompleteDualRoleOutput(Object candidate) {
        return candidate instanceof WirelessDualRoleOutput && candidate instanceof MTEHatchOutput
            && candidate instanceof IOutputBus;
    }

    public static List<IOutputBus> augment(MTEMultiBlockBase controller, List<IOutputBus> busses) {
        return augment(controller.mOutputHatches, busses);
    }

    static List<IOutputBus> augment(List<MTEHatchOutput> outputHatches, List<IOutputBus> busses) {
        for (MTEHatchOutput hatch : outputHatches) {
            if (hatch != null && hatch.isValid() && isCompleteDualRoleOutput(hatch)) {
                appendIdentity(busses, (IOutputBus) hatch);
            }
        }
        return busses;
    }

    public static Boolean registerSteamOutput(MTESteamMultiBlockBase<?> controller, IGregTechTileEntity tile,
        int casingIndex) {
        return registerSteamOutput(tile, casingIndex, controller::addOutputHatchToMachineList);
    }

    static Boolean registerSteamOutput(IGregTechTileEntity tile, int casingIndex, SteamOutputRegistrar registrar) {
        if (tile == null || !isCompleteDualRoleOutput(tile.getMetaTileEntity())) return null;
        return registrar.add(tile, casingIndex);
    }
}

@FunctionalInterface
interface SteamOutputRegistrar {

    boolean add(IGregTechTileEntity tile, int casingIndex);
}
