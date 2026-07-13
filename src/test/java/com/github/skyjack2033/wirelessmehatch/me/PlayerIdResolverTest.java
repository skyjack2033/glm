package com.github.skyjack2033.wirelessmehatch.me;

import static org.junit.Assert.assertEquals;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.UUID;

import org.junit.Test;

import appeng.api.networking.IGridNode;
import appeng.core.worlddata.IWorldData;
import appeng.core.worlddata.IWorldPlayerData;
import appeng.core.worlddata.WorldData;
import appeng.me.GridNode;
import appeng.me.helpers.AENetworkProxy;
import appeng.me.helpers.IGridProxyable;

public class PlayerIdResolverTest {

    @Test
    public void appliesFirstAe2PlayerIdZero() throws ReflectiveOperationException {
        GridNode node = new GridNode(null);
        AENetworkProxy networkProxy = new AENetworkProxy(null, "test", null, false) {

            @Override
            public IGridNode getNode() {
                return node;
            }
        };
        IGridProxyable host = proxy(IGridProxyable.class, "getProxy", networkProxy);
        IWorldPlayerData playerData = proxy(IWorldPlayerData.class, "getPlayerID", 0);
        IWorldData worldData = proxy(IWorldData.class, "playerData", playerData);
        Field instance = WorldData.class.getDeclaredField("instance");
        instance.setAccessible(true);
        Object previousInstance = instance.get(null);

        try {
            instance.set(null, worldData);
            assertEquals(-1, node.getPlayerID());

            PlayerIdResolver
                .applyOwnerPlayerId(host, UUID.fromString("31c4910d-9b69-4725-8969-9ed53ac8a7dc"), "Developer");

            assertEquals(0, node.getPlayerID());
        } finally {
            instance.set(null, previousInstance);
        }
    }

    private static <T> T proxy(Class<T> type, String methodName, Object result) {
        return type.cast(
            Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[] { type },
                (proxy, method, arguments) -> method.getName()
                    .equals(methodName) ? result : null));
    }
}
