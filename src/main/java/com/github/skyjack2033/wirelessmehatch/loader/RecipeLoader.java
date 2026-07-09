package com.github.skyjack2033.wirelessmehatch.loader;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import com.github.skyjack2033.wirelessmehatch.Config;
import com.github.skyjack2033.wirelessmehatch.WirelessMEHatch;
import com.google.common.base.Optional;

import appeng.api.AEApi;
import appeng.api.definitions.IItemDefinition;
import gregtech.api.enums.ItemList;
import gregtech.api.util.GTModHandler;

public final class RecipeLoader {

    private RecipeLoader() {}

    public static void register() {
        // Wireless Output Hatch (ME) = ME Output Hatch + ME Output Bus + AE2 Quantum Entangled Singularity + tier
        // circuit
        ItemStack meOutputHatch = ItemList.Hatch_Output_ME.get(1);
        ItemStack meOutputBus = ItemList.Hatch_Output_Bus_ME.get(1);
        ItemStack quantumSingularity = getAe2QuantumEntangledSingularity();
        ItemStack wirelessOutput = GTModHandler
            .getModItem("gregtech", "gt.blockmachines", 1, Config.wirelessOutputHatchMeId);

        if (meOutputHatch == null || meOutputBus == null || quantumSingularity == null || wirelessOutput == null) {
            WirelessMEHatch.LOG.warn(
                "Skipping Wireless Output Hatch ME recipe registration: a required ingredient is null "
                    + "(meOutputHatch={}, meOutputBus={}, quantumSingularity={}, wirelessOutput={}).",
                meOutputHatch,
                meOutputBus,
                quantumSingularity,
                wirelessOutput);
        } else {
            GTModHandler.addCraftingRecipe(
                wirelessOutput,
                GTModHandler.RecipeBits.NOT_REMOVABLE,
                new Object[] { "ABA", "CDC", "AEA", 'A', meOutputHatch, 'B', meOutputBus, 'C', quantumSingularity, 'D',
                    ItemList.Circuit_Advanced.get(1), 'E', Items.ender_eye });
        }

        // Wireless Input Hatch (ME) = ME Input Hatch + ME Input Bus + Quantum Singularity + tier circuit
        ItemStack meInputHatch = ItemList.Hatch_Input_ME.get(1);
        ItemStack meInputBus = ItemList.Hatch_Input_Bus_ME.get(1);
        ItemStack wirelessInput = GTModHandler
            .getModItem("gregtech", "gt.blockmachines", 1, Config.wirelessInputHatchMeId);

        if (meInputHatch == null || meInputBus == null || quantumSingularity == null || wirelessInput == null) {
            WirelessMEHatch.LOG.warn(
                "Skipping Wireless Input Hatch ME recipe registration: a required ingredient is null "
                    + "(meInputHatch={}, meInputBus={}, quantumSingularity={}, wirelessInput={}).",
                meInputHatch,
                meInputBus,
                quantumSingularity,
                wirelessInput);
        } else {
            GTModHandler.addCraftingRecipe(
                wirelessInput,
                GTModHandler.RecipeBits.NOT_REMOVABLE,
                new Object[] { "ABA", "CDC", "AEA", 'A', meInputHatch, 'B', meInputBus, 'C', quantumSingularity, 'D',
                    ItemList.Circuit_Advanced.get(1), 'E', Items.ender_eye });
        }
    }

    /**
     * Get the AE2 Quantum Entangled Singularity via AE2's official definitions API, avoiding fragile hardcoded
     * item-name/meta lookups. Falls back to null if AE2 is not loaded or the item is unavailable.
     */
    private static ItemStack getAe2QuantumEntangledSingularity() {
        try {
            IItemDefinition qeSingularity = AEApi.instance()
                .definitions()
                .materials()
                .qESingularity();
            if (qeSingularity != null && qeSingularity.isEnabled()) {
                Optional<ItemStack> stack = qeSingularity.maybeStack(1);
                if (stack.isPresent()) {
                    return stack.get();
                }
            }
        } catch (Throwable t) {
            WirelessMEHatch.LOG.warn("Failed to get AE2 Quantum Entangled Singularity via API: {}", t.toString());
        }
        return null;
    }
}
