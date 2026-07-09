package com.github.skyjack2033.wirelessmehatch.me;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import com.github.skyjack2033.wirelessmehatch.WirelessMEHatch;
import com.github.skyjack2033.wirelessmehatch.api.IWirelessMEHatch;

import appeng.api.AEApi;
import appeng.api.implementations.items.IMemoryCard;
import appeng.api.implementations.items.MemoryCardMessages;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.pathing.ControllerState;
import appeng.api.networking.pathing.IPathingGrid;
import appeng.me.helpers.IGridProxyable;
import appeng.tile.networking.TileController;
import cpw.mods.fml.common.eventhandler.Event;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Handles the wireless ME binding flow via AE2 Memory Card:
 *
 * 1. Sneak-right-click an AE2 ME Controller with a Memory Card -> record controller coordinates + player identity.
 * 2. Right-click a wireless hatch with the bound card -> establish a grid connection to the controller.
 * 3. After binding, the card data is cleared (one-time use per binding).
 * 4. Screwdriver-right-click a wireless hatch -> unbind, and clear the card data if held.
 */
public class WapInteractionHandler {

    private static final String CONFIG_KEY = "itemGroup.wirelessmehatch.wap_binding";
    private static final String DATA_KEY_CTRL_DIM = "ctrlDim";
    private static final String DATA_KEY_CTRL_X = "ctrlX";
    private static final String DATA_KEY_CTRL_Y = "ctrlY";
    private static final String DATA_KEY_CTRL_Z = "ctrlZ";
    private static final String DATA_KEY_PLAYER_NAME = "playerName";
    private static final String DATA_KEY_PLAYER_UUID = "playerUUID";

