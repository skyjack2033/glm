package com.github.skyjack2033.wirelessmehatch.me;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.ForgeDirection;

import com.github.skyjack2033.wirelessmehatch.WirelessMEHatch;

import appeng.api.AEApi;
import appeng.api.exceptions.FailedConnection;
import appeng.api.features.ILocatable;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.util.AECableType;
import appeng.api.util.DimensionalCoord;
import appeng.me.helpers.AENetworkProxy;
import appeng.me.helpers.IGridProxyable;
import appeng.tile.misc.TileSecurity;
import appeng.tile.networking.TileController;
import appeng.tile.networking.TileWirelessBase;

public final class WirelessLinkManager {

    private static final String KEY_TARGET = "wirelessTarget";
    private static final String KEY_PROXY = "wirelessLink.proxy";
    private static final String LEGACY_KEY_BOUND = "bound";
    private static final String LEGACY_KEY_DIM = "ctrlDim";
    private static final String LEGACY_KEY_X = "ctrlX";
    private static final String LEGACY_KEY_Y = "ctrlY";
    private static final String LEGACY_KEY_Z = "ctrlZ";

    private final IGridProxyable host;
    private final ProxyContext context;
    private final Runnable onChanged;

    private AENetworkProxy proxy;
    private WirelessLinkTarget target;
    private IGridConnection connection;
    private int tickCountdown;
    private String lastFailure = "";

    public WirelessLinkManager(IGridProxyable host, ProxyContext context, Runnable onChanged) {
        this.host = host;
        this.context = context;
        this.onChanged = onChanged;
    }

    public AENetworkProxy getProxy() {
        if (proxy == null) {
            proxy = new AENetworkProxy(host, "proxy", context.getProxyItem(), true);
            proxy.setFlags(GridFlags.REQUIRE_CHANNEL);
            proxy.setValidSides(context.getValidSides());
        }
        return proxy;
    }

    public void onReady() {
        getProxy().onReady();
        applyOwnerPlayerId();
        tickCheck(true);
    }

    public void bind(WirelessLinkTarget newTarget) {
        target = newTarget;
        destroyConnection();
        applyOwnerPlayerId();
        tickCheck(true);
        markChanged();
    }

    public void clear() {
        target = null;
        lastFailure = "";
        destroyConnection();
        markChanged();
    }

    public boolean hasTarget() {
        return target != null;
    }

    public WirelessLinkTarget getTarget() {
        return target;
    }

    public boolean isConnected() {
        return connection != null && isConnectionAlive();
    }

    public String getLastFailure() {
        return lastFailure;
    }

    public void tickCheck() {
        tickCheck(false);
    }

    public void tickCheck(boolean force) {
        if (!force && tickCountdown-- > 0) return;
        tickCountdown = 20;
        if (target == null) return;
        if (connection != null && isConnectionAlive()) return;
        destroyConnection();
        establishConnection();
    }

    public void invalidate() {
        destroyConnection();
        if (proxy != null) {
            proxy.invalidate();
        }
    }

    public void onChunkUnload() {
        destroyConnection();
        if (proxy != null) {
            proxy.onChunkUnload();
        }
    }

    public void writeToNBT(NBTTagCompound tag) {
        if (target != null) {
            NBTTagCompound targetTag = new NBTTagCompound();
            target.writeToNBT(targetTag);
            tag.setTag(KEY_TARGET, targetTag);
        }
        IGridNode node = getLocalNode();
        if (node != null) {
            node.saveToNBT(KEY_PROXY, tag);
        }
    }

    public void readFromNBT(NBTTagCompound tag) {
        if (tag.hasKey(KEY_TARGET)) {
            target = WirelessLinkTarget.readFromNBT(tag.getCompoundTag(KEY_TARGET));
        } else if (tag.getBoolean(LEGACY_KEY_BOUND)) {
            target = new WirelessLinkTarget(
                WirelessLinkTarget.AnchorType.ME_CONTROLLER,
                tag.getInteger(LEGACY_KEY_DIM),
                tag.getInteger(LEGACY_KEY_X),
                tag.getInteger(LEGACY_KEY_Y),
                tag.getInteger(LEGACY_KEY_Z),
                0L,
                null,
                "");
        }
        IGridNode node = getLocalNode();
        if (node != null) {
            node.loadFromNBT(KEY_PROXY, tag);
        }
    }

