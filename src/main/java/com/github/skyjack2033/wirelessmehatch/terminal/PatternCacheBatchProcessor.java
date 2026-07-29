package com.github.skyjack2033.wirelessmehatch.terminal;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants.NBT;

import appeng.items.misc.ItemEncodedPattern;

final class PatternCacheBatchProcessor {

    private static final String ITEM_SUBSTITUTION_KEY = "substitute";
    private static final String OUTPUT_SUBSTITUTION_KEY = "beSubstitute";

    private PatternCacheBatchProcessor() {}

    static Result apply(IInventory inventory, PatternCacheBatchCommand command) {
        int changed = 0;
        int skipped = 0;
        for (int slot = 0; slot < inventory.getSizeInventory(); slot++) {
            ItemStack original = inventory.getStackInSlot(slot);
            if (original == null) continue;
            if (!(original.getItem() instanceof ItemEncodedPattern)) {
                skipped++;
                continue;
            }

            ItemStack updated = original.copy();
            boolean success = command.isScaling() ? scale(updated, command.getFactor(), command.isMultiplication())
                : setSubstitution(updated, command);
            if (success) {
                inventory.setInventorySlotContents(slot, updated);
                changed++;
            } else {
                skipped++;
            }
        }
        return new Result(changed, skipped);
    }

    static PatternCacheFlagState getItemSubstitutionState(IInventory inventory) {
        return getFlagState(inventory, ITEM_SUBSTITUTION_KEY);
    }

    static PatternCacheFlagState getOutputSubstitutionState(IInventory inventory) {
        return getFlagState(inventory, OUTPUT_SUBSTITUTION_KEY);
    }

    private static PatternCacheFlagState getFlagState(IInventory inventory, String key) {
        boolean foundEnabled = false;
        boolean foundDisabled = false;
        for (int slot = 0; slot < inventory.getSizeInventory(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (stack == null || !(stack.getItem() instanceof ItemEncodedPattern) || stack.getTagCompound() == null) {
                continue;
            }
            if (stack.getTagCompound()
                .getBoolean(key)) {
                foundEnabled = true;
            } else {
                foundDisabled = true;
            }
            if (foundEnabled && foundDisabled) return PatternCacheFlagState.MIXED;
        }
        if (foundEnabled) return PatternCacheFlagState.ON;
        return foundDisabled ? PatternCacheFlagState.OFF : PatternCacheFlagState.EMPTY;
    }

    private static boolean scale(ItemStack stack, int factor, boolean multiplication) {
        NBTTagCompound pattern = stack.getTagCompound();
        if (pattern == null || pattern.getBoolean("crafting")) return false;

        NBTTagList inputs = pattern.getTagList("in", NBT.TAG_COMPOUND);
        NBTTagList outputs = pattern.getTagList("out", NBT.TAG_COMPOUND);
        Validation validation = new Validation(factor, multiplication);
        validateList(inputs, validation);
        validateList(outputs, validation);
        if (!validation.valid || !validation.foundCount) return false;

        modifyList(inputs, factor, multiplication);
        modifyList(outputs, factor, multiplication);
        return true;
    }

    private static void validateList(NBTTagList list, Validation validation) {
        for (int index = 0; index < list.tagCount() && validation.valid; index++) {
            NBTTagCompound entry = list.getCompoundTagAt(index);
            if (entry.hasKey("Cnt", NBT.TAG_LONG)) {
                validation.check(entry.getLong("Cnt"), Long.MAX_VALUE);
            } else if (entry.hasKey("Count")) {
                validation.check(entry.getInteger("Count"), Integer.MAX_VALUE);
            }
        }
    }

    private static void modifyList(NBTTagList list, int factor, boolean multiplication) {
        for (int index = 0; index < list.tagCount(); index++) {
            NBTTagCompound entry = list.getCompoundTagAt(index);
            if (entry.hasKey("Cnt", NBT.TAG_LONG)) {
                long count = entry.getLong("Cnt");
                entry.setLong("Cnt", multiplication ? count * factor : count / factor);
            } else if (entry.hasKey("Count")) {
                int count = entry.getInteger("Count");
                entry.setInteger("Count", multiplication ? count * factor : count / factor);
            }
        }
    }

    private static boolean setSubstitution(ItemStack stack, PatternCacheBatchCommand command) {
        NBTTagCompound pattern = stack.getTagCompound();
        if (pattern == null) return false;

        switch (command) {
            case ITEM_SUBSTITUTION_ON:
                pattern.setBoolean(ITEM_SUBSTITUTION_KEY, true);
                return true;
            case ITEM_SUBSTITUTION_OFF:
                pattern.setBoolean(ITEM_SUBSTITUTION_KEY, false);
                return true;
            case OUTPUT_SUBSTITUTION_ON:
                pattern.setBoolean(OUTPUT_SUBSTITUTION_KEY, true);
                return true;
            case OUTPUT_SUBSTITUTION_OFF:
                pattern.setBoolean(OUTPUT_SUBSTITUTION_KEY, false);
                return true;
            default:
                return false;
        }
    }

    private static final class Validation {

        private final int factor;
        private final boolean multiplication;
        private boolean valid = true;
        private boolean foundCount;

        private Validation(int factor, boolean multiplication) {
            this.factor = factor;
            this.multiplication = multiplication;
        }

        private void check(long value, long maximum) {
            foundCount = true;
            if (value <= 0) {
                valid = false;
            } else if (multiplication) {
                valid = value <= maximum / factor;
            } else {
                valid = value % factor == 0;
            }
        }
    }

    static final class Result {

        private final int changed;
        private final int skipped;

        private Result(int changed, int skipped) {
            this.changed = changed;
            this.skipped = skipped;
        }

        int getChanged() {
            return changed;
        }

        int getSkipped() {
            return skipped;
        }
    }
}
