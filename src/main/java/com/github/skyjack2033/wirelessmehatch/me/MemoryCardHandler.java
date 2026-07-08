package com.github.skyjack2033.wirelessmehatch.me;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.github.skyjack2033.wirelessmehatch.api.IWirelessMEHatch;

import appeng.api.implementations.items.IMemoryCard;
import appeng.api.implementations.items.MemoryCardMessages;

/**
 * Handles binding a wireless hatch to an AE2 Wireless Access Point via the AE2 Memory Card.
 *
 * Flow:
 * 1. Sneak-right-click a WAP with a Memory Card -> record WAP's locatable serial on the card.
 * 2. Right-click a wireless hatch with the card -> bind the hatch to that serial.
 * 3. Screwdriver-right-click a wireless hatch -> unbind.
 */
public final class MemoryCardHandler {

    private static final String CONFIG_KEY = "wirelessmehatch.wap_binding";
    private static final String DATA_KEY = "wapSerial";

    private MemoryCardHandler() {}

    /**
     * Called when a player sneak-right-clicks a WAP with a Memory Card.
     * Records the WAP's locatable serial onto the card.
     *
     * @return true if the serial was recorded (card was a valid Memory Card and WAP had a serial).
     */
    public static boolean recordWapSerial(ItemStack cardStack, long wapSerial, EntityPlayer player) {
        if (!(cardStack.getItem() instanceof IMemoryCard card)) return false;
        if (wapSerial == 0L) return false;
        NBTTagCompound data = new NBTTagCompound();
        data.setLong(DATA_KEY, wapSerial);
        card.setMemoryCardContents(cardStack, CONFIG_KEY, data);
        card.notifyUser(player, MemoryCardMessages.SETTINGS_SAVED);
        return true;
    }

    /**
     * Called when a player right-clicks a wireless hatch with a Memory Card.
     * Binds the hatch to the serial recorded on the card.
     *
     * @return true if binding occurred.
     */
    public static boolean bindHatchFromCard(IWirelessMEHatch hatch, ItemStack cardStack, EntityPlayer player) {
        if (!(cardStack.getItem() instanceof IMemoryCard card)) return false;
        NBTTagCompound data = card.getData(cardStack);
        if (data == null || !data.hasKey(DATA_KEY)) {
            card.notifyUser(player, MemoryCardMessages.INVALID_MACHINE);
            return false;
        }
        long serial = data.getLong(DATA_KEY);
        if (serial == 0L) {
            card.notifyUser(player, MemoryCardMessages.INVALID_MACHINE);
            return false;
        }
        hatch.setBoundWapSerial(serial);
        card.notifyUser(player, MemoryCardMessages.SETTINGS_LOADED);
        return true;
    }

    /**
     * Called when a player screwdriver-right-clicks a wireless hatch.
     * Unbinds the hatch (sets serial to 0).
     */
    public static void unbindHatch(IWirelessMEHatch hatch, EntityPlayer player) {
        hatch.setBoundWapSerial(0L);
    }
}
