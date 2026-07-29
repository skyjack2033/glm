package com.github.skyjack2033.wirelessmehatch.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import appeng.client.gui.implementations.GuiMEMonitorable;

@Mixin(value = GuiMEMonitorable.class, remap = false)
public interface GuiMEMonitorableAccessor {

    @Accessor("standardSize")
    void wirelessmehatch$setStandardSize(int standardSize);
}
