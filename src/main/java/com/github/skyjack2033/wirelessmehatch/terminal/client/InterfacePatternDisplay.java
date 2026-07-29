package com.github.skyjack2033.wirelessmehatch.terminal.client;

import net.minecraft.item.ItemStack;

import appeng.api.storage.data.IAEStack;
import appeng.items.misc.ItemEncodedPattern;

final class InterfacePatternDisplay {

    private InterfacePatternDisplay() {}

    static ItemStack resolve(ItemStack source) {
        if (source == null || !(source.getItem() instanceof ItemEncodedPattern)) return source;

        try {
            IAEStack<?> output = ((ItemEncodedPattern) source.getItem()).getOutputAE(source);
            if (output == null) return source;

            ItemStack display = output.getItemStackForNEI();
            return display == null ? source : display;
        } catch (RuntimeException ignored) {
            return source;
        }
    }
}
