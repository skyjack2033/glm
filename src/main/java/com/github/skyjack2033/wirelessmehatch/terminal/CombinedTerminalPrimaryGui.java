package com.github.skyjack2033.wirelessmehatch.terminal;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.container.PrimaryGui;

final class CombinedTerminalPrimaryGui extends PrimaryGui {

    CombinedTerminalPrimaryGui(ItemStack icon, TileEntity tile, ForgeDirection side) {
        super(null, icon, tile, side);
    }

    @Override
    public void open(EntityPlayer player) {
        CombinedTerminalGuiHandler.open(player, te, side);
    }
}
