package com.github.skyjack2033.wirelessmehatch.me;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.UUID;

import net.minecraft.tileentity.TileEntity;

import org.junit.BeforeClass;
import org.junit.Test;

import com.github.skyjack2033.wirelessmehatch.MinecraftRegistryTestBootstrap;
import com.github.skyjack2033.wirelessmehatch.metatileentity.MTEWirelessUnifiedOutputAssemblyME;

import appeng.api.util.DimensionalCoord;
import appeng.tile.networking.TileWirelessConnector;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.metatileentity.BaseMetaTileEntity;

public class WirelessKitBindingServiceTest {

    @BeforeClass
    public static void bootstrapMinecraftRegistries() throws ReflectiveOperationException {
        MinecraftRegistryTestBootstrap.initializeVanillaItems();
    }

    @Test
    public void adapterPairRequiresExactlyOneAssembly() throws Exception {
        WirelessKitEndpoint connector = WirelessKitEndpoint.fromTile(new TileWirelessConnector());
        WirelessKitEndpoint assembly = WirelessKitEndpoint.fromTile(assemblyBase());

        assertTrue(WirelessKitBindingService.isAssemblyPair(connector, assembly));
        assertTrue(WirelessKitBindingService.isAssemblyPair(assembly, connector));
        assertFalse(WirelessKitBindingService.isAssemblyPair(connector, connector));
        assertFalse(WirelessKitBindingService.isAssemblyPair(assembly, assembly));
    }

    @Test
    public void alreadyBoundRequiresTheSameAnchorAndBindingPlayer() {
        UUID firstPlayer = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID secondPlayer = UUID.fromString("00000000-0000-0000-0000-000000000002");
        DimensionalCoord anchor = new DimensionalCoord(10, 20, 30, 4);
        WirelessLinkTarget current = new WirelessLinkTarget(
            WirelessLinkTarget.AnchorType.WIRELESS_CONNECTOR,
            4,
            10,
            20,
            30,
            0L,
            firstPlayer,
            "first");

        assertTrue(WirelessKitBindingService.isSameBinding(current, anchor, firstPlayer));
        assertFalse(WirelessKitBindingService.isSameBinding(current, anchor, secondPlayer));
        assertFalse(WirelessKitBindingService.isSameBinding(current, new DimensionalCoord(11, 20, 30, 4), firstPlayer));
    }

    private static TileEntity assemblyBase() throws Exception {
        BaseMetaTileEntity base = new BaseMetaTileEntity();
        MTEWirelessUnifiedOutputAssemblyME assembly = new MTEWirelessUnifiedOutputAssemblyME(
            "wireless_kit_binding_test",
            4,
            new String[0],
            null);
        BaseMetaTileEntity.class.getMethod("setMetaTileEntity", IMetaTileEntity.class)
            .invoke(base, assembly);
        ((Object) assembly).getClass()
            .getMethod("setBaseMetaTileEntity", Class.forName("gregtech.api.interfaces.tileentity.IGregTechTileEntity"))
            .invoke(assembly, base);
        return base;
    }
}
