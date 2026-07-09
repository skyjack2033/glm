package com.github.skyjack2033.wirelessmehatch.me;

import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import appeng.api.implementations.items.IMemoryCard;
import appeng.api.implementations.tiles.IWirelessAccessPoint;
import cpw.mods.fml.common.eventhandler.Event;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Listens for sneak-right-click on AE2 Wireless Access Points with a Memory Card, and records the WAP's locatable
 * serial plus the player's identity onto the card for later binding to a wireless hatch.
 */
public class WapInteractionHandler {

    @SubscribeEvent
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.action != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK) return;
        if (!event.entityPlayer.isSneaking()) return;

        ItemStack held = event.entityPlayer.getHeldItem();
        if (held == null || !(held.getItem() instanceof IMemoryCard)) return;

        TileEntity te = event.world.getTileEntity(event.x, event.y, event.z);
        if (!(te instanceof IWirelessAccessPoint)) return;

        IWirelessAccessPoint wap = (IWirelessAccessPoint) te;
        long serial = wap.getActionableNode() != null ? getLocatableSerial(wap) : 0L;
        if (serial == 0L) return;

        // Record the WAP serial and the player's identity onto the memory card.
        MemoryCardHandler.recordWapSerial(held, serial, event.entityPlayer);

        // Prevent the WAP from processing the sneak-right-click (e.g. opening its own UI).
        event.useBlock = Event.Result.DENY;
    }

    /**
     * Extract the locatable serial from a WAP. IWirelessAccessPoint extends IActionHost (not ILocatable directly), but
     * AE2's TileWireless implements ILocatable. We try to cast through the tile entity.
     */
    private long getLocatableSerial(IWirelessAccessPoint wap) {
        if (wap instanceof appeng.api.features.ILocatable locatable) {
            return locatable.getLocatableSerial();
        }
        return 0L;
    }
}
