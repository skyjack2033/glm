package com.github.skyjack2033.wirelessmehatch.mixin;

import net.minecraftforge.fluids.FluidStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.github.skyjack2033.wirelessmehatch.output.SharedFluidVoidProtectionHelper;

import gregtech.api.interfaces.tileentity.IVoidable;
import it.unimi.dsi.fastutil.ints.Int2IntFunction;

@Mixin(targets = "gregtech.api.util.VoidProtectionHelper", remap = false)
public abstract class VoidProtectionHelperMixin {

    @Shadow(remap = false)
    private IVoidable machine;

    @Shadow(remap = false)
    private FluidStack[] fluidOutputs;

    @Shadow(remap = false)
    private int maxParallel;

    @Shadow(remap = false)
    private Int2IntFunction fluidOutputChanceGetter;

    @Inject(
        method = "calculateMaxFluidParallels()I",
        at = @At("RETURN"),
        cancellable = true,
        require = 1,
        remap = false)
    private void wirelessmehatch$limitSharedFluidCapacity(CallbackInfoReturnable<Integer> callback) {
        Integer limited = SharedFluidVoidProtectionHelper
            .limit(machine, fluidOutputs, maxParallel, fluidOutputChanceGetter);
        if (limited != null) callback.setReturnValue(limited);
    }
}
