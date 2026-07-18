package com.github.skyjack2033.wirelessmehatch.me;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.github.skyjack2033.wirelessmehatch.metatileentity.MTEWirelessUnifiedOutputAssemblyME;

import appeng.api.networking.security.BaseActionSource;
import appeng.api.util.DimensionalCoord;
import appeng.me.helpers.AENetworkProxy;
import appeng.tile.networking.TileWirelessBase;
import appeng.util.Platform;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

public final class WirelessKitEndpoint {

    private final TileWirelessBase wirelessBase;
    private final MTEWirelessUnifiedOutputAssemblyME assembly;

    private WirelessKitEndpoint(TileWirelessBase wirelessBase, MTEWirelessUnifiedOutputAssemblyME assembly) {
        this.wirelessBase = wirelessBase;
        this.assembly = assembly;
    }

    public static WirelessKitEndpoint fromTile(TileEntity tile) {
        if (tile instanceof TileWirelessBase wirelessBase) {
            return new WirelessKitEndpoint(wirelessBase, null);
        }
        if (tile instanceof IGregTechTileEntity gtTile) {
            IMetaTileEntity metaTileEntity = gtTile.getMetaTileEntity();
            if (metaTileEntity instanceof MTEWirelessUnifiedOutputAssemblyME assembly) {
                return new WirelessKitEndpoint(null, assembly);
            }
        }
        return null;
    }

    public static WirelessKitEndpoint fromWorld(World world, DimensionalCoord location) {
        if (world == null || location == null || world.provider.dimensionId != location.getDimension()) return null;
        return fromTile(world.getTileEntity(location.x, location.y, location.z));
    }

    public boolean isAssembly() {
        return assembly != null;
    }

    public boolean isWirelessBase() {
        return wirelessBase != null;
    }

    public TileWirelessBase getWirelessBase() {
        return wirelessBase;
    }

    public MTEWirelessUnifiedOutputAssemblyME getAssembly() {
        return assembly;
    }

    public AENetworkProxy getProxy() {
        return assembly != null ? assembly.getProxy() : wirelessBase.getProxy();
    }

    public boolean canAccess(BaseActionSource source) {
        try {
            return Platform.canAccess(getProxy(), source);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public boolean isHub() {
        return wirelessBase != null && wirelessBase.isHub();
    }

    public boolean isBound() {
        return assembly != null ? assembly.hasWirelessTarget()
            : !wirelessBase.getConnectedTiles()
                .isEmpty();
    }

    public int getFreeSlots() {
        if (wirelessBase != null) return wirelessBase.getFreeSlots();
        return assembly.hasWirelessTarget() ? 0 : 1;
    }

    public boolean canAccept(WirelessKitEndpoint source) {
        if (source == null) return false;
        if (isAssembly()) return source.isWirelessBase() && getFreeSlots() > 0;
        if (source.isAssembly()) return true;
        return wirelessBase != null && source.isWirelessBase() && wirelessBase.getFreeSlots() > 0;
    }

    public DimensionalCoord getLocation() {
        return assembly != null ? assembly.getLocation() : wirelessBase.getLocation();
    }
}
