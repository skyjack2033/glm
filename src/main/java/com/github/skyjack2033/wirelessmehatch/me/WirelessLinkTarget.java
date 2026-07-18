package com.github.skyjack2033.wirelessmehatch.me;

import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;

import appeng.tile.networking.TileWirelessBase;

public final class WirelessLinkTarget {

    private static final String KEY_BOUND = "bound";
    private static final String KEY_ANCHOR_TYPE = "anchorType";
    private static final String KEY_DIM = "dim";
    private static final String KEY_X = "x";
    private static final String KEY_Y = "y";
    private static final String KEY_Z = "z";
    private static final String KEY_SERIAL = "locatableSerial";
    private static final String KEY_OWNER_UUID = "ownerUuid";
    private static final String KEY_OWNER_NAME = "ownerName";

    public enum AnchorType {
        ME_CONTROLLER,
        SECURITY_TERMINAL,
        WIRELESS_CONNECTOR
    }

    private final AnchorType anchorType;
    private final int dimensionId;
    private final int x;
    private final int y;
    private final int z;
    private final long locatableSerial;
    private final UUID ownerUuid;
    private final String ownerName;

    public WirelessLinkTarget(AnchorType anchorType, int dimensionId, int x, int y, int z, long locatableSerial,
        UUID ownerUuid, String ownerName) {
        this.anchorType = anchorType;
        this.dimensionId = dimensionId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.locatableSerial = locatableSerial;
        this.ownerUuid = ownerUuid;
        this.ownerName = ownerName;
    }

    public static WirelessLinkTarget forWirelessConnector(TileWirelessBase tile, UUID ownerUuid, String ownerName) {
        if (tile == null || tile.getWorldObj() == null) return null;
        return new WirelessLinkTarget(
            AnchorType.WIRELESS_CONNECTOR,
            tile.getWorldObj().provider.dimensionId,
            tile.xCoord,
            tile.yCoord,
            tile.zCoord,
            0L,
            ownerUuid,
            ownerName);
    }

    public static WirelessLinkTarget readFromNBT(NBTTagCompound tag) {
        if (tag == null || !tag.getBoolean(KEY_BOUND)) return null;
        AnchorType anchorType;
        try {
            anchorType = AnchorType.valueOf(tag.getString(KEY_ANCHOR_TYPE));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
        UUID uuid = null;
        if (tag.hasKey(KEY_OWNER_UUID)) {
            try {
                uuid = UUID.fromString(tag.getString(KEY_OWNER_UUID));
            } catch (IllegalArgumentException ignored) {}
        }
        return new WirelessLinkTarget(
            anchorType,
            tag.getInteger(KEY_DIM),
            tag.getInteger(KEY_X),
            tag.getInteger(KEY_Y),
            tag.getInteger(KEY_Z),
            tag.getLong(KEY_SERIAL),
            uuid,
            tag.getString(KEY_OWNER_NAME));
    }

    public void writeToNBT(NBTTagCompound tag) {
        tag.setBoolean(KEY_BOUND, true);
        tag.setString(KEY_ANCHOR_TYPE, anchorType.name());
        tag.setInteger(KEY_DIM, dimensionId);
        tag.setInteger(KEY_X, x);
        tag.setInteger(KEY_Y, y);
        tag.setInteger(KEY_Z, z);
        tag.setLong(KEY_SERIAL, locatableSerial);
        if (ownerUuid != null) {
            tag.setString(KEY_OWNER_UUID, ownerUuid.toString());
        }
        tag.setString(KEY_OWNER_NAME, ownerName == null ? "" : ownerName);
    }

    public AnchorType getAnchorType() {
        return anchorType;
    }

    public int getDimensionId() {
        return dimensionId;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    public long getLocatableSerial() {
        return locatableSerial;
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public String describe() {
        return anchorType + " @ dim " + dimensionId + " (" + x + ", " + y + ", " + z + ")";
    }
}
