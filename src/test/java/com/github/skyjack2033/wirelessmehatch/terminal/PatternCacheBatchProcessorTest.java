package com.github.skyjack2033.wirelessmehatch.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import net.minecraft.inventory.InventoryBasic;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import org.junit.BeforeClass;
import org.junit.Test;

import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.items.misc.ItemEncodedPattern;
import sun.misc.Unsafe;

public class PatternCacheBatchProcessorTest {

    private static Unsafe unsafe;
    private static Item nativePatternItem;
    private static Item thirdPartyPatternItem;

    @BeforeClass
    public static void initializeUnsafe() throws ReflectiveOperationException {
        Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
        theUnsafe.setAccessible(true);
        unsafe = (Unsafe) theUnsafe.get(null);
        nativePatternItem = registerItem(
            30030,
            "wirelessmehatch_test:native_encoded_pattern",
            createNativePatternItem());
        thirdPartyPatternItem = registerItem(
            30031,
            "wirelessmehatch_test:third_party_pattern",
            new ThirdPartyPatternItem());
    }

    @Test
    public void multipliesBothNativeAndGenericStackCounts() {
        InventoryBasic inventory = inventory(pattern(6, 1200L));

        PatternCacheBatchProcessor.Result result = PatternCacheBatchProcessor
            .apply(inventory, PatternCacheBatchCommand.MULTIPLY_3);

        assertEquals(1, result.getChanged());
        assertEquals(0, result.getSkipped());
        assertEquals(18, input(inventory.getStackInSlot(0)).getInteger("Count"));
        assertEquals(3600L, output(inventory.getStackInSlot(0)).getLong("Cnt"));
    }

    @Test
    public void divisionIsAtomicWhenAnyCountIsNotDivisible() {
        ItemStack original = pattern(6, 10L);
        InventoryBasic inventory = inventory(original);

        PatternCacheBatchProcessor.Result result = PatternCacheBatchProcessor
            .apply(inventory, PatternCacheBatchCommand.DIVIDE_3);

        assertEquals(0, result.getChanged());
        assertEquals(1, result.getSkipped());
        assertEquals(6, input(inventory.getStackInSlot(0)).getInteger("Count"));
        assertEquals(10L, output(inventory.getStackInSlot(0)).getLong("Cnt"));
    }

    @Test
    public void multiplicationSkipsOverflowWithoutMutatingThePattern() {
        ItemStack original = pattern(Integer.MAX_VALUE, Long.MAX_VALUE);
        InventoryBasic inventory = inventory(original);

        PatternCacheBatchProcessor.Result result = PatternCacheBatchProcessor
            .apply(inventory, PatternCacheBatchCommand.MULTIPLY_2);

        assertEquals(0, result.getChanged());
        assertEquals(1, result.getSkipped());
        assertEquals(Integer.MAX_VALUE, input(inventory.getStackInSlot(0)).getInteger("Count"));
        assertEquals(Long.MAX_VALUE, output(inventory.getStackInSlot(0)).getLong("Cnt"));
    }

    @Test
    public void genericStacksIgnoreTheirZeroCompatibilityCount() {
        ItemStack pattern = pattern(1, 6L);
        NBTTagCompound genericInput = input(pattern);
        genericInput.setInteger("Count", 0);
        genericInput.setLong("Cnt", 6L);
        InventoryBasic inventory = inventory(pattern);

        PatternCacheBatchProcessor.Result result = PatternCacheBatchProcessor
            .apply(inventory, PatternCacheBatchCommand.DIVIDE_3);

        assertEquals(1, result.getChanged());
        assertEquals(0, input(inventory.getStackInSlot(0)).getInteger("Count"));
        assertEquals(2L, input(inventory.getStackInSlot(0)).getLong("Cnt"));
    }

    @Test
    public void updatesSubstitutionFlagsAcrossTheCache() {
        InventoryBasic inventory = inventory(pattern(1, 1L), pattern(2, 2L));

        PatternCacheBatchProcessor.apply(inventory, PatternCacheBatchCommand.ITEM_SUBSTITUTION_ON);
        PatternCacheBatchProcessor.apply(inventory, PatternCacheBatchCommand.OUTPUT_SUBSTITUTION_ON);

        for (int slot = 0; slot < inventory.getSizeInventory(); slot++) {
            NBTTagCompound tag = inventory.getStackInSlot(slot)
                .getTagCompound();
            assertTrue(tag.getBoolean("substitute"));
            assertTrue(tag.getBoolean("beSubstitute"));
        }

        PatternCacheBatchProcessor.apply(inventory, PatternCacheBatchCommand.ITEM_SUBSTITUTION_OFF);
        assertFalse(
            inventory.getStackInSlot(0)
                .getTagCompound()
                .getBoolean("substitute"));
        assertTrue(
            inventory.getStackInSlot(0)
                .getTagCompound()
                .getBoolean("beSubstitute"));
    }

