package com.github.skyjack2033.wirelessmehatch.me;

import static org.junit.Assert.assertEquals;

import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;

import org.junit.Test;

public class WirelessLinkTargetTest {

    @Test
    public void wirelessConnectorAnchorRoundTripsThroughNbt() {
        assertRoundTrip("WIRELESS_CONNECTOR");
    }

    @Test
    public void legacyAnchorTypesStillRoundTripThroughNbt() {
        assertRoundTrip("ME_CONTROLLER");
        assertRoundTrip("SECURITY_TERMINAL");
    }

    private static void assertRoundTrip(String anchorTypeName) {
        WirelessLinkTarget.AnchorType anchorType = WirelessLinkTarget.AnchorType.valueOf(anchorTypeName);
        UUID ownerUuid = UUID.fromString("12345678-1234-5678-9abc-def012345678");
        WirelessLinkTarget original = new WirelessLinkTarget(
            anchorType,
            -7,
            849,
            4,
            -1432,
            9_876_543_210L,
            ownerUuid,
            "test-owner");

        NBTTagCompound tag = new NBTTagCompound();
        original.writeToNBT(tag);
        WirelessLinkTarget restored = WirelessLinkTarget.readFromNBT(tag);

        assertEquals(anchorType, restored.getAnchorType());
        assertEquals(-7, restored.getDimensionId());
        assertEquals(849, restored.getX());
        assertEquals(4, restored.getY());
        assertEquals(-1432, restored.getZ());
        assertEquals(9_876_543_210L, restored.getLocatableSerial());
        assertEquals(ownerUuid, restored.getOwnerUuid());
        assertEquals("test-owner", restored.getOwnerName());
    }
}
