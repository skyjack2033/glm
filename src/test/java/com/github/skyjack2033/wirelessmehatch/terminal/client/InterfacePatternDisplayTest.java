package com.github.skyjack2033.wirelessmehatch.terminal.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;

import org.junit.BeforeClass;
import org.junit.Test;

import com.github.skyjack2033.wirelessmehatch.MinecraftRegistryTestBootstrap;

import appeng.api.config.FuzzyMode;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IAETagCompound;
import appeng.core.sync.packets.PacketInterfaceTerminalUpdate;
import appeng.items.misc.ItemEncodedPattern;
import io.netty.buffer.ByteBuf;
import sun.misc.Unsafe;

public class InterfacePatternDisplayTest {

    private static Unsafe unsafe;
    private static Item outputItem;
    private static Item outputPatternItem;
    private static Item nullPatternItem;
    private static Item throwingPatternItem;

    @BeforeClass
    public static void bootstrapItems() throws ReflectiveOperationException {
        MinecraftRegistryTestBootstrap.initializeVanillaItems();
        Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
        theUnsafe.setAccessible(true);
        unsafe = (Unsafe) theUnsafe.get(null);

        outputItem = registerItem(30040, "wirelessmehatch_test:display_output", new NamedItem("Real Output"));
        outputPatternItem = registerItem(
            30041,
            "wirelessmehatch_test:output_pattern",
            allocate(OutputPatternItem.class));
        nullPatternItem = registerItem(30042, "wirelessmehatch_test:null_pattern", allocate(NullPatternItem.class));
        throwingPatternItem = registerItem(
            30043,
            "wirelessmehatch_test:throwing_pattern",
            allocate(ThrowingPatternItem.class));
    }

    @Test
    public void resolvesNativeEncodedPatternOutputForRendering() {
        ItemStack pattern = new ItemStack(outputPatternItem);

        ItemStack display = InterfacePatternDisplay.resolve(pattern);

        assertEquals(outputItem, display.getItem());
        assertEquals("Real Output", display.getDisplayName());
    }

    @Test
    public void leavesOrdinaryItemsUnchanged() {
        ItemStack ordinary = new ItemStack(outputItem);

        assertSame(ordinary, InterfacePatternDisplay.resolve(ordinary));
    }

    @Test
    public void fallsBackToEncodedPatternWhenOutputIsMissingOrInvalid() {
        ItemStack missing = new ItemStack(nullPatternItem);
        ItemStack invalid = new ItemStack(throwingPatternItem);

        assertSame(missing, InterfacePatternDisplay.resolve(missing));
        assertSame(invalid, InterfacePatternDisplay.resolve(invalid));
    }

    @Test
    public void interfaceSearchMatchesResolvedOutputName() {
        InterfaceTerminalModel model = new InterfaceTerminalModel();
        NBTTagList patterns = new NBTTagList();
        patterns.appendTag(new ItemStack(outputPatternItem).writeToNBT(new NBTTagCompound()));
        model.applyUpdate(
            new PacketInterfaceTerminalUpdate().addNewEntry(50L, "Interface", true)
                .setLoc(1, 2, 3, 0, 1)
                .setItems(1, 1, 1, patterns));

        List<InterfaceTerminalModel.Entry> results = model.getVisibleEntries("real output");

        assertEquals(1, results.size());
        assertEquals(
            50L,
            results.get(0)
                .getId());
    }

    private static Item allocate(Class<? extends ItemEncodedPattern> type) throws InstantiationException {
        Item item = (Item) unsafe.allocateInstance(type);
        try {
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

    private static final class NamedItem extends Item {

        private final String displayName;

        private NamedItem(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String getItemStackDisplayName(ItemStack stack) {
            return displayName;
        }
    }

    private static final class OutputPatternItem extends ItemEncodedPattern {

        @Override
        public IAEStack<?> getOutputAE(ItemStack stack) {
            return new DisplayAEStack();
        }
    }

    private static final class NullPatternItem extends ItemEncodedPattern {

        @Override
        public IAEStack<?> getOutputAE(ItemStack stack) {
            return null;
        }
    }

    private static final class ThrowingPatternItem extends ItemEncodedPattern {

        @Override
        public IAEStack<?> getOutputAE(ItemStack stack) {
            throw new IllegalArgumentException("malformed pattern");
        }
    }

    private static final class DisplayAEStack implements IAEStack<DisplayAEStack> {

        @Override
        public void add(DisplayAEStack stack) {}

        @Override
        public long getStackSize() {
            return 1;
        }

        @Override
        public DisplayAEStack setStackSize(long stackSize) {
            return this;
        }

        @Override
        public long getCountRequestable() {
            return 0;
        }

        @Override
        public DisplayAEStack setCountRequestable(long countRequestable) {
            return this;
        }

        @Override
        public boolean isCraftable() {
            return false;
        }

        @Override
        public DisplayAEStack setCraftable(boolean craftable) {
            return this;
        }

        @Override
        public DisplayAEStack reset() {
            return this;
        }

        @Override
        public boolean isMeaningful() {
            return true;
        }

        @Override
        public void incStackSize(long amount) {}

        @Override
        public void decStackSize(long amount) {}

        @Override
        public void incCountRequestable(long amount) {}

        @Override
        public void decCountRequestable(long amount) {}

        @Override
        public void writeToNBT(NBTTagCompound tag) {}

        @Override
        public void writeToPacket(ByteBuf buffer) throws IOException {}

        @Override
        public boolean fuzzyComparison(Object stack, FuzzyMode mode) {
            return stack == this;
        }

        @Override
        public DisplayAEStack copy() {
            return this;
        }

        @Override
        public DisplayAEStack empty() {
            return this;
        }

        @Override
        public IAETagCompound getTagCompound() {
            return null;
        }

        @Override
        public boolean isItem() {
            return true;
        }

        @Override
        public boolean isFluid() {
            return false;
        }

        @Override
        public StorageChannel getChannel() {
            return StorageChannel.ITEMS;
        }

        @Override
        public String getLocalizedName() {
            return "Real Output";
        }

        @Override
        public boolean isSameType(DisplayAEStack stack) {
            return stack != null;
        }

        @Override
        public boolean isSameType(Object stack) {
            return stack instanceof DisplayAEStack;
        }

        @Override
        public String getUnlocalizedName() {
            return "wirelessmehatch_test:display_output";
        }

        @Override
        public String getDisplayName() {
            return "Real Output";
        }

        @Override
        public String getModId() {
            return "wirelessmehatch_test";
        }

        @Override
        public void setTagCompound(NBTTagCompound tag) {}

        @Override
        public boolean hasTagCompound() {
            return false;
        }

        @Override
        public ItemStack getItemStackForNEI() {
            return new ItemStack(outputItem);
        }

        @Override
        public void drawInGui(Minecraft minecraft, int x, int y) {}

        @Override
        public void drawOverlayInGui(Minecraft minecraft, int x, int y, boolean craftable, boolean requestable,
            boolean stored, boolean fuzzy) {}

        @Override
        public void drawOnBlockFace(World world) {}

        @Override
        public int getAmountPerUnit() {
            return 1;
        }

        @Override
        public IAEStackType<DisplayAEStack> getStackType() {
            return null;
        }
    }
}