    @Test
    public void derivesMixedSubstitutionStateFromNativePatternNbt() {
        ItemStack disabled = pattern(nativePatternItem, false, 1, 1L);
        ItemStack enabled = pattern(nativePatternItem, false, 1, 1L);
        enabled.getTagCompound()
            .setBoolean("beSubstitute", true);
        ItemStack ignoredThirdParty = pattern(thirdPartyPatternItem, false, 1, 1L);
        ignoredThirdParty.getTagCompound()
            .setBoolean("beSubstitute", true);

        assertEquals(
            PatternCacheFlagState.MIXED,
            PatternCacheBatchProcessor.getOutputSubstitutionState(inventory(disabled, enabled, ignoredThirdParty)));
        assertEquals(
            PatternCacheFlagState.EMPTY,
            PatternCacheBatchProcessor.getOutputSubstitutionState(inventory(ignoredThirdParty)));
    }

    @Test
    public void batchProcessingSkipsThirdPartyPatternItems() {
        ItemStack nativePattern = pattern(nativePatternItem, false, 4, 8L);
        ItemStack thirdPartyPattern = pattern(thirdPartyPatternItem, false, 4, 8L);
        InventoryBasic inventory = inventory(nativePattern, thirdPartyPattern);

        PatternCacheBatchProcessor.Result result = PatternCacheBatchProcessor
            .apply(inventory, PatternCacheBatchCommand.MULTIPLY_2);

        assertEquals(1, result.getChanged());
        assertEquals(1, result.getSkipped());
        assertEquals(8, input(inventory.getStackInSlot(0)).getInteger("Count"));
        assertEquals(4, input(inventory.getStackInSlot(1)).getInteger("Count"));
    }

    @Test
    public void scalingSkipsCraftingPatterns() {
        ItemStack processingPattern = pattern(nativePatternItem, false, 4, 8L);
        ItemStack craftingPattern = pattern(nativePatternItem, true, 4, 8L);
        InventoryBasic inventory = inventory(processingPattern, craftingPattern);

        PatternCacheBatchProcessor.Result result = PatternCacheBatchProcessor
            .apply(inventory, PatternCacheBatchCommand.MULTIPLY_2);

        assertEquals(1, result.getChanged());
        assertEquals(1, result.getSkipped());
        assertEquals(8, input(inventory.getStackInSlot(0)).getInteger("Count"));
        assertEquals(4, input(inventory.getStackInSlot(1)).getInteger("Count"));
    }

    private static InventoryBasic inventory(ItemStack... stacks) {
        InventoryBasic inventory = new InventoryBasic("test", false, stacks.length);
        for (int slot = 0; slot < stacks.length; slot++) {
            inventory.setInventorySlotContents(slot, stacks[slot]);
        }
        return inventory;
    }

    private static ItemStack pattern(int itemCount, long genericCount) {
        return pattern(nativePatternItem, false, itemCount, genericCount);
    }

    private static ItemStack pattern(Item item, boolean crafting, int itemCount, long genericCount) {
        ItemStack stack = new ItemStack(item);
        NBTTagCompound pattern = new NBTTagCompound();
        NBTTagList inputs = new NBTTagList();
        NBTTagCompound input = new NBTTagCompound();
        input.setInteger("Count", itemCount);
        inputs.appendTag(input);
        NBTTagList outputs = new NBTTagList();
        NBTTagCompound output = new NBTTagCompound();
        output.setLong("Cnt", genericCount);
        outputs.appendTag(output);
        pattern.setTag("in", inputs);
        pattern.setTag("out", outputs);
        pattern.setBoolean("crafting", crafting);
        stack.setTagCompound(pattern);
        return stack;
    }

    private static Item createNativePatternItem() {
        try {
            Item item = (Item) unsafe.allocateInstance(ItemEncodedPattern.class);
            Method getDelegate = Item.itemRegistry.getClass()
                .getMethod("getDelegate", Object.class, Class.class);
            Object delegate = getDelegate.invoke(Item.itemRegistry, item, Item.class);
            Field delegateField = Item.class.getField("delegate");
            unsafe.putObject(item, unsafe.objectFieldOffset(delegateField), delegate);
            return item;
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static Item registerItem(int id, String name, Item item) throws ReflectiveOperationException {
        Item registered = (Item) Item.itemRegistry.getObject(name);
        if (registered != null) return registered;
        Method addObjectRaw = Item.itemRegistry.getClass()
            .getDeclaredMethod("addObjectRaw", int.class, String.class, Object.class);
        addObjectRaw.setAccessible(true);
        addObjectRaw.invoke(Item.itemRegistry, id, name, item);
        return item;
    }

    private static final class ThirdPartyPatternItem extends Item implements ICraftingPatternItem {

        @Override
        public ICraftingPatternDetails getPatternForItem(ItemStack stack, net.minecraft.world.World world) {
            return null;
        }
    }

    private static NBTTagCompound input(ItemStack stack) {
        return stack.getTagCompound()
            .getTagList("in", 10)
            .getCompoundTagAt(0);
    }

    private static NBTTagCompound output(ItemStack stack) {
        return stack.getTagCompound()
            .getTagList("out", 10)
            .getCompoundTagAt(0);
    }
}
