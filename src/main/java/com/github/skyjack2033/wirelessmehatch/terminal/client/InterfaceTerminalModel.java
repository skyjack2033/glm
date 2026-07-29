package com.github.skyjack2033.wirelessmehatch.terminal.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.StatCollector;

import appeng.api.storage.data.IAEStackType;
import appeng.core.sync.packets.PacketInterfaceTerminalUpdate;
import appeng.core.sync.packets.PacketInterfaceTerminalUpdate.PacketAdd;
import appeng.core.sync.packets.PacketInterfaceTerminalUpdate.PacketEntry;
import appeng.core.sync.packets.PacketInterfaceTerminalUpdate.PacketOverwrite;
import appeng.core.sync.packets.PacketInterfaceTerminalUpdate.PacketRemove;
import appeng.core.sync.packets.PacketInterfaceTerminalUpdate.PacketRename;

public final class InterfaceTerminalModel {

    private static final int MAX_INVENTORY_SLOTS = 4096;

    private static final Comparator<Entry> ENTRY_ORDER = (left, right) -> {
        int byPriority = Integer.compare(right.priority, left.priority);
        if (byPriority != 0) return byPriority;

        int byName = left.getDisplayName()
            .compareToIgnoreCase(right.getDisplayName());
        if (byName != 0) return byName;

        return Long.compare(left.id, right.id);
    };

    private final Map<Long, Entry> entries = new LinkedHashMap<>();
    private long selectedEntryId = -1;
    private boolean online = true;

    public boolean applyUpdates(List<PacketEntry> updates, int statusFlags) {
        Entry previousSelection = getSelectedEntry();
        if ((statusFlags & PacketInterfaceTerminalUpdate.CLEAR_ALL_BIT) != 0) {
            entries.clear();
            selectedEntryId = -1;
        }

        online = (statusFlags & PacketInterfaceTerminalUpdate.DISCONNECT_BIT) == 0;
        if (updates != null) {
            for (PacketEntry update : updates) {
                applyUpdate(update);
            }
        }
        repairSelection();
        return previousSelection != getSelectedEntry();
    }

    public void applyUpdate(PacketEntry update) {
        if (update instanceof PacketAdd) {
            add((PacketAdd) update);
        } else if (update instanceof PacketOverwrite) {
            overwrite((PacketOverwrite) update);
        } else if (update instanceof PacketRemove) {
            remove(update.entryId);
        } else if (update instanceof PacketRename) {
            rename(update.entryId, ((PacketRename) update).newName, ((PacketRename) update).suffix);
        }
        repairSelection();
    }

    public boolean isOnline() {
        return online;
    }

    public int size() {
        return entries.size();
    }

    public Entry getEntry(long entryId) {
        return entries.get(entryId);
    }

    public List<Entry> getVisibleEntries(String searchText) {
        String normalizedSearch = normalize(searchText);
        List<Entry> result = new ArrayList<>();
        for (Entry entry : entries.values()) {
            if (!entry.terminalVisible) continue;
            if (entry.matches(normalizedSearch)) result.add(entry);
        }
        Collections.sort(result, ENTRY_ORDER);
        return Collections.unmodifiableList(result);
    }

    public Entry getSelectedEntry() {
        return entries.get(selectedEntryId);
    }

    public long getSelectedEntryId() {
        return selectedEntryId;
    }

    public boolean select(long entryId) {
        Entry entry = entries.get(entryId);
        if (entry == null || !entry.terminalVisible) return false;
        selectedEntryId = entryId;
        return true;
    }

    public Entry selectFirstVisible(String searchText) {
        List<Entry> visible = getVisibleEntries(searchText);
        if (visible.isEmpty()) {
            selectedEntryId = -1;
            return null;
        }
        if (!containsId(visible, selectedEntryId)) selectedEntryId = visible.get(0).id;
        return entries.get(selectedEntryId);
    }

    void remove(long entryId) {
        entries.remove(entryId);
        repairSelection();
    }

    void rename(long entryId, String name, String suffix) {
        Entry entry = entries.get(entryId);
        if (entry == null) return;
        entry.name = nullToEmpty(name);
        entry.suffix = nullToEmpty(suffix);
    }

    private void add(PacketAdd packet) {
        Entry entry = new Entry(packet.entryId);
        entry.name = nullToEmpty(packet.name);
        entry.suffix = nullToEmpty(packet.suffix);
        entry.x = packet.x;
        entry.y = packet.y;
        entry.z = packet.z;
        entry.dimension = packet.dim;
        entry.side = packet.side;
        entry.rows = Math.max(0, packet.rows);
        entry.rowSize = Math.max(0, packet.rowSize);
        entry.numSlots = clampInventorySize(packet.numSlots);
        entry.online = packet.online;
        entry.p2pOutput = packet.p2pOutput;
        entry.terminalVisible = packet.terminalVisible;
        entry.priority = packet.priority;
        entry.supportedStackTypes = packet.supportedStackTypes == null ? new IAEStackType<?>[0]
            : packet.supportedStackTypes.clone();
        entry.selfRepresentation = copy(packet.selfRep);
        entry.displayRepresentation = copy(packet.dispRep);
        entry.replaceItems(packet.items, entry.inventorySize());
        entries.put(packet.entryId, entry);
        if (selectedEntryId < 0) selectedEntryId = packet.entryId;
    }

