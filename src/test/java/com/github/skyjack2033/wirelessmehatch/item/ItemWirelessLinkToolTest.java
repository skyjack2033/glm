package com.github.skyjack2033.wirelessmehatch.item;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ItemWirelessLinkToolTest {

    @Test
    public void clientUseFirstDefersToServerPacket() {
        assertFalse(ItemWirelessLinkTool.shouldHandleUseFirst(true));
    }

    @Test
    public void serverUseFirstHandlesSupportedBlock() {
        assertTrue(ItemWirelessLinkTool.shouldHandleUseFirst(false));
    }
}
