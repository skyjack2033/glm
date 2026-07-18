package com.github.skyjack2033.wirelessmehatch.me;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.junit.Test;

import appeng.api.util.DimensionalCoord;

public class WirelessKitNbtStateTest {

    private static final String STATE_CLASS = "com.github.skyjack2033.wirelessmehatch.me.WirelessKitNbtState";

    @Test
    public void initializesTheNativeWirelessKitSchema() throws Exception {
        ItemStack kit = kit();

        invoke("ensureInitialized", new Class<?>[] { ItemStack.class }, kit);

        NBTTagCompound tag = kit.getTagCompound();
        assertNotNull(tag);
        assertTrue(tag.hasKey("Simple", 10));
        assertTrue(tag.hasKey("Advanced", 10));
        assertTrue(tag.hasKey("advancedLineQueue", 10));
        assertTrue(tag.hasKey("advancedLineBinding", 10));
        assertTrue(tag.hasKey("Super", 10));
        NBTTagCompound superTag = tag.getCompoundTag("Super");
        assertTrue(superTag.hasKey("pins", 9));
        assertTrue(superTag.hasKey("names", 9));
        assertTrue(superTag.hasKey("pos", 10));
    }

    @Test
    public void storesReadsAndClearsAPoint() throws Exception {
        ItemStack kit = kit();
        DimensionalCoord point = coord(12, 34, -56, -7);

        invoke(
            "storePoint",
            new Class<?>[] { ItemStack.class, String.class, DimensionalCoord.class },
            kit,
            "Simple",
            point);

        assertEquals(point, invoke("readPoint", new Class<?>[] { ItemStack.class, String.class }, kit, "Simple"));
        invoke("clear", new Class<?>[] { ItemStack.class, String.class }, kit, "Simple");
        assertNull(invoke("readPoint", new Class<?>[] { ItemStack.class, String.class }, kit, "Simple"));
    }

    @Test
    public void advancedQueuePreservesOrderDimensionAndRemovesOnlyTheHead() throws Exception {
        ItemStack kit = kit();
        DimensionalCoord first = coord(1, 2, 3, -1);
        DimensionalCoord second = coord(4, 5, 6, 0);
        DimensionalCoord third = coord(7, 8, 9, 42);

        appendAdvanced(kit, first);
        appendAdvanced(kit, second);
        appendAdvanced(kit, third);

        assertEquals(Arrays.asList(first, second, third), readAdvanced(kit));
        invoke("removeAdvancedHead", new Class<?>[] { ItemStack.class }, kit);
        assertEquals(Arrays.asList(second, third), readAdvanced(kit));
        invoke("removeAdvancedHead", new Class<?>[] { ItemStack.class }, kit);
        assertEquals(Collections.singletonList(third), readAdvanced(kit));
    }

    @Test
    public void expandsAxisAlignedLinesInclusivelyInEitherDirection() throws Exception {
        assertEquals(
            Arrays.asList(coord(2, 5, 7, 3), coord(3, 5, 7, 3), coord(4, 5, 7, 3)),
            expandLine(coord(2, 5, 7, 3), coord(4, 5, 7, 3)));
        assertEquals(
            Arrays.asList(coord(4, 5, 7, 3), coord(3, 5, 7, 3), coord(2, 5, 7, 3)),
            expandLine(coord(4, 5, 7, 3), coord(2, 5, 7, 3)));
        assertEquals(
            Arrays.asList(coord(2, 3, 7, 3), coord(2, 4, 7, 3), coord(2, 5, 7, 3)),
            expandLine(coord(2, 3, 7, 3), coord(2, 5, 7, 3)));
        assertEquals(
            Arrays.asList(coord(2, 5, 9, 3), coord(2, 5, 8, 3), coord(2, 5, 7, 3)),
            expandLine(coord(2, 5, 9, 3), coord(2, 5, 7, 3)));
    }

    @Test
    public void rejectsCrossDimensionDiagonalAndCoincidentLines() throws Exception {
        assertEquals(Collections.emptyList(), expandLine(coord(1, 2, 3, 0), coord(4, 2, 3, 1)));
        assertEquals(Collections.emptyList(), expandLine(coord(1, 2, 3, 0), coord(4, 5, 3, 0)));
        assertEquals(Collections.emptyList(), expandLine(coord(1, 2, 3, 0), coord(1, 2, 3, 0)));
    }

    @Test
    public void simpleAndAdvancedPendingAnchorsAreIndependent() throws Exception {
        ItemStack kit = kit();
        DimensionalCoord simple = coord(11, 12, 13, 7);
        DimensionalCoord advanced = coord(21, 22, 23, 8);

        invoke("storeSimplePendingAnchor", new Class<?>[] { ItemStack.class, DimensionalCoord.class }, kit, simple);
        invoke("storeAdvancedPendingAnchor", new Class<?>[] { ItemStack.class, DimensionalCoord.class }, kit, advanced);

        assertEquals(simple, invoke("readSimplePendingAnchor", new Class<?>[] { ItemStack.class }, kit));
        assertEquals(advanced, invoke("readAdvancedPendingAnchor", new Class<?>[] { ItemStack.class }, kit));
        assertNull(invoke("readPoint", new Class<?>[] { ItemStack.class, String.class }, kit, "Simple"));
        invoke("clearSimplePendingAnchor", new Class<?>[] { ItemStack.class }, kit);
        assertNull(invoke("readSimplePendingAnchor", new Class<?>[] { ItemStack.class }, kit));
        assertEquals(advanced, invoke("readAdvancedPendingAnchor", new Class<?>[] { ItemStack.class }, kit));
        invoke("clearAdvancedPendingAnchor", new Class<?>[] { ItemStack.class }, kit);
        assertNull(invoke("readAdvancedPendingAnchor", new Class<?>[] { ItemStack.class }, kit));
    }

    private static void appendAdvanced(ItemStack kit, DimensionalCoord point) throws Exception {
        invoke("appendAdvanced", new Class<?>[] { ItemStack.class, DimensionalCoord.class }, kit, point);
    }

    @SuppressWarnings("unchecked")
    private static List<DimensionalCoord> readAdvanced(ItemStack kit) throws Exception {
        return (List<DimensionalCoord>) invoke("readAdvanced", new Class<?>[] { ItemStack.class }, kit);
    }

    @SuppressWarnings("unchecked")
    private static List<DimensionalCoord> expandLine(DimensionalCoord first, DimensionalCoord second) throws Exception {
        return (List<DimensionalCoord>) invoke(
            "expandLine",
            new Class<?>[] { DimensionalCoord.class, DimensionalCoord.class },
            first,
            second);
    }

    private static Object invoke(String name, Class<?>[] parameterTypes, Object... arguments) throws Exception {
        Class<?> state = Class.forName(STATE_CLASS);
        Method method = state.getMethod(name, parameterTypes);
        try {
            return method.invoke(null, arguments);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) throw (Exception) cause;
            throw e;
        }
    }

    private static ItemStack kit() {
        return new ItemStack(new Item());
    }

    private static DimensionalCoord coord(int x, int y, int z, int dimension) {
        return new DimensionalCoord(x, y, z, dimension);
    }
}