    private void overwrite(PacketOverwrite packet) {
        Entry entry = entries.get(packet.entryId);
        if (entry == null) return;

        if (packet.onlineValid) entry.online = packet.online;
        if (packet.sizeValid) {
            entry.rows = Math.max(0, packet.rows);
            entry.rowSize = Math.max(0, packet.rowSize);
            entry.numSlots = clampInventorySize(packet.numSlots);
            entry.resize(entry.inventorySize());
        }
        if (packet.itemsValid) {
            if (packet.allItemUpdate) {
                entry.replaceItems(packet.items, packet.items == null ? 0 : packet.items.tagCount());
                if (packet.sizeValid) entry.resize(entry.inventorySize());
            } else {
                entry.updateItems(packet.items, packet.validIndices);
            }
        }
        if (packet.priorityValid) entry.priority = packet.priority;
        if (packet.terminalVisibleValid) entry.terminalVisible = packet.terminalVisible;
    }

    private void repairSelection() {
        Entry selected = entries.get(selectedEntryId);
        if (selected != null && selected.terminalVisible) return;
        List<Entry> ordered = getVisibleEntries("");
        selectedEntryId = ordered.isEmpty() ? -1 : ordered.get(0).id;
    }

    private static boolean containsId(List<Entry> entries, long id) {
        for (Entry entry : entries) {
            if (entry.id == id) return true;
        }
        return false;
    }

    private static ItemStack copy(ItemStack stack) {
        return stack == null ? null : stack.copy();
    }

    private static String normalize(String value) {
        return nullToEmpty(value).trim()
            .toLowerCase(Locale.ROOT);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static int clampInventorySize(int size) {
        return Math.max(0, Math.min(MAX_INVENTORY_SLOTS, size));
    }

    public static final class Entry {

        private final long id;
        private String name = "";
        private String suffix = "";
        private int x;
        private int y;
        private int z;
        private int dimension;
        private int side;
        private int rows;
        private int rowSize;
        private int numSlots;
        private boolean online;
        private boolean p2pOutput;
        private boolean terminalVisible = true;
        private int priority;
        private IAEStackType<?>[] supportedStackTypes = new IAEStackType<?>[0];
        private ItemStack selfRepresentation;
        private ItemStack displayRepresentation;
        private ItemStack[] items = new ItemStack[0];

        private Entry(long id) {
            this.id = id;
        }

        public long getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getSuffix() {
            return suffix;
        }

        public String getSearchableName() {
            return name + suffix;
        }

        public String getDisplayName() {
            if (name.isEmpty()) return suffix;

            String translated;
            if (StatCollector.canTranslate(name)) {
                translated = StatCollector.translateToLocal(name);
            } else if (StatCollector.canTranslate(name + ".name")) {
                translated = StatCollector.translateToLocal(name + ".name");
            } else {
                translated = StatCollector.translateToFallback(name);
            }
            return translated + suffix;
        }

        public int getX() {
            return x;
        }

        public int getY() {
            return y;
        }

        public int getZ() {
            return z;
        }

        public int getDimension() {
            return dimension;
        }

        public int getSide() {
            return side;
        }

        public int getRows() {
            return rows;
        }

        public int getRowSize() {
            return rowSize;
        }

        public int getNumSlots() {
            return numSlots;
        }

        public boolean isOnline() {
            return online;
        }

        public boolean isP2POutput() {
            return p2pOutput;
        }

        public boolean isTerminalVisible() {
            return terminalVisible;
        }

        public int getPriority() {
            return priority;
        }

        public IAEStackType<?>[] getSupportedStackTypes() {
            return supportedStackTypes.clone();
        }

        public ItemStack getSelfRepresentation() {
            return selfRepresentation;
        }

        public ItemStack getDisplayRepresentation() {
            return displayRepresentation;
        }

        public int getInventorySize() {
            return items.length;
        }

        public ItemStack getStack(int slot) {
            return slot < 0 || slot >= items.length ? null : items[slot];
        }

        private int inventorySize() {
            long capacity = (long) rows * rowSize;
            return (int) Math.min(numSlots, Math.min(MAX_INVENTORY_SLOTS, capacity));
        }

        private void resize(int newSize) {
            int safeSize = clampInventorySize(newSize);
            if (items.length != safeSize) items = Arrays.copyOf(items, safeSize);
        }

        private void replaceItems(NBTTagList list, int newSize) {
            items = new ItemStack[clampInventorySize(newSize)];
            if (list == null) return;
            int count = Math.min(items.length, list.tagCount());
            for (int slot = 0; slot < count; slot++) {
                items[slot] = ItemStack.loadItemStackFromNBT(list.getCompoundTagAt(slot));
            }
        }

        private void updateItems(NBTTagList list, int[] validIndices) {
            if (list == null || validIndices == null) return;
            int count = Math.min(list.tagCount(), validIndices.length);
            for (int index = 0; index < count; index++) {
                int slot = validIndices[index];
                if (slot >= 0 && slot < items.length) {
                    items[slot] = ItemStack.loadItemStackFromNBT(list.getCompoundTagAt(index));
                }
            }
        }

        private boolean matches(String normalizedSearch) {
            if (normalizedSearch.isEmpty()) return true;
            if (normalize(getSearchableName()).contains(normalizedSearch)
                || normalize(getDisplayName()).contains(normalizedSearch)) return true;
            for (ItemStack stack : items) {
                if (stack == null) continue;
                if (normalize(stack.getDisplayName()).contains(normalizedSearch)) return true;

                ItemStack display = InterfacePatternDisplay.resolve(stack);
                if (display != stack && normalize(display.getDisplayName()).contains(normalizedSearch)) return true;
            }
            return false;
        }
    }
}
