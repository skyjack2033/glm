package com.github.skyjack2033.wirelessmehatch.metatileentity;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidHandler;
import net.minecraftforge.fluids.IFluidTank;

import org.junit.BeforeClass;
import org.junit.Test;

import com.github.skyjack2033.wirelessmehatch.api.WirelessOutputCapacityHost;

import cpw.mods.fml.common.registry.RegistryDelegate;
import gregtech.api.enums.GTValues;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import sun.misc.Unsafe;

public class MTEWirelessUnifiedOutputAssemblyMETest {

    private static final Class<?>[] NO_TYPES = new Class<?>[0];

    private static Unsafe unsafe;
    private static Fluid testFluid;

    @BeforeClass
    public static void createTestFluidFixture() throws ReflectiveOperationException {
        Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
        theUnsafe.setAccessible(true);
        unsafe = (Unsafe) theUnsafe.get(null);
        testFluid = new Fluid("wirelessmehatch_test_fluid");
    }

    @Test
    public void connectedTextureRequiresLiveLinkAndActiveProxy() {
        MTEWirelessUnifiedOutputAssemblyME assembly = assembly();
        Class<?>[] parameterTypes = { boolean.class, boolean.class };

        assertFalse(invokeBoolean(assembly, "shouldShowConnectedTexture", parameterTypes, false, false));
        assertFalse(invokeBoolean(assembly, "shouldShowConnectedTexture", parameterTypes, false, true));
        assertFalse(invokeBoolean(assembly, "shouldShowConnectedTexture", parameterTypes, true, false));
        assertTrue(invokeBoolean(assembly, "shouldShowConnectedTexture", parameterTypes, true, true));
    }

    @Test
    public void sharedRemainingCapacityUsesLongAggregateOccupancy() throws ReflectiveOperationException {
        MTEWirelessUnifiedOutputAssemblyME assembly = assembly();
        WirelessOutputCapacityHost capacityHost = (WirelessOutputCapacityHost) (Object) assembly;
        long capacity = (long) Integer.MAX_VALUE * 2L + 17L;
        capacityHost.setFluidCapacity(capacity);

        FluidStack chunk = fluid(Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE, fill(assembly, chunk, true));
        assertEquals(Integer.MAX_VALUE, fill(assembly, chunk, true));

        Class<?> contract = Class.forName("com.github.skyjack2033.wirelessmehatch.api.SharedFluidOutputStore");
        assertTrue(contract.isInstance(assembly));
        Method remainingCapacity = contract.getMethod("getSharedFluidRemainingCapacity");
        assertEquals(17L, ((Long) remainingCapacity.invoke(assembly)).longValue());
        assertEquals(Integer.MAX_VALUE, getFluidTank(assembly).getFluidAmount());
        assertEquals(Integer.MAX_VALUE, getFluidTank(assembly).getCapacity());
        assertNull(getFluidTank(assembly).getInfo().fluid);
        assertEquals(Integer.MAX_VALUE, getFluidTank(assembly).getInfo().capacity);

        capacityHost.setFluidCapacity(1L);
        assertEquals((long) Integer.MAX_VALUE * 2L, capacityHost.getFluidCapacity());
        assertEquals(0L, ((Long) remainingCapacity.invoke(assembly)).longValue());
    }

