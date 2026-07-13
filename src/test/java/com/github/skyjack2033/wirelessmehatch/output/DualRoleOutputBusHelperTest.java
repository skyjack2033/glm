package com.github.skyjack2033.wirelessmehatch.output;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import net.minecraft.item.ItemStack;

import org.junit.BeforeClass;
import org.junit.Test;

import com.github.skyjack2033.wirelessmehatch.api.WirelessDualRoleOutput;
import com.github.skyjack2033.wirelessmehatch.metatileentity.MTEWirelessUnifiedOutputAssemblyME;

import gregtech.api.enums.OutputBusType;
import gregtech.api.interfaces.IOutputBus;
import gregtech.api.interfaces.IOutputBusTransaction;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchOutput;
import gregtech.api.util.GTUtility;
import sun.misc.Unsafe;

public class DualRoleOutputBusHelperTest {

    private static Unsafe unsafe;

    @BeforeClass
    public static void initializeUnsafe() throws ReflectiveOperationException {
        Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
        theUnsafe.setAccessible(true);
        unsafe = (Unsafe) theUnsafe.get(null);
    }

    @Test
    public void physicalAssemblyImplementsDualRoleOutputBusContracts() {
        assertTrue(IOutputBus.class.isAssignableFrom(MTEWirelessUnifiedOutputAssemblyME.class));
        assertTrue(WirelessDualRoleOutput.class.isAssignableFrom(MTEWirelessUnifiedOutputAssemblyME.class));
    }

    @Test
    public void appendsCandidateAfterExistingBusses() {
        IOutputBus first = new StubOutputBus();
        IOutputBus second = new StubOutputBus();
        IOutputBus candidate = new StubOutputBus();
        List<IOutputBus> busses = new ArrayList<>();
        busses.add(first);
        busses.add(second);

        DualRoleOutputBusHelper.appendIdentity(busses, candidate);

        assertEquals(3, busses.size());
        assertSame(first, busses.get(0));
        assertSame(second, busses.get(1));
        assertSame(candidate, busses.get(2));
    }

    @Test
    public void doesNotAppendSameObjectTwice() {
        IOutputBus candidate = new StubOutputBus();
        List<IOutputBus> busses = new ArrayList<>();

        DualRoleOutputBusHelper.appendIdentity(busses, candidate);
        DualRoleOutputBusHelper.appendIdentity(busses, candidate);

        assertEquals(1, busses.size());
        assertSame(candidate, busses.get(0));
    }

    @Test
    public void distinctObjectsAreNotCollapsedByEquals() {
        IOutputBus first = new EqualStubOutputBus();
        IOutputBus second = new EqualStubOutputBus();
        List<IOutputBus> busses = new ArrayList<>();

        DualRoleOutputBusHelper.appendIdentity(busses, first);
        DualRoleOutputBusHelper.appendIdentity(busses, second);

        assertEquals(2, busses.size());
        assertSame(first, busses.get(0));
        assertSame(second, busses.get(1));
    }

    @Test
    public void markerOnlyObjectIsNotACompleteDualRoleOutput() {
        Object markerOnly = new MarkerOnlyOutput();

        assertFalse(DualRoleOutputBusHelper.isCompleteDualRoleOutput(markerOnly));
    }

    @Test
    public void augmentAddsOnlyValidCompleteDualRoleOutputHatches() {
        List<MTEHatchOutput> hatches = new ArrayList<>();
        MTEWirelessUnifiedOutputAssemblyME valid = assembly();
        attachToValidTile(valid);
        MTEWirelessUnifiedOutputAssemblyME invalid = assembly();
        MTEHatchOutput incomplete = ordinaryOutputHatch();
        attachToValidTile(incomplete);
        hatches.add((MTEHatchOutput) (Object) valid);
        hatches.add((MTEHatchOutput) (Object) invalid);
        hatches.add(incomplete);
        hatches.add(null);
        IOutputBus existing = new StubOutputBus();
        List<IOutputBus> snapshot = new ArrayList<>();
        snapshot.add(existing);

        List<IOutputBus> result = DualRoleOutputBusHelper.augment(hatches, snapshot);

        assertSame(snapshot, result);
        assertEquals(2, result.size());
        assertSame(existing, result.get(0));
        assertSame(valid, result.get(1));
    }

