package com.github.skyjack2033.wirelessmehatch.mixin;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.github.skyjack2033.wirelessmehatch.me.WapInteractionHandler;

import appeng.api.implementations.items.IMemoryCard;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.tile.networking.TileController;

/**
 * Prevents AE2's ToolMemoryCard from clearing card data when sneak-right-clicking an ME Controller. Instead, records
 * the controller's coordinates onto the card.
 */
@Mixin(targets = "appeng.items.tools.ToolMemoryCard", remap = false)
public class ToolMemoryCardMixin {

    @Inject(method = "onItemUse", at = @At("HEAD"), cancellable = true)
    private void onItemUseHead(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side,
        float hitX, float hitY, float hitZ, CallbackInfoReturnable<Boolean> cir) {
        if (!player.isSneaking()) return;

        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof TileController)) return;

        // This is a sneak-right-click on an ME Controller with (presumably) a memory card.
        // Cancel AE2's default clear behavior and delegate to our handler.
        if (stack.getItem() instanceof IMemoryCard) {
            IGridHost gridHost = (IGridHost) te;
            IGridNode ctrlNode = gridHost.getGridNode(ForgeDirection.UNKNOWN);
            if (ctrlNode != null) {
                WapInteractionHandler.recordController(stack, ctrlNode, te, player);
            }
            cir.setReturnValue(true);
        }
    }
}
