package com.github.skyjack2033.wirelessmehatch.terminal.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Collections;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import org.junit.BeforeClass;
import org.junit.Test;

import com.github.skyjack2033.wirelessmehatch.MinecraftRegistryTestBootstrap;

import appeng.core.sync.packets.PacketInterfaceTerminalUpdate;
import appeng.core.sync.packets.PacketInterfaceTerminalUpdate.PacketAdd;
import appeng.core.sync.packets.PacketInterfaceTerminalUpdate.PacketEntry;
import appeng.core.sync.packets.PacketInterfaceTerminalUpdate.PacketOverwrite;
import appeng.core.sync.packets.PacketInterfaceTerminalUpdate.PacketRemove;
import appeng.core.sync.packets.PacketInterfaceTerminalUpdate.PacketRename;

public class InterfaceTerminalModelTest {

    private static final int TEST_ITEM_A_ID = 30020;
    private static final int TEST_ITEM_B_ID = 30021;
    private static Item testItemA;
    private static Item testItemB;

    @BeforeClass
    public static void bootstrapMinecraftRegistries() throws ReflectiveOperationException {
        MinecraftRegistryTestBootstrap.initializeVanillaItems();
        testItemA = MinecraftRegistryTestBootstrap
            .registerItemIfAbsent(TEST_ITEM_A_ID, "wirelessmehatch_test:interface_pattern_a");
        testItemB = MinecraftRegistryTestBootstrap
            .registerItemIfAbsent(TEST_ITEM_B_ID, "wirelessmehatch_test:interface_pattern_b");
    }

    @Test
    public void appliesAddAndPartialOverwriteWithoutTouchingOtherSlots() {
        InterfaceTerminalModel model = new InterfaceTerminalModel();
        PacketAdd add = add(12L, "tile.interface.alpha", 5, stacks(stack(testItemA, 1), null, null));

        model.applyUpdates(Collections.<PacketEntry>singletonList(add), 0);

        InterfaceTerminalModel.Entry entry = model.getEntry(12L);
        assertEquals(3, entry.getInventorySize());
        assertEquals(
            testItemA,
            entry.getStack(0)
                .getItem());
        assertNull(entry.getStack(1));

        PacketInterfaceTerminalUpdate packet = new PacketInterfaceTerminalUpdate();
        PacketOverwrite overwrite = packet.addOverwriteEntry(12L)
            .setItems(new int[] { 1 }, stacks(stack(testItemB, 4)))
            .setOnline(false);
        model.applyUpdate(overwrite);

        assertEquals(
            testItemA,
            entry.getStack(0)
                .getItem());
        assertEquals(
            testItemB,
            entry.getStack(1)
                .getItem());
        assertEquals(4, entry.getStack(1).stackSize);
        assertFalse(entry.isOnline());
    }

    @Test
    public void appliesFullOverwriteAndResizeTogether() {
        InterfaceTerminalModel model = new InterfaceTerminalModel();
        model.applyUpdate(add(2L, "tile.interface", 0, stacks(stack(testItemA, 1))));

        PacketInterfaceTerminalUpdate packet = new PacketInterfaceTerminalUpdate();
        PacketOverwrite overwrite = packet.addOverwriteEntry(2L)
            .setItems(null, stacks(stack(testItemB, 2), null, stack(testItemA, 3), null))
            .setSize(2, 2, 4)
            .setPriority(17)
            .setTerminalVisible(false);
        model.applyUpdate(overwrite);

        InterfaceTerminalModel.Entry entry = model.getEntry(2L);
        assertEquals(4, entry.getInventorySize());
        assertEquals(2, entry.getRows());
        assertEquals(2, entry.getRowSize());
        assertEquals(17, entry.getPriority());
        assertFalse(entry.isTerminalVisible());
        assertEquals(
            testItemA,
            entry.getStack(2)
                .getItem());
    }

    @Test
    public void keepsSelectionAcrossRenameAndFallsBackAfterRemoval() throws Exception {
        InterfaceTerminalModel model = new InterfaceTerminalModel();
        model.applyUpdates(
            Arrays.<PacketEntry>asList(
                add(10L, "beta", 0, stacks((ItemStack) null)),
                add(11L, "alpha", 10, stacks((ItemStack) null))),
            0);
        assertTrue(model.select(10L));

        model.applyUpdate(construct(PacketRename.class, 10L, "aardvark", " #1"));

        assertEquals(10L, model.getSelectedEntryId());
        assertEquals(
            "aardvark",
            model.getVisibleEntries("")
                .get(1)
                .getName());

        model.applyUpdate(construct(PacketRemove.class, 10L));

        assertEquals(11L, model.getSelectedEntryId());
        assertSame(model.getEntry(11L), model.getSelectedEntry());
    }

    @Test
    public void filtersByNameAndClearsOnAuthoritativeReset() {
        InterfaceTerminalModel model = new InterfaceTerminalModel();
        model.applyUpdates(
            Arrays.<PacketEntry>asList(
                add(1L, "tile.interface.press", 0, stacks((ItemStack) null)),
                add(2L, "tile.interface.assembler", 4, stacks((ItemStack) null))),
            PacketInterfaceTerminalUpdate.DISCONNECT_BIT);

        assertFalse(model.isOnline());
        assertEquals(
            1,
            model.getVisibleEntries("PRESS")
                .size());
        assertEquals(
            2L,
            model.selectFirstVisible("assembler")
                .getId());

        model.applyUpdates(Collections.<PacketEntry>emptyList(), PacketInterfaceTerminalUpdate.CLEAR_ALL_BIT);

        assertTrue(model.isOnline());
        assertEquals(0, model.size());
        assertEquals(-1L, model.getSelectedEntryId());
    }

