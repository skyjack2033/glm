package com.github.skyjack2033.wirelessmehatch.me;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.AEApi;
import appeng.api.exceptions.FailedConnection;
import appeng.api.features.ILocatable;
import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.me.helpers.AENetworkProxy;
import appeng.me.helpers.IGridProxyable;
import cpw.mods.fml.common.FMLLog;

/**
 * Manages a wireless (invisible) grid connection between a hatch's own {@link AENetworkProxy} node and a bound AE2
 * Wireless Access Point's node, using {@code AEApi.instance().createGridConnection}. Cross-dimension capable; no range
 * check.
 *
 * Lifecycle mirrors AE2's QuantumBridge / P2P ME tunnel pattern: hold the IGridConnection, verify on tick, destroy on
 * invalidate or rebind.
 */
public class WirelessGridManager {

    private static final String NBT_KEY = "boundWapSerial";

    private final IGridProxyable host;
    private final Runnable onConnectionChanged;
    private long boundWapSerial = 0L;
    private IGridConnection connection;
    private int checkCooldown = 0;

    public WirelessGridManager(IGridProxyable host, Runnable onConnectionChanged) {
        this.host = host;
        this.onConnectionChanged = onConnectionChanged;
    }

    /** @return the bound WAP serial, or 0 if unbound. */
    public long getBoundWapSerial() {
        return boundWapSerial;
    }

    /** @return true if an active grid connection to the bound network currently exists. */
    public boolean isConnected() {
        return connection != null;
    }

    /** Bind to a WAP serial (0 to unbind). Re-establishes the connection if already connected. */
    public void bind(long serial) {
        if (serial == boundWapSerial) return;
        destroyConnection();
        boundWapSerial = serial;
        establishConnection();
        onConnectionChanged.run();
    }

    /** Unbind and tear down the connection. */
    public void unbind() {
        bind(0L);
    }

    /** Called every tile tick (throttled internally) to verify and re-establish the connection. */
    public void tickCheck() {
        if (checkCooldown-- > 0) return;
        checkCooldown = 20; // check once per second
        if (boundWapSerial == 0L) return;
        if (connection == null) {
            establishConnection();
        }
    }

    /** Tear down everything. Called when the host tile is invalidated or chunk unloaded. */
    public void invalidate() {
        destroyConnection();
    }

    public void writeToNBT(NBTTagCompound tag) {
        tag.setLong(NBT_KEY, boundWapSerial);
    }

    public void readFromNBT(NBTTagCompound tag) {
        boundWapSerial = tag.getLong(NBT_KEY);
    }

    private void establishConnection() {
        if (boundWapSerial == 0L) return;
        destroyConnection();
        ILocatable target = AEApi.instance()
            .registries()
            .locatable()
            .getLocatableBy(boundWapSerial);
        if (!(target instanceof IGridHost gridHost)) {
            return; // WAP not loaded / destroyed - will retry on next tickCheck
        }
        IGridNode remoteNode = gridHost.getGridNode(ForgeDirection.UNKNOWN);
        IGridNode localNode = getLocalNode();
        if (remoteNode == null || localNode == null) return;
        try {
            connection = AEApi.instance()
                .createGridConnection(localNode, remoteNode);
            onConnectionChanged.run();
        } catch (FailedConnection failed) {
            // Colour mismatch or security rule - log and leave disconnected; will retry on next tickCheck
            FMLLog.warning(
                "[WirelessMEHatch] Failed to establish grid connection to WAP serial %d: %s",
                boundWapSerial,
                failed.getMessage());
        }
    }

    private void destroyConnection() {
        if (connection != null) {
            connection.destroy();
            connection = null;
            onConnectionChanged.run();
        }
    }

    private IGridNode getLocalNode() {
        try {
            return host.getProxy()
                .getNode();
        } catch (Exception ignored) {
            return null;
        }
    }
}