    @Test
    public void unsidedFillSimulatesThenCommitsAgainstCoreCapacity() {
        MTEWirelessUnifiedOutputAssemblyME assembly = assembly();
        ((WirelessOutputCapacityHost) (Object) assembly).setFluidCapacity(10L);
        FluidStack six = fluid(6);

        assertTrue(invokeBoolean(assembly, "isEmptyAndAcceptsAnyFluid", NO_TYPES));
        assertEquals(6, fill(assembly, six, false));
        assertEquals(6, six.amount);
        assertEquals(0L, invokeLong(assembly, "getFluidCached"));
        assertEquals(10L, sharedRemainingCapacity(assembly));

        assertEquals(6, fill(assembly, six, true));
        assertEquals(6, six.amount);
        assertEquals(6L, invokeLong(assembly, "getFluidCached"));
        assertEquals(6, getFluidTank(assembly).getFluidAmount());
        assertEquals(4L, sharedRemainingCapacity(assembly));
        assertFalse(invokeBoolean(assembly, "isEmptyAndAcceptsAnyFluid", NO_TYPES));
        ((WirelessOutputCapacityHost) (Object) assembly).setItemCapacity(23L);
        assertArrayEquals(
            new String[] { "Item cached: 0 / 23", "Fluid cached: 6 / 10", "Wireless target: Unbound",
                "ME connection: Disconnected" },
            (String[]) invoke(assembly, "getInfoData", NO_TYPES));

        FluidStack four = fluid(4);
        assertEquals(4, fill(assembly, four, true));
        assertEquals(4, four.amount);
        assertEquals(0L, sharedRemainingCapacity(assembly));
        assertEquals(0, fill(assembly, fluid(1), true));
        assertFalse(canStoreFluid(assembly, fluid(1)));
    }

    @Test
    public void sidedFillAndAllDrainPathsAreDenied() {
        MTEWirelessUnifiedOutputAssemblyME assembly = assembly();
        ((WirelessOutputCapacityHost) (Object) assembly).setFluidCapacity(10L);
        FluidStack supplied = fluid(5);
        setInheritedFluid(assembly, fluid(3));
        IFluidHandler handler = (IFluidHandler) (Object) assembly;
        IFluidTank aggregateTank = getFluidTank(assembly);

        assertEquals(0, handler.fill(ForgeDirection.NORTH, supplied, false));
        assertEquals(0, aggregateTank.fill(supplied, false));
        assertEquals(0, aggregateTank.fill(supplied, true));
        assertEquals(5, supplied.amount);
        assertEquals(0L, invokeLong(assembly, "getFluidCached"));
        assertFalse(handler.canFill(ForgeDirection.NORTH, testFluid));
        assertFalse(handler.canDrain(ForgeDirection.NORTH, testFluid));
        assertNull(aggregateTank.drain(2, false));
        assertNull(aggregateTank.drain(2, true));
        assertNull(handler.drain(ForgeDirection.NORTH, 2, false));
        assertNull(handler.drain(ForgeDirection.NORTH, fluid(2), false));
        assertNull(
            invoke(
                assembly,
                "drain",
                new Class<?>[] { ForgeDirection.class, FluidStack.class, int.class, boolean.class },
                ForgeDirection.NORTH,
                fluid(2),
                2,
                false));
        assertNull(aggregateTank.getFluid());
        assertEquals(0, invokeFluidTankInt(aggregateTank, "getCanFillAmount"));
        assertEquals(0L, invokeLong(assembly, "getFluidCached"));
        assertEquals(3, getInheritedFluid(assembly).amount);
    }

