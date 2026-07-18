package com.github.skyjack2033.wirelessmehatch.me;

import java.util.UUID;

import com.github.skyjack2033.wirelessmehatch.WirelessMEHatch;
import com.mojang.authlib.GameProfile;

import appeng.api.networking.IGridNode;
import appeng.core.worlddata.WorldData;
import appeng.me.helpers.AENetworkProxy;
import appeng.me.helpers.IGridProxyable;
import cpw.mods.fml.common.FMLLog;

/**
 * Resolves the AE2 integer player ID for the player who paired the assembly and applies it to the assembly grid node,
 * so the node passes AE2 Security Terminal checks.
 *
 * <p>
 * AE2 nodes carry an integer player ID (see {@link IGridNode#setPlayerID(int)}) used by Security Terminals to decide
 * whether storage operations are allowed. The assembly constructs its proxy lazily and has no cable-side placement
 * path that can stamp the node, so the Wireless Kit binding player is persisted with the target and applied here.
 * </p>
 *
 * <p>
 * AE2's player-data registry maps a {@link GameProfile} to its integer ID. This helper builds an offline game profile
 * from the persisted binding-player UUID and name, looks up the AE2 ID, then sets it on the node.
 * </p>
 */
public final class PlayerIdResolver {

    private PlayerIdResolver() {}

    /**
     * Resolve the binding player's AE2 player ID from {@code ownerUuid}/{@code ownerName} and apply it to the grid node
     * behind {@code host}'s proxy. Safe to call after {@link AENetworkProxy#onReady()}; no-op if the player is unknown
     * or the node/registry is unavailable.
     *
     * @param host      the {@link IGridProxyable} whose proxy/node should be stamped with the player ID
     * @param ownerUuid the persisted binding-player UUID (may be {@code null})
     * @param ownerName the persisted binding-player name (may be {@code null}); only used for the game profile
     */
    public static void applyOwnerPlayerId(IGridProxyable host, UUID ownerUuid, String ownerName) {
        if (ownerUuid == null) return;
        AENetworkProxy proxy;
        IGridNode node;
        try {
            proxy = host.getProxy();
            node = proxy == null ? null : proxy.getNode();
        } catch (Exception ignored) {
            return;
        }
        if (node == null) return;
        try {
            String name = ownerName != null ? ownerName : ownerUuid.toString();
            int playerId = WorldData.instance()
                .playerData()
                .getPlayerID(new GameProfile(ownerUuid, name));
            node.setPlayerID(playerId);
        } catch (Throwable t) {
            // AE2 player-data registry can be unavailable very early or in degenerate setups; never let this break the
            // tile's first tick.
            FMLLog.warning(
                "[WirelessMEHatch] Could not resolve AE2 player ID for binding player %s (%s): %s",
                ownerName,
                ownerUuid,
                t.toString());
            WirelessMEHatch.LOG.warn(
                "Could not resolve AE2 player ID for binding player {} ({}): {}",
                ownerName,
                ownerUuid,
                t.toString());
        }
    }

}
