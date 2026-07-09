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
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.pathing.ControllerState;
import appeng.api.networking.pathing.IPathingGrid;
import appeng.me.helpers.IGridProxyable;
import appeng.tile.networking.TileController;
import cpw.mods.fml.common.eventhandler.Event;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Handles the wireless ME binding flow via AE2 Memory Card:
 *
 * 1. Sneak-right-click an AE2 ME Controller with a Memory Card -> record controller coordinates + player identity +
 * remaining channels.
 * 2. Right-click a wireless hatch with the bound card -> establish a grid connection to the controller.
 * 3. After binding, the card data is cleared (one-time use per binding).
 * 4. Screwdriver-right-click a wireless hatch -> unbind, and clear the card data.
 */
public class WapInteractionHandler {

    private static final String CONFIG_KEY = "itemGroup.wirelessmehatch.wap_binding";
    private static final String DATA_KEY_CTRL_DIM = "ctrlDim";
    private final String DATA_KEY_CTRL_X = "ctrlX";
    private final String DATA_KEY_CTRL_Y = "ctrlY";
    private final String DATA_KEY_CTRL_Z = "ctrlZ";
    private final String DATA_KEY_PLAYER_NAME = "playerName";
    private final String DATA_KEY_PLAYER_UUID = "playerUUID";

    @SubscribeEvent
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.action != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK) return;
        if (!event.entityPlayer.isSneaking()) return;

        ItemStack held = event.entityPlayer.getHeldItem();
        if (held == null || !(held.getItem() instanceof IMemoryCard)) return;

        TileEntity te = event.world.getTileEntity(event.x, event.y, event.z);
        if (!(te instanceof TileController)) return;

        IGridHost gridHost = (IGridHost) te;
        IGridNode ctrlNode = gridHost.getGridNode(ForgeDirection.UNKNOWN);
        if (ctrlNode == null) return;

        // Get channel info from the controller's grid.
        int[] channelInfo = getChannelInfo(ctrlNode);
        int usedChannels = channelInfo[0];
        int maxChannels = channelInfo[1];
        int remaining = maxChannels - usedChannels;

        // If no channels remain, notify and abort.
        if (remaining <= 0) {
            event.entityPlayer.addChatMessage(
                new ChatComponentText("\u00A7c[Wireless ME Hatch] This ME Controller has no available channels!"));
            event.useBlock = Event.Result.DENY;
            event.useItem = Event.Result.DENY;
            return;
        }

        // Record the controller coordinates, player identity, and channel info onto the memory card.
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

        // Show remaining channels to the player.
        event.entityPlayer.addChatMessage(
            new ChatComponentText(
                "\u00A7a[Wireless ME Hatch] ME Controller bound. Channels: " + usedChannels
                    + "/"
                    + maxChannels
                    + " (remaining: "
                    + remaining
                    + ")"));

        // Prevent the controller from processing the sneak-right-click.
        event.useBlock = Event.Result.DENY;
        event.useItem = Event.Result.DENY;
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
        if (data == null || !data.hasKey("ctrlX")) {
            card.notifyUser(player, MemoryCardMessages.INVALID_MACHINE);
            return false;
        }

        int dim = data.getInteger("ctrlDim");
        int x = data.getInteger("ctrlX");
        int y = data.getInteger("ctrlY");
        int z = data.getInteger("ctrlZ");

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
        if (data.hasKey("playerUUID")) {
            try {
                java.util.UUID uuid = java.util.UUID.fromString(data.getString("playerUUID"));
                String name = data.hasKey("playerName") ? data.getString("playerName") : uuid.toString();
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
        card.setMemoryCardContents(cardStack, "itemGroup.wirelessmehatch.wap_binding", new NBTTagCompound());

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
            card.setMemoryCardContents(held, "itemGroup.wirelessmehatch.wap_binding", new NBTTagCompound());
            card.notifyUser(player, MemoryCardMessages.SETTINGS_CLEARED);
        }
    }

    /**
     * Get channel usage info from a controller's grid node. Returns {usedChannels, maxChannels}.
     */
    private int[] getChannelInfo(IGridNode ctrlNode) {
        int[] result = new int[] { 0, 0 };
        try {
            IGrid grid = ctrlNode.getGrid();
            if (grid == null) return result;

            IPathingGrid pathing = grid.getCache(IPathingGrid.class);
            if (pathing == null || pathing.getControllerState() == ControllerState.NO_CONTROLLER) {
                return result;
            }

            // Count nodes that require a channel.
            int used = 0;
            for (IGridNode node : grid.getNodes()) {
                if (node.hasFlag(GridFlags.REQUIRE_CHANNEL)) {
                    used++;
                }
            }

            // Max channels from the controller node's pathing info.
            // In AE2 1.7.10, each controller block supports channels based on its tier.
            // GridNode.getMaxChannels() returns the max for this node's path.
            int max = 0;
            if (ctrlNode instanceof appeng.me.GridNode gn) {
                max = gn.getMaxChannels();
            }

            result[0] = used;
            result[1] = max;
        } catch (Exception ignored) {}
        return result;
    }

    /**
     * Encode controller coordinates into a single long for IWirelessMEHatch.setBoundWapSerial.
     */
    private static long encodeCoords(int dim, int x, int y, int z) {
        return ((long) (dim & 0xFF) << 32) | ((long) (x & 0xFFFF) << 16) | (long) (z & 0xFFFF);
    }
}
