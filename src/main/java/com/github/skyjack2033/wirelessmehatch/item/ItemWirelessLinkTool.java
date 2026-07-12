package com.github.skyjack2033.wirelessmehatch.item;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;

import com.github.skyjack2033.wirelessmehatch.api.WirelessBindable;
import com.github.skyjack2033.wirelessmehatch.me.WirelessLinkTarget;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.GregTechAPI;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

public class ItemWirelessLinkTool extends Item {

    private static final String KEY_TARGET = "target";

    public ItemWirelessLinkTool() {
        setUnlocalizedName("wirelessmehatch.link_tool");
        setTextureName("wirelessmehatch:wireless_link_tool");
        setCreativeTab(GregTechAPI.TAB_GREGTECH);
        setMaxStackSize(1);
    }

    @Override
    public boolean onItemUseFirst(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side,
        float hitX, float hitY, float hitZ) {
        return useOnBlock(stack, player, world, x, y, z, false, true);
    }

    @Override
    public boolean onItemUse(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side,
        float hitX, float hitY, float hitZ) {
        return useOnBlock(stack, player, world, x, y, z, true, false);
    }

    public boolean bindTargetToAssembly(ItemStack stack, EntityPlayer player, WirelessBindable bindable) {
        WirelessLinkTarget target = getTarget(stack);
        if (target == null) {
            if (!player.worldObj.isRemote) {
                player.addChatMessage(
                    new ChatComponentText(EnumChatFormatting.RED + "No ME target stored in the link tool."));
            }
            return true;
        }
        if (!player.worldObj.isRemote && bindable.bindWirelessTarget(target)) {
            player.addChatMessage(new ChatComponentText(EnumChatFormatting.GREEN + "Bound to " + target.describe()));
        }
        return true;
    }

    public static WirelessLinkTarget getTarget(ItemStack stack) {
        if (stack == null || stack.stackTagCompound == null || !stack.stackTagCompound.hasKey(KEY_TARGET)) return null;
        return WirelessLinkTarget.readFromNBT(stack.stackTagCompound.getCompoundTag(KEY_TARGET));
    }

    private boolean useOnBlock(ItemStack stack, EntityPlayer player, World world, int x, int y, int z,
        boolean reportUnsupported, boolean onlySupported) {
        TileEntity tile = world.getTileEntity(x, y, z);
        if (onlySupported && !player.isSneaking() && !isSupportedTile(tile)) return false;
        if (world.isRemote) return true;
        if (player.isSneaking()) {
            clearTarget(stack);
            player.addChatMessage(new ChatComponentText(EnumChatFormatting.YELLOW + "Wireless link target cleared."));
            return true;
        }

        if (tile instanceof IGregTechTileEntity gtTile) {
            IMetaTileEntity mte = gtTile.getMetaTileEntity();
            if (mte instanceof WirelessBindable bindable) {
                return bindTargetToAssembly(stack, player, bindable);
            }
        }

        WirelessLinkTarget target = WirelessLinkTarget
            .forTile(tile, player.getUniqueID(), player.getCommandSenderName());
        if (target != null) {
            setTarget(stack, target);
            player.addChatMessage(new ChatComponentText(EnumChatFormatting.GREEN + "Stored " + target.describe()));
            return true;
        }

        if (reportUnsupported) {
            player.addChatMessage(
                new ChatComponentText(
                    EnumChatFormatting.RED + "Not a supported ME target or wireless output assembly."));
        }
        return reportUnsupported;
    }

    private boolean isSupportedTile(TileEntity tile) {
        if (tile instanceof IGregTechTileEntity gtTile) {
            IMetaTileEntity mte = gtTile.getMetaTileEntity();
            if (mte instanceof WirelessBindable) return true;
        }
        return WirelessLinkTarget.forTile(tile, null, "") != null;
    }

    private static void setTarget(ItemStack stack, WirelessLinkTarget target) {
        if (stack.stackTagCompound == null) {
            stack.stackTagCompound = new NBTTagCompound();
        }
        NBTTagCompound targetTag = new NBTTagCompound();
        target.writeToNBT(targetTag);
        stack.stackTagCompound.setTag(KEY_TARGET, targetTag);
    }

    private static void clearTarget(ItemStack stack) {
        if (stack.stackTagCompound != null) {
            stack.stackTagCompound.removeTag(KEY_TARGET);
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void addInformation(ItemStack stack, EntityPlayer player, List tooltip, boolean advanced) {
        WirelessLinkTarget target = getTarget(stack);
        if (target == null) {
            tooltip.add(EnumChatFormatting.GRAY + "No ME target stored");
        } else {
            tooltip.add(EnumChatFormatting.AQUA + target.describe());
        }
    }
}