    @Test
    public void augmentDoesNotDuplicateSameOutputAlreadyInSnapshot() {
        List<MTEHatchOutput> hatches = new ArrayList<>();
        MTEWirelessUnifiedOutputAssemblyME assembly = assembly();
        attachToValidTile(assembly);
        hatches.add((MTEHatchOutput) (Object) assembly);
        List<IOutputBus> snapshot = new ArrayList<>();
        snapshot.add((IOutputBus) (Object) assembly);

        DualRoleOutputBusHelper.augment(hatches, snapshot);

        assertEquals(1, snapshot.size());
        assertSame(assembly, snapshot.get(0));
    }

    @Test
    public void registerSteamOutputReturnsNullForNullOrIncompleteTile() {
        AtomicInteger calls = new AtomicInteger();
        Object markerOnly = Proxy.newProxyInstance(
            WirelessDualRoleOutput.class.getClassLoader(),
            new Class<?>[] { IMetaTileEntity.class, WirelessDualRoleOutput.class },
            (proxy, method, arguments) -> defaultValue(method.getReturnType()));

        assertNull(DualRoleOutputBusHelper.registerSteamOutput(null, 17, (tile, casingIndex) -> {
            calls.incrementAndGet();
            return true;
        }));
        assertNull(DualRoleOutputBusHelper.registerSteamOutput(tile(markerOnly), 17, (tile, casingIndex) -> {
            calls.incrementAndGet();
            return true;
        }));
        assertEquals(0, calls.get());
    }

    @Test
    public void registerSteamOutputDelegatesAndReturnsControllerBoolean() {
        MTEWirelessUnifiedOutputAssemblyME assembly = assembly();
        IGregTechTileEntity tile = tile(assembly);
        AtomicReference<IGregTechTileEntity> registeredTile = new AtomicReference<>();
        AtomicInteger registeredCasing = new AtomicInteger(-1);
        AtomicInteger calls = new AtomicInteger();

        assertEquals(Boolean.FALSE, DualRoleOutputBusHelper.registerSteamOutput(tile, 29, (candidate, casingIndex) -> {
            calls.incrementAndGet();
            registeredTile.set(candidate);
            registeredCasing.set(casingIndex);
            return false;
        }));
        assertEquals(1, calls.get());
        assertSame(tile, registeredTile.get());
        assertEquals(29, registeredCasing.get());
    }

    private static MTEWirelessUnifiedOutputAssemblyME assembly() {
        return new MTEWirelessUnifiedOutputAssemblyME("dual_role_output_test", 4, new String[0], null);
    }

    private static MTEHatchOutput ordinaryOutputHatch() {
        try {
            return (MTEHatchOutput) unsafe.allocateInstance(MTEHatchOutput.class);
        } catch (InstantiationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static void attachToValidTile(Object metaTileEntity) {
        try {
            metaTileEntity.getClass()
                .getMethod("setBaseMetaTileEntity", IGregTechTileEntity.class)
                .invoke(metaTileEntity, tile(metaTileEntity));
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static IGregTechTileEntity tile(Object metaTileEntity) {
        return (IGregTechTileEntity) Proxy.newProxyInstance(
            IGregTechTileEntity.class.getClassLoader(),
            new Class<?>[] { IGregTechTileEntity.class },
            (proxy, method, arguments) -> {
                if (method.getName()
                    .equals("getMetaTileEntity")) return metaTileEntity;
                if (method.getName()
                    .equals("isDead")) return false;
                return defaultValue(method.getReturnType());
            });
    }

    private static Object defaultValue(Class<?> returnType) {
        if (!returnType.isPrimitive()) return null;
        if (returnType == boolean.class) return false;
        if (returnType == byte.class) return (byte) 0;
        if (returnType == short.class) return (short) 0;
        if (returnType == int.class) return 0;
        if (returnType == long.class) return 0L;
        if (returnType == float.class) return 0.0F;
        if (returnType == double.class) return 0.0D;
        if (returnType == char.class) return '\0';
        return null;
    }

    private static final class MarkerOnlyOutput implements WirelessDualRoleOutput {
    }

    private static class StubOutputBus implements IOutputBus {

        @Override
        public boolean isFiltered() {
            return false;
        }

        @Override
        public boolean isFilteredToItem(GTUtility.ItemId itemId) {
            return false;
        }

        @Override
        public OutputBusType getBusType() {
            return null;
        }

        @Override
        public boolean storePartial(ItemStack stack, boolean simulate) {
            return false;
        }

        @Override
        public IOutputBusTransaction createTransaction() {
            return null;
        }
    }

    private static final class EqualStubOutputBus extends StubOutputBus {

        @Override
        public boolean equals(Object object) {
            return object instanceof EqualStubOutputBus;
        }

        @Override
        public int hashCode() {
            return 1;
        }
    }
}
