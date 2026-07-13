package com.github.skyjack2033.wirelessmehatch.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.github.skyjack2033.wirelessmehatch.output.DualRoleOutputBusHelper;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.base.MTESteamMultiBlockBase;

@Mixin(
    targets = "gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.base.MTESteamMultiBlockBase",
    remap = false)
public abstract class MTESteamMultiBlockBaseMixin {

    @Inject(
        method = "getOutputBusses()Ljava/util/List;",
        at = @At("RETURN"),
        cancellable = true,
        require = 1,
        remap = false)
    private void wirelessmehatch$appendDualRoleOutputs(CallbackInfoReturnable<List> callback) {
        List result = callback.getReturnValue();
        DualRoleOutputBusHelper.augment((MTEMultiBlockBase) (Object) this, result);
        callback.setReturnValue(result);
    }

    @Inject(
        method = "addSteamBusOutput(Lgregtech/api/interfaces/tileentity/IGregTechTileEntity;I)Z",
        at = @At("HEAD"),
        cancellable = true,
        require = 1,
        remap = false)
    private void wirelessmehatch$registerDualRoleOutput(IGregTechTileEntity tile, int casingIndex,
        CallbackInfoReturnable<Boolean> callback) {
        Boolean handled = DualRoleOutputBusHelper
            .registerSteamOutput((MTESteamMultiBlockBase<?>) (Object) this, tile, casingIndex);
        if (handled != null) callback.setReturnValue(handled);
    }
}
