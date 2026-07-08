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
 *
 * <p>
 * Connection liveness (I1): AE2 may destroy a grid connection remotely (e.g. when the WAP is broken, its chunk unloads,
 * or the grid splits) without notifying this manager. To detect this, {@link #tickCheck()} re-validates the stored
 * connection each tick by re-resolving the bound WAP serial and comparing the connection's two endpoint nodes against
 * the live nodes of both endpoints - the same pattern AE2's {@code QuantumCluster.updateStatus} uses. If the connection
 * is stale, it is torn down so the next tick re-establishes it.
 * </p>
 *
 * <p>
 * Callback coalescing (I4): internal destroy→establish sequences (rebind, reconnect) temporarily suppress
 * {@link #onConnectionChanged} and fire it exactly once based on the net connectivity change, avoiding the previous
 * double-fire on rebind.
 * </p>
 */
public class WirelessGridManager {

    private static final String NBT_KEY = "boundWapSerial";

    private final IGridProxyable host;
    private final Runnable onConnectionChanged;
    private long boundWapSerial = 0L;
    private IGridConnection connection;
    private int checkCooldown = 0;

    /**
     * While true, {@link #notifyConnectionChanged()} is a no-op. Used during internal destroy→establish sequences so
     * the
     * callback fires once per net state change rather than once per internal step.
     */
    private boolean suppressCallback = false;

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
        boolean wasConnected = connection != null;
        // Suppress the per-step callback: fire once at the end based on the net change.
        suppressCallback = true;
        try {
            destroyConnection();
            boundWapSerial = serial;
            establishConnection();
        } finally {
            suppressCallback = false;
        }
        if (wasConnected != (connection != null)) {
            notifyConnectionChanged();
        }
    }

    /** Unbind and tear down the connection. */
    public void unbind() {
        bind(0L);
    }

    /**
     * Called every tile tick (throttled internally) to verify and re-establish the connection.
     *
     * <p>
     * When a connection already exists, it is re-validated: the bound WAP is re-resolved and the connection's endpoint
     * nodes are compared against the live nodes of both endpoints (mirroring {@code QuantumCluster.updateStatus}). If
     * the connection is stale (WAP gone, not an IGridHost, or its node changed), it is torn down and the callback is
     * fired; the next tick re-establishes it.
     * </p>
     */
    public void tickCheck() {
        if (checkCooldown-- > 0) return;
        checkCooldown = 20; // check once per second
        if (boundWapSerial == 0L) return;
        if (connection == null) {
            establishConnection();
            return;
        }
        if (!isConnectionAlive()) {
            // AE2 destroyed the connection remotely (or the WAP/node changed). Tear down our stale reference so the
            // next tick re-establishes, and notify the host that connectivity changed. destroy() is best-effort: if AE2
            // already tore the connection down, the call may no-op or throw, so guard it.
            try {
                connection.destroy();
            } catch (Exception ignored) {
                // Connection was already destroyed remotely - nothing to clean up.
            }
            connection = null;
            notifyConnectionChanged();
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

    /**
     * Re-resolve the bound WAP and verify the stored connection still binds the live nodes of both endpoints. Mirrors
     * {@code QuantumCluster.updateStatus}: if the WAP is gone, is no longer an {@link IGridHost}, or either endpoint's
     * current node no longer matches the connection's recorded nodes, the connection is considered stale.
     *
     * @return true if {@link #connection} still appears to be a valid, live link; false if AE2 tore it down remotely.
     */
    private boolean isConnectionAlive() {
        if (connection == null) return false;
        ILocatable target = AEApi.instance()
            .registries()
            .locatable()
            .getLocatableBy(boundWapSerial);
        if (!(target instanceof IGridHost gridHost)) {
            return false; // WAP not loaded / destroyed remotely.
        }
        IGridNode remoteNode = gridHost.getGridNode(ForgeDirection.UNKNOWN);
        IGridNode localNode = getLocalNode();
        if (remoteNode == null || localNode == null) {
            // Cannot re-resolve an endpoint - the connection cannot be trusted.
            return false;
        }
        // The connection records exactly the two nodes it links (a()/b()). If either endpoint's live node no longer
        // matches, AE2 has rebuilt the grid out from under us (e.g. the WAP was replaced).
        IGridNode connA = connection.a();
        IGridNode connB = connection.b();
        boolean endpointsMatch = (connA == localNode && connB == remoteNode)
            || (connA == remoteNode && connB == localNode);
        return endpointsMatch;
    }

    private void establishConnection() {
        if (boundWapSerial == 0L) return;
        boolean wasConnected = connection != null;
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
            if (!wasConnected) {
                notifyConnectionChanged();
            }
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
            notifyConnectionChanged();
        }
    }

    /**
     * Fire {@link #onConnectionChanged} unless {@link #suppressCallback} is set. This lets internal destroy→establish
     * sequences coalesce callbacks into a single net-state-change notification.
     */
    private void notifyConnectionChanged() {
        if (!suppressCallback) {
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
