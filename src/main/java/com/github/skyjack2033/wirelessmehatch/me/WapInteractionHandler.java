package com.github.skyjack2033.wirelessmehatch.me;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;

import com.github.skyjack2033.wirelessmehatch.api.IWirelessMEHatch;

import appeng.api.implementations.items.IMemoryCard;
import appeng.api.implementations.items.MemoryCardMessages;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.pathing.ControllerState;
import appeng.api.networking.pathing.IPathingGrid;
import appeng.me.helpers.IGridProxyable;

/**
 * Handles the wireless ME binding flow via AE2 Memory Card:
 *
 * 1. Sneak-right-click an AE2 ME Controller with a Memory Card -> record controller coordinates + player identity.
 * (Implemented via ToolMemoryCardMixin to intercept AE2's own clear-on-sneak behavior.)
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
     * Called from ToolMemoryCardMixin when a player sneak-right-clicks an ME Controller with a Memory Card. Records
     * controller coordinates + player identity onto the card, replacing AE2's default clear behavior.
     */
    public static void recordController(ItemStack cardStack, IGridNode ctrlNode, TileEntity controllerTe,
        EntityPlayer player) {
        IMemoryCard card = (IMemoryCard) cardStack.getItem();
        NBTTagCompound data = new NBTTagCompound();
        data.setInteger(DATA_KEY_CTRL_DIM, controllerTe.getWorldObj().provider.dimensionId);
        data.setInteger(DATA_KEY_CTRL_X, controllerTe.xCoord);
        data.setInteger(DATA_KEY_CTRL_Y, controllerTe.yCoord);
        data.setInteger(DATA_KEY_CTRL_Z, controllerTe.zCoord);
        data.setString(DATA_KEY_PLAYER_NAME, player.getCommandSenderName());
        data.setString(
            DATA_KEY_PLAYER_UUID,
            player.getUniqueID()
                .toString());
        card.setMemoryCardContents(cardStack, CONFIG_KEY, data);
        card.notifyUser(player, MemoryCardMessages.SETTINGS_SAVED);

        // Show channel info to the player (using the efficient AE2/Waila method).
        String channelInfo = getChannelInfoString(ctrlNode);
        player.addChatMessage(new ChatComponentText("\u00A7a[Wireless ME Hatch] ME Controller bound." + channelInfo));
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

        // Verify the controller still exists at the stored coordinates.
        World world = DimensionManager.getWorld(dim);
        if (world == null) {
            card.notifyUser(player, MemoryCardMessages.INVALID_MACHINE);
            return false;
        }
        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof IGridHost)) {
            card.notifyUser(player, MemoryCardMessages.INVALID_MACHINE);
            return false;
        }

        // Apply player identity for AE2 security before binding.
        if (hatch instanceof IGridProxyable proxyable && data.hasKey(DATA_KEY_PLAYER_UUID)) {
            try {
                IGridNode localNode = proxyable.getProxy()
                    .getNode();
                if (localNode != null) {
                    java.util.UUID uuid = java.util.UUID.fromString(data.getString(DATA_KEY_PLAYER_UUID));
                    String name = data.hasKey(DATA_KEY_PLAYER_NAME) ? data.getString(DATA_KEY_PLAYER_NAME)
                        : uuid.toString();
                    int playerId = appeng.core.worlddata.WorldData.instance()
                        .playerData()
                        .getPlayerID(new com.mojang.authlib.GameProfile(uuid, name));
                    if (playerId != 0) {
                        localNode.setPlayerID(playerId);
                    }
                }
            } catch (Throwable ignored) {}
        }

        // Bind via setBoundWapSerial - WirelessGridManager.unpacks coordinates and establishes the connection.
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
     * connections (O(connections), typically 1-6), NOT the entire grid.
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

    private static long encodeCoords(int dim, int x, int y, int z) {
        return ((long) (dim & 0xFF) << 48) | ((long) (x & 0xFFFF) << 32)
            | ((long) (y & 0xFF) << 16)
            | (long) (z & 0xFFFF);
    }
}
