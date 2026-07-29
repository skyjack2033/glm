package com.github.skyjack2033.wirelessmehatch.mixin;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.Vec3;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.github.skyjack2033.wirelessmehatch.terminal.CombinedTerminalActivationHandler;

@Mixin(targets = "appeng.parts.reporting.AbstractPartTerminal", remap = false)
public abstract class CombinedTerminalActivationMixin {

    @Inject(
        method = "onPartActivate(Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/util/Vec3;)Z",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/util/Platform;openGUI(Lnet/minecraft/entity/player/EntityPlayer;"
                + "Lnet/minecraft/tileentity/TileEntity;Lnet/minecraftforge/common/util/ForgeDirection;"
                + "Lappeng/core/sync/GuiBridge;)V"),
        cancellable = true,
        require = 1,
        remap = false)
    private void wirelessmehatch$openCombinedTerminal(EntityPlayer player, Vec3 hitVec,
        CallbackInfoReturnable<Boolean> callback) {
        if (CombinedTerminalActivationHandler.tryOpen(this, player, hitVec)) {
            callback.setReturnValue(true);
        }
    }
}
