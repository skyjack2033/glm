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
        registerLinkToolRecipe();
        registerUnifiedOutputAssemblyRecipe();
    }

    private static void registerLinkToolRecipe() {
        ItemStack linkTool = new ItemStack(ItemLoader.WIRELESS_LINK_TOOL, 1);
        ItemStack circuit = ItemList.Circuit_Advanced.get(1);
        ItemStack dataCircuit = ItemList.Circuit_Data.get(1);
        if (circuit == null || dataCircuit == null) {
            WirelessMEHatch.LOG.warn("Skipping Wireless Link Tool recipe: missing circuit ingredient.");
            return;
        }
        GTModHandler.addCraftingRecipe(
            linkTool,
            GTModHandler.RecipeBits.NOT_REMOVABLE,
            new Object[] { " E ", "CDC", " R ", 'E', Items.ender_eye, 'C', circuit, 'D', dataCircuit, 'R',
                Items.redstone });
    }

    private static void registerUnifiedOutputAssemblyRecipe() {
        ItemStack meOutputHatch = ItemList.Hatch_Output_ME.get(1);
        ItemStack meOutputBus = ItemList.Hatch_Output_Bus_ME.get(1);
        ItemStack quantumSingularity = getAe2QuantumEntangledSingularity();
        ItemStack wirelessOutput = GTModHandler
            .getModItem("gregtech", "gt.blockmachines", 1, Config.wirelessUnifiedOutputAssemblyMeId);
        ItemStack linkTool = new ItemStack(ItemLoader.WIRELESS_LINK_TOOL, 1);

        if (meOutputHatch == null || meOutputBus == null || quantumSingularity == null || wirelessOutput == null) {
            WirelessMEHatch.LOG.warn(
                "Skipping Wireless Unified Output Assembly recipe: missing ingredient "
                    + "(meOutputHatch={}, meOutputBus={}, quantumSingularity={}, wirelessOutput={}).",
                meOutputHatch,
                meOutputBus,
                quantumSingularity,
                wirelessOutput);
            return;
        }

        GTModHandler.addCraftingRecipe(
            wirelessOutput,
            GTModHandler.RecipeBits.NOT_REMOVABLE,
            new Object[] { "ABA", "CDC", "AEA", 'A', quantumSingularity, 'B', meOutputBus, 'C', meOutputHatch, 'D',
                ItemList.Circuit_Advanced.get(1), 'E', linkTool });
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
