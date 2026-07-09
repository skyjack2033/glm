package com.github.skyjack2033.wirelessmehatch.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import gregtech.api.util.VoidProtectionHelper;

/**
 * When the machine has dual output hatches (which accept unlimited items/fluids), skip the void-protection parallel
 * calculation entirely. This prevents "Not enough item output space" for machines whose getOutputBusses() is overridden
 * (e.g. steam machines) and thus doesn't include our dual hatch adapters.
 */
@Mixin(value = VoidProtectionHelper.class, remap = false)
public class VoidProtectionHelperMixin {

    /**
     * At the start of {@code determineParallel()}, check if the machine has dual output hatches. If so, skip the entire
     * calculation by returning early - the hatches have Long.MAX_VALUE capacity, so there is always enough space.
     */
    @Inject(method = "determineParallel", at = @At("HEAD"), cancellable = true)
    private void wirelessmehatch$onDetermineParallel(CallbackInfo ci) {
        VoidProtectionHelper self = (VoidProtectionHelper) (Object) this;
        // Access the machine field via reflection to check for dual output hatches.
        try {
            java.lang.reflect.Field machineField = VoidProtectionHelper.class.getDeclaredField("machine");
            machineField.setAccessible(true);
            Object machine = machineField.get(self);
            if (machine instanceof MTEMultiBlockBaseMixinAccessor accessor) {
                List<?> dualHatches = accessor.wirelessmehatch$getDualOutputHatches();
                if (dualHatches != null && !dualHatches.isEmpty()) {
                    // Skip void protection entirely - our hatches have unlimited capacity.
                    ci.cancel();
                }
            }
        } catch (Exception ignored) {
            // If reflection fails, fall through to normal void protection.
        }
    }
}
