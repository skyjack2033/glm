package com.github.skyjack2033.wirelessmehatch.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.github.skyjack2033.wirelessmehatch.output.DualRoleOutputBusHelper;

import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;

@Pseudo
@Mixin(targets = "com.science.gtnl.common.machine.multiMachineBase.SteamMultiMachineBase", remap = false)
public abstract class GTNLSteamMultiMachineBaseMixin {

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
}
