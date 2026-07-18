package com.github.skyjack2033.wirelessmehatch.mixin;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.github.skyjack2033.wirelessmehatch.me.WirelessKitInteractionHandler;

@Mixin(targets = "appeng.items.tools.ToolWirelessKit", remap = false)
public abstract class ToolWirelessKitMixin {

    @Inject(
        method = "onItemUse(Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/player/EntityPlayer;"
            + "Lnet/minecraft/world/World;IIIIFFF)Z",
        at = @At("HEAD"),
        cancellable = true,
        require = 1,
        remap = false)
    private void wirelessmehatch$handleAssemblyEndpoint(ItemStack kit, EntityPlayer player, World world, int x, int y,
        int z, int side, float hitX, float hitY, float hitZ, CallbackInfoReturnable<Boolean> callback) {
        Boolean result = WirelessKitInteractionHandler.handleServerUse(kit, player, world, x, y, z);
        if (result != null) callback.setReturnValue(result);
    }

    @Inject(
        method = "onItemRightClick(Lnet/minecraft/item/ItemStack;Lnet/minecraft/world/World;"
            + "Lnet/minecraft/entity/player/EntityPlayer;)Lnet/minecraft/item/ItemStack;",
        at = @At("HEAD"),
        require = 1,
        remap = false)
    private void wirelessmehatch$clearPendingStateWithNativeMode(ItemStack kit, World world, EntityPlayer player,
        CallbackInfoReturnable<ItemStack> callback) {
        WirelessKitInteractionHandler.handleServerRightClick(kit, player, world);
    }
}
