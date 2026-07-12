package com.github.skyjack2033.wirelessmehatch.output;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import gregtech.api.util.GTUtility;

public class WirelessItemOutputTransactionTest {

    private static final long ITEM_CAPACITY = 5L;

    private WirelessUnifiedOutputCore core;
    private WirelessItemOutputTransaction transaction;
    private Item item;

    @BeforeClass
    public static void bootstrapMinecraftRegistries() throws ReflectiveOperationException {
        if (Item.itemRegistry.getObject("feather") == null) {
            Method addObjectRaw = Item.itemRegistry.getClass()
                .getDeclaredMethod("addObjectRaw", int.class, String.class, Object.class);
            addObjectRaw.setAccessible(true);
            addObjectRaw.invoke(Item.itemRegistry, 288, "minecraft:feather", new Item());
        }
        assertNotNull(net.minecraft.init.Items.feather);
    }

    @Before
    public void setUp() {
        core = new WirelessUnifiedOutputCore(null, () -> {}, ITEM_CAPACITY, Long.MAX_VALUE);
        transaction = new WirelessItemOutputTransaction(null, core);
        item = new Item();
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
        ItemStack stack = stack(3);
        transaction.storePartial(GTUtility.ItemId.create(stack), stack);

        transaction.commit();

        assertEquals(3L, core.getItemCached());
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
    public void secondCommitDoesNotStoreTwice() {
        ItemStack stack = stack(3);
        transaction.storePartial(GTUtility.ItemId.create(stack), stack);

        transaction.commit();
        transaction.commit();

        assertEquals(3L, core.getItemCached());
    }

    @Test
    public void storeAfterCommitIsIgnored() {
        transaction.commit();
        ItemStack stack = stack(3);

        assertFalse(transaction.storePartial(GTUtility.ItemId.create(stack), stack));

        assertEquals(3, stack.stackSize);
        assertEquals(0L, core.getItemCached());
    }

    private ItemStack stack(int amount) {
        return new ItemStack(item, amount);
    }
}
