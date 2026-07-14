package com.github.skyjack2033.wirelessmehatch.loader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.github.skyjack2033.wirelessmehatch.Config;
import com.github.skyjack2033.wirelessmehatch.metatileentity.MTEWirelessUnifiedOutputAssemblyME;

import gregtech.api.interfaces.metatileentity.IMetaTileEntity;

public class MetaTileEntityLoaderTest {

    private static final int UNIFIED_OUTPUT_ASSEMBLY_ID = 31701;
    private static final int FORMER_LEGACY_INPUT_HATCH_ID = 17002;

    @Test
    public void defaultIdIsOutsideTstAndGtNotLeisureAllocations() {
        assertEquals(UNIFIED_OUTPUT_ASSEMBLY_ID, Config.wirelessUnifiedOutputAssemblyMeId);
        assertTrue(Config.wirelessUnifiedOutputAssemblyMeId > 22794);
    }

    @Test
    public void registerChangesOnlyTheUnifiedOutputAssemblySlot() {
        IMetaTileEntity[] registry = new IMetaTileEntity[32001];
        MTEWirelessUnifiedOutputAssemblyME formerLegacySlot = assembly("former_legacy_slot");
        MTEWirelessUnifiedOutputAssemblyME registeredAssembly = assembly("registered_assembly");
        registry[FORMER_LEGACY_INPUT_HATCH_ID] = formerLegacySlot;
        int[] factoryCalls = { 0 };

        MetaTileEntityLoader.register(registry, (id, name, regionalName) -> {
            factoryCalls[0]++;
            assertEquals(UNIFIED_OUTPUT_ASSEMBLY_ID, id);
            assertEquals("hatch.output.me.wireless.unified", name);
            assertEquals("Wireless Unified Output Assembly (ME)", regionalName);
            return registeredAssembly;
        });

        assertEquals(1, factoryCalls[0]);
        assertSame(registeredAssembly, registry[UNIFIED_OUTPUT_ASSEMBLY_ID]);
        assertSame(formerLegacySlot, registry[FORMER_LEGACY_INPUT_HATCH_ID]);
        for (int id = 0; id < registry.length; id++) {
            if (id != UNIFIED_OUTPUT_ASSEMBLY_ID && id != FORMER_LEGACY_INPUT_HATCH_ID) {
                assertNull("MetaTileEntityLoader unexpectedly changed registry slot " + id, registry[id]);
            }
        }
    }

    private static MTEWirelessUnifiedOutputAssemblyME assembly(String name) {
        return new MTEWirelessUnifiedOutputAssemblyME(name, 4, new String[0], null);
    }
}
