package com.github.skyjack2033.wirelessmehatch.me;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import appeng.api.util.DimensionalCoord;
import appeng.helpers.WireLessToolHelper;

public final class WirelessKitNbtState {

    private static final String NBT_PENDING_SIMPLE_ANCHOR = "wirelessmehatch.pendingSimpleAnchor";
    private static final String NBT_PENDING_ADVANCED_ANCHOR = "wirelessmehatch.pendingAdvancedAnchor";

    private WirelessKitNbtState() {}

    public static void ensureInitialized(ItemStack kit) {
        if (kit.getTagCompound() == null) {
            WireLessToolHelper.newNBT(kit);
            return;
        }

        NBTTagCompound root = kit.getTagCompound();
        ensureCompound(root, WireLessToolHelper.NbtSimple);
        ensureCompound(root, WireLessToolHelper.NbtAdvanced);
        ensureCompound(root, WireLessToolHelper.NbtAdvancedLineQueue);
        ensureCompound(root, WireLessToolHelper.NbtAdvancedLineBinding);

        if (!root.hasKey(WireLessToolHelper.NbtSuper, 10)) {
            NBTTagCompound superTag = new NBTTagCompound();
            superTag.setTag(WireLessToolHelper.NbtSuperPins, new NBTTagList());
            superTag.setTag(WireLessToolHelper.NbtSuperNames, new NBTTagList());
            superTag.setTag(WireLessToolHelper.NbtSuperPos, new NBTTagCompound());
            root.setTag(WireLessToolHelper.NbtSuper, superTag);
        }
    }

    public static void storePoint(ItemStack kit, String key, DimensionalCoord point) {
        ensureInitialized(kit);
        NBTTagCompound tag = new NBTTagCompound();
        point.writeToNBT(tag);
        kit.getTagCompound()
            .setTag(key, tag);
    }

    public static DimensionalCoord readPoint(ItemStack kit, String key) {
        if (kit == null || kit.getTagCompound() == null
            || !kit.getTagCompound()
                .hasKey(key, 10))
            return null;
        NBTTagCompound tag = kit.getTagCompound()
            .getCompoundTag(key);
        return tag.hasNoTags() ? null : DimensionalCoord.readFromNBT(tag);
    }

    public static void clear(ItemStack kit, String key) {
        ensureInitialized(kit);
        kit.getTagCompound()
            .setTag(key, new NBTTagCompound());
    }

    public static void storeSimplePendingAnchor(ItemStack kit, DimensionalCoord point) {
        storePoint(kit, NBT_PENDING_SIMPLE_ANCHOR, point);
    }

    public static DimensionalCoord readSimplePendingAnchor(ItemStack kit) {
        return readPoint(kit, NBT_PENDING_SIMPLE_ANCHOR);
    }

    public static void clearSimplePendingAnchor(ItemStack kit) {
        clearPendingAnchor(kit, NBT_PENDING_SIMPLE_ANCHOR);
    }

    public static void storeAdvancedPendingAnchor(ItemStack kit, DimensionalCoord point) {
        storePoint(kit, NBT_PENDING_ADVANCED_ANCHOR, point);
    }

    public static DimensionalCoord readAdvancedPendingAnchor(ItemStack kit) {
        return readPoint(kit, NBT_PENDING_ADVANCED_ANCHOR);
    }

    public static void clearAdvancedPendingAnchor(ItemStack kit) {
        clearPendingAnchor(kit, NBT_PENDING_ADVANCED_ANCHOR);
    }

    public static DimensionalCoord readLinePoint(ItemStack kit, String lineKey, String pointKey) {
        if (kit == null || kit.getTagCompound() == null
            || !kit.getTagCompound()
                .hasKey(lineKey, 10))
            return null;
        NBTTagCompound line = kit.getTagCompound()
            .getCompoundTag(lineKey);
        if (!line.hasKey(pointKey, 10)) return null;
        return DimensionalCoord.readFromNBT(line.getCompoundTag(pointKey));
    }

    public static void storeLinePoint(ItemStack kit, String lineKey, String pointKey, DimensionalCoord point) {
        ensureInitialized(kit);
        NBTTagCompound line = kit.getTagCompound()
            .getCompoundTag(lineKey);
        NBTTagCompound pointTag = new NBTTagCompound();
        point.writeToNBT(pointTag);
        line.setTag(pointKey, pointTag);
        kit.getTagCompound()
            .setTag(lineKey, line);
    }

    public static void appendAdvanced(ItemStack kit, DimensionalCoord point) {
        List<DimensionalCoord> queue = new ArrayList<>(readAdvanced(kit));
        queue.add(point);
        writeAdvanced(kit, queue);
    }

    public static List<DimensionalCoord> readAdvanced(ItemStack kit) {
        if (kit == null || kit.getTagCompound() == null) return Collections.emptyList();
        return DimensionalCoord.readAsListFromNBT(
            kit.getTagCompound()
                .getCompoundTag(WireLessToolHelper.NbtAdvanced));
    }

    public static void removeAdvancedHead(ItemStack kit) {
        List<DimensionalCoord> queue = new ArrayList<>(readAdvanced(kit));
        if (!queue.isEmpty()) queue.remove(0);
        writeAdvanced(kit, queue);
    }

    public static List<DimensionalCoord> expandLine(DimensionalCoord first, DimensionalCoord second) {
        if (first == null || second == null || first.getDimension() != second.getDimension() || first.equals(second)) {
            return Collections.emptyList();
        }

        int changedAxes = (first.x == second.x ? 0 : 1) + (first.y == second.y ? 0 : 1) + (first.z == second.z ? 0 : 1);
        if (changedAxes != 1) return Collections.emptyList();

        int length = Math
            .max(Math.max(Math.abs(first.x - second.x), Math.abs(first.y - second.y)), Math.abs(first.z - second.z));
        int stepX = Integer.compare(second.x, first.x);
        int stepY = Integer.compare(second.y, first.y);
        int stepZ = Integer.compare(second.z, first.z);
        List<DimensionalCoord> line = new ArrayList<>(length + 1);
        for (int offset = 0; offset <= length; offset++) {
            line.add(
                new DimensionalCoord(
                    first.x + stepX * offset,
                    first.y + stepY * offset,
                    first.z + stepZ * offset,
                    first.getDimension()));
        }
        return line;
    }

    private static void ensureCompound(NBTTagCompound root, String key) {
        if (!root.hasKey(key, 10)) root.setTag(key, new NBTTagCompound());
    }

    private static void clearPendingAnchor(ItemStack kit, String key) {
        if (kit != null && kit.getTagCompound() != null) kit.getTagCompound()
            .removeTag(key);
    }

    private static void writeAdvanced(ItemStack kit, List<DimensionalCoord> queue) {
        ensureInitialized(kit);
        NBTTagCompound tag = new NBTTagCompound();
        DimensionalCoord.writeListToNBT(tag, queue);
        kit.getTagCompound()
            .setTag(WireLessToolHelper.NbtAdvanced, tag);
    }
}
