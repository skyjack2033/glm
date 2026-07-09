package com.github.skyjack2033.wirelessmehatch.me;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.ForgeDirection;

import org.apache.logging.log4j.LogManager;

import appeng.api.AEApi;
import appeng.api.exceptions.FailedConnection;
import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.me.helpers.AENetworkProxy;
import appeng.me.helpers.IGridProxyable;

/**
 * Manages a wireless (invisible) grid connection between a hatch's own {@link AENetworkProxy} node and a bound AE2 ME
 * Controller, using {@code AEApi.instance().createGridConnection}. The controller is identified by its world
 * coordinates (dim, x, y, z), stored in NBT.
 *
 * <p>
 * Lifecycle: on {@code bind()}, the connection is established immediately. On {@code tickCheck()} (throttled to once
 * per second), the connection is re-validated; if stale, it is torn down and re-established on the next tick. On
 * {@code invalidate()} (tile removal / chunk unload), the connection is destroyed.
 * </p>
 */
public class WirelessGridManager {

    private static final String NBT_KEY_BOUND = "bound";
    private static final String NBT_KEY_DIM = "ctrlDim";
    private static final String NBT_KEY_X = "ctrlX";
    private static final String NBT_KEY_Y = "ctrlY";
    private static final String NBT_KEY_Z = "ctrlZ";

    private final IGridProxyable host;
    private final Runnable onConnectionChanged;

    private int ctrlDim = 0;
    private int ctrlX = 0;
    private int ctrlY = 0;
    private int ctrlZ = 0;
    private boolean bound = false;

    private IGridConnection connection;
    private int checkCooldown = 0;
    private boolean suppressCallback = false;

    public WirelessGridManager(IGridProxyable host, Runnable onConnectionChanged) {
        this.host = host;
        this.onConnectionChanged = onConnectionChanged;
    }

    /** @return packed coordinates as a long (for IWirelessMEHatch.getBoundWapSerial compatibility). */
    public long getBoundWapSerial() {
        if (!bound) return 0L;
        return ((long) (ctrlDim & 0xFF) << 48) | ((long) (ctrlX & 0xFFFF) << 32)
            | ((long) (ctrlY & 0xFF) << 16)
            | (long) (ctrlZ & 0xFFFF);
    }

    /** @return true if an active grid connection exists. */
    public boolean isConnected() {
        return connection != null;
    }

    /** Bind to an ME Controller at the given world coordinates. Pass all-zeros to unbind. */
    public void bind(int dim, int x, int y, int z) {
        boolean wasConnected = connection != null;
        suppressCallback = true;
        try {
            destroyConnection();
            ctrlDim = dim;
            ctrlX = x;
            ctrlY = y;
            ctrlZ = z;
            bound = (dim != 0 || x != 0 || y != 0 || z != 0);
            if (bound) {
                establishConnection();
            }
        } finally {
            suppressCallback = false;
        }
        if (wasConnected != (connection != null)) {
            notifyConnectionChanged();
        }
    }

    /** Legacy bind via packed serial (for IWirelessMEHatch.setBoundWapSerial). */
    public void bind(long serial) {
        if (serial == 0L) {
            bind(0, 0, 0, 0);
            return;
        }
        int dim = (int) ((serial >> 48) & 0xFF);
        int x = (int) ((serial >> 32) & 0xFFFF);
        int y = (int) ((serial >> 16) & 0xFF);
        int z = (int) (serial & 0xFFFF);
        if (x > 32767) x -= 65536;
        if (z > 32767) z -= 65536;
        bind(dim, x, y, z);
    }

    /** Unbind and tear down the connection. */
    public void unbind() {
        bind(0, 0, 0, 0);
    }

    /** Called every tile tick (throttled) to verify and re-establish the connection. */
    public void tickCheck() {
        if (checkCooldown-- > 0) return;
        checkCooldown = 20;
        if (!bound) return;
        if (connection == null) {
            establishConnection();
            return;
        }
        if (!isConnectionAlive()) {
            try {
                connection.destroy();
            } catch (Exception ignored) {}
            connection = null;
            notifyConnectionChanged();
        }
    }

    /** Tear down everything. Called when the host tile is invalidated or chunk unloaded. */
    public void invalidate() {
        destroyConnection();
    }

    public void writeToNBT(NBTTagCompound tag) {
        tag.setBoolean(NBT_KEY_BOUND, bound);
        tag.setInteger(NBT_KEY_DIM, ctrlDim);
        tag.setInteger(NBT_KEY_X, ctrlX);
        tag.setInteger(NBT_KEY_Y, ctrlY);
        tag.setInteger(NBT_KEY_Z, ctrlZ);
    }

    public void readFromNBT(NBTTagCompound tag) {
        bound = tag.getBoolean(NBT_KEY_BOUND);
        ctrlDim = tag.getInteger(NBT_KEY_DIM);
        ctrlX = tag.getInteger(NBT_KEY_X);
        ctrlY = tag.getInteger(NBT_KEY_Y);
        ctrlZ = tag.getInteger(NBT_KEY_Z);
    }

    private boolean isConnectionAlive() {
        if (connection == null) return false;
        IGridNode remoteNode = resolveControllerNode();
        IGridNode localNode = getLocalNode();
        if (remoteNode == null || localNode == null) return false;
        IGridNode connA = connection.a();
        IGridNode connB = connection.b();
        return (connA == localNode && connB == remoteNode) || (connA == remoteNode && connB == localNode);
    }

    private void establishConnection() {
        if (!bound) return;
        boolean wasConnected = connection != null;
        destroyConnection();

        IGridNode remoteNode = resolveControllerNode();
        if (remoteNode == null) return;

        IGridNode localNode = getLocalNode();
        if (localNode == null) return;

        try {
            connection = AEApi.instance()
                .createGridConnection(localNode, remoteNode);
            if (!wasConnected) {
                notifyConnectionChanged();
            }
        } catch (FailedConnection failed) {
            LogManager.getLogger("WirelessMEHatch")
                .warn(
                    "Failed to establish grid connection to ME controller at dim={} x={} y={} z={}: {}",
                    ctrlDim,
                    ctrlX,
                    ctrlY,
                    ctrlZ,
                    failed.getMessage());
        }
    }

    private IGridNode resolveControllerNode() {
        if (!bound) return null;
        World world = DimensionManager.getWorld(ctrlDim);
        if (world == null) return null;
        TileEntity te = world.getTileEntity(ctrlX, ctrlY, ctrlZ);
        if (!(te instanceof IGridHost gridHost)) return null;
        return gridHost.getGridNode(ForgeDirection.UNKNOWN);
    }

    private void destroyConnection() {
        if (connection != null) {
            try {
                connection.destroy();
            } catch (Exception ignored) {}
            connection = null;
            if (!suppressCallback) {
                notifyConnectionChanged();
            }
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

    private void notifyConnectionChanged() {
        if (!suppressCallback) {
            onConnectionChanged.run();
        }
    }
}
