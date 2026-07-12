package com.github.skyjack2033.wirelessmehatch.metatileentity;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.common.util.ForgeDirection;

import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchInput;

public final class MTELegacyWirelessInputHatchME extends MTEHatchInput {

    private static final int TIER = 4;

    public MTELegacyWirelessInputHatchME(int id, String name, String regionalName) {
        super(
            id,
            name,
            regionalName,
            TIER,
            new String[] { "Legacy Wireless ME Input Hatch", "This block is kept only for old save compatibility.",
                "Replace it with supported input infrastructure before running recipes." });
    }

    public MTELegacyWirelessInputHatchME(String name, int tier, String[] description, ITexture[][][] textures) {
        super(name, tier, description, textures);
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity tileEntity) {
        return new MTELegacyWirelessInputHatchME(mName, mTier, mDescriptionArray, mTextures);
    }

    @Override
    public boolean onRightclick(IGregTechTileEntity baseTile, EntityPlayer player) {
        if (baseTile != null && baseTile.isServerSide()) {
            player.addChatMessage(
                new ChatComponentText(EnumChatFormatting.YELLOW + "This legacy Wireless ME Input Hatch is disabled."));
        }
        return true;
    }

    @Override
    public void onScrewdriverRightClick(ForgeDirection side, EntityPlayer player, float x, float y, float z,
        ItemStack tool) {
        if (player != null && !player.worldObj.isRemote) {
            player.addChatMessage(
                new ChatComponentText(
                    EnumChatFormatting.YELLOW + "This legacy Wireless ME Input Hatch cannot be rebound."));
        }
    }
}