    @Test
    public void containerAndInventoryTankPathsAreDisabled() {
        MTEWirelessUnifiedOutputAssemblyME assembly = assembly();
        ((WirelessOutputCapacityHost) (Object) assembly).setFluidCapacity(10L);

        assertFalse(invokeBoolean(assembly, "doesFillContainers", NO_TYPES));
        assertFalse(invokeBoolean(assembly, "doesEmptyContainers", NO_TYPES));
        assertFalse(invokeBoolean(assembly, "canTankBeFilled", NO_TYPES));
        assertFalse(invokeBoolean(assembly, "canTankBeEmptied", NO_TYPES));
        assertFalse(invokeBoolean(assembly, "isFluidInputAllowed", new Class<?>[] { FluidStack.class }, fluid(1)));
        assertFalse(
            invokeBoolean(assembly, "isLiquidOutput", new Class<?>[] { ForgeDirection.class }, ForgeDirection.NORTH));
        assertFalse(invokeBoolean(assembly, "isValidSlot", new Class<?>[] { int.class }, 0));
        assertFalse(
            invokeBoolean(
                assembly,
                "allowPutStack",
                new Class<?>[] { IGregTechTileEntity.class, int.class, ForgeDirection.class, ItemStack.class },
                null,
                0,
                ForgeDirection.NORTH,
                null));
        assertFalse(
            invokeBoolean(
                assembly,
                "allowPullStack",
                new Class<?>[] { IGregTechTileEntity.class, int.class, ForgeDirection.class, ItemStack.class },
                null,
                0,
                ForgeDirection.NORTH,
                null));
        assertNull(getFluidTank(assembly).getFluid());
        assertSame(GTValues.emptyFluidTankInfo, ((IFluidHandler) (Object) assembly).getTankInfo(ForgeDirection.NORTH));
        assertTrue(canStoreFluid(assembly, fluid(1)));
        assertFalse(canStoreFluid(assembly, null));
        assertFalse(canStoreFluid(assembly, fluid(null, 1)));
        assertFalse(canStoreFluid(assembly, fluid(0)));

        setInheritedFluid(assembly, fluid(2));
        assertNull(invoke(assembly, "getFillableStack", NO_TYPES));
        assertNull(getInheritedFluid(assembly));
        setInheritedFluid(assembly, fluid(2));
        assertNull(invoke(assembly, "setFillableStack", new Class<?>[] { FluidStack.class }, fluid(4)));
        assertNull(getInheritedFluid(assembly));
        setInheritedFluid(assembly, fluid(2));
        assertNull(invoke(assembly, "getDrainableStack", NO_TYPES));
        assertNull(getInheritedFluid(assembly));
        setInheritedFluid(assembly, fluid(2));
        assertNull(invoke(assembly, "setDrainableStack", new Class<?>[] { FluidStack.class }, fluid(4)));
        assertNull(getInheritedFluid(assembly));
    }

    @Test
    public void inheritedFluidIsClearedWhenUnifiedNbtLoads() {
        MTEWirelessUnifiedOutputAssemblyME authoritative = assembly();
        setInheritedFluid(authoritative, fluid(9));
        NBTTagCompound authoritativeTag = new NBTTagCompound();
        NBTTagCompound output = new NBTTagCompound();
        output.setLong("fluidCapacity", 10L);
        authoritativeTag.setTag("wirelessOutput", output);

        loadOutputData(authoritative, authoritativeTag, getInheritedFluid(authoritative));

        assertNull(getInheritedFluid(authoritative));
        assertEquals(0L, invokeLong(authoritative, "getFluidCached"));
        assertEquals(10L, sharedRemainingCapacity(authoritative));

        MTEWirelessUnifiedOutputAssemblyME legacy = assembly();
        setInheritedFluid(legacy, fluid(9));
        NBTTagCompound legacyTag = new NBTTagCompound();
        NBTTagCompound fluidProvider = new NBTTagCompound();
        fluidProvider.setTag("cache", new NBTTagCompound());
        legacyTag.setTag("fluidProvider", fluidProvider);

        loadOutputData(legacy, legacyTag, getInheritedFluid(legacy));

        assertNull(getInheritedFluid(legacy));
        assertEquals(0L, invokeLong(legacy, "getFluidCached"));

        MTEWirelessUnifiedOutputAssemblyME standalone = assembly();
        WirelessOutputCapacityHost capacityHost = (WirelessOutputCapacityHost) (Object) standalone;
        capacityHost.setFluidCapacity(5L);
        setInheritedFluid(standalone, fluid(12));

        loadOutputData(standalone, new NBTTagCompound(), getInheritedFluid(standalone));

        assertNull(getInheritedFluid(standalone));
        assertEquals(12L, invokeLong(standalone, "getFluidCached"));
        assertEquals(5L, capacityHost.getFluidCapacity());
        assertEquals(0L, sharedRemainingCapacity(standalone));

        MTEWirelessUnifiedOutputAssemblyME saving = assembly();
        setInheritedFluid(saving, fluid(2));
        NBTTagCompound saved = new NBTTagCompound();
        invoke(saving, "saveNBTData", new Class<?>[] { NBTTagCompound.class }, saved);
        assertNull(getInheritedFluid(saving));
        assertFalse(saved.hasKey("mFluid"));
    }

    private static MTEWirelessUnifiedOutputAssemblyME assembly() {
        return new MTEWirelessUnifiedOutputAssemblyME("wireless_output_test", 4, new String[0], null);
    }

