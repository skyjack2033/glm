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

    private static final String CONFIG_KEY = "itemGroup.wirelessmehatch.wap_binding";
    private static final String DATA_KEY = "wapSerial";
    private static final String PLAYER_ID_KEY = "playerId";
    private static final String PLAYER_NAME_KEY = "playerName";

    private MemoryCardHandler() {}

    /**
     * Called when a player sneak-right-clicks a WAP with a Memory Card.
     * Records the WAP's locatable serial and the player's identity onto the card.
     *
     * @return true if the serial was recorded (card was a valid Memory Card and WAP had a serial).
     */
    public static boolean recordWapSerial(ItemStack cardStack, long wapSerial, EntityPlayer player) {
        if (!(cardStack.getItem() instanceof IMemoryCard card)) return false;
        if (wapSerial == 0L) return false;
        NBTTagCompound data = new NBTTagCompound();
        data.setLong(DATA_KEY, wapSerial);
        // Store the player's identity for AE2 security system compatibility.
        data.setString(PLAYER_NAME_KEY, player.getCommandSenderName());
        data.setString(
            PLAYER_ID_KEY,
            player.getUniqueID()
                .toString());
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
        // Apply the player identity from the card to the hatch's grid node for AE2 security.
        if (data.hasKey(PLAYER_ID_KEY) && hatch instanceof appeng.me.helpers.IGridProxyable proxyable) {
            try {
                appeng.api.networking.IGridNode node = proxyable.getProxy()
                    .getNode();
                if (node != null && data.hasKey(PLAYER_ID_KEY)) {
                    java.util.UUID uuid = java.util.UUID.fromString(data.getString(PLAYER_ID_KEY));
                    String name = data.hasKey(PLAYER_NAME_KEY) ? data.getString(PLAYER_NAME_KEY) : uuid.toString();
                    int playerId = appeng.core.worlddata.WorldData.instance()
                        .playerData()
                        .getPlayerID(new com.mojang.authlib.GameProfile(uuid, name));
                    if (playerId != 0) {
                        node.setPlayerID(playerId);
                    }
                }
            } catch (Throwable ignored) {
                // Security registration is best-effort; don't fail the binding if it doesn't work.
            }
        }
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
