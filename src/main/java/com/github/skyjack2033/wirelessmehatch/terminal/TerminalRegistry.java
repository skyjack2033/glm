package com.github.skyjack2033.wirelessmehatch.terminal;

import net.minecraft.item.ItemStack;

import com.github.skyjack2033.wirelessmehatch.WirelessMEHatch;

import cpw.mods.fml.common.registry.GameRegistry;

public final class TerminalRegistry {

    public static final String COMBINED_TERMINAL_NAME = "combined_terminal";

    private static ItemCombinedTerminal combinedTerminal;

    private TerminalRegistry() {}

    public static void registerItems() {
        if (combinedTerminal != null) return;

        combinedTerminal = new ItemCombinedTerminal();
        GameRegistry.registerItem(combinedTerminal, COMBINED_TERMINAL_NAME, WirelessMEHatch.MODID);
    }

    public static ItemCombinedTerminal getCombinedTerminal() {
        if (combinedTerminal == null) {
            throw new IllegalStateException("Combined terminal item has not been registered");
        }
        return combinedTerminal;
    }

    public static ItemStack combinedTerminalStack() {
        return new ItemStack(getCombinedTerminal(), 1, 0);
    }
}
