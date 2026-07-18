package com.github.skyjack2033.wirelessmehatch.loader;

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
        registerUnifiedOutputAssemblyRecipe();
    }

    private static void registerUnifiedOutputAssemblyRecipe() {
        ItemStack meOutputHatch = ItemList.Hatch_Output_ME.get(1);
        ItemStack meOutputBus = ItemList.Hatch_Output_Bus_ME.get(1);
        ItemStack quantumSingularity = getAe2QuantumEntangledSingularity();
        ItemStack wirelessOutput = GTModHandler
            .getModItem("gregtech", "gt.blockmachines", 1, Config.wirelessUnifiedOutputAssemblyMeId);
        ItemStack wirelessKit = getAe2WirelessKit();
        ItemStack advancedCircuit = ItemList.Circuit_Advanced.get(1);

        if (meOutputHatch == null || meOutputBus == null
            || quantumSingularity == null
            || wirelessOutput == null
            || wirelessKit == null
            || advancedCircuit == null) {
            WirelessMEHatch.LOG.warn(
                "Skipping Wireless Unified Output Assembly recipe: missing ingredient "
                    + "(meOutputHatch={}, meOutputBus={}, quantumSingularity={}, wirelessOutput={}, "
                    + "wirelessKit={}, advancedCircuit={}).",
                meOutputHatch,
                meOutputBus,
                quantumSingularity,
                wirelessOutput,
                wirelessKit,
                advancedCircuit);
            return;
        }

        GTModHandler.addCraftingRecipe(
            wirelessOutput,
            GTModHandler.RecipeBits.NOT_REMOVABLE,
            new Object[] { "ABA", "CDC", "AEA", 'A', quantumSingularity, 'B', meOutputBus, 'C', meOutputHatch, 'D',
                advancedCircuit, 'E', wirelessKit });
    }

    private static ItemStack getAe2WirelessKit() {
        try {
            IItemDefinition wirelessKit = AEApi.instance()
                .definitions()
                .items()
                .toolWirelessKit();
            if (wirelessKit != null && wirelessKit.isEnabled()) {
                Optional<ItemStack> stack = wirelessKit.maybeStack(1);
                if (stack.isPresent()) return stack.get();
            }
        } catch (RuntimeException | LinkageError error) {
            WirelessMEHatch.LOG.warn("Failed to get AE2 Wireless Kit via API: {}", error.toString());
        }
        return null;
    }

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
