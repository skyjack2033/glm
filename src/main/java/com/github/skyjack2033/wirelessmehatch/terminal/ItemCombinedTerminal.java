package com.github.skyjack2033.wirelessmehatch.terminal;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import com.github.skyjack2033.wirelessmehatch.WirelessMEHatch;

import appeng.api.AEApi;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartItem;

public final class ItemCombinedTerminal extends Item implements IPartItem {

    ItemCombinedTerminal() {
        setUnlocalizedName(WirelessMEHatch.MODID + ".combined_terminal");
        setTextureName(WirelessMEHatch.MODID + ":combined_terminal");
        setCreativeTab(CreativeTabs.tabMisc);
        setMaxStackSize(64);
    }

    @Override
    public IPart createPartFromItemStack(ItemStack stack) {
        return new PartCombinedTerminal(stack);
    }

    @Override
    public boolean onItemUse(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side,
        float hitX, float hitY, float hitZ) {
        return AEApi.instance()
            .partHelper()
            .placeBus(stack, x, y, z, side, player, world);
    }
}
