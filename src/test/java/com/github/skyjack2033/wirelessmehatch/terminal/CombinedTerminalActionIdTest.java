package com.github.skyjack2033.wirelessmehatch.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import net.minecraft.inventory.InventoryBasic;

import org.junit.Test;

public class CombinedTerminalActionIdTest {

    @Test
    public void interfaceIdsUseTheirOwnTypeBits() {
        long encoded = CombinedTerminalContainer.encodeInterfaceEntryId(42L);

        assertTrue(CombinedTerminalContainer.isInterfaceActionId(encoded));
        assertFalse(CombinedTerminalContainer.isCacheActionId(encoded));
        assertEquals(42L, CombinedTerminalContainer.decodeInterfaceEntryId(encoded));
    }

    @Test
    public void cacheCommandsDoNotCollideWithNormalOrInterfaceActions() {
        long encoded = CombinedTerminalContainer.encodeCacheCommand(PatternCacheBatchCommand.DIVIDE_5);

        assertTrue(CombinedTerminalContainer.isCacheActionId(encoded));
        assertFalse(CombinedTerminalContainer.isInterfaceActionId(encoded));
        assertEquals(PatternCacheBatchCommand.DIVIDE_5, CombinedTerminalContainer.decodeCacheCommand(encoded));
        assertFalse(CombinedTerminalContainer.isCacheActionId(0L));
        assertFalse(CombinedTerminalContainer.isInterfaceActionId(0L));
    }

    @Test
    public void unknownNegativeIdsFallBackToNativeHandling() {
        assertTrue(CombinedTerminalContainer.isCacheActionId(-1L));
        assertNull(CombinedTerminalContainer.decodeCacheCommand(-1L));
        assertFalse(CombinedTerminalContainer.isInterfaceActionId(-1L));
    }

    @Test
    public void interfaceSlotsMustExistInTheTrackedInventory() {
        InventoryBasic inventory = new InventoryBasic("patterns", false, 2);

        assertFalse(CombinedTerminalContainer.isValidInterfaceSlot(null, 0));
        assertFalse(CombinedTerminalContainer.isValidInterfaceSlot(inventory, -1));
        assertTrue(CombinedTerminalContainer.isValidInterfaceSlot(inventory, 0));
        assertTrue(CombinedTerminalContainer.isValidInterfaceSlot(inventory, 1));
        assertFalse(CombinedTerminalContainer.isValidInterfaceSlot(inventory, 2));
        assertFalse(CombinedTerminalContainer.isValidInterfaceSlot(inventory, Integer.MAX_VALUE));
    }
}
