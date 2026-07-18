package com.github.skyjack2033.wirelessmehatch.mixin;

import java.util.ArrayList;

import net.minecraft.entity.player.InventoryPlayer;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.github.skyjack2033.wirelessmehatch.me.WirelessKitSuperModeHandler;

import appeng.container.implementations.ContainerWirelessKit;
import appeng.helpers.WirelessKitCommand;
import appeng.helpers.WirelessToolDataObject;
import appeng.items.contents.WirelessKitObject;

@Mixin(targets = "appeng.container.implementations.ContainerWirelessKit", remap = false)
public abstract class ContainerWirelessKitMixin {

    @Shadow
    @Final
    private WirelessKitObject toolInv;

    @Shadow
    @Final
    private ArrayList<WirelessToolDataObject> data;

    @Shadow
    public abstract void updateData();

    @Inject(
        method = "updateData()V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/nbt/NBTTagCompound;hasNoTags()Z", shift = At.Shift.BEFORE),
        require = 1,
        remap = false)
    private void wirelessmehatch$appendAssemblyData(CallbackInfo callback) {
        WirelessKitSuperModeHandler.appendAssemblyData(toolInv, data);
    }

    @Inject(
        method = "processCommand(Lappeng/helpers/WirelessKitCommand;)V",
        at = @At("HEAD"),
        cancellable = true,
        require = 1,
        remap = false)
    private void wirelessmehatch$handleAssemblyCommand(WirelessKitCommand command, CallbackInfo callback) {
        InventoryPlayer inventory = ((ContainerWirelessKit) (Object) this).getPlayerInv();
        if (WirelessKitSuperModeHandler.handleCommand(command, toolInv, data, inventory.player)) {
            updateData();
            callback.cancel();
        }
    }
}