    /**
     * High-priority handler: intercepts sneak-right-click on ME Controller with a Memory Card BEFORE AE2's own
     * ToolMemoryCard.onItemUse can process (and clear) the card. By setting both useBlock and useItem to DENY at
     * HIGHEST priority, we prevent AE2 from seeing the interaction at all.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.action != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK) return;
        if (!event.entityPlayer.isSneaking()) return;

        ItemStack held = event.entityPlayer.getHeldItem();
        if (held == null || !(held.getItem() instanceof IMemoryCard)) return;

        TileEntity te = event.world.getTileEntity(event.x, event.y, event.z);
        if (!(te instanceof TileController)) return;

        // Fully cancel the interaction BEFORE AE2's ToolMemoryCard can process it.
        // This prevents AE2 from clearing the card data (its sneak-right-click-on-block behavior).
        event.setCanceled(true);
        event.useBlock = Event.Result.DENY;
        event.useItem = Event.Result.DENY;

        // Only process on server side - client just sees the cancellation.
        if (event.world.isRemote) return;

        IGridHost gridHost = (IGridHost) te;
        IGridNode ctrlNode = gridHost.getGridNode(ForgeDirection.UNKNOWN);
        if (ctrlNode == null) return;

        // Record controller coordinates and player identity onto the memory card.
        IMemoryCard card = (IMemoryCard) held.getItem();
        NBTTagCompound data = new NBTTagCompound();
        data.setInteger(DATA_KEY_CTRL_DIM, te.getWorldObj().provider.dimensionId);
        data.setInteger(DATA_KEY_CTRL_X, te.xCoord);
        data.setInteger(DATA_KEY_CTRL_Y, te.yCoord);
        data.setInteger(DATA_KEY_CTRL_Z, te.zCoord);
        data.setString(DATA_KEY_PLAYER_NAME, event.entityPlayer.getCommandSenderName());
        data.setString(
            DATA_KEY_PLAYER_UUID,
            event.entityPlayer.getUniqueID()
                .toString());
        card.setMemoryCardContents(held, CONFIG_KEY, data);
        card.notifyUser(event.entityPlayer, MemoryCardMessages.SETTINGS_SAVED);

        // Show channel info to the player (using the efficient AE2/Waila method).
        String channelInfo = getChannelInfoString(ctrlNode);
        event.entityPlayer
            .addChatMessage(new ChatComponentText("\u00A7a[Wireless ME Hatch] ME Controller bound." + channelInfo));
    }

    /**
     * Called when a player right-clicks a wireless hatch with a Memory Card. Reads the controller coordinates from the
     * card, resolves the controller tile entity, and establishes a grid connection.
     *
     * @return true if binding occurred.
     */
    public static boolean bindHatchFromCard(IWirelessMEHatch hatch, ItemStack cardStack, EntityPlayer player) {
        if (!(cardStack.getItem() instanceof IMemoryCard card)) return false;
        NBTTagCompound data = card.getData(cardStack);
        if (data == null || !data.hasKey(DATA_KEY_CTRL_X)) {
            card.notifyUser(player, MemoryCardMessages.INVALID_MACHINE);
            return false;
        }

        int dim = data.getInteger(DATA_KEY_CTRL_DIM);
        int x = data.getInteger(DATA_KEY_CTRL_X);
        int y = data.getInteger(DATA_KEY_CTRL_Y);
        int z = data.getInteger(DATA_KEY_CTRL_Z);

        World world = DimensionManager.getWorld(dim);
        if (world == null) {
            card.notifyUser(player, MemoryCardMessages.INVALID_MACHINE);
            return false;
        }

        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof IGridHost gridHost)) {
            card.notifyUser(player, MemoryCardMessages.INVALID_MACHINE);
            return false;
        }

        IGridNode remoteNode = gridHost.getGridNode(ForgeDirection.UNKNOWN);
        if (remoteNode == null) {
            card.notifyUser(player, MemoryCardMessages.INVALID_MACHINE);
            return false;
        }

        if (!(hatch instanceof IGridProxyable proxyable)) {
            card.notifyUser(player, MemoryCardMessages.INVALID_MACHINE);
            return false;
        }

        IGridNode localNode = proxyable.getProxy()
            .getNode();
        if (localNode == null) {
            card.notifyUser(player, MemoryCardMessages.INVALID_MACHINE);
            return false;
        }

        try {
            AEApi.instance()
                .createGridConnection(localNode, remoteNode);
        } catch (Exception failed) {
            WirelessMEHatch.LOG.warn("Failed to establish grid connection to ME controller: {}", failed.getMessage());
            card.notifyUser(player, MemoryCardMessages.INVALID_MACHINE);
            return false;
        }

        // Apply player identity for AE2 security.
        if (data.hasKey(DATA_KEY_PLAYER_UUID)) {
            try {
                java.util.UUID uuid = java.util.UUID.fromString(data.getString(DATA_KEY_PLAYER_UUID));
                String name = data.hasKey(DATA_KEY_PLAYER_NAME) ? data.getString(DATA_KEY_PLAYER_NAME)
                    : uuid.toString();
                int playerId = appeng.core.worlddata.WorldData.instance()
                    .playerData()
                    .getPlayerID(new com.mojang.authlib.GameProfile(uuid, name));
                if (playerId != 0) {
                    localNode.setPlayerID(playerId);
                }
            } catch (Throwable ignored) {}
        }

        // Mark the hatch as bound.
        hatch.setBoundWapSerial(encodeCoords(dim, x, y, z));

        card.notifyUser(player, MemoryCardMessages.SETTINGS_LOADED);

        // Clear the card data (one-time use per binding).
        card.setMemoryCardContents(cardStack, CONFIG_KEY, new NBTTagCompound());

        return true;
    }

    /**
     * Called when a player screwdriver-right-clicks a wireless hatch. Unbinds the hatch and clears the card if held.
     */
    public static void unbindHatch(IWirelessMEHatch hatch, EntityPlayer player) {
        hatch.setBoundWapSerial(0L);
        // If the player holds a memory card, clear its data too.
        ItemStack held = player.getHeldItem();
        if (held != null && held.getItem() instanceof IMemoryCard card) {
            card.setMemoryCardContents(held, CONFIG_KEY, new NBTTagCompound());
            card.notifyUser(player, MemoryCardMessages.SETTINGS_CLEARED);
        }
    }

    /**
     * Get channel info string using the AE2/Waila official method: only inspect the controller node's direct
     * connections (O(connections), typically 1-6), NOT the entire grid. This is the same pattern used by
     * {@code IUsedChannelProvider} and {@code TileWirelessBase.getUsedChannels()}.
     *
     * Returns a string like " Channels: 3/8 (remaining: 5)" or " Channels: disabled".
     */
    private static String getChannelInfoString(IGridNode ctrlNode) {
        try {
            IGrid grid = ctrlNode.getGrid();
            if (grid == null) return "";

            IPathingGrid pathing = grid.getCache(IPathingGrid.class);
            if (pathing == null) return "";

            if (pathing.getControllerState() == ControllerState.NO_CONTROLLER) {
                return " (no controller on network)";
            }

            // Used channels: max of all direct connection's getUsedChannels() -- same as Waila/IUsedChannelProvider.
            int used = 0;
            for (IGridConnection conn : ctrlNode.getConnections()) {
                used = Math.max(used, conn.getUsedChannels());
            }

            // Max channels: from the node itself (8 for standard, 32 for dense, MAX for creative).
            int max = 8;
            if (ctrlNode instanceof appeng.me.GridNode gn) {
                int calculated = gn.getMaxChannels();
                if (calculated > 0) {
                    max = calculated;
                }
            }

            int remaining = max - used;
            return " Channels: " + used + "/" + max + " (remaining: " + remaining + ")";
        } catch (Exception ignored) {
            return "";
        }
    }

    /**
     * Encode controller coordinates into a single long for IWirelessMEHatch.setBoundWapSerial.
     */
    private static long encodeCoords(int dim, int x, int y, int z) {
        return ((long) (dim & 0xFF) << 32) | ((long) (x & 0xFFFF) << 16) | (long) (z & 0xFFFF);
    }
}