    @Test
    public void hidesTerminalInvisibleEntriesAndRepairsSelection() {
        InterfaceTerminalModel model = new InterfaceTerminalModel();
        PacketAdd visible = add(21L, "visible", 0, stacks((ItemStack) null));
        PacketAdd hidden = add(22L, "hidden", 10, stacks((ItemStack) null)).setTerminalVisible(false);

        model.applyUpdates(Arrays.<PacketEntry>asList(visible, hidden), 0);

        assertEquals(
            1,
            model.getVisibleEntries("")
                .size());
        assertEquals(21L, model.getSelectedEntryId());
        assertFalse(model.select(22L));

        PacketOverwrite hideSelected = new PacketInterfaceTerminalUpdate().addOverwriteEntry(21L)
            .setTerminalVisible(false);
        model.applyUpdate(hideSelected);

        assertTrue(
            model.getVisibleEntries("")
                .isEmpty());
        assertEquals(-1L, model.getSelectedEntryId());
    }

    @Test
    public void reportsSelectionChangeWhenTheServerRemovesTheSelectedInterface() throws Exception {
        InterfaceTerminalModel model = new InterfaceTerminalModel();
        model.applyUpdates(
            Arrays.<PacketEntry>asList(
                add(40L, "selected", 10, stacks((ItemStack) null)),
                add(41L, "fallback", 0, stacks((ItemStack) null))),
            0);
        assertTrue(model.select(40L));

        boolean changed = model
            .applyUpdates(Collections.<PacketEntry>singletonList(construct(PacketRemove.class, 40L)), 0);

        assertTrue(changed);
        assertEquals(41L, model.getSelectedEntryId());
    }

    @Test
    public void reportsSelectionChangeWhenTheServerHidesTheSelectedInterface() throws Exception {
        InterfaceTerminalModel model = new InterfaceTerminalModel();
        model.applyUpdates(
            Arrays.<PacketEntry>asList(
                add(50L, "selected", 10, stacks((ItemStack) null)),
                add(51L, "fallback", 0, stacks((ItemStack) null))),
            0);
        assertTrue(model.select(50L));
        PacketOverwrite hideSelected = new PacketInterfaceTerminalUpdate().addOverwriteEntry(50L)
            .setTerminalVisible(false);

        boolean changed = model.applyUpdates(Collections.<PacketEntry>singletonList(hideSelected), 0);

        assertTrue(changed);
        assertEquals(51L, model.getSelectedEntryId());
    }

    @Test
    public void reportsSelectionChangeWhenAuthoritativeResetRebuildsTheSameId() {
        InterfaceTerminalModel model = new InterfaceTerminalModel();
        model.applyUpdates(Collections.<PacketEntry>singletonList(add(60L, "old", 0, stacks((ItemStack) null))), 0);
        InterfaceTerminalModel.Entry previous = model.getSelectedEntry();

        boolean changed = model.applyUpdates(
            Collections.<PacketEntry>singletonList(add(60L, "replacement", 0, stacks((ItemStack) null))),
            PacketInterfaceTerminalUpdate.CLEAR_ALL_BIT);

        assertTrue(changed);
        assertTrue(previous != model.getSelectedEntry());
        assertEquals(60L, model.getSelectedEntryId());
    }

    @Test
    public void doesNotReportSelectionChangeForAnInPlaceSelectedUpdate() {
        InterfaceTerminalModel model = new InterfaceTerminalModel();
        model
            .applyUpdates(Collections.<PacketEntry>singletonList(add(70L, "selected", 0, stacks((ItemStack) null))), 0);
        InterfaceTerminalModel.Entry previous = model.getSelectedEntry();
        PacketOverwrite updateSelected = new PacketInterfaceTerminalUpdate().addOverwriteEntry(70L)
            .setOnline(false);

        boolean changed = model.applyUpdates(Collections.<PacketEntry>singletonList(updateSelected), 0);

        assertFalse(changed);
        assertSame(previous, model.getSelectedEntry());
    }

    @Test
    public void capsOversizedInterfaceInventories() {
        InterfaceTerminalModel model = new InterfaceTerminalModel();
        PacketAdd oversized = new PacketInterfaceTerminalUpdate().addNewEntry(30L, "oversized", true)
            .setLoc(1, 2, 3, 0, 1)
            .setItems(5000, 2, 10000, stacks());

        model.applyUpdate(oversized);

        InterfaceTerminalModel.Entry entry = model.getEntry(30L);
        assertEquals(4096, entry.getInventorySize());
        assertEquals(4096, entry.getNumSlots());
    }

    private static PacketAdd add(long id, String name, int priority, NBTTagList items) {
        return new PacketInterfaceTerminalUpdate().addNewEntry(id, name, true)
            .setLoc(1, 2, 3, 0, 1)
            .setItems(1, items.tagCount(), items.tagCount(), items)
            .setPriority(priority);
    }

    private static ItemStack stack(Item item, int size) {
        return new ItemStack(item, size, 0);
    }

    private static NBTTagList stacks(ItemStack... stacks) {
        NBTTagList result = new NBTTagList();
        for (ItemStack stack : stacks) {
            result.appendTag(stack == null ? new NBTTagCompound() : stack.writeToNBT(new NBTTagCompound()));
        }
        return result;
    }

    private static <T extends PacketEntry> T construct(Class<T> type, Object... arguments) throws Exception {
        Class<?>[] parameterTypes = new Class<?>[arguments.length];
        for (int i = 0; i < arguments.length; i++) {
            parameterTypes[i] = arguments[i] instanceof Long ? long.class : String.class;
        }
        Constructor<T> constructor = type.getDeclaredConstructor(parameterTypes);
        constructor.setAccessible(true);
        return constructor.newInstance(arguments);
    }

}
