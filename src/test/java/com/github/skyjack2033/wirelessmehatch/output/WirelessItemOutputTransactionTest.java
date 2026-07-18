package com.github.skyjack2033.wirelessmehatch.output;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.github.skyjack2033.wirelessmehatch.MinecraftRegistryTestBootstrap;

import gregtech.api.util.GTUtility;

public class WirelessItemOutputTransactionTest {

    private static final long ITEM_CAPACITY = 5L;
    private static final int TEST_ITEM_A_ID = 30000;
    private static final int TEST_ITEM_B_ID = 30001;
    private static final String TEST_ITEM_A_NAME = "wirelessmehatch_test:item_a";
    private static final String TEST_ITEM_B_NAME = "wirelessmehatch_test:item_b";

    private static Item testItemA;
    private static Item testItemB;

    private WirelessUnifiedOutputCore core;
    private WirelessItemOutputTransaction transaction;

    @BeforeClass
    public static void bootstrapMinecraftRegistries() throws ReflectiveOperationException {
        MinecraftRegistryTestBootstrap.initializeVanillaItems();
        testItemA = MinecraftRegistryTestBootstrap.registerItemIfAbsent(TEST_ITEM_A_ID, TEST_ITEM_A_NAME);
        testItemB = MinecraftRegistryTestBootstrap.registerItemIfAbsent(TEST_ITEM_B_ID, TEST_ITEM_B_NAME);
    }

    @Before
    public void setUp() {
        core = new WirelessUnifiedOutputCore(null, () -> {}, ITEM_CAPACITY, Long.MAX_VALUE);
        transaction = new WirelessItemOutputTransaction(null, core);
    }

    @Test
    public void uncommittedTransactionDoesNotMutateCore() {
        ItemStack stack = stack(3);

        assertTrue(transaction.storePartial(GTUtility.ItemId.create(stack), stack));

        assertEquals(0L, core.getItemCached());
        assertEquals(0, stack.stackSize);
    }

    @Test
    public void commitTransfersExactlyBufferedItems() {
        ItemStack first = stack(testItemA, 2);
        ItemStack second = stack(testItemB, 3);
        GTUtility.ItemId firstId = GTUtility.ItemId.create(first);
        GTUtility.ItemId secondId = GTUtility.ItemId.create(second);
        transaction.storePartial(firstId, first);
        transaction.storePartial(secondId, second);

        transaction.commit();

        Map<GTUtility.ItemId, Long> committed = serializedItems();
        assertEquals(2, committed.size());
        assertEquals(Long.valueOf(2L), committed.get(firstId));
        assertEquals(Long.valueOf(3L), committed.get(secondId));
        assertEquals(ITEM_CAPACITY, core.getItemCached());
    }

    @Test
    public void partialAcceptanceLeavesStackRemainder() {
        ItemStack stack = stack(8);

        assertFalse(transaction.storePartial(GTUtility.ItemId.create(stack), stack));

        assertEquals(3, stack.stackSize);
        assertEquals(0L, core.getItemCached());
        transaction.commit();
        assertEquals(ITEM_CAPACITY, core.getItemCached());
    }

    @Test
    public void cumulativeCapacityIncludesCoreAndPreviouslyBufferedItems() {
        ItemStack existing = stack(1);
        assertTrue(core.storeItem(existing, false));
        assertEquals(0, existing.stackSize);

        ItemStack first = stack(2);
        ItemStack second = stack(4);
        assertTrue(transaction.storePartial(GTUtility.ItemId.create(first), first));
        assertFalse(transaction.storePartial(GTUtility.ItemId.create(second), second));

        assertEquals(0, first.stackSize);
        assertEquals(2, second.stackSize);
        assertEquals(1L, core.getItemCached());

        transaction.commit();

        assertEquals(0, first.stackSize);
        assertEquals(2, second.stackSize);
        assertEquals(ITEM_CAPACITY, core.getItemCached());
    }

    @Test
    public void secondCommitDoesNotStoreTwice() {
        ItemStack stack = stack(3);
        transaction.storePartial(GTUtility.ItemId.create(stack), stack);

        transaction.commit();
        transaction.commit();

        assertEquals(3L, core.getItemCached());
    }

    @Test
    public void storeAfterCommitIsIgnored() {
        ItemStack committed = stack(2);
        assertTrue(transaction.storePartial(GTUtility.ItemId.create(committed), committed));
        transaction.commit();
        assertEquals(2L, core.getItemCached());

        ItemStack afterCommit = stack(3);
        assertFalse(transaction.storePartial(GTUtility.ItemId.create(afterCommit), afterCommit));

        assertEquals(3, afterCommit.stackSize);
        assertEquals(2L, core.getItemCached());
    }

    private Map<GTUtility.ItemId, Long> serializedItems() {
        NBTTagCompound tag = new NBTTagCompound();
        core.writeToNBT(tag);
        NBTTagCompound items = tag.getCompoundTag("itemCache");
        Map<GTUtility.ItemId, Long> result = new HashMap<>();
        int count = items.getInteger("count");
        for (int i = 0; i < count; i++) {
            NBTTagCompound entry = items.getCompoundTag("e" + i);
            ItemStack stack = ItemStack.loadItemStackFromNBT(entry.getCompoundTag("stack"));
            result.put(GTUtility.ItemId.create(stack), entry.getLong("amount"));
        }
        return result;
    }

    private ItemStack stack(int amount) {
        return stack(testItemA, amount);
    }

    private ItemStack stack(Item item, int amount) {
        return new ItemStack(item, amount);
    }
}
