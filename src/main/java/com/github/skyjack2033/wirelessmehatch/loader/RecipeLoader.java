package com.github.skyjack2033.wirelessmehatch.loader;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import gregtech.api.enums.ItemList;
import gregtech.api.util.GTModHandler;

public final class RecipeLoader {

    /**
     * AE2 quantum entangled singularity. Looked up directly from AE2 since GT does not expose it through
     * {@link ItemList}. The item name/meta (item.ItemMultiMaterial:36) is the AE2 1.7.10 registration for the entangled
     * singularity - flagged for verification at runtime (AE2 dev jar was not in cache for static verification).
     */
    private static final String AE2_MOD_ID = "appliedenergistics2";
    private static final String AE2_MULTI_MATERIAL = "item.ItemMultiMaterial";
    private static final int AE2_META_ENTANGLED_SINGULARITY = 36;

    private RecipeLoader() {}

    public static void register() {
        // Wireless Output Hatch (ME) = ME Output Hatch + ME Output Bus + AE2 Quantum Entangled Singularity + tier
        // circuit
        ItemStack meOutputHatch = ItemList.Hatch_Output_ME.get(1);
        ItemStack meOutputBus = ItemList.Hatch_Output_Bus_ME.get(1);
        ItemStack quantumSingularity = GTModHandler
            .getModItem(AE2_MOD_ID, AE2_MULTI_MATERIAL, 1, AE2_META_ENTANGLED_SINGULARITY);
        ItemStack wirelessOutput = GTModHandler
            .getModItem("gregtech", "gt.blockmachines", 1, MetaTileEntityLoader.WIRELESS_OUTPUT_HATCH_ME_ID);

        GTModHandler.addCraftingRecipe(
            wirelessOutput,
            GTModHandler.RecipeBits.NOT_REMOVABLE,
            new Object[] { "ABA", "CDC", "AEA", 'A', meOutputHatch, 'B', meOutputBus, 'C', quantumSingularity, 'D',
                ItemList.Circuit_Advanced.get(1), 'E', Items.ender_eye });

        // Wireless Input Hatch (ME) = ME Input Hatch + ME Input Bus + Quantum Singularity + tier circuit
        ItemStack meInputHatch = ItemList.Hatch_Input_ME.get(1);
        ItemStack meInputBus = ItemList.Hatch_Input_Bus_ME.get(1);
        ItemStack wirelessInput = GTModHandler
            .getModItem("gregtech", "gt.blockmachines", 1, MetaTileEntityLoader.WIRELESS_INPUT_HATCH_ME_ID);

        GTModHandler.addCraftingRecipe(
            wirelessInput,
            GTModHandler.RecipeBits.NOT_REMOVABLE,
            new Object[] { "ABA", "CDC", "AEA", 'A', meInputHatch, 'B', meInputBus, 'C', quantumSingularity, 'D',
                ItemList.Circuit_Advanced.get(1), 'E', Items.ender_eye });
    }
}
