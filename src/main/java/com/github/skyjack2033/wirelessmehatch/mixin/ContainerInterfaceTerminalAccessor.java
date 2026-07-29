package com.github.skyjack2033.wirelessmehatch.mixin;

import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import appeng.container.implementations.ContainerInterfaceTerminal;

@Mixin(value = ContainerInterfaceTerminal.class, remap = false)
public interface ContainerInterfaceTerminalAccessor {

    @Accessor("trackedById")
    Map<Long, ?> wirelessmehatch$getTrackedById();
}
