package com.github.skyjack2033.wirelessmehatch.me;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import net.minecraft.tileentity.TileEntity;

import org.junit.BeforeClass;
import org.junit.Test;

import com.github.skyjack2033.wirelessmehatch.MinecraftRegistryTestBootstrap;
import com.github.skyjack2033.wirelessmehatch.metatileentity.MTEWirelessUnifiedOutputAssemblyME;

import appeng.tile.networking.TileWirelessConnector;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.metatileentity.BaseMetaTileEntity;

public class WirelessKitEndpointTest {

    private static final String ENDPOINT_CLASS = "com.github.skyjack2033.wirelessmehatch.me.WirelessKitEndpoint";

    @BeforeClass
    public static void bootstrapMinecraftRegistries() throws ReflectiveOperationException {
        MinecraftRegistryTestBootstrap.initializeVanillaItems();
    }

    @Test
    public void recognizesNativeWirelessConnector() throws Exception {
        Object endpoint = fromTile(new TileWirelessConnector());

        assertNotNull(endpoint);
        assertFalse(invokeBoolean(endpoint, "isAssembly"));
        assertTrue(invokeBoolean(endpoint, "isWirelessBase"));
    }

    @Test
    public void recognizesAssemblyInsideGregTechBaseTile() throws Exception {
        MTEWirelessUnifiedOutputAssemblyME assembly = new MTEWirelessUnifiedOutputAssemblyME(
            "wireless_kit_endpoint_test",
            4,
            new String[0],
            null);
        BaseMetaTileEntity base = attachAssembly(assembly);

        Object endpoint = fromTile(base);

        assertNotNull(endpoint);
        assertTrue(invokeBoolean(endpoint, "isAssembly"));
        assertFalse(invokeBoolean(endpoint, "isWirelessBase"));
        assertNull(invoke(endpoint, "getLocation"));
    }

    @Test
    public void rejectsOrdinaryTileEntity() throws Exception {
        assertNull(fromTile(new TileEntity()));
    }

    @Test
    public void assemblyTargetStopsAcceptingAfterFirstBinding() throws Exception {
        MTEWirelessUnifiedOutputAssemblyME assembly = newAssembly();
        Object assemblyEndpoint = fromTile(attachAssembly(assembly));
        Object connectorEndpoint = fromTile(new TileWirelessConnector());

        assertEquals(1, invokeInt(assemblyEndpoint, "getFreeSlots"));
        assertTrue(canAccept(assemblyEndpoint, connectorEndpoint));
        setBoundTarget(assembly);
        assertEquals(0, invokeInt(assemblyEndpoint, "getFreeSlots"));
        assertFalse(canAccept(assemblyEndpoint, connectorEndpoint));
    }

    @Test
    public void fullWirelessBaseStillAcceptsAssemblyAsNetworkAnchor() throws Exception {
        TileWirelessConnector fullConnector = new TileWirelessConnector();
        Field maxConnections = Class.forName("appeng.tile.networking.TileWirelessBase")
            .getDeclaredField("maxConnections");
        maxConnections.setAccessible(true);
        maxConnections.setInt(fullConnector, 0);
        Object fullConnectorEndpoint = fromTile(fullConnector);
        Object assemblyEndpoint = fromTile(attachAssembly(newAssembly()));

        assertTrue(canAccept(fullConnectorEndpoint, assemblyEndpoint));
    }

    private static MTEWirelessUnifiedOutputAssemblyME newAssembly() {
        return new MTEWirelessUnifiedOutputAssemblyME("wireless_kit_endpoint_capacity_test", 4, new String[0], null);
    }

    private static void setBoundTarget(MTEWirelessUnifiedOutputAssemblyME assembly) throws Exception {
        Field managerField = MTEWirelessUnifiedOutputAssemblyME.class.getDeclaredField("linkManager");
        managerField.setAccessible(true);
        Object manager = managerField.get(assembly);
        Field targetField = manager.getClass()
            .getDeclaredField("target");
        targetField.setAccessible(true);
        targetField.set(
            manager,
            new WirelessLinkTarget(WirelessLinkTarget.AnchorType.WIRELESS_CONNECTOR, 0, 1, 2, 3, 0L, null, ""));
    }

    private static BaseMetaTileEntity attachAssembly(MTEWirelessUnifiedOutputAssemblyME assembly) throws Exception {
        BaseMetaTileEntity base = new BaseMetaTileEntity();
        BaseMetaTileEntity.class.getMethod("setMetaTileEntity", IMetaTileEntity.class)
            .invoke(base, assembly);
        ((Object) assembly).getClass()
            .getMethod("setBaseMetaTileEntity", Class.forName("gregtech.api.interfaces.tileentity.IGregTechTileEntity"))
            .invoke(assembly, base);
        return base;
    }

    private static boolean canAccept(Object endpoint, Object source) throws Exception {
        return ((Boolean) invoke(
            endpoint.getClass()
                .getMethod("canAccept", endpoint.getClass()),
            endpoint,
            source)).booleanValue();
    }

    private static Object fromTile(TileEntity tile) throws Exception {
        Class<?> endpointClass = Class.forName(ENDPOINT_CLASS);
        Method fromTile = endpointClass.getMethod("fromTile", TileEntity.class);
        return invoke(fromTile, null, tile);
    }

    private static boolean invokeBoolean(Object endpoint, String methodName) throws Exception {
        return ((Boolean) invoke(
            endpoint.getClass()
                .getMethod(methodName),
            endpoint)).booleanValue();
    }

    private static int invokeInt(Object endpoint, String methodName) throws Exception {
        return ((Integer) invoke(
            endpoint.getClass()
                .getMethod(methodName),
            endpoint)).intValue();
    }

    private static Object invoke(Object endpoint, String methodName) throws Exception {
        return invoke(
            endpoint.getClass()
                .getMethod(methodName),
            endpoint);
    }

    private static Object invoke(Method method, Object receiver, Object... arguments) throws Exception {
        try {
            return method.invoke(receiver, arguments);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof Exception) throw (Exception) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw exception;
        }
    }

}
