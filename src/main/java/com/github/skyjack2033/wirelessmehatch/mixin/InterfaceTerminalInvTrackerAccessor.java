package com.github.skyjack2033.wirelessmehatch.mixin;

import net.minecraft.inventory.IInventory;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "appeng.container.implementations.ContainerInterfaceTerminal$InvTracker", remap = false)
public interface InterfaceTerminalInvTrackerAccessor {

    @Accessor("patterns")
    IInventory wirelessmehatch$getPatterns();
}
