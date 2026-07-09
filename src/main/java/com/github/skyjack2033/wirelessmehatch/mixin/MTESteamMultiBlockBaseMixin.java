package com.github.skyjack2033.wirelessmehatch.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import gregtech.api.interfaces.IOutputBus;

/**
 * Steam multiblock machines ({@code MTESteamMultiBlockBase}) override {@code getOutputBusses()} to return only
 * {@code mSteamOutputs}, bypassing the base class. This Mixin appends our dual output hatch adapters to that return
 * list too, so the steam machine's item output pipeline can see them.
 */
@Mixin(
    targets = "gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.base.MTESteamMultiBlockBase",
    remap = false)
public class MTESteamMultiBlockBaseMixin {

    @Inject(method = "getOutputBusses", at = @At("RETURN"))
    private void wirelessmehatch$onSteamGetOutputBusses(CallbackInfoReturnable<List<IOutputBus>> cir) {
        List<IOutputBus> busses = cir.getReturnValue();
        // Access the dual output list from MTEMultiBlockBaseMixin via the shared interface.
        if (this instanceof MTEMultiBlockBaseMixinAccessor accessor) {
            List<?> dualHatches = accessor.wirelessmehatch$getDualOutputHatches();
            if (dualHatches == null || dualHatches.isEmpty()) return;
            for (Object dual : dualHatches) {
                if (dual instanceof com.github.skyjack2033.wirelessmehatch.metatileentity.MTEWirelessOutputHatchME hatch) {
                    busses
                        .add(new com.github.skyjack2033.wirelessmehatch.metatileentity.WirelessOutputBusAdapter(hatch));
                }
            }
        }
    }
}