    private static FluidStack fluid(int amount) {
        return fluid(testFluid, amount);
    }

    private static FluidStack fluid(Fluid fluid, int amount) {
        try {
            FluidStack stack = (FluidStack) unsafe.allocateInstance(FluidStack.class);
            stack.amount = amount;
            Field delegate = FluidStack.class.getDeclaredField("fluidDelegate");
            delegate.setAccessible(true);
            delegate.set(stack, new DirectFluidDelegate(fluid));
            return stack;
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static IFluidTank getFluidTank(MTEWirelessUnifiedOutputAssemblyME assembly) {
        return (IFluidTank) invoke(assembly, "getFluidTank", NO_TYPES);
    }

    private static int invokeFluidTankInt(IFluidTank tank, String name) {
        try {
            return ((Integer) tank.getClass()
                .getMethod(name)
                .invoke(tank)).intValue();
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Could not invoke aggregate tank method " + name, exception);
        }
    }

    private static int fill(MTEWirelessUnifiedOutputAssemblyME assembly, FluidStack fluid, boolean doFill) {
        return ((Integer) invoke(assembly, "fill", new Class<?>[] { FluidStack.class, boolean.class }, fluid, doFill))
            .intValue();
    }

    private static boolean canStoreFluid(MTEWirelessUnifiedOutputAssemblyME assembly, FluidStack fluid) {
        return invokeBoolean(assembly, "canStoreFluid", new Class<?>[] { FluidStack.class }, fluid);
    }

    private static long sharedRemainingCapacity(MTEWirelessUnifiedOutputAssemblyME assembly) {
        return invokeLong(assembly, "getSharedFluidRemainingCapacity");
    }

    private static void loadOutputData(MTEWirelessUnifiedOutputAssemblyME assembly, NBTTagCompound tag,
        FluidStack inheritedFluid) {
        invoke(
            assembly,
            "loadOutputData",
            new Class<?>[] { NBTTagCompound.class, FluidStack.class },
            tag,
            inheritedFluid);
    }

    private static void setInheritedFluid(MTEWirelessUnifiedOutputAssemblyME assembly, FluidStack fluid) {
        try {
            ((Object) assembly).getClass()
                .getField("mFluid")
                .set(assembly, fluid);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static FluidStack getInheritedFluid(MTEWirelessUnifiedOutputAssemblyME assembly) {
        try {
            Field field = ((Object) assembly).getClass()
                .getField("mFluid");
            return (FluidStack) field.get(assembly);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static boolean invokeBoolean(MTEWirelessUnifiedOutputAssemblyME assembly, String name,
        Class<?>[] parameterTypes, Object... arguments) {
        return ((Boolean) invoke(assembly, name, parameterTypes, arguments)).booleanValue();
    }

    private static long invokeLong(MTEWirelessUnifiedOutputAssemblyME assembly, String name, Object... arguments) {
        return ((Long) invoke(assembly, name, NO_TYPES, arguments)).longValue();
    }

    private static Object invoke(MTEWirelessUnifiedOutputAssemblyME assembly, String name, Class<?>[] parameterTypes,
        Object... arguments) {
        try {
            Class<?> assemblyClass = ((Object) assembly).getClass();
            Method method;
            try {
                method = assemblyClass.getDeclaredMethod(name, parameterTypes);
            } catch (NoSuchMethodException ignored) {
                method = assemblyClass.getMethod(name, parameterTypes);
            }
            method.setAccessible(true);
            return method.invoke(assembly, arguments);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) throw runtimeException;
            if (cause instanceof Error error) throw error;
            throw new AssertionError("Assembly method " + name + " failed", cause);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Could not invoke assembly method " + name, exception);
        }
    }

    private static final class DirectFluidDelegate implements RegistryDelegate<Fluid> {

        private final Fluid fluid;

        private DirectFluidDelegate(Fluid fluid) {
            this.fluid = fluid;
        }

        @Override
        public Fluid get() {
            return fluid;
        }

        @Override
        public String name() {
            return fluid == null ? "" : fluid.getName();
        }

        @Override
        public Class<Fluid> type() {
            return Fluid.class;
        }
    }
}