    private void establishConnection() {
        IGridNode localNode = getLocalNode();
        IGridNode remoteNode = resolveRemoteNode();
        if (localNode == null || remoteNode == null) return;
        try {
            connection = AEApi.instance()
                .createGridConnection(localNode, remoteNode);
            lastFailure = "";
            markChanged();
        } catch (FailedConnection failed) {
            lastFailure = failed.getMessage();
            WirelessMEHatch.LOG
                .warn("Could not create wireless ME connection to {}: {}", target.describe(), lastFailure);
        }
    }

    private boolean isConnectionAlive() {
        if (connection == null) return false;
        IGridNode localNode = getLocalNode();
        IGridNode remoteNode = resolveRemoteNode();
        if (localNode == null || remoteNode == null) return false;
        IGridNode a = connection.a();
        IGridNode b = connection.b();
        return (a == localNode && b == remoteNode) || (a == remoteNode && b == localNode);
    }

    private IGridNode getLocalNode() {
        try {
            return getProxy().getNode();
        } catch (Exception ignored) {
            return null;
        }
    }

    private IGridNode resolveRemoteNode() {
        if (target == null) return null;
        TileEntity tile = resolveTargetTile();
        if (tile instanceof IGridHost gridHost) {
            return gridHost.getGridNode(ForgeDirection.UNKNOWN);
        }
        lastFailure = "Target is not an AE2 grid host";
        return null;
    }

    private TileEntity resolveTargetTile() {
        if (target.getAnchorType() == WirelessLinkTarget.AnchorType.SECURITY_TERMINAL
            && target.getLocatableSerial() != 0L) {
            try {
                ILocatable locatable = AEApi.instance()
                    .registries()
                    .locatable()
                    .getLocatableBy(target.getLocatableSerial());
                if (locatable instanceof TileSecurity security) {
                    return security;
                }
            } catch (Throwable ignored) {}
        }
        World world = DimensionManager.getWorld(target.getDimensionId());
        if (world == null) {
            lastFailure = "Target dimension is not loaded";
            return null;
        }
        TileEntity tile = world.getTileEntity(target.getX(), target.getY(), target.getZ());
        if (target.getAnchorType() == WirelessLinkTarget.AnchorType.ME_CONTROLLER
            && !(tile instanceof TileController)) {
            lastFailure = "Target controller is missing";
            return null;
        }
        if (target.getAnchorType() == WirelessLinkTarget.AnchorType.SECURITY_TERMINAL
            && !(tile instanceof TileSecurity)) {
            lastFailure = "Target security terminal is missing";
            return null;
        }
        if (target.getAnchorType() == WirelessLinkTarget.AnchorType.WIRELESS_CONNECTOR
            && !(tile instanceof TileWirelessBase)) {
            lastFailure = "Target wireless connector is missing";
            return null;
        }
        return tile;
    }

    private void applyOwnerPlayerId() {
        if (target != null) {
            PlayerIdResolver.applyOwnerPlayerId(host, target.getOwnerUuid(), target.getOwnerName());
        }
    }

    private void destroyConnection() {
        if (connection != null) {
            try {
                connection.destroy();
            } catch (Exception ignored) {}
            connection = null;
        }
    }

    private void markChanged() {
        if (onChanged != null) {
            onChanged.run();
        }
    }

    public interface ProxyContext {

        net.minecraft.item.ItemStack getProxyItem();

        java.util.EnumSet<ForgeDirection> getValidSides();

        DimensionalCoord getLocation();

        AECableType getCableType(ForgeDirection side);
    }
}
